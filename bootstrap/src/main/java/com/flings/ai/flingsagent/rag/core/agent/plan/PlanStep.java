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

package com.flings.ai.flingsagent.rag.core.agent.plan;

import com.flings.ai.flingsagent.rag.core.agent.AgentRole;

import java.util.List;

/**
 * 执行计划中的单个步骤
 *
 * @param role        目标 worker 角色
 * @param agentName   目标 agent 名称（展示用）
 * @param subQuestion 分配给该 worker 的子问题
 * @param dependsOn   依赖的前序步骤 agentName（MVP 阶段为空，预留 DAG 编排）
 */
public record PlanStep(AgentRole role, String agentName, String subQuestion, List<String> dependsOn) {

    public static PlanStep of(AgentRole role, String agentName, String subQuestion) {
        return new PlanStep(role, agentName, subQuestion, List.of());
    }
}
