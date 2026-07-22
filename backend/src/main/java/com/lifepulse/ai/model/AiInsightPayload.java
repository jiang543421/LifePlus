package com.lifepulse.ai.model;

import java.time.Instant;
import java.util.List;

/**
 * AI 洞察内部领域对象（spec §6.2）。
 *
 * <p>这是 Service 层与 Controller 层之间的传递对象；不含 {@code freshnessSeconds}
 * （在 Controller 现算，spec §6.3）。可序列化为 Redis 缓存值。
 *
 * @param headline    中文主文，1-2 句
 * @param chips       3 个 chip（顺序固定）；全空数据时可为空列表
 * @param generatedAt 服务端生成时间
 */
public record AiInsightPayload(
    String headline,
    List<MetricValue> chips,
    Instant generatedAt
) {
    public AiInsightPayload {
        // 防御性拷贝，保证不可变
        chips = chips == null ? List.of() : List.copyOf(chips);
    }
}