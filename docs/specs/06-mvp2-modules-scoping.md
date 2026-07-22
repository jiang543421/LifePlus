# 06 — MVP2 模块范围与选型记录

> 本文件是 LifePulse v1.2 系列（Phase 2+）的**模块范围决策记录**，对应 issue：`docs/issues/2026-07-18-mvp2-placeholder-modules.md`。
> 不包含各模块的详细技术设计——后者见 06-expense-design / 07-diet-design / 08-daily-report-design 各自的 sub-spec。
>
> **索引**：[01-architecture](./01-architecture.md) · [02-database](./02-database.md) · [03-api-auth](./03-api-auth.md) · [04-frontend](./04-frontend.md) · [05-nfr-testing](./05-nfr-testing.md) · [06-expense-design](./06-expense-design.md) · [07-diet-design](./07-diet-design.md) · [08-daily-report-design](./08-daily-report-design.md)

---

## 1. 背景

MVP1（v1.0.0-mvp）上线后首页 6 卡中**有 4 卡**仍为占位（"即将上线"）：

| 卡片 | 路由 | 状态（v1.0.0-mvp 发布时） |
|---|---|---|
| 日报 | `/daily` | placeholder |
| 消费 | `/expenses` | placeholder |
| 饮食 | `/diet` | placeholder |
| AI 分析 | `/ai` | placeholder |

详见 `RELEASES/v1.0.0-mvp.md` §5.1 与 `docs/issues/2026-07-18-mvp2-placeholder-modules.md`。

## 2. 选型决策（issue AC 验证记录）

| AC | 验证 |
|---|---|
| 新增 `docs/specs/06-mvp2-modules-scoping.md`（4 卡片各 1 段） | **本文**（v1.2.1 落档） |
| 与各方确认后选 1-2 个先做；剩余继续占位 | v1.2.1 选定 **expense**（v1.0.0-mvp 之后第一个增量） |
| 选中的模块进入 Phase 2.x 计划 | `docs/superpowers/plans/2026-07-20-expense-v1-2-1.md` |
| 后端按子模块新增表 + Flyway + Service/Controller/IT；前端新增 view/store/types | V4 `t_expense` + 7 Service 方法 + 7 Controller 端点 + IT；ExpenseView/DetailView + Pinia store |
| 100% 单测 + 关键流 E2E；vue-tsc 0 error | 231/231 后端 + 368/368 前端 + 46/46 E2E 全绿 |
| 首页卡 `placeholder: false`，直接路由跳到对应模块入口 | `home-card-expense` 激活，跳 `/expenses` |

## 3. 四卡片概要（用户故事 + 主要字段 + 数据源 + 风险）

> 每卡片 1 段；详细设计见对应 sub-spec。

### 3.1 日报（daily）

- **用户故事**：作为 LifePulse 用户，我在一个视图里就能看到「今天/本周做了什么、效率如何、钱花在哪」，不需要在多个工具间切换
- **主要字段**：任务完成率、当日事件数、当日消费总额、饮食汇总（v1.2.3 冻结）
- **数据源**：跨 `t_task` / `t_plan` / `t_expense` / `t_diet` 4 表**只读实时聚合**；不引入新表
- **风险**：跨 4 表聚合性能；通过 V5 索引审计 + IT T8 P95 闸门控制（详细见 08-daily-report-design §10）
- **详情**：[08-daily-report-design.md](./08-daily-report-design.md)

### 3.2 消费（expense）

- **用户故事**：作为用户，我希望快速记录日常开销、按分类查看月度汇总，以便了解钱花在哪、是否超预算
- **主要字段**：金额（`DECIMAL(12,2)`）/ 分类（预置 14 类）/ 发生时间 / 备注 / 软删
- **数据源**：`t_expense`（v1.2.1 V4 新建）
- **风险**：金额精度（用 BigDecimal 而非 double）；不做预算 / 不做循环账单（v1.0 PRD §6.2 显式不做）
- **详情**：[06-expense-design.md](./06-expense-design.md)

### 3.3 饮食（diet）

- **用户故事**：作为用户，我希望随手记录一餐吃了什么、手填四项营养（kcal / protein / carb / fat），以便了解摄入结构
- **主要字段**：餐别 / 名称 / kcal / protein / carb / fat / 发生时间 / 备注
- **数据源**：`t_diet`（v1.2.2 V6 新建）
- **风险**：手填营养用户嫌麻烦 → 数据稀疏；通过"高频名称一键复用"UI + 30 天 top 10 聚合缓解
- **详情**：[07-diet-design.md](./07-diet-design.md)

### 3.4 AI 分析（ai）

- **用户故事**：作为用户，我希望系统能基于我已有的任务 / 日程 / 消费数据自动总结趋势、提示异常（如"本周消费 +30% vs 上周"），而不是自己算
- **主要字段**：洞察文本（人类可读句子）/ 关联模块（task / plan / expense / diet）/ 关联时段（day / week）
- **数据源**：与 daily 共享 — 同样跨 `t_task` / `t_plan` / `t_expense` / `t_diet` 聚合；**新增** LLM 推理层（详见 `docs/architecture/ai-business-architecture.md`）
- **风险**：LLM 推理成本 / 延迟；v2.0 已通过模板化降本（不直接调 LLM 做长文本生成）
- **详情**：`docs/superpowers/specs/2026-07-21-ai-v2-design.md` + `docs/prd/06-...`（待补）；本文仅占位

## 4. 排期（实际交付记录）

| 版本 | 模块 | PR | 入口 | spec |
|---|---|---|---|---|
| **v1.2.1** | expense | #13（commit `4ab5a87`）→ `main` | `/expenses` | 06-expense-design |
| **v1.2.2** | diet | 未发版（v1.2.2 后被 diet 设计冻结 + AI v2 优先） | `/diets` | 07-diet-design |
| **v1.2.3** | daily | 进行中 | `/daily` | 08-daily-report-design |
| **v2.0.0** | ai | 进行中（`feat/ai-v2.0`） | `/ai` | ai-v2-design |

## 5. 范围边界（v1.0 PRD §6.2 / `docs/prd/04-future-prd.md` 同步约束）

- **不做**：团队 / 共享 / 权限分级；图片识别；离线缓存；支付订阅；多语言；预算 / 循环账单；多账户；导入导出；食物库 / OCR（饮食模块）
- **本期延后**：饮食解冻（v1.2.4+）、AI 主动建议 / 对话（v2.1+）、跨用户协作（v3+）

---

> **后续动作**：本文件作为 MVP2 选型的**决策记录**，不再随各模块技术细节变化而修改；任何模块的范围调整（如新增 / 取消 / 推迟）需更新 §4 排期表与对应 sub-spec。