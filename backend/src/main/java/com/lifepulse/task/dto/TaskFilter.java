package com.lifepulse.task.dto;

import java.time.LocalDate;

/**
 * 任务列表过滤条件（spec §5.3 GET /tasks）。
 *
 * <p>所有字段可空（{@code null} = 不过滤）；{@code page} 1-based（控制器负责校验），
 * {@code size} 受 {@link com.lifepulse.task.TaskConstants#MAX_PAGE_SIZE} 限制。
 */
public record TaskFilter(
        Integer status,
        Integer priority,
        String tag,
        LocalDate dueFrom,
        LocalDate dueTo,
        int page,
        int size
) {
}