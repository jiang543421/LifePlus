import { describe, it, expect } from 'vitest';
import {
  buildMonthGrid,
  monthRange,
  spanDates,
  groupEventsByDate,
  shiftMonth,
  WEEKDAY_LABELS,
  MONTH_QUERY_SIZE,
} from '@/utils/calendar';
import type { PlanListItem } from '@/types';

const mkEvent = (over: Partial<PlanListItem> & Pick<PlanListItem, 'id' | 'startTime' | 'endTime'>): PlanListItem => ({
  title: 't',
  allDay: 0,
  location: null,
  reminderMin: null,
  ...over,
});

describe('calendar / buildMonthGrid', () => {
  // 2026-08-01 是周六；周一首列，前导偏移 5 天 → 网格起点 2026-07-27（周一）
  it('固定 42 格，周一首列，正确补位上/下月', () => {
    const cells = buildMonthGrid(2026, 8);
    expect(cells).toHaveLength(42);
    expect(cells[0].date).toBe('2026-07-27');
    expect(cells[0].inMonth).toBe(false);
    // 第 6 格（index 5）应为 8-01
    expect(cells[5].date).toBe('2026-08-01');
    expect(cells[5].day).toBe(1);
    expect(cells[5].inMonth).toBe(true);
  });

  it('当月天数正确（八月 31 天）', () => {
    const cells = buildMonthGrid(2026, 8);
    expect(cells.filter((c) => c.inMonth)).toHaveLength(31);
  });

  it('todayStr 命中时标记 isToday，仅一格', () => {
    const cells = buildMonthGrid(2026, 8, '2026-08-15');
    const todays = cells.filter((c) => c.isToday);
    expect(todays).toHaveLength(1);
    expect(todays[0].date).toBe('2026-08-15');
  });

  it('todayStr 不在网格范围时无高亮', () => {
    const cells = buildMonthGrid(2026, 8, '2030-01-01');
    expect(cells.some((c) => c.isToday)).toBe(false);
  });
});

describe('calendar / monthRange', () => {
  it('返回当月首末本地 datetime 边界', () => {
    expect(monthRange(2026, 8)).toEqual({
      from: '2026-08-01T00:00:00',
      to: '2026-08-31T23:59:59',
    });
  });

  it('二月非闰年末日为 28', () => {
    expect(monthRange(2026, 2).to).toBe('2026-02-28T23:59:59');
  });
});

describe('calendar / spanDates', () => {
  it('同日事件返回单元素', () => {
    expect(spanDates('2026-08-10T09:00:00', '2026-08-10T10:00:00')).toEqual(['2026-08-10']);
  });

  it('跨日事件返回逐日序列（含首尾）', () => {
    expect(spanDates('2026-08-10T22:00:00', '2026-08-12T02:00:00')).toEqual([
      '2026-08-10',
      '2026-08-11',
      '2026-08-12',
    ]);
  });

  it('end 早于 start 时退化为仅起始日', () => {
    expect(spanDates('2026-08-10T10:00:00', '2026-08-09T10:00:00')).toEqual(['2026-08-10']);
  });
});

describe('calendar / groupEventsByDate', () => {
  it('跨日事件出现在覆盖的每一天', () => {
    const events = [
      mkEvent({ id: 1, startTime: '2026-08-10T09:00:00', endTime: '2026-08-10T10:00:00' }),
      mkEvent({ id: 2, startTime: '2026-08-10T22:00:00', endTime: '2026-08-12T02:00:00', allDay: 1 }),
    ];
    const map = groupEventsByDate(events);
    expect(map.get('2026-08-10')?.map((e) => e.id)).toEqual([1, 2]);
    expect(map.get('2026-08-11')?.map((e) => e.id)).toEqual([2]);
    expect(map.get('2026-08-12')?.map((e) => e.id)).toEqual([2]);
    expect(map.get('2026-08-13')).toBeUndefined();
  });

  it('空列表返回空 Map', () => {
    expect(groupEventsByDate([]).size).toBe(0);
  });
});

describe('calendar / shiftMonth', () => {
  it('跨年前移（1 月 → 上年 12 月）', () => {
    expect(shiftMonth(2026, 1, -1)).toEqual({ year: 2025, month: 12 });
  });

  it('跨年后移（12 月 → 次年 1 月）', () => {
    expect(shiftMonth(2026, 12, 1)).toEqual({ year: 2027, month: 1 });
  });
});

describe('calendar / constants', () => {
  it('周标签周一首列共 7 项', () => {
    expect(WEEKDAY_LABELS).toEqual(['一', '二', '三', '四', '五', '六', '日']);
  });

  it('月查询页大小为 200', () => {
    expect(MONTH_QUERY_SIZE).toBe(200);
  });
});
