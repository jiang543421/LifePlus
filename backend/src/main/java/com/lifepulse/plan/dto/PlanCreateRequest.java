package com.lifepulse.plan.dto;

import com.lifepulse.plan.PlanConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建计划请求体（spec §5.4 POST /plans）。
 *
 * <p>{@code title} 必填且长度受限；{@code startTime}/{@code endTime} 必填；
 * {@code endTime > startTime} 跨字段校验由 service 层完成（spec §2.3）。
 * {@code allDay} 为 {@code 0/1}，{@code null} → DB DEFAULT 0；
 * {@code reminderMin} 为 {@code null} → DB DEFAULT 15（占位字段，MVP1 不实现推送）。
 *
 * <p>{@code LocalDateTime} 是无时区列的承载类型（spec §2.3 "DATETIME 不带时区"）；
 * 序列化约定 ISO-8601 无 offset 字符串，前端 dayjs TZ Asia/Shanghai 解释。
 */
public record PlanCreateRequest(
        @NotBlank
        @Size(max = PlanConstants.MAX_TITLE_LEN)
        String title,

        @NotNull
        LocalDateTime startTime,

        @NotNull
        LocalDateTime endTime,

        @Min(0)
        @Max(1)
        Integer allDay,

        @Size(max = PlanConstants.MAX_LOCATION_LEN)
        String location,

        @Size(max = PlanConstants.MAX_NOTE_LEN)
        String note,

        @Min(0)
        @Max(PlanConstants.MAX_REMINDER_MIN)
        Integer reminderMin
) {
}