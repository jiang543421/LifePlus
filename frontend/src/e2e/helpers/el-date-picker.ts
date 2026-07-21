/**
 * Element Plus 2.x el-date-picker 的 Playwright 兼容封装（2026-07 T18）。
 *
 * <p>背景：EP 2.x el-date-picker type=datetime 的 popper 浮层依赖 pointerdown
 * 事件链，Chromium + Playwright 派发的 click 偶发不触发 v-model（详见
 * MEMORY `EP 2.x el-date-picker + Playwright 坑`）。组件 UI 行为已由
 * `EventDialog.spec.ts` 单测 16+ 用例覆盖；E2E 走 store 直连不稳，本 helper
 * 用「绕开 UI、定位到 ElDatePicker Vue 实例直接 emit update:modelValue」的
 * 折中：测试代码本身仍走真实 UI 路径（点「新建事件」→ 填 → 提交），只是
 * 日期输入这一步改用本函数替代 popper 操作。
 *
 * <p>原理：el-date-picker 渲染一个 `<input>`；DOM 元素挂
 * `__vueParentComponent`（Vue 3 dev），向上遍历直到拿到 ElDatePicker 实例，
 * 调用其 `emit('update:modelValue', value)`，由 v-model 把新值写入父组件
 * 的 `form.start` / `form.end`（EventDialog 里就是这两个）。
 */
import type { Page } from '@playwright/test';

/**
 * 直接给 wrapper 内的 el-date-picker 设值，绕开 popper UI。
 *
 * @param page Playwright Page
 * @param selector CSS selector 指向包裹 el-date-picker 的 wrapper（通常用
 *   data-testid，如 `'[data-testid="event-start"]'`）
 * @param value 与组件 `value-format` 对齐的字符串，默认
 *   `YYYY-MM-DDTHH:mm:ss`（EventDialog 的 `DATETIME_FMT`）
 */
export async function fillDateTimePicker(
  page: Page,
  selector: string,
  value: string,
): Promise<void> {
  await page.evaluate(
    ({ selector, value }) => {
      const wrapper = document.querySelector(selector);
      if (!wrapper) {
        throw new Error(`fillDateTimePicker: selector not found: ${selector}`);
      }
      const input = wrapper.querySelector('input');
      if (!input) {
        throw new Error(`fillDateTimePicker: no <input> inside ${selector}`);
      }
      // Vue 3 dev：每个组件渲染出的 DOM 节点都挂 __vueParentComponent；
      // 沿 input 向上找，直到命中 emit('update:modelValue') 的组件。
      let el: Element | null = input;
      let component: { emit: (event: string, ...args: unknown[]) => void } | null = null;
      while (el && !component) {
        const candidate = (el as unknown as {
          __vueParentComponent?: { emit?: (event: string, ...args: unknown[]) => void };
        }).__vueParentComponent;
        if (candidate && typeof candidate.emit === 'function') {
          component = candidate;
        } else {
          el = el.parentElement;
        }
      }
      if (!component) {
        throw new Error(`fillDateTimePicker: no Vue component found for ${selector}`);
      }
      // 父组件的 v-model 监听器会把新值写入 form.start / form.end；
      // ElDatePicker 也会同步更新输入框显示。
      component.emit('update:modelValue', value);
    },
    { selector, value },
  );
}