package com.lifepulse.daily;

import java.util.Map;

/**
 * 任务指标（plan §3 DTO / spec §2 数据聚合）。
 *
 * <p><b>v1.2.3 完成判定语义</b>：{@code completedCount} 统计 {@code status = DONE}
 * 且 {@code due_date} 落在目标日期的记录数。{@code totalCount} 统计该日所有
 * due task（任意 status）。{@code completionRate = completed / total}，
 * {@code total = 0} 时为 0.0。
 *
 * <p>状态 / 优先级分布映射键为字面值字符串
 * （"TODO" / "DONE" / "CANCELLED" / "NONE" / "LOW" / "MEDIUM" / "HIGH"），
 * 供前端 el-tag 渲染。空状态的 bucket 仍以 0 填充（Provider 责任）。
 *
 * <p><b>语义偏差说明</b>：v1.2.3 不依赖 {@code completed_at} 列（V2 schema 不存在），
 * 使用 {@code status + due_date} 近似。若未来引入精确完成时间维度（V7+ 加列），
 * 本 record 字段顺序 / 名称不变，只调 Provider 实现。
 */
public record TaskMetrics(
        long completedCount,
        long totalCount,
        double completionRate,
        Map<String, Long> statusDistribution,
        Map<String, Long> priorityDistribution
) {
}
