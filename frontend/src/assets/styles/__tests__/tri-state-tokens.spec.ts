import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import TriStateEmpty from '@/components/TriStateEmpty.vue';
import TriStateError from '@/components/TriStateError.vue';

/**
 * v1.2.6 #5：三态设计 token 统一 — 验证全局 CSS 变量文件内容。
 *
 * <p>为什么静态读文件？
 * <ul>
 *   <li>vitest jsdom 不会把动态 import 的 side-effect CSS 应用到 document.documentElement，
 *       所以 getComputedStyle(:root).getPropertyValue() 取不到值</li>
 *   <li>main.ts 的 import 顺序与测试 import 不同；静态读文件 + 字符串匹配
 *       是最稳定的方式锁定 token 定义</li>
 *   <li>未来 refactor 改 token 名 / 值，本测试立即失败</li>
 * </ul>
 *
 * <p>为什么不直接断言 runtime computed style？
 * <ul>
 *   <li>会强依赖 Element Plus 主题 CSS 注入顺序（必须在 tri-state.css 之前）</li>
 *   <li>会强依赖 vitest jsdom CSS parser（jsdom 不完整）</li>
 *   <li>改 token 值需要同时改测试与 main.ts，违背单源原则</li>
 * </ul>
 */
const cssPath = resolve(__dirname, '..', 'tri-state.css');
const css = readFileSync(cssPath, 'utf-8');

describe('tri-state tokens', () => {
  it('--tri-state-loading-bg 引用 Element Plus --el-fill-color-blank', () => {
    expect(css).toMatch(/--tri-state-loading-bg:\s*var\(--el-fill-color-blank\)/);
  });

  it('--tri-state-loading-radius = 8px（与其他模块卡圆角一致）', () => {
    expect(css).toMatch(/--tri-state-loading-radius:\s*8px/);
  });

  it('tri-state.css 提供 JSDoc 注释解释 token 范围（v1.2.6 #5 设计意图）', () => {
    expect(css).toContain('v1.2.6 #5');
    expect(css).toContain('三态设计 token 统一');
  });

  it('TriStateEmpty 容器渲染（与 token 解耦；锁定组件基线）', () => {
    const w = mount(TriStateEmpty, {
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="tristate-empty"]').exists()).toBe(true);
  });

  it('TriStateError 容器渲染（与 token 解耦；锁定组件基线）', () => {
    const w = mount(TriStateError, {
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="tristate-error"]').exists()).toBe(true);
  });
});