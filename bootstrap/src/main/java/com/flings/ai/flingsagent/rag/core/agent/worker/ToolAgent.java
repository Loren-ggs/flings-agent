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
 * MCP 工具专家 Agent
 * <p>
 * 仅负责 MCP 意图的工具并行调用（{@link RetrievalMode#MCP_ONLY}），产出只含 mcpContext 的
 * 局部 {@link RetrievalContext} 写入编排上下文，由 Orchestrator 与 KnowledgeAgent 的产物合并。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolAgent implements Agent {

    private final RetrievalEngine retrievalEngine;

    @Override
    public String getName() {
        return "tool-agent";
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.TOOL;
    }

    @RagTraceNode(name = "agent-tool", type = "AGENT")
    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        RetrievalContext retrievalContext = retrievalEngine.retrieve(
                context.getSubIntents(),
                0,
                RetrievalMode.MCP_ONLY
        );
        context.putWorkerRetrieval(getRole(), retrievalContext);

        boolean hasMcp = retrievalContext != null && retrievalContext.hasMcp();
        return AgentResult.of(
                getRole(),
                getName(),
                hasMcp ? AgentResult.Status.SUCCESS : AgentResult.Status.EMPTY,
                hasMcp ? "已完成 MCP 工具调用" : "未触发工具调用",
                System.currentTimeMillis() - start
        );
    }
}
