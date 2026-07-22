package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.expense.repository.ExpenseMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseAiProviderTest {

    @Mock
    private ExpenseMapper expenseMapper;

    @InjectMocks
    private ExpenseAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void collect_someExpense_returnsNonEmpty() {
        when(expenseMapper.sumByUserOccurredBetween(anyLong(), any(), any()))
            .thenReturn(new BigDecimal("123.45"));

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(mv.unit()).isEqualTo("¥");
        assertThat(mv.isNonEmpty()).isTrue();
        assertThat(mv.trend()).isEqualTo(Trend.NONE);
    }

    @Test
    void collect_nullMapperResult_returnsZeroAndNotNonEmpty() {
        when(expenseMapper.sumByUserOccurredBetween(anyLong(), any(), any()))
            .thenReturn(null);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void collect_zeroMapperResult_returnsZeroAndNotNonEmpty() {
        when(expenseMapper.sumByUserOccurredBetween(anyLong(), any(), any()))
            .thenReturn(BigDecimal.ZERO);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void collect_passesUserIdAndWeekRange() {
        when(expenseMapper.sumByUserOccurredBetween(anyLong(), any(), any()))
            .thenReturn(new BigDecimal("100"));

        provider.collect(42L, ctx);

        Mockito.verify(expenseMapper).sumByUserOccurredBetween(
            eq(42L),
            org.mockito.ArgumentMatchers.notNull(),
            org.mockito.ArgumentMatchers.notNull()
        );
    }

    @Test
    void isEnabled_alwaysTrue() {
        assertThat(provider.isEnabled(1L)).isTrue();
        assertThat(provider.isEnabled(999L)).isTrue();
    }

    @Test
    void key_returnsExpense() {
        assertThat(provider.key()).isEqualTo("expense");
    }
}