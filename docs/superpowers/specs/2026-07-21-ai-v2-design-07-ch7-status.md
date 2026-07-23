## 14. 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| `DailyAiProvider` 接口未提供 `computeStreak` | T4.6 阻塞 | 实现前先在 `ai` 包加 `computeStreak(userId): MetricValue`，独立 commit |
| 模板表达力不足，未来扩展受限 | 中 | 预留 v2.1 切换到 `String.format` + 简单条件足够；如不够再升 Mustache |
| 单实例 Redis 故障导致缓存永久失效 | 低 | Redis 不可用时降级到无缓存模式（§10） |
| 用户误判 insight 不准 | 低 | 留 v2.1 加"反馈"按钮，本期不做 |
| `expense` v1.2.1 / `daily` v1.2.3 未合 main 导致 v2.0 测试失败 | 中 | `isEnabled` 配置开关兜底，CI 跑前确认配置 |

---

## 15. 开放问题

| # | 问题 | 决定时机 |
|---|---|---|
| Q1 | v2.1 独立分析页的导航入口位置 | v2.0 末期调研 |
| Q2 | 是否给"刷新"按钮加 cooldown（前端禁用 5 秒） | 实施时决定 |
| Q3 | chip 顺序是否用户可调 | v2.1+ |
| Q4 | 是否对"连续 7 天无 insight"做引导 | v2.1+ |
| Q5 | `DietMetricProvider` 在日报中冻结锁，AI 模块是否复用其实现 | **T4.5 启动时决定**：默认不复用，AI 模块独立写 |

---

## 16. 参考文献

- 项目级规范：`CLAUDE.md` §1-9
- MVP1 设计索引：`docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md`
- API 规范：`docs/specs/03-api-auth.md`（沿用鉴权 + MyResponse 信封）
- 数据库规范：`docs/specs/02-database.md`（仅读既有表，不新增）
- 日报 v1.2.3 实施计划：`docs/superpowers/plans/2026-07-21-daily-v1-2-3.md`（参考 Provider 模式）
- Expense v1.2.1 实施计划：`docs/superpowers/plans/2026-07-20-expense-v1-2-1.md`

---

## 17. 后续步骤

1. 用户审阅本 spec
2. 用户认可后调用 `superpowers:writing-plans` 拆分实施任务
3. 不直接进入实现；先有 plan，再按 TDD 落地

---

## 18. 实施状态（2026-07-22 补完）

> 本节记录 v2.0 实际交付与原 spec 的偏差。完整 PR 拆分见 `docs/superpowers/plans/2026-07-22-ai-v2-0.md`。

### 18.1 交付总览

| 项 | 状态 | 备注 |
|---|---|---|
| 5 个 Provider（Task/Plan/Expense/Diet/Daily stub） | ✅ 已实现 | Daily 默认 `isEnabled=false`，其他 4 个默认 `true` |
| 缓存（Redis 30 min TTL） | ✅ 已实现 | key=`ai:insight:{userId}`；GET 命中不延长 TTL；`/refresh` 覆写同 key |
| 模板引擎（headline + chip 副标） | ✅ 已实现 | `ai-templates.properties` + JDK `String.format` |
| 降级（1501） | ✅ 已实现 | 所有 enabled provider 失败时抛 `BusinessException(1501)` |
| 鉴权（JWT access token） | ✅ 已实现 | `@AuthenticationPrincipal Long userId` |
| 限流（60/min GET、6/min POST） | ✅ 已实现（**用户维度**，见 18.2） |
| 后端单测 / 切片 / Testcontainers IT | ✅ 已实现 | 386/386 全绿 |
| 前端类型 / api / AiDrawer / HomeView 接入 | ✅ 已实现 | 22 个新 vitest 用例 |
| E2E（Playwright） | ✅ 已实现 | 5 个用例全绿：`ai-flow.spec.ts` |
| 文档（本 spec + README） | ✅ 已实现 | 本节 + `README.md` §1 / §8 已标记 |

### 18.2 与原 spec 的偏差

| 项 | 原 spec | 实际实现 | 说明 |
|---|---|---|---|
| URL 前缀 | `/api/ai/insight/today` | `/api/v1/ai/insight/today` | 与既有 task/plan/expense/diet 路由统一走 `/api/v1` 前缀（CLAUDE.md §3 顶层约束）|
| Controller 类名 | `AiAnalysisController` | `AiInsightController` | 与 Service / DTO 命名收敛到 "Insight" 单一术语 |
| Service 类名 | `AiAnalysisService` | `AiInsightService` | 同上 |
| Trend enum 大小写 | `up` / `down` / `flat` / `none` | `UP` / `DOWN` / `FLAT` / `NONE` | 与 Java enum 命名约定对齐（大写）|
| 限流维度 | IP（60/min/IP、6/min/IP） | **用户**（60/min/user、6/min/user）| 复用既有 `RateLimiter.checkLimit(userId, ...)`（CLAUDE.md §7.2 与登录限流同维度）；键前缀 `lp:rl:ai:insight:`（AiConstants.INSIGHT_RL_KEY_PREFIX）|
| 缓存键前缀 | `ai:insight:{userId}` | 同上 | 无偏差 |
| `freshnessSeconds` | Controller 现算 | 同上 | 无偏差 |
| 错误码 1501（AI_DEGRADED）| 新增 | 同上 | `ErrorCode.AI_DEGRADED = 1501` |
| `daily-enabled` 默认值 | `false` | 同上 | `application.yml` 显式写入（CLAUDE.md §2.2）|

### 18.3 范围裁剪

| 范围 | 决策 |
|---|---|
| 独立分析页 `/ai-analysis` | **未实现**（按原 spec 留 v2.1+）|
| 趋势迷你图 / 图表 | **未实现**（v2.1+）|
| LLM 调用 | **未实现**（按 spec 不引入）|
| 多语言 i18n | **未实现**（按 spec 不引入）|
| 反馈按钮 | **未实现**（v2.1+）|

### 18.4 验收实测（PR 6 收尾时）

| 指标 | 结果 |
|---|---|
| 后端 `mvn verify` | 386/386 全绿（Service 行覆盖 ≥ 85%） |
| 前端 `vitest run` | 473/473 全绿（41 个 spec 文件） |
| 前端 `vue-tsc` 类型检查 | AI 文件全绿（剩余 3 条为无关的 diet 模块预存错误）|
| E2E `playwright test src/e2e/ai` | 5/5 全绿（含跨用户隔离断言）|
| 后端 `/api/v1/ai/insight/today` 集成（Testcontainers）| 5 个 IT 用例全绿，含缓存 hit/miss、daily 降级、cross-user 隔离 |

### 18.5 已知未决（延后）

- **Q1**：v2.1 独立分析页的导航入口位置 — 留待 v2.0 末期调研
- **Q2**：刷新按钮 cooldown — 当前 spec 未要求，已实现"刷新中转圈 disabled"作为最小抑制
- **Q3**：chip 顺序用户可调 — 留 v2.1+
- **Q4**：连续 7 天无 insight 引导 — 留 v2.1+
- **Q5**：Diet provider 复用 daily `DietMetricProvider` — 当前 AI 模块独立写，未复用（spec §15 已决定）

---

## 19. 合并清单（待 squash 到 main 时检查）

- [ ] PR #16：T1-T3 项目脚手架 + 领域模型 + 模板引擎
- [ ] PR #17：T4 Provider 5 实现 + 单测
- [ ] PR #18：T5 Service 编排（缓存 + 降级 + 5 provider 串联）
- [ ] PR #19：T6 Controller（限流 + 鉴权）+ 集成测试
- [ ] PR #20：T7 前端实现（types + api + AiDrawer + HomeView 接入）
- [ ] PR #21：T8 E2E + 文档收尾（本 spec §18 + README）
- [ ] 合并后打 tag `v2.0.0-ai` + 写 `RELEASES/v2.0.0-ai.md`
