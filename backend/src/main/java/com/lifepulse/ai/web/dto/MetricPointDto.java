package com.lifepulse.ai.web.dto;

import java.time.LocalDate;

/**
 * 时间序列单点（spec §v2.2 trend / CLAUDE.md §4.1 不可变 record）。
 *
 * <p>前端 sparkline 渲染用：{@code value} 为归一化前原始值，{@code label} 为
 * 已格式化展示文案（避免前端再次做 i18n / 千分位 / 货币符号）。
 *
 * @param date  数据日期（Asia/Shanghai 本地日）
 * @param value 原始数值：{@code task=completionRate 0.0-1.0} / {@code plan=eventCount}
 *              / {@code expense=totalAmount（float，避免前端 BigDecimal 处理）}；
 *              {@code diet} 永久空列表
 * @param label 展示文案（"85%" / "3 项" / "¥420.00"）
 */
public record MetricPointDto(
        LocalDate date,
        double value,
        String label
) {
}