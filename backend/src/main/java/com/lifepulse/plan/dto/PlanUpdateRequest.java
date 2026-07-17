package com.lifepulse.plan.dto;

import com.lifepulse.plan.PlanConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 更新计划请求体（spec §5.4 PUT /plans/{id}）。
 *
 * <p>所有字段可选 — 服务端用 {@code null} 判定跳过；{@code title=null} 保留原值，
 * 显式 {@code ""}（空串）也保留原值（{@code @Size} 仅约束非空输入）。
 * 鉴权 1003 防御在 service 层完成（{@code findByUserAndId} empty → 抛 1003）。
 *
 * <p>若 {@code allDay} 设置为 {@code 1}，service 层会归一化
 * {@code startTime/endTime} 到当日 00:00:00 / 23:59:59（CLAUDE.md Phase 3 决策）。
 */
public record PlanUpdateRequest(
        @Size(max = PlanConstants.MAX_TITLE_LEN)
        String title,

        LocalDateTime startTime,

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