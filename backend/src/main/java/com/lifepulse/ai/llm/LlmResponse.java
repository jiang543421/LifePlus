package com.lifepulse.ai.llm;

/**
 * LLM 调用响应（spec §4.3）。
 *
 * <p>不可变 record。{@link #empty()} 用于 v2.1 3 层降级链中的"无内容"占位
 * （L2 模板生成空 advice/highlight 时也回退到这里）。
 *
 * @param content         响应正文；可能为空字符串
 * @param promptTokens    输入 token 数；调用方未上报则为 0
 * @param responseTokens  输出 token 数；调用方未上报则为 0
 * @param latencyMs       端到端耗时（ms）；用于观测与日志
 */
public record LlmResponse(
    String content,
    int promptTokens,
    int responseTokens,
    long latencyMs
) {
    public static LlmResponse empty() {
        return new LlmResponse("", 0, 0, 0L);
    }
}
