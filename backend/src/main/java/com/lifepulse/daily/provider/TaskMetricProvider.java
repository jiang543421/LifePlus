package com.lifepulse.daily.provider;

import com.lifepulse.daily.TaskMetrics;
import com.lifepulse.task.TaskConstants;
import com.lifepulse.task.repository.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 数据源的日指标聚合器（plan §5 T3）。
 *
 * <p>调用时机：{@link com.lifepulse.daily.service.DailyReportService}
 * 处理 {@code GET /api/daily} 时按用户与目标日单次调用；周报循环 7 次。
 *
 * <p><b>v1.2.3 完成判定语义</b>：{@code status = DONE} 且 {@code due_date} 落在目标日。
 * 不依赖 {@code completed_at}（V2 schema 不存在该列）；语义偏差详见
 * {@link TaskMetrics} JavaDoc。
 *
 * <p>构造器显式注入（与 {@code TaskService} 同款），便于单测手写 mock。
 */
@Component
public class TaskMetricProvider implements MetricProvider<TaskMetrics> {

    private static final Logger log = LoggerFactory.getLogger(TaskMetricProvider.class);

    private final TaskMapper taskMapper;

    public TaskMetricProvider(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 聚合单日任务指标。
     *
     * <p>任何子查询异常一律向上抛（Mapper 异常已足够明确），不做兜底零值掩盖
     * 真实错误。空数据集返回全零结构 + 三桶零值 status/priority map。
     *
     * @param userId 已鉴权用户 ID（越权拦截在 Service 层）
     * @param date   目标日（Asia/Shanghai 边界已由 Service 处理）
     * @return 单日任务指标；空数据时 completionRate = 0.0
     */
    @Override
    public TaskMetrics aggregateDaily(long userId, LocalDate date) {
        long total = taskMapper.countByUserDueBetween(userId, date, date);
        long completed = taskMapper.countCompletedByUserDueBetween(
                userId, date, date, TaskConstants.STATUS_DONE);
        double rate = total == 0L ? 0.0 : (double) completed / (double) total;

        Map<String, Long> statusDist = toDistribution(
                taskMapper.selectStatusBucketsByUserDueBetween(userId, date, date),
                STATUS_LABELS);
        Map<String, Long> priorityDist = toDistribution(
                taskMapper.selectPriorityBucketsByUserDueBetween(userId, date, date),
                PRIORITY_LABELS);

        log.debug("TaskMetricProvider user={} date={} total={} done={} rate={}",
                userId, date, total, completed, rate);

        return new TaskMetrics(completed, total, rate, statusDist, priorityDist);
    }

    /**
     * 把 mapper 返回的 {@code List<Map<String, Object>>}（key: "bucket" / "cnt"）
     * 转成 {@code Map<String, Long>}（key: 字面值标签）。
     *
     * <p>未出现的 bucket 填 0，保证前端按固定 key 渲染不缺位。
     */
    private static Map<String, Long> toDistribution(
            List<Map<String, Object>> rows,
            Map<Integer, String> labelByCode) {
        Map<String, Long> result = new HashMap<>(labelByCode.size());
        // 先按已知 code 填 0，确保返回 map 包含所有预期 key
        for (Map.Entry<Integer, String> e : labelByCode.entrySet()) {
            result.put(e.getValue(), 0L);
        }
        // 覆盖实际出现 bucket 的真实计数
        for (Map<String, Object> row : rows) {
            int bucket = ((Number) row.get("bucket")).intValue();
            long cnt = ((Number) row.get("cnt")).longValue();
            String label = labelByCode.get(bucket);
            if (label != null) {
                result.put(label, cnt);
            }
            // label == null: 数据库有未知 code，防御性跳过；记录留待日志层另议
        }
        return Map.copyOf(result);
    }

    // ---- 状态 / 优先级字面值映射 ----
    // 与 TaskConstants 数值约定一致；新增 code 务必同步 TaskConstants + 此表。

    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            TaskConstants.STATUS_TODO, "TODO",
            TaskConstants.STATUS_DONE, "DONE",
            TaskConstants.STATUS_CANCELLED, "CANCELLED"
    );

    private static final Map<Integer, String> PRIORITY_LABELS = Map.of(
            TaskConstants.PRIORITY_NONE, "NONE",
            TaskConstants.PRIORITY_LOW, "LOW",
            TaskConstants.PRIORITY_MEDIUM, "MEDIUM",
            TaskConstants.PRIORITY_HIGH, "HIGH"
    );
}