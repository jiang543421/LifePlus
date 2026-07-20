# [优化] el-date-picker E2E 稳定性

**描述**：Element Plus 2.x `el-date-picker type=datetime` 的 popper 浮层在 Playwright + Chromium 下 pointerdown 事件链不可靠，v-model 偶发不更新。当前 plan-flow E2E 用 pinia store 直连绕开，但 UI 真实路径未覆盖。

**Acceptance Criteria**：
- [x] 选定方案 (b)：自定义 helper `e2e/helpers/el-date-picker.ts` + `e2e/helpers/el-select.ts`，绕开 popper 走真实 dialog / select 流程（仅日期 / 选项输入步骤由 helper 替代 popper 操作）
- [x] `plan-flow.spec.ts`「新建事件」用例恢复真实 UI 路径：点「+ 新建事件」→ `fillDateTimePicker` 填起止 → 填标题 → 提交；删除 `createPlanViaStore` 黑魔法
- [x] 同样审计 `task-flow.spec.ts`：删除 `setTaskFilter` 黑魔法，「列表筛选 status=TODO」用例改用 `selectElOption` 走真实 select 链路（emit update:modelValue → statusModel setter → emit update:filter → onFilterUpdate → API）
- [x] 新增 helper `e2e/helpers/el-date-picker.ts` + `e2e/helpers/el-select.ts`
- [x] 3 轮全量 E2E（46/46 x 3）全部稳定通过，无偶发；记录到 MEMORY

**Refs**：CLAUDE.md MEMORY (ep-2x-playwright-el-select.md) / RELEASES/v1.0.0-mvp.md §5.2
