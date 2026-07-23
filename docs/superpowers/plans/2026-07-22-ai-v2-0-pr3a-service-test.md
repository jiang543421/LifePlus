## PR 3 — Service 编排（缓存 + 降级）

涵盖 spec §13 T5.1 - T5.4。完成此 PR 后，Service 层有完整 5 provider 串联、Redis 30min 缓存、3 层降级语义；Controller 可直接调用。

### Task 5.1: AiInsightService 基础结构 + getOrCompute 缓存路径

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/service/AiInsightService.java`
- Create: `backend/src/test/java/com/lifepulse/ai/service/AiInsightServiceTest.java`

**Interfaces:**
- Consumes: `AiInsightProvider[]`（5 个，从 PR2 注入），`AiTemplateEngine`，`RedisTemplate<String, AiInsightPayload>`
- Produces: `getOrCompute(userId) → AiInsightPayload`，`refresh(userId) → AiInsightPayload`

- [ ] **Step 1: 写失败测试 `AiInsightServiceTest.java`（核心用例）**

```java
package com.lifepulse.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.model.AiInsightPayload;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.ai.provider.AiCollectContext;
import com.lifepulse.ai.provider.AiInsightProvider;
import com.lifepulse.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    @Mock private AiTemplateEngine templateEngine;
    @Mock private RedisTemplate<String, AiInsightPayload> redis;
    @Mock private ValueOperations<String, AiInsightPayload> valueOps;
    @Mock private TaskAiProviderStub taskProvider;
    @Mock private PlanAiProviderStub planProvider;
    @Mock private ExpenseAiProviderStub expenseProvider;
    @Mock private DietAiProviderStub dietProvider;
    @Mock private DailyAiProviderStub dailyProvider;

    private AiInsightService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new AiInsightService(
            templateEngine, redis,
            List.of(taskProvider, planProvider, expenseProvider, dietProvider, dailyProvider)
        );
        // 全部 enabled
        lenient().when(taskProvider.isEnabled(anyLong())).thenReturn(true);
        lenient().when(planProvider.isEnabled(anyLong())).thenReturn(true);
        lenient().when(expenseProvider.isEnabled(anyLong())).thenReturn(true);
        lenient().when(dietProvider.isEnabled(anyLong())).thenReturn(true);
        lenient().when(dailyProvider.isEnabled(anyLong())).thenReturn(true);
    }

    // === 缓存路径 ===

    @Test
    void getOrCompute_cacheHit_returnsCachedWithoutInvokingProviders() {
        var cached = new AiInsightPayload(
            "cached headline", List.of(), Instant.parse("2026-07-21T10:00:00Z")
        );
        when(valueOps.get("ai:insight:1")).thenReturn(cached);

        var result = service.getOrCompute(1L);

        assertThat(result).isSameAs(cached);
        verify(taskProvider, never()).collect(anyLong(), any());
        verify(planProvider, never()).collect(anyLong(), any());
    }

    @Test
    void getOrCompute_cacheMiss_invokesAllEnabledProviders() {
        when(valueOps.get("ai:insight:1")).thenReturn(null);
        when(taskProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("80"), "%", Trend.UP));
        when(planProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("3"), "项", Trend.FLAT));
        when(expenseProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("420"), "¥", Trend.DOWN));
        when(dietProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("1500"), "kcal", Trend.FLAT));
        when(dailyProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("7"), "天", Trend.FLAT));
        when(templateEngine.formatHeadline(any(), any())).thenReturn("rendered headline");

        var result = service.getOrCompute(1L);

        assertThat(result.headline()).isEqualTo("rendered headline");
        verify(taskProvider, times(1)).collect(anyLong(), any());
        verify(planProvider, times(1)).collect(anyLong(), any());
        verify(valueOps).set(eq("ai:insight:1"), any(), any());
    }

    // === 降级：单 provider 异常 ===

    @Test
    void getOrCompute_singleProviderThrows_skipsAndContinues() {
        when(valueOps.get(any())).thenReturn(null);
        when(taskProvider.collect(anyLong(), any())).thenThrow(new RuntimeException("DB error"));
        when(planProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("3"), "项", Trend.FLAT));
        when(expenseProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("420"), "¥", Trend.DOWN));
        when(dietProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("1500"), "kcal", Trend.FLAT));
        when(dailyProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("7"), "天", Trend.FLAT));
        when(templateEngine.formatHeadline(any(), any())).thenReturn("partial headline");

        var result = service.getOrCompute(1L);

        assertThat(result.headline()).isEqualTo("partial headline");
        // 其余 provider 仍被调用
        verify(planProvider, times(1)).collect(anyLong(), any());
    }

    // === 降级：所有 enabled provider 失败 ===

    @Test
    void getOrCompute_allProvidersThrow_throws1501() {
        when(valueOps.get(any())).thenReturn(null);
        when(taskProvider.collect(anyLong(), any())).thenThrow(new RuntimeException("a"));
        when(planProvider.collect(anyLong(), any())).thenThrow(new RuntimeException("b"));
        when(expenseProvider.collect(anyLong(), any())).thenThrow(new RuntimeException("c"));
        when(dietProvider.collect(anyLong(), any())).thenThrow(new RuntimeException("d"));
        when(dailyProvider.collect(anyLong(), any())).thenThrow(new RuntimeException("e"));

        assertThatThrownBy(() -> service.getOrCompute(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("AI 服务暂不可用")
            .extracting("code").isEqualTo(1501);
    }

    // === 降级：isEnabled=false 跳过 ===

    @Test
    void getOrCompute_dailyDisabled_doesNotInvokeDailyProvider() {
        when(dailyProvider.isEnabled(anyLong())).thenReturn(false);
        when(valueOps.get(any())).thenReturn(null);
        when(taskProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("80"), "%", Trend.UP));
        when(planProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("3"), "项", Trend.FLAT));
        when(expenseProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("420"), "¥", Trend.DOWN));
        when(dietProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("1500"), "kcal", Trend.FLAT));
        when(templateEngine.formatHeadline(any(), any())).thenReturn("headline");

        service.getOrCompute(1L);

        verify(dailyProvider, never()).collect(anyLong(), any());
    }

    // === 降级：Redis 不可用 ===

    @Test
    void getOrCompute_redisDownOnRead_fallsBackToRecompute() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenThrow(new RedisConnectionFailureException("redis down"));
        when(taskProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("80"), "%", Trend.UP));
        when(templateEngine.formatHeadline(any(), any())).thenReturn("headline");

        var result = service.getOrCompute(1L);

        assertThat(result.headline()).isEqualTo("headline");
        // 重新计算后，写入也失败的话也不应抛错
    }

    // === 零数据 ===

    @Test
    void getOrCompute_zeroData_returnsEmptyHeadline() {
        when(valueOps.get(any())).thenReturn(null);
        // 所有 provider 返回 ZERO + NONE
        when(taskProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(BigDecimal.ZERO, "%", Trend.NONE));
        when(planProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(BigDecimal.ZERO, "项", Trend.NONE));
        when(expenseProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(BigDecimal.ZERO, "¥", Trend.NONE));
        when(dietProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(BigDecimal.ZERO, "kcal", Trend.NONE));
        when(dailyProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(BigDecimal.ZERO, "天", Trend.NONE));
        when(templateEngine.formatHeadline(eq("headline.empty"))).thenReturn("还没有数据");

        var result = service.getOrCompute(1L);

        assertThat(result.headline()).isEqualTo("还没有数据");
        assertThat(result.chips()).isEmpty();
    }

    // === refresh ===

    @Test
    void refresh_evictsCacheAndRecomputes() {
        when(taskProvider.collect(anyLong(), any()))
            .thenReturn(new MetricValue(new BigDecimal("80"), "%", Trend.UP));
        when(templateEngine.formatHeadline(any(), any())).thenReturn("new headline");

        var result = service.refresh(1L);

        verify(redis).delete("ai:insight:1");
        assertThat(result.headline()).isEqualTo("new headline");
    }

    // === Stub 接口（用 lambda 即可） ===

    interface TaskAiProviderStub extends AiInsightProvider {
        @Override default String key() { return "task"; }
    }
    interface PlanAiProviderStub extends AiInsightProvider {
        @Override default String key() { return "plan"; }
    }
    interface ExpenseAiProviderStub extends AiInsightProvider {
        @Override default String key() { return "expense"; }
    }
    interface DietAiProviderStub extends AiInsightProvider {
        @Override default String key() { return "diet"; }
    }
    interface DailyAiProviderStub extends AiInsightProvider {
        @Override default String key() { return "daily"; }
    }

    // helper: mock eq 不需 import，参考外部 import
    static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
```

- [ ] **Step 2: 运行测试，预期 FAIL**

```powershell
cd backend; mvn -q test -Dtest=AiInsightServiceTest
```

预期：编译失败（`AiInsightService` 不存在）。
