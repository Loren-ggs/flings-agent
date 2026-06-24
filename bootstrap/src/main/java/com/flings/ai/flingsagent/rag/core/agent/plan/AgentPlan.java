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

import java.util.List;

/**
 * Planner 产出的执行计划
 *
 * @param steps      待执行的 worker 步骤（MVP 阶段全部并行）
 * @param systemOnly 是否为纯系统意图（无需检索，直接系统响应）
 * @param reasoning  规划理由（展示与可观测用）
 */
public record AgentPlan(List<PlanStep> steps, boolean systemOnly, String reasoning) {

    public static AgentPlan ofSystemOnly(String reasoning) {
        return new AgentPlan(List.of(), true, reasoning);
    }

    public boolean hasWorkers() {
        return steps != null && !steps.isEmpty();
    }
}
