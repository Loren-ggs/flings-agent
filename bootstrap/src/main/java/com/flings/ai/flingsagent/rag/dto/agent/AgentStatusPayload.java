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

package com.flings.ai.flingsagent.rag.dto.agent;

/**
 * 单个 agent 状态变更事件载荷（SSE: agent_status）
 *
 * @param role       agent 角色（PLANNER / KNOWLEDGE / TOOL / SYNTHESIS / CRITIC）
 * @param agentName  agent 名称
 * @param status     状态：running / success / empty / error
 * @param durationMs 耗时毫秒（running 时为 0）
 * @param summary    产物摘要（running 时可为空）
 */
public record AgentStatusPayload(String role, String agentName, String status, long durationMs, String summary) {

    public static AgentStatusPayload running(String role, String agentName) {
        return new AgentStatusPayload(role, agentName, "running", 0L, null);
    }
}
