package com.lifepulse.ai.llm;

/**
 * LLM 调用元数据（spec §1.6 / Task 7 brief）。
 *
 * <p>不可变 record；嵌入 {@link LlmInsightPayload} 的 {@code llmMeta} 字段，
 * 由 {@code AiInsightResponse} 透传给前端用于渲染 {@code source="llm"} 时的
 * 模型 / token / 耗时标签。
 *
 * @param promptTokens    输入 token 数（来自 {@code LlmResponse.promptTokens}）
 * @param responseTokens  输出 token 数（来自 {@code LlmResponse.responseTokens}）
 * @param latencyMs       端到端耗时（毫秒）
 */
public record LlmMeta(
    int promptTokens,
    int responseTokens,
    long latencyMs
) {}
