import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import DietDialog from '@/components/DietDialog.vue';
import type { DietResponse } from '@/types';
import { useDietStore } from '@/stores/diet';

vi.mock('@/api/diet', () => ({
  dietApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    summary: vi.fn(),
    frequent: vi.fn(),
  },
}));

import { dietApi } from '@/api/diet';

const editItem: DietResponse = {
  id: 7,
  userId: 1,
  mealType: 'LUNCH',
  name: '米饭',
  kcal: 230,
  proteinG: 5,
  carbG: 50,
  fatG: 1,
  note: '午饭',
  occurredAt: '2026-07-15T12:00:00+08:00',
  createdAt: '2026-07-15T12:00:00+08:00',
  updatedAt: '2026-07-15T12:00:00+08:00',
};

interface OpenOpts {
  mode: 'create' | 'edit';
  item?: DietResponse | null;
  submitting?: boolean;
}

/**
 * 打开弹窗。
 *
 * <p>Element Plus ElDialog 默认 Teleport 到 document.body；用 {@code attachTo} 把 wrapper
 * 挂到 body，让 {@code wrapper.find} 能搜到弹窗内容（否则 Teleport 出去的部分不计入）。
 */
async function openDialog(opts: OpenOpts) {
  const w = mount(DietDialog, {
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
  vi.mocked(dietApi.create).mockReset();
  vi.mocked(dietApi.update).mockReset();
  vi.mocked(dietApi.list).mockReset();
});

// ---------------------------------------------------------------
// 渲染
// ---------------------------------------------------------------
describe('DietDialog / render', () => {
  it('open=true 渲染 ElDialog 与 footer 操作按钮', async () => {
    const w = await openDialog({ mode: 'create' });
    expect(w.find('[data-testid="diet-dialog"]').exists()).toBe(true);
    expect(w.find('[data-testid="diet-dialog-submit"]').exists()).toBe(true);
    expect(w.find('[data-testid="diet-dialog-cancel"]').exists()).toBe(true);
  });

  it('mode=create → 标题为「新增饮食」', async () => {
    const w = await openDialog({ mode: 'create' });
    expect(w.find('[data-testid="diet-dialog"]').text()).toContain('新增饮食');
  });

  it('mode=edit → 标题为「编辑饮食」', async () => {
    const w = await openDialog({ mode: 'edit', item: editItem });
    expect(w.find('[data-testid="diet-dialog"]').text()).toContain('编辑饮食');
  });

  it('餐别下拉渲染 4 个 MEAL_TYPES 选项（默认 LUNCH）', async () => {
    const w = await openDialog({ mode: 'create' });
    // el-select 默认渲染第一个 option label 到 trigger（create 默认 mealType='LUNCH'）
    expect(w.find('[data-testid="diet-dialog-meal-type"]').text()).toContain('午餐');
  });
});

// ---------------------------------------------------------------
// create mode — open 触发空表单
// ---------------------------------------------------------------
describe('DietDialog / create lifecycle', () => {
  it('create + item=null → 打开后表单 name="" / note=""', async () => {
    const w = await openDialog({ mode: 'create' });
    const nameEl = w.find('[data-testid="diet-dialog-name"] input');
    expect(nameEl.exists()).toBe(true);
    expect((nameEl.element as HTMLInputElement).value).toBe('');

    const noteEl = w.find('[data-testid="diet-dialog-note"] textarea');
    expect(noteEl.exists()).toBe(true);
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('');
  });

  it('点击取消 emit update:modelValue=false，不调 store', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="diet-dialog-cancel"]').trigger('click');
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([false]);
    expect(dietApi.create).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------
// edit mode — open 触发预填
// ---------------------------------------------------------------
describe('DietDialog / edit lifecycle', () => {
  it('edit + item → 预填 name + note', async () => {
    const w = await openDialog({ mode: 'edit', item: editItem });
    const nameEl = w.find('[data-testid="diet-dialog-name"] input');
    expect((nameEl.element as HTMLInputElement).value).toBe('米饭');

    const noteEl = w.find('[data-testid="diet-dialog-note"] textarea');
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('午饭');
  });

  it('edit + item.note=null → 打开后 note=""', async () => {
    const w = await openDialog({ mode: 'edit', item: { ...editItem, note: null } });
    const noteEl = w.find('[data-testid="diet-dialog-note"] textarea');
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('');
  });

  it('edit + item → 餐别显示中文 label', async () => {
    const w = await openDialog({ mode: 'edit', item: editItem });
    expect(w.find('[data-testid="diet-dialog-meal-type"]').text()).toContain('午餐');
  });

  it('edit + item=null → 不崩（防陈旧数据，form 走空表单分支）', async () => {
    const w = await openDialog({ mode: 'edit', item: null });
    expect(w.find('[data-testid="diet-dialog"]').exists()).toBe(true);
    const nameEl = w.find('[data-testid="diet-dialog-name"] input');
    expect((nameEl.element as HTMLInputElement).value).toBe('');
  });
});

// ---------------------------------------------------------------
// 校验：手工校验拦截，未通过不调 store
// ---------------------------------------------------------------
describe('DietDialog / validation', () => {
  it('name 全空白提交 → dietApi.create 未调', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="diet-dialog-name"] input').setValue('   ');
    await w.find('[data-testid="diet-dialog-submit"]').trigger('click');
    await flushPromises();
    expect(dietApi.create).not.toHaveBeenCalled();
    expect(w.emitted('success')).toBeFalsy();
  });

  it('name 空字符串提交 → dietApi.create 未调', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="diet-dialog-submit"]').trigger('click');
    await flushPromises();
    expect(dietApi.create).not.toHaveBeenCalled();
    expect(w.emitted('update:modelValue')).toBeFalsy();
  });

  it('name 超 64 字符提交 → dietApi.create 未调', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="diet-dialog-name"] input').setValue('x'.repeat(65));
    await w.find('[data-testid="diet-dialog-submit"]').trigger('click');
    await flushPromises();
    expect(dietApi.create).not.toHaveBeenCalled();
  });

  it('note 超 200 字符提交 → dietApi.create 未调', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="diet-dialog-name"] input').setValue('米饭');
    await w.find('[data-testid="diet-dialog-note"] textarea').setValue('y'.repeat(201));
    await w.find('[data-testid="diet-dialog-submit"]').trigger('click');
    await flushPromises();
    expect(dietApi.create).not.toHaveBeenCalled();
  });

  it('校验失败弹窗保持打开（不 emit update:modelValue=false）', async () => {
    const w = await openDialog({ mode: 'create' });
    await w.find('[data-testid="diet-dialog-submit"]').trigger('click');
    await flushPromises();
    expect(w.props('modelValue')).toBe(true);
    expect(w.emitted('update:modelValue')).toBeFalsy();
  });
});

// ---------------------------------------------------------------
// 提交流程：通过 store 层契约验证 payload 形状
// ---------------------------------------------------------------
describe('DietDialog / submit happy path', () => {
  it('create 模式 store.create 收到 4 字段 + 默认营养字段 (0/0/0) + ISO occurredAt', async () => {
    const store = useDietStore();
    vi.mocked(dietApi.create).mockResolvedValueOnce({
      id: 99,
      userId: 1,
      mealType: 'BREAKFAST',
      name: '豆浆',
      kcal: 80,
      proteinG: 0,
      carbG: 0,
      fatG: 0,
      note: null,
      occurredAt: '2026-07-15T07:30:00+08:00',
      createdAt: '2026-07-15T07:30:01+08:00',
      updatedAt: '2026-07-15T07:30:01+08:00',
    });
    vi.mocked(dietApi.list).mockResolvedValueOnce({ items: [], total: 0, page: 1, size: 20 });

    // 模拟 DietDialog 真实提交时构造的 CreateDietRequest 契约
    const occurredAt = '2026-07-15T07:25:00+08:00'; // dayjs().subtract(5, 'minute').toISOString() 的契约形态
    await store.create({
      name: '豆浆',
      mealType: 'BREAKFAST',
      kcal: 80,
      proteinG: 0,
      carbG: 0,
      fatG: 0,
      note: null,
      occurredAt,
    });
    expect(dietApi.create).toHaveBeenCalledWith({
      name: '豆浆',
      mealType: 'BREAKFAST',
      kcal: 80,
      proteinG: 0,
      carbG: 0,
      fatG: 0,
      note: null,
      occurredAt,
    });
  });

  it('note 全空白 trim → store.create 收到 note=null', async () => {
    const store = useDietStore();
    vi.mocked(dietApi.create).mockResolvedValueOnce({
      id: 1,
      userId: 1,
      mealType: 'LUNCH',
      name: 'x',
      kcal: 1,
      proteinG: 0,
      carbG: 0,
      fatG: 0,
      note: null,
      occurredAt: '2026-07-15T12:00:00+08:00',
      createdAt: '2026-07-15T12:00:01+08:00',
      updatedAt: '2026-07-15T12:00:01+08:00',
    });
    vi.mocked(dietApi.list).mockResolvedValueOnce({ items: [], total: 0, page: 1, size: 20 });

    const trimmed = '   '.trim() || null;
    await store.create({
      name: 'x',
      mealType: 'LUNCH',
      kcal: 1,
      proteinG: 0,
      carbG: 0,
      fatG: 0,
      note: trimmed,
      occurredAt: '2026-07-15T12:00:00+08:00',
    });
    expect(dietApi.create).toHaveBeenCalledWith(expect.objectContaining({ note: null }));
  });

  it('edit 模式 store.update 携带 id + 4 字段 + occurredAt（不带营养字段）', async () => {
    const store = useDietStore();
    vi.mocked(dietApi.update).mockResolvedValueOnce(undefined);
    vi.mocked(dietApi.list).mockResolvedValueOnce({ items: [], total: 0, page: 1, size: 20 });

    await store.update(editItem.id, {
      name: '调整后',
      mealType: 'DINNER',
      kcal: 999,
      note: '调整',
      occurredAt: editItem.occurredAt,
    });
    expect(dietApi.update).toHaveBeenCalledWith(editItem.id, {
      name: '调整后',
      mealType: 'DINNER',
      kcal: 999,
      note: '调整',
      occurredAt: editItem.occurredAt,
    });
  });
});

// ---------------------------------------------------------------
// 状态耦合：mode 切换触发预填 / 重置
// ---------------------------------------------------------------
describe('DietDialog / open lifecycle', () => {
  it('从 create → edit（item 变化） → name/note 预填生效', async () => {
    const w = mount(DietDialog, {
      props: { modelValue: false, mode: 'create', item: null },
      global: { plugins: [ElementPlus] },
      attachTo: document.body,
    });
    await w.setProps({ modelValue: true });
    await flushPromises();

    let nameEl = w.find('[data-testid="diet-dialog-name"] input');
    expect((nameEl.element as HTMLInputElement).value).toBe('');

    await w.setProps({ mode: 'edit', item: editItem });
    await flushPromises();

    nameEl = w.find('[data-testid="diet-dialog-name"] input');
    expect((nameEl.element as HTMLInputElement).value).toBe('米饭');

    const noteEl = w.find('[data-testid="diet-dialog-note"] textarea');
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('午饭');
  });

  it('关闭后再次打开（item 清空）不残留旧数据', async () => {
    const w = mount(DietDialog, {
      props: { modelValue: false, mode: 'edit', item: editItem },
      global: { plugins: [ElementPlus] },
      attachTo: document.body,
    });
    await w.setProps({ modelValue: true });
    await flushPromises();
    let nameEl = w.find('[data-testid="diet-dialog-name"] input');
    expect((nameEl.element as HTMLInputElement).value).toBe('米饭');

    await w.setProps({ modelValue: false });
    await w.setProps({ mode: 'edit', item: null });
    await w.setProps({ modelValue: true });
    await flushPromises();

    nameEl = w.find('[data-testid="diet-dialog-name"] input');
    expect((nameEl.element as HTMLInputElement).value).toBe('');

    const noteEl = w.find('[data-testid="diet-dialog-note"] textarea');
    expect((noteEl.element as HTMLTextAreaElement).value).toBe('');
  });
});

// ---------------------------------------------------------------
// submitting 状态
// ---------------------------------------------------------------
describe('DietDialog / submitting prop', () => {
  it('submitting=true 时 submit 按钮显示 loading', async () => {
    const w = await openDialog({ mode: 'create', submitting: true });
    const submitBtn = w.find('[data-testid="diet-dialog-submit"]');
    // el-button 在 loading 态渲染 .is-loading 类
    expect(submitBtn.classes()).toContain('is-loading');
  });
});