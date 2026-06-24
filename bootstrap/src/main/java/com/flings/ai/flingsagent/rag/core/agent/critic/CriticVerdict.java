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

package com.flings.ai.flingsagent.rag.core.agent.critic;

import java.util.List;

/**
 * Critic 反思判定结果
 *
 * @param verdict             判定：通过或需要补充检索
 * @param supplementQuestions RETRY 时给出的补充检索子问题
 * @param reason              判定理由（展示与可观测用）
 */
public record CriticVerdict(Verdict verdict, List<String> supplementQuestions, String reason) {

    public enum Verdict {

        /**
         * 证据充分，可进入答案生成
         */
        PASS,

        /**
         * 证据不足，需补充检索后重试
         */
        RETRY
    }

    public static CriticVerdict pass(String reason) {
        return new CriticVerdict(Verdict.PASS, List.of(), reason);
    }

    public boolean isRetry() {
        return verdict == Verdict.RETRY;
    }
}
