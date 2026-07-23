package com.lifepulse.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.ai.llm.exception.LlmResponseInvalidException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 把 {@link LlmResponse#content()}（LLM 原始 JSON 字符串）解析为 {@link LlmInsightPayload}
 * （spec §4.3 / CLAUDE.md §11.6 code=1512）。
 *
 * <p>校验规则：headline(20-200) / advice(10-200) / highlight(10-200) 必填且长度受限；
 * mood 缺省或未知值经 {@link Mood#fromString} clamp 到 NEUTRAL。任何失败均抛
 * {@link LlmResponseInvalidException}，由上层 Service catch 后降级到 L2 模板（§11.3）。
 *
 * <p>敏感词仅扫描 advice + highlight（headline 允许犀利用词）。为满足 §7.1，
 * 异常 msg 与日志仅含字段名 + 命中 token，不含 LLM 响应体全文。
 *
 * @param response LLM 调用响应（content 可能为 null / 空 / 非 JSON）
 * @return 校验通过的不可变 payload
 * @throws LlmResponseInvalidException 空响应 / 非 JSON / 缺字段 / 越界 / 命中敏感词
 */
@Component
public class LlmJsonParser {

    private static final int MIN_HEADLINE = 20;
    private static final int MAX_HEADLINE = 200;
    private static final int MIN_ADVICE = 10;
    private static final int MAX_ADVICE = 200;
    private static final int MIN_HIGHLIGHT = 10;
    private static final int MAX_HIGHLIGHT = 200;

    private static final Set<String> SENSITIVE_TOKENS = Set.of(
        "fuck", "shit", "傻逼", "操你妈"
    );

    private final ObjectMapper objectMapper;

    public LlmJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LlmInsightPayload parse(LlmResponse response) {
        String body = response.content();
        if (body == null || body.isBlank()) {
            throw new LlmResponseInvalidException("Empty LLM response body");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception ex) {
            // 只带异常自身 message，不拼 body 全文（§7.1）
            throw new LlmResponseInvalidException("LLM JSON parse failed: " + ex.getMessage(), ex);
        }
        String headline = requireString(root, "headline");
        String advice = requireString(root, "advice");
        String highlight = requireString(root, "highlight");
        validateLength("headline", headline, MIN_HEADLINE, MAX_HEADLINE);
        validateLength("advice", advice, MIN_ADVICE, MAX_ADVICE);
        validateLength("highlight", highlight, MIN_HIGHLIGHT, MAX_HIGHLIGHT);
        checkSensitive("advice", advice);
        checkSensitive("highlight", highlight);
        Mood mood = Mood.fromString(root.path("mood").asText(null));
        return new LlmInsightPayload(
            headline.trim(), advice.trim(), highlight.trim(), mood,
            response.promptTokens(), response.responseTokens(), response.latencyMs()
        );
    }

    private static String requireString(JsonNode root, String field) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new LlmResponseInvalidException("Missing or empty field: " + field);
        }
        return value;
    }

    private static void validateLength(String field, String value, int min, int max) {
        int len = value.length();
        if (len < min || len > max) {
            throw new LlmResponseInvalidException(
                field + " length out of range [" + min + "," + max + "]: " + len);
        }
    }

    private static void checkSensitive(String field, String value) {
        String lower = value.toLowerCase();
        for (String token : SENSITIVE_TOKENS) {
            if (lower.contains(token)) {
                throw new LlmResponseInvalidException(
                    field + " contains sensitive token: " + token);
            }
        }
    }
}
