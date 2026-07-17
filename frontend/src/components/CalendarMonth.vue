<script setup lang="ts">
import { computed } from 'vue';
import { ElButton } from 'element-plus';
import type { PlanListItem } from '@/types';
import { PlanAllDayValue } from '@/types';
import { buildMonthGrid, groupEventsByDate, WEEKDAY_LABELS, type DayCell } from '@/utils/calendar';

/**
 * 月视图网格（spec §04 §5 + PRD PLAN-1）。
 *
 * <p>展示层组件：纯 props 输入 + emit 事件，不直接调 API / store。
 * 父视图管理 year/month/selectedDate 与事件列表，月份切换与选中日期均通过 emit 上抛。
 */
const props = defineProps<{
  /** 展示年份。 */
  year: number;
  /** 展示月份（1-based）。 */
  month: number;
  /** 当月范围内的事件（含跨日事件）。 */
  events: ReadonlyArray<PlanListItem>;
  /** 当前选中日期（YYYY-MM-DD）；null = 无选中。 */
  selectedDate: string | null;
}>();

const emit = defineEmits<{
  (e: 'prev'): void;
  (e: 'next'): void;
  (e: 'today'): void;
  (e: 'select', date: string): void;
}>();

/** 展示上限：每格最多 3 个点，其余折叠为 +N。 */
const MAX_DOTS = 3;

const cells = computed<DayCell[]>(() => buildMonthGrid(props.year, props.month));
const eventsByDate = computed(() => groupEventsByDate(props.events));

function dayEvents(date: string): PlanListItem[] {
  return eventsByDate.value.get(date) ?? [];
}

function hasAllDay(date: string): boolean {
  return dayEvents(date).some((e) => e.allDay === PlanAllDayValue.ALL_DAY);
}

function dotEvents(date: string): PlanListItem[] {
  return dayEvents(date).slice(0, MAX_DOTS);
}

function overflowCount(date: string): number {
  return Math.max(0, dayEvents(date).length - MAX_DOTS);
}

function cellTitle(date: string): string {
  const n = dayEvents(date).length;
  return n > 0 ? `${n} 个事件` : '';
}
</script>

<template>
  <section class="calendar-month" data-testid="calendar-month">
    <header class="cal-header">
      <div class="nav">
        <el-button text data-testid="cal-prev" @click="emit('prev')">‹</el-button>
        <span class="title" data-testid="cal-title">{{ year }} 年 {{ month }} 月</span>
        <el-button text data-testid="cal-next" @click="emit('next')">›</el-button>
      </div>
      <el-button size="small" data-testid="cal-today" @click="emit('today')">今天</el-button>
    </header>

    <div class="weekdays">
      <span v-for="w in WEEKDAY_LABELS" :key="w" class="weekday">{{ w }}</span>
    </div>

    <div class="grid" data-testid="cal-grid">
      <button
        v-for="c in cells"
        :key="c.date"
        type="button"
        class="cell"
        :class="{
          out: !c.inMonth,
          today: c.isToday,
          selected: c.date === selectedDate,
          'has-allday': hasAllDay(c.date),
        }"
        :title="cellTitle(c.date)"
        :data-date="c.date"
        data-testid="day-cell"
        @click="emit('select', c.date)"
      >
        <span class="daynum">{{ c.day }}</span>
        <span v-if="dayEvents(c.date).length" class="dots" data-testid="day-dots">
          <i
            v-for="e in dotEvents(c.date)"
            :key="e.id"
            class="dot"
            :class="{ allday: e.allDay === PlanAllDayValue.ALL_DAY }"
          ></i>
          <em v-if="overflowCount(c.date)" class="more">+{{ overflowCount(c.date) }}</em>
        </span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.calendar-month {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
}
.cal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.nav {
  display: flex;
  align-items: center;
  gap: 8px;
}
.title {
  font-size: 18px;
  font-weight: 600;
  min-width: 120px;
  text-align: center;
}
.weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 8px;
}
.weekday {
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}
.cell {
  position: relative;
  min-height: 72px;
  padding: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  transition: background 0.15s;
}
.cell:hover {
  background: var(--el-fill-color-light);
}
.cell.out {
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-lighter);
}
.cell.today .daynum {
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 50%;
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.cell.selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}
.cell.has-allday::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  border-radius: 6px 6px 0 0;
  background: var(--el-color-success);
}
.daynum {
  font-size: 14px;
}
.dots {
  display: flex;
  align-items: center;
  gap: 3px;
  flex-wrap: wrap;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
  display: inline-block;
}
.dot.allday {
  background: var(--el-color-success);
}
.more {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-style: normal;
}
</style>
