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