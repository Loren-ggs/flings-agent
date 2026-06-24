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

package com.flings.ai.flingsagent.rag.core.retrieve;

/**
 * 检索模式
 * <p>
 * 用于把 {@link RetrievalEngine} 的"知识库检索"与"MCP 工具调用"两部分按需拆分，
 * 让 KnowledgeAgent（纯 KB）与 ToolAgent（纯 MCP）作为独立 worker 并行调用同一套底层逻辑。
 */
public enum RetrievalMode {

    /**
     * 仅知识库检索
     */
    KB_ONLY,

    /**
     * 仅 MCP 工具调用
     */
    MCP_ONLY,

    /**
     * 知识库 + MCP（旧线性链路的默认行为）
     */
    BOTH;

    public boolean includesKb() {
        return this != MCP_ONLY;
    }

    public boolean includesMcp() {
        return this != KB_ONLY;
    }
}
