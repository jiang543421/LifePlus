package com.lifepulse.ai.llm;

import java.time.Duration;

/**
 * LLM 调用请求（spec §4.3 / CLAUDE.md §11.1）。
 *
 * <p>不可变 record。compact constructor 校验必要字段；调用方须保证
 * {@code timeout} 与 {@link com.lifepulse.ai.llm.LlmProperties#timeoutMs()} 语义一致。
 *
 * @param systemPrompt      系统提示词；非空
 * @param userPrompt        用户提示词；非空
 * @param maxResponseTokens 单次响应 token 上限（1-1000）
 * @param timeout           单次调用超时
 */
public record LlmRequest(
    String systemPrompt,
    String userPrompt,
    int maxResponseTokens,
    Duration timeout
) {
    public LlmRequest {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt is required");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt is required");
        }
        if (maxResponseTokens <= 0 || maxResponseTokens > 1000) {
            throw new IllegalArgumentException("maxResponseTokens out of range: " + maxResponseTokens);
        }
    }
}
