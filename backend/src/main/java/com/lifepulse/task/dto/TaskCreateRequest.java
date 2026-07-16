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
 * {@code dueDate} 必须非未来（{@code @PastOrPresent}）；{@code planId} 不强制归属校验
 * （Phase 2-B 内 MVP1 不做"plan 必须属于同一 user"的强校验，留待 Phase 3 plan 模块
 * 落地时统一处理 — 仅记录 TODO=plan-cross-user）。
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