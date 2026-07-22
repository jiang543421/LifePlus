package com.lifepulse.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.ai.provider.AiInsightProvider;
import com.lifepulse.ai.web.dto.AiChipDto;
import com.lifepulse.ai.web.dto.AiInsightResponse;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOps;

    private AiTemplateEngine templateEngine;
    private AiInsightService service;

    private FakeProvider taskProvider;
    private FakeProvider expenseProvider;
    private FakeProvider planProvider;
    private FakeProvider dietProvider;
    private FakeProvider dailyProvider;

    @BeforeEach
    void setUp() {
        templateEngine = new AiTemplateEngine();
        templateEngine.loadFromClasspath();

        taskProvider = new FakeProvider(AiConstants.PROVIDER_TASK, true,
            new MetricValue(new BigDecimal("80"), "%", Trend.FLAT));
        expenseProvider = new FakeProvider(AiConstants.PROVIDER_EXPENSE, true,
            new MetricValue(new BigDecimal("420"), "¥", Trend.FLAT));
        planProvider = new FakeProvider(AiConstants.PROVIDER_PLAN, true,
            new MetricValue(new BigDecimal("3"), "项", Trend.NONE));
        dietProvider = new FakeProvider(AiConstants.PROVIDER_DIET, true,
            new MetricValue(new BigDecimal("1650"), "kcal", Trend.NONE));
        dailyProvider = new FakeProvider(AiConstants.PROVIDER_DAILY, false,
            MetricValue.none());

        service = new AiInsightService(
            List.of(taskProvider, expenseProvider, planProvider, dietProvider, dailyProvider),
            templateEngine,
            redisObjectProvider(redis)
        );
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StringRedisTemplate> redisObjectProvider(StringRedisTemplate redis) {
        ObjectProvider<StringRedisTemplate> op = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(redis);
        return op;
    }

    @Test
    void getInsight_cacheMiss_allSuccess_returnsFullPayload() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        AiInsightResponse r = service.getInsight(7L);

        assertThat(r.headline()).contains("80").contains("420");
        assertThat(r.chips()).hasSize(3);
        assertThat(r.chips().get(0).key()).isEqualTo(AiConstants.CHIP_TASK_COMPLETION);
        assertThat(r.chips().get(1).key()).isEqualTo(AiConstants.CHIP_WEEKLY_EXPENSE);
        assertThat(r.chips().get(2).key()).isEqualTo(AiConstants.CHIP_PLAN_DENSITY);
        verify(valueOps, times(1)).set(anyString(), anyString(),
            eq(AiConstants.CACHE_TTL_MINUTES), eq(TimeUnit.MINUTES));
    }

    @Test
    void getInsight_cacheHit_skipsProvidersAndReturnsCached() {
        AiInsightResponse cached = new AiInsightResponse(
            "cached headline", List.of(), Instant.now(), 12L);
        String json = serialize(cached);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(json);

        AiInsightResponse r = service.getInsight(7L);

        assertThat(r.headline()).isEqualTo("cached headline");
        assertThat(taskProvider.collectCallCount).isZero();
        assertThat(expenseProvider.collectCallCount).isZero();
        verify(valueOps, never()).set(anyString(), anyString(),
            anyLong(), any(TimeUnit.class));
    }

    @Test
    void getInsight_oneProviderFails_otherChipsStillPopulated() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        taskProvider.throwOnCollect = new RuntimeException("task db down");

        AiInsightResponse r = service.getInsight(7L);

        // task 失败但 expense 成功 → headline 走 expenseOnly 分支
        assertThat(r.headline()).contains("420");
        // task chip 占位
        assertThat(r.chips().get(0).key()).isEqualTo(AiConstants.CHIP_TASK_COMPLETION);
        assertThat(r.chips().get(0).value()).isEqualTo("—");
        // expense chip 正常
        assertThat(r.chips().get(1).value()).contains("420");
        // plan chip 正常
        assertThat(r.chips().get(2).value()).isEqualTo("3");
        verify(valueOps, times(1)).set(anyString(), anyString(),
            eq(AiConstants.CACHE_TTL_MINUTES), eq(TimeUnit.MINUTES));
    }

    @Test
    void getInsight_allProvidersFailOrDisabled_throws1501() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        taskProvider.throwOnCollect = new RuntimeException("task db down");
        expenseProvider.throwOnCollect = new RuntimeException("expense db down");
        planProvider.throwOnCollect = new RuntimeException("plan db down");
        dietProvider.throwOnCollect = new RuntimeException("diet db down");

        assertThatThrownBy(() -> service.getInsight(7L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.AI_DEGRADED));
        // 失败时不写缓存
        verify(valueOps, never()).set(anyString(), anyString(),
            anyLong(), any(TimeUnit.class));
    }

    @Test
    void getInsight_disabledProviderTreatedAsNone() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        expenseProvider.enabled = false; // 模拟 expense 模块未上线

        AiInsightResponse r = service.getInsight(7L);

        // expense 关闭 → headline 走 taskOnly 分支
        assertThat(r.headline()).contains("80");
        assertThat(r.chips().get(1).value()).isEqualTo("—");
        assertThat(expenseProvider.collectCallCount).isZero();
    }

    @Test
    void getInsight_redisReadFailure_fallsThroughToCompute() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        AiInsightResponse r = service.getInsight(7L);

        assertThat(r.headline()).contains("80").contains("420");
        assertThat(r.chips()).hasSize(3);
    }

    @Test
    void getInsight_redisWriteFailure_doesNotPropagate() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("redis write down"))
            .when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        AiInsightResponse r = service.getInsight(7L);

        assertThat(r.headline()).contains("80").contains("420");
    }

    @Test
    void chipPlan_densityThreeRendersNormal() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        AiInsightResponse r = service.getInsight(7L);

        AiChipDto planChip = r.chips().get(2);
        assertThat(planChip.value()).isEqualTo("3");
        assertThat(planChip.deltaText()).isEqualTo("今日 3 项");
    }

    @Test
    void chipPlan_densityZeroRendersFree() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        planProvider.next = new MetricValue(BigDecimal.ZERO, "项", Trend.NONE);

        AiInsightResponse r = service.getInsight(7L);

        AiChipDto planChip = r.chips().get(2);
        assertThat(planChip.value()).isEqualTo("0");
        assertThat(planChip.deltaText()).isEqualTo("今日 0 项（有空闲）");
    }

    @Test
    void chipPlan_densityBusyThresholdFive() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        planProvider.next = new MetricValue(new BigDecimal("5"), "项", Trend.NONE);

        AiInsightResponse r = service.getInsight(7L);

        AiChipDto planChip = r.chips().get(2);
        assertThat(planChip.value()).isEqualTo("5");
        assertThat(planChip.deltaText()).isEqualTo("今日 5 项（较忙）");
    }

    @Test
    void freshnessSeconds_returnsNonNegative() {
        AiInsightResponse r = new AiInsightResponse(
            "h", List.of(),
            Instant.now().minus(Duration.ofSeconds(5)), 0L);

        long fs = AiInsightService.freshnessSeconds(r);

        assertThat(fs).isGreaterThanOrEqualTo(5L);
    }

    private static String serialize(AiInsightResponse r) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 简单可控 provider：固定返回 next；可抛、可关闭。 */
    private static class FakeProvider implements AiInsightProvider {
        private final String k;
        private boolean enabled;
        private MetricValue next;
        private RuntimeException throwOnCollect;
        private int collectCallCount;

        FakeProvider(String key, boolean enabled, MetricValue next) {
            this.k = key;
            this.enabled = enabled;
            this.next = next;
        }

        @Override public String key() { return k; }
        @Override public boolean isEnabled(Long userId) { return enabled; }
        @Override public MetricValue collect(Long userId,
                                             com.lifepulse.ai.provider.AiCollectContext ctx) {
            collectCallCount++;
            if (throwOnCollect != null) {
                throw throwOnCollect;
            }
            return next;
        }
    }
}