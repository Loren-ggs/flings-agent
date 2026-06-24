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

package com.flings.ai.flingsagent.rag.core.agent.synthesis;

import com.flings.ai.flingsagent.framework.convention.ChatMessage;
import com.flings.ai.flingsagent.framework.convention.ChatRequest;
import com.flings.ai.flingsagent.framework.trace.RagTraceNode;
import com.flings.ai.flingsagent.infra.chat.LLMService;
import com.flings.ai.flingsagent.infra.chat.StreamCancellationHandle;
import com.flings.ai.flingsagent.rag.core.agent.Agent;
import com.flings.ai.flingsagent.rag.core.agent.AgentContext;
import com.flings.ai.flingsagent.rag.core.agent.AgentResult;
import com.flings.ai.flingsagent.rag.core.agent.AgentRole;
import com.flings.ai.flingsagent.rag.core.intent.IntentResolver;
import com.flings.ai.flingsagent.rag.core.prompt.PromptContext;
import com.flings.ai.flingsagent.rag.core.prompt.RAGPromptService;
import com.flings.ai.flingsagent.rag.dto.IntentGroup;
import com.flings.ai.flingsagent.rag.dto.RetrievalContext;
import com.flings.ai.flingsagent.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 汇总生成 Agent
 * <p>
 * 唯一往用户真流式输出的 Agent：把各 worker 写入上下文的产物（{@link RetrievalContext}）
 * 组装成 {@link PromptContext}，复用 {@link RAGPromptService} 构建消息后调用
 * {@link LLMService#streamChat} 流式回写。逻辑等价于旧 StreamChatPipeline.streamLLMResponse。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SynthesisAgent implements Agent {

    private final RAGPromptService promptBuilder;
    private final LLMService llmService;
    private final IntentResolver intentResolver;
    private final StreamTaskManager taskManager;

    @Override
    public String getName() {
        return "synthesis-agent";
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.SYNTHESIS;
    }

    @RagTraceNode(name = "agent-synthesis", type = "AGENT")
    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        RetrievalContext retrievalContext = context.getRetrievalContext();
        IntentGroup intentGroup = context.getIntentGroup() != null
                ? context.getIntentGroup()
                : intentResolver.mergeIntentGroup(context.getSubIntents());

        PromptContext promptContext = PromptContext.builder()
                .question(context.getRewriteResult().rewrittenQuestion())
                .mcpContext(retrievalContext.getMcpContext())
                .kbContext(retrievalContext.getKbContext())
                .mcpIntents(intentGroup.mcpIntents())
                .kbIntents(intentGroup.kbIntents())
                .intentChunks(retrievalContext.getIntentChunks())
                .build();

        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                context.getHistory(),
                context.getRewriteResult().rewrittenQuestion(),
                context.getRewriteResult().subQuestions()
        );

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .thinking(context.isDeepThinking())
                .temperature(retrievalContext.hasMcp() ? 0.3D : 0D)
                .topP(retrievalContext.hasMcp() ? 0.8D : 1D)
                .build();

        StreamCancellationHandle handle = llmService.streamChat(chatRequest, context.getCallback());
        taskManager.bindHandle(context.getTaskId(), handle);

        return AgentResult.of(
                getRole(),
                getName(),
                AgentResult.Status.SUCCESS,
                "生成最终回答",
                System.currentTimeMillis() - start
        );
    }
}
