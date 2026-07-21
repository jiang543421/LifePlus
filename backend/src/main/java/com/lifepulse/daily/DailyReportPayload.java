package com.lifepulse.daily;

import java.time.LocalDate;

/**
 * 日报响应（plan §3 DTO / spec §5 GET /api/daily）。
 *
 * <p>服务端请求时实时聚合 {@code t_task} / {@code t_plan} / {@code t_expense}，
 * 只读不可改。饮食模块未上线时 {@code diet.enabled = false}。
 */
public record DailyReportPayload(
        LocalDate date,
        TaskMetrics task,
        PlanMetrics plan,
        ExpenseMetrics expense,
        DietMetrics diet
) {
}
