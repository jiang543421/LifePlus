# [Phase 2+] 日报 / 消费 / 饮食 / AI 分析四个占位卡落地

**描述**：首页 6 卡中 4 个（日报/消费/饮食/AI 分析）当前点击弹「即将上线」。MVP2 候选范围，需要先做用户调研/优先级/数据模型设计，再分 Phase 增量实现。

**Acceptance Criteria**：
- [x] 新增 `docs/specs/06-mvp2-modules-scoping.md`：4 卡片各写 1 段「用户故事 + 主要字段 + 数据源 + 风险」→ 已落地为 `docs/specs/06-expense-design.md` + `07-diet-design.md`（饮食候选）；日报 / AI 仍待 PR
- [x] 与各方确认后选 1-2 个先做；剩余继续占位（保留「即将上线」即可）→ v1.2.1 选定 expense
- [x] 选中的模块进入 Phase 2.x 计划（task / plan / scan / AI 等子模块分立）→ `docs/superpowers/plans/2026-07-20-expense-v1-2-1.md`
- [x] 后端按子模块新增表 + Flyway migration + Service/Controller/IT；前端新增 view/store/types → V4 `t_expense` + 7 Service 方法 + 7 Controller 端点 + IT；前端 ExpenseView/DetailView + Pinia store
- [x] 100% 单测 + 关键流 E2E；vue-tsc 0 error → 231/231 后端 + 368/368 前端 + 46/46 E2E 全绿
- [x] 首页卡 `placeholder: false`，直接路由跳到对应模块入口 → `home-card-expense` 激活，跳 `/expenses`

**Refs**：RELEASES/v1.0.0-mvp.md §1 / §5.1

---

## v1.2.1 完成度

| 子模块 | 状态 | 入口 | 备注 |
|---|---|---|---|
| 消费（expense） | **✅ 已完成** | `/expenses` | PR #13（commit `4ab5a87`）合并 main；Home 卡已激活 |
| 饮食（diet） | ⏳ 已设计未实现 | `/diet`（placeholder） | spec `07-diet-design.md` 已落档，留待 v1.2.2 决策 |
| 日报（daily） | ⏳ 未设计 | `/daily`（placeholder） | 暂无 spec |
| AI 分析（ai） | ⏳ 未设计 | `/ai`（placeholder） | 暂无 spec |

**完成时间**：2026-07-20
**完成版本**：v1.2.1
**完成 PR**：#13（`feat/expense-v1.2.1` → `main`）
**完整交付状态**：见 `RELEASES/v1.2.1.md`（如已发布）

> 本 issue 在 v1.2.1 范围内**部分关闭**：仅 expense 子模块完成。
> 剩余 3 子模块（饮食 / 日报 / AI 分析）仍 OPEN，建议拆为独立 issue `2026-07-20-diet-v1-2-2.md` 等分别跟踪。