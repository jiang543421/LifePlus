package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.task.repository.TaskMapper;
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
class TaskAiProviderTest {

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void collect_allTasksDone_returnsCompletion100() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(10);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(10);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(mv.unit()).isEqualTo("%");
        assertThat(mv.isNonEmpty()).isTrue();
    }

    @Test
    void collect_halfDone_returnsCompletion50() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(10);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(5);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    void collect_noTasks_returnsZeroAndNotNonEmpty() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(0);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(0);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void collect_passesUserIdToMapper() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(2);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(1);

        provider.collect(42L, ctx);

        Mockito.verify(taskMapper).countTodayTasks(eq(42L), any());
        Mockito.verify(taskMapper).countTodayCompletedTasks(eq(42L), any());
    }

    @Test
    void isEnabled_alwaysTrue() {
        assertThat(provider.isEnabled(1L)).isTrue();
        assertThat(provider.isEnabled(999L)).isTrue();
    }

    @Test
    void key_returnsTask() {
        assertThat(provider.key()).isEqualTo("task");
    }

    @Test
    void collect_zeroTotal_returnsTrendNone() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(0);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(0);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.trend()).isEqualTo(Trend.NONE);
    }
}