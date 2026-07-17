package com.lifepulse.plan.dto;

import com.lifepulse.plan.entity.Plan;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 计划详情响应（spec §5.4 GET /plans/{id}、POST /plans）。
 *
 * <p>字段顺序与实体一致；{@code from(Plan)} 在 service 层调用，避免 DTO 反向依赖 mapper。
 */
public record PlanResponse(
        Long id,
        Long userId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer allDay,
        String location,
        String note,
        Integer reminderMin,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PlanResponse from(Plan p) {
        return new PlanResponse(
                p.getId(),
                p.getUserId(),
                p.getTitle(),
                p.getStartTime(),
                p.getEndTime(),
                p.getAllDay(),
                p.getLocation(),
                p.getNote(),
                p.getReminderMin(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}