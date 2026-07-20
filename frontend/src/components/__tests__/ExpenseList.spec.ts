import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import ExpenseList from '@/components/ExpenseList.vue';
import type { ExpenseListItem } from '@/types';

/** 构造一个最小 ExpenseListItem 用于 list 渲染测试。 */
function mkItem(overrides: Partial<ExpenseListItem> = {}): ExpenseListItem {
  return {
    id: 1,
    amount: 10,
    category: 'MEAL',
    note: 'test',
    occurredAt: '2026-07-15T12:00:00',
    ...overrides,
  };
}

function mountList(items: ExpenseListItem[]) {
  return mount(ExpenseList, {
    props: { items },
    global: { plugins: [ElementPlus] },
  });
}

describe('ExpenseList / grouping', () => {
  it('按 occurredAt 日期分组（YYYY-MM-DD）', () => {
    const items = [
      mkItem({ id: 1, occurredAt: '2026-07-15T12:00:00' }),
      mkItem({ id: 2, occurredAt: '2026-07-15T18:00:00' }),
      mkItem({ id: 3, occurredAt: '2026-07-14T09:00:00' }),
    ];
    const w = mountList(items);
    expect(w.findAll('.day-group')).toHaveLength(2);
  });

  it('同日多条 → 1 个 day-group 内多条 ExpenseItem', () => {
    const items = [
      mkItem({ id: 1, occurredAt: '2026-07-15T08:00:00' }),
      mkItem({ id: 2, occurredAt: '2026-07-15T12:00:00' }),
      mkItem({ id: 3, occurredAt: '2026-07-15T18:00:00' }),
    ];
    const w = mountList(items);
    expect(w.findAll('.day-group')).toHaveLength(1);
    expect(w.findAll('.expense-item')).toHaveLength(3);
  });

  it('组内保持入参顺序（Map 插入序）', () => {
    const items = [
      mkItem({ id: 10, occurredAt: '2026-07-15T12:00:00' }),
      mkItem({ id: 5, occurredAt: '2026-07-15T18:00:00' }),
      mkItem({ id: 7, occurredAt: '2026-07-15T09:00:00' }),
    ];
    const w = mountList(items);
    const rendered = w.findAll('.expense-item');
    expect(rendered.map((n) => n.attributes('data-testid'))).toEqual([
      'expense-item-10',
      'expense-item-5',
      'expense-item-7',
    ]);
  });

  it('不同日期 → 多个 day-group，data-testid 包含日期', () => {
    const items = [
      mkItem({ id: 1, occurredAt: '2026-07-15T12:00:00' }),
      mkItem({ id: 2, occurredAt: '2026-07-14T09:00:00' }),
    ];
    const w = mountList(items);
    expect(w.find('[data-testid="day-group-2026-07-15"]').exists()).toBe(true);
    expect(w.find('[data-testid="day-group-2026-07-14"]').exists()).toBe(true);
  });
});

describe('ExpenseList / empty state', () => {
  it('items=[] → 渲染 empty 文案，无 day-group', () => {
    const w = mountList([]);
    expect(w.findAll('.day-group')).toHaveLength(0);
    const empty = w.find('[data-testid="expense-list-empty"]');
    expect(empty.exists()).toBe(true);
    expect(empty.text()).toBe('暂无消费记录');
  });
});

describe('ExpenseList / events', () => {
  it('点击「编辑」按钮 emit edit(id)', async () => {
    const w = mountList([mkItem({ id: 42, occurredAt: '2026-07-15T12:00:00' })]);
    await w.find('[data-testid="expense-item-edit"]').trigger('click');
    expect(w.emitted('edit')?.[0]).toEqual([42]);
  });

  it('点击「删除」按钮 emit delete(id)', async () => {
    const w = mountList([mkItem({ id: 7, occurredAt: '2026-07-15T12:00:00' })]);
    await w.find('[data-testid="expense-item-delete"]').trigger('click');
    expect(w.emitted('delete')?.[0]).toEqual([7]);
  });

  it('多 item 时每个 item 的 edit 携带自己的 id', async () => {
    const items = [
      mkItem({ id: 100, occurredAt: '2026-07-15T12:00:00' }),
      mkItem({ id: 200, occurredAt: '2026-07-14T09:00:00' }),
    ];
    const w = mountList(items);
    const editButtons = w.findAll('[data-testid="expense-item-edit"]');
    await editButtons[1]!.trigger('click');
    expect(w.emitted('edit')?.[0]).toEqual([200]);
  });
});

describe('ExpenseList / ExpenseListItem 内容', () => {
  it('渲染分类 label + 金额 + note + 时间', () => {
    const w = mountList([
      mkItem({
        id: 1,
        category: 'MEAL',
        amount: 35.5,
        note: '午饭',
        occurredAt: '2026-07-15T12:30:00',
      }),
    ]);
    const item = w.find('[data-testid="expense-item-1"]');
    expect(item.exists()).toBe(true);
    expect(item.find('[data-testid="expense-item-category"]').text()).toBe('餐饮');
    expect(item.find('[data-testid="expense-item-amount"]').text()).toBe('¥ 35.50');
    expect(item.find('[data-testid="expense-item-note"]').text()).toContain('「午饭」');
    expect(item.find('[data-testid="expense-item-time"]').text()).toBe('07-15 12:30');
  });

  it('note=null → 不渲染引号包裹', () => {
    const w = mountList([mkItem({ id: 1, note: null, occurredAt: '2026-07-15T12:00:00' })]);
    const noteEl = w.find('[data-testid="expense-item-note"]');
    expect(noteEl.text()).toBe('「」');
  });

  it('note 超长（>20 字符）→ 截断为 20 + "…"', () => {
    const longNote = '这是一段非常非常非常非常长的备注用于测试截断行为';
    expect(longNote.length).toBeGreaterThan(20);
    const w = mountList([
      mkItem({ id: 1, note: longNote, occurredAt: '2026-07-15T12:00:00' }),
    ]);
    const noteText = w.find('[data-testid="expense-item-note"]').text();
    // 「{前 20 字符}…」
    expect(noteText).toBe(`「${longNote.slice(0, 20)}…」`);
  });

  it('不同分类渲染对应中文 label', () => {
    const w = mountList([
      mkItem({ id: 1, category: 'SHOPPING', occurredAt: '2026-07-15T12:00:00' }),
      mkItem({ id: 2, category: 'TRANSPORT', occurredAt: '2026-07-15T13:00:00' }),
      mkItem({ id: 3, category: 'SUBSCRIPTION', occurredAt: '2026-07-15T14:00:00' }),
      mkItem({ id: 4, category: 'OTHER', occurredAt: '2026-07-15T15:00:00' }),
    ]);
    const labels = w.findAll('[data-testid="expense-item-category"]').map((n) => n.text());
    expect(labels).toEqual(['购物', '交通', '订阅', '其他']);
  });
});