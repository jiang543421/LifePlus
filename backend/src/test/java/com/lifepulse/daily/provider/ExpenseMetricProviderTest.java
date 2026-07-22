package com.lifepulse.daily.provider;

import com.lifepulse.daily.DailyConstants;
import com.lifepulse.daily.ExpenseMetrics;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.repository.ExpenseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExpenseMetricProvider 单元测试（plan §5 T4 / spec §6 service 行覆盖 ≥ 80%）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>空数据集 → totalAmount=0, count=0, breakdown 全零（5 类齐全）, top 空</li>
 *   <li>单类别消费 → 聚合正确</li>
 *   <li>多类别部分缺失 → breakdown 自动补 0</li>
 *   <li>topCategories 按金额降序</li>
 *   <li>topCategories 零金额不入榜</li>
 *   <li>topCategories 并列金额按枚举声明顺序</li>
 *   <li>mapper 调用以 UTC 半开区间 [dayStart, nextDayStart)</li>
 *   <li>name() 返回类名</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExpenseMetricProviderTest {

    private static final long USER_ID = 99L;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 21);

    /** Asia/Shanghai 2026-07-21 00:00 → UTC 2026-07-20 16:00。 */
    private static final OffsetDateTime DAY_START_UTC =
            DATE.atStartOfDay(DailyConstants.ZONE).toInstant().atOffset(ZoneOffset.UTC);
    private static final OffsetDateTime NEXT_DAY_START_UTC =
            DATE.plusDays(1).atStartOfDay(DailyConstants.ZONE).toInstant().atOffset(ZoneOffset.UTC);

    @Mock
    private ExpenseMapper expenseMapper;

    private ExpenseMetricProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ExpenseMetricProvider(expenseMapper);
    }

    @Test
    @DisplayName("空数据集：total=0, count=0, breakdown 5 类全 0, top 空列表")
    void aggregateDaily_noExpenses_returnsZeros() {
        when(expenseMapper.summaryTotal(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(BigDecimal.ZERO);
        when(expenseMapper.countByUserOnDay(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(0L);
        when(expenseMapper.summaryByCategory(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(List.of());

        ExpenseMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.count()).isZero();
        assertThat(m.categoryBreakdown())
                .hasSize(5)
                .containsEntry(ExpenseCategory.MEAL.name(), BigDecimal.ZERO)
                .containsEntry(ExpenseCategory.SHOPPING.name(), BigDecimal.ZERO)
                .containsEntry(ExpenseCategory.TRANSPORT.name(), BigDecimal.ZERO)
                .containsEntry(ExpenseCategory.SUBSCRIPTION.name(), BigDecimal.ZERO)
                .containsEntry(ExpenseCategory.OTHER.name(), BigDecimal.ZERO);
        assertThat(m.topCategories()).isEmpty();
    }

    @Test
    @DisplayName("单类别消费：MEAL=100 → total=100, count=1, breakdown 4 类补 0, top=[MEAL 100]")
    void aggregateDaily_singleCategory_aggregatesTotal() {
        when(expenseMapper.summaryTotal(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(new BigDecimal("100.00"));
        when(expenseMapper.countByUserOnDay(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(1L);
        when(expenseMapper.summaryByCategory(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(List.of(row(ExpenseCategory.MEAL.name(), "100.00")));

        ExpenseMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.totalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(m.count()).isEqualTo(1L);
        assertThat(m.categoryBreakdown())
                .containsEntry(ExpenseCategory.MEAL.name(), new BigDecimal("100.00"))
                .containsEntry(ExpenseCategory.SHOPPING.name(), BigDecimal.ZERO);
        assertThat(m.topCategories())
                .containsExactly(new ExpenseMetrics.CategoryTop(
                        ExpenseCategory.MEAL.name(), new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("多类别消费：3 类有 / 2 类无 → breakdown 5 类齐全")
    void aggregateDaily_multipleCategories_aggregatesBreakdownAndBackfillsZeros() {
        when(expenseMapper.summaryTotal(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(new BigDecimal("350.00"));
        when(expenseMapper.countByUserOnDay(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(5L);
        when(expenseMapper.summaryByCategory(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(List.of(
                        row(ExpenseCategory.MEAL.name(), "150.00"),
                        row(ExpenseCategory.TRANSPORT.name(), "100.00"),
                        row(ExpenseCategory.SHOPPING.name(), "100.00")));

        ExpenseMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.count()).isEqualTo(5L);
        assertThat(m.categoryBreakdown())
                .hasSize(5)
                .containsEntry(ExpenseCategory.MEAL.name(), new BigDecimal("150.00"))
                .containsEntry(ExpenseCategory.TRANSPORT.name(), new BigDecimal("100.00"))
                .containsEntry(ExpenseCategory.SHOPPING.name(), new BigDecimal("100.00"))
                .containsEntry(ExpenseCategory.SUBSCRIPTION.name(), BigDecimal.ZERO)
                .containsEntry(ExpenseCategory.OTHER.name(), BigDecimal.ZERO);
    }

    @Test
    @DisplayName("topCategories 按金额降序，限 Top 3")
    void aggregateDaily_topCategories_sortedByAmountDesc() {
        when(expenseMapper.summaryTotal(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(new BigDecimal("600.00"));
        when(expenseMapper.countByUserOnDay(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(10L);
        // 5 类全部有值，但只有前 3 应入榜
        when(expenseMapper.summaryByCategory(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(List.of(
                        row(ExpenseCategory.MEAL.name(), "300.00"),
                        row(ExpenseCategory.SHOPPING.name(), "150.00"),
                        row(ExpenseCategory.TRANSPORT.name(), "100.00"),
                        row(ExpenseCategory.SUBSCRIPTION.name(), "40.00"),
                        row(ExpenseCategory.OTHER.name(), "10.00")));

        ExpenseMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.topCategories()).hasSize(3);
        assertThat(m.topCategories().get(0).code()).isEqualTo(ExpenseCategory.MEAL.name());
        assertThat(m.topCategories().get(0).amount()).isEqualByComparingTo("300.00");
        assertThat(m.topCategories().get(1).code()).isEqualTo(ExpenseCategory.SHOPPING.name());
        assertThat(m.topCategories().get(1).amount()).isEqualByComparingTo("150.00");
        assertThat(m.topCategories().get(2).code()).isEqualTo(ExpenseCategory.TRANSPORT.name());
        assertThat(m.topCategories().get(2).amount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("topCategories 零金额不入榜（breakdown 含 0 但 top 只含 > 0 项）")
    void aggregateDaily_topCategories_excludesZeroAmount() {
        when(expenseMapper.summaryTotal(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(new BigDecimal("50.00"));
        when(expenseMapper.countByUserOnDay(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(1L);
        when(expenseMapper.summaryByCategory(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(List.of(
                        row(ExpenseCategory.MEAL.name(), "50.00"),
                        row(ExpenseCategory.SHOPPING.name(), "0.00"))); // 显式 0

        ExpenseMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.categoryBreakdown())
                .containsEntry(ExpenseCategory.SHOPPING.name(), BigDecimal.ZERO);
        assertThat(m.topCategories())
                .hasSize(1)
                .allSatisfy(ct -> assertThat(ct.amount()).isPositive());
    }

    @Test
    @DisplayName("topCategories 并列金额按枚举声明顺序（MEAL 在 TRANSPORT 之前）")
    void aggregateDaily_topCategories_tiedAmounts_breaksByEnumOrder() {
        when(expenseMapper.summaryTotal(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(new BigDecimal("300.00"));
        when(expenseMapper.countByUserOnDay(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(3L);
        when(expenseMapper.summaryByCategory(USER_ID, DAY_START_UTC, NEXT_DAY_START_UTC))
                .thenReturn(List.of(
                        row(ExpenseCategory.TRANSPORT.name(), "100.00"),
                        row(ExpenseCategory.MEAL.name(), "100.00"),
                        row(ExpenseCategory.SHOPPING.name(), "100.00")));

        ExpenseMetrics m = provider.aggregateDaily(USER_ID, DATE);

        // 金额全部 100，并列；按枚举声明顺序 MEAL → SHOPPING → TRANSPORT
        assertThat(m.topCategories()).hasSize(3);
        assertThat(m.topCategories().get(0).code()).isEqualTo(ExpenseCategory.MEAL.name());
        assertThat(m.topCategories().get(1).code()).isEqualTo(ExpenseCategory.SHOPPING.name());
        assertThat(m.topCategories().get(2).code()).isEqualTo(ExpenseCategory.TRANSPORT.name());
    }

    @Test
    @DisplayName("mapper 调用以 UTC 半开区间 [dayStart, nextDayStart)，从 Asia/Shanghai 日期转换")
    void aggregateDaily_callsMapperWithUtcHalfOpenInterval() {
        when(expenseMapper.summaryTotal(anyLong(), eq(DAY_START_UTC), eq(NEXT_DAY_START_UTC)))
                .thenReturn(BigDecimal.ZERO);
        when(expenseMapper.countByUserOnDay(anyLong(), eq(DAY_START_UTC), eq(NEXT_DAY_START_UTC)))
                .thenReturn(0L);
        when(expenseMapper.summaryByCategory(anyLong(), eq(DAY_START_UTC), eq(NEXT_DAY_START_UTC)))
                .thenReturn(List.of());

        provider.aggregateDaily(USER_ID, DATE);

        // capture 实参以断言精确 UTC 时刻
        ArgumentCaptor<OffsetDateTime> startCap = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> nextCap = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(expenseMapper).summaryTotal(eq(USER_ID), startCap.capture(), nextCap.capture());
        assertThat(startCap.getValue()).isEqualTo(DAY_START_UTC);
        assertThat(nextCap.getValue()).isEqualTo(NEXT_DAY_START_UTC);
        assertThat(startCap.getValue().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("name() 默认返回类名")
    void name_returnsSimpleClassName() {
        assertThat(provider.name()).isEqualTo("ExpenseMetricProvider");
    }

    // ---- helper ----

    private static Map<String, Object> row(String code, String amount) {
        Map<String, Object> r = new HashMap<>();
        r.put("k", code);
        r.put("v", new BigDecimal(amount));
        return r;
    }
}