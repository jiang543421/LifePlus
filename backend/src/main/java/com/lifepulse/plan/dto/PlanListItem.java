package com.lifepulse.plan.dto;

import com.lifepulse.plan.entity.Plan;

import java.time.LocalDateTime;

/**
 * 计划列表项（spec §5.4 GET /plans）。
 *
 * <p>保留 {@code startTime/endTime} 以供 CalendarMonth 渲染跨日事件 marker；
 * 精简字段（无 {@code userId}/{@code createdAt}/{@code updatedAt}/{@code note}），
 * 列表场景下减少网络 payload；详情场景用 {@link PlanResponse}。
 *
 * <p>{@code note} 在列表场景下省略——日历网格对 note 信息密度低；
 * 若产品后续需要在 hover 弹窗中显示，可在不破坏契约前提下补字段。
 */
public record PlanListItem(
        Long id,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer allDay,
        String location,
        Integer reminderMin
) {
    public static PlanListItem from(Plan p) {
        return new PlanListItem(
                p.getId(),
                p.getTitle(),
                p.getStartTime(),
                p.getEndTime(),
                p.getAllDay(),
                p.getLocation(),
                p.getReminderMin()
        );
    }
}