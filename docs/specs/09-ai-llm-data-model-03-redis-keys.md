## 2. Redis 键空间（5 类键）

### 2.1 总览

| # | 键 | 类型 | TTL | 来源 | 用途 |
|---|---|---|---|---|---|
| 1 | `lp:ai:insight:<userId>` | String (JSON) | **6h**（v2.1 改；v2.0 是 30min）| v2.0 沿用 | 洞察缓存（含 v2.1 新字段）|
| 2 | `lp:rl:ai:insight:<userId>` | String (int) | 60s | v2.0 沿用 | GET 限流 30/min/user |
| 3 | `lp:rl:ai:refresh:<userId>` | String (int) | 60s | v2.0 沿用 | POST refresh 限流（v2.1 由 6/min 改 **3/min**）|
| 4 | `lp:ai:quota:<userId>:<yyyymmdd>` | String (int) | **25h**（首次 INCR 时 EXPIRE）| **v2.1 新增** | 每日 LLM 调用配额计数 |
| 5 | `lp:ai:circuit:state` + `state:openedAt` | String | **永久**（手动清）| **v2.1 新增** | 熔断状态 |
| 6 | `lp:ai:circuit:failures` | Sorted Set (score=ms) | 5min 滑动 | **v2.1 新增** | 失败时间戳窗口（ZSET）|

> 注释：限流键属于 v2.0 沿用，命名空间是 `lp:rl:ai:*`（不是 `lp:ai:rl:*`），详见 §2.6。

### 2.2 键 1：`lp:ai:insight:<userId>`（v2.0 沿用 + 结构升级）

| 字段 | v2.0 | v2.1 |
|---|---|---|
| 类型 | `String` (JSON) | 同 |
| TTL | 1800s (30min) | **21600s (6h)** |
| Value schema | `{headline, chips[], generatedAt, freshnessSeconds}` | + `source` / `advice` / `highlight` / `mood` / `llmMeta`（可空）|
| Key 命名 | `lp:ai:insight:<userId>` | **同（不破 v2.0 缓存键命名）** |
| 写策略 | `SETEX` | 同 |
| 读策略 | `GET`，失败降级 | 同 |
| 兼容性 | — | v2.0 旧条目反序列化时新字段取 `null`，UI 不渲染 |

**Value JSON 示例**：

```json
{
  "headline": "今日任务完成率 80%，节奏紧凑但收尾在望。",
  "advice": "下午 4-6 点日程最密，建议 14:30 提前结束午休。",
  "highlight": "消费下降 12% 表现优秀；任务节奏稳定；日程密度正常。",
  "mood": "positive",
  "source": "llm",
  "chips": [
    { "key": "taskCompletion", "label": "任务完成率", "value": "80", "unit": "%", "trend": "UP", "deltaText": "较昨日 +5pp" },
    { "key": "weeklyExpense",   "label": "本周消费",   "value": "420", "unit": "¥", "trend": "DOWN", "deltaText": "较上周 -12%" },
    { "key": "planDensity",     "label": "今日日程",   "value": "4",   "unit": "项", "trend": "FLAT", "deltaText": "今日 4 项" }
  ],
  "generatedAt": "2026-07-22T17:30:00+08:00",
  "freshnessSeconds": 1234,
  "llmMeta": {
    "promptTokens": 412,
    "responseTokens": 87,
    "latencyMs": 2341
  }
}
```

### 2.3 键 2+3：限流（v2.0 沿用）

| 端点 | 键 | 阈值 | 触发 | HTTP |
|---|---|---|---|---|
| GET `/today` / `/analysis` | `lp:rl:ai:insight:<userId>` | **30/min/user** | `BusinessException(1006)` | 429 |
| POST `/refresh` | `lp:rl:ai:refresh:<userId>` | **3/min/user**（v2.0 是 6/min，v2.1 砍半）| 同上 | 429 |

**算法**：`RateLimiter.check(key, limit)` —— Redis `INCR` + `EXPIRE 60`；超限抛 `BusinessException(1006)`。

### 2.4 键 4：`lp:ai:quota:<userId>:<yyyymmdd>`（v2.1 新增）

| 字段 | 规格 |
|---|---|
| 类型 | `String`（Redis 计数器）|
| TTL | 首次 `INCR` 时 `EXPIRE 90000`（25h 留 1h 跨日余量）|
| Value | 当前已用次数（int 字符串）|
| 算法 | `INCR` + `EXPIRE NX`（原子）|
| 超限 | 抛 `LlmQuotaExceededException` → Service 内部 catch → L2 模板 |
| 配额 | 50/天/用户（`lp.ai.llm.daily-quota=50`，可配置）|
| 失败 | Redis 不可用 → `LlmQuotaGuard` 内部 catch + log.warn + **放行（fail-open）**|

```java
@Component
public class LlmQuotaGuard {
    public void checkAndIncrement(long userId) {
        String key = "lp:ai:quota:" + userId + ":" + LocalDate.now();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofHours(25));
            }
            if (count > quota) {
                throw new LlmQuotaExceededException(userId, count, quota);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("LlmQuotaGuard: redis unavailable, fail-open for userId={}", userId);
            // 放行，依赖 LLM 调用自身的失败处理
        }
    }
}
```

### 2.5 键 5+6：`lp:ai:circuit:state` + `lp:ai:circuit:failures`（v2.1 新增）

| 字段 | 规格 |
|---|---|
| 类型 | state/openedAt = `String`（永久）；failures = `Sorted Set`（score=ms，member=userId 或 timestamp）|
| 状态值 | `"CLOSED"`（正常）/ `"OPEN"`（熔断）/ `"HALF_OPEN"`（试探）|
| 失败窗口 | 5min 内连续 10 次失败 → 触发熔断 |
| 熔断时长 | 30min 后下一次请求试探一次 |
| Ollama 模式 | `circuitBreaker.enabled=false`，自动禁用（本地进程死掉与远程故障语义不同）|
| 失败 | Redis 不可用 → 内部 catch + log.warn + **降级到 CLOSED（fail-closed 即不熔断）**|

**键命名空间**：

```
lp:ai:circuit:state                    ← 全局当前状态（不按 user）
lp:ai:circuit:state:openedAt           ← 熔断开启时间戳（epoch ms）
lp:ai:circuit:failures                 ← ZSET，score=ms，member=timestamp string
```

```java
@Component
public class LlmCircuitBreaker {
    public void tryAcquire(long userId) {
        if (!enabled) return;  // Ollama 模式跳过

        try {
            String state = redis.opsForValue().get(STATE_KEY);
            if ("OPEN".equals(state)) {
                long openedAt = Long.parseLong(redis.opsForValue().get(STATE_KEY + ":openedAt"));
                if (Instant.now().toEpochMilli() - openedAt < cooldownMs) {
                    throw new LlmCircuitOpenException();
                }
                // cooldown 到期，下次试探
                redis.opsForValue().set(STATE_KEY, "HALF_OPEN");
            }

            // 5min 滑动窗口：清理 + 计数
            long now = Instant.now().toEpochMilli();
            redis.opsForZSet().removeRangeByScore(FAILURES_KEY, 0, now - windowMs);
            Long failures = redis.opsForZSet().zCard(FAILURES_KEY);
            if (failures != null && failures >= failureThreshold) {
                redis.opsForValue().set(STATE_KEY, "OPEN");
                redis.opsForValue().set(STATE_KEY + ":openedAt", String.valueOf(now));
                throw new LlmCircuitOpenException();
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("LlmCircuitBreaker: redis unavailable, fail-closed for userId={}", userId);
            // 放行，不熔断
        }
    }

    public void recordSuccess() {
        try {
            redis.opsForValue().set(STATE_KEY, "CLOSED");
            redis.delete(STATE_KEY + ":openedAt");
            redis.delete(FAILURES_KEY);
        } catch (RedisConnectionFailureException e) {
            log.warn("recordSuccess: redis unavailable, ignored", e);
        }
    }

    public void recordFailure() {
        try {
            redis.opsForZSet().add(FAILURES_KEY, String.valueOf(Instant.now().toEpochMilli()), Instant.now().toEpochMilli());
            redis.expire(FAILURES_KEY, Duration.ofMinutes(5));
        } catch (RedisConnectionFailureException e) {
            log.warn("recordFailure: redis unavailable, ignored", e);
        }
    }
}
```

### 2.6 限流键完整对照

| 键 | 阈值 | 算法 | 触发错误码 |
|---|---|---|---|
| `lp:rl:login:<ip>:<email>` | 5/min | Redis INCR + EXPIRE 60 | 1006 |
| `lp:rl:register:<ip>` | 3/min | 同上 | 1006 |
| `lp:rl:ai:insight:<userId>` | **30/min** | 同上 | 1006 |
| `lp:rl:ai:refresh:<userId>` | **3/min**（v2.1 改 6→3）| 同上 | 1006 |

> 限流键命名空间用 `lp:rl:*`（不是 `lp:ai:rl:*`），与 v2.0 命名一致。

---
