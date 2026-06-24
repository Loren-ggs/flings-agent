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

package com.flings.ai.flingsagent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多智能体编排配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.agent")
public class AgentProperties {

    /**
     * 多智能体编排总开关。
     * false（默认）时回退到旧 StreamChatPipeline 线性链路，便于灰度与 A/B 对比。
     */
    private boolean enabled = false;

    /**
     * 最大反思轮次（Critic 反思循环硬上限）
     */
    private int maxReflectionRounds = 2;

    /**
     * 编排总超时（毫秒）
     */
    private long orchestratorTimeoutMs = 60000;

    private Critic critic = new Critic();

    private Workers workers = new Workers();

    @Data
    public static class Critic {

        /**
         * 反思循环开关
         */
        private boolean enabled = true;
    }

    @Data
    public static class Workers {

        private Worker knowledge = new Worker();

        private Worker tool = new Worker();
    }

    @Data
    public static class Worker {

        private boolean enabled = true;

        private long timeoutMs = 15000;
    }
}
