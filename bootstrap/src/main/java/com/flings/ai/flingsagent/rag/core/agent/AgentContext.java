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

package com.flings.ai.flingsagent.rag.core.agent;

import com.flings.ai.flingsagent.framework.convention.ChatMessage;
import com.flings.ai.flingsagent.infra.chat.StreamCallback;
import com.flings.ai.flingsagent.rag.core.agent.critic.CriticVerdict;
import com.flings.ai.flingsagent.rag.core.agent.plan.AgentPlan;
import com.flings.ai.flingsagent.rag.core.guidance.GuidanceDecision;
import com.flings.ai.flingsagent.rag.core.rewrite.RewriteResult;
import com.flings.ai.flingsagent.rag.dto.IntentGroup;
import com.flings.ai.flingsagent.rag.dto.RetrievalContext;
import com.flings.ai.flingsagent.rag.dto.SubQuestionIntent;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多智能体编排上下文
 * <p>
 * 贯穿一次问答的所有 Agent，承载不可变输入与编排过程中逐步填充的中间态。
 * 取代旧 {@code StreamChatContext} 在多智能体链路中的角色。
 */
@Getter
@Builder
public class AgentContext {

    // ==================== 不可变输入参数 ====================

    private final String question;

    private final String conversationId;

    private final String taskId;

    private final boolean deepThinking;

    private final String userId;

    /**
     * 流式回调出口（trace-aware 包装后的 StreamChatEventHandler）
     */
    private final StreamCallback callback;

    // ==================== 编排中填充的中间状态 ====================

    @Setter
    private List<ChatMessage> history;

    @Setter
    private RewriteResult rewriteResult;

    @Setter
    private List<SubQuestionIntent> subIntents;

    @Setter
    private IntentGroup intentGroup;

    @Setter
    private RetrievalContext retrievalContext;

    /**
     * Planner 产出的执行计划
     */
    @Setter
    private AgentPlan plan;

    /**
     * 歧义引导判定结果（命中则走澄清短路）
     */
    @Setter
    private GuidanceDecision guidanceDecision;

    /**
     * 各 worker 产出的局部检索结果（按角色），由 Orchestrator 合并为最终 retrievalContext
     */
    @Builder.Default
    private Map<AgentRole, RetrievalContext> workerRetrievals = new ConcurrentHashMap<>();

    /**
     * Critic 反思判定结果
     */
    @Setter
    private CriticVerdict criticVerdict;

    /**
     * 当前反思轮次（Phase 3 反思循环使用）
     */
    @Setter
    @Builder.Default
    private int reflectionRound = 0;

    /**
     * 各 Agent 产物（按角色），用于编排控制与可观测
     */
    @Builder.Default
    private Map<AgentRole, AgentResult> agentResults = new ConcurrentHashMap<>();

    /**
     * 记录一个 Agent 的执行结果
     */
    public void recordResult(AgentResult result) {
        if (result != null && result.getRole() != null) {
            agentResults.put(result.getRole(), result);
        }
    }

    /**
     * 记录一个 worker 的局部检索结果
     */
    public void putWorkerRetrieval(AgentRole role, RetrievalContext retrieval) {
        if (role != null && retrieval != null) {
            workerRetrievals.put(role, retrieval);
        }
    }
}
