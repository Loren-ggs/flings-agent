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

import com.flings.ai.flingsagent.framework.convention.ChatRequest;
import com.flings.ai.flingsagent.infra.chat.LLMService;
import com.flings.ai.flingsagent.infra.chat.StreamCallback;
import com.flings.ai.flingsagent.infra.chat.StreamCancellationHandle;
import com.flings.ai.flingsagent.rag.config.AgentProperties;
import com.flings.ai.flingsagent.rag.core.agent.Agent;
import com.flings.ai.flingsagent.rag.core.agent.AgentContext;
import com.flings.ai.flingsagent.rag.core.agent.AgentResult;
import com.flings.ai.flingsagent.rag.core.agent.AgentRole;
import com.flings.ai.flingsagent.rag.core.agent.plan.AgentPlan;
import com.flings.ai.flingsagent.rag.core.agent.plan.PlanStep;
import com.flings.ai.flingsagent.rag.core.intent.IntentResolver;
import com.flings.ai.flingsagent.rag.core.memory.ConversationMemoryService;
import com.flings.ai.flingsagent.rag.core.prompt.PromptTemplateLoader;
import com.flings.ai.flingsagent.rag.core.rewrite.RewriteResult;
import com.flings.ai.flingsagent.rag.dto.RetrievalContext;
import com.flings.ai.flingsagent.rag.service.handler.StreamTaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentOrchestrator 编排状态机单元测试（纯 Mockito，不依赖 Spring 上下文）
 * <p>
 * 覆盖三条核心编排分支：纯系统意图短路、空证据短路、正常流程抵达 Synthesis。
 */
class AgentOrchestratorTests {

    private ConversationMemoryService memoryService;
    private LLMService llmService;
    private PromptTemplateLoader promptTemplateLoader;
    private StreamTaskManager taskManager;
    private AgentProperties agentProperties;
    private StreamCallback callback;

    private Agent planner;
    private Agent knowledge;
    private Agent synthesis;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        memoryService = mock(ConversationMemoryService.class);
        IntentResolver intentResolver = mock(IntentResolver.class);
        llmService = mock(LLMService.class);
        promptTemplateLoader = mock(PromptTemplateLoader.class);
        taskManager = mock(StreamTaskManager.class);
        callback = mock(StreamCallback.class);
        agentProperties = new AgentProperties();

        planner = mock(Agent.class);
        when(planner.getRole()).thenReturn(AgentRole.PLANNER);
        knowledge = mock(Agent.class);
        when(knowledge.getRole()).thenReturn(AgentRole.KNOWLEDGE);
        synthesis = mock(Agent.class);
        when(synthesis.getRole()).thenReturn(AgentRole.SYNTHESIS);

        when(memoryService.loadAndAppend(any(), any(), any())).thenReturn(List.of());

        orchestrator = new AgentOrchestrator(
                memoryService, intentResolver, llmService, promptTemplateLoader,
                taskManager, agentProperties, Runnable::run,
                List.of(planner, knowledge, synthesis));
        orchestrator.init();
    }

    private AgentContext baseContext() {
        return AgentContext.builder()
                .question("战士有什么卡")
                .conversationId("c1")
                .taskId("t1")
                .deepThinking(false)
                .userId("u1")
                .callback(callback)
                .build();
    }

    @Test
    void systemOnlyPlanShouldStreamSystemResponseAndSkipWorkers() {
        doAnswer(invocation -> {
            AgentContext ctx = invocation.getArgument(0);
            ctx.setRewriteResult(new RewriteResult("战士有什么卡", List.of("战士有什么卡")));
            ctx.setSubIntents(List.of());
            ctx.setPlan(AgentPlan.ofSystemOnly("纯系统意图"));
            return AgentResult.of(AgentRole.PLANNER, "planner", AgentResult.Status.SUCCESS, "", 1L);
        }).when(planner).execute(any());
        when(promptTemplateLoader.load(any())).thenReturn("system");
        when(llmService.streamChat(any(ChatRequest.class), any(StreamCallback.class)))
                .thenReturn(mock(StreamCancellationHandle.class));

        orchestrator.execute(baseContext());

        verify(llmService).streamChat(any(ChatRequest.class), any(StreamCallback.class));
        verify(knowledge, never()).execute(any());
        verify(synthesis, never()).execute(any());
    }

    @Test
    void emptyEvidenceShouldShortCircuitBeforeSynthesis() {
        agentProperties.getCritic().setEnabled(false);
        doAnswer(invocation -> {
            AgentContext ctx = invocation.getArgument(0);
            ctx.setRewriteResult(new RewriteResult("战士有什么卡", List.of("战士有什么卡")));
            ctx.setPlan(new AgentPlan(
                    List.of(PlanStep.of(AgentRole.KNOWLEDGE, "knowledge", "战士有什么卡")), false, "检索"));
            return AgentResult.of(AgentRole.PLANNER, "planner", AgentResult.Status.SUCCESS, "", 1L);
        }).when(planner).execute(any());
        // worker 不写入 workerRetrievals → 证据为空
        when(knowledge.execute(any()))
                .thenReturn(AgentResult.of(AgentRole.KNOWLEDGE, "knowledge", AgentResult.Status.EMPTY, "", 1L));

        orchestrator.execute(baseContext());

        verify(callback).onContent(contains("未检索到"));
        verify(callback).onComplete();
        verify(synthesis, never()).execute(any());
    }

    @Test
    void evidenceFoundShouldReachSynthesis() {
        agentProperties.getCritic().setEnabled(false);
        doAnswer(invocation -> {
            AgentContext ctx = invocation.getArgument(0);
            ctx.setRewriteResult(new RewriteResult("战士有什么卡", List.of("战士有什么卡")));
            ctx.setPlan(new AgentPlan(
                    List.of(PlanStep.of(AgentRole.KNOWLEDGE, "knowledge", "战士有什么卡")), false, "检索"));
            return AgentResult.of(AgentRole.PLANNER, "planner", AgentResult.Status.SUCCESS, "", 1L);
        }).when(planner).execute(any());
        doAnswer(invocation -> {
            AgentContext ctx = invocation.getArgument(0);
            ctx.putWorkerRetrieval(AgentRole.KNOWLEDGE, RetrievalContext.builder()
                    .kbContext("战士卡牌：打击、防御……")
                    .intentChunks(Map.of())
                    .build());
            return AgentResult.of(AgentRole.KNOWLEDGE, "knowledge", AgentResult.Status.SUCCESS, "命中 3 篇", 1L);
        }).when(knowledge).execute(any());
        when(synthesis.execute(any()))
                .thenReturn(AgentResult.of(AgentRole.SYNTHESIS, "synthesis", AgentResult.Status.SUCCESS, "", 1L));

        orchestrator.execute(baseContext());

        verify(synthesis).execute(any());
        verify(callback, never()).onContent(contains("未检索到"));
    }
}
