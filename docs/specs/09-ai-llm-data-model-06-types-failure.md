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
