package com.lifepulse.ai.llm;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * LLM 洞察输出（spec §1.5 / Task 7 brief）。
 *
 * <p>{@code LlmInsightGenerator}（Task 11）的成功输出；模板路径（L2 降级）时
 * {@link #llmMeta} 为 {@code null}，{@link #mood} 可为 {@code null}（模板不强制）。
 *
 * <p>{@code headline} / {@link Mood} / {@code advice} / {@code highlight} 4 字段
 * 由 {@code LlmJsonParser}（Task 9）做进一步长度与必填校验，本 record 仅做结构表达。
 *
 * <p>{@code @JsonInclude(NON_NULL)}：模板路径下 {@code llmMeta=null} / 部分字段为
 * {@code null} 时不写入 JSON 键，符合 CLAUDE.md §11.1 不可变性 + spec §0 P4 向后兼容。
 *
 * <p>{@code provider/model} 字段未纳入本 record —— 它们由调用方（{@code AiInsightService}）
 * 持有并独立写入响应，不与 token 计数 / 耗时混在同一结构中。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmInsightPayload(
    String headline,
    String advice,
    String highlight,
    Mood mood,
    LlmMeta llmMeta
) {
    /**
     * 便捷构造：调用方持有 token / 耗时原子数据时直接组合成 {@link LlmMeta}。
     */
    public LlmInsightPayload(String headline, String advice, String highlight, Mood mood,
                             int promptTokens, int responseTokens, long latencyMs) {
        this(headline, advice, highlight, mood,
            new LlmMeta(promptTokens, responseTokens, latencyMs));
    }
}
