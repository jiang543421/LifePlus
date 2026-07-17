import dayjs from 'dayjs';
import type { PlanListItem } from '@/types';

/**
 * 月视图纯函数集合（无副作用，便于单测）。
 *
 * <p>约定：所有 `month` 参数为 1-based（1..12），对齐用户心智；内部与 dayjs 的
 * 0-based month 转换在此隔离。日期字符串统一 `YYYY-MM-DD`；本地 datetime 统一
 * `YYYY-MM-DDTHH:mm:ss`（无 offset，约定 TZ Asia/Shanghai 解释，与后端 Plan DTO 对齐）。
 */

/** 每页足够覆盖单月全部事件（月视图不分页；超出则由列表页兜底）。 */
export const MONTH_QUERY_SIZE = 200;

/** 网格固定 6 周 × 7 天 = 42 格（避免行数跳动导致布局抖动）。 */
const GRID_DAYS = 42;
const DAYS_IN_WEEK = 7;
/** spanDates 防御上限：单事件最多跨 366 天（跨年全年）。 */
const MAX_SPAN_DAYS = 366;

/** 周一为首列的星期标签。 */
export const WEEKDAY_LABELS: ReadonlyArray<string> = ['一', '二', '三', '四', '五', '六', '日'] as const;

/** 月视图单元格。 */
export interface DayCell {
  /** YYYY-MM-DD。 */
  readonly date: string;
  /** 该日在月中的日号（1..31）。 */
  readonly day: number;
  /** 是否属于当前展示月（false = 补位的上/下月日期）。 */
  readonly inMonth: boolean;
  /** 是否今天。 */
  readonly isToday: boolean;
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

function firstOfMonth(year: number, month: number) {
  return dayjs(`${year}-${pad2(month)}-01`);
}

/**
 * 生成 6×7 月网格（周一为首列）。
 * @param year 年
 * @param month 月（1-based）
 * @param todayStr 今天日期（YYYY-MM-DD）；缺省取当前系统日期。显式传入以便单测确定性。
 */
export function buildMonthGrid(year: number, month: number, todayStr?: string): DayCell[] {
  const first = firstOfMonth(year, month);
  // dayjs.day() 0=周日..6=周六；转周一首列偏移：(day + 6) % 7
  const leadingOffset = (first.day() + 6) % DAYS_IN_WEEK;
  const gridStart = first.subtract(leadingOffset, 'day');
  const today = todayStr ?? dayjs().format('YYYY-MM-DD');

  const cells: DayCell[] = [];
  for (let i = 0; i < GRID_DAYS; i += 1) {
    const d = gridStart.add(i, 'day');
    const date = d.format('YYYY-MM-DD');
    cells.push({
      date,
      day: d.date(),
      inMonth: d.month() === month - 1 && d.year() === year,
      isToday: date === today,
    });
  }
  return cells;
}

/**
 * 当月按月范围查询的时间边界（本地 datetime，含首末秒）。
 * @returns { from: 'YYYY-MM-01T00:00:00', to: 'YYYY-MM-<last>T23:59:59' }
 */
export function monthRange(year: number, month: number): { from: string; to: string } {
  const first = firstOfMonth(year, month);
  const last = first.endOf('month');
  return {
    from: first.format('YYYY-MM-DD[T]00:00:00'),
    to: last.format('YYYY-MM-DD[T]23:59:59'),
  };
}

/**
 * 事件覆盖的所有日期（YYYY-MM-DD，含首尾），支持跨日事件。
 * 越界或非法（end 早于 start）时返回仅含起始日的单元素数组。
 */
export function spanDates(startTime: string, endTime: string): string[] {
  const start = dayjs(startTime).startOf('day');
  const end = dayjs(endTime).startOf('day');
  if (end.isBefore(start)) return [start.format('YYYY-MM-DD')];

  const out: string[] = [];
  let cursor = start;
  let guard = 0;
  while (!cursor.isAfter(end) && guard < MAX_SPAN_DAYS) {
    out.push(cursor.format('YYYY-MM-DD'));
    cursor = cursor.add(1, 'day');
    guard += 1;
  }
  return out;
}

/**
 * 将事件列表按覆盖日期归组（跨日事件出现在每一天）。
 * 返回新 Map，值为新数组（immutability）。
 */
export function groupEventsByDate(events: ReadonlyArray<PlanListItem>): Map<string, PlanListItem[]> {
  const map = new Map<string, PlanListItem[]>();
  for (const ev of events) {
    for (const date of spanDates(ev.startTime, ev.endTime)) {
      const prev = map.get(date) ?? [];
      map.set(date, [...prev, ev]);
    }
  }
  return map;
}

/** 相对当前 year/month 平移 delta 个月，返回新的 { year, month }（month 1-based）。 */
export function shiftMonth(year: number, month: number, delta: number): { year: number; month: number } {
  const d = firstOfMonth(year, month).add(delta, 'month');
  return { year: d.year(), month: d.month() + 1 };
}
