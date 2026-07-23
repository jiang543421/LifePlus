package com.lifepulse.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.daily.DailyReportPayload;
import com.lifepulse.daily.DietMetrics;
import com.lifepulse.daily.ExpenseMetrics;
import com.lifepulse.daily.PlanMetrics;
import com.lifepulse.daily.TaskMetrics;
import com.lifepulse.daily.service.DailyReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AiTrendService 单元测试（spec §v2.2 trend）。
 *
 * <p>每个 case 一个独立 commit，便于 review 拆解。
 */
@ExtendWith(MockitoExtension.class)
class AiTrendServiceTest {

    @Mock
    private DailyReportService dailyReportService;

    private AiTrendService service;

    @BeforeEach
    void setUp() {
        service = new AiTrendService(dailyReportService);
    }

    /**
     * Case 1：窗口越界（&lt; 1 或 &gt; MAX_HISTORY_DAYS=30）→ 1001 VALIDATION。
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 31, 100, Integer.MIN_VALUE})
    void range_windowOutOfRange_throws1001(int windowDays) {
        assertThatThrownBy(() -> service.range(1L, windowDays))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    /**
     * Case 2：14 天 happy path — 应触发 14 次 DailyReportService.daily() 调用，
     * 3 个有数据 series 各 14 个点（升序），diet 永远空数组。
     */
    @Test
    void range_14Days_aggregates14PointsPerSeries_andCallsDaily14Times() {
        LocalDate today = LocalDate.of(2026, 7, 23);
        when(dailyReportService.today()).thenReturn(today);
        when(dailyReportService.daily(anyLong(), any()))
                .thenReturn(stubPayload(0.8, 4L, new BigDecimal("100.00")));

        var resp = service.range(1L, 14);

        verify(dailyReportService, times(14)).daily(eq(1L), any());
        assertThat(resp.window()).isEqualTo(14);
        assertThat(resp.from()).isEqualTo(today.minusDays(13));
        assertThat(resp.to()).isEqualTo(today);
        assertThat(resp.series().get("task").points()).hasSize(14);
        assertThat(resp.series().get("plan").points()).hasSize(14);
        assertThat(resp.series().get("expense").points()).hasSize(14);
        assertThat(resp.series().get("diet").points()).isEmpty();
        assertThat(resp.metrics()).containsExactly("task", "plan", "expense", "diet");
        // 升序：from < to
        assertThat(resp.series().get("task").points().get(0).date())
                .isBefore(resp.series().get("task").points().get(13).date());
    }

    /**
     * Case 3：7 天与 30 天窗口 — 验证 mapper 调用次数 + from/to 计算。
     * 30 天贴 MAX_HISTORY_DAYS 上限，应允许通过。
     */
    @ParameterizedTest
    @ValueSource(ints = {7, 30})
    void range_windowBoundaries_callsDailyCorrectTimes(int windowDays) {
        LocalDate today = LocalDate.of(2026, 7, 23);
        when(dailyReportService.today()).thenReturn(today);
        when(dailyReportService.daily(anyLong(), any()))
                .thenReturn(stubPayload(0.5, 2L, new BigDecimal("50.00")));

        var resp = service.range(1L, windowDays);

        verify(dailyReportService, times(windowDays)).daily(eq(1L), any());
        assertThat(resp.from()).isEqualTo(today.minusDays(windowDays - 1L));
        assertThat(resp.to()).isEqualTo(today);
        assertThat(resp.window()).isEqualTo(windowDays);
        assertThat(resp.series().get("task").points().get(0).date()).isEqualTo(resp.from());
        assertThat(resp.series().get("task").points().get(windowDays - 1).date()).isEqualTo(resp.to());
    }

    /**
     * Case 4：diet 占位独立性 — 即便 stub 返回带 enabled=true 的 DietMetrics，
     * 响应里 diet 槽位永远空数组 + 占位 label（CLAUDE.md §1 NOT-DO）。
     */
    @Test
    void range_dietSlotAlwaysEmpty_andLabelIsPlaceholder_regardlessOfStub() {
        LocalDate today = LocalDate.of(2026, 7, 23);
        when(dailyReportService.today()).thenReturn(today);
        // 即使 DietMetrics.enabled=true 也不应被采纳
        DietMetrics enabledDiet = new DietMetrics(true, null, "ignored");
        when(dailyReportService.daily(anyLong(), any()))
                .thenReturn(new DailyReportPayload(
                        LocalDate.of(2026, 7, 20),
                        new TaskMetrics(5, 10, 0.5, Map.of(), Map.of()),
                        new PlanMetrics(2L, 60L, Map.of(), 5),
                        new ExpenseMetrics(new BigDecimal("10.00"), 1L, Map.of(), List.of()),
                        enabledDiet));

        var resp = service.range(1L, 14);

        // diet 槽位存在但永远空 + 占位 label
        assertThat(resp.series()).containsKey("diet");
        assertThat(resp.series().get("diet").points()).isEmpty();
        assertThat(resp.series().get("diet").label()).isEqualTo("饮食（永久占位）");
        assertThat(resp.series().get("diet").key()).isEqualTo("diet");
        // 其它 3 个 series 仍正常填点
        assertThat(resp.series().get("task").points()).hasSize(14);
    }

    /**
     * Case 5：task 字段映射 — value=completionRate（原始 double），label="{pct}%"，
     * date 与 offset 对齐。
     */
    @Test
    void range_taskSeries_mapsCompletionRateToValueAndPercentLabel() {
        LocalDate today = LocalDate.of(2026, 7, 23);
        when(dailyReportService.today()).thenReturn(today);
        // 第一天 0.0 / 第二天 0.85 / 第三天 1.0
        double[] rates = {0.0, 0.85, 1.0};
        java.util.concurrent.atomic.AtomicInteger callIdx = new java.util.concurrent.atomic.AtomicInteger();
        when(dailyReportService.daily(anyLong(), any()))
                .thenAnswer(inv -> stubPayload(rates[callIdx.getAndIncrement()],
                        0L, BigDecimal.ZERO));

        var resp = service.range(1L, 3);

        var taskPoints = resp.series().get("task").points();
        assertThat(taskPoints).hasSize(3);
        // 第一天 rate=0.0
        assertThat(taskPoints.get(0).date()).isEqualTo(today.minusDays(2));
        assertThat(taskPoints.get(0).value()).isEqualTo(0.0);
        assertThat(taskPoints.get(0).label()).isEqualTo("0%");
        // 第二天 rate=0.85 → 85%
        assertThat(taskPoints.get(1).value()).isEqualTo(0.85);
        assertThat(taskPoints.get(1).label()).isEqualTo("85%");
        // 第三天 rate=1.0 → 100%
        assertThat(taskPoints.get(2).value()).isEqualTo(1.0);
        assertThat(taskPoints.get(2).label()).isEqualTo("100%");
    }

    /**
     * Case 6：plan 字段映射 — value=eventCount（整数 double），label="{count} 项"，
     * 0 事件也要正常出点。
     */
    @Test
    void range_planSeries_mapsEventCountToValueAndUnitLabel() {
        LocalDate today = LocalDate.of(2026, 7, 23);
        when(dailyReportService.today()).thenReturn(today);
        long[] counts = {0L, 3L, 12L};
        java.util.concurrent.atomic.AtomicInteger callIdx = new java.util.concurrent.atomic.AtomicInteger();
        when(dailyReportService.daily(anyLong(), any()))
                .thenAnswer(inv -> stubPayload(0.0, counts[callIdx.getAndIncrement()],
                        BigDecimal.ZERO));

        var resp = service.range(1L, 3);

        var planPoints = resp.series().get("plan").points();
        assertThat(planPoints).hasSize(3);
        // 0 事件也要有 label，不能跳过
        assertThat(planPoints.get(0).value()).isEqualTo(0.0);
        assertThat(planPoints.get(0).label()).isEqualTo("0项");
        assertThat(planPoints.get(1).value()).isEqualTo(3.0);
        assertThat(planPoints.get(1).label()).isEqualTo("3项");
        assertThat(planPoints.get(2).value()).isEqualTo(12.0);
        assertThat(planPoints.get(2).label()).isEqualTo("12项");
    }

    /**
     * Case 7：expense 字段映射 — value=amount（double，HALF_UP 2 位小数），
     * label="¥{amount.toPlainString}"（保留原始小数位）。
     */
    @Test
    void range_expenseSeries_mapsAmountToValueAndYuanLabel() {
        LocalDate today = LocalDate.of(2026, 7, 23);
        when(dailyReportService.today()).thenReturn(today);
        BigDecimal[] amounts = {
                new BigDecimal("0.00"),
                new BigDecimal("42.50"),
                new BigDecimal("1234.56")
        };
        java.util.concurrent.atomic.AtomicInteger callIdx = new java.util.concurrent.atomic.AtomicInteger();
        when(dailyReportService.daily(anyLong(), any()))
                .thenAnswer(inv -> stubPayload(0.0, 0L, amounts[callIdx.getAndIncrement()]));

        var resp = service.range(1L, 3);

        var expensePoints = resp.series().get("expense").points();
        assertThat(expensePoints).hasSize(3);
        // 0 元也要出点
        assertThat(expensePoints.get(0).value()).isEqualTo(0.00);
        assertThat(expensePoints.get(0).label()).isEqualTo("¥0.00");
        assertThat(expensePoints.get(1).value()).isEqualTo(42.50);
        assertThat(expensePoints.get(1).label()).isEqualTo("¥42.50");
        // 4 位金额：HALF_UP 2 位 → 1234.56
        assertThat(expensePoints.get(2).value()).isEqualTo(1234.56);
        assertThat(expensePoints.get(2).label()).isEqualTo("¥1234.56");
    }

    /** 构造固定指标的 payload（mapper 调用 stub 用）。 */
    private static DailyReportPayload stubPayload(double completionRate,
                                                  long eventCount,
                                                  BigDecimal amount) {
        TaskMetrics task = new TaskMetrics(
                Math.round(completionRate * 10), 10, completionRate,
                Map.of("DONE", 8L, "TODO", 2L), Map.of("MEDIUM", 5L, "LOW", 5L));
        PlanMetrics plan = new PlanMetrics(eventCount, 240L, Map.of(), 14);
        ExpenseMetrics expense = new ExpenseMetrics(amount, 3L,
                Map.of("MEAL", amount, "OTHER", BigDecimal.ZERO), java.util.List.of());
        DietMetrics diet = new DietMetrics(false, null, "diet module disabled (CLAUDE.md §1 NOT-DO)");
        return new DailyReportPayload(LocalDate.of(2026, 7, 20), task, plan, expense, diet);
    }
}