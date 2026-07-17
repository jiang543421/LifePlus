import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import CalendarMonth from '@/components/CalendarMonth.vue';
import type { PlanListItem } from '@/types';

const mkEvent = (over: Partial<PlanListItem> & Pick<PlanListItem, 'id' | 'startTime' | 'endTime'>): PlanListItem => ({
  title: 't',
  allDay: 0,
  location: null,
  reminderMin: null,
  ...over,
});

function mountCal(events: PlanListItem[] = [], selectedDate: string | null = null) {
  return mount(CalendarMonth, {
    props: { year: 2026, month: 8, events, selectedDate },
    global: { plugins: [ElementPlus] },
  });
}

describe('CalendarMonth', () => {
  it('渲染 42 个日格与月份标题', () => {
    const w = mountCal();
    expect(w.findAll('[data-testid="day-cell"]')).toHaveLength(42);
    expect(w.find('[data-testid="cal-title"]').text()).toBe('2026 年 8 月');
  });

  it('渲染周一首列的 7 个星期标签', () => {
    const w = mountCal();
    expect(w.findAll('.weekday').map((n) => n.text())).toEqual(['一', '二', '三', '四', '五', '六', '日']);
  });

  it('点击上/下月/今天按钮各 emit 对应事件', async () => {
    const w = mountCal();
    await w.find('[data-testid="cal-prev"]').trigger('click');
    await w.find('[data-testid="cal-next"]').trigger('click');
    await w.find('[data-testid="cal-today"]').trigger('click');
    expect(w.emitted('prev')).toHaveLength(1);
    expect(w.emitted('next')).toHaveLength(1);
    expect(w.emitted('today')).toHaveLength(1);
  });

  it('点击某天 emit select 携带该日期', async () => {
    const w = mountCal();
    await w.find('[data-date="2026-08-15"]').trigger('click');
    expect(w.emitted('select')?.[0]).toEqual(['2026-08-15']);
  });

  it('有事件的日期渲染事件点', () => {
    const w = mountCal([mkEvent({ id: 1, startTime: '2026-08-10T09:00:00', endTime: '2026-08-10T10:00:00' })]);
    const cell = w.find('[data-date="2026-08-10"]');
    expect(cell.find('[data-testid="day-dots"]').exists()).toBe(true);
    expect(cell.findAll('.dot')).toHaveLength(1);
  });

  it('全天事件的日期带 has-allday 顶部色条与 allday 点', () => {
    const w = mountCal([mkEvent({ id: 2, allDay: 1, startTime: '2026-08-20T00:00:00', endTime: '2026-08-20T23:59:59' })]);
    const cell = w.find('[data-date="2026-08-20"]');
    expect(cell.classes()).toContain('has-allday');
    expect(cell.find('.dot.allday').exists()).toBe(true);
  });

  it('事件超过 3 个时最多 3 个点并显示 +N', () => {
    const events = [1, 2, 3, 4].map((id) =>
      mkEvent({ id, startTime: '2026-08-05T09:00:00', endTime: '2026-08-05T10:00:00' }),
    );
    const w = mountCal(events);
    const cell = w.find('[data-date="2026-08-05"]');
    expect(cell.findAll('.dot')).toHaveLength(3);
    expect(cell.find('.more').text()).toBe('+1');
  });

  it('selectedDate 对应格带 selected 类', () => {
    const w = mountCal([], '2026-08-15');
    expect(w.find('[data-date="2026-08-15"]').classes()).toContain('selected');
  });

  it('非当月补位格带 out 类', () => {
    const w = mountCal();
    expect(w.find('[data-date="2026-07-27"]').classes()).toContain('out');
  });
});
