/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flings.ai.flingsagent.rag.core.agent.orchestrator;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.flings.ai.flingsagent.framework.convention.ChatMessage;
import com.flings.ai.flingsagent.framework.convention.ChatRequest;
import com.flings.ai.flingsagent.framework.convention.RetrievedChunk;
import com.flings.ai.flingsagent.framework.trace.RagTraceNode;
import com.flings.ai.flingsagent.infra.chat.LLMService;
import com.flings.ai.flingsagent.infra.chat.StreamCancellationHandle;
import com.flings.ai.flingsagent.rag.config.AgentProperties;
import com.flings.ai.flingsagent.rag.core.agent.Agent;
import com.flings.ai.flingsagent.rag.core.agent.AgentContext;
import com.flings.ai.flingsagent.rag.core.agent.AgentResult;
import com.flings.ai.flingsagent.rag.core.agent.AgentRole;
import com.flings.ai.flingsagent.rag.core.agent.critic.CriticVerdict;
import com.flings.ai.flingsagent.rag.core.agent.plan.AgentPlan;
import com.flings.ai.flingsagent.rag.core.agent.plan.PlanStep;
import com.flings.ai.flingsagent.rag.core.guidance.GuidanceDecision;
import com.flings.ai.flingsagent.rag.core.intent.IntentResolver;
import com.flings.ai.flingsagent.rag.core.memory.ConversationMemoryService;
import com.flings.ai.flingsagent.rag.core.prompt.PromptTemplateLoader;
import com.flings.ai.flingsagent.rag.core.rewrite.RewriteResult;
import com.flings.ai.flingsagent.rag.dto.RetrievalContext;
import com.flings.ai.flingsagent.rag.dto.SubQuestionIntent;
import com.flings.ai.flingsagent.rag.dto.agent.AgentPlanPayload;
import com.flings.ai.flingsagent.rag.dto.agent.AgentStatusPayload;
import com.flings.ai.flingsagent.rag.dto.agent.ReflectionPayload;
import com.flings.ai.flingsagent.rag.enums.SSEEventType;
import com.flings.ai.flingsagent.rag.service.handler.StreamTaskManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.flings.ai.flingsagent.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;

/**
 * 多智能体编排器（Orchestrator-Worker）
 * <p>
 * 取代旧 StreamChatPipeline 成为多智能体链路的唯一编排入口。注入 {@code List<Agent>} 后
 * 按 {@link AgentRole} 分组调度。
 * <p>
 * 编排流程：记忆加载 → PlannerAgent（改写/意图/歧义/计划） → 歧义/纯系统短路
 * → 按 plan 并行执行 worker（KnowledgeAgent / ToolAgent）→ CriticAgent 反思（证据充分性）
 * → 不足则用补充子问题重新检索并累积证据 → 空证据短路 → SynthesisAgent 流式生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final ConversationMemoryService memoryService;
    private final IntentResolver intentResolver;
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final StreamTaskManager taskManager;
    private final AgentProperties agentProperties;
    private final Executor ragContextExecutor;
    private final List<Agent> agents;

    private final Map<AgentRole, Agent> agentByRole = new EnumMap<>(AgentRole.class);

    @PostConstruct
    void init() {
        for (Agent agent : agents) {
            agentByRole.putIfAbsent(agent.getRole(), agent);
        }
        log.info("AgentOrchestrator 已注册 agent 角色：{}", agentByRole.keySet());
    }

    @RagTraceNode(name = "agent-orchestrator", type = "AGENT")
    public void execute(AgentContext context) {
        // 1. 预处理：加载对话记忆
        loadMemory(context);

        // 2. Planner：改写 / 意图 / 歧义 / 计划
        emitRunning(context, AgentRole.PLANNER);
        runAgent(AgentRole.PLANNER, context);
        emitPlan(context);

        // 短路 A：意图歧义 → 澄清引导
        GuidanceDecision guidance = context.getGuidanceDecision();
        if (guidance != null && guidance.isPrompt()) {
            context.getCallback().onContent(guidance.getPrompt());
            context.getCallback().onComplete();
            return;
        }

        // 短路 B：纯系统意图 → 系统响应
        if (context.getPlan() != null && context.getPlan().systemOnly()) {
            streamSystemResponse(context);
            return;
        }

        // 3. Worker 并行 + Critic 反思循环
        boolean hasEvidence = runRetrievalWithReflection(context);

        // 短路 C：空证据
        if (!hasEvidence) {
            context.getCallback().onContent("未检索到与问题相关的文档内容。");
            context.getCallback().onComplete();
            return;
        }

        // 4. Synthesis：流式生成最终答案
        emitRunning(context, AgentRole.SYNTHESIS);
        runAgent(AgentRole.SYNTHESIS, context);
    }

    private void loadMemory(AgentContext context) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                context.getConversationId(),
                context.getUserId(),
                ChatMessage.user(context.getQuestion())
        );
        context.setHistory(history);
    }

    private void runAgent(AgentRole role, AgentContext context) {
        Agent agent = agentByRole.get(role);
        if (agent == null) {
            throw new IllegalStateException("缺少角色为 " + role + " 的 Agent");
        }
        context.recordResult(agent.execute(context));
    }

    /**
     * Worker 并行检索 + Critic 反思循环。
     * <p>
     * 反思评估"证据充分性"（非答案文本），不足则用补充子问题再检索并累积证据。
     * 终止条件：证据空、Critic PASS、达最大反思轮次、或 Critic 无补充方向。
     *
     * @return 是否检索到有效证据
     */
    private boolean runRetrievalWithReflection(AgentContext context) {
        boolean criticEnabled = agentProperties.getCritic().isEnabled()
                && agentByRole.containsKey(AgentRole.CRITIC);
        int maxRounds = Math.max(0, agentProperties.getMaxReflectionRounds());
        List<RetrievalContext> accumulated = new ArrayList<>();

        while (true) {
            // 并行执行 worker（worker 读取当前 subIntents 检索）
            runWorkers(context);
            accumulated.add(mergeRetrievals(context.getWorkerRetrievals().values()));

            RetrievalContext total = mergeRetrievals(accumulated);
            context.setRetrievalContext(total);

            if (total.isEmpty()) {
                return false;
            }
            if (!criticEnabled) {
                return true;
            }

            // 反思：评估证据是否充分
            emitRunning(context, AgentRole.CRITIC);
            runAgent(AgentRole.CRITIC, context);
            CriticVerdict verdict = context.getCriticVerdict();
            emitReflection(context, verdict);
            if (verdict == null || !verdict.isRetry()) {
                return true;
            }
            if (context.getReflectionRound() >= maxRounds) {
                log.info("已达最大反思轮次 {}，停止补充检索", maxRounds);
                return true;
            }
            if (CollUtil.isEmpty(verdict.supplementQuestions())) {
                return true;
            }

            // 准备下一轮补充检索
            prepareSupplementRound(context, verdict.supplementQuestions());
        }
    }

    /**
     * 用补充子问题重新做意图识别，供下一轮 worker 检索（worker 集合沿用原 plan）
     */
    private void prepareSupplementRound(AgentContext context, List<String> supplementQuestions) {
        context.setReflectionRound(context.getReflectionRound() + 1);
        log.info("第 {} 轮补充检索，补充子问题：{}", context.getReflectionRound(), supplementQuestions);

        RewriteResult supplement = new RewriteResult(
                context.getRewriteResult().rewrittenQuestion(),
                supplementQuestions
        );
        List<SubQuestionIntent> supplementIntents = intentResolver.resolve(supplement);
        context.setSubIntents(supplementIntents);
        context.setIntentGroup(intentResolver.mergeIntentGroup(supplementIntents));
    }

    /**
     * 按 plan 并行执行 worker，join 后在主线程串行记录结果（避免并发写元信息）
     */
    private void runWorkers(AgentContext context) {
        List<PlanStep> steps = context.getPlan() == null ? List.of() : context.getPlan().steps();
        if (CollUtil.isEmpty(steps)) {
            return;
        }
        // worker 启动事件（协作面板先亮出卡片）
        steps.forEach(step -> emitRunning(context, step.role(), step.agentName()));

        List<CompletableFuture<AgentResult>> futures = steps.stream()
                .map(step -> CompletableFuture.supplyAsync(() -> {
                    Agent worker = agentByRole.get(step.role());
                    return worker == null ? null : worker.execute(context);
                }, ragContextExecutor))
                .toList();
        futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .forEach(result -> {
                    context.recordResult(result);
                    emitAgentStatus(context, result);
                });
    }

    private RetrievalContext mergeRetrievals(Collection<RetrievalContext> partials) {
        StringBuilder kb = new StringBuilder();
        StringBuilder mcp = new StringBuilder();
        Map<String, List<RetrievedChunk>> intentChunks = new HashMap<>();
        for (RetrievalContext rc : partials) {
            if (rc == null) {
                continue;
            }
            if (StrUtil.isNotBlank(rc.getKbContext())) {
                kb.append(rc.getKbContext());
            }
            if (StrUtil.isNotBlank(rc.getMcpContext())) {
                mcp.append(rc.getMcpContext());
            }
            if (CollUtil.isNotEmpty(rc.getIntentChunks())) {
                intentChunks.putAll(rc.getIntentChunks());
            }
        }
        return RetrievalContext.builder()
                .kbContext(kb.toString().trim())
                .mcpContext(mcp.toString().trim())
                .intentChunks(intentChunks)
                .build();
    }

    /**
     * 纯系统意图响应（迁移自旧 StreamChatPipeline.streamSystemResponse）
     */
    private void streamSystemResponse(AgentContext context) {
        String customPrompt = context.getSubIntents().stream()
                .flatMap(si -> si.nodeScores().stream())
                .map(ns -> ns.getNode().getPromptTemplate())
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(context.getHistory())) {
            messages.addAll(context.getHistory());
        }
        messages.add(ChatMessage.user(context.getRewriteResult().rewrittenQuestion()));

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7D)
                .thinking(false)
                .build();
        StreamCancellationHandle handle = llmService.streamChat(request, context.getCallback());
        taskManager.bindHandle(context.getTaskId(), handle);
    }

    // ==================== SSE 协作事件（推送给前端协作面板）====================

    private void emitRunning(AgentContext context, AgentRole role) {
        Agent agent = agentByRole.get(role);
        emitRunning(context, role, agent == null ? role.name() : agent.getName());
    }

    private void emitRunning(AgentContext context, AgentRole role, String agentName) {
        context.getCallback().onAgentEvent(
                SSEEventType.AGENT_STATUS.value(),
                AgentStatusPayload.running(role.name(), agentName));
    }

    private void emitAgentStatus(AgentContext context, AgentResult result) {
        if (result == null) {
            return;
        }
        context.getCallback().onAgentEvent(
                SSEEventType.AGENT_STATUS.value(),
                new AgentStatusPayload(
                        result.getRole() == null ? null : result.getRole().name(),
                        result.getAgentName(),
                        statusText(result.getStatus()),
                        result.getDurationMs(),
                        result.getSummary()));
    }

    private void emitPlan(AgentContext context) {
        AgentPlan plan = context.getPlan();
        if (plan == null) {
            return;
        }
        List<AgentPlanPayload.PlanStepView> steps = plan.steps().stream()
                .map(s -> new AgentPlanPayload.PlanStepView(
                        s.role() == null ? null : s.role().name(),
                        s.agentName(),
                        s.subQuestion()))
                .toList();
        context.getCallback().onAgentEvent(
                SSEEventType.PLAN.value(),
                new AgentPlanPayload(context.getReflectionRound(), plan.reasoning(), steps));
    }

    private void emitReflection(AgentContext context, CriticVerdict verdict) {
        if (verdict == null) {
            return;
        }
        context.getCallback().onAgentEvent(
                SSEEventType.REFLECTION.value(),
                new ReflectionPayload(
                        context.getReflectionRound(),
                        verdict.verdict() == null ? null : verdict.verdict().name(),
                        verdict.reason(),
                        verdict.supplementQuestions()));
    }

    private String statusText(AgentResult.Status status) {
        if (status == null) {
            return "success";
        }
        return switch (status) {
            case SUCCESS -> "success";
            case EMPTY -> "empty";
            case ERROR -> "error";
        };
    }
}
