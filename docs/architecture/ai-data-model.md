# 数据模型设计 · AI 分析 v2.0

> 版本：v0.1 · 日期：2026-07-22 · 模块：`ai`（v2.0）
> 输入：[ai-v2-design.md §6](../superpowers/specs/2026-07-21-ai-v2-design.md) · [02-database](../specs/02-database.md)
> 索引：[ai-business-architecture](./ai-business-architecture.md) · [ai-tech-architecture](./ai-tech-architecture.md)

---

## 1. 核心声明：零新增表

AI 模块**纯只读聚合**，不落库、无 Flyway 迁移。读取的 5 张已存在表：

| 表 | 用途 | 来源模块 |
|---|---|---|
| `t_task` | 当日完成率 + 本周趋势 | task v1.0 |
| `t_plan` | 当日活动分钟 + 峰值小时 | plan v1.0 |
| `t_expense` | 当日 + 本周支出 | expense v1.2.1 |
| `t_diet` | 当日热量 + 蛋白 | diet v1.0 |
| `t_daily_report` | 周均值（可选） | daily v1.2.3 |

每张表的所有读取必须按 `user_id` 过滤（CLAUDE.md §7.2 + 各模块设计 §数据隔离）。

---

## 2. Redis 缓存结构

### 2.1 键清单

| 键 | 类型 | TTL | 写入方 | 读取方 |
|---|---|---|---|---|
| `ai:insight:<userId>` | String (JSON) | 1800 s | `AiInsightService` | `AiInsightService` |

- 无前缀清理：30 min 自动过期，无需主动失效
- 无批量键：无 `SCAN` / `MGET` 调用
- 无分布式锁：缓存击穿由单实例 in-flight 请求自然去重

### 2.2 值结构

```json
{
  "headline": "今日任务完成率 80%，较昨日 +5pp；本周消费 ¥420，较上周 -12%。",
  "chips": [
    { "key": "taskCompletion", "label": "任务完成", "value": "80%", "delta": "较昨日 +5pp", "trend": "UP" },
    { "key": "weeklyExpense",  "label": "本周支出", "value": "¥420", "delta": "较上周 -12%", "trend": "DOWN" },
    { "key": "todayExpense",   "label": "今日支出", "value": "¥35",  "delta": "—",          "trend": "NONE" }
  ],
  "generatedAt": "2026-07-22T08:30:00Z"
}
```

### 2.3 失败语义

| 操作 | 失败 |
|---|---|
| Redis GET | log.warn + 跳过缓存继续算（不抛） |
| Redis SETEX | log.warn + 仍返 200（响应已构造） |
| Redis 连接超时 | 同上，由 `Lettuce` 默认 2 s 超时控制 |

---

## 3. DTO 清单

| 类型 | 字段 | 序列化 |
|---|---|---|
| `AiInsightResponse` | `headline: String`, `chips: List<AiChipDto>`, `generatedAt: Instant` | `@JsonInclude(NON_NULL)` |
| `AiChipDto` | `key: String`, `label: String`, `value: String`, `delta: String`, `trend: Trend` | 同上 |
| `MetricValue` (domain) | `value: BigDecimal`, `trend: Trend`, `deltaRaw: Object` | 不外露 |
| `AiInsightPayload` (domain) | `headlineKey: String`, `headlineArgs: Object[]`, `chips: List<ChipPlan>`, `generatedAt: Instant` | 不外露 |
| `AiCollectContext` | `userId: long`, `today: LocalDate`, `weekStart: LocalDate` | 不外露 |

不可变性（CLAUDE.md §4.1）：
- 全部 `record`；`List<AiChipDto>` 返 `List.copyOf(...)`；`chips` 在 Service 构造后冻结
- `Map<String, AiInsightProvider>` 注入一次，运行时不可变

---

## 4. 模板数据模型（ai-templates.properties）

文件位置：`backend/src/main/resources/ai-templates.properties`
加载方式：`InputStreamReader(..., StandardCharsets.UTF_8)`（避免 ISO-8859-1 损坏中文）
渲染器：`java.text.MessageFormat`（支持 `{0}`/`{1}`）

### 4.1 键清单（15 条）

| 键前缀 | 数量 | 用途 |
|---|---|---|
| `headline.full` | 1 | 主文（任务 + 消费齐全） |
| `headline.taskOnly` | 1 | 主文（仅任务） |
| `headline.empty` | 1 | 主文（无数据兜底） |
| `fallback.headline` | 1 | 模板错位降级文案 |
| `chip.<key>.up` | 3 | chip 上升文案 |
| `chip.<key>.down` | 3 | chip 下降文案 |
| `chip.<key>.flat` | 3 | chip 持平文案 |
| `chip.<key>.none` | 1 | chip 无数据（共用） |

> `chip.none` 单一键覆盖所有 5 个 chip 的 NONE 态，渲染时返回 `—`。

### 4.2 占位符约定

| 模板 | 占位符 | 示例 |
|---|---|---|
| `headline.full` | `{0}=完成率`, `{1}=较昨日`, `{2}=本周消费`, `{3}=较上周` | "今日任务完成率 {0}%，{1}；本周消费 ¥{2}，{3}。" |
| `headline.taskOnly` | `{0}=完成率`, `{1}=较昨日` | "今日任务完成率 {0}%，{1}。继续记录几天后将出现更全面的洞察。" |
| `chip.<k>.up` | `{0}=差值` | "较昨日 +{0}pp" |
| `chip.<k>.down` | `{0}=差值` | "较上周 -{0}%" |
| `chip.<k>.flat` | 无 | "与昨日持平" |

### 4.3 降级矩阵

| 场景 | 引擎行为 |
|---|---|
| 键缺失 | 启动期抛 `IllegalStateException`（fail fast） |
| 占位符数量 > 入参 | log.error + 返 `fallback.headline` |
| `MessageFormat` 抛 IAE | log.error + 同上 |
| chip 模板错位 | log.error + 返 `—` |

---

## 5. 时区与精度

| 项 | 取值 |
|---|---|
| DB 时间 | `DATETIME(3)` UTC |
| 应用层 | `LocalDate` (Asia/Shanghai) |
| 序列化 | `Instant` UTC + Z 后缀 |
| 金额 | `BigDecimal`，序列化时 stripTrailingZeros |
| 百分比 | 整数 pp（小数四舍五入） |

---

## 6. 不存储的字段（明示）

- 用户偏好 / 历史快照 / 个性化画像
- LLM prompt / 模型权重
- PII 明文（邮箱 / 手机号）
- 任何写回业务表的 AI 衍生数据

如未来需个性化 → 新增 `t_user_insight_pref`（独立 spec，本设计不预留）。
