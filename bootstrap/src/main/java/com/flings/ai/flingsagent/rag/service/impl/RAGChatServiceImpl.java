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

package com.flings.ai.flingsagent.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.flings.ai.flingsagent.framework.context.UserContext;
import com.flings.ai.flingsagent.infra.chat.StreamCallback;
import com.flings.ai.flingsagent.rag.service.ratelimit.ChatQueueLimiter;
import com.flings.ai.flingsagent.rag.service.RAGChatService;
import com.flings.ai.flingsagent.rag.service.handler.StreamCallbackFactory;
import com.flings.ai.flingsagent.rag.service.handler.StreamTaskManager;
import com.flings.ai.flingsagent.rag.config.AgentProperties;
import com.flings.ai.flingsagent.rag.core.agent.AgentContext;
import com.flings.ai.flingsagent.rag.core.agent.orchestrator.AgentOrchestrator;
import com.flings.ai.flingsagent.rag.service.pipeline.StreamChatContext;
import com.flings.ai.flingsagent.rag.service.pipeline.StreamChatPipeline;
import com.flings.ai.flingsagent.rag.trace.StreamChatTraceRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话服务默认实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGChatServiceImpl implements RAGChatService {

    private final StreamChatPipeline chatPipeline;
    private final AgentOrchestrator agentOrchestrator;
    private final AgentProperties agentProperties;
    private final ChatQueueLimiter chatQueueLimiter;
    private final StreamCallbackFactory callbackFactory;
    private final StreamChatTraceRunner traceRunner;
    private final StreamTaskManager taskManager;

    @Override
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        String actualConversationId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String taskId = IdUtil.getSnowflakeNextIdStr();
        StreamCallback callback = callbackFactory.createChatEventHandler(emitter, actualConversationId, taskId);

        chatQueueLimiter.enqueue(question, actualConversationId, emitter,
                () -> traceRunner.run(question, actualConversationId, taskId, callback, traceAware -> {
                    if (agentProperties.isEnabled()) {
                        // 多智能体编排链路
                        AgentContext agentContext = AgentContext.builder()
                                .question(question)
                                .conversationId(actualConversationId)
                                .taskId(taskId)
                                .deepThinking(Boolean.TRUE.equals(deepThinking))
                                .userId(UserContext.getUserId())
                                .callback(traceAware)
                                .build();
                        agentOrchestrator.execute(agentContext);
                    } else {
                        // 旧线性流水线链路（回退）
                        StreamChatContext ctx = StreamChatContext.builder()
                                .question(question)
                                .conversationId(actualConversationId)
                                .taskId(taskId)
                                .deepThinking(Boolean.TRUE.equals(deepThinking))
                                .userId(UserContext.getUserId())
                                .callback(traceAware)
                                .build();
                        chatPipeline.execute(ctx);
                    }
                }));
    }

    @Override
    public void stopTask(String taskId) {
        taskManager.cancel(taskId);
    }
}
