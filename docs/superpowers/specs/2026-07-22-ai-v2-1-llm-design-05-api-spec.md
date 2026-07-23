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
