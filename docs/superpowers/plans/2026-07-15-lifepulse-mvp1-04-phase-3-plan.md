# Phase 3 — Plan module

> **Sub-specs to load at start of Phase 3:** `02-database §2.3`, `03-api-auth §5.4`, `04-frontend` § for plan views.
>
> **状态：✅ 已完成（2026-07-17，branch `feat/phase3-plan`，7 个 commit）。**
> 实际落地范围覆盖 T-P01..T-P05 + F-P01..F-P05；**F-H03 链接（本计划下的任务）** 已在 Phase 3.1 跟进（见下方 # Phase 3.1 — Plan × Task linkage）。

## Task T-P01: Flyway V3 — `t_plan`

- [x] **Step 1: V3** with columns + `idx_user_start` per `02-database §3`.
- [x] **Step 2: `flyway:info` confirm, commit** `feat(db): t_plan`（实现见 commit `d440490`）

---

## Task T-P02: `Plan` entity + `PlanMapper`

- [x] **Step 1: IT** insert, find by id, page by user+range (uses `idx_user_start`).
- [x] **Step 2: Implement** entity + mapper.
- [x] **Step 3: Run, commit** `feat(plan): plan entity and mapper`（实现见 commit `d440490`）

---

## Task T-P03: `PlanService` + cross-user check + range query

- [x] **Step 1: Unit tests** for range query, cross-user (1003), `endTime > startTime` (1001).
- [x] **Step 2: Implement**, commit `feat(plan): plan service`（实现见 commit `44cf8b1`）

---

## Task T-P04: `PlanController` 5 endpoints

- [x] **Step 1: Slice tests**, implement DTOs and controller matching `03-api-auth §5.4`.
- [x] **Step 2: Run, commit** `feat(plan): plan controller`（实现见 commit `573ab66`）

---

## Task T-P05: Plan integration test (incl. task link)

`PlanIT`: register → create plan → create task with `planId` → `GET /by-plan/{planId}` returns 1 task.

- [x] **Step 1: Test, run, commit** `test(plan): e2e with task link`（实现见 commit `573ab66` 内 `PlanFlowIT`）

---

## Task F-P01: `stores/plan.ts`

Same pattern as F-T01. Commit `feat(frontend): plan store`。
- [x] 实现见 commit `44733d7`。

---

## Task F-P02: `api/plan.ts`

- [x] **Step 1: Test, implement** wrapping endpoints, commit `feat(frontend): plan api`（实现见 commit `44733d7`）

---

## Task F-P03: `PlanCalendarView` (month view + event markers)

**Files:** `frontend/src/views/PlanCalendarView.vue`, `frontend/src/components/CalendarMonth.vue`, `frontend/src/utils/calendar.ts`

- [x] **Step 1: Test** `CalendarMonth` renders day grid + event dots.
- [x] **Step 2: Implement**: month grid via dayjs; click day → side panel list.
- [x] **Step 3: Run, commit** `feat(frontend): calendar month view`（实现见 commit `0f30b15`）

---

## Task F-P04: `PlanDetailView` + `EventDialog`

- [x] **Step 1: Test** dialog open/close + save.
- [x] **Step 2: Implement** event dialog (create/edit), detail read-only.
- [x] **Step 3: Run, commit** `feat(frontend): plan detail and dialog`（实现见 commit `0f30b15`）

---

## Task F-P05: Calendar e2e

`frontend/e2e/plan/plan-flow.spec.ts`：guard / empty / create / edit / delete / month switch / cross-user 1003 共 7 用例。`api-mock.ts` 增加 `setupPlanDefaults`（CRUD + from/to 范围查询）+ `mockPlanCrossUser`。

- [x] **Step 1: Write, run, commit** `test(frontend): plan e2e`（实现见 commit `5a2a86a`）
- 注：单测落地 50 个 Vitest 用例见 `b045d56`（calendar util + CalendarMonth + EventDialog + PlanCalendarView + PlanDetailView + plan store）。

---
