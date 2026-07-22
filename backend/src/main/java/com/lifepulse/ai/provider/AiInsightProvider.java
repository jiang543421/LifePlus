package com.lifepulse.ai.provider;

import com.lifepulse.ai.model.MetricValue;

/**
 * AI 洞察 provider 接口（spec §7）。
 *
 * <p>实现方：基于 {@link com.lifepulse.security.UserContext#current()} 拿 userId，
 * 自行调用对应 Mapper 聚合，返回 {@link MetricValue}。
 *
 * <p>{@link #isEnabled(Long)} 由 Service 在循环前调用；返回 false 时整个 provider 跳过。
 */
public interface AiInsightProvider {

    /** provider 唯一键（用于 metrics map key / 日志）。 */
    String key();

    /**
     * 是否启用。读 {@link com.lifepulse.ai.AiInsightProperties} 开关 + 运行时条件。
     */
    boolean isEnabled(Long userId);

    /**
     * 采集指标。失败时抛异常，由 Service catch（spec §4.3）；不可返回 null 替代抛错。
     */
    MetricValue collect(Long userId, AiCollectContext ctx);
}