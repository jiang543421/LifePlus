# 08 — Daily Report（日报模块设计 v1.2.3）

> 本文件为 LifePulse v1.2 设计规格第 3 部分（日报模块）。对应 issue：`2026-07-18-mvp2-placeholder-modules.md`。
> 编码日报模块时单独加载本文，加载本文件时无需同时加载其他 sub-spec。
>
> **索引**：[01-architecture](./01-architecture.md) · [02-database](./02-database.md) · [03-api-auth](./03-api-auth.md) · [04-frontend](./04-frontend.md) · [05-nfr-testing](./05-nfr-testing.md) · [06-expense-design](./06-expense-design.md) · [07-diet-design](./07-diet-design.md)

---

## 1. 设计原则

- **范围 v1.2.3**：跨任务 / 日程 / 消费 / 饮食四个数据源**只读实时聚合**，不引入 `t_daily` 表；用户**零录入**成本
- **不做写动作**：没有 POST / PATCH / DELETE；任何 mutation 由各模块自有 controller 承担
- **数据隔离**：所有 Provider 都按 `userId` 过滤聚合（CLAUDE.md §7.2）
- **时区**：DB `DATETIME(3)` UTC 存储 / `Asia/Shanghai`（`DailyConstants.ZONE`）做日界；沿用 02-database §1
- **历史窗口**：`MAX_HISTORY_DAYS = 30`（`DailyConstants`）；超出窗口的日期参数返回 1001
- **周定义**：ISO 8601 周一为周首、跨年周至少 4 天（`DailyConstants.WEEK_START` + `ISO_WEEK_MIN_DAYS`）
- **饮食指标冻结**：v1.2.3 上线时 `DietMetricProvider` 永远返回 `DietMetrics(enabled=false, value=null, reason=...)`；前端按 `enabled=false` 渲染占位卡
- **Flyway**：V5 `V5__daily_indexes.sql`（no-op 审计，已落档）；不创建新表
- **首页入口**：发布时 `HomeView` 中 `daily` 卡 `placeholder: false`，跳 `/daily`
- **PRD**：产品需求见 [`docs/prd/05-daily-report.md`](../prd/05-daily-report.md)；实施计划见 [`docs/superpowers/plans/2026-07-21-daily-v1-2-3.md`](../superpowers/plans/2026-07-21-daily-v1-2-3.md)

## 2. 聚合数据源（不引入新表）

日报 / 周报聚合读取 4 张已有表，**不**写任何持久化数据：

| 数据源 | 表 | 聚合字段 | 过滤维度 |
|---|---|---|---|
| 任务 | `t_task` | `completedCount`（status=DONE 计数）/ `totalCount`（非软删计数） | `(user_id, due_date BETWEEN ? AND ?)` |
| 日程 | `t_plan` | `eventCount`（非软删计数） | `(user_id, start_time BETWEEN ? AND ?)` |
| 消费 | `t_expense` | `totalAmount`（`SUM(amount)`，BigDecimal 精度） | `(user_id, occurred_at BETWEEN ? AND ?)` |
| 饮食 | `t_diet` | **v1.2.3 冻结**：不读表，返回 `DietMetrics(enabled=false, ...)` | N/A |

> **设计决策**：由于 `t_task` 当前 schema 没有 `completed_at` 列（V2），按 `status = DONE AND due_date BETWEEN ...` 判定完成（V5 migration §1 备注）；V2 既有的 `idx_user_status_due (user_id, status, due_date)` 完美覆盖，无需新索引。

## 3. Provider 抽象

```java
public interface MetricProvider<T> {
    T aggregateDaily(long userId, LocalDate date);
}
```

每个数据源一个 `@Component`：

| 实现 | 类型参数 | 关键 mapper 调用 |
|---|---|---|
| `TaskMetricProvider` | `TaskMetrics` | `taskMapper.countByUserAndDueDateBetween(userId, from, to, status)` × 2 |
| `PlanMetricProvider` | `PlanMetrics` | `planMapper.countByUserAndStartTimeBetween(userId, from, to)` |
| `ExpenseMetricProvider` | `ExpenseMetrics` | `expenseMapper.sumAmountByUserAndOccurredAtBetween(userId, from, to)` |
| `DietMetricProvider` | `DietMetrics` | **冻结**：直接返回 `new DietMetrics(false, null, FROZEN_REASON)`，不注入 mapper |

### 3.1 输出 record 形状

```java
public record TaskMetrics(long completedCount, long totalCount) {}

public record PlanMetrics(long eventCount) {}

public record ExpenseMetrics(BigDecimal totalAmount) {}

public record DietMetrics(boolean enabled, DietMetricsValue value, String reason) {}
// value 为 record (BigDecimal kcal, BigDecimal proteinG, BigDecimal carbG, BigDecimal fatG)
// v1.2.3 永远为 null；解冻饮食指标时填充
```

## 4. Service 层

```java
@Service
public class DailyReportService {
    private final TaskMetricProvider taskProvider;
    private final PlanMetricProvider planProvider;
    private final ExpenseMetricProvider expenseProvider;
    private final DietMetricProvider dietProvider;

    public DailyReportPayload daily(long userId, LocalDate date);
    public WeeklyReportPayload week(long userId, LocalDate anyDayInWeek);
}
```

- **不做鉴权**（Controller 用 `UserContext.current()` 取 userId 后传入）
- **不做缓存**（v1.2.3 实时聚合；IT T8 验证 1w 行种子 P95 < 200ms / 周报 < 500ms）
- **校验**：`daily` / `week` 都校验 `date >= today - MAX_HISTORY_DAYS`；超出抛 `BusinessException(1001)`
- **未来日期允许**（不影响聚合性能，逻辑对称）

### 4.1 WeeklyTriplet.delta=null 语义

```java
public record WeeklyTriplet(double current, double previous, Double delta) {}
```

当 `previous == 0` 时 `delta = null`，避免除零与符号歧义（"下降 100%" vs "下降 0"）；前端按 `delta == null` 渲染"—"。

## 5. Flyway 迁移

```
V5__daily_indexes.sql   -- no-op 审计（已落档）
```

V5 是审计性迁移：**不**新增任何 DDL。其注释完整记录了 4 个数据源的索引覆盖审计（详见 V5 文件头部 "设计变更记录"）：

1. `t_task` V2 既有的 `idx_user_status_due` 已覆盖日报范围查询
2. `t_plan` V3 既有的 `idx_user_start` 已覆盖
3. `t_expense` V4 既有的 `idx_user_occurred` 已覆盖
4. 未来引入 `completed_at` 时再新建 V7+ 加 `idx_user_completed_at`

> 如生产 Flyway 历史已有"V5 加索引"期望的 deploy artifact，按 V5 文件"部署说明"段处理（递增版本号）。

## 6. API 端点

> 全局约定沿用 03-api-auth；返回信封 / 错误码同 06-expense-design §5.2。

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/v1/daily?date=YYYY-MM-DD` | 单日日报：4 类指标 + 饮食冻结态 |
| GET | `/api/v1/daily/week?date=YYYY-MM-DD` | 周报：含与上周对比（3 项 triplet：taskCompletion / planEvents / expenseAmount） |

### 6.1 DTO 字段

```java
record DailyReportPayload(LocalDate date,
                          TaskMetrics task,
                          PlanMetrics plan,
                          ExpenseMetrics expense,
                          DietMetrics diet) {}

record WeeklyReportPayload(String isoWeek,     // e.g. "2026-W29"
                           LocalDate weekStart,
                           LocalDate weekEnd,
                           WeeklyComparison comparison) {}

record WeeklyComparison(WeeklyTriplet taskCompletion,
                        WeeklyTriplet planEvents,
                        WeeklyTriplet expenseAmount) {}

record WeeklyTriplet(double current, double previous, Double delta) {}
```

### 6.2 错误码沿用

| 场景 | code |
|---|---|
| 日期参数缺失 / 解析失败 / 超出 30 天窗口 | 1001 |
| 未登录 / token 失效 | 1002 |
| 不存在（理论上不会触发，接口无 `{id}`） | 1004 |
| 后端聚合失败 | 500 |

## 7. 前端结构

### 7.1 新增路由

| 路径 | 视图 | 守卫 |
|---|---|---|
| `/daily` | DailyView | 需登录 |

### 7.2 新增 / 修改文件

```
frontend/src/
├─ api/
│  └─ daily.ts                            (新)
├─ stores/
│  └─ daily.ts                            (新)
├─ views/
│  └─ DailyView.vue                       (新)
├─ components/
│  ├─ daily/TaskMetricsCard.vue           (新 — 完成率进度条)
│  ├─ daily/PlanMetricsCard.vue           (新 — 事件数 + 列表预览)
│  ├─ daily/ExpenseMetricsCard.vue        (新 — 当日总额 + vs 昨日)
│  └─ daily/DietMetricsCard.vue           (新 — enabled=false 渲染占位)
├─ types/
│  └─ daily.ts                            (新 — 与后端 DTO 对齐)
```

### 7.3 视图布局

**DailyView**：桌面 ≥1024px 两行两列 4 卡；<1024px 单列堆叠。

```
┌──────────────────┬──────────────────┐
│ 任务              │ 日程              │
│ 完成 3 / 5        │ 事件 2           │
│ ▓▓▓▓▓▓░░░░ 60%   │ 10:00 站会       │
│                  │ 14:00 评审        │
├──────────────────┼──────────────────┤
│ 消费              │ 饮食              │
│ ¥128.50          │ 暂未启用          │
│ vs 昨日 +20.00    │ v1.2.4+ 启用     │
└──────────────────┴──────────────────┘

[本周] 2026-W29  周一~周日  ↔
[上一周 2026-W28]  完成率 50% → 65% (+15%)  事件 5 → 8 (+3)  消费 ¥820 → ¥1,200 (+¥380)
```

### 7.4 Pinia store

```ts
state: {
  daily: DailyReportPayload | null
  week: WeeklyReportPayload | null
  filter: { date: string }            // ISO YYYY-MM-DD，本地日
  loading: boolean
  error: string | null
}
getters: {
  taskCompletionRate: (s) => s.daily ? s.daily.task.completedCount / Math.max(s.daily.task.totalCount, 1) : 0,
  dietEnabled: (s) => s.daily?.diet.enabled ?? false,
}
actions: {
  fetchDaily(date)
  fetchWeek(date)
  resetFilter()
}
```

> 不缓存（与 diet/expense 一致）；切换日期时重新拉取。

## 8. 安全细节

- **越权**：日报 / 周报接口**无 `{id}` 路径**，天然不可越权；Provider 内部按 `userId` 过滤（CLAUDE.md §7.2）
- **写限流**：无（只读接口）
- **日期窗口**：超出 30 天 → 1001；防止拉取全表导致性能问题
- **错误日志**：不打印 userId 以外的 PII（CLAUDE.md §7.3）
- **SQL / 输入校验 / CORS / 错误日志**：沿用 06-expense-design §7

## 9. 测试策略

### 9.1 覆盖率门槛

沿用 05-nfr-testing §5.1（与消费模块同阈值）。

### 9.2 必备测试用例

**Provider 单测（4 类 × 4 cases = 16 cases）**：
- happy path：每个 Provider 给定 userId + 日期 → 正确聚合
- 跨用户过滤：传入不同 userId → 结果为 0 / empty
- 时间窗口边界：from / to + 1 day → 0
- 空集：userId 无数据 → 全 0

**Service 单测（≥12 cases）**：
- `daily` happy path：4 Provider mock → payload 正确组装
- `daily` 超出 30 天窗口 → 1001（×2：daily / week）
- `week` ISO 周边界：周一 / 周日 / 跨年（×3）
- `week` prev week 全 0 → delta = null（×3：taskCompletion / planEvents / expenseAmount）
- `daily` 未来日期允许（×1）
- `today()` 注入：package-private 重载便于单测固定日期

**Controller 切片（≥8 cases）**：鉴权 / 日期格式 / 日期缺失 / 日期超出窗口 / userId 注入 / 401 / 跨用户（不可达但保留断言）

**IT（≥6 cases，T8 性能断言）**：
- register → login → 注入 task/plan/expense → GET /daily → 4 类指标正确
- 周报：注入上周 + 本周数据 → 验证 triplet 正确
- 跨用户：userA 注入数据 → userB GET → 全 0
- T8 性能：1w 行种子（`PERF_SEED_ROWS=10000`）下 P95 ≤ `DAILY_P95_BUDGET=200ms` / `WEEKLY_P95_BUDGET=500ms`

**前端单测**：
- store ≥ 6 cases（fetchDaily / fetchWeek / completionRate getter / 错误处理 / resetFilter）
- DailyView ≥ 6 cases（4 卡渲染 / 周报切换 / 饮食占位卡 / 空态）
- DietMetricsCard ≥ 2 cases（enabled=false 占位 / 未来 enabled=true 数据渲染）

**E2E（≥4 cases）**：
- 登录 → 首页点日报卡 → 跳 /daily → 看到 4 卡
- 切换日期 → 数字变化
- 切到本周 → 周报 triplet 渲染
- 饮食卡显示「v1.2.4+ 启用」占位文案

### 9.3 流水线闸门

同 06-expense-design §8.3。

## 10. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 跨 4 表聚合性能差（1w 行种子下 P95 超预算） | 中 | V5 审计索引已覆盖；T8 IT 性能闸门；CI 过慢时下调 `PERF_SEED_ROWS` + 按比例放宽 `*_P95_BUDGET` |
| `delta=null` 前端遗漏处理 → 显示 NaN | 低 | WeeklyTriplet record 已明确 `Double`（包装类型）保证 null；前端 types 同构 |
| ISO 周跨年周起始日不符合中国用户预期 | 低 | ISO 8601 是行业标准；前端在 ISO week 标签后展示 `weekStart ~ weekEnd` 实际日期 |
| 饮食冻结契约被无意破坏 | 中 | `DietMetricProviderTest` 强制断言 `enabled=false` / `value=null` / reason 非空；任何变更 = 测试红 |
| 周报 7 次单日聚合 = 28 次 mapper 调用，单次 IT 耗时 | 低 | 接受 ≤500ms P95；T8 IT 闸门兜底 |

---

> **下一步**：本文 + 06-expense-design.md + 07-diet-design.md 一起通过后，由 writing-plans 技能生成 v1.2.3 实施计划。diet 解冻时新建 v1.2.4+ spec。