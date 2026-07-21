package com.lifepulse.daily.provider;

import com.lifepulse.daily.PlanMetrics;
import com.lifepulse.plan.repository.PlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlanMetricProvider 单元测试（plan §5 T3 / spec §6 service 行覆盖 ≥ 80%）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>空数据集 → eventCount=0, totalMinutes=0, categoryDistribution=空 Map,
 *       busiestHour=null</li>
 *   <li>仅全天事件 → eventCount=N, totalMinutes=0（COALESCE 兜底）</li>
 *   <li>单小时最忙 → busiestHour 取 ORDER BY 第一行</li>
 *   <li>多 hour bucket 并列时按 hour ASC 取最小</li>
 *   <li>3 个 mapper 调用都以半开区间 [dayStart, nextDayStart) 调用</li>
 *   <li>categoryDistribution 永远返回空 Map（MVP1 plan 无 category 列）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlanMetricProviderTest {

    private static final long USER_ID = 7L;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 21);
    private static final LocalDateTime DAY_START = DATE.atStartOfDay();
    private static final LocalDateTime NEXT_DAY_START = DATE.plusDays(1).atStartOfDay();

    @Mock
    private PlanMapper planMapper;

    private PlanMetricProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PlanMetricProvider(planMapper);
    }

    @Test
    @DisplayName("空数据集：全零结构 + busiestHour=null + categoryDistribution=空 Map")
    void aggregateDaily_noEvents_returnsAllZeros() {
        when(planMapper.countByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START)).thenReturn(0L);
        when(planMapper.sumActiveMinutesByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(0L);
        when(planMapper.selectHourBucketsByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(List.of());

        PlanMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.eventCount()).isZero();
        assertThat(m.totalMinutes()).isZero();
        assertThat(m.categoryDistribution()).isEmpty();
        assertThat(m.busiestHour()).isNull();
    }

    @Test
    @DisplayName("全天事件：eventCount=N 但 totalMinutes=0（COALESCE 兜底）")
    void aggregateDaily_allDayEvents_excludedFromMinutes() {
        when(planMapper.countByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START)).thenReturn(2L);
        when(planMapper.sumActiveMinutesByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(0L);
        when(planMapper.selectHourBucketsByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(List.of(hourBucket(9, 2L)));

        PlanMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.eventCount()).isEqualTo(2L);
        assertThat(m.totalMinutes()).isZero();
        assertThat(m.busiestHour()).isEqualTo(9);
    }

    @Test
    @DisplayName("单小时最忙：hour bucket 取 ORDER BY cnt DESC, hour ASC 的第一行")
    void aggregateDaily_singleBusiestHour_returnsThatHour() {
        when(planMapper.countByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START)).thenReturn(3L);
        when(planMapper.sumActiveMinutesByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(120L);
        // mapper 已 ORDER BY cnt DESC, bucket ASC
        when(planMapper.selectHourBucketsByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(List.of(
                        hourBucket(14, 3L),
                        hourBucket(9, 1L)));

        PlanMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.busiestHour()).isEqualTo(14);
    }

    @Test
    @DisplayName("多 hour bucket 并列时按 hour ASC 取最小")
    void aggregateDaily_tiedCounts_picksEarlierHour() {
        when(planMapper.countByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START)).thenReturn(2L);
        when(planMapper.sumActiveMinutesByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(60L);
        when(planMapper.selectHourBucketsByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(List.of(
                        hourBucket(8, 1L),
                        hourBucket(20, 1L)));

        PlanMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.busiestHour()).isEqualTo(8);
    }

    @Test
    @DisplayName("3 个 mapper 调用都以半开区间 [dayStart, nextDayStart) 调用")
    void aggregateDaily_callsMappersWithHalfOpenDayRange() {
        when(planMapper.countByUserOnDay(anyLong(), eq(DAY_START), eq(NEXT_DAY_START)))
                .thenReturn(0L);
        when(planMapper.sumActiveMinutesByUserOnDay(anyLong(), eq(DAY_START), eq(NEXT_DAY_START)))
                .thenReturn(0L);
        when(planMapper.selectHourBucketsByUserOnDay(anyLong(), eq(DAY_START), eq(NEXT_DAY_START)))
                .thenReturn(List.of());

        provider.aggregateDaily(USER_ID, DATE);

        ArgumentCaptor<LocalDateTime> dayStartCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> nextCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(planMapper).countByUserOnDay(eq(USER_ID), dayStartCap.capture(), nextCap.capture());
        assertThat(dayStartCap.getValue()).isEqualTo(DAY_START);
        assertThat(nextCap.getValue()).isEqualTo(NEXT_DAY_START);

        verify(planMapper).sumActiveMinutesByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START);
        verify(planMapper).selectHourBucketsByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START);
    }

    @Test
    @DisplayName("categoryDistribution 永远返回空 Map（MVP1 plan 无 category 列）")
    void aggregateDaily_categoryDistributionAlwaysEmpty() {
        when(planMapper.countByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START)).thenReturn(1L);
        when(planMapper.sumActiveMinutesByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(30L);
        when(planMapper.selectHourBucketsByUserOnDay(USER_ID, DAY_START, NEXT_DAY_START))
                .thenReturn(List.of(hourBucket(10, 1L)));

        PlanMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.categoryDistribution()).isEmpty();
        assertThat(m.categoryDistribution()).isNotNull();
    }

    @Test
    @DisplayName("name() 默认返回类名")
    void name_returnsSimpleClassName() {
        assertThat(provider.name()).isEqualTo("PlanMetricProvider");
    }

    // ---- helper ----

    private static Map<String, Object> hourBucket(int hour, long cnt) {
        Map<String, Object> row = new HashMap<>();
        row.put("bucket", hour);
        row.put("cnt", cnt);
        return row;
    }
}