# Phase 3.1 — Plan × Task linkage（F-H03 补完）

> **Sub-specs to load:** `03-api-auth §5.3`（Task API 复用，by-plan 端点）+ `04-frontend`（PlanDetailView 视图段）。
>
> **状态：✅ 已完成（2026-07-17，branch `feat/phase3-1-plan-tasks` 基于 `main` @ `d420c72`，8 个 commit）。**
> 后端端点 `GET /tasks/by-plan/{planId}` 已随 Phase 2 就绪（TaskMapper + TaskService + TaskController + IT 全覆盖），本次仅做前端集成 + 状态扩展 + E2E 补完。

## 目标

`PlanDetailView` 加载并展示本日程下的关联任务（PRD F-H03）。复用后端 `/tasks/by-plan/{planId}` 端点（user_id+plan_id 联合过滤 → 隐式跨用户隔离，返回空列表而非 1003）。

## Task T-31-A: `task` store 扩展 `fetchByPlan` action

- [x] **Step 1: Spec** `stores/task.spec.ts` 新增 `fetchByPlan` describe（4 case：成功 / ApiError / 通用错 / 成功重置错），commit `test(task): add fetchByPlan store spec`（commit `9caead6`）
- [x] **Step 2: Implement** state 加 `byPlanTasks / byPlanLoading / byPlanError`，action 复用 taskApi.byPlan；commit `feat(task): add fetchByPlan action and by-plan state`（commit `8452c75`）

## Task F-31-B: 新组件 `PlanTaskList.vue`

- [x] **Step 1: Spec** `components/__tests__/PlanTaskList.spec.ts` 覆盖 4 态 + 交互（点击 emit open / planId 变化重新拉取）；commit `test(plan): add PlanTaskList component spec`（commit `2928749`）
- [x] **Step 2: Implement** 纯展示组件，props `planId: number`，emit `open(id)`；commit `feat(plan): add PlanTaskList component for plan detail related tasks`（commit `abffd53`）

## Task F-31-C: `PlanDetailView` 嵌入 `PlanTaskList`

- [x] **Step 1: Spec** `views/__tests__/PlanDetailView.spec.ts` 新增 3 case（渲染区块 / 点击跳转 / 加载失败时不渲染）；commit `test(plan): add F-H03 related-tasks assertions to PlanDetailView spec`（commit `43da48e`）
- [x] **Step 2: Implement** article 内 `<PlanTaskList :plan-id="plan.id" @open="onTaskOpen" />`，`onTaskOpen` 路由到 `/tasks/${id}`；commit `feat(plan): embed PlanTaskList in PlanDetailView`（commit `16ceb8f`）

## Task F-31-D: E2E 补完

- [x] **Step 1: Spec** `e2e/plan/plan-flow.spec.ts` 新增 case（创建 plan → 创建 2 task planId 指向 → 详情页看 2 行），`Promise.all` 同时订阅 `waitForResponse` 与 `goto`；commit `test(e2e): add F-H03 related-tasks assertion to plan flow`（commit `20e14a9`）
- [x] **Step 2: Mock** `e2e/helpers/api-mock.ts` 加 `GET /tasks/by-plan/{planId}` 分支（用 `state.detail.values()` 反查，因为 `TaskListItem` 无 planId 字段）；commit `test(e2e): mock /tasks/by-plan endpoint in api-mock helper`（commit `1130e1c`）
- [x] **Step 3: Run all** `npx playwright test plan-flow.spec.ts` → 8/8 passed

## 测试覆盖

| 层 | 用例 | 备注 |
|---|---|---|
| store 单测 | `stores/task.spec.ts` 5 case | fetchByPlan 4 态 + 重置 |
| 组件单测 | `PlanTaskList.spec.ts` 6 case | 4 态 + 2 交互 |
| 视图单测 | `PlanDetailView.spec.ts` 3 case | 渲染区块 / 跳转 / 失败不渲染 |
| E2E | `plan-flow.spec.ts` 新增 1 case（总 8） | 详情页 2 行关联任务 |

## 影响面

- 后端零改动（`/tasks/by-plan/{planId}` 已在 Phase 2 完成）
- store 状态加 3 字段（`byPlanTasks / byPlanLoading / byPlanError`），与现有 `list / loading / error` 平行，互不污染
- 跨用户越权由后端 `user_id + plan_id` 联合过滤兜底，返回空列表而非 1003

---
