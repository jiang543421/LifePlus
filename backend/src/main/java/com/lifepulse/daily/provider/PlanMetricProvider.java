package com.lifepulse.daily.provider;

import com.lifepulse.daily.PlanMetrics;
import com.lifepulse.plan.repository.PlanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Plan 数据源的日指标聚合器（plan §5 T3）。
 *
 * <p>调用时机与 Task 同；详见 {@link TaskMetricProvider}。
 *
 * <p><b>day 范围约定</b>：{@code start_time} 为 DATETIME 无时区信息
 * （V3 schema 约定），按"Asia/Shanghai 墙钟时间"落在目标日的 00:00–23:59。
 * 数据库存的是不带时区的本地时间字面值，应用层直接用
 * {@code date.atStartOfDay()} / {@code date.plusDays(1).atStartOfDay()} 即可。
 *
 * <p><b>categoryDistribution 当前永远返回空 Map</b>：MVP1 t_plan 无 category 列
 * （spec §2.3 未定义）。{@link PlanMetrics} 该字段为前瞻预留。
 *
 * <p>构造器显式注入（与 {@code TaskService} 同款），便于单测手写 mock。
 */
@Component
public class PlanMetricProvider implements MetricProvider<PlanMetrics> {

    private static final Logger log = LoggerFactory.getLogger(PlanMetricProvider.class);

    private final PlanMapper planMapper;

    public PlanMetricProvider(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    @Override
    public PlanMetrics aggregateDaily(long userId, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime nextDayStart = date.plusDays(1).atStartOfDay();

        long eventCount = planMapper.countByUserOnDay(userId, dayStart, nextDayStart);
        long activeMinutes = planMapper.sumActiveMinutesByUserOnDay(userId, dayStart, nextDayStart);
        Integer busiestHour = busiestHourOf(
                planMapper.selectHourBucketsByUserOnDay(userId, dayStart, nextDayStart));

        log.debug("PlanMetricProvider user={} date={} events={} minutes={} busiest={}",
                userId, date, eventCount, activeMinutes, busiestHour);

        return new PlanMetrics(eventCount, activeMinutes, Map.of(), busiestHour);
    }

    /**
     * 从 {@code ORDER BY cnt DESC, bucket ASC} 的 hour bucket 列表取第一个的 bucket。
     *
     * <p>空列表返回 {@code null}（表示"今日无事件"）。
     */
    private static Integer busiestHourOf(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> top = rows.get(0);
        return ((Number) top.get("bucket")).intValue();
    }
}