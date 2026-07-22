package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.plan.repository.PlanMapper;
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
class PlanAiProviderTest {

    @Mock
    private PlanMapper planMapper;

    @InjectMocks
    private PlanAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void collect_threeEvents_returnsCountAndNonEmpty() {
        when(planMapper.countTodayEvents(anyLong(), any())).thenReturn(3);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(mv.unit()).isEqualTo("项");
        assertThat(mv.isNonEmpty()).isTrue();
        assertThat(mv.trend()).isEqualTo(Trend.NONE);
    }

    @Test
    void collect_noEvents_returnsZeroAndNotNonEmpty() {
        when(planMapper.countTodayEvents(anyLong(), any())).thenReturn(0);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void collect_passesUserIdAndDate() {
        when(planMapper.countTodayEvents(anyLong(), any())).thenReturn(2);

        provider.collect(42L, ctx);

        Mockito.verify(planMapper).countTodayEvents(eq(42L), eq(ctx.today()));
    }

    @Test
    void isEnabled_alwaysTrue() {
        assertThat(provider.isEnabled(1L)).isTrue();
        assertThat(provider.isEnabled(999L)).isTrue();
    }

    @Test
    void key_returnsPlan() {
        assertThat(provider.key()).isEqualTo("plan");
    }

    @Test
    void collect_trendAlwaysNone_regardlessOfCount() {
        // pin 行为：plan provider 无跨日对比信号，trend 永为 NONE（模板自己按 value 渲染 busy/normal/free）
        when(planMapper.countTodayEvents(anyLong(), any())).thenReturn(10);
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.trend()).isEqualTo(Trend.NONE);
        assertThat(mv.unit()).isEqualTo("项");
    }

    @Test
    void collect_crossUser_userIdPropagatedToMapper() {
        // pin CLAUDE.md §7.2：userId 必须透传给 mapper，禁止从 ctx 推断
        when(planMapper.countTodayEvents(eq(7L), any())).thenReturn(2);

        provider.collect(7L, ctx);

        Mockito.verify(planMapper).countTodayEvents(eq(7L), eq(ctx.today()));
    }
}
