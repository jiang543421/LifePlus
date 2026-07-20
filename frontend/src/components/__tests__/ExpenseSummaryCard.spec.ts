import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import ExpenseSummaryCard from '@/components/ExpenseSummaryCard.vue';
import type { ExpenseSummary } from '@/types';

function mkSummary(over: Partial<ExpenseSummary> = {}): ExpenseSummary {
  return {
    startMonth: '2026-07-01',
    endMonth: '2026-07-01',
    amountByCategory: {
      MEAL: 0,
      SHOPPING: 0,
      TRANSPORT: 0,
      SUBSCRIPTION: 0,
      OTHER: 0,
    },
    totalAmount: 0,
    ...over,
  };
}

function mountCard(props: { summary: ExpenseSummary | null; year: number; month: number }) {
  return mount(ExpenseSummaryCard, {
    props,
    global: { plugins: [ElementPlus] },
  });
}

describe('ExpenseSummaryCard / total amount', () => {
  it('summary=null → 总额 ¥ 0.00', () => {
    const w = mountCard({ summary: null, year: 2026, month: 7 });
    expect(w.find('[data-testid="summary-total"]').text()).toBe('¥ 0.00');
  });

  it('summary.totalAmount=350 → ¥ 350.00', () => {
    const w = mountCard({ summary: mkSummary({ totalAmount: 350 }), year: 2026, month: 7 });
    expect(w.find('[data-testid="summary-total"]').text()).toBe('¥ 350.00');
  });

  it('summary.totalAmount=0.5 → ¥ 0.50（保留两位）', () => {
    const w = mountCard({ summary: mkSummary({ totalAmount: 0.5 }), year: 2026, month: 7 });
    expect(w.find('[data-testid="summary-total"]').text()).toBe('¥ 0.50');
  });
});

describe('ExpenseSummaryCard / breakdown rows', () => {
  it('渲染固定 5 行（5 个分类标签）', () => {
    const w = mountCard({ summary: mkSummary(), year: 2026, month: 7 });
    expect(w.findAll('[data-testid^="summary-row-"]')).toHaveLength(5);
  });

  it('分类 label 按 CATEGORY_LABEL 渲染', () => {
    const w = mountCard({ summary: mkSummary(), year: 2026, month: 7 });
    expect(w.find('[data-testid="summary-row-MEAL"] .summary-row__label').text()).toBe('餐饮');
    expect(w.find('[data-testid="summary-row-SHOPPING"] .summary-row__label').text()).toBe(
      '购物',
    );
    expect(w.find('[data-testid="summary-row-TRANSPORT"] .summary-row__label').text()).toBe(
      '交通',
    );
    expect(w.find('[data-testid="summary-row-SUBSCRIPTION"] .summary-row__label').text()).toBe(
      '订阅',
    );
    expect(w.find('[data-testid="summary-row-OTHER"] .summary-row__label').text()).toBe('其他');
  });

  it('金额与占比按 amountByCategory 计算', () => {
    const w = mountCard({
      summary: mkSummary({
        totalAmount: 100,
        amountByCategory: {
          MEAL: 60,
          SHOPPING: 20,
          TRANSPORT: 20,
          SUBSCRIPTION: 0,
          OTHER: 0,
        },
      }),
      year: 2026,
      month: 7,
    });
    expect(w.find('[data-testid="summary-amount-MEAL"]').text()).toBe('¥ 60.00');
    expect(w.find('[data-testid="summary-percent-MEAL"]').text()).toBe('60.0%');
    expect(w.find('[data-testid="summary-amount-SHOPPING"]').text()).toBe('¥ 20.00');
    expect(w.find('[data-testid="summary-percent-SHOPPING"]').text()).toBe('20.0%');
  });

  it('totalAmount=0 → 所有 percent 为 0.0%（避免除零）', () => {
    const w = mountCard({ summary: mkSummary(), year: 2026, month: 7 });
    const percents = w.findAll('[data-testid^="summary-percent-"]').map((n) => n.text());
    expect(percents).toEqual(['0.0%', '0.0%', '0.0%', '0.0%', '0.0%']);
  });

  it('progress bar fill width = percent%', () => {
    const w = mountCard({
      summary: mkSummary({
        totalAmount: 200,
        amountByCategory: {
          MEAL: 100,
          SHOPPING: 0,
          TRANSPORT: 0,
          SUBSCRIPTION: 0,
          OTHER: 0,
        },
      }),
      year: 2026,
      month: 7,
    });
    const fillEl = w.find('[data-testid="summary-bar-MEAL"]').element as HTMLElement;
    expect(fillEl.style.width).toBe('50%');
    const fillEl2 = w.find('[data-testid="summary-bar-SHOPPING"]').element as HTMLElement;
    expect(fillEl2.style.width).toBe('0%');
  });
});

describe('ExpenseSummaryCard / month nav', () => {
  it('渲染 monthLabel 为 "YYYY-MM" 零填充', () => {
    const w = mountCard({ summary: mkSummary(), year: 2026, month: 7 });
    expect(w.find('[data-testid="summary-month"]').text()).toBe('2026-07');
  });

  it('month=1 → prev emit (year-1, 12)', async () => {
    const w = mountCard({ summary: mkSummary(), year: 2026, month: 1 });
    await w.find('[data-testid="summary-prev-month"]').trigger('click');
    expect(w.emitted('change-month')?.[0]).toEqual([2025, 12]);
  });

  it('month=12 → next emit (year+1, 1)', async () => {
    const w = mountCard({ summary: mkSummary(), year: 2026, month: 12 });
    await w.find('[data-testid="summary-next-month"]').trigger('click');
    expect(w.emitted('change-month')?.[0]).toEqual([2027, 1]);
  });

  it('普通月份：prev/next 在年内回卷', async () => {
    const w = mountCard({ summary: mkSummary(), year: 2026, month: 7 });
    await w.find('[data-testid="summary-prev-month"]').trigger('click');
    expect(w.emitted('change-month')?.[0]).toEqual([2026, 6]);
    await w.find('[data-testid="summary-next-month"]').trigger('click');
    expect(w.emitted('change-month')?.[1]).toEqual([2026, 8]);
  });
});