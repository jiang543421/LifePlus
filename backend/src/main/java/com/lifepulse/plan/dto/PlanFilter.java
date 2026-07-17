package com.lifepulse.plan.dto;

import java.time.LocalDateTime;

/**
 * 计划列表过滤条件（spec §5.4 GET /plans）。
 *
 * <p>{@code from}/{@code to} 范围下/上界（含），均为 {@code null} 表示无下/上界；
 * 与 mapper SQL 的 {@code IS NULL OR ...} 分支对齐（spec §2.3 查询模式）。
 * {@code page} 1-based（控制器负责校验），{@code size} 受
 * {@link com.lifepulse.plan.PlanConstants#MAX_PAGE_SIZE} 限制。
 */
public record PlanFilter(
        LocalDateTime from,
        LocalDateTime to,
        int page,
        int size
) {
}