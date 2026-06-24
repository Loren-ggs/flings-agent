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
 * Critic 反思结论事件载荷（SSE: reflection）
 *
 * @param round               当前反思轮次
 * @param verdict             判定：PASS / RETRY
 * @param reason              判定理由
 * @param supplementQuestions 补充检索子问题（RETRY 时）
 */
public record ReflectionPayload(int round, String verdict, String reason, List<String> supplementQuestions) {
}
