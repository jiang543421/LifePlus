# Phase 4 — Home dashboard

> **Sub-spec:** `04-frontend §4` only.

## Task F-H01: `HomeView` with 4 cards + 2 placeholders

- [ ] **Step 1: Test** ModuleCard emits click; PlaceholderCard toast on click.
- [ ] **Step 2: Implement** `grid-template-columns: repeat(3,1fr)`, 6 cards.
- [ ] **Step 3: Run, commit** `feat(frontend): home dashboard`

---

## Task F-H02: `TopBar` + `UserMenu`

- [ ] **Step 1: Test** UserMenu triggers `auth.clear()` + push `/login`.
- [ ] **Step 2: Implement** menu 我的资料 / 设置 / 退出登录.
- [ ] **Step 3: Run, commit** `feat(frontend): top bar and user menu`

---

## Task F-H03: Link task ↔ plan in PlanDetailView

Add "本计划下的任务" section using `api.task.byPlan(planId)`; create-task button pre-fills `planId`.

- [ ] **Step 1: Test** empty state + populated state; commit `feat(frontend): task list under plan`

---

## Task F-H04: Responsive breakpoints

Media queries `@media (max-width:1023px)` → 2 cols; `<767px` → 1 col + collapsed top bar.

- [ ] **Step 1: Lint passes; manual verify; commit** `feat(frontend): responsive layout`

---
