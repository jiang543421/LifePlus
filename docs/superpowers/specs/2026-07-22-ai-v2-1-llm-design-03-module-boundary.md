## 5. 模块职责划分 — `LlmInsightGenerator` 与 `AiInsightService` 的边界

### 5.1 设计原则

| 原则 | 含义 |
|---|---|
| **单向依赖** | `AiInsightService` 调 `LlmInsightGenerator`，反向不允许 |
| **职责互斥** | LLM 业务归 `LlmInsightGenerator`；编排/降级归 `Service` |
| **接口隔离** | Generator 对外只暴露 `generate()`；不暴露 LlmClient / Parser |
| **失败透明** | Generator 抛业务异常（不带 HTTP 语义）；Service 决定是否降级 |
| **配置隔离** | Generator 读 `lp.ai.llm.*`；Service 读 `lp.ai.*`（含 `enabled`）|

### 5.2 `LlmInsightGenerator` 的职责（**只**做这些）

1. 配额检查：`LlmQuotaGuard.checkAndIncrement(userId)` → 超限抛 `LlmQuotaExceededException`
2. 熔断检查：`LlmCircuitBreaker.tryAcquire(userId)` → 熔断中抛 `LlmCircuitOpenException`
3. 构建 prompt：`LlmPromptBuilder.build(metrics, ctx)` → 渲染 system + user
4. 调用 LLM：`LlmClient.generate(LlmRequest)` → 5s 超时
5. 解析响应：`LlmJsonParser.parse(LlmResponse)` → 失败抛 `LlmResponseInvalidException`
6. 构建结果：把 `LlmInsightPayload` 返回（**不写缓存、不抛 HTTP 异常**）

```java
@Service
public class LlmInsightGenerator {
    /**
     * 调 LLM 生成洞察。失败抛业务异常，由 Service 决定降级。
     * @throws LlmQuotaExceededException   1510 配额超限
     * @throws LlmCircuitOpenException    1511 熔断中
     * @throws LlmResponseInvalidException 1512 输出解析失败
     * @throws LlmUnavailableException    1513 LLM 服务不可用
     */
    public LlmInsightPayload generate(long userId, List<MetricValue> metrics, LocalDate today) {
        // 1. quotaGuard.checkAndIncrement(userId)
        // 2. circuitBreaker.tryAcquire(userId)
        // 3. LlmRequest request = promptBuilder.build(metrics, today)
        // 4. LlmResponse response = client.generate(request)  // 5s 超时
        // 5. LlmInsightPayload payload = jsonParser.parse(response)
        // 6. return payload  // 不写缓存
    }
}
```

### 5.3 `AiInsightService` 的职责（**只**做编排）

1. 缓存读写：`Redis GET/SETEX lp:ai:insight:<userId>`（v2.0 沿用，TTL 改 6h）
2. Provider 聚合：并行 4 个 `Provider.collect()`，失败不阻断
3. 调用 Generator：`LlmInsightGenerator.generate(userId, metrics, today)` → 拿 `LlmInsightPayload`
4. 降级链：
   - LLM 成功 → 用 LLM 输出，组装 `AiInsightResponse`，`source="llm"`
   - LLM 失败 → 走 `AiTemplateEngine.formatHeadline(...)`，`source="template"`
   - 模板失败 → 抛 `BusinessException(1501)` → 503
5. 写缓存：组装完 `AiInsightResponse` 后 `SETEX`
6. 决定 source：根据 LLM/模板/失败路径，**唯一**决定 `source` 字段

```java
@Service
public class AiInsightService {
    public AiInsightResponse getInsight(long userId) {
        // 1. 缓存命中 → 返
        // 2. 4 Provider 并行 collect
        // 3. try LlmInsightGenerator.generate(...)
        //    - 成功 → payload = result
        //    - 失败 (catch Llm*) → payload = null → 走 L2 模板
        // 4. 渲染 headline (LLM or template)
        // 5. 模板也失败 → throw BusinessException(1501)
        // 6. 组装 AiInsightResponse(source=...)
        // 7. Redis SETEX (失败仅 WARN)
        // 8. return
    }
}
```

### 5.4 边界对照表

| 关注点 | 归 Generator | 归 Service |
|---|---|---|
| 缓存读写 | ❌ | ✅ |
| Provider 聚合 | ❌ | ✅ |
| Prompt 拼装 | ✅ | ❌ |
| 调 LLM 客户端 | ✅（编排） | ❌ |
| JSON 解析 | ✅ | ❌ |
| 配额检查 | ✅ | ❌ |
| 熔断检查 | ✅ | ❌ |
| 失败降级决策 | ❌（只抛业务异常）| ✅（唯一决策者） |
| `source` 字段赋值 | ❌ | ✅（唯一决定者） |
| HTTP 语义转换 | ❌（抛业务异常）| ✅（`BusinessException` → 1501/503） |
| 鉴权 | ❌ | ❌（Controller 层）|
| 限流 | ❌ | ❌（Controller 层）|
| 日志 | ✅（prompt/response 摘要）| ✅（source 决策日志）|

### 5.5 `LlmInsightPayload` 输出结构

```java
// ai.llm 子包内
public record LlmInsightPayload(
    String headline,       // 必填，40-120 字
    String advice,         // 必填，30-80 字
    String highlight,      // 必填，20-60 字
    Mood mood,             // 必填，POSITIVE/NEUTRAL/CAUTIOUS
    int promptTokens,      // 调试用，Service 透传到 llmMeta
    int responseTokens,
    long latencyMs
) {}

public enum Mood { POSITIVE, NEUTRAL, CAUTIOUS }
```

### 5.6 异常分类与传播矩阵

| 异常 | 抛出位置 | Service catch 行为 | 响应 source |
|---|---|---|---|
| `LlmQuotaExceededException` | `LlmQuotaGuard` | catch → 走 L2 模板 | `"template"` |
| `LlmCircuitOpenException` | `LlmCircuitBreaker` | catch → 走 L2 模板 | `"template"` |
| `LlmUnavailableException`（5s 超时/5xx/429）| `LlmClient` | catch → 走 L2 模板 + 计入熔断 | `"template"` |
| `LlmResponseInvalidException`（4xx/JSON错/超长/敏感词）| `LlmClient` / `LlmJsonParser` | catch → 走 L2 模板 + 计入熔断 + log.error | `"template"` |
| `IllegalArgumentException`（模板占位符错位）| `AiTemplateEngine` | 不 catch → 抛 1501 | 503 |
| `BusinessException(1501)` | `AiInsightService` | 顶层 catch → 抛 | 503 |
| `RedisConnectionFailureException` | `RedisTemplate` | catch → log.warn + 跳过缓存 | 视 LLM/模板结果 |
| 任何未捕获 | `AiInsightService` | catch → 抛 500 | 500 |

> 关键点：**Generator 永远不重试**。重试是 Service 的权力（但本期不做重试，因为 LLM 抖动 5s 内重试大概率失败且拖慢响应）。

### 5.7 单测边界

| 类 | 测试归属 | Mock 范围 |
|---|---|---|
| `LlmInsightGeneratorTest` | Generator | Mock `LlmClient` / `LlmJsonParser` / `LlmQuotaGuard` / `LlmCircuitBreaker` / `LlmPromptBuilder` |
| `AiInsightServiceTest` | Service | Mock 4 Provider / `LlmInsightGenerator` / `AiTemplateEngine` / Redis |
| `DeepSeekClientTest` | Client | Mock `RestClient`（用 `MockRestServiceServer`） |
| `OllamaClientTest` | Client | Mock `RestClient`（用 `MockRestServiceServer`） |
| `LlmPromptBuilderTest` | Builder | 无 mock，纯字符串渲染 |
| `LlmJsonParserTest` | Parser | 无 mock，纯解析 |
| `LlmCircuitBreakerTest` | Breaker | Mock Redis |
| `LlmQuotaGuardTest` | Guard | Mock Redis |

---
