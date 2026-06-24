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

/**
 * 智能体角色
 * <p>
 * Orchestrator-Worker 拓扑中的固定角色：
 * - PLANNER：任务规划，拆解子任务、决定激活哪些 worker
 * - KNOWLEDGE / TOOL：专家 worker，分别负责知识库检索与 MCP 工具调用
 * - SYNTHESIS：汇总各 worker 产物并流式生成最终答案
 * - CRITIC：反思证据是否充分，决定是否补充检索重试
 */
public enum AgentRole {

    PLANNER,

    KNOWLEDGE,

    TOOL,

    SYNTHESIS,

    CRITIC
}
