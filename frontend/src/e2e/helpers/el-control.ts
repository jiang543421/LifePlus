/**
 * Element Plus 2.x 表单控件 Playwright 兼容封装（统一入口，2026-07 T19）。
 *
 * <p>背景：EP 2.x 的 el-select / el-date-picker / el-input-number 在真实
 * Chromium + Playwright 下 popper 浮层 / 按钮链触发不可靠：
 * <ul>
 *   <li>el-select 选项 click 偶发不 commit v-model（hover 移动
 *       aria-activedescendant 但 click 不触发 update:modelValue）</li>
 *   <li>el-date-picker type=datetime 的 popper 浮层依赖 pointerdown 链，
 *       派发的 click 偶发不触发 update:modelValue</li>
 *   <li>el-input-number 的 +/- 按钮在自动化 click 下偶发值跳变（与
 *       输入框值不一致）</li>
 * </ul>
 *
 * <p>统一方案：所有控件走「DOM 上溯找含 v-model 监听器的最近 Vue 组件实例，
 * 直接 emit('update:modelValue', value)」路径。组件 UI 行为由对应单测
 * 覆盖（TaskFilters.spec / EventDialog.spec / DietDialog.spec 等）；E2E
 * 关注「输入 → store → API」链路稳定性。
 *
 * <p>关键约束：
 * <ol>
 *   <li>判定条件：组件实例的 `vnode.props['onUpdate:modelValue']` 必须存在
 *       —— 表明它是 v-model 的接收方（即 ElSelect / ElDatePicker /
 *       ElInputNumber 自身），不是某个 wrapper / 业务组件</li>
 *   <li>起点：wrapper 内 `<input>` 元素（EP 控件都渲染 input 作为触发器）</li>
 *   <li>命中后 `instance.emit('update:modelValue', value)`，触发 v-model 监听器
 *       → 父组件 setter → 子组件 view 同步更新</li>
 * </ol>
 *
 * <p>本文件统一了原先 `helpers/el-select.ts` 与 `helpers/el-date-picker.ts`
 * 重复的 Vue 组件上溯逻辑，并补全 `fillElInputNumber`。旧 helper 文件改为
 * re-export shim，保持 import 兼容。
 */
import type { Page } from '@playwright/test';

/** Vue 3 dev 模式下挂在 DOM 节点上的组件实例结构（仅取本 helper 用到的字段）。 */
interface VueComponentInstance {
  emit: (event: string, ...args: unknown[]) => void;
  vnode?: { props?: Record<string, unknown> };
}

/**
 * 给 wrapper 内的 EP 表单控件直接 emit 新值，绕开 popper / 按钮 click。
 *
 * <p>必须把 Vue 组件上溯逻辑写在 page.evaluate 闭包内（page.evaluate 序列化
 * 函数引用会丢失闭包上下文）。三个公开 helper 共享同一闭包体，仅 helper 名
 * 与 value 类型不同。
 */
async function emitToVModelTarget(
  page: Page,
  wrapperSelector: string,
  value: string | number,
  helperName: string,
): Promise<void> {
  await page.evaluate(
    ({ wrapperSelector, value, helperName }) => {
      const wrapper = document.querySelector(wrapperSelector);
      if (!wrapper) {
        throw new Error(`${helperName}: selector not found: ${wrapperSelector}`);
      }
      const input = wrapper.querySelector('input');
      let el: Element | null = input ?? wrapper;
      let strictMatch: VueComponentInstance | null = null;
      let looseMatch: VueComponentInstance | null = null;
      // 两段式 walk：
      // 1. 严格：要求 vnode.props['onUpdate:modelValue']（v-model 接收方）。
      //    命中即用 — 适用于 ElSelect / ElInputNumber 等组件实例直接挂在
      //    渲染根 DOM 节点的情况。
      // 2. 兜底：首个 emit-capable Vue 实例（从 input 沿 DOM 上溯）。
      //    适用于 ElDatePicker：其内部 ElInput 子组件的 __vueParentComponent
      //    先被命中，但 ElInput 没有 v-model 监听器；继续上溯到 ElDatePicker
      //    后 vnode.props 的 onUpdate:modelValue 在某些 EP 版本下不暴露给
      //    __vueParentComponent.props 读取（旧版 el-date-picker helper 即用
      //    此兜底通过 emit 向上冒泡到 ElDatePicker）。
      while (el && !strictMatch) {
        const inst = (el as unknown as { __vueParentComponent?: VueComponentInstance })
          .__vueParentComponent;
        if (inst && typeof inst.emit === 'function') {
          if (
            inst.vnode?.props &&
            'onUpdate:modelValue' in inst.vnode.props
          ) {
            strictMatch = inst;
            break;
          }
          if (!looseMatch) {
            looseMatch = inst;
          }
        }
        el = el.parentElement;
      }
      const target = strictMatch ?? looseMatch;
      if (!target) {
        const label =
          wrapper.getAttribute('data-testid') ?? wrapper.tagName.toLowerCase();
        throw new Error(
          `${helperName}: no emit-capable Vue component for "${label}"`,
        );
      }
      target.emit('update:modelValue', value);
    },
    { wrapperSelector, value, helperName },
  );
}

// ---------------------------------------------------------------------------
// 公开 API
// ---------------------------------------------------------------------------

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
  await emitToVModelTarget(page, wrapperSelector, value, 'selectElOption');
}

/**
 * 给 wrapper 内的 el-date-picker 直接 emit 新值，绕开 popper 浮层。
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
  await emitToVModelTarget(page, selector, value, 'fillDateTimePicker');
}

/**
 * 给 wrapper 内的 el-input-number 直接 emit 新值，绕开 +/- 按钮 click。
 *
 * <p>EP 2.x el-input-number 在 Playwright 下用按钮 click 触发值跳变偶发与
 * 输入框显示不同步；input.fill() 在某些值（如 `0`、超长串）下也会触发边界
 * 行为不一致。统一走 emit 链路确保 store 端立即收到目标值。
 *
 * @param page Playwright Page
 * @param selector CSS selector 指向包裹 el-input-number 的 wrapper（通常用
 *   data-testid，如 `'[data-testid="diet-dialog-kcal"]'`）
 * @param value 数值（与 el-input-number 的 `:model-value` 类型对齐）
 */
export async function fillElInputNumber(
  page: Page,
  selector: string,
  value: number,
): Promise<void> {
  await emitToVModelTarget(page, selector, value, 'fillElInputNumber');
}
