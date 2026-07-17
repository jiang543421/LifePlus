package com.lifepulse.task.dto;

import com.lifepulse.task.entity.Task;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 任务详情响应（spec §5.3 GET /tasks/{id}、POST /tasks）。
 *
 * <p>字段顺序与实体一致；{@code from(Task)} 在 service 层调用，避免 DTO 反向依赖 mapper。
 */
public record TaskResponse(
        Long id,
        Long userId,
        Long planId,
        String title,
        Integer status,
        Integer priority,
        LocalDate dueDate,
        String tag,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(),
                t.getUserId(),
                t.getPlanId(),
                t.getTitle(),
                t.getStatus(),
                t.getPriority(),
                t.getDueDate(),
                t.getTag(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}