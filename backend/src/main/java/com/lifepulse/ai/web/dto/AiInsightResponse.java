package com.lifepulse.ai.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifepulse.ai.llm.LlmMeta;
import com.lifepulse.ai.llm.Mood;
import java.time.Instant;
import java.util.List;

/**
 * /api/ai/insight/today 响应 data 字段（spec §7.3 / v2.1 扩展）。
 *
 * <p>v2.1 新增 5 字段全部 {@code @JsonInclude(NON_NULL)}，保证 v2.0 旧缓存条目可被 v2.1 代码反序列化
 * （缺失字段取 {@code null}），前端拿到 null 不渲染对应区块。
 *
 * @param headline          中文主文
 * @param chips             3-4 个 chip
 * @param generatedAt       服务端生成时间
 * @param freshnessSeconds  距生成的秒数（负值钳为 0）
 * @param source            {@code "llm"} | {@code "template"}
 * @param advice            LLM 生成的建议（模板降级时 null）
 * @param highlight         LLM 生成的亮点（模板降级时 null）
 * @param mood              LLM 生成的情绪（模板降级时 null）
 * @param llmMeta           LLM 调用元信息（模板降级时 null）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiInsightResponse(
        String headline,
        List<AiChipDto> chips,
        Instant generatedAt,
        long freshnessSeconds,
        @JsonInclude(JsonInclude.Include.NON_NULL) String source,
        @JsonInclude(JsonInclude.Include.NON_NULL) String advice,
        @JsonInclude(JsonInclude.Include.NON_NULL) String highlight,
        @JsonInclude(JsonInclude.Include.NON_NULL) Mood mood,
        @JsonInclude(JsonInclude.Include.NON_NULL) LlmMeta llmMeta) {

    /** v2.0 兼容构造器（v2.0 老代码/测试 fallback 用）。 */
    public AiInsightResponse(String headline, List<AiChipDto> chips,
            Instant generatedAt, long freshnessSeconds) {
        this(headline, chips, generatedAt, freshnessSeconds, null, null, null, null, null);
    }

    /** 钳制 freshnessSeconds 不为负；chips 不可变。 */
    public AiInsightResponse {
        if (freshnessSeconds < 0) {
            freshnessSeconds = 0;
        }
        chips = chips == null ? List.of() : List.copyOf(chips);
    }
}
