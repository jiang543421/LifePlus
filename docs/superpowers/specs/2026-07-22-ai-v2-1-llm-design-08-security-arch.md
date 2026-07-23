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
