# LifePulse AI 分析模块 v2.1 — 设计规格

- **Status:** Ready for User Review（spec 自检已通过）
- **Date:** 2026-07-22
- **Author:** 资深 PM 视角
- **Scope:** v2.1（LLM 增强 + 独立分析页；基于 v2.0 增量）
- **Branch:** `feat/ai-llm-v2.1`
- **Source of truth:** 本文件 + PRD `docs/prd/07-ai-llm-prd.md` + v2.0 spec `docs/superpowers/specs/2026-07-21-ai-v2-design.md`（基线）
- **替代:** v2.0 spec §1 硬约束"不引入 LLM"（同步在 v2.0 spec §19 加修订说明）

---

## 1. 背景

v2.0（已发布，tag `v2.0.0-ai`）实现了 AI 智能卡 + 详情抽屉，5 个 Provider 聚合 + 30min Redis 缓存 + 模板引擎降级（4 级 L0/L1/L2/L3）。但 v2.0 显式不做：
- LLM 调用（纯规则 + 模板）
- 独立分析页 `/ai-analysis`（抽屉里留了"v2.1 将推出..."承诺）
- 趋势迷你图 / 用户反馈 / chip 顺序可调

v2.1 目标：
1. **兑现 v2.0 承诺**：独立分析页 `/ai-analysis`
2. **突破 v2.0 限制**：引入 LLM（DeepSeek 主选 + Ollama 备选）生成更智能的 headline/advice/highlight
3. **保留 v2.0 基线**：模板引擎降级、缓存架构、Provider 聚合全部沿用

**约束（v2.0 红线）**：
- 1 人团队；显式不做团队/共享/图片识别/支付订阅/多语言
- 不引入新数据库表（PRD §5.3 红线）
- 不修改既有数据模型
- 引入 LLM 但通过 3 层降级保证 v2.0 模板引擎仍是 fallback

**约束（v2.1 新增）**：
- 不引入新第三方依赖（手写 Spring `RestClient` 调 OpenAI 兼容协议，0 Maven 依赖）
- LLM 调用月成本 ≤ 5 元（DeepSeek V3 估算）
- 每日 LLM 调用 ≤ 50/用户/天（配额硬上限）
- 启动期密钥缺失 / 占位符必失败（fail fast）

---

## 2. 目标与非目标

### 2.1 目标（v2.1 必须交付）

| # | 目标 | 衡量 |
|---|---|---|
| G1 | 智能卡 headline 升级为 LLM 生成 | headline 含"判断"（紧凑/略松/正常/异常）+ 数字；AC 见 PRD §4 US-1 |
| G2 | 抽屉 → 独立分析页 `/ai-analysis` 跳转 | 抽屉底部"v2.1 将推出..."改"查看完整分析 →"；AC 见 PRD §4 US-2 |
| G3 | 独立分析页 4 段内容（headline / advice / highlight / chips）| 单次 LLM 调用，JSON schema `{headline, advice, highlight, mood}` |
| G4 | 3 层降级链路（L1 LLM → L2 模板 → L3 1501）| L1 失败必走 L2；L2 失败必抛 1501；用户无感 |
| G5 | DeepSeek + Ollama 双 provider 切换 | `lp.ai.llm.provider` 配置切换；功能不变；AC 见 PRD §4 US-3 |
| G6 | 全套 6 项成本控制 | 缓存 6h / 限流 30+3 per min / 配额 50 per day / 熔断 5min-10fail-30min / token 1500+300 / 4 个新错误码 |
| G7 | 端到端鉴权 + 跨用户隔离 | 缓存键按 userId 分；4 Provider 全部按 userId 过滤 |
| G8 | 测试覆盖达标 | Service ≥ 85%；LlmInsightGenerator ≥ 90%；E2E ≥ 5 用例 |
| G9 | 安全：API Key 环境变量管理 | `LP_LLM_API_KEY` 走 `${...}` 占位符；启动期 fail fast；gitleaks 钩子 |
| G10 | 文档与发布 | v2.1 spec + v2.0 spec 修订 + RELEASES + README + 00-index |

### 2.2 非目标（v2.1 明确不做）

- ❌ 趋势迷你图 / 折线图（需历史数据存储，v2.0 spec §5 "无新表" 拒绝；留 v2.2）
- ❌ 历史 insight 落 `t_ai_insight` 表 + 7 天回看（同上）
- ❌ 多 tab 切换（今日 / 本周 / 本月）—— 每次 tab 调 LLM 成本 ×3
- ❌ "反馈"按钮（赞同/不赞同）—— 需新表 + 异步队列
- ❌ chip 顺序用户可调（持久化到 t_user）—— 需新字段 + UI
- ❌ 连续 N 天无 insight 引导 —— 等 v2.1 用户反馈再决定
- ❌ dry-run 模式（LLM 输出但不入响应）—— 调试改 yml 即可
- ❌ 用户级 LLM 开关（`t_user.ai_llm_enabled` 字段 + SettingsView toggle）—— 1 人项目无价值
- ❌ 月度预算上限 —— DeepSeek 成本 < 5 元/月，不需要
- ❌ 多语言 i18n —— v2.0 不做，v2.1 也不做
- ❌ Spring AI 库 —— 手写 RestClient 200 行够用
- ❌ 提示词可视化调试工具
- ❌ LLM 输出 A/B 测试

---

## 3. 用户价值

**目标用户**：LifePulse 单人用户（开发者本人）；自托管极客（Ollama 模式）。

**使用场景**：
- 每天打开首页 → 第一眼看到 AI 卡 headline（含"判断"和"建议"） → 1 秒判断今天能否收尾
- 需要时点开抽屉看完整洞察 → 点"查看完整分析 →"跳独立分析页 → 4 段结构化内容
- 不想用 SaaS AI 时改 yml 切换 Ollama → 全本地运行，token 不出境

**核心价值**：**"AI 升级的当日状态总览"** —— 智能卡从"数字播报"升级为"判断 + 建议 + 亮点/关注点"，独立分析页兑现 v2.0 承诺。

---

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

## 7. API 规格

### 7.1 端点

| 方法 | 路径 | 说明 | 鉴权 | 限流 | 状态 |
|---|---|---|---|---|---|
| GET | `/api/v1/ai/insight/today` | 返回当前洞察（缓存优先）| access token | 30/min/user | v2.0 沿用 |
| GET | `/api/v1/ai/insight/analysis` | 返回洞察（独立分析页用，与 today 共享缓存）| access token | 30/min/user | **v2.1 新增** |
| POST | `/api/v1/ai/insight/refresh` | 清缓存 + 立即重算 | access token | **3/min/user**（v2.0 是 6/min）| v2.0 改造 |

### 7.2 响应载荷（v2.1 升级）

`AiInsightResponse` 见 §6.3。响应示例见 §6.4。

### 7.3 字段约束

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `headline` | string | ✅ | 40-120 字中文（v2.0 沿用）|
| `advice` | string | ❌ | 30-80 字中文，LLM 降级时 null |
| `highlight` | string | ❌ | 20-60 字中文，LLM 降级时 null |
| `mood` | enum | ❌ | `POSITIVE` / `NEUTRAL` / `CAUTIOUS`，LLM 降级时 null |
| `source` | enum | ✅ | `"llm"` / `"template"`（v2.0 旧条目反序列化为 `"template"`）|
| `chips` | array | ✅ | 4 chip（v2.0 沿用）|
| `generatedAt` | ISO-8601 | ✅ | 服务端生成时间，含时区 |
| `freshnessSeconds` | int | ✅ | Controller 现算 |
| `llmMeta` | object | ❌ | `{promptTokens, responseTokens, latencyMs}`，仅 LLM 成功时存在 |

### 7.4 错误码

| code | 含义 | HTTP | 触发 | 是否直接返 |
|---|---|---|---|---|
| 0 | 成功 | 200 | 正常 | ✅ |
| 1003 | 跨用户越权 | 403 | （本期不触发，端点不接受 userId 参数）| ❌ |
| 1006 | 操作过于频繁（分钟级）| 429 | RateLimiter 命中 | ✅ |
| 1500 | 系统异常 | 500 | 未捕获 | ✅ |
| 1501 | AI 服务不可用 | 503 | L3 降级（LLM 失败 + 模板失败）| ✅ |
| 1510 | LLM 配额超限 | 429 | `LlmQuotaExceededException` | ❌（Service catch → L2）|
| 1511 | LLM 熔断中 | 503 | `LlmCircuitOpenException` | ❌（Service catch → L2）|
| 1512 | LLM 响应解析失败 | 503 | `LlmResponseInvalidException` | ❌（Service catch → L2）|
| 1513 | LLM 不可用 | 503 | `LlmUnavailableException` | ❌（Service catch → L2）|

> 关键设计：**1510/1511/1512/1513 这 4 个错误码几乎不会直接出现在响应中**——Service 内部全部 catch 走 L2 模板。它们主要在日志 / metrics 中体现，**真实给用户看到的只有 0/1006/1501/500**。

---

## 8. 高可用策略 — 3 层降级链路 L1 LLM → L2 模板 → L3 1501

### 8.1 3 层降级状态机

```
                  ┌─────────────────────────┐
                  │  Entry: AiInsightService │
                  │       .getInsight()     │
                  └────────────┬────────────┘
                               │
                  ┌────────────▼────────────┐
                  │  Redis 缓存命中？        │
                  └─┬─────────────────────┬─┘
                    │ Yes                 │ No
                    ▼                     ▼
              ┌──────────┐         ┌──────────────┐
              │ 直接返回 │         │ 4 Provider 聚合│
              │ (含 source│         └──────┬───────┘
              │  字段)   │                │
              └──────────┘                ▼
                              ┌─────────────────────┐
                              │ L1: LlmInsightGenerator│
                              │   .generate()         │
                              └─┬───────────────┬───┘
                                │ Success       │ Failure
                                ▼               ▼
                        ┌──────────────┐  ┌────────────────────┐
                        │ source=llm   │  │ catch Llm*Exception │
                        │ 组装 Response│  │  ├─ quota 1510     │
                        │ SETEX 缓存   │  │  ├─ circuit 1511   │
                        │ 返回         │  │  ├─ invalid 1512   │
                        └──────────────┘  │  └─ unavailable 1513│
                                          └──────┬─────────────┘
                                                 │ (全部 catch)
                                                 ▼
                                       ┌─────────────────────┐
                                       │ L2: AiTemplateEngine │
                                       │   .formatHeadline()  │
                                       │   + 3 formatChipDelta│
                                       └─┬───────────────┬───┘
                                         │ Success       │ Failure
                                         ▼               ▼
                                  ┌──────────────┐ ┌──────────────┐
                                  │ source=template│ │ L3: 1501    │
                                  │ 组装 Response │ │ 抛 Business  │
                                  │ SETEX 缓存   │ │ Exception    │
                                  │ 返回         │ │ → HTTP 503   │
                                  └──────────────┘ └──────────────┘
```

### 8.2 L1 → L2 触发场景分类

> **两类场景**:
> - **A 类：L1 失败降级**（前 9 行）—— LLM 被调用但抛异常，Service catch → L2；按规则计入熔断
> - **B 类：绕开 L1**（后 5 行）—— 根本没调 LLM，直接走 L2；不计入熔断
> 两类最终结果都是 `source="template"`，但语义和用量不同：A 类是 "LLM 失败回退"，B 类是 "无意义调用跳过"。

| 类别 | 触发场景 | 异常类型 | LLM 用量 | Service 行为 |
|---|---|---|---|---|
| A | DeepSeek 5xx | `LlmUnavailableException` | 1 次失败计入熔断 | catch → L2 |
| A | DeepSeek 429 | `LlmUnavailableException` | 1 次失败计入熔断 | catch → L2（**不重试**）|
| A | DeepSeek 4xx（非 429）| `LlmResponseInvalidException` | 1 次失败计入熔断 | catch → L2 + log.error（配置/账号问题）|
| A | Ollama 不可用 | `LlmUnavailableException` | 1 次失败（Ollama 不计入熔断）| catch → L2 |
| A | 网络超时 > 5s | `LlmUnavailableException` | 1 次失败计入熔断 | catch → L2 |
| A | LLM 返回非 JSON | `LlmResponseInvalidException` | 1 次失败计入熔断 | catch → L2 |
| A | JSON 缺 `headline` 字段 | `LlmResponseInvalidException` | 1 次失败计入熔断 | catch → L2 |
| A | `headline` 长度 < 20 或 > 200 | `LlmResponseInvalidException` | 1 次失败计入熔断 | catch → L2 |
| A | `advice/highlight` 含敏感词 | `LlmResponseInvalidException` | 1 次失败计入熔断 | catch → L2 + log.error |
| — | `mood` 字段值非法 | 钳到 `NEUTRAL`（不算失败）| 0 失败 | 继续（不算降级）|
| B | 配额超限 | `LlmQuotaExceededException` | 0（不计入熔断）| catch → L2 + `source="template"` |
| B | 熔断中 | `LlmCircuitOpenException` | 0（不计入熔断）| catch → L2 + `source="template"` |
| B | **4 chip 全部 `none()`** | — | 0 | 直接 L2（避免浪费）|
| B | **配置 `lp.ai.llm.enabled=false`** | — | 0 | 直接 L2 |

### 8.3 L2 → L3 降级触发条件（**任一**触发即抛 1501）

| 触发场景 | 异常 | HTTP |
|---|---|---|
| `headline.empty` 模板键缺失 | `IllegalStateException`（启动期）| 启动失败（fail fast）|
| 模板占位符数量错位 | `IllegalArgumentException`（运行时）| 抛 `BusinessException(1501)` → 503 |
| `MessageFormat` 解析异常 | `IllegalArgumentException` | 同上 |
| Provider 全部抛错 + LLM 也失败 + 模板失败 | 多重失败 | 抛 `BusinessException(1501)` → 503 |

### 8.4 L3 输出（1501 `AI_DEGRADED`）

| 字段 | 值 |
|---|---|
| HTTP | 503 |
| code | 1501 |
| message | "AI 服务暂时不可用，请稍后重试" |
| 数据 | `data: null`（无 chips / headline）|
| 触发 | L1 LLM 失败 **且** L2 模板失败 |

### 8.5 与 v2.0 4 级 Provider 降级的关系

| v2.0 级别 | v2.0 含义 | v2.1 行为 |
|---|---|---|
| L0 正常 | 全部 Provider 成功 | 调用 LLM 升级 headline（v2.1 改动）|
| L1 部分缺失 | ≥1 Provider 抛错 | 仍调 LLM，LLM 内部已知道哪些 chip 缺失 |
| L2 全失败 | 全部 Provider 抛错 | **不调 LLM**（4 chip 全 none() 无意义）→ 走 L2 模板 |
| L3 服务降级 | Redis + DB 全挂 | 走 v2.0 `fallback.headline` = "数据异常，请稍后重试" |

**v2.1 的 3 层降级是 v2.0 L0/L1 之上的"headline 升级降级"**，与 v2.0 4 级 Provider 降级正交：

```
v2.1 完整降级路径：

L0 全部 Provider 成功 + LLM 成功
  → source=llm
L0 全部 Provider 成功 + LLM 失败
  → 走 L2 模板（v2.0 L1/L0 的"模板拼接"）
  → source=template
L1 部分 Provider 失败 + LLM 成功
  → LLM 内部知道缺失，仍生成（advice/highlight 会暗示部分数据）
  → source=llm
L1 部分 Provider 失败 + LLM 失败
  → 走 L2 模板（部分 chip 缺失的模板）
  → source=template
L2 全部 Provider 失败
  → 不调 LLM（浪费）
  → 直接 headline.empty
  → source=template
L3 Redis+DB 全挂
  → 模板也失败（DB 不可用时 LLM 也不知道答案）
  → 抛 1501 → 503
```

### 8.6 关键反模式（明确不做）

| 反模式 | 原因 |
|---|---|
| LLM 失败时**重试** | 5s 超时已包含网络抖动；429 重试只会更糟 |
| LLM 失败时**异步重试** | 个人项目无队列基础设施；复杂度不值 |
| LLM 失败时**切到备用 provider** | 双 provider 切换 = 配置开关的事，不是降级逻辑 |
| 降级时给前端**额外提示** | 用户无感最重要（PRD §4 US-1 决策）|
| 降级时**关闭 refresh 按钮** | refresh 本就是强制重算，不需要禁用 |
| 1501 时**返回部分数据** | 1501 语义是"完全不可用"，部分数据会误导前端 |

---

## 9. 模板与 Prompt 规范

### 9.1 LLM Prompt 模板（`backend/src/main/resources/llm-prompt.properties`）

```properties
# System prompt（角色定位）
system.role=你是一个个人数字生活助手。基于用户今日的 4 个维度数据，生成简洁的中文洞察和建议。要求：1) 只基于提供的数据，不要编造；2) 中文，1-2 句；3) 不超过 120 字；4) 包含 1 个判断（紧凑/略松/正常/异常）和 1 个数字；5) 语气像朋友建议。

# User prompt 模板（4 chip 数据）
user.chip.taskCompletion=【任务】完成率 {0}%，{1}
user.chip.weeklyExpense=【消费】本周 ¥{0}，{1}
user.chip.planDensity=【日程】今日 {0} 项
user.chip.dietIntake=【饮食】摄入 {0}/{1} kcal

# User prompt 引导
user.template=请基于以下数据生成洞察（JSON 格式，4 字段：headline, advice, highlight, mood）：

{0}

要求：headline 40-120 字；advice 30-80 字；highlight 20-60 字；mood ∈ positive/neutral/cautious。

# Chip 副标（v2.0 沿用，仅任务/消费/计划/饮食）
chip.taskCompletion.up=较昨日 +{0}pp
chip.taskCompletion.down=较昨日 {0}pp
chip.taskCompletion.flat=与昨日持平
chip.weeklyExpense.up=较上周 +{0}%
chip.weeklyExpense.down=较上周 -{0}%
chip.weeklyExpense.flat=与上周持平
chip.planDensity.busy=今日 {0} 项（较忙）
chip.planDensity.normal=今日 {0} 项
chip.planDensity.free=今日 {0} 项（有空闲）
chip.dietIntake.up=较昨日 +{0}%
chip.dietIntake.down=较昨日 -{0}%
chip.dietIntake.flat=与昨日持平

# Headline 模板（v2.0 沿用，L2 降级用）
headline.full=今日任务完成率 {0}%，{1}；本周消费 ¥{2}，{3}。
headline.taskOnly=今日任务完成率 {0}%，{1}。继续记录几天后将出现更全面的洞察。
headline.expenseOnly=本周消费 ¥{0}，{1}。
headline.dietOnly=今日饮食摄入 {0}/{1} kcal，{2}。
headline.empty=还没有数据，继续记录几天后将出现洞察。
fallback.headline=数据异常，请稍后重试
```

### 9.2 LLM 期望输出 JSON Schema

```json
{
  "type": "object",
  "required": ["headline", "advice", "highlight", "mood"],
  "properties": {
    "headline":  { "type": "string", "minLength": 20, "maxLength": 200 },
    "advice":    { "type": "string", "minLength": 10, "maxLength": 200 },
    "highlight": { "type": "string", "minLength": 10, "maxLength": 200 },
    "mood":      { "type": "string", "enum": ["positive", "neutral", "cautious"] }
  }
}
```

### 9.3 模板缺失 / 错位降级

- **模板键缺失** → 启动期 `IllegalStateException` → Spring 启动失败（fail fast）
- **占位符数量不匹配** → `log.error` + 走 `fallback.headline` 降级到 L2 → L3 1501

---

## 10. 安全架构 — API Key 环境变量管理

### 10.1 总原则（与 CLAUDE.md §7.1 / §7.2 / §7.4 红线对齐）

| 红线 | v2.1 落地 |
|---|---|
| 禁止硬编码任何密钥 | `LP_LLM_API_KEY` 走 `${LP_LLM_API_KEY}` 占位符 |
| 仓库只能有 `.env.example` | `.env` 必须 `.gitignore` 排除；CI 用 secret manager 注入 |
| hex/base64 密码串嫌疑必拒 | pre-commit + gitleaks 扫描 |
| 本地开发用 placeholder | `.env.example` 留 `LP_LLM_API_KEY=sk-replace-with-your-deepseek-key` 注释 |

### 10.2 配置注入链

```
.env.example                (仓库留位, 真实值不入)
       │
       ▼ (开发者本地 cp .env.example .env，填真实值)
.env                         (本地开发，.gitignore 排除)
       │
       ▼ (docker-compose 读 .env)
docker-compose.yml           (env_file: .env)
       │
       ▼ (容器内进程读取环境变量)
LP_LLM_API_KEY=sk-xxx...    (进程环境变量)
       │
       ▼ (Spring Environment 解析 ${LP_LLM_API_KEY})
application.yml
  lp:
    ai:
      llm:
        api-key: ${LP_LLM_API_KEY}    # 占位符引用
       │
       ▼ (@ConfigurationProperties 绑定)
LlmProperties.llm.apiKey  (LlmClient 注入)
       │
       ▼ (HTTP Authorization: Bearer <apiKey>)
LlmClient.generate() → DeepSeek API
```

### 10.3 配置项清单（`lp.ai.llm.*`）

| 配置项 | 占位符 | 默认值 | 必填 | 校验 |
|---|---|---|---|---|
| `lp.ai.llm.enabled` | `${LP_LLM_ENABLED:true}` | `true` | 否 | 启动期校验 boolean |
| `lp.ai.llm.provider` | `${LP_LLM_PROVIDER:deepseek}` | `deepseek` | 否 | 启动期校验 enum |
| `lp.ai.llm.base-url` | `${LP_LLM_BASE_URL:https://api.deepseek.com/v1}` | DeepSeek 默认 | 否 | URL 格式校验 |
| `lp.ai.llm.api-key` | `${LP_LLM_API_KEY:}` | **空** | **是**（除 ollama 模式）| 启动期 fail fast |
| `lp.ai.llm.model` | `${LP_LLM_MODEL:deepseek-chat}` | `deepseek-chat` | 否 | 非空字符串 |
| `lp.ai.llm.timeout-ms` | `${LP_LLM_TIMEOUT_MS:5000}` | `5000` | 否 | 1000-30000 |
| `lp.ai.llm.max-prompt-tokens` | `${LP_LLM_MAX_PROMPT_TOKENS:1500}` | `1500` | 否 | 100-4000 |
| `lp.ai.llm.max-response-tokens` | `${LP_LLM_MAX_RESPONSE_TOKENS:300}` | `300` | 否 | 50-1000 |
| `lp.ai.llm.daily-quota` | `${LP_LLM_DAILY_QUOTA:50}` | `50` | 否 | 1-1000 |
| `lp.ai.llm.circuit-breaker.enabled` | `${LP_LLM_CB_ENABLED:true}` | `true` | 否 | boolean |
| `lp.ai.llm.circuit-breaker.failure-threshold` | `${LP_LLM_CB_THRESHOLD:10}` | `10` | 否 | 1-100 |
| `lp.ai.llm.circuit-breaker.window-minutes` | `${LP_LLM_CB_WINDOW:5}` | `5` | 否 | 1-60 |
| `lp.ai.llm.circuit-breaker.cooldown-minutes` | `${LP_LLM_CB_COOLDOWN:30}` | `30` | 否 | 1-1440 |

### 10.4 启动期 fail fast

```java
@Component
@ConfigurationProperties("lp.ai.llm")
@Validated
public record LlmProperties(
    boolean enabled,
    @NotBlank String provider,        // deepseek | ollama
    @URL String baseUrl,
    String apiKey,                     // deepseek 必填，ollama 可空
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

### 10.5 仓库保护

#### `.env.example`（**唯一可提交**）

```bash
# === AI LLM 配置（v2.1 新增）===
LP_LLM_ENABLED=true
LP_LLM_PROVIDER=deepseek              # deepseek | ollama
LP_LLM_BASE_URL=https://api.deepseek.com/v1
LP_LLM_API_KEY=sk-replace-with-your-deepseek-key   # ← 替换为真实密钥
LP_LLM_MODEL=deepseek-chat
LP_LLM_TIMEOUT_MS=5000
LP_LLM_MAX_PROMPT_TOKENS=1500
LP_LLM_MAX_RESPONSE_TOKENS=300
LP_LLM_DAILY_QUOTA=50
LP_LLM_CB_ENABLED=true
LP_LLM_CB_THRESHOLD=10
LP_LLM_CB_WINDOW=5
LP_LLM_CB_COOLDOWN=30
```

#### `.gitignore`（强制）

```gitignore
# v2.1 新增（与现有一致）
.env
.env.local
.env.*.local
```

#### `.gitleaks.toml`（CLAUDE.md §7.1 强制）

```toml
[[rules]]
id = "deepseek-api-key"
description = "DeepSeek API Key"
regex = '''sk-[a-zA-Z0-9]{20,}'''
tags = ["secret", "api-key"]

[[rules]]
id = "openai-api-key"
description = "OpenAI API Key (fallback if user later switches)"
regex = '''sk-(proj-)?[a-zA-Z0-9]{20,}'''
tags = ["secret", "api-key"]

[[rules]]
id = "lp-llm-api-key-env"
description = "LP_LLM_API_KEY non-placeholder value"
regex = '''LP_LLM_API_KEY=(?!sk-replace-|.*\$\{)[a-zA-Z0-9-]{20,}'''
tags = ["secret", "env"]
```

### 10.6 运行时密钥处理（防意外打印）

| 位置 | 行为 |
|---|---|
| **日志** | 禁打 apiKey 全文；只打 `provider` + `model` |
| **错误信息** | `BusinessException` message 不含密钥 |
| **HTTP 头** | `Authorization: Bearer <apiKey>` 走 `RestClient` interceptor 自动加，不进日志 |
| **异常堆栈** | 5xx 响应**不**含 apiKey |
| **Actuator** | `/actuator/env` 默认**禁用**密钥值 |
| **Micrometer** | 标签中**不含** apiKey |
| **Redis 缓存** | 缓存值**不含** apiKey |
| **前端** | 永远**不**接收 apiKey |
| **E2E 测试** | 用 `sk-test-valid-format-12345`（满足长度校验）|

### 10.7 鉴权与越权

| 项 | 行为 |
|---|---|
| 鉴权 | access token 必填（v2.0 沿用 `JwtAuthFilter`）|
| 越权 | 跨用户访问 1003（缓存键 `lp:ai:insight:<userId>` 天然按用户分；LLM 输出只与 userId 关联的 metrics 拼装）|
| Controller | `@PreAuthorize("isAuthenticated()")`（v2.0 沿用）|

```java
@RestController
@RequestMapping("/api/v1/ai/insight")
public class AiInsightController {
    @GetMapping("/today")
    public MyResponse<AiInsightResponse> getToday(@AuthenticationPrincipal Long userId) {
        return MyResponse.ok(aiInsightService.getInsight(userId));
    }

    @GetMapping("/analysis")  // v2.1 新增
    public MyResponse<AiInsightResponse> getAnalysis(@AuthenticationPrincipal Long userId) {
        return MyResponse.ok(aiInsightService.getInsight(userId));  // 共享缓存
    }

    @PostMapping("/refresh")
    public MyResponse<AiInsightResponse> refresh(@AuthenticationPrincipal Long userId) {
        return MyResponse.ok(aiInsightService.refresh(userId));
    }
}
```

### 10.8 限流（双层）

| 限流 | 键 | 阈值 | 触发 |
|---|---|---|---|
| GET `/today` / `/analysis` | `lp:rl:ai:insight:<userId>` | **30/min/user**（v2.0 实际值）| 1006 / 429 |
| POST `/refresh` | `lp:rl:ai:refresh:<userId>` | **3/min/user**（v2.1 砍半）| 1006 / 429 |
| LLM 每日配额 | `lp:ai:quota:<userId>:<yyyymmdd>` | **50/day/user** | 1510（Service catch → L2）|
| 熔断 | `lp:ai:circuit:*` | 5min/10fail/30min | 1511（Service catch → L2）|

### 10.9 审计与可观测

| 事件 | 级别 | 字段 |
|---|---|---|
| LLM 降级（L1→L2）| WARN | `userId`, `provider`, `exception_type`, `latencyMs` |
| LLM 连续 5 次降级 | ERROR | 上面 + `circuit_state` |
| 配额超限 | WARN | `userId`, `quota_used`, `quota_limit` |
| 熔断触发 | ERROR | `failures_in_window`, `window_minutes` |
| 熔断恢复 | INFO | `state=CLOSED` |
| LLM 4xx（账号/配置错误）| ERROR | `userId`, `provider`, `http_status` |
| LLM 5xx | WARN | 同上 |
| LLM 429 | WARN | 同上（不重试）|
| LLM 超时 | WARN | `userId`, `timeoutMs` |
| 启动期密钥缺失 | ERROR | `provider`, `key_field`（**不打密钥值**）|
| **禁打** | — | `headline` 全文、`prompt` 全文、`response` 全文、`apiKey` 任何部分、`userEmail` |

#### Micrometer metrics（`/actuator/metrics`）

```
lp.ai.insight.requests.total{source=cache|llm|template|error}     // 计数
lp.ai.insight.llm.latency{provider=deepseek|ollama, result=ok|fail}  // 分布
lp.ai.insight.quota.used{userId}                                  // gauge
lp.ai.insight.circuit.state{state=CLOSED|OPEN|HALF_OPEN}          // gauge
lp.ai.insight.degrade.l1_to_l2.total{reason=...}                  // 计数
lp.ai.insight.degrade.l2_to_l3.total                              // 计数
```

#### 启动 banner（沿用 v2.0 风格）

```
================================================
LifePulse Backend (v2.1.0-ai)
Git: abc1234 | Build: 2026-07-22T17:00:00Z
AI v2.1 LLM: enabled=true, provider=deepseek, model=deepseek-chat, timeout=5000ms
AI v2.1 Quota: 50/day/user
AI v2.1 CircuitBreaker: enabled=true, threshold=10/5min, cooldown=30min
================================================
```

**注意**：banner 中**不打印** `apiKey` 任何字符（连前缀都不打）。

### 10.10 密钥泄露应急

| 场景 | 检测 | 响应 |
|---|---|---|
| 密钥误提交 git | gitleaks pre-commit / CI 扫描 | commit 拒绝 + 立即重置密钥 |
| 密钥出现在日志 | 人工 / 定期 grep | 立即重置密钥 + 加固日志过滤 |
| 密钥出现在响应 | 集成测试断言 + Actuator 巡检 | 立即重置密钥 + 加固 DTO 白名单 |
| 密钥出现在前端 | 前端单测断言 + E2E | 立即重置密钥 + 加固 DTO |
| 第三方密钥泄露监控 | DeepSeek 控制台 / 阿里云密钥管理 | 启用密钥轮换流程 |

**密钥轮换**：DeepSeek 控制台 → 撤销旧 key → 创建新 key → 改 `.env` → 重启（5 分钟闭环）。

---

## 11. 前端 UX

### 11.1 智能卡（升级）

```
┌─────────────────────────────────────┐
│ [图标]  AI分析         [AI生成] 🔄  ⓘ│  ← 新增 source 标签
│                                      │
│ 今日任务完成率 80%，节奏紧凑但收尾   │  ← LLM 生成
│ 在望。下午 4-6 点日程最密，建议      │  ← LLM 替换的"具体可执行建议"
│ 14:30 提前结束午休准备。             │
│                                      │
│ ┌──────┐ ┌──────┐ ┌──────┐          │
│ │任务80%│ │¥420  │ │日程4 │          │
│ │+5pp↑│ │-12%↓│ │持平  │          │
│ └──────┘ └──────┘ └──────┘          │
│                                      │
│              查看明细 →              │
└─────────────────────────────────────┘
```

- 整卡可点击（→ 抽屉）
- 🔄 按钮：触发 refresh
- ⓘ 按钮：触发抽屉（与整卡点击等价）
- **source 标签**："AI 生成"（绿）/"模板"（灰）
- 加载中状态：headline 区显示 "分析中..."（≥ 3s）

### 11.2 详情抽屉（升级）

- 标题："AI 分析 · {generatedAt 格式化}"
- 顶部：完整 headline（可能比卡片更长）
- 中部：3 个 chip 大尺寸展示
- **底部："查看完整分析 →"**（跳转 `/ai-analysis`，v2.0 留的"v2.1 将推出..."已替换）
- 右上角 source 标签
- 关闭：右上角 X、ESC 键、点击遮罩

### 11.3 独立分析页 `/ai-analysis`（新增）

```
┌─────────────────────────────────────────────────┐
│ ← 返回   AI 分析 · 2026-07-22 17:30   🔄  [AI生成] │
├─────────────────────────────────────────────────┤
│                                                  │
│  ┌─────────────────────────────────────────┐    │
│  │  今日任务完成率 80%，节奏紧凑但收尾      │    │
│  │  在望。                                 │    │
│  │  (headline 大字 18-24px)                │    │
│  └─────────────────────────────────────────┘    │
│                                                  │
│  💡 今日建议                                     │
│  ┌─────────────────────────────────────────┐    │
│  │  下午 4-6 点日程最密，建议               │    │
│  │  14:30 提前结束午休准备。                 │    │
│  │  (advice 卡片，60-80 字)                 │    │
│  └─────────────────────────────────────────┘    │
│                                                  │
│  ✨ 亮点 / 关注点                                │
│  • 消费下降 12% 表现优秀                        │
│  • 任务节奏稳定                                 │
│  • 日程密度正常                                 │
│  (highlight 列表)                              │
│                                                  │
│  📊 关键指标                                     │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐   │
│  │任务 80%│ │消费 420│ │日程 4项│ │饮食 92%│   │
│  │+5pp ↑  │ │-12% ↓  │ │持平    │ │+2% ↑   │   │
│  └────────┘ └────────┘ └────────┘ └────────┘   │
│                                                  │
│  v2.2 将加入趋势图表和时间维度对比              │
└─────────────────────────────────────────────────┘
```

| 元素 | 数据来源 | 渲染 |
|---|---|---|
| 顶部栏 | 元信息 | 返回按钮 + 时间 + 刷新 + source 标签 |
| Headline 区 | LLM `headline` | 大字 18-24px |
| Advice 区 | LLM `advice` | 卡片 + 💡 icon |
| Highlight 区 | LLM `highlight` 拆分 | bullet 列表 |
| Chip 完整区 | v2.0 chip 数据 | 4 个大卡 |
| 底部 | 文案 | "v2.2 将加入..." 提示 |

### 11.4 空状态

`headline="还没有数据，继续记录几天后将出现洞察。"` + 4 chip 全 `value="—" trend="none"`，无 advice/highlight 段落（v2.1 不展示 advice/highlight 区域）。

### 11.5 降级状态（用户无感）

- `source="template"` → 标签显示"模板"（灰色）但 headline 内容是模板拼接的
- `source="llm"` → 标签显示"AI 生成"（绿色）

---

## 12. 测试策略

### 12.1 测试金字塔

| 层 | 工具 | 目标 | 计划用例数 |
|---|---|---|---|
| 单元 | JUnit 5 + Mockito | LLM 客户端/解析/熔断/配额/Generator 行覆盖 ≥ 80% | 35 |
| Slice | `@WebMvcTest` | 鉴权/错误码 100% | 4 |
| 集成 | Testcontainers | 关键闭环 100% | 5 |
| 前端 | Vitest | Store/关键组件 100% | 15 |
| E2E | Playwright | 关键用户路径 100% | 5 |

### 12.2 关键测试用例

**`LlmInsightGeneratorTest`**（核心）：
- `generate_quotaExceeded_throws1510`
- `generate_circuitOpen_throws1511`
- `generate_clientSuccess_parserSuccess_returnsPayload`
- `generate_clientTimeout_throws1513`
- `generate_client5xx_throws1513`
- `generate_client429_throws1513`
- `generate_parserInvalidJson_throws1512`
- `generate_parserMissingHeadline_throws1512`
- `generate_parserHeadlineTooShort_throws1512`
- `generate_parserInvalidMood_clampsToNeutral`
- `generate_circuitBreakerDisabled_skipsCheck`（Ollama 模式）

**`DeepSeekClientTest`**：
- `generate_validRequest_returnsResponse`
- `generate_http500_throwsLlmUnavailable`
- `generate_http429_throwsLlmUnavailable`
- `generate_http400_throwsLlmResponseInvalid`
- `generate_timeout_throwsLlmUnavailable`
- `generate_emptyBody_throwsLlmResponseInvalid`

**`OllamaClientTest`**：
- `generate_validRequest_returnsResponse`
- `generate_connectionRefused_throwsLlmUnavailable`
- `generate_invalidJsonResponse_throwsLlmResponseInvalid`
- `generate_timeout_throwsLlmUnavailable`

**`LlmJsonParserTest`**：
- `parse_validJson_returnsPayload`
- `parse_missingHeadline_throwsInvalid`
- `parse_invalidMood_clampsToNeutral`
- `parse_extraFields_ignored`
- `parse_emptyJson_throwsInvalid`

**`LlmCircuitBreakerTest`**：
- `tryAcquire_stateClosed_returnsNormally`
- `tryAcquire_stateOpen_recentlyOpened_throwsCircuitOpen`
- `tryAcquire_stateOpen_cooldownExpired_transitionsToHalfOpen`
- `recordFailure_10FailuresIn5Min_opensCircuit`
- `tryAcquire_redisUnavailable_failClosed`
- `tryAcquire_ollamaMode_skipsCheck`

**`LlmQuotaGuardTest`**：
- `checkAndIncrement_firstCall_incrementsAndExpires`
- `checkAndIncrement_overLimit_throwsQuotaExceeded`
- `checkAndIncrement_redisUnavailable_failOpen`

**`AiInsightServiceTest`**（v2.0 基础上扩展）：
- `getInsight_cacheHit_returnsCachedWithSource`
- `getInsight_llmSuccess_returnsWithSourceLlm`
- `getInsight_llmFails_usesTemplateWithSourceTemplate`
- `getInsight_templateFails_throws1501`
- `getInsight_allProvidersFail_skipsLlm_usesEmptyTemplate`
- `getInsight_llmDisabled_skipsLlm_usesTemplate`

**`AiInsightControllerWebTest`**（v2.0 基础上扩展）：
- `getToday_noAuth_returns401`
- `getToday_validToken_returns200WithSource`
- `getAnalysis_validToken_returns200WithFullPayload`
- `getToday_serviceThrows1501_returns503`

**`AiAnalysisIT`**（Testcontainers）：
- `endToEnd_realData_llmEnabled_sourceIsLlm`
- `endToEnd_redisDown_fallsBackToRecompute`
- `endToEnd_quotaExceeded_usesTemplateSource`
- `endToEnd_circuitOpen_usesTemplateSource`
- `endToEnd_crossUserIsolation_userACannotSeeUserBInsight`
- `endToEnd_ollamaMode_usesLocalLlm`

**E2E `ai-v2-1-flow.spec.ts`**：
- 登录后看到 AI 卡与 headline（带 source="AI 生成" 标签）
- 抽屉底部显示"查看完整分析 →"链接
- 点击跳转 `/ai-analysis` 看到 4 段内容
- 独立分析页 refresh 按钮触发 source 变化
- 配置切换到 Ollama 后端到端仍可用

### 12.3 覆盖率门禁

| 层 | 阈值 | 失败后果 |
|---|---|---|
| Service | 行 ≥ 85% | `mvn verify` 失败 |
| Controller 鉴权/错误码 | 路径 100% | 同上 |
| `LlmInsightGenerator` | 行 ≥ 90% | 同上 |
| `LlmClient` 各实现 | 行 ≥ 80% | 同上 |
| `LlmCircuitBreaker` | 行 ≥ 85% | 同上 |
| `LlmQuotaGuard` | 行 ≥ 85% | 同上 |
| 前端 store 关键 action | 100% | `pnpm test` 失败 |
| E2E 5 个用例 | 全绿 | `pnpm exec playwright test` 失败 |

---

## 13. 任务清单（v2.1 共 19 条，4 PR 串行）

### PR1：LLM 客户端脚手架

- **T1.1** `LlmClient` 接口 + `LlmRequest` / `LlmResponse` 不可变 record
- **T1.2** `LlmProperties`（`@ConfigurationProperties + @Validated`）+ 启动期 fail fast
- **T1.3** `DeepSeekClient`（OpenAI 兼容）+ 5s 超时 + `RestClient` interceptor
- **T1.4** `OllamaClient`（本地 HTTP）+ `format: "json"` 模式
- **T1.5** 两个客户端单测（各 ≥ 6 用例，`MockRestServiceServer`）
- **T1.6** `.env.example` + `application.yml` 模板 + `docker-compose.yml` ollama 可选服务
- **T1.7** `.gitleaks.toml` 新增 3 条规则

### PR2：LLM 业务编排

- **T2.1** `LlmPromptBuilder` 渲染 system + user prompt
- **T2.2** `LlmJsonParser` 解析 JSON + 字段缺失/超长/非法 mood 处理
- **T2.3** `LlmQuotaGuard`（Redis INCR+EXPIRE，fail-open）
- **T2.4** `LlmCircuitBreaker`（Redis ZSET 滑动窗口，Ollama 模式自动禁用，fail-closed）
- **T2.5** `LlmInsightGenerator` 编排 + 4 个异常类
- **T2.6** 改造 `AiInsightService` 加 L1→L2→L3 降级链 + `source` 字段
- **T2.7** `AiInsightResponse` 加 5 个新字段（可空）+ `LlmMeta`
- **T2.8** TTL 改 6h + POST refresh 限流改 3/min
- **T2.9** 4 个新错误码（1510/1511/1512/1513）+ `ErrorCode` 枚举
- **T2.10** `llm-prompt.properties` + `ai-templates.properties` 扩展
- **T2.11** 单测：Generator ≥ 11 用例，QuotaGuard ≥ 3，Breaker ≥ 6，JsonParser ≥ 5
- **T2.12** IT：Testcontainers ≥ 6 用例（含 Ollama 模式）

### PR3：独立分析页 + 前端

- **T3.1** `AiAnalysisController`（`GET /api/v1/ai/insight/analysis`，共享缓存）
- **T3.2** `AiAnalysisView.vue` 4 段内容 + 顶部栏 + source 标签
- **T3.3** `stores/aiAnalysis.ts` state + load + refresh
- **T3.4** `api/ai.ts` 加 `getAnalysis()` + `types/ai.ts` 加 `AiAnalysis` 类型
- **T3.5** 路由 `/ai-analysis` + 鉴权守卫
- **T3.6** 智能卡 source 标签 + 加载中状态
- **T3.7** 抽屉底部"查看完整分析 →"跳转
- **T3.8** 前端单测：≥ 15 用例（store + 关键组件 + 独立页）

### PR4：E2E + 文档 + 发布

- **T4.1** Playwright `ai-v2-1-flow.spec.ts` 5 个用例
- **T4.2** gitleaks + pre-commit 钩子接入
- **T4.3** 同步修订 v2.0 spec（移除"不引入 LLM"硬约束，加 v2.1 修订说明）
- **T4.4** `RELEASES/v2.1.0-ai.md`
- **T4.5** `README.md` §1 / §8 标记 v2.1
- **T4.6** `00-index.md` §3 交付节奏表更新（已提前做）
- **T4.7** `mvn verify` + `pnpm test` + `pnpm exec playwright test` 全绿
- **T4.8** 合并后打 tag `v2.1.0-ai`

---

## 14. 风险

| 类别 | 风险 | 影响 | 缓解 |
|---|---|---|---|
| 技术 | DeepSeek API 抖动 / 限流 429 | 中 | 3 层降级 L2 自动切模板；限流计数 5min/10fail/30min 熔断 |
| 技术 | Ollama 本地模式首字延迟 5-10s，用户体验差 | 中 | GET 命中走缓存不调 LLM；独立分析页 loading 状态 ≥ 3s 显示 |
| 技术 | LLM 输出 JSON 格式错乱（DeepSeek 偶尔出现"json\n{...}"前缀）| 中 | prompt 显式要求"只输出 JSON 对象" + 解析器容忍前后空白 |
| 技术 | LLM 输出敏感词（虽概率低）| 低 | 关键词黑名单扫描，命中走 L2 降级 + log.error |
| 技术 | Spring `RestClient` 5s 超时不够（DeepSeek 高峰）| 中 | 留 `lp.ai.llm.timeout-ms` 配置项，可调到 10s |
| 技术 | `RestClient` 与现有 `RestTemplate` / `WebClient` 冲突 | 低 | v2.0 已用 `RestTemplate` 做邮件发送；新建 `LlmRestClient` Bean 不冲突 |
| 资源 | 单开发者节奏，4 PR 串行 1-2 周 | 中 | 每个 PR 独立可合并 / 可回滚；切分支 worktree 隔离 |
| 资源 | 配额 50/天被 1 用户独享用尽 | 低 | 单用户项目无并发；超限后切模板仍可用 |
| 资源 | 独立分析页 C1 用户是否真用未验证 | 中 | 列入 v2.2 验收观察项；埋点 7 天内跳转率 < 5% 则下线 |
| 成本 | DeepSeek 计费规则变更（涨价）| 低 | 切 Ollama 模式兜底；配置文件改 base URL 可换通义千问 |
| 成本 | `LP_LLM_API_KEY` 误提交到 git | **高** | CLAUDE.md §7.1 红线 + gitleaks 钩子 + 启动期占位符校验 |
| 合规 | v2.0 spec §1 "不引入 LLM" 硬约束被打破 | 低 | 同步在 v2.0 spec 加 "v2.1 修订说明" 小节，引用 v2.1 spec |
| 兼容 | v2.0 PR #16 已合并的字段（Trend enum、URL 前缀）与 v2.1 兼容 | 低 | v2.1 完全沿用 v2.0 字段，新增字段用 `@JsonInclude(NON_NULL)` 兼容 |
| 兼容 | v2.0 缓存键 `ai:insight:{userId}` 命中 v2.1 反序列化失败（新字段缺失）| 中 | DTO 用 `@JsonInclude(NON_NULL)` 兼容；新字段给默认值 |
| 安全 | 提示词注入（用户通过修改本地配置注入恶意 prompt）| 低 | LLM 输出只用作展示文本，不拼回 prompt 二次输入；不调用函数 |
| 安全 | LLM 返回内容含 XSS（理论上不可能，但兜底）| 低 | Vue 默认转义；禁 `v-html`；CSP 沿用 v2.0 配置 |
| 运维 | Ollama 容器镜像 5GB+，CI 跑测试慢 | 中 | Testcontainers 集成测试用 WireMock mock LLM，不拉真 Ollama |
| 运维 | 4 chip 输入数据有缺失（用户新注册无数据）| 低 | LLM 走"empty"模板；4 chip 全 `value="—"`；走降级同 v2.0 |
| 决策 | v2.0 spec §18.2 修订表与实际代码不一致（30 vs 60/min）| 低 | v2.1 spec 以实际代码为准（30/min）|
| 决策 | 1 个 GitHub issue 跟踪 `v2.0 spec §18.2 偏差表` 与 `v2.1 spec` 不一致 | 低 | v2.1 实施时同步修订 v2.0 spec §18.2 |

---

## 15. 开放问题

| # | 问题 | 状态 | 决定时机 |
|---|---|---|---|
| Q1 | DeepSeek API 真的比通义千问便宜 / 质量更好吗？ | 待验证 | T1.3 启动时跑 10 次真实调用做对比 |
| Q2 | 独立分析页用户是否真用？ | 待观察 | v2.2 验收期（7 天内跳转率 < 5% 则下线）|
| Q3 | 50/天配额是否够？ | 待观察 | v2.2 调优期（统计 1 个月实际使用量）|
| Q4 | Ollama 模式 `deepseek-r1:8b` 是否需要换成 `qwen2.5:7b`（中文更好）？| 待验证 | T1.4 启动时跑中文测试集 |
| Q5 | 熔断阈值 5min/10fail 是否合理？ | 待观察 | v2.2 调优期（观察 1 个月实际抖动）|
| Q6 | `mood` 字段前端是否展示？ | **已决定：否** | §11 UX 草图未列；只在 `LlmInsightPayload` 内部流转，便于后续扩展 |
| Q7 | `llmMeta` 字段前端是否可见？ | **已决定：否** | §11 UX 草图未列；仅 ops 通过 `/actuator/metrics` + 日志看，节省带宽 |

---

## 16. 参考文献

- 项目级规范：`CLAUDE.md` §1-9
- v2.0 设计索引：`docs/superpowers/specs/2026-07-21-ai-v2-design.md`（基线）
- v2.0 发布记录：`RELEASES/v2.0.0-ai.md`（v2.1 修订基线）
- v2.0 PRD 索引：`docs/prd/00-index.md`（用户画像 / KANO / RICE 通用框架）
- v2.1 PRD：`docs/prd/07-ai-llm-prd.md`
- v2.0 业务架构：`docs/architecture/ai-business-architecture.md`
- v2.0 技术架构：`docs/architecture/ai-tech-architecture.md`（修正源）
- v2.0 数据模型：`docs/architecture/ai-data-model.md`
- API 规范：`docs/specs/03-api-auth.md`（沿用鉴权 + MyResponse 信封）
- 数据库规范：`docs/specs/02-database.md`（仅读既有表，不新增）
- MVP1 设计索引：`docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md`

---

## 17. 后续步骤

1. 用户审 spec（本文件）
2. spec 通过后调用 `superpowers:writing-plans` 拆分实施任务
3. 不直接进入实现；先有 plan，再按 TDD 落地

---

*Status: DRAFT - 待 spec 自检通过 + 用户审 spec*
