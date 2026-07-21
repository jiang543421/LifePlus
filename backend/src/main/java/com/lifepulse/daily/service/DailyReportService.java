package com.lifepulse.daily.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.daily.DailyConstants;
import com.lifepulse.daily.DailyReportPayload;
import com.lifepulse.daily.DietMetrics;
import com.lifepulse.daily.ExpenseMetrics;
import com.lifepulse.daily.PlanMetrics;
import com.lifepulse.daily.TaskMetrics;
import com.lifepulse.daily.WeeklyComparison;
import com.lifepulse.daily.WeeklyReportPayload;
import com.lifepulse.daily.provider.DietMetricProvider;
import com.lifepulse.daily.provider.ExpenseMetricProvider;
import com.lifepulse.daily.provider.MetricProvider;
import com.lifepulse.daily.provider.PlanMetricProvider;
import com.lifepulse.daily.provider.TaskMetricProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * 日报 / 周报编排服务（plan §5 T6 / spec §5 GET /api/daily 与 /api/daily/week）。
 *
 * <p><b>职责</b>：串联 4 个 {@link MetricProvider}，做日期范围校验与 ISO 周处理。
 * 不做鉴权（Controller 用 {@code UserContext.current()} 取 userId 后传入），
 * 不做缓存（v1.2.3 实时聚合，1w 行种子 P95 < 200ms 由 IT T8 验证）。
 *
 * <p><b>日期约束</b>：{@code daily} 与 {@code week} 都校验
 * {@link DailyConstants#MAX_HISTORY_DAYS}（30 天）回溯上限。
 * {@code date < today - 30} 抛 {@link BusinessException}{@code (ErrorCode.VALIDATION, ...)}
 * 即 1001。未来日期允许（不影响聚合性能，逻辑也对称）。
 *
 * <p><b>ISO 周定义</b>：{@link WeekFields#ISO} = {@code (MONDAY, 4)}，
 * 周一为周首，跨年周至少 4 天属于该年才计入。例如 {@code 2026-W01} 起始
 * 为 {@code 2025-12-29}（周一）而非常规的 {@code 2026-01-01}。
 *
 * <p><b>对比 delta = null 的语义</b>：上周对应值为 0 时，{@link WeeklyComparison.WeeklyTriplet#delta()}
 * 返回 {@code null}，避免除零与符号歧义（"下降 100%" vs "下降 0"）。
 *
 * <p>构造器显式注入（与 {@code TaskService} 同款）。
 */
@Service
public class DailyReportService {

    private static final Logger log = LoggerFactory.getLogger(DailyReportService.class);

    /** ISO 周字段（周一为周首，minDays=4 处理跨年周）。 */
    private static final WeekFields ISO_WEEK = WeekFields.ISO;

    private final TaskMetricProvider taskProvider;
    private final PlanMetricProvider planProvider;
    private final ExpenseMetricProvider expenseProvider;
    private final DietMetricProvider dietProvider;

    public DailyReportService(TaskMetricProvider taskProvider,
                              PlanMetricProvider planProvider,
                              ExpenseMetricProvider expenseProvider,
                              DietMetricProvider dietProvider) {
        this.taskProvider = taskProvider;
        this.planProvider = planProvider;
        this.expenseProvider = expenseProvider;
        this.dietProvider = dietProvider;
    }

    /**
     * 聚合单日日报。
     *
     * @param userId 当前用户（已鉴权，Controller 负责跨用户防御）
     * @param date   目标日（Asia/Shanghai 本地日，未做时区转换——Controller DTO 已校验）
     * @return 日报 payload
     * @throws BusinessException 1001 当 {@code date} 超出 {@link DailyConstants#MAX_HISTORY_DAYS} 窗口
     */
    public DailyReportPayload daily(long userId, LocalDate date) {
        validateInWindow(date);

        TaskMetrics task = taskProvider.aggregateDaily(userId, date);
        PlanMetrics plan = planProvider.aggregateDaily(userId, date);
        ExpenseMetrics expense = expenseProvider.aggregateDaily(userId, date);
        DietMetrics diet = dietProvider.aggregateDaily(userId, date);

        log.debug("daily user={} date={} done={}/{} events={} amount={} diet.enabled={}",
                userId, date, task.completedCount(), task.totalCount(),
                plan.eventCount(), expense.totalAmount(), diet.enabled());

        return new DailyReportPayload(date, task, plan, expense, diet);
    }

    /**
     * 聚合周报（含与上周对比）。
     *
     * <p>{@code anyDayInWeek} 可为该周任意一天（ISO 8601 周一为首、周日为末，
     * 故周日属于该 ISO 周的最后一天，对齐后 {@code weekStart} 为上周一）。
     *
     * <p>7 天逐日聚合 → 派生对比：
     * <ul>
     *   <li>taskCompletion: currentWeek 平均完成率 vs previousWeek 平均完成率</li>
     *   <li>planEvents: 本周事件总数 vs 上周事件总数</li>
     *   <li>expenseAmount: 本周消费总额 vs 上周消费总额</li>
     * </ul>
     *
     * @throws BusinessException 1001 当 {@code weekEnd} 超出 {@link DailyConstants#MAX_HISTORY_DAYS} 窗口
     */
    public WeeklyReportPayload week(long userId, LocalDate anyDayInWeek) {
        LocalDate weekStart = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate prevWeekStart = weekStart.minusDays(7);
        LocalDate prevWeekEnd = prevWeekStart.plusDays(6);

        // 校验整周（含上周对比的过去回溯）：任一天超出 30 天窗口即拒
        validateInWindow(weekEnd);
        validateInWindow(prevWeekEnd);

        WeeklyTotals current = aggregateWeek(userId, weekStart);
        WeeklyTotals previous = aggregateWeek(userId, prevWeekStart);

        String isoWeek = formatIsoWeek(weekStart);

        WeeklyComparison comparison = new WeeklyComparison(
                taskTriplet(current, previous),
                planTriplet(current, previous),
                expenseTriplet(current, previous));

        log.debug("week user={} week={} ({}~{}) currentDone={} prevDone={} currentAmt={} prevAmt={}",
                userId, isoWeek, weekStart, weekEnd,
                current.totalCompleted(), previous.totalCompleted(),
                current.amountSum(), previous.amountSum());

        return new WeeklyReportPayload(isoWeek, weekStart, weekEnd, comparison);
    }

    // ---- 内部辅助 ----

    /**
     * 校验目标日期是否在 {@link DailyConstants#MAX_HISTORY_DAYS} 窗口内。
     *
     * <p>"今天"以 {@link DailyConstants#ZONE}（Asia/Shanghai）的当前日计算。
     * 测试时可由 {@link DailyReportService#today()} 包覆注入（见 package-private 重载）。
     */
    private void validateInWindow(LocalDate date) {
        validateInWindow(date, today());
    }

    /** 同上但接受显式 today 参数，便于单测注入固定日期。 */
    void validateInWindow(LocalDate date, LocalDate today) {
        LocalDate earliest = today.minusDays(DailyConstants.MAX_HISTORY_DAYS);
        if (date.isBefore(earliest)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION,
                    "date out of range: " + date + " earlier than " + earliest);
        }
    }

    /** 暴露 today() 以便单测 override（package-private 单测 + Controller 显式调用）。 */
    public LocalDate today() {
        return LocalDate.now(DailyConstants.ZONE);
    }

    /**
     * 聚合 7 天每日指标，返回汇总值（用于周报对比）。
     *
     * <p>7 次单日 Provider 调用 = 7 × 4 = 28 次 mapper 调用；
     * T8 IT 验证在 1w 行种子下整体 P95 < 300ms（{@link DailyConstants#WEEKLY_P95_BUDGET}）。
     */
    private WeeklyTotals aggregateWeek(long userId, LocalDate weekStart) {
        long completedSum = 0L;
        long totalSum = 0L;
        long eventsSum = 0L;
        BigDecimal amountSum = BigDecimal.ZERO;

        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            TaskMetrics t = taskProvider.aggregateDaily(userId, d);
            PlanMetrics p = planProvider.aggregateDaily(userId, d);
            ExpenseMetrics e = expenseProvider.aggregateDaily(userId, d);

            completedSum += t.completedCount();
            totalSum += t.totalCount();
            eventsSum += p.eventCount();
            amountSum = amountSum.add(e.totalAmount());
        }
        return new WeeklyTotals(completedSum, totalSum, eventsSum, amountSum);
    }

    private static WeeklyComparison.WeeklyTriplet taskTriplet(WeeklyTotals c, WeeklyTotals p) {
        double curRate = c.totalCount() == 0L ? 0.0 : (double) c.totalCompleted() / c.totalCount();
        double prevRate = p.totalCount() == 0L ? 0.0 : (double) p.totalCompleted() / p.totalCount();
        Double delta = (p.totalCount() == 0L) ? null : curRate - prevRate;
        return new WeeklyComparison.WeeklyTriplet(curRate, prevRate, delta);
    }

    private static WeeklyComparison.WeeklyTriplet planTriplet(WeeklyTotals c, WeeklyTotals p) {
        long cur = c.eventsSum();
        long prev = p.eventsSum();
        Double delta = (prev == 0L) ? null : (double) (cur - prev);
        return new WeeklyComparison.WeeklyTriplet((double) cur, (double) prev, delta);
    }

    private static WeeklyComparison.WeeklyTriplet expenseTriplet(WeeklyTotals c, WeeklyTotals p) {
        BigDecimal cur = c.amountSum();
        BigDecimal prev = p.amountSum();
        Double delta = (prev.compareTo(BigDecimal.ZERO) == 0) ? null
                : cur.subtract(prev).doubleValue();
        return new WeeklyComparison.WeeklyTriplet(
                cur.doubleValue(), prev.doubleValue(), delta);
    }

    /**
     * 格式化为 {@code "YYYY-Www"}（如 {@code "2026-W29"}），ISO 周编号。
     *
     * <p>使用 {@link LocalDate#get} + {@link WeekFields#ISO}：
     * <ul>
     *   <li>{@code weekBasedYear()}：跨年周所属年（如 2026-W01 起始 2025-12-29 → 2026）</li>
     *   <li>{@code weekOfWeekBasedYear()}：ISO 周编号 01-53</li>
     * </ul>
     *
     * <p>可见性：package-private static，便于单测直接覆盖跨年逻辑而无需触发
     * {@code validateInWindow} 的 30 天窗口校验。
     */
    static String formatIsoWeek(LocalDate weekStart) {
        int year = weekStart.get(ISO_WEEK.weekBasedYear());
        int week = weekStart.get(ISO_WEEK.weekOfWeekBasedYear());
        return String.format(Locale.ROOT, "%04d-W%02d", year, week);
    }

    /**
     * 7 天汇总值 record（package-private 便于单测直接断言）。
     *
     * @param totalCompleted 7 天已完成任务数之和
     * @param totalCount     7 天总任务数之和
     * @param eventsSum      7 天事件总数
     * @param amountSum      7 天消费总额（BigDecimal 加和保持精度）
     */
    record WeeklyTotals(long totalCompleted,
                        long totalCount,
                        long eventsSum,
                        BigDecimal amountSum) {
    }
}