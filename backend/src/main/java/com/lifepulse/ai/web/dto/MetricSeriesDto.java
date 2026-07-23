package com.lifepulse.ai.web.dto;

import java.util.List;

/**
 * 单个指标的时间序列（spec §v2.2 trend）。
 *
 * <p>前端 {@code TrendPanel} 按 {@code key} 路由到对应 sparkline 槽位：
 * <ul>
 *   <li>{@code "task"} — 任务完成率（%）/ 双轴 0-1 归一化</li>
 *   <li>{@code "plan"} — 日程事件数（项）/ 整数</li>
 *   <li>{@code "expense"} — 消费金额（¥）/ 金额</li>
 *   <li>{@code "diet"} — 永久空列表（CLAUDE.md §1 NOT-DO：{@code DietMetrics.enabled=false}）</li>
 * </ul>
 *
 * @param key    指标 key，与前端常量对齐
 * @param label  中文展示名（"任务完成率" / "日程事件" / "消费金额" / "饮食（永久占位）"）
 * @param unit   单位字符串（"%" / "项" / "¥" / ""），sparkline tooltip 末尾拼接
 * @param points 按日期升序的时间序列点；diet 永远为空列表
 */
public record MetricSeriesDto(
        String key,
        String label,
        String unit,
        List<MetricPointDto> points
) {
    public MetricSeriesDto {
        // 防御性拷贝（CLAUDE.md §4.1 不可变性）
        points = points == null ? List.of() : List.copyOf(points);
    }

    /** DietMetrics 占位系列（永久空数组 + 灰色 UI 占位）。 */
    public static MetricSeriesDto dietPlaceholder() {
        return new MetricSeriesDto("diet", "饮食（永久占位）", "", List.of());
    }
}