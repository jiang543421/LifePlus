package com.lifepulse.task.dto;

import com.lifepulse.task.entity.Task;

import java.time.LocalDate;

/**
 * 任务列表项（spec §5.3 GET /tasks、/by-plan/{planId}）。
 *
 * <p>精简字段（无 {@code userId}/{@code createdAt}/{@code updatedAt}），
 * 列表场景下减少网络 payload；详情场景用 {@link TaskResponse}。
 */
public record TaskListItem(
        Long id,
        String title,
        Integer status,
        Integer priority,
        LocalDate dueDate,
        String tag
) {
    public static TaskListItem from(Task t) {
        return new TaskListItem(
                t.getId(),
                t.getTitle(),
                t.getStatus(),
                t.getPriority(),
                t.getDueDate(),
                t.getTag()
        );
    }
}