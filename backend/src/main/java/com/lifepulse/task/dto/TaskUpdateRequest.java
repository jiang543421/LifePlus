package com.lifepulse.task.dto;

import com.lifepulse.task.TaskConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 更新任务请求体（spec §5.3 PUT /tasks/{id}）。
 *
 * <p>所有字段可选 — 服务端用 {@code null} 判定跳过；{@code title=null} 保留原值，
 * 显式 {@code ""}（空串）也保留原值（{@code @Size} 仅约束非空输入）。
 * 鉴权 1003 防御在 service 层完成（{@code findByUserAndId} empty → 抛 1003）。
 */
public record TaskUpdateRequest(
        @Size(max = TaskConstants.MAX_TITLE_LEN)
        String title,

        @Min(TaskConstants.MIN_STATUS)
        Integer status,

        @Min(0)
        @Max(TaskConstants.MAX_PRIORITY)
        Integer priority,

        @PastOrPresent
        LocalDate dueDate,

        @Size(max = TaskConstants.MAX_TAG_LEN)
        String tag,

        Long planId
) {
}