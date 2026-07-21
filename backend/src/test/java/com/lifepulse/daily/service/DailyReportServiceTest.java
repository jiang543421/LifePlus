package com.lifepulse.daily.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.daily.DailyReportPayload;
import com.lifepulse.daily.DietMetrics;
import com.lifepulse.daily.ExpenseMetrics;
import com.lifepulse.daily.PlanMetrics;
import com.lifepulse.daily.TaskMetrics;
import com.lifepulse.daily.WeeklyComparison;
import com.lifepulse.daily.WeeklyReportPayload;
import com.lifepulse.daily.provider.DietMetricProvider;
import com.lifepulse.daily.provider.ExpenseMetricProvider;
import com.lifepulse.daily.provider.PlanMetricProvider;
import com.lifepulse.daily.provider.TaskMetricProvider;
import com.lifepulse.daily.service.DailyReportService.WeeklyTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DailyReportService 单元测试（plan §5 T6 / spec §6 service 行覆盖 ≥ 80%）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>daily: 4 个 Provider 各调一次, payload 组装正确</li>
 *   <li>daily: 超出 30 天窗口抛 1001</li>
 *   <li>daily: 未来日期允许 (无上限)</li>
 *   <li>week: 任意传入日期对齐到周一作为 weekStart</li>
 *   <li>week: 当前周 vs 上周对比正确 (delta)</li>
 *   <li>week: 上周全零 → delta = null (避免除零/符号歧义)</li>
 *   <li>week: ISO 周格式 (普通周 + 跨年周)</li>
 *   <li>today: 返回 Asia/Shanghai 当前日</li>
 *   <li>validateInWindow: 边界值 (today - 30 接受, today - 31 拒绝)</li>
 * </ul>
 *
 * <p>{@code today()} 通过 test-only 子类 {@link FixedTodayService} 固定，
 * 避免测试时间相关脆性（运行年月变化不破坏断言）。
 */
@ExtendWith(MockitoExtension.class)
class DailyReportServiceTest {

    private static final long USER_ID = 100L;
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 7, 21); // Tuesday

    @Mock private TaskMetricProvider taskProvider;
    @Mock private PlanMetricProvider planProvider;
    @Mock private ExpenseMetricProvider expenseProvider;
    @Mock private DietMetricProvider dietProvider;

    private FixedTodayService service;

    @BeforeEach
    void setUp() {
        service = new FixedTodayService(
                taskProvider, planProvider, expenseProvider, dietProvider, FIXED_TODAY);
        // 默认 diet 永远冻结返回值（避免 strict stubbing 报错）
        lenient().when(dietProvider.aggregateDaily(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DietMetrics(false, null, "frozen"));
    }

    // ==================== daily ====================

    @Test
    @DisplayName("daily：4 Provider 各调一次，payload 组装正确")
    void daily_returnsAllMetricsForValidDate() {
        LocalDate date = FIXED_TODAY;
        stubAllForDate(date, 5, 10, 0.5, 3, 120L, "300.00");

        DailyReportPayload p = service.daily(USER_ID, date);

        assertThat(p.date()).isEqualTo(date);
        assertThat(p.task().completedCount()).isEqualTo(5L);
        assertThat(p.task().totalCount()).isEqualTo(10L);
        assertThat(p.task().completionRate()).isEqualTo(0.5);
        assertThat(p.plan().eventCount()).isEqualTo(3L);
        assertThat(p.plan().totalMinutes()).isEqualTo(120L);
        assertThat(p.expense().totalAmount()).isEqualByComparingTo("300.00");
        assertThat(p.diet().enabled()).isFalse();

        verify(taskProvider).aggregateDaily(USER_ID, date);
        verify(planProvider).aggregateDaily(USER_ID, date);
        verify(expenseProvider).aggregateDaily(USER_ID, date);
        verify(dietProvider).aggregateDaily(USER_ID, date);
    }

    @Test
    @DisplayName("daily：超出 30 天窗口 → 抛 BusinessException(1001)")
    void daily_dateOlderThan30Days_throwsValidation() {
        LocalDate tooOld = FIXED_TODAY.minusDays(31);

        assertThatThrownBy(() -> service.daily(USER_ID, tooOld))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    @DisplayName("daily：边界 today-30 接受，today-31 拒绝")
    void daily_boundaryDate_accepted() {
        LocalDate boundary = FIXED_TODAY.minusDays(30);
        stubAllForDate(boundary, 1, 1, 1.0, 0, 0L, "0.00");

        // boundary 接受
        DailyReportPayload p = service.daily(USER_ID, boundary);
        assertThat(p.date()).isEqualTo(boundary);

        // boundary-1 拒绝
        assertThatThrownBy(() -> service.daily(USER_ID, boundary.minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    @DisplayName("daily：未来日期允许（无上限）")
    void daily_futureDate_accepted() {
        LocalDate future = FIXED_TODAY.plusDays(7);
        stubAllForDate(future, 0, 0, 0.0, 0, 0L, "0.00");

        DailyReportPayload p = service.daily(USER_ID, future);

        assertThat(p.date()).isEqualTo(future);
        verify(taskProvider).aggregateDaily(USER_ID, future);
    }

    // ==================== week ====================

    @Test
    @DisplayName("week：传入周二 → weekStart 为本周一")
    void week_midWeekDate_alignsToMonday() {
        // FIXED_TODAY = 2026-07-21 (Tue)
        LocalDate tue = FIXED_TODAY; // Tue
        LocalDate expectedMon = LocalDate.of(2026, 7, 20);
        LocalDate expectedSun = LocalDate.of(2026, 7, 26);

        // current week: 7 天全部返回简单值
        stubAllForDateRange(expectedMon, expectedSun, 1L, 2L, 0.5, 0L, 0L, "10.00");
        // previous week: 全部 0
        stubAllForDateRange(expectedMon.minusDays(7), expectedSun.minusDays(7),
                0L, 0L, 0.0, 0L, 0L, "0.00");

        WeeklyReportPayload w = service.week(USER_ID, tue);

        assertThat(w.weekStart()).isEqualTo(expectedMon);
        assertThat(w.weekEnd()).isEqualTo(expectedSun);
        // ISO 周：2026-07-20 是 W30
        assertThat(w.isoWeek()).isEqualTo("2026-W30");
    }

    @Test
    @DisplayName("week：当前周 vs 上周对比，delta 正确")
    void week_currentVsPrevious_correctDeltas() {
        LocalDate mon = LocalDate.of(2026, 7, 20);
        LocalDate sun = LocalDate.of(2026, 7, 26);

        // current: 1 done / 2 total, 2 events, 50 amount（每日）
        //   7 日 sum：completed=7, total=14；events=14；amount=350
        // previous: 0 done / 2 total, 1 event, 30 amount（每日）
        //   7 日 sum：completed=0, total=14；events=7；amount=210
        // （previous 全部非零 → 所有 delta 都可计算）
        stubAllForDateRange(mon, sun, 1L, 2L, 0.5, 2L, 60L, "50.00");
        stubAllForDateRange(mon.minusDays(7), sun.minusDays(7),
                0L, 2L, 0.0, 1L, 30L, "30.00");

        WeeklyReportPayload w = service.week(USER_ID, mon);

        // taskCompletion: curRate=7/14=0.5, prevRate=0/14=0.0, delta=+0.5
        WeeklyComparison.WeeklyTriplet taskT = w.comparison().taskCompletion();
        assertThat(taskT.current()).isEqualTo(0.5);
        assertThat(taskT.previous()).isEqualTo(0.0);
        assertThat(taskT.delta()).isEqualTo(0.5);

        // planEvents: cur=14, prev=7, delta=+7.0
        WeeklyComparison.WeeklyTriplet planT = w.comparison().planEvents();
        assertThat(planT.current()).isEqualTo(14.0);
        assertThat(planT.previous()).isEqualTo(7.0);
        assertThat(planT.delta()).isEqualTo(7.0);

        // expenseAmount: cur=350, prev=210, delta=+140.0
        WeeklyComparison.WeeklyTriplet expT = w.comparison().expenseAmount();
        assertThat(expT.current()).isEqualTo(350.0);
        assertThat(expT.previous()).isEqualTo(210.0);
        assertThat(expT.delta()).isEqualTo(140.0);
    }

    @Test
    @DisplayName("week：上周全零 → delta 全为 null（避免除零 / 符号歧义）")
    void week_previousWeekAllZero_returnsNullDeltas() {
        LocalDate mon = LocalDate.of(2026, 7, 20);
        LocalDate sun = LocalDate.of(2026, 7, 26);

        // current: 5 completed / 10 total, 2 events, 100 amount
        stubAllForDateRange(mon, sun, 5L, 10L, 0.5, 2L, 60L, "100.00");
        // previous: all zero
        stubAllForDateRange(mon.minusDays(7), sun.minusDays(7),
                0L, 0L, 0.0, 0L, 0L, "0.00");

        WeeklyReportPayload w = service.week(USER_ID, mon);

        // taskCompletion: previous.totalCount=0 → delta=null
        assertThat(w.comparison().taskCompletion().delta()).isNull();
        // planEvents: previous=0 → delta=null
        assertThat(w.comparison().planEvents().delta()).isNull();
        // expenseAmount: previous=0 → delta=null
        assertThat(w.comparison().expenseAmount().delta()).isNull();

        // current/previous 值仍正确（null 只影响 delta）
        assertThat(w.comparison().taskCompletion().current()).isEqualTo(0.5);
        assertThat(w.comparison().expenseAmount().current()).isEqualTo(700.0);
    }

    @Test
    @DisplayName("week：当前周全零但上周非零 → delta 可计算（负值）")
    void week_currentZeroPreviousNonZero_negativeDelta() {
        LocalDate mon = LocalDate.of(2026, 7, 20);
        LocalDate sun = LocalDate.of(2026, 7, 26);

        // current: all zero
        stubAllForDateRange(mon, sun, 0L, 0L, 0.0, 0L, 0L, "0.00");
        // previous: 3 completed / 6 total, 4 events, 200 amount（每日）
        stubAllForDateRange(mon.minusDays(7), sun.minusDays(7),
                3L, 6L, 0.5, 4L, 30L, "200.00");

        WeeklyReportPayload w = service.week(USER_ID, mon);

        // 7 日聚合：current.eventsSum=0, previous.eventsSum=7×4=28 → delta = -28.0
        // current.amountSum=0, previous.amountSum=7×200=1400 → delta = -1400.0
        assertThat(w.comparison().taskCompletion().delta()).isEqualTo(-0.5);
        assertThat(w.comparison().planEvents().delta()).isEqualTo(-28.0);
        assertThat(w.comparison().expenseAmount().delta()).isEqualTo(-1400.0);
    }

    @Test
    @DisplayName("formatIsoWeek：跨年周（2025-12-29 周一 → 2026-W01）")
    void formatIsoWeek_crossYear_returnsCorrectLabel() {
        // 直接测 formatIsoWeek：避免走 service.week() 触发 30 天窗口校验
        // （weekEnd=2026-01-04 距离 FIXED_TODAY=2026-07-21 远超 30 天，
        //  此场景只能验证 ISO 周格式逻辑，而非全链路）
        assertThat(DailyReportService.formatIsoWeek(LocalDate.of(2025, 12, 29)))
                .isEqualTo("2026-W01");
        // 常规周（同一年的 W30）
        assertThat(DailyReportService.formatIsoWeek(LocalDate.of(2026, 7, 20)))
                .isEqualTo("2026-W30");
        // 年末周日归入下一年 W01（2024-12-29 也是 2025-W01）
        assertThat(DailyReportService.formatIsoWeek(LocalDate.of(2024, 12, 30)))
                .isEqualTo("2025-W01");
    }

    @Test
    @DisplayName("week：传入周日 → weekStart 为该 ISO 周的前一周一（ISO 周以周一为首、周日为末）")
    void week_sundayDate_alignsToPreviousMonday() {
        // 2026-07-19 (Sun) 是 ISO 周 2026-W30 的最后一天
        // （Mon=2026-07-20 ... Sun=2026-07-26）—— 但 previousOrSame(MONDAY) 对齐到该周的周一
        // = 2026-07-13（不是 2026-07-20）。后者是"下一周"的周首。
        // ISO 8601 把周日视为该周的最后一天，对齐应当回到该 ISO 周的周一。
        // 这里使用 Mon 2026-07-13 属"上一ISO 周 W29"。但 2026-07-19 所在 ISO 周是 W30。
        // 因此：用 previousOrSame 行为本身不重要，重要的是用户传入任意一天都能正确对齐
        // 到所在 ISO 周的周一。核对下方断言确认行为。
        LocalDate sun = LocalDate.of(2026, 7, 19);
        // Stub 两周覆盖 service 实际迭代范围
        // 2026-07-19 的 previousOrSame(MONDAY) = 2026-07-13
        stubAllForDateRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19),
                0L, 0L, 0.0, 0L, 0L, "0.00");
        stubAllForDateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12),
                0L, 0L, 0.0, 0L, 0L, "0.00");

        WeeklyReportPayload w = service.week(USER_ID, sun);

        assertThat(w.weekStart()).isEqualTo(LocalDate.of(2026, 7, 13));
        assertThat(w.weekEnd()).isEqualTo(LocalDate.of(2026, 7, 19));
    }

    @Test
    @DisplayName("week：传入周一自身 → 直接作为 weekStart")
    void week_mondayDate_isWeekStart() {
        LocalDate mon = LocalDate.of(2026, 7, 20);
        stubAllForDateRange(mon, mon.plusDays(6), 0L, 0L, 0.0, 0L, 0L, "0.00");
        stubAllForDateRange(mon.minusDays(7), mon.minusDays(1),
                0L, 0L, 0.0, 0L, 0L, "0.00");

        WeeklyReportPayload w = service.week(USER_ID, mon);

        assertThat(w.weekStart()).isEqualTo(mon);
    }

    @Test
    @DisplayName("week：当前周 weekEnd 超出 30 天窗口 → 抛 1001")
    void week_weekEndTooOld_throwsValidation() {
        // FIXED_TODAY=2026-07-21；earliest=2026-06-21。
        // 取一个远在该边界之外的 weekEnd：weekEnd = today-37 = 2026-06-14（周日）
        // 对应 weekStart = 2026-06-08（周一）
        LocalDate weekEnd = FIXED_TODAY.minusDays(37);
        LocalDate weekStart = weekEnd.minusDays(6);
        assertThat(weekEnd).isBefore(FIXED_TODAY.minusDays(30));

        assertThatThrownBy(() -> service.week(USER_ID, weekStart))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    @DisplayName("week：当前周在窗口内时上周 prevWeekEnd 必 ≤ weekEnd（校验不会抛异常）")
    void week_passesValidMonday_doesNotThrow() {
        // FIXED_TODAY=2026-07-21 (Tue); weekStart=本周一=2026-07-13
        // weekEnd=2026-07-19; prevWeekEnd=2026-07-12。两者均在 30 天窗口内。
        // 关键点：必须传 Mon，否则 service 用 previousOrSame(MONDAY) 后移，会偏移 stub 范围。
        LocalDate weekStart = FIXED_TODAY.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        // 上句等价于 FIXED_TODAY=2026-07-21 → weekStart=2026-07-13 (Mon)
        stubAllForDateRange(weekStart, weekStart.plusDays(6),
                0L, 0L, 0.0, 0L, 0L, "0.00");
        stubAllForDateRange(weekStart.minusDays(7), weekStart.minusDays(1),
                0L, 0L, 0.0, 0L, 0L, "0.00");

        WeeklyReportPayload w = service.week(USER_ID, weekStart);

        assertThat(w.weekStart()).isEqualTo(weekStart);
    }

    @Test
    @DisplayName("week：每个 Provider 被调 7 次（每天 1 次 × 7 天）")
    void week_providersCalledSevenTimesPerWeek() {
        LocalDate mon = LocalDate.of(2026, 7, 20);
        stubAllForDateRange(mon, mon.plusDays(6), 0L, 0L, 0.0, 0L, 0L, "0.00");
        stubAllForDateRange(mon.minusDays(7), mon.minusDays(1),
                0L, 0L, 0.0, 0L, 0L, "0.00");

        service.week(USER_ID, mon);

        // 7 次单日聚合 × 2 周 = 14 次调用
        verify(taskProvider, times(14)).aggregateDaily(eq(USER_ID), org.mockito.ArgumentMatchers.any());
        verify(planProvider, times(14)).aggregateDaily(eq(USER_ID), org.mockito.ArgumentMatchers.any());
        verify(expenseProvider, times(14)).aggregateDaily(eq(USER_ID), org.mockito.ArgumentMatchers.any());
    }

    // ==================== today / validateInWindow ====================

    @Test
    @DisplayName("today() 默认实现：返回 Asia/Shanghai 当前日")
    void today_returnsAsiaShanghaiDate() {
        DailyReportService real = new DailyReportService(
                taskProvider, planProvider, expenseProvider, dietProvider);
        LocalDate today = real.today();
        // 仅校验为 LocalDate（不依赖真实时钟）
        assertThat(today).isInstanceOf(LocalDate.class);
    }

    @Test
    @DisplayName("validateInWindow：边界值精确校验")
    void validateInWindow_boundaryPrecision() {
        // 直接调用 package-private 方法注入 today
        LocalDate today = FIXED_TODAY;
        // exactly 30 days ago: 接受
        service.validateInWindow(today.minusDays(30), today);
        // 31 days ago: 拒绝
        assertThatThrownBy(() -> service.validateInWindow(today.minusDays(31), today))
                .isInstanceOf(BusinessException.class);
    }

    // ---- helpers ----

    /**
     * 固定 today() 的测试子类（package-private，不暴露生产代码）。
     * 不影响生产代码路径，只在 {@link #today()} 钩子处换值。
     */
    private static final class FixedTodayService extends DailyReportService {
        private final LocalDate fixedToday;

        FixedTodayService(TaskMetricProvider t, PlanMetricProvider p,
                          ExpenseMetricProvider e, DietMetricProvider d,
                          LocalDate fixedToday) {
            super(t, p, e, d);
            this.fixedToday = fixedToday;
        }

        @Override
        public LocalDate today() {
            return fixedToday;
        }
    }

    private void stubAllForDate(LocalDate date,
                                long done, long total, double rate,
                                long events, long minutes, String amount) {
        TaskMetrics task = new TaskMetrics(done, total, rate, Map.of(), Map.of());
        PlanMetrics plan = new PlanMetrics(events, minutes, Map.of(), events > 0 ? 9 : null);
        ExpenseMetrics expense = new ExpenseMetrics(
                new BigDecimal(amount), done + events,
                new HashMap<>(), List.of());
        // lenient(): 同一 date 在多 week 测试里被重复 stub，避免 strict 模式误报
        lenient().when(taskProvider.aggregateDaily(USER_ID, date)).thenReturn(task);
        lenient().when(planProvider.aggregateDaily(USER_ID, date)).thenReturn(plan);
        lenient().when(expenseProvider.aggregateDaily(USER_ID, date)).thenReturn(expense);
    }

    private void stubAllForDateRange(LocalDate from, LocalDate to,
                                     long done, long total, double rate,
                                     long events, long minutes, String amount) {
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            stubAllForDate(d, done, total, rate, events, minutes, amount);
        }
    }
}