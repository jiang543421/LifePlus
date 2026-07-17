package com.lifepulse.task.dto;

import com.lifepulse.task.TaskConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 状态切换请求体（spec §5.3 PATCH /tasks/{id}/status）。
 *
 * <p>仅含 {@code status}，取值范围由 {@link TaskConstants#MIN_STATUS}
 * / {@link TaskConstants#MAX_STATUS} 限定。
 */
public record TaskStatusRequest(
        @NotNull
        @Min(TaskConstants.MIN_STATUS)
        @Max(TaskConstants.MAX_STATUS)
        Integer status
) {
}