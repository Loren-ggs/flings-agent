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

package com.flings.ai.flingsagent.rag.core.agent.planner;

import cn.hutool.core.collection.CollUtil;
import com.flings.ai.flingsagent.framework.trace.RagTraceNode;
import com.flings.ai.flingsagent.rag.config.AgentProperties;
import com.flings.ai.flingsagent.rag.core.agent.Agent;
import com.flings.ai.flingsagent.rag.core.agent.AgentContext;
import com.flings.ai.flingsagent.rag.core.agent.AgentResult;
import com.flings.ai.flingsagent.rag.core.agent.AgentRole;
import com.flings.ai.flingsagent.rag.core.agent.plan.AgentPlan;
import com.flings.ai.flingsagent.rag.core.agent.plan.PlanStep;
import com.flings.ai.flingsagent.rag.core.guidance.GuidanceDecision;
import com.flings.ai.flingsagent.rag.core.guidance.IntentGuidanceService;
import com.flings.ai.flingsagent.rag.core.intent.IntentResolver;
import com.flings.ai.flingsagent.rag.core.rewrite.QueryRewriteService;
import com.flings.ai.flingsagent.rag.core.rewrite.RewriteResult;
import com.flings.ai.flingsagent.rag.dto.IntentGroup;
import com.flings.ai.flingsagent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 规划 Agent
 * <p>
 * 整合"改写拆分 → 意图识别 → 歧义判定 → 生成执行计划"。复用现有
 * {@link QueryRewriteService} / {@link IntentResolver} / {@link IntentGuidanceService}，
 * 把决策结果（rewriteResult / subIntents / intentGroup / guidanceDecision / plan）写入编排上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerAgent implements Agent {

    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final AgentProperties agentProperties;

    @Override
    public String getName() {
        return "planner-agent";
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.PLANNER;
    }

    @RagTraceNode(name = "agent-planner", type = "AGENT")
    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();

        // 1. 改写 + 拆分
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(context.getQuestion(), context.getHistory());
        context.setRewriteResult(rewriteResult);

        // 2. 意图识别 + 分组
        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewriteResult);
        context.setSubIntents(subIntents);
        IntentGroup intentGroup = intentResolver.mergeIntentGroup(subIntents);
        context.setIntentGroup(intentGroup);

        // 3. 歧义判定（供 Orchestrator 决定是否走澄清短路）
        GuidanceDecision guidance = guidanceService.detectAmbiguity(rewriteResult.rewrittenQuestion(), subIntents);
        context.setGuidanceDecision(guidance);

        // 4. 生成执行计划
        AgentPlan plan = buildPlan(rewriteResult, subIntents, intentGroup);
        context.setPlan(plan);

        return AgentResult.of(
                getRole(),
                getName(),
                AgentResult.Status.SUCCESS,
                plan.reasoning(),
                System.currentTimeMillis() - start
        );
    }

    private AgentPlan buildPlan(RewriteResult rewriteResult,
                                List<SubQuestionIntent> subIntents,
                                IntentGroup intentGroup) {
        boolean systemOnly = CollUtil.isNotEmpty(subIntents)
                && subIntents.stream().allMatch(si -> intentResolver.isSystemOnly(si.nodeScores()));
        if (systemOnly) {
            return AgentPlan.ofSystemOnly("纯系统意图，直接由系统响应回答");
        }

        List<PlanStep> steps = new ArrayList<>();
        // 知识检索 worker 始终激活（含向量全局兜底通道）
        if (agentProperties.getWorkers().getKnowledge().isEnabled()) {
            steps.add(PlanStep.of(AgentRole.KNOWLEDGE, "knowledge-agent", rewriteResult.rewrittenQuestion()));
        }
        // 仅当存在 MCP 意图时激活工具 worker
        if (CollUtil.isNotEmpty(intentGroup.mcpIntents()) && agentProperties.getWorkers().getTool().isEnabled()) {
            steps.add(PlanStep.of(AgentRole.TOOL, "tool-agent", rewriteResult.rewrittenQuestion()));
        }

        String reasoning = String.format("KB 意图 %d 个、MCP 意图 %d 个，激活 worker：%s",
                intentGroup.kbIntents().size(),
                intentGroup.mcpIntents().size(),
                steps.stream().map(s -> s.role().name()).toList());
        return new AgentPlan(steps, false, reasoning);
    }
}
