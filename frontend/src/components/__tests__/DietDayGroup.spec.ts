import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import DietDayGroup from '@/components/DietDayGroup.vue';
import type { DietListItem, MealType } from '@/types';

/** 构造一个最小 DietListItem；默认 LUNCH + 米饭 + 230 kcal。 */
function mkItem(overrides: Partial<DietListItem> = {}): DietListItem {
  return {
    id: 1,
    mealType: 'LUNCH',
    name: '米饭',
    kcal: 230,
    proteinG: 5,
    carbG: 50,
    fatG: 1,
    note: null,
    occurredAt: '2026-07-15T12:00:00+08:00',
    ...overrides,
  };
}

function mountGroup(items: DietListItem[], day = '2026-07-15') {
  return mount(DietDayGroup, {
    props: { day, items },
    global: { plugins: [ElementPlus] },
  });
}

describe('DietDayGroup / 4 餐分组渲染', () => {
  it('4 餐各一笔 → 4 个 meal-group + 中文 label', () => {
    const items = [
      mkItem({ id: 1, mealType: 'BREAKFAST', name: '燕麦', kcal: 380 }),
      mkItem({ id: 2, mealType: 'LUNCH', name: '米饭', kcal: 230 }),
      mkItem({ id: 3, mealType: 'DINNER', name: '鱼', kcal: 200 }),
      mkItem({ id: 4, mealType: 'SNACK', name: '苹果', kcal: 95 }),
    ];
    const w = mountGroup(items);
    // 4 个 meal-group（一天 4 餐全有）
    expect(w.findAll('[data-testid^="diet-meal-group-"]')).toHaveLength(4);
    // 中文 label 校验
    expect(w.find('[data-testid="diet-meal-header-2026-07-15-BREAKFAST"]').text()).toContain('早餐');
    expect(w.find('[data-testid="diet-meal-header-2026-07-15-LUNCH"]').text()).toContain('午餐');
    expect(w.find('[data-testid="diet-meal-header-2026-07-15-DINNER"]').text()).toContain('晚餐');
    expect(w.find('[data-testid="diet-meal-header-2026-07-15-SNACK"]').text()).toContain('加餐');
  });

  it('单日只给 LUNCH → 只渲染 1 个 meal-group + 1 个 item', () => {
    const w = mountGroup([mkItem({ id: 1, mealType: 'LUNCH' })]);
    expect(w.findAll('[data-testid^="diet-meal-group-"]')).toHaveLength(4); // 4 个餐别 header
    expect(w.findAll('[data-testid^="diet-item-"]')).toHaveLength(1);
    // 其他 3 餐无 item（itemCount 角标为 "0 笔"）
    expect(w.find('[data-testid="diet-meal-count-2026-07-15-BREAKFAST"]').text()).toBe('0 笔');
    expect(w.find('[data-testid="diet-meal-count-2026-07-15-LUNCH"]').text()).toBe('1 笔');
    expect(w.find('[data-testid="diet-meal-count-2026-07-15-DINNER"]').text()).toBe('0 笔');
    expect(w.find('[data-testid="diet-meal-count-2026-07-15-SNACK"]').text()).toBe('0 笔');
  });

  it('同餐多笔 → 保持入参顺序', () => {
    const items = [
      mkItem({ id: 10, mealType: 'LUNCH', name: '米饭' }),
      mkItem({ id: 5, mealType: 'LUNCH', name: '西兰花' }),
      mkItem({ id: 7, mealType: 'LUNCH', name: '鸡胸肉' }),
    ];
    const w = mountGroup(items);
    const rendered = w.findAll('[data-testid^="diet-item-"]');
    expect(rendered.map((n) => n.attributes('data-testid'))).toEqual([
      'diet-item-10',
      'diet-item-5',
      'diet-item-7',
    ]);
  });
});

describe('DietDayGroup / 每餐子合计 kcal', () => {
  it('LUNCH 2 笔：kcal=230+120 → 子合计 350.00', () => {
    const items = [
      mkItem({ id: 1, mealType: 'LUNCH', kcal: 230 }),
      mkItem({ id: 2, mealType: 'LUNCH', kcal: 120 }),
    ];
    const w = mountGroup(items);
    const totalEl = w.find('[data-testid="diet-meal-total-2026-07-15-LUNCH"]');
    expect(totalEl.text()).toBe('小计 350.00 kcal');
  });

  it('多餐各自子合计正确', () => {
    const items = [
      mkItem({ id: 1, mealType: 'BREAKFAST', kcal: 380 }),
      mkItem({ id: 2, mealType: 'LUNCH', kcal: 230 }),
      mkItem({ id: 3, mealType: 'LUNCH', kcal: 55 }),
      mkItem({ id: 4, mealType: 'DINNER', kcal: 720 }),
    ];
    const w = mountGroup(items);
    expect(w.find('[data-testid="diet-meal-total-2026-07-15-BREAKFAST"]').text()).toBe('小计 380.00 kcal');
    expect(w.find('[data-testid="diet-meal-total-2026-07-15-LUNCH"]').text()).toBe('小计 285.00 kcal');
    expect(w.find('[data-testid="diet-meal-total-2026-07-15-DINNER"]').text()).toBe('小计 720.00 kcal');
    // SNACK 0 笔 → 不渲染 total
    expect(w.find('[data-testid="diet-meal-total-2026-07-15-SNACK"]').exists()).toBe(false);
  });
});

describe('DietDayGroup / 折叠 / 展开', () => {
  it('默认展开（caret = ▾），点击 LUNCH 标题折叠（caret = ▸），再次点击展开', async () => {
    const w = mountGroup([mkItem({ id: 1, mealType: 'LUNCH' })]);
    // 初始展开
    expect(w.find('[data-testid="diet-meal-caret-2026-07-15-LUNCH"]').text()).toBe('▾');
    expect(w.findAll('[data-testid^="diet-item-"]')).toHaveLength(1);

    // 点击 LUNCH 标题折叠
    await w.find('[data-testid="diet-meal-header-2026-07-15-LUNCH"]').trigger('click');
    expect(w.find('[data-testid="diet-meal-caret-2026-07-15-LUNCH"]').text()).toBe('▸');
    // 折叠后 item 不在 DOM 里（v-if）
    expect(w.findAll('[data-testid^="diet-item-"]')).toHaveLength(0);

    // 再点一次展开
    await w.find('[data-testid="diet-meal-header-2026-07-15-LUNCH"]').trigger('click');
    expect(w.find('[data-testid="diet-meal-caret-2026-07-15-LUNCH"]').text()).toBe('▾');
    expect(w.findAll('[data-testid^="diet-item-"]')).toHaveLength(1);
  });

  it('单餐折叠不影响其他餐', async () => {
    const items = [
      mkItem({ id: 1, mealType: 'LUNCH' }),
      mkItem({ id: 2, mealType: 'DINNER' }),
    ];
    const w = mountGroup(items);
    await w.find('[data-testid="diet-meal-header-2026-07-15-LUNCH"]').trigger('click');

    expect(w.find('[data-testid="diet-meal-caret-2026-07-15-LUNCH"]').text()).toBe('▸');
    expect(w.find('[data-testid="diet-meal-caret-2026-07-15-DINNER"]').text()).toBe('▾');
    // DINNER 仍渲染 item
    expect(w.find('[data-testid="diet-item-2"]').exists()).toBe(true);
  });
});

describe('DietDayGroup / 事件转发', () => {
  it('点击「编辑」按钮 emit edit(id)', async () => {
    const w = mountGroup([mkItem({ id: 42, mealType: 'LUNCH' })]);
    await w.find('[data-testid="diet-edit-42"]').trigger('click');
    expect(w.emitted('edit')?.[0]).toEqual([42]);
  });

  it('点击「删除」按钮 emit delete(id)', async () => {
    const w = mountGroup([mkItem({ id: 7, mealType: 'LUNCH' })]);
    await w.find('[data-testid="diet-delete-7"]').trigger('click');
    expect(w.emitted('delete')?.[0]).toEqual([7]);
  });

  it('多 item 时每个 item 的 edit 携带自己的 id', async () => {
    const items = [
      mkItem({ id: 100, mealType: 'BREAKFAST' }),
      mkItem({ id: 200, mealType: 'LUNCH' }),
      mkItem({ id: 300, mealType: 'DINNER' }),
    ];
    const w = mountGroup(items);
    await w.find('[data-testid="diet-edit-200"]').trigger('click');
    expect(w.emitted('edit')?.[0]).toEqual([200]);
  });
});

describe('DietDayGroup / item 内容', () => {
  it('渲染 name + 时间 (HH:mm) + kcal', () => {
    const w = mountGroup([
      mkItem({ id: 1, mealType: 'LUNCH', name: '米饭', kcal: 230, occurredAt: '2026-07-15T12:30:00+08:00' }),
    ]);
    const item = w.find('[data-testid="diet-item-1"]');
    expect(item.find('.meal-item__time').text()).toBe('12:30');
    expect(item.find('.meal-item__name').text()).toBe('米饭');
    expect(item.find('.meal-item__kcal').text()).toBe('230.00 kcal');
  });

  it('不同 mealType 渲染到对应 group', () => {
    const meals: MealType[] = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'];
    const items = meals.map((m, i) => mkItem({ id: i + 1, mealType: m, name: `${m}-food` }));
    const w = mountGroup(items);
    for (let i = 0; i < meals.length; i++) {
      const mealType = meals[i]!;
      const id = i + 1;
      const item = w.find(`[data-testid="diet-item-${id}"]`);
      expect(item.exists()).toBe(true);
      // 父级 meal-group 的 data-testid 包含 mealType
      const group = item.element.closest('[data-testid^="diet-meal-group-"]');
      expect(group?.getAttribute('data-testid')).toBe(`diet-meal-group-2026-07-15-${mealType}`);
    }
  });
});