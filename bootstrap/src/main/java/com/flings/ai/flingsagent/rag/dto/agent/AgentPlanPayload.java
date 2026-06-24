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

import java.util.List;

/**
 * Planner 执行计划事件载荷（SSE: plan）
 *
 * @param round     反思轮次（0 为首轮规划）
 * @param reasoning 规划理由
 * @param steps     计划步骤（决定激活哪些 worker）
 */
public record AgentPlanPayload(int round, String reasoning, List<PlanStepView> steps) {

    /**
     * 计划步骤视图
     *
     * @param role        worker 角色
     * @param agentName   agent 名称
     * @param subQuestion 分配给该 worker 的子问题
     */
    public record PlanStepView(String role, String agentName, String subQuestion) {
    }
}
