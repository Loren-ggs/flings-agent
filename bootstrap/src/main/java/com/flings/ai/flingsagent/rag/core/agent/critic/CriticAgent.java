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

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flings.ai.flingsagent.framework.convention.ChatMessage;
import com.flings.ai.flingsagent.framework.convention.ChatRequest;
import com.flings.ai.flingsagent.framework.trace.RagTraceNode;
import com.flings.ai.flingsagent.infra.chat.LLMService;
import com.flings.ai.flingsagent.rag.core.agent.Agent;
import com.flings.ai.flingsagent.rag.core.agent.AgentContext;
import com.flings.ai.flingsagent.rag.core.agent.AgentResult;
import com.flings.ai.flingsagent.rag.core.agent.AgentRole;
import com.flings.ai.flingsagent.rag.dto.RetrievalContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 反思 Agent
 * <p>
 * 评估"已检索证据是否足以充分回答问题"（不评估最终答案文本，故反思循环发生在 Synthesis 之前，
 * 保证最终流式输出只跑一次、绝不撤回已输出内容）。证据不足时返回 RETRY 与补充检索子问题。
 * 用非流式 {@link LLMService#chat} 做决策，结果以 JSON 解析，解析失败时容错为 PASS 不阻塞主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriticAgent implements Agent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_EVIDENCE_CHARS = 3000;

    private static final String SYSTEM_PROMPT = """
            你是一个严格的检索质量审查员。你的任务是判断"已检索到的证据"是否足以充分回答用户问题。
            只评估证据是否充分，不要自己回答问题。
            - 若证据充分：verdict=PASS。
            - 若证据不足（缺少关键信息、覆盖面不够、答非所问）：verdict=RETRY，并在 supplementQuestions 给出 1-3 个用于补充检索的具体子问题。
            严格只输出如下 JSON，不要任何多余文字或 markdown 代码块：
            {"verdict":"PASS或RETRY","supplementQuestions":["..."],"reason":"简要理由"}
            """;

    private final LLMService llmService;

    @Override
    public String getName() {
        return "critic-agent";
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.CRITIC;
    }

    @RagTraceNode(name = "agent-critic", type = "AGENT")
    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String question = context.getRewriteResult().rewrittenQuestion();
        String evidence = buildEvidenceDigest(context.getRetrievalContext());

        CriticVerdict verdict;
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system(SYSTEM_PROMPT),
                            ChatMessage.user(buildUserPrompt(question, evidence))
                    ))
                    .temperature(0D)
                    .thinking(false)
                    .build();
            String raw = llmService.chat(request);
            verdict = parseVerdict(raw);
        } catch (Exception e) {
            log.warn("Critic 执行异常，默认通过", e);
            verdict = CriticVerdict.pass("Critic 异常，默认通过");
        }
        context.setCriticVerdict(verdict);

        return AgentResult.of(
                getRole(),
                getName(),
                AgentResult.Status.SUCCESS,
                String.format("反思结论：%s（%s）", verdict.verdict(), verdict.reason()),
                System.currentTimeMillis() - start
        );
    }

    private String buildUserPrompt(String question, String evidence) {
        return "用户问题：" + question + "\n\n已检索证据：\n" + (StrUtil.isBlank(evidence) ? "（无）" : evidence);
    }

    private String buildEvidenceDigest(RetrievalContext retrievalContext) {
        if (retrievalContext == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(retrievalContext.getKbContext())) {
            sb.append(retrievalContext.getKbContext());
        }
        if (StrUtil.isNotBlank(retrievalContext.getMcpContext())) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(retrievalContext.getMcpContext());
        }
        String digest = sb.toString();
        return digest.length() > MAX_EVIDENCE_CHARS ? digest.substring(0, MAX_EVIDENCE_CHARS) : digest;
    }

    private CriticVerdict parseVerdict(String raw) {
        if (StrUtil.isBlank(raw)) {
            return CriticVerdict.pass("空响应，默认通过");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(extractJson(raw));
            String verdictText = node.path("verdict").asText("PASS");
            List<String> supplements = new ArrayList<>();
            JsonNode arr = node.path("supplementQuestions");
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    String s = item.asText();
                    if (StrUtil.isNotBlank(s)) {
                        supplements.add(s);
                    }
                }
            }
            String reason = node.path("reason").asText("");
            boolean retry = "RETRY".equalsIgnoreCase(verdictText) && !supplements.isEmpty();
            return retry
                    ? new CriticVerdict(CriticVerdict.Verdict.RETRY, supplements, reason)
                    : new CriticVerdict(CriticVerdict.Verdict.PASS, List.of(), reason);
        } catch (Exception e) {
            log.warn("Critic 结果解析失败，默认通过：{}", raw, e);
            return CriticVerdict.pass("解析失败，默认通过");
        }
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : raw;
    }
}
