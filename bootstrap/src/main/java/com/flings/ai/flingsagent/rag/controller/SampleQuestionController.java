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

package com.flings.ai.flingsagent.rag.controller;

import com.flings.ai.flingsagent.framework.convention.Result;
import com.flings.ai.flingsagent.framework.web.Results;
import com.flings.ai.flingsagent.rag.controller.vo.SampleQuestionVO;
import com.flings.ai.flingsagent.rag.service.SampleQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 示例问题控制器（问答首页推荐展示）
 */
@RestController
@RequiredArgsConstructor
public class SampleQuestionController {

    private final SampleQuestionService sampleQuestionService;

    /**
     * 随机获取示例问题列表（问答首页推荐问法）
     */
    @GetMapping("/rag/sample-questions")
    public Result<List<SampleQuestionVO>> listSampleQuestions() {
        return Results.success(sampleQuestionService.listRandomQuestions());
    }
}
