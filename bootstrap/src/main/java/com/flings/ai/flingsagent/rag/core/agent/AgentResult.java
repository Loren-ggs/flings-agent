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

import lombok.Builder;
import lombok.Data;

/**
 * 智能体执行结果（元信息）
 * <p>
 * 实际产物（检索上下文、文本等）由各 Agent 写入 {@link AgentContext} 的强类型字段，
 * 本对象只承载用于编排控制与可观测（trace / SSE 卡片）的状态、摘要与耗时。
 */
@Data
@Builder
public class AgentResult {

    /**
     * 产出该结果的角色
     */
    private AgentRole role;

    /**
     * 产出该结果的 agent 名称
     */
    private String agentName;

    /**
     * 执行状态
     */
    private Status status;

    /**
     * 给前端协作卡片展示的产物摘要
     */
    private String summary;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;

    public enum Status {

        /**
         * 成功且有产物
         */
        SUCCESS,

        /**
         * 成功但无产物（如未检索到内容）
         */
        EMPTY,

        /**
         * 执行异常
         */
        ERROR
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isEmpty() {
        return status == Status.EMPTY;
    }

    public static AgentResult of(AgentRole role, String agentName, Status status, String summary, long durationMs) {
        return AgentResult.builder()
                .role(role)
                .agentName(agentName)
                .status(status)
                .summary(summary)
                .durationMs(durationMs)
                .build();
    }
}
