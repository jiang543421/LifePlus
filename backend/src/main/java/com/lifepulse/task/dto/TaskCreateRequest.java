package com.lifepulse.task.dto;

import com.lifepulse.task.TaskConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 创建任务请求体（spec §5.3 POST /tasks）。
 *
 * <p>{@code title} 必填且长度受限；其他字段可空，省略即用 DB 默认（status=0、priority=0）。
 * {@code dueDate} 必须非未来（{@code @PastOrPresent}）；{@code planId} 非空时必须归属
 * 当前用户，否则由 {@code TaskService.requireOwnedPlan} 抛 {@code 1003}（CLAUDE.md §7.2
 * 跨用户拦截硬约束）。
 */
public record TaskCreateRequest(
        @NotBlank
        @Size(max = TaskConstants.MAX_TITLE_LEN)
        String title,

        @Max(TaskConstants.MAX_PRIORITY)
        Integer priority,

        @PastOrPresent
        LocalDate dueDate,

        @Size(max = TaskConstants.MAX_TAG_LEN)
        String tag,

        Long planId
) {
}
