package com.lifepulse.ai.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * /api/ai/insight/today 响应 data 字段（spec §6.2）。
 *
 * <p>{@code freshnessSeconds} 由 Controller 现算：
 * {@code Duration.between(generatedAt, Instant.now()).getSeconds()}，负值钳为 0。
 *
 * @param headline          中文主文
 * @param chips             3 个 chip
 * @param generatedAt       服务端生成时间
 * @param freshnessSeconds  距生成的秒数（负值钳为 0）
 */
public record AiInsightResponse(
    String headline,
    List<AiChipDto> chips,
    Instant generatedAt,
    long freshnessSeconds
) {

    /** 钳制 freshnessSeconds 不为负。 */
    public AiInsightResponse {
        if (freshnessSeconds < 0) {
            freshnessSeconds = 0;
        }
        chips = chips == null ? List.of() : List.copyOf(chips);
    }
}