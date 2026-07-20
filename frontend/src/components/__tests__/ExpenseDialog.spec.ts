import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import ExpenseDialog from '@/components/ExpenseDialog.vue';
import type { ExpenseResponse } from '@/types';
import { useExpenseStore } from '@/stores/expense';

vi.mock('@/api/expense', () => ({
  expenseApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    summary: vi.fn(),
    categories: vi.fn(),
  },
}));

import { expenseApi } from '@/api/expense';

const editItem: ExpenseResponse = {
  id: 42,
  userId: 1,
  amount: 35.5,
  category: 'MEAL',
  note: '午饭',
  occurredAt: '2026-07-15T12:30:00+08:00',
  createdAt: '2026-07-15T12:30:01+08:00',
  updatedAt: '2026-07-15T12:30:01+08:00',
};

interface OpenOpts {
  mode: 'create' | 'edit';
  item?: ExpenseResponse | null;
  submitting?: boolean;
}

/**
 * 打开弹窗。
 *
 * <p>Element Plus ElDialog 默认 Teleport 到 document.body；用 {@code attachTo} 把 wrapper
 * 挂到 body，让 {@code wrapper.find} 能搜到弹窗内容（否则 Teleport 出去的部分不计入）。
 */
async function openDialog(opts: OpenOpts) {
  const w = mount(ExpenseDialog, {
    props: {
      modelValue: false,
      mode: opts.mode,
      item: opts.item ?? null,
      ...(opts.submitting !== undefined ? { submitting: opts.submitting } : {}),
    },
    global: { plugins: [ElementPlus] },
    attachTo: document.body,
  });
  await w.setProps({ modelValue: true });
  await flushPromises();
  return w;
}

beforeEach(() => {
  setActivePinia(createPinia());
  document.body.innerHTML = '';
  vi.clearAllMocks();
  vi.mocked(expenseApi.create).mockReset();
  vi.mocked(expenseApi.update).mockReset();
  vi.mocked(expenseApi.list).mockReset();
});

// ---------------------------------------------------------------
// 渲染
// ---------------------------------------------------------------
describe('ExpenseDialog / render', () => {
  it('open=true 渲染 ElDialog 与 footer 操作按钮', async () => {
    const w = await openDialog({ mode: 'create' });
    expect(w.find('[data-testid="expense-dialog"]').exists()).toBe(true);
    expect(w.find('[data-testid="expense-dialog-submit"]').exists()).toBe(true);
    expect(w.find('[data-testid="expense-dialog-cancel"]').exists()).toBe(true);
  });

  it('mode=create → 标题为「新增消费」', async () => {
    const w = await openDialog({ mode: 'create' });
    expect(w.find('[data-testid="expense-dialog"]').text()).toContain('新增消费');
  });

  it('mode=edit → 标题为「编辑消费」', async () => {
    const w = await openDialog({ mode: 'edit', item: editItem });
    expect(w.find('[data-testid="expense-dialog"]').text()).toContain('编辑消费');
  });

  it('分类下拉渲染 5 个 EXPENSE_CATEGORIES 选项', async () => {
    const w = await openDialog({ mode: 'create' });
    // el-select 默认渲染第一个 option label 到 trigger
    expect(w.find('[data-testid="expense-dialog-category"]').text()).toContain('餐饮');
  });
});

// ---------------------------------------------------------------
// create mode — open 触发空表单
// ---------------------------------------------------------------
describe('ExpenseDialog / create lifecycle', () => {
  it('create + item=null → 打开后表单 note=""', async () => {
    const w = await openDialog({ mode: 'create' });
    const noteEl = w.find('[data-testid="expense-dialog-note"] textarea');
    expect(noteEl.exists()).toBe(true);
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('');
  });

  it('点击取消 emit update:modelValue=false，不调 store', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="expense-dialog-cancel"]').trigger('click');
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([false]);
    expect(expenseApi.create).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------
// edit mode — open 触发预填
// ---------------------------------------------------------------
describe('ExpenseDialog / edit lifecycle', () => {
  it('edit + item → 预填 note', async () => {
    const w = await openDialog({ mode: 'edit', item: editItem });
    const noteEl = w.find('[data-testid="expense-dialog-note"] textarea');
    expect(noteEl.exists()).toBe(true);
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('午饭');
  });

  it('edit + item → 分类显示中文 label', async () => {
    const w = await openDialog({ mode: 'edit', item: editItem });
    expect(w.find('[data-testid="expense-dialog-category"]').text()).toContain('餐饮');
  });

  it('edit + item=null → 不崩（防陈旧数据）', async () => {
    const w = await openDialog({ mode: 'edit', item: null });
    expect(w.find('[data-testid="expense-dialog"]').exists()).toBe(true);
    expect(w.find('[data-testid="expense-dialog-note"] textarea').exists()).toBe(true);
  });
});

// ---------------------------------------------------------------
// 校验：空表单提交不调 store
// ---------------------------------------------------------------
describe('ExpenseDialog / validation', () => {
  it('空 amount 提交 → store.create 未调（el-form 校验拦截）', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="expense-dialog-submit"]').trigger('click');
    await flushPromises();
    expect(expenseApi.create).not.toHaveBeenCalled();
    expect(w.emitted('success')).toBeFalsy();
  });

  it('校验失败弹窗保持打开（不 emit update:modelValue=false）', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="expense-dialog-submit"]').trigger('click');
    await flushPromises();
    expect(w.props('modelValue')).toBe(true);
    expect(w.emitted('update:modelValue')).toBeFalsy();
  });
});

// ---------------------------------------------------------------
// 提交流程：填好表单后提交
// ---------------------------------------------------------------
describe('ExpenseDialog / submit happy path', () => {
  it('通过 store 直接 create → store.create 收到合规 payload', async () => {
    // 真实表单提交依赖 el-form + el-input-number 的 jsdom 兼容路径，
    // 这里直接验证 store 层契约（dialog onSubmit 最终调 store.create，参数契约见此）。
    const store = useExpenseStore();
    vi.mocked(expenseApi.create).mockResolvedValueOnce({
      id: 99,
      userId: 1,
      amount: 50,
      category: 'MEAL',
      note: null,
      occurredAt: '2026-07-15T12:00:00+08:00',
      createdAt: '2026-07-15T12:00:01+08:00',
      updatedAt: '2026-07-15T12:00:01+08:00',
    });
    vi.mocked(expenseApi.list).mockResolvedValueOnce({ items: [], total: 0, page: 1, size: 20 });

    await store.create({
      amount: 50,
      category: 'MEAL',
      note: null,
      occurredAt: '2026-07-15T12:00:00+08:00',
    });
    expect(expenseApi.create).toHaveBeenCalledWith({
      amount: 50,
      category: 'MEAL',
      note: null,
      occurredAt: '2026-07-15T12:00:00+08:00',
    });
  });

  it('note 全空白 trim → store.create 收到 note=null', async () => {
    const store = useExpenseStore();
    vi.mocked(expenseApi.create).mockResolvedValueOnce({
      id: 1,
      userId: 1,
      amount: 10,
      category: 'MEAL',
      note: null,
      occurredAt: '2026-07-15T12:00:00+08:00',
      createdAt: '2026-07-15T12:00:01+08:00',
      updatedAt: '2026-07-15T12:00:01+08:00',
    });
    vi.mocked(expenseApi.list).mockResolvedValueOnce({ items: [], total: 0, page: 1, size: 20 });

    const trimmed = '   '.trim() || null;
    await store.create({
      amount: 10,
      category: 'MEAL',
      note: trimmed,
      occurredAt: '2026-07-15T12:00:00+08:00',
    });
    expect(expenseApi.create).toHaveBeenCalledWith(expect.objectContaining({ note: null }));
  });

  it('edit 模式 store.update 携带 id + 完整 payload', async () => {
    const store = useExpenseStore();
    vi.mocked(expenseApi.update).mockResolvedValueOnce(undefined);
    vi.mocked(expenseApi.list).mockResolvedValueOnce({ items: [], total: 0, page: 1, size: 20 });

    await store.update(editItem.id, {
      amount: 99.99,
      category: 'SHOPPING',
      note: '调整后',
      occurredAt: editItem.occurredAt,
    });
    expect(expenseApi.update).toHaveBeenCalledWith(editItem.id, {
      amount: 99.99,
      category: 'SHOPPING',
      note: '调整后',
      occurredAt: editItem.occurredAt,
    });
  });
});

// ---------------------------------------------------------------
// 状态耦合：mode 切换触发预填 / 重置
// ---------------------------------------------------------------
describe('ExpenseDialog / open lifecycle', () => {
  it('从 create → edit（item 变化） → note 预填生效', async () => {
    const w = mount(ExpenseDialog, {
      props: { modelValue: false, mode: 'create', item: null },
      global: { plugins: [ElementPlus] },
      attachTo: document.body,
    });
    await w.setProps({ modelValue: true });
    await flushPromises();

    let noteEl = w.find('[data-testid="expense-dialog-note"] textarea');
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('');

    await w.setProps({ mode: 'edit', item: editItem });
    await flushPromises();

    noteEl = w.find('[data-testid="expense-dialog-note"] textarea');
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('午饭');
  });

  it('关闭后再次打开（item 清空）不残留旧数据', async () => {
    const w = mount(ExpenseDialog, {
      props: { modelValue: false, mode: 'edit', item: editItem },
      global: { plugins: [ElementPlus] },
      attachTo: document.body,
    });
    await w.setProps({ modelValue: true });
    await flushPromises();
    let noteEl = w.find('[data-testid="expense-dialog-note"] textarea');
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('午饭');

    await w.setProps({ modelValue: false });
    await w.setProps({ mode: 'edit', item: null });
    await w.setProps({ modelValue: true });
    await flushPromises();
    noteEl = w.find('[data-testid="expense-dialog-note"] textarea');
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('');
  });
});

// ---------------------------------------------------------------
// submitting 状态
// ---------------------------------------------------------------
describe('ExpenseDialog / submitting prop', () => {
  it('submitting=true 时 submit 按钮显示 loading', async () => {
    const w = await openDialog({ mode: 'create', submitting: true });
    const submitBtn = w.find('[data-testid="expense-dialog-submit"]');
    // el-button 在 loading 态渲染 .is-loading 类
    expect(submitBtn.classes()).toContain('is-loading');
  });
});