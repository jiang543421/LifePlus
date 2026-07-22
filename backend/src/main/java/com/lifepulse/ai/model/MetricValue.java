package com.lifepulse.ai.model;

import java.math.BigDecimal;

/**
 * Provider 采集到的单个指标值（不可变 record）。
 *
 * <p>所有数值用 {@link BigDecimal} 避免浮点精度问题（任务完成率、消费额）。
 * {@link Trend} 描述相对变化方向，用于 chip 颜色与副标文案。
 *
 * @param value  数值；可为 {@code null}（表示"无数据"）
 * @param unit   显示单位（"%" / "¥" / "项" / "kcal"）
 * @param trend  变化方向
 */
public record MetricValue(BigDecimal value, String unit, Trend trend) {

    /** 无数据占位（value=null + trend=NONE）。 */
    public static MetricValue none() {
        return new MetricValue(null, null, Trend.NONE);
    }

    /** 是否包含有效数据。value 非 null 且 > 0。 */
    public boolean isNonEmpty() {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}