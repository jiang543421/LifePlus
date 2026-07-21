package com.lifepulse.daily;

import java.time.LocalDate;

/**
 * 周报响应（plan §3 DTO / spec §5 GET /api/daily/week）。
 *
 * <p>{@code isoWeek} 形如 "2026-W29"（ISO 8601）；{@code weekStart} 必为周一，
 * {@code weekEnd} 必为周日（基于 {@link com.lifepulse.daily.DailyConstants#WEEK_START}）。
 * 跨年周按 ISO 8601 规则处理：例如 {@code 2026-W01} 起始为 2025-12-29。
 */
public record WeeklyReportPayload(
        String isoWeek,
        LocalDate weekStart,
        LocalDate weekEnd,
        WeeklyComparison comparison
) {
}
