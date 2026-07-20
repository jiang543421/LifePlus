/**
 * Element Plus 2.x el-select 的 Playwright 兼容封装（2026-07 T18）。
 *
 * <p>背景：EP 2.x el-select 选项的 select 触发器绑定在 `pointerdown.prevent`
 * 上；Playwright 在 Chromium 下 `.click()` 派发的 pointerdown 序列偶发不
 * 触发 v-model（hover 移动 aria-activedescendant 但 click 不 commit）。
 * 详见 CLAUDE.md MEMORY `ep-2x-playwright-el-select`。
 *
 * <p>组件 UI 行为由 `TaskFilters.spec.ts` 单测覆盖；E2E 关注「选项 → store →
 * API」链路。本 helper 沿 DOM 上溯，找到**对 `update:modelValue` 有 v-model
 * 监听器**的 Vue 组件实例（即 ElSelect 本身），直接 emit 新值，跳过 popper
 * 点击。终点与原 `setTaskFilter`（绕去 store 直连）等价，但走的是真实
 * `update:modelValue → emitPatch → update:filter → onFilterUpdate` 链路，
 * 不是 pinia hack。
 *
 * <p>与 `el-date-picker.ts` 是一对：都在 DOM 上找含 v-model 监听器的最近
 * 祖先 Vue 组件实例，对其 `emit('update:modelValue', value)`。
 */
import type { Page } from '@playwright/test';

interface VueComponentInstance {
  emit: (event: string, ...args: unknown[]) => void;
  vnode?: { props?: Record<string, unknown> };
}

/**
 * 给 wrapper 内的 el-select 直接 emit 新值，绕开 popper 点击。
 *
 * @param page Playwright Page
 * @param wrapperSelector CSS selector 指向包裹 el-select 的 wrapper
 *   （通常用 data-testid，如 `'[data-testid="filter-status"]'`）
 * @param value 要选中的选项值（与 el-option 的 `:value` 一致）
 */
export async function selectElOption(
  page: Page,
  wrapperSelector: string,
  value: string | number,
): Promise<void> {
  await page.evaluate(
    ({ wrapperSelector, value }) => {
      const wrapper = document.querySelector(wrapperSelector);
      if (!wrapper) {
        throw new Error(`selectElOption: selector not found: ${wrapperSelector}`);
      }
      // 沿 DOM 上溯，找到对 update:modelValue 有 v-model 监听器的 Vue 实例。
      // 起点：wrapper 内的 <input>（el-select 触发器）。
      const input = wrapper.querySelector('input');
      let el: Element | null = input ?? wrapper;
      let target: VueComponentInstance | null = null;
      while (el && !target) {
        const inst = (el as unknown as { __vueParentComponent?: VueComponentInstance })
          .__vueParentComponent;
        if (
          inst &&
          typeof inst.emit === 'function' &&
          inst.vnode?.props &&
          'onUpdate:modelValue' in inst.vnode.props
        ) {
          target = inst;
        } else {
          el = el.parentElement;
        }
      }
      if (!target) {
        throw new Error(
          `selectElOption: no component with v-model listener for ${wrapperSelector}`,
        );
      }
      // v-model 监听器（statusModel.setter）会被触发，写回 local 并继续 emit
      // update:filter，与前端正常点击选项的链路一致。
      target.emit('update:modelValue', value);
    },
    { wrapperSelector, value },
  );
}