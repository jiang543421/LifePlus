package com.lifepulse.daily;

import java.util.Map;

/**
 * 日程指标（plan §3 DTO）。
 *
 * <p>事件数与总分钟基于 {@code t_plan.start_time} 落在目标日 00:00–23:59 的记录。
 * {@code totalMinutes} <b>排除全天事件</b>（{@code all_day = 1}），全天事件只在
 * {@code eventCount} 中计数、不贡献分钟数。{@code busiestHour} 为 0–23 整数，
 * 一天内无事件时为 null。
 */
public record PlanMetrics(
        long eventCount,
        long totalMinutes,
        Map<String, Long> categoryDistribution,
        Integer busiestHour
) {
}
