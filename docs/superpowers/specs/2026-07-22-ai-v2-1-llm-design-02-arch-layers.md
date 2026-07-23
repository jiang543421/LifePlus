## 4. 系统分层架构（**v2.1 新增 LLM 接入层**）

### 4.1 v2.0 既有架构（沿用）

```
Controller → Service → (Provider[] ∥ TemplateEngine) → Redis
                          ↓
                         Mapper → DB
```

调用链：`Web → Service → Provider → Mapper → DB`（单向，Service 不调外部 API）。

### 4.2 v2.1 新增后架构

```
                  ┌─────────────────────────────────┐
   HTTP ─────────▶│  AiInsightController (v2.0)     │ Web 层
                  │  + GET /analysis (新)           │
                  └────────────┬────────────────────┘
                               ▼
                  ┌─────────────────────────────────┐
                  │  AiInsightService (改造)         │ Service 层
                  │  编排：缓存 + Provider 聚合      │
                  │       + LLM 降级链 (新)          │
                  └──┬────────────────────┬──────────┘
                     │                    │
        ┌────────────▼─────────┐ ┌─────────▼──────────────────┐
        │ 4 Provider + 1 stub │ │ LlmInsightGenerator (新)    │ LLM 接入层
        │ + TemplateEngine    │ │  prompt → client → 降级     │ (v2.1)
        │ (v2.0 沿用)         │ │  唯一 LLM 业务编排点        │
        └──────────────────────┘ └─┬──────────────────┬────────┘
                                   │                  │
                          ┌────────▼─────┐    ┌───────▼───────┐
                          │ DeepSeekClient│    │ OllamaClient  │ Provider 适配
                          │ (OpenAI 协议) │    │ (本地 HTTP)   │
                          └───────────────┘    └───────────────┘
                                   │                  │
                          ┌────────▼──────────────────▼────────┐
                          │  Redis (lp:ai:insight: 6h +        │
                          │         lp:ai:quota: +             │
                          │         lp:ai:circuit: )           │ 缓存层 (v2.1 扩展)
                          └────────────────────────────────────┘
```

### 4.3 LLM 接入层（v2.1 新增子包 `com.lifepulse.ai.llm`）

| 子包 | 类型 | 职责 |
|---|---|---|
| `LlmClient` | **interface** | `generate(LlmRequest) → LlmResponse`；配置驱动 provider 切换 |
| `provider.DeepSeekClient` | `@Component` (conditional) | OpenAI 兼容 POST；`@ConditionalOnProperty(lp.ai.llm.provider=deepseek)` |
| `provider.OllamaClient` | `@Component` (conditional) | 本地 HTTP POST；`@ConditionalOnProperty(lp.ai.llm.provider=ollama)` |
| `LlmInsightGenerator` | `@Service` | 编排：构建 prompt → 调 LlmClient → 解析 JSON → 失败抛业务异常 |
| `LlmPromptBuilder` | `@Component` | 渲染 system + user prompt 模板（4 chip 数据 → 字符串） |
| `LlmJsonParser` | `@Component` | 解析 LLM JSON 输出；字段缺失/超长/非法 mood 抛异常 |
| `LlmCircuitBreaker` | `@Component` | 5min/10fail/30min 熔断（Ollama 模式自动禁用） |
| `LlmQuotaGuard` | `@Component` | 50/用户/天（Redis `INCR+EXPIRE`） |
| `LlmProperties` | `@ConfigurationProperties` | `lp.ai.llm.*` 配置组；启动期 fail fast |
| `exception.LlmQuotaExceededException` | — | 1510（Service 内部 catch → L2） |
| `exception.LlmCircuitOpenException` | — | 1511（Service 内部 catch → L2） |
| `exception.LlmResponseInvalidException` | — | 1512（Service 内部 catch → L2） |
| `exception.LlmUnavailableException` | — | 1513（Service 内部 catch → L2） |

### 4.4 调用链（v2.1 完整路径）

```
GET /api/v1/ai/insight/today
  → AiInsightController
  → RateLimiter (30/min/user, 沿用 v2.0)
  → AiInsightService.getInsight(userId)
      ├─ Redis GET lp:ai:insight:<userId>     ← 缓存命中直接返回（含 source）
      ├─ 并行 4 Provider.collect()            ← 规则计算 4 chip
      ├─ AiInsightService 调用 LlmInsightGenerator.generate(userId, metrics, today)
      │   ├─ LlmQuotaGuard.checkAndIncrement()   ← 超限抛 1510
      │   ├─ LlmCircuitBreaker.tryAcquire()      ← 熔断中抛 1511
      │   ├─ LlmPromptBuilder.build(metrics)     ← 渲染 prompt
      │   ├─ LlmClient.generate(request)         ← 5s 超时
      │   └─ LlmJsonParser.parse(response)       ← 失败抛 1512/1513
      ├─ 失败 → 走 v2.0 模板引擎 (L2 降级)
      ├─ 模板也失败 → 抛 1501 (L3 降级)
      ├─ Redis SETEX lp:ai:insight:<userId> 21600 payload
      └─ 返回 AiInsightResponse（source=llm|template）
```

### 4.5 依赖方向约束（v2.1 调整）

- **保留**：`Web → Service → Provider → Mapper → DB` 单向
- **新增**：`Service → LlmInsightGenerator → LlmClient` 子链（仍向下）
- **禁止**：`LlmClient` 调 `Service`；`LlmInsightGenerator` 调 `Controller`；`LlmClient` 直接读 Mapper

### 4.6 与 v2.0 spec §18.2 偏差表的修正

| 项 | v2.0 spec §18.2 写 | v2.0 tech-arch 实际 | v2.1 PRD 写 | v2.1 spec 写 |
|---|---|---|---|---|
| GET 限流 | 60/min/user | **30/min/user** | 60/min/user | **30/min/user**（以代码为准） |
| 缓存键 | `ai:insight:{userId}` | **`lp:ai:insight:<userId>`** | `ai:insight:{userId}` | **`lp:ai:insight:<userId>`** |
| 降级层级 | 3 层 | 4 级 | 3 层 | **3 层 LLM 降级 + 沿用 v2.0 4 级 Provider 降级** |

---
