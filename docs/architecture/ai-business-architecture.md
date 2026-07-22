# 业务架构设计 · AI 分析 v2.0

> 版本：v0.1 · 日期：2026-07-22 · 模块：`ai`（v2.0）
> 输入：[ai-v2-design.md §4](../superpowers/specs/2026-07-21-ai-v2-design.md) · [ai-v2-prd.md §3](../superpowers/specs/2026-07-21-ai-v2-prd.md)
> 索引：[ai-tech-architecture](./ai-tech-architecture.md) · [ai-data-model](./ai-data-model.md)

---

## 1. 业务定位

- **角色**：聚合 4 个领域模块（task / plan / expense / diet）的当日与本周指标，按规则拼出 1 句主文 + 3 个 chip delta
- **不做**：LLM 调用、跨用户推荐、个性化训练、离线缓存、推送
- **依赖方**：v1.2 模块（task v1.0 / plan v1.0 / expense v1.2.1 / diet v1.0）；daily v1.2.3 仅作为可选 provider 输入
- **输出形态**：`GET /api/v1/ai/insight/today`（读，60/min/user，30min Redis 缓存）+ `POST /api/v1/ai/insight/refresh`（写，6/min/user），被 HomeView 智能卡 + 抽屉详情复用

---

## 2. 模块列表

| 模块 | 类型 | 职责 | 边界（不做） |
|---|---|---|---|
| `AiInsightService` | Service | 编排 5 个 Provider → 聚合 → 模板渲染 → 缓存 | 不写日志到 DB、不调外部 API |
| `TaskMetricProvider` | Provider | 读 `t_task` 当日完成率 + 周趋势 | 不修改任务、不算逾期 |
| `PlanMetricProvider` | Provider | 读 `t_plan` 当日活动分钟 + 峰值小时 | 不写新事件、不改计划 |
| `ExpenseMetricProvider` | Provider | 读 `t_expense` 当日 + 本周支出与分类环 | 不算预算、不提醒 |
| `DietMetricProvider` | Provider | 读 `t_diet` 当日热量 + 蛋白达标率 | 不算 BMI、不推荐食谱 |
| `DailyAiProvider` | Provider | 读 daily 报告均值（开关受 `lp.ai.daily-enabled` 控制） | daily 模块自身报告逻辑 |
| `AiTemplateEngine` | Engine | 从 `ai-templates.properties` 加载 + `MessageFormat` 渲染 + 降级 | 不做国际化切换 |
| `AiInsightController` | Web | `GET /api/v1/ai/insight/today` + `POST /api/v1/ai/insight/refresh`，鉴权 + 限流 | 不暴露内部 Provider 状态 |

> Provider 是**接口**，Service 通过 `Map<String, MetricProvider>` 注入；开关 false 的 Provider 注入空实现，返回 NONE。

---

## 3. 模块依赖关系图

```
                  ┌─────────────────────────────┐
   HTTP ────────▶ │  AiInsightController         │  Web 层
                  └──────────────┬──────────────┘
                                 ▼
                  ┌─────────────────────────────┐
                  │  AiInsightService           │  Service 层
                  │  (orchestrator + cache)     │
                  └──────┬──────────────┬───────┘
                         │              │
        ┌────────────────▼──┐      ┌────▼──────────────────┐
        │  AiTemplateEngine   │      │  MetricProvider[]     │  Provider 层
        │  (MessageFormat)    │      │  (5 impls)            │
        └─────────────────────┘      └─┬───────┬───────┬─────┘
                                       │       │       │
                          ┌────────────▼─┐ ┌───▼───┐ ┌─▼──────────┐
                          │ TaskMapper   │ │ Plan… │ │ Expense…   │  Mapper 层
                          │ PlanMapper   │ │ Diet… │ │ (DailyR…)  │
                          └──────────────┘ └───────┘ └────────────┘
                                       │
                          ┌────────────▼──────────────┐
                          │  Redis (30 min TTL cache)  │  缓存层
                          └───────────────────────────┘
```

依赖方向：**Web → Service → Provider → Mapper → DB**；Service → Redis；Service → TemplateEngine。所有箭头**只指向下层**，禁止反向依赖。

---

## 4. 核心业务流程

### 4.1 端到端链路（GET /api/v1/ai/insight/today）

```
[1] 前端 HomeView mounted
        │
        ▼
[2] GET /api/v1/ai/insight/today  Authorization: Bearer <access>
        │
        ▼
[3] JwtAuthFilter 解析 → UserContext.current() = userId
        │
        ▼
[4] AiInsightController.insight()
        │   ├─ RateLimiter.check("lp:rl:ai:<userId>", 30/min)
        │   └─ 命中 → 抛 BusinessException(1006)
        ▼
[5] AiInsightService.getInsight(userId)
        │   ├─ Redis GET lp:ai:insight:<userId>
        │   ├─ 命中且未过期 → 反序列化 → 返 [7]
        ▼
[6] 并行调用 5 个 Provider.collect(userId, today, weekStart)
        │   每个 Provider：若开关 false / 异常 → 返回 MetricValue.none()
        ▼
[7] AiTemplateEngine.formatHeadline("headline.full", v1, t1, v2, t2)
        │   + 3 × formatChipDelta("taskCompletion", trend, value)
        ▼
[8] 组装 AiInsightResponse（headline + 3 chips + generatedAt）
        │
        ├─ Redis SETEX lp:ai:insight:<userId> 1800 payload
        ▼
[9] 200 OK { success: true, data: AiInsightResponse }
```

### 4.2 数据流时序

| 步骤 | 组件 | 耗时预算（P95） | 失败处理 |
|---|---|---|---|
| 1-3 | Filter + Controller | ≤10 ms | 401 / 403 早返 |
| 4 | 限流 | ≤2 ms | 1006 |
| 5 | Redis GET | ≤5 ms | miss → 续走 |
| 6 | 5 Provider 并行 | ≤120 ms | 任一失败 → 该 chip=NONE，整体仍返 |
| 7 | 模板渲染 | ≤5 ms | 降级 `fallback.headline` |
| 8-9 | 序列化 + 写缓存 | ≤10 ms | 写失败仅 WARN，不影响响应 |

整体 P95 目标 **≤200 ms**；超时（>2 s）触发 4 级降级（见 §5.1）。

---

## 5. 异常处理策略

### 5.1 4 级降级

| 级别 | 触发 | 主文 | chip | HTTP |
|---|---|---|---|---|
| L0 正常 | 全部成功 | `headline.full` | 实数值 | 200 |
| L1 部分缺失 | ≥1 Provider 抛错 | `headline.taskOnly` | 仅成功项 | 200 |
| L2 全失败 | 全部 Provider 抛错 | `headline.empty` | 全 `—` | 200 |
| L3 服务降级 | Redis 不可用 + DB 超时 | `fallback.headline` = "数据异常，请稍后重试" | 全 `—` | 200（前端仍可渲染） |

**不返 5xx**：前端拿到 200 + 占位文案即可展示，避免无限 spinner。

### 5.2 异常分类

| 异常 | 抛出位置 | 转换 |
|---|---|---|
| Provider 业务异常（mapper null） | Provider 内部 catch | log.warn + 返回 `MetricValue.none()` |
| Provider 未知异常 | Provider 内部 catch | log.error + 同上 |
| Redis 故障（GET/SET 失败） | Service catch | log.warn + 跳过缓存（不计 L3） |
| 模板键缺失 | Engine 启动期 | `IllegalStateException` → 启动失败 |
| 限流触发 | Controller 前置 | `BusinessException(1006)` → 1006 |
| 鉴权失败 | JwtAuthFilter | 401 |

### 5.3 跨用户越权

Provider 一律按 `userId` 过滤；Mapper 方法签名必含 `userId`，无 userId 参数的方法禁止接入 AI 链路。

---

## 6. 与现有模块的契约

| 调用方 | 契约 | 备注 |
|---|---|---|
| `TaskMapper.countByUserDueBetween` | 入参 `(userId, from, to)` 出参 `int` | 现有 v1.0 |
| `PlanMapper.sumActiveMinutesByUserOnDay` | 入参 `(userId, day)` 出参 `long` | 现有 v1.0 |
| `ExpenseMapper.sumByUserOccurredBetween` | 入参 `(userId, from, to)` 出参 `BigDecimal` | v1.2.1 |
| `DietMapper.sumCaloriesByUserOnDay` | 入参 `(userId, day)` 出参 `int` | v1.0 |
| `DailyReportService.weeklyAverage` | 入参 `userId` 出参 `DailySummary` | 仅当 `lp.ai.daily-enabled=true` |

任一 Mapper 方法签名变更 → AI 模块同步调整；CI 通过 `mvn verify` 强制编译失败兜底。
