# [优化] el-date-picker E2E 稳定性

**描述**：Element Plus 2.x `el-date-picker type=datetime` 的 popper 浮层在 Playwright + Chromium 下 pointerdown 事件链不可靠，v-model 偶发不更新。当前 plan-flow E2E 用 pinia store 直连绕开，但 UI 真实路径未覆盖。

**Acceptance Criteria**：
- [ ] 评估两条路之一：(a) 升级到 Element Plus 2.9+ 看是否修复；(b) 引入 `@playwright/test` 的 `page.locator('.el-date-editor').fill()` 自定义 helper 处理 popper
- [ ] 选定方案后，`plan-flow.spec.ts`「新建事件」用例恢复真实 UI 路径（点 + 选日期 + 提交），删除 `createPlanViaStore` 黑魔法
- [ ] 同样审计 `task-flow.spec.ts` 中 `setTaskFilter` 是否能恢复 `el-select` 真实点击
- [ ] 新增 helper `e2e/helpers/el-date-picker.ts` 与 `el-select.ts`，沉淀策略避免每个 spec 重写
- [ ] CI 跑 3 次全部稳定通过（无偶发），记录到 MEMORY

**Refs**：CLAUDE.md MEMORY (ep-2x-playwright-el-select.md) / RELEASES/v1.0.0-mvp.md §5.2
