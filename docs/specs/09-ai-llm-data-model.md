# AI 分析模块 v2.1 数据模型设计

> 版本：v0.1 · 日期：2026-07-22 · 模块：`ai`（v2.1 LLM 增强）
> 输入：[2026-07-22-ai-v2-1-llm-design.md §4-§7, §10](../superpowers/specs/2026-07-22-ai-v2-1-llm-design.md) · [07-ai-llm-prd.md §5](../prd/07-ai-llm-prd.md)
> 索引：[2026-07-22-ai-v2-1-llm-design.md](../superpowers/specs/2026-07-22-ai-v2-1-llm-design.md) · [08-ai-llm.md](../ui-prototypes/08-ai-llm.md) · [ai-tech-architecture.md §3](../architecture/ai-tech-architecture.md)

---

## 0. 设计原则

| # | 原则 | 含义 |
|---|---|---|
| P1 | **零新增表** | 沿用 v2.0 + MVP2 5 张业务表（`t_user` / `t_refresh_token` / `t_task` / `t_plan` / `t_expense` / `t_diet` / `t_daily_report`），Flyway 零迁移 |
| P2 | **不修改既有字段** | 5 张业务表字段语义不变；AI 模块只读，不写 |
| P3 | **Redis 命名空间扩展** | 沿用 `lp:*` 前缀，新增 `lp:ai:quota:*` / `lp:ai:circuit:*` 两类键 |
| P4 | **DTO 向后兼容** | `AiInsightResponse` 新增字段用 `@JsonInclude(NON_NULL)` 序列化，v2.0 旧缓存条目可被 v2.1 代码反序列化（缺失字段取 `null`）|
| P5 | **不可变优先** | 所有 record + `@Builder` 替代 setter；集合返回 `List.copyOf` |
| P6 | **配置全部走 `@ConfigurationProperties`** | `LlmProperties` 启动期 fail fast（密钥缺失 / 占位符 → `IllegalStateException`）|

---

## 1. Java 类结构（新增）

### 1.1 总览

```
com.lifepulse.ai/
├── config/
│   └── AiInsightProperties              [v2.0 沿用] lp.ai.* 配置组
└── llm/                                 [v2.1 新增子包]
    ├── LlmClient                        (interface)
    ├── LlmRequest                       (record, 不可变)
    ├── LlmResponse                      (record, 不可变)
    ├── LlmInsightPayload                (record, 不可变)
    ├── LlmMeta                          (record, 不可变)
    ├── Mood                             (enum)
    ├── LlmInsightGenerator              (@Service, 编排入口)
    ├── LlmPromptBuilder                 (@Component)
    ├── LlmJsonParser                    (@Component)
    ├── LlmCircuitBreaker                (@Component)
    ├── LlmQuotaGuard                    (@Component)
    ├── LlmProperties                    (@ConfigurationProperties("lp.ai.llm"))
    ├── provider/
    │   ├── DeepSeekClient               (@Component @ConditionalOnProperty)
    │   └── OllamaClient                 (@Component @ConditionalOnProperty)
    └── exception/
        ├── LlmQuotaExceededException    (extends RuntimeException)
        ├── LlmCircuitOpenException      (extends RuntimeException)
        ├── LlmUnavailableException      (extends RuntimeException)
        └── LlmResponseInvalidException  (extends RuntimeException)
```

### 1.2 `LlmProperties`（`@ConfigurationProperties("lp.ai.llm")`）

> 启动期 `@Validated` 校验；密钥缺失 / 占位符 / 长度不足 → `IllegalStateException` → Spring 启动失败（fail fast）。

```java
@Component
@ConfigurationProperties("lp.ai.llm")
@Validated
public record LlmProperties(
    boolean enabled,
    @NotBlank String provider,              // "deepseek" | "ollama"
    @URL String baseUrl,
    String apiKey,                          // deepseek 必填；ollama 可空
    @NotBlank String model,
    @Min(1000) @Max(30000) int timeoutMs,
    @Min(100) @Max(4000) int maxPromptTokens,
    @Min(50) @Max(1000) int maxResponseTokens,
    @Min(1) @Max(1000) int dailyQuota,
    CircuitBreaker circuitBreaker
) {
    public LlmProperties {
        if (enabled && "deepseek".equals(provider)) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                    "lp.ai.llm.api-key is required when provider=deepseek. " +
                    "Set LP_LLM_API_KEY env var or disable llm (lp.ai.llm.enabled=false).");
            }
            if (apiKey.startsWith("sk-replace-") || apiKey.length() < 20) {
                throw new IllegalStateException(
                    "lp.ai.llm.api-key looks like a placeholder. " +
                    "Replace LP_LLM_API_KEY with a real DeepSeek key.");
            }
        }
        if ("ollama".equals(provider) && circuitBreaker.enabled()) {
            log.warn("lp.ai.llm.circuit-breaker.auto-disabled for ollama provider");
        }
    }

    public record CircuitBreaker(
        boolean enabled,
        @Min(1) @Max(100) int failureThreshold,
        @Min(1) @Max(60) int windowMinutes,
        @Min(1) @Max(1440) int cooldownMinutes
    ) {}
}
```

| 字段 | 类型 | 默认 | 占位符 | 校验 |
|---|---|---|---|---|
| `enabled` | boolean | `true` | `${LP_LLM_ENABLED:true}` | boolean |
| `provider` | String | `deepseek` | `${LP_LLM_PROVIDER:deepseek}` | enum |
| `baseUrl` | String | `https://api.deepseek.com/v1` | `${LP_LLM_BASE_URL:...}` | URL 格式 |
| `apiKey` | String | **空** | `${LP_LLM_API_KEY:}` | **deepseek 必填，≥20 字符，非占位符** |
| `model` | String | `deepseek-chat` | `${LP_LLM_MODEL:deepseek-chat}` | 非空 |
| `timeoutMs` | int | `5000` | `${LP_LLM_TIMEOUT_MS:5000}` | 1000-30000 |
| `maxPromptTokens` | int | `1500` | `${LP_LLM_MAX_PROMPT_TOKENS:1500}` | 100-4000 |
| `maxResponseTokens` | int | `300` | `${LP_LLM_MAX_RESPONSE_TOKENS:300}` | 50-1000 |
| `dailyQuota` | int | `50` | `${LP_LLM_DAILY_QUOTA:50}` | 1-1000 |
| `circuitBreaker.enabled` | boolean | `true` | `${LP_LLM_CB_ENABLED:true}` | boolean（ollama 自动禁用）|
| `circuitBreaker.failureThreshold` | int | `10` | `${LP_LLM_CB_THRESHOLD:10}` | 1-100 |
| `circuitBreaker.windowMinutes` | int | `5` | `${LP_LLM_CB_WINDOW:5}` | 1-60 |
| `circuitBreaker.cooldownMinutes` | int | `30` | `${LP_LLM_CB_COOLDOWN:30}` | 1-1440 |

### 1.3 `LlmRequest`（不可变 record）

```java
public record LlmRequest(
    String systemPrompt,       // 角色定位（system role）
    String userPrompt,         // 数据 + 引导（user role）
    int maxResponseTokens,     // 上限（来自 LlmProperties.maxResponseTokens）
    Duration timeout           // 超时（来自 LlmProperties.timeoutMs）
) {
    public LlmRequest {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt is required");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt is required");
        }
        if (maxResponseTokens <= 0 || maxResponseTokens > 1000) {
            throw new IllegalArgumentException("maxResponseTokens out of range");
        }
    }
}
```

### 1.4 `LlmResponse`（不可变 record）

```java
public record LlmResponse(
    String content,            // LLM 返回的字符串（OpenAI 协议 `choices[0].message.content`）
    int promptTokens,          // `usage.prompt_tokens`
    int responseTokens,        // `usage.completion_tokens`
    long latencyMs             // 从调用到收到 response 的耗时（毫秒）
) {
    public static LlmResponse empty() {
        return new LlmResponse("", 0, 0, 0L);
    }
}
```

### 1.5 `LlmInsightPayload`（不可变 record，Generator 输出）

```java
public record LlmInsightPayload(
    String headline,           // 必填，40-120 字中文
    String advice,             // 必填，30-80 字中文
    String highlight,          // 必填，20-60 字中文
    Mood mood,                 // 必填，POSITIVE/NEUTRAL/CAUTIOUS
    int promptTokens,          // 调试用，Service 透传到 AiInsightResponse.llmMeta
    int responseTokens,
    long latencyMs
) {}

public enum Mood {
    POSITIVE, NEUTRAL, CAUTIOUS;

    @JsonCreator
    public static Mood fromString(String s) {
        if (s == null) return NEUTRAL;
        return switch (s.toLowerCase()) {
            case "positive" -> POSITIVE;
            case "neutral"  -> NEUTRAL;
            case "cautious" -> CAUTIOUS;
            default -> {
                log.warn("LlmJsonParser: unknown mood '{}', clamped to NEUTRAL", s);
                yield NEUTRAL;
            }
        };
    }
}
```

### 1.6 `LlmMeta`（不可变 record，嵌入响应）

```java
public record LlmMeta(
    int promptTokens,
    int responseTokens,
    long latencyMs
) {}
```

### 1.7 `LlmClient`（接口）

```java
public interface LlmClient {
    /**
     * 调用 LLM 生成内容。
     * @throws LlmUnavailableException      5xx / 429 / 超时 / 网络错误
     * @throws LlmResponseInvalidException  4xx / 响应解析失败 / 非 JSON
     */
    LlmResponse generate(LlmRequest request);
}
```

**实现**：

| 实现 | 条件 | 协议 | URL 模式 |
|---|---|---|---|
| `DeepSeekClient` | `@ConditionalOnProperty(prefix = "lp.ai.llm", name = "provider", havingValue = "deepseek")` | OpenAI 兼容 | `POST {baseUrl}/chat/completions` |
| `OllamaClient` | `@ConditionalOnProperty(prefix = "lp.ai.llm", name = "provider", havingValue = "ollama")` | Ollama 原生 | `POST {baseUrl}/api/chat`（`format: "json"`）|

### 1.8 `LlmInsightGenerator`（编排入口）

```java
@Service
public class LlmInsightGenerator {
    /**
     * 调 LLM 生成洞察。失败抛业务异常，由 AiInsightService 决定降级。
     * @throws LlmQuotaExceededException   1510 配额超限
     * @throws LlmCircuitOpenException    1511 熔断中
     * @throws LlmResponseInvalidException 1512 输出解析失败
     * @throws LlmUnavailableException    1513 LLM 服务不可用
     */
    public LlmInsightPayload generate(long userId, List<MetricValue> metrics, LocalDate today) {
        // 1. quotaGuard.checkAndIncrement(userId)
        // 2. circuitBreaker.tryAcquire(userId)
        // 3. LlmRequest request = promptBuilder.build(metrics, today)
        // 4. LlmResponse response = client.generate(request)
        // 5. circuitBreaker.recordSuccess()  ← 成功
        // 6. LlmInsightPayload payload = jsonParser.parse(response)
        // 7. return payload  // 不写缓存，不抛 HTTP 异常
    }
}
```

### 1.9 `LlmPromptBuilder` / `LlmJsonParser`

```java
@Component
public class LlmPromptBuilder {
    /**
     * 渲染 system + user prompt。
     * 4 chip 数据 + 用户提示拼接，输出 LlmRequest。
     */
    public LlmRequest build(long userId, List<MetricValue> metrics, LocalDate today);
}

@Component
public class LlmJsonParser {
    /**
     * 解析 LLM JSON 输出。
     * @throws LlmResponseInvalidException 字段缺失/超长/非法 mood
     */
    public LlmInsightPayload parse(LlmResponse response);
}
```

### 1.10 `LlmCircuitBreaker` / `LlmQuotaGuard`

```java
@Component
public class LlmCircuitBreaker {
    /**
     * 检查熔断状态。失败时抛 LlmCircuitOpenException。
     * Ollama 模式 enabled=false，自动跳过。
     * Redis 不可用 → fail-closed（放行）。
     */
    public void tryAcquire(long userId);

    /** LLM 成功时调用：清失败计数 + state 置 CLOSED。 */
    public void recordSuccess();

    /** LLM 失败时调用：ZSET add now()，触发熔断判读。 */
    public void recordFailure();
}

@Component
public class LlmQuotaGuard {
    /**
     * 配额自增。超限抛 LlmQuotaExceededException。
     * Redis 不可用 → fail-open（放行）。
     */
    public void checkAndIncrement(long userId);
}
```

### 1.11 4 个异常类

```java
public class LlmQuotaExceededException extends RuntimeException {
    private final long userId;
    private final long count;
    private final long limit;

    public LlmQuotaExceededException(long userId, long count, long limit) {
        super(String.format("userId=%d, quota=%d/%d", userId, count, limit));
    }
}

public class LlmCircuitOpenException extends RuntimeException {
    public LlmCircuitOpenException() {
        super("LLM circuit breaker is OPEN");
    }
}

public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message, Throwable cause) { super(message, cause); }
}

public class LlmResponseInvalidException extends RuntimeException {
    public LlmResponseInvalidException(String message, Throwable cause) { super(message, cause); }
}
```

### 1.12 `AiInsightResponse` 扩展（v2.0 + v2.1）

```java
public record AiInsightResponse(
    // === v2.0 沿用字段 ===
    String headline,                          // v2.0 字段（v2.1 可被 LLM 重写）
    List<AiChipDto> chips,                    // v2.0 沿用
    Instant generatedAt,                      // v2.0 沿用
    long freshnessSeconds,                    // v2.0 沿用，Controller 现算

    // === v2.1 新增字段（@JsonInclude(NON_NULL)，旧缓存反序列化时取 null） ===
    @JsonInclude(NON_NULL) String source,     // "llm" | "template"
    @JsonInclude(NON_NULL) String advice,     // LLM 生成，模板降级时 null
    @JsonInclude(NON_NULL) String highlight,  // LLM 生成，模板降级时 null
    @JsonInclude(NON_NULL) String mood,       // "positive" | "neutral" | "cautious"
    @JsonInclude(NON_NULL) LlmMeta llmMeta    // {promptTokens, responseTokens, latencyMs}
) {}
```

**v2.0 → v2.1 兼容性矩阵**：

| 场景 | 反序列化结果 | 行为 |
|---|---|---|
| v2.0 旧条目（无 v2.1 字段）| `source/advice/highlight/mood/llmMeta` = `null` | source 视为 `"template"`；advice/highlight/mood 不展示（UI 不渲染 null）|
| v2.1 新条目（LLM 成功）| 全部字段非 null | 完整渲染 + source="AI 生成" |
| v2.1 新条目（模板降级）| `advice/highlight/mood/llmMeta` = `null` | 模板降级，source="模板" |
| 部分字段缺失（JSON 解析异常）| 整条作废 | 重算 |

---

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

## 3. 无新表声明（hard rule）

### 3.1 现状（v2.0 基线）

| 表 | 模块 | 用途 | AI 模块用法 |
|---|---|---|---|
| `t_user` | auth | 用户基本信息 | 不读（鉴权由 JwtAuthFilter 处理）|
| `t_refresh_token` | auth | refresh token 哈希 | 不读 |
| `t_task` | task | 任务 | **只读**（`TaskAiProvider` 读当日完成率）|
| `t_plan` | plan | 日程 | **只读**（`PlanAiProvider` 读当日活动分钟）|
| `t_expense` | expense | 消费 | **只读**（`ExpenseAiProvider` 读今日/本周）|
| `t_diet` | diet | 饮食 | **只读**（`DietAiProvider` 读今日热量）|
| `t_daily_report` | daily | 日报聚合 | **可选只读**（`DailyAiProvider` 开关由 `lp.ai.daily-enabled` 控制）|

### 3.2 v2.1 改动（与 v2.0 完全相同）

| 项 | v2.0 | v2.1 |
|---|---|---|
| 新增表 | ❌ 无 | ❌ **无** |
| 新增列 | ❌ 无 | ❌ **无** |
| 新增索引 | ❌ 无 | ❌ **无** |
| Flyway 迁移 | ❌ 无 | ❌ **无** |
| 数据模型语义变更 | ❌ 无 | ❌ **无** |

**结论**：v2.1 **不修改任何数据库结构**。零 Flyway 迁移，零迁移脚本文件。

### 3.3 跨用户访问约束（沿用 CLAUDE.md §7.2）

| 关注点 | 规则 |
|---|---|
| Provider Mapper 调用 | 必传 `UserContext.current()` 取出的 `userId`，**禁止从请求参数取** |
| 缓存键 | 全部按 `<userId>` 隔离：`lp:ai:insight:<userId>` / `lp:ai:quota:<userId>:<yyyymmdd>` |
| 跨用户访问 | 不可能发生（端点不接受 userId 参数；JWT 解析即得） |
| 越权响应 | 1003 / 403（防御性兜底，本期不触发） |

### 3.4 "无新表"约束的边界

| 不破 | 破 |
|---|---|
| ❌ 新增 `t_ai_insight` 表存历史 | — |
| ❌ 新增 `t_user.ai_llm_enabled` 字段 | — |
| ❌ 新增 `t_ai_feedback` 表存赞同/不赞同 | — |
| ❌ 新增 `t_ai_prompt_version` 表存 prompt 版本 | — |
| ❌ 任何 Flyway V*.sql 文件 | — |

> 任何"应该存 DB 的"内容，本期通过 Redis 短期缓存 + 日志/metrics 兜底；如必须存，留 v2.2+ 评估。

---

## 4. 字段长度与边界总表

### 4.1 LLM 输出字段约束

| 字段 | 必填 | 最小长度 | 最大长度 | 校验失败处理 |
|---|---|---|---|---|
| `headline` | ✅ | 20 字 | 200 字 | 抛 `LlmResponseInvalidException` → L2 降级 |
| `advice` | ✅ | 10 字 | 200 字 | 同上 |
| `highlight` | ✅ | 10 字 | 200 字 | 同上 |
| `mood` | ✅ | — | — | 钳到 `NEUTRAL`（不算失败）|

> 长度按"字符数"算（不是字节数），中文 1 字 = 1 字符。

### 4.2 Redis 键与值边界

| 键 | Value 最大字节 | 估算 |
|---|---|---|
| `lp:ai:insight:<userId>` | ~2 KB | 4 chip × 200B + headline 200B + advice 200B + highlight 200B + llmMeta 100B + JSON 开销 |
| `lp:ai:quota:*` | 4 字节 | int（"50"）|
| `lp:ai:circuit:state` | 16 字节 | enum string |
| `lp:ai:circuit:state:openedAt` | 16 字节 | epoch ms |
| `lp:ai:circuit:failures` | ~300 B | 10 × "1700000000000" |

### 4.3 配额与熔断阈值边界

| 项 | 默认 | 范围 | 来源 |
|---|---|---|---|
| LLM 配额 | 50/用户/天 | 1-1000 | `lp.ai.llm.daily-quota` |
| 熔断阈值 | 10 失败/5min | 1-100 / 1-60min | `lp.ai.llm.circuit-breaker.*` |
| 熔断恢复 | 30min | 1-1440min | 同上 |
| 缓存 TTL | 6h | 60s-24h | `lp.ai.insight.cache-ttl`（可扩展配置项）|

---

## 5. 类型与序列化约定

### 5.1 JSON 序列化（Jackson）

| 注解 | 应用对象 | 效果 |
|---|---|---|
| `@JsonInclude(NON_NULL)` | `AiInsightResponse` v2.1 字段 | 字段为 null 时不序列化（避免空字段污染响应）|
| `@JsonInclude(NON_NULL)` | `LlmMeta` | 同上 |
| `@JsonCreator` | `Mood.fromString` | LLM 输出 `mood` 字符串自动转 enum |
| `@JsonProperty` | `LlmInsightPayload.promptTokens` 等 | 字段名映射（默认 camelCase，无须显式）|

### 5.2 时间与时区

| 类型 | 序列化 | 反序列化 |
|---|---|---|
| `Instant` | ISO-8601（含时区）：`2026-07-22T17:30:00+08:00` | 自动解析 |
| `LocalDate` | `yyyy-MM-dd`：`2026-07-22` | 自动解析 |
| `Duration` | 仅内部使用，**不序列化** | — |

### 5.3 BigDecimal（v2.0 沿用）

| 场景 | 序列化 | 用途 |
|---|---|---|
| chip value | 字符串（如 `"80"`）| 避免前端精度丢失（前端用 string 展示） |
| 内部计算 | `BigDecimal` | 保留精度 |

> v2.1 沿用 v2.0 的字符串序列化策略，不变更。

### 5.4 集合返回（CLAUDE.md §4.1）

| 类型 | 规则 |
|---|---|
| `List<MetricValue>` | Provider 返回 `List.copyOf(...)`，禁止 mutation |
| `List<AiChipDto>` | Service 返回 `List.copyOf(...)` |

---

## 6. 失败降级的存储行为

| 失败类型 | 读 | 写 | 删 |
|---|---|---|---|
| Redis 完全不可用 | 视作 MISS，走计算 | 跳过，log.warn | 跳过，log.warn |
| Redis 超时（>500ms） | 同上 | 同上 | 同上 |
| 配额 Redis 操作失败 | 配额检查放行（fail-open）| 配额计数丢失 | — |
| 熔断 Redis 操作失败 | 熔断检查放行（fail-closed 即不熔断）| 失败计数丢失 | — |
| DB 不可用（Provider 抛错）| Service catch → 该 chip = NONE | — | — |

> **核心原则**：Redis 任何失败不阻断响应；DB 失败部分 chip 退化为 NONE，整体仍返 200。

---

## 7. 不存的内容（v2.1 明确不存）

| 项 | 不存的原因 | 替代方案 |
|---|---|---|
| 历史 insight 快照 | "无新表" 硬约束 | 仅缓存当前值，过期重算 |
| LLM 调用详细日志 | 日志禁打 prompt/response 全文 | Micrometer metrics 记延迟/成功/失败计数 |
| 用户偏好（chip 顺序）| v2.1 不做 | 留 v2.2+ |
| 反馈数据 | v2.1 不做 | 留 v2.2+ |
| prompt 模板版本 | 模板文件进 git | 不存 Redis |
| 熔断历史 | 仅记录窗口内 10 个时间戳 | 5min 后自动过期 |

---

## 8. 引用

- 主 Spec：[2026-07-22-ai-v2-1-llm-design.md](../superpowers/specs/2026-07-22-ai-v2-1-llm-design.md)
- v2.1 PRD：[07-ai-llm-prd.md](../prd/07-ai-llm-prd.md) §5
- v2.0 技术架构：[ai-tech-architecture.md §3](../architecture/ai-tech-architecture.md)（缓存键命名 / 限流阈值的修正源）
- v2.0 业务架构：[ai-business-architecture.md §2](../architecture/ai-business-architecture.md)（模块列表）
- API 规范沿用：[03-api-auth.md §5.3](03-api-auth.md)（MyResponse 信封 / ErrorCode）
- 数据库规范沿用：[02-database.md §2](02-database.md)（不新增表的约束源）
- UI 原型：[08-ai-llm.md](../ui-prototypes/08-ai-llm.md)

---

*Status: DRAFT - 待用户审 + 后续 writing-plans 引用*