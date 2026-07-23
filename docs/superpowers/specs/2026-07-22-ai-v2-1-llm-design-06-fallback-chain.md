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
