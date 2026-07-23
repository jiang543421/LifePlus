package com.lifepulse.ai.service;

import com.lifepulse.ai.web.dto.AiTrendResponse;
import com.lifepulse.ai.web.dto.MetricPointDto;
import com.lifepulse.ai.web.dto.MetricSeriesDto;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.daily.DailyConstants;
import com.lifepulse.daily.DailyReportPayload;
import com.lifepulse.daily.ExpenseMetrics;
import com.lifepulse.daily.PlanMetrics;
import com.lifepulse.daily.TaskMetrics;
import com.lifepulse.daily.service.DailyReportService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * AI 趋势图聚合服务（spec §v2.2 trend / CLAUDE.md §11.1 无新表）。
 *
 * <p><b>职责</b>：在 {@code [today - window + 1, today]} 范围内逐日调用
 * {@link DailyReportService#daily(long, LocalDate)}，复用既有 4 个
 * MetricProvider 聚合出 task / plan / expense 三个时间序列；diet 槽位
 * 永久空数组（CLAUDE.md §1 NOT-DO）。
 *
 * <p><b>无新表 / 无新列</b>：本服务只读 {@code t_task} / {@code t_plan} /
 * {@code t_expense} / {@code t_diet}，由 DailyReportService 内部聚合。
 *
 * <p><b>窗口约束</b>：{@code windowDays} 必须 ∈ {@code [1, MAX_HISTORY_DAYS=30]}。
 * 越界抛 {@link ErrorCode#VALIDATION}（1001）。Controller 层负责把
 * {@code ?window=7|14|30} 映射到具体 int；Service 不限制具体档位。
 *
 * <p><b>性能预算</b>：N × 4 次 mapper 调用（N = windowDays）。
 * <ul>
 *   <li>7 天 = 28 次 mapper 调用</li>
 *   <li>14 天 = 56 次 mapper 调用（默认）</li>
 *   <li>30 天 = 120 次 mapper 调用</li>
 * </ul>
 * 由 IT 验证 P95 ≤ 800ms（{@code DAILY_P95_BUDGET × 30/14} 余量），本批次不写
 * 性能 IT（沿用 {@code DailyReportServiceIT} 的既有 P95 ≤ 200ms 单日基线）。
 *
 * <p><b>构造器</b>：显式注入 {@link DailyReportService}（CLAUDE.md java/patterns）。
 */
@Service
public class AiTrendService {

    /** 指标 key 常量（与 DTO FIXED_METRICS 顺序一致；本类不导出避免双源）。 */
    static final String KEY_TASK = "task";
    static final String KEY_PLAN = "plan";
    static final String KEY_EXPENSE = "expense";
    static final String KEY_DIET = "diet";

    /** 展示文案常量。 */
    private static final String LABEL_TASK = "任务完成率";
    private static final String LABEL_PLAN = "日程事件";
    private static final String LABEL_EXPENSE = "消费金额";

    /** 单位常量。 */
    private static final String UNIT_PERCENT = "%";
    private static final String UNIT_EVENT = "项";
    private static final String UNIT_YUAN = "¥";

    /** 金额保留 2 位小数（与 {@code ExpenseMetrics.totalAmount} 一致）。 */
    private static final int AMOUNT_SCALE = 2;

    private final DailyReportService dailyReportService;

    public AiTrendService(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    /**
     * 聚合 {@code windowDays} 天的趋势数据。
     *
     * @param userId     当前用户（已鉴权）
     * @param windowDays 时间窗天数，1 ≤ windowDays ≤ MAX_HISTORY_DAYS
     * @return 趋势响应（4 槽 series，其中 diet 为空）
     * @throws BusinessException 1001 当 windowDays 越界
     */
    public AiTrendResponse range(long userId, int windowDays) {
        validateWindow(windowDays);

        LocalDate today = dailyReportService.today();
        LocalDate to = today;
        LocalDate from = today.minusDays(windowDays - 1L);

        // 按日期升序填点；用 LinkedHashMap 保 series 插入顺序便于前端 for-of 渲染
        List<MetricPointDto> taskPoints = new ArrayList<>(windowDays);
        List<MetricPointDto> planPoints = new ArrayList<>(windowDays);
        List<MetricPointDto> expensePoints = new ArrayList<>(windowDays);

        for (long offset = 0; offset < windowDays; offset++) {
            LocalDate d = from.plusDays(offset);
            DailyReportPayload payload = dailyReportService.daily(userId, d);

            taskPoints.add(toTaskPoint(d, payload.task()));
            planPoints.add(toPlanPoint(d, payload.plan()));
            expensePoints.add(toExpensePoint(d, payload.expense()));
        }

        Map<String, MetricSeriesDto> series = new LinkedHashMap<>(4);
        series.put(KEY_TASK, new MetricSeriesDto(KEY_TASK, LABEL_TASK, UNIT_PERCENT, taskPoints));
        series.put(KEY_PLAN, new MetricSeriesDto(KEY_PLAN, LABEL_PLAN, UNIT_EVENT, planPoints));
        series.put(KEY_EXPENSE, new MetricSeriesDto(KEY_EXPENSE, LABEL_EXPENSE, UNIT_YUAN, expensePoints));
        series.put(KEY_DIET, MetricSeriesDto.dietPlaceholder());

        return new AiTrendResponse(
                windowDays,
                from,
                to,
                AiTrendResponse.FIXED_METRICS,
                series,
                Instant.now());
    }

    /** 校验 windowDays 范围（包络，不允许 0 / 负 / 超 MAX_HISTORY_DAYS）。 */
    private static void validateWindow(int windowDays) {
        if (windowDays < 1 || windowDays > DailyConstants.MAX_HISTORY_DAYS) {
            throw new BusinessException(
                    ErrorCode.VALIDATION,
                    "window out of range: " + windowDays
                            + " (must be in [1, " + DailyConstants.MAX_HISTORY_DAYS + "])");
        }
    }

    /** task 单点：value = completionRate（0.0-1.0），label = "85%"。 */
    private static MetricPointDto toTaskPoint(LocalDate d, TaskMetrics t) {
        long pct = Math.round(t.completionRate() * 100.0);
        return new MetricPointDto(d, t.completionRate(), pct + UNIT_PERCENT);
    }

    /** plan 单点：value = eventCount（整数），label = "3 项"。 */
    private static MetricPointDto toPlanPoint(LocalDate d, PlanMetrics p) {
        long count = p.eventCount();
        return new MetricPointDto(d, (double) count, count + UNIT_EVENT);
    }

    /** expense 单点：value = totalAmount（float 形式），label = "¥420.00"。 */
    private static MetricPointDto toExpensePoint(LocalDate d, ExpenseMetrics e) {
        BigDecimal amount = e.totalAmount();
        double v = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP).doubleValue();
        return new MetricPointDto(d, v, UNIT_YUAN + amount.toPlainString());
    }
}