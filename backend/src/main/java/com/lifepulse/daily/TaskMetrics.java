package com.lifepulse.daily;

import java.util.Map;

/**
 * 任务指标（plan §3 DTO / spec §2 数据聚合）。
 *
 * <p>完成数基于 {@code t_task.completed_at} 落在目标日期 00:00–23:59（Asia/Shanghai）
 * 且 status = DONE（约定 1）的记录。状态 / 优先级分布映射键为字面值字符串
 * （"TODO" / "DONE" / "CANCELLED" / "NONE" / "LOW" / "MEDIUM" / "HIGH"），
 * 供前端 el-tag 渲染。
 */
public record TaskMetrics(
        long completedCount,
        long totalCount,
        double completionRate,
        Map<String, Long> statusDistribution,
        Map<String, Long> priorityDistribution
) {
}
