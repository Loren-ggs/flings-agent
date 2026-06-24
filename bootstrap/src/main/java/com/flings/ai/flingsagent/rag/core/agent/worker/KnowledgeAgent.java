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

package com.flings.ai.flingsagent.rag.core.agent.worker;

import com.flings.ai.flingsagent.framework.trace.RagTraceNode;
import com.flings.ai.flingsagent.rag.config.SearchChannelProperties;
import com.flings.ai.flingsagent.rag.core.agent.Agent;
import com.flings.ai.flingsagent.rag.core.agent.AgentContext;
import com.flings.ai.flingsagent.rag.core.agent.AgentResult;
import com.flings.ai.flingsagent.rag.core.agent.AgentRole;
import com.flings.ai.flingsagent.rag.core.retrieve.RetrievalEngine;
import com.flings.ai.flingsagent.rag.core.retrieve.RetrievalMode;
import com.flings.ai.flingsagent.rag.dto.RetrievalContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识检索专家 Agent
 * <p>
 * Phase 1：整体复用 {@link RetrievalEngine}（KB 多通道检索 + MCP 工具），产出统一的
 * {@link RetrievalContext} 写入编排上下文。Phase 2 会把纯 KB 与纯 MCP 拆为两个独立 worker。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeAgent implements Agent {

    private final RetrievalEngine retrievalEngine;
    private final SearchChannelProperties searchProperties;

    @Override
    public String getName() {
        return "knowledge-agent";
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.KNOWLEDGE;
    }

    @RagTraceNode(name = "agent-knowledge", type = "AGENT")
    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        RetrievalContext retrievalContext = retrievalEngine.retrieve(
                context.getSubIntents(),
                searchProperties.getDefaultTopK(),
                RetrievalMode.KB_ONLY
        );
        context.putWorkerRetrieval(getRole(), retrievalContext);

        boolean hasKb = retrievalContext != null && retrievalContext.hasKb();
        return AgentResult.of(
                getRole(),
                getName(),
                hasKb ? AgentResult.Status.SUCCESS : AgentResult.Status.EMPTY,
                hasKb ? "已召回知识库上下文" : "未检索到相关内容",
                System.currentTimeMillis() - start
        );
    }
}
