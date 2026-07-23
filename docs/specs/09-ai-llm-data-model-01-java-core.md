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
