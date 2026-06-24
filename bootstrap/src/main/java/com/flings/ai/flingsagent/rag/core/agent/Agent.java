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
 * 智能体接口
 * <p>
 * 仿照 {@code SearchChannel} 的"实现接口 + {@code @Component} 自动注册"范式：
 * 任意 {@code @Component} 实现都会被 Spring 收集进 {@code List<Agent>}，
 * 由 {@code AgentOrchestrator} 按 {@link AgentRole} 分组调度，无需改动编排框架。
 * <p>
 * 约定：worker 类 Agent（KNOWLEDGE / TOOL）把检索/工具产物写入 {@link AgentContext}，
 * 返回的 {@link AgentResult} 只用于编排控制与可观测。
 */
public interface Agent {

    /**
     * Agent 名称（用于日志、trace 与前端卡片）
     */
    String getName();

    /**
     * Agent 角色
     */
    AgentRole getRole();

    /**
     * 是否在当前上下文启用
     */
    default boolean isEnabled(AgentContext context) {
        return true;
    }

    /**
     * 执行
     *
     * @param context 编排上下文
     * @return 执行结果元信息
     */
    AgentResult execute(AgentContext context);
}
