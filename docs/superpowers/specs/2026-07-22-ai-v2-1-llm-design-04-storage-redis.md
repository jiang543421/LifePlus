## 6. 数据存储方案 — 复用 Redis 缓存（无新表）

### 6.1 总原则

| 原则 | 含义 |
|---|---|
| **不新增表** | 沿用 v2.0 5 张业务表，零 Flyway 迁移 |
| **不修改表结构** | `t_task` / `t_plan` / `t_expense` / `t_diet` / `t_daily_report*` 字段不变 |
| **Redis 命名空间扩展** | 沿用 `lp:*` 前缀，新增 3 类键 |
| **缓存值结构升级** | 在 `AiInsightResponse` 上加可空字段，**不破坏 v2.0 反序列化**（新字段给默认值） |
| **配额 / 熔断独立键** | 配额 `lp:ai:quota:` / 熔断 `lp:ai:circuit:` |

### 6.2 Redis 键空间总览

```
lp:ai:insight:<userId>              ← 洞察缓存（v2.0 沿用，TTL 改 6h）
lp:rl:ai:insight:<userId>           ← GET 限流计数（v2.0 沿用，30/min 不变）
lp:rl:ai:refresh:<userId>           ← POST refresh 限流计数（v2.0 沿用，6→3/min）
lp:ai:quota:<userId>:<yyyymmdd>     ← 【新】每日 LLM 调用配额（50/天/用户）
lp:ai:circuit:state                 ← 【新】熔断器状态（全局，不按 user）
lp:ai:circuit:state:openedAt        ← 【新】熔断开启时间戳
lp:ai:circuit:failures              ← 【新】熔断器失败计数（5min sliding window）
```

### 6.3 5 类键详细规格

#### 键 1：`lp:ai:insight:<userId>`（v2.0 沿用 + 结构升级）

| 字段 | v2.0 | v2.1 |
|---|---|---|
| 类型 | `String` (JSON) | 同 |
| TTL | 1800s (30min) | **21600s (6h)** |
| Value schema | `AiInsightResponse { headline, chips[], generatedAt, freshnessSeconds }` | `AiInsightResponse`（v2.0 字段 + `source` + `advice` + `highlight` + `mood` + `llmMeta`） |
| Key 命名 | `lp:ai:insight:<userId>` | 同（**不破 v2.0 缓存键命名**） |
| 写策略 | `SETEX` | 同 |
| 读策略 | `GET`，失败降级 | 同 |
| 兼容性 | — | v2.0 旧条目反序列化时 `source/advice/...` 取默认值 `"template"` / `null` |

```java
// 新响应 DTO（沿用 v2.0 字段 + 新增 v2.1 字段）
public record AiInsightResponse(
    String headline,                    // v2.0 沿用
    List<AiChipDto> chips,              // v2.0 沿用
    Instant generatedAt,                // v2.0 沿用
    long freshnessSeconds,              // v2.0 沿用
    // v2.1 新增字段（可空，旧缓存反序列化时取 null）
    @JsonInclude(NON_NULL) String source,        // "llm" | "template"
    @JsonInclude(NON_NULL) String advice,         // LLM 生成，模板降级时 null
    @JsonInclude(NON_NULL) String highlight,      // LLM 生成，模板降级时 null
    @JsonInclude(NON_NULL) String mood,           // "positive" | "neutral" | "cautious"
    @JsonInclude(NON_NULL) LlmMeta llmMeta        // {promptTokens, responseTokens, latencyMs}
) {}

public record LlmMeta(int promptTokens, int responseTokens, long latencyMs) {}
```

#### 键 2：`lp:ai:quota:<userId>:<yyyymmdd>`（v2.1 新增）

| 字段 | 规格 |
|---|---|
| 类型 | `String`（Redis 计数器） |
| TTL | 首次 `INCR` 时 `EXPIRE 86400`（24h 滑动到次日 0 点清零） |
| Value | 当前已用次数（int 字符串） |
| 算法 | `INCR` + `EXPIRE NX`（原子操作） |
| 超限 | 抛 `LlmQuotaExceededException` → 1510（Service 内部 catch → L2 模板） |
| 配额 | 50/天/用户（`lp.ai.llm.daily-quota=50`） |
| 失败 | Redis 不可用 → `LlmQuotaGuard` 内部 catch + log.warn + **放行**（fail-open） |

```java
@Component
public class LlmQuotaGuard {
    public void checkAndIncrement(long userId) {
        String key = "lp:ai:quota:" + userId + ":" + LocalDate.now();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                redisTemplate.expire(key, Duration.ofHours(25)); // 25h 留余量
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

#### 键 3-4：`lp:ai:circuit:state` + `lp:ai:circuit:state:openedAt` + `lp:ai:circuit:failures`（v2.1 新增）

| 字段 | 规格 |
|---|---|
| 类型 | `String`（state/openedAt）+ `Sorted Set`（failures） |
| TTL | state/openedAt 无 TTL（永久）；failures 5min 滚动 |
| 状态值 | `"CLOSED"`（正常）/ `"OPEN"`（熔断）/ `"HALF_OPEN"`（试探） |
| 失败窗口 | 5min 内连续 10 次失败 → 触发熔断 |
| 熔断时长 | 30min 后下一次请求试探一次 |
| Ollama 模式 | `circuitBreaker.enabled=false`（本地进程死掉与远程故障语义不同） |
| 失败 | Redis 不可用 → `LlmCircuitBreaker` 内部 catch + log.warn + **降级到 CLOSED**（fail-closed 即不熔断） |

```java
@Component
public class LlmCircuitBreaker {
    public void tryAcquire(long userId) {
        if (!enabled) return; // Ollama 模式跳过

        String stateKey = "lp:ai:circuit:state";
        String failuresKey = "lp:ai:circuit:failures";

        try {
            String state = redis.opsForValue().get(stateKey);
            if ("OPEN".equals(state)) {
                long openedAt = Long.parseLong(redis.opsForValue().get(stateKey + ":openedAt"));
                if (Instant.now().toEpochMilli() - openedAt < 30 * 60_000) {
                    throw new LlmCircuitOpenException();
                }
                // 30min 到期，下次试探
                redis.opsForValue().set(stateKey, "HALF_OPEN");
            }

            // 5min 滑动窗口：清理 + 计数
            long now = Instant.now().toEpochMilli();
            redis.opsForZSet().removeRangeByScore(failuresKey, 0, now - 5 * 60_000);
            Long failures = redis.opsForZSet().zCard(failuresKey);
            if (failures != null && failures >= 10) {
                redis.opsForValue().set(stateKey, "OPEN");
                redis.opsForValue().set(stateKey + ":openedAt", String.valueOf(now));
                throw new LlmCircuitOpenException();
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("LlmCircuitBreaker: redis unavailable, fail-closed for userId={}", userId);
            // 放行，不熔断
        }
    }

    public void recordSuccess() { /* 清失败计数 + state 置 CLOSED */ }
    public void recordFailure() { /* ZSET add now() */ }
}
```

### 6.4 缓存值结构（v2.1 升级）

#### 序列化格式（JSON 示例）

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

#### v2.0 → v2.1 兼容性矩阵

| 场景 | 反序列化结果 | 行为 |
|---|---|---|
| v2.0 旧条目（无 source/advice/...）| 新字段 = `null` | source 视为 `"template"`；advice/highlight 用模板补 |
| v2.1 新条目（LLM 成功）| 全部字段 | 完整渲染 |
| v2.1 新条目（LLM 降级到模板）| advice/highlight = `null` | 模板降级，source = `"template"` |
| 部分字段缺失（JSON 解析异常）| 整条作废 | 重算 |

### 6.5 TTL 与配额边界

| 项 | 值 | 计算依据 |
|---|---|---|
| 缓存 TTL | 6h | 个人项目每天 1-3 次打开；refresh 强制重算 |
| 配额 / 用户 / 天 | 50 | DeepSeek V3 ~0.01元/次 × 50 = 0.5元/天；月 15 元（实际更低）|
| 熔断窗口 | 5min | DeepSeek 抖动最长 1-2min；5min 留 2x 余量 |
| 熔断阈值 | 10fail/5min | DeepSeek 全挂概率极低，10 次相当于"全站级故障" |
| 熔断恢复 | 30min | 单次 LLM 抖动 < 5s；5min 不足以恢复时升级到 30min |
| 配额 Redis TTL | 25h | 跨过 0 点 1h 余量（避免 23:59 调用跨日遗留）|

### 6.6 失败降级的 Redis 行为

| 失败类型 | 读 | 写 | 删 |
|---|---|---|---|
| Redis 完全不可用 | 视作 MISS，走计算 | 跳过，log.warn | 跳过，log.warn |
| Redis 超时（>500ms） | 同上 | 同上 | 同上 |
| 配额 Redis 操作失败 | 配额检查放行（fail-open）| 配额计数丢失 | — |
| 熔断 Redis 操作失败 | 熔断检查放行（fail-closed 即不熔断）| 失败计数丢失 | — |

### 6.7 不存的内容（v2.1 明确不存）

| 项 | 不存的原因 | 替代方案 |
|---|---|---|
| 历史 insight 快照 | v2.0 spec §5 "无新表" | 仅缓存当前值，过期重算 |
| LLM 调用详细日志 | 日志禁打 prompt/response 全文 | Micrometer metrics 记延迟/成功/失败计数 |
| 用户偏好（chip 顺序）| v2.1 不做 | 留 v2.2+ |
| 反馈数据 | v2.1 不做 | 留 v2.2+ |
| prompt 模板版本 | 模板文件进 git | 不存 Redis |
| 熔断历史 | 仅记录窗口内 10 个时间戳 | 5min 后自动过期 |

---
