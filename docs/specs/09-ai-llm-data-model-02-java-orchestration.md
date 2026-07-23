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
