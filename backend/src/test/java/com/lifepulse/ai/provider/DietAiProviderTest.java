package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.diet.repository.DietMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DietAiProviderTest {

    @Mock
    private DietMapper dietMapper;

    @InjectMocks
    private DietAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    private Map<String, Object> summaryWith(long kcal) {
        Map<String, Object> m = new HashMap<>();
        m.put("kcal", BigDecimal.valueOf(kcal));
        m.put("proteinG", BigDecimal.ZERO);
        m.put("carbG", BigDecimal.ZERO);
        m.put("fatG", BigDecimal.ZERO);
        return m;
    }

    @Test
    void collect_someIntake_returnsNonEmpty() {
        when(dietMapper.summaryOnDate(anyLong(), any(), any())).thenReturn(summaryWith(1650L));

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("1650"));
        assertThat(mv.unit()).isEqualTo("kcal");
        assertThat(mv.isNonEmpty()).isTrue();
        assertThat(mv.trend()).isEqualTo(Trend.NONE);
    }

    @Test
    void collect_nullMapperResult_returnsZeroAndNotNonEmpty() {
        when(dietMapper.summaryOnDate(anyLong(), any(), any())).thenReturn(null);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void collect_zeroKcal_returnsZeroAndNotNonEmpty() {
        when(dietMapper.summaryOnDate(anyLong(), any(), any())).thenReturn(summaryWith(0L));

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void collect_missingKcalKey_returnsZeroAndNotNonEmpty() {
        when(dietMapper.summaryOnDate(anyLong(), any(), any())).thenReturn(new HashMap<>());

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void isEnabled_alwaysTrue() {
        assertThat(provider.isEnabled(1L)).isTrue();
        assertThat(provider.isEnabled(999L)).isTrue();
    }

    @Test
    void key_returnsDiet() {
        assertThat(provider.key()).isEqualTo("diet");
    }

    @Test
    void collect_kcalValueIsStringNumber_parsesViaDoubleValue() {
        // pin extractKcal 兼容 Long/Integer/Double 等 Number 子类
        Map<String, Object> row = new HashMap<>();
        row.put("kcal", Long.valueOf(800));
        when(dietMapper.summaryOnDate(anyLong(), any(), any())).thenReturn(row);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("800"));
        assertThat(mv.isNonEmpty()).isTrue();
    }

    @Test
    void collect_kcalValueIsUnknownType_returnsZero() {
        // pin extractKcal 对未知类型 fallback 到 ZERO（不抛）
        Map<String, Object> row = new HashMap<>();
        row.put("kcal", "not-a-number");  // String 而非 Number
        when(dietMapper.summaryOnDate(anyLong(), any(), any())).thenReturn(row);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void collect_passesUserIdToMapper() {
        // pin CLAUDE.md §7.2：userId 必须透传
        Map<String, Object> row = new HashMap<>();
        row.put("kcal", BigDecimal.valueOf(500));
        when(dietMapper.summaryOnDate(eq(7L), any(), any())).thenReturn(row);

        provider.collect(7L, ctx);

        verify(dietMapper).summaryOnDate(eq(7L), any(), any());
    }
}
