import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import DietNutritionCard from '@/components/DietNutritionCard.vue';
import type { DietSummary } from '@/types';

function mkSummary(over: Partial<DietSummary> = {}): DietSummary {
  return {
    kcal: 0,
    proteinG: 0,
    carbG: 0,
    fatG: 0,
    kcalDeltaYesterday: null,
    kcalDeltaLastWeek: null,
    ...over,
  };
}

function mountCard(props: { summary: DietSummary | null }) {
  return mount(DietNutritionCard, {
    props,
    global: { plugins: [ElementPlus] },
  });
}

describe('DietNutritionCard / 顶部大字号热量', () => {
  it('summary=null → "0.00 / 2000 kcal"', () => {
    const w = mountCard({ summary: null });
    expect(w.find('[data-testid="diet-nutrition-total"]').text()).toBe('0.00 / 2000 kcal');
  });

  it('summary.kcal=1680 → "1680.00 / 2000 kcal"', () => {
    const w = mountCard({ summary: mkSummary({ kcal: 1680 }) });
    expect(w.find('[data-testid="diet-nutrition-total"]').text()).toBe('1680.00 / 2000 kcal');
  });
});

describe('DietNutritionCard / 4 项营养行渲染', () => {
  it('渲染固定 4 行（kcal / proteinG / carbG / fatG）', () => {
    const w = mountCard({ summary: mkSummary() });
    const rows = w.findAll('[data-testid^="diet-nutrition-row-"]');
    expect(rows).toHaveLength(4);
    // 顺序：kcal → proteinG → carbG → fatG（与 MEAL_TYPES 枚举序对齐）
    expect(rows.map((r) => r.attributes('data-testid'))).toEqual([
      'diet-nutrition-row-kcal',
      'diet-nutrition-row-proteinG',
      'diet-nutrition-row-carbG',
      'diet-nutrition-row-fatG',
    ]);
  });

  it('中文 label 与推荐摄入常量渲染正确', () => {
    const w = mountCard({ summary: mkSummary() });
    expect(w.find('[data-testid="diet-nutrition-row-kcal"]').text()).toContain('热量');
    expect(w.find('[data-testid="diet-nutrition-row-kcal"]').text()).toContain('2000 kcal');

    expect(w.find('[data-testid="diet-nutrition-row-proteinG"]').text()).toContain('蛋白质');
    expect(w.find('[data-testid="diet-nutrition-row-proteinG"]').text()).toContain('60 g');

    expect(w.find('[data-testid="diet-nutrition-row-carbG"]').text()).toContain('碳水');
    expect(w.find('[data-testid="diet-nutrition-row-carbG"]').text()).toContain('300 g');

    expect(w.find('[data-testid="diet-nutrition-row-fatG"]').text()).toContain('脂肪');
    expect(w.find('[data-testid="diet-nutrition-row-fatG"]').text()).toContain('65 g');
  });

  it('value 数字按 summary 渲染（formatAmount 保留两位）', () => {
    const w = mountCard({
      summary: mkSummary({ kcal: 1680, proteinG: 55, carbG: 220, fatG: 50 }),
    });
    expect(w.find('[data-testid="diet-nutrition-value-kcal"]').text()).toBe('1680.00 / 2000 kcal');
    expect(w.find('[data-testid="diet-nutrition-value-proteinG"]').text()).toBe('55.00 / 60 g');
    expect(w.find('[data-testid="diet-nutrition-value-carbG"]').text()).toBe('220.00 / 300 g');
    expect(w.find('[data-testid="diet-nutrition-value-fatG"]').text()).toBe('50.00 / 65 g');
  });
});

describe('DietNutritionCard / 进度条宽度', () => {
  it('kcal=1000/2000 → bar width=50%', () => {
    const w = mountCard({ summary: mkSummary({ kcal: 1000 }) });
    const fill = w.find('[data-testid="diet-nutrition-bar-kcal"]').element as HTMLElement;
    expect(fill.style.width).toBe('50%');
  });

  it('value 超过 recommended → bar width 截断到 100%', () => {
    const w = mountCard({ summary: mkSummary({ kcal: 5000 }) }); // 5000/2000 = 250%
    const fill = w.find('[data-testid="diet-nutrition-bar-kcal"]').element as HTMLElement;
    expect(fill.style.width).toBe('100%');
  });

  it('value=0 → bar width=0%', () => {
    const w = mountCard({ summary: mkSummary() });
    for (const key of ['kcal', 'proteinG', 'carbG', 'fatG']) {
      const fill = w.find(`[data-testid="diet-nutrition-bar-${key}"]`).element as HTMLElement;
      expect(fill.style.width).toBe('0%');
    }
  });
});

describe('DietNutritionCard / delta（昨日 / 上周同日）', () => {
  it('delta=null → 显示「无对比数据」', () => {
    const w = mountCard({
      summary: mkSummary({ kcal: 1680, kcalDeltaYesterday: null, kcalDeltaLastWeek: null }),
    });
    expect(w.find('[data-testid="diet-nutrition-delta-yesterday-value"]').text()).toBe('无对比数据');
    expect(w.find('[data-testid="diet-nutrition-delta-last-week-value"]').text()).toBe('无对比数据');
  });

  it('summary=null → 两个 delta 都显示「无对比数据」', () => {
    const w = mountCard({ summary: null });
    expect(w.find('[data-testid="diet-nutrition-delta-yesterday-value"]').text()).toBe('无对比数据');
    expect(w.find('[data-testid="diet-nutrition-delta-last-week-value"]').text()).toBe('无对比数据');
  });

  it('deltaYesterday=120 → 显示 "+120.00"', () => {
    const w = mountCard({ summary: mkSummary({ kcalDeltaYesterday: 120 }) });
    expect(w.find('[data-testid="diet-nutrition-delta-yesterday-value"]').text()).toBe('+120.00');
  });

  it('deltaYesterday=-80 → 显示 "-80.00"（无 + 前缀）', () => {
    const w = mountCard({ summary: mkSummary({ kcalDeltaYesterday: -80 }) });
    expect(w.find('[data-testid="diet-nutrition-delta-yesterday-value"]').text()).toBe('-80.00');
  });

  it('deltaLastWeek=-250 → 独立显示 "-250.00"', () => {
    const w = mountCard({ summary: mkSummary({ kcalDeltaLastWeek: -250 }) });
    expect(w.find('[data-testid="diet-nutrition-delta-last-week-value"]').text()).toBe('-250.00');
  });

  it('昨日和上周同日独立（互不影响）', () => {
    const w = mountCard({
      summary: mkSummary({ kcalDeltaYesterday: 100, kcalDeltaLastWeek: null }),
    });
    expect(w.find('[data-testid="diet-nutrition-delta-yesterday-value"]').text()).toBe('+100.00');
    expect(w.find('[data-testid="diet-nutrition-delta-last-week-value"]').text()).toBe('无对比数据');
  });
});