package com.lifepulse.ai.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 趋势图响应（spec §v2.2 trend / CLAUDE.md §11.1 无新表约束）。
 *
 * <p>数据源：复用 {@code DailyReportService.daily(userId, date)} 在 {@code window}
 * 天范围内逐日聚合，N × 4 次 Provider 调用（14 天 = 56 次 mapper）。
 * 全部基于既有 5 张业务表（{@code t_task} / {@code t_plan} / {@code t_expense}
 * / {@code t_diet} / {@code t_daily_report}），无新表 / 无新列。
 *
 * @param window      时间窗天数（7 / 14 / 30）
 * @param from        起始日期（含）
 * @param to          结束日期（含，今日）
 * @param metrics     指标 key 列表，固定 {@code ["task", "plan", "expense", "diet"]}
 * @param series      按 key 索引的指标系列；diet 永远为空数组
 * @param generatedAt 服务端生成时间（与 insight payload 同源同精度）
 */
public record AiTrendResponse(
        int window,
        LocalDate from,
        LocalDate to,
        List<String> metrics,
        Map<String, MetricSeriesDto> series,
        Instant generatedAt
) {
    public AiTrendResponse {
        // 防御性拷贝（CLAUDE.md §4.1）
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        series = series == null ? Map.of() : Map.copyOf(series);
    }

    /** 固定 4 槽 metrics 列表（前端按此顺序渲染 2×2 grid）。 */
    public static final List<String> FIXED_METRICS =
            List.of("task", "plan", "expense", "diet");
}