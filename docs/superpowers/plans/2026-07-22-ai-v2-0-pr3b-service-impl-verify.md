- [ ] **Step 3: 实现 `AiInsightService.java`**

```java
package com.lifepulse.ai.service;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.AiInsightPayload;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.provider.AiCollectContext;
import com.lifepulse.ai.provider.AiInsightProvider;
import com.lifepulse.common.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 洞察服务编排（spec §3.1）。
 *
 * <p>主流程：缓存检查 → enabled provider 循环采集 → 模板渲染 → 回写缓存。
 * 降级 3 层：
 * <ol>
 *   <li>provider 模块未上线（isEnabled=false）→ 跳过</li>
 *   <li>单 provider 异常 → catch + log.warn + 跳过</li>
 *   <li>所有 enabled provider 失败 → 抛 1501</li>
 *   <li>Redis 不可用 → 读视作 MISS，写/删跳过，log.warn</li>
 * </ol>
 */
@Service
public class AiInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiInsightService.class);

    private final AiTemplateEngine templateEngine;
    private final RedisTemplate<String, AiInsightPayload> redis;
    private final List<AiInsightProvider> providers;

    public AiInsightService(
        AiTemplateEngine templateEngine,
        RedisTemplate<String, AiInsightPayload> redis,
        List<AiInsightProvider> providers
    ) {
        this.templateEngine = templateEngine;
        this.redis = redis;
        this.providers = providers;
    }

    /** 取当前洞察；命中缓存直接返回，未命中重算并回写。 */
    public AiInsightPayload getOrCompute(Long userId) {
        String key = AiConstants.CACHE_KEY_PREFIX + userId;
        AiInsightPayload cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        AiInsightPayload fresh = compute(userId);
        writeCache(key, fresh);
        return fresh;
    }

    /** 清缓存 + 重算。 */
    public AiInsightPayload refresh(Long userId) {
        String key = AiConstants.CACHE_KEY_PREFIX + userId;
        evictCache(key);
        AiInsightPayload fresh = compute(userId);
        writeCache(key, fresh);
        return fresh;
    }

    // === 私有 ===

    private AiInsightPayload compute(Long userId) {
        AiCollectContext ctx = AiCollectContext.nowInShanghai();
        Map<String, MetricValue> metrics = new LinkedHashMap<>();
        int errorCount = 0;
        int enabledCount = 0;

        for (var provider : providers) {
            if (!provider.isEnabled(userId)) {
                continue;
            }
            enabledCount++;
            try {
                MetricValue mv = provider.collect(userId, ctx);
                if (mv != null && mv.isNonEmpty()) {
                    metrics.put(provider.key(), mv);
                }
            } catch (Exception e) {
                errorCount++;
                log.warn("ai provider '{}' failed, skip: {}", provider.key(), e.toString());
            }
        }

        if (enabledCount > 0 && errorCount == enabledCount) {
            throw new BusinessException(1501, "AI 服务暂不可用，请稍后重试");
        }

        String headline = renderHeadline(metrics);
        List<MetricValue> chips = renderChips(metrics);
        return new AiInsightPayload(headline, chips, Instant.now());
    }

    private String renderHeadline(Map<String, MetricValue> metrics) {
        int count = metrics.size();
        if (count == 0) {
            return templateEngine.formatHeadline(AiConstants.TMPL_HEADLINE_EMPTY);
        }
        // 简化：仅支持 0/1/≥2 三档
        if (metrics.containsKey("task") && metrics.containsKey("expense")) {
            return templateEngine.formatHeadline(
                AiConstants.TMPL_HEADLINE_FULL,
                metrics.get("task").value().toPlainString(),
                templateEngine.formatChipDelta(
                    AiConstants.CHIP_TASK_COMPLETION,
                    metrics.get("task").trend().name().toLowerCase(),
                    ""  // 简化：副标由 Controller 渲染
                ),
                metrics.get("expense").value().toPlainString(),
                ""  // 简化
            );
        }
        if (metrics.containsKey("task")) {
            return templateEngine.formatHeadline(
                AiConstants.TMPL_HEADLINE_TASK_ONLY,
                metrics.get("task").value().toPlainString(),
                "今日数据"
            );
        }
        if (metrics.containsKey("expense")) {
            return templateEngine.formatHeadline(
                AiConstants.TMPL_HEADLINE_EXPENSE_ONLY,
                metrics.get("expense").value().toPlainString(),
                "本周数据"
            );
        }
        return templateEngine.formatHeadline(AiConstants.TMPL_HEADLINE_EMPTY);
    }

    private List<MetricValue> renderChips(Map<String, MetricValue> metrics) {
        if (metrics.isEmpty()) {
            return List.of();
        }
        List<MetricValue> chips = new ArrayList<>();
        // 固定顺序：task → expense → plan
        if (metrics.containsKey("task")) {
            chips.add(metrics.get("task"));
        } else {
            chips.add(new MetricValue(null, "%", com.lifepulse.ai.model.Trend.NONE));
        }
        if (metrics.containsKey("expense")) {
            chips.add(metrics.get("expense"));
        } else {
            chips.add(new MetricValue(null, "¥", com.lifepulse.ai.model.Trend.NONE));
        }
        if (metrics.containsKey("plan")) {
            chips.add(metrics.get("plan"));
        } else {
            chips.add(new MetricValue(null, "项", com.lifepulse.ai.model.Trend.NONE));
        }
        return chips;
    }

    private AiInsightPayload readCache(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RedisConnectionFailureException e) {
            log.warn("ai cache read failed, fallback to recompute: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, AiInsightPayload payload) {
        try {
            redis.opsForValue().set(key, payload, Duration.ofMinutes(AiConstants.CACHE_TTL_MINUTES));
        } catch (RedisConnectionFailureException e) {
            log.warn("ai cache write failed, skip: {}", e.getMessage());
        }
    }

    private void evictCache(String key) {
        try {
            redis.delete(key);
        } catch (RedisConnectionFailureException e) {
            log.warn("ai cache evict failed, continue: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 注册 RedisTemplate<String, AiInsightPayload> bean**

打开 `backend/src/main/java/com/lifepulse/common/config/RedisConfig.java`（如存在），或新建：

```java
package com.lifepulse.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.ai.model.AiInsightPayload;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, AiInsightPayload> aiInsightRedisTemplate(
        RedisConnectionFactory factory, ObjectMapper objectMapper
    ) {
        var template = new RedisTemplate<String, AiInsightPayload>();
        template.setConnectionFactory(factory);
        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, AiInsightPayload.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

- [ ] **Step 5: 运行测试，预期 PASS**

```powershell
cd backend; mvn -q test -Dtest=AiInsightServiceTest
```

预期：`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/service/AiInsightService.java
git add backend/src/main/java/com/lifepulse/common/config/RedisConfig.java
git add backend/src/test/java/com/lifepulse/ai/service/AiInsightServiceTest.java
git commit -m "feat(ai): add AiInsightService with 3-layer degradation"
```

---

### Task 5.2-5.4: Service 边缘场景 + refresh + 异常路径

> Task 5.1 已包含主要单测（getOrCompute 缓存命中/未命中/单 provider 异常/全失败/模块未上线/Redis 不可用/零数据 + refresh）。T5.2-T5.4 在 Task 5.1 测试已覆盖；如需增加可在此追加。

- [ ] **Step 1: 确认 Service 行覆盖 ≥ 85%**

```powershell
cd backend; mvn -q test -Dtest=AiInsightServiceTest
# 查看覆盖率：mvn -q verify 后看 target/site/jacoco/index.html
```

预期：`AiInsightService` 行覆盖 ≥ 85%。如不足，补充以下边缘单测（按需添加）：

```java
@Test
void getOrCompute_cacheHit_doesNotRefreshTTL() {
    // 验证 redis.set 在 HIT 路径不被调用
}

@Test
void getOrCompute_redisDownOnWrite_returnsPayloadAnyway() {
    // 验证写失败不影响返回
}
```

- [ ] **Step 2: 提 PR**

PR 标题：`feat(ai): add AiInsightService with cache + 3-layer degradation`

PR 描述：
```
## 改了什么
- AiInsightService 编排 5 个 provider
- Redis 30min 缓存（命中不延长 TTL）
- 3 层降级：模块未上线 / 单 provider 异常 / 全失败 1501
- Redis 不可用降级为无缓存模式
- RedisConfig 注册 AiInsightPayload 专用 RedisTemplate

## 测试覆盖
- AiInsightServiceTest: 8+ 用例
- 行覆盖：service 包 ≥ 85%

## 影响面
- 新增 1 service + 1 config
- 无新表 / 无新依赖
```

合并到 `feat/ai-v2.0`，进入 PR 4。

---
