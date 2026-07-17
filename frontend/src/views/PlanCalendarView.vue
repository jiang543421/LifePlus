<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { ElMessage, ElButton, ElTag } from 'element-plus';
import { usePlanStore } from '@/stores/plan';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import { MONTH_QUERY_SIZE, monthRange, groupEventsByDate, shiftMonth } from '@/utils/calendar';
import { PlanAllDayValue } from '@/types';
import type { PlanCreateRequest, PlanListItem, PlanUpdateRequest } from '@/types';
import CalendarMonth from '@/components/CalendarMonth.vue';
import EventDialog from '@/components/EventDialog.vue';

/**
 * 日历主页（spec §04 §5 + PRD PLAN-1/PLAN-2）。
 *
 * <p>持有当前 year/month/selectedDate；每次月份切换按月范围拉取事件（store.fetchList）。
 * 新建通过 EventDialog 提交，成功后重新拉取当月事件。
 */
const router = useRouter();
const store = usePlanStore();

const now = dayjs();
const year = ref(now.year());
const month = ref(now.month() + 1);
const selectedDate = ref(now.format('YYYY-MM-DD'));

const dialogOpen = ref(false);
const submitting = ref(false);

const events = computed<PlanListItem[]>(() => store.list ?? []);
const eventsByDate = computed(() => groupEventsByDate(events.value));
const selectedEvents = computed<PlanListItem[]>(() => eventsByDate.value.get(selectedDate.value) ?? []);

async function loadMonth(): Promise<void> {
  const { from, to } = monthRange(year.value, month.value);
  store.setFilter({ from, to });
  store.setPage(1, MONTH_QUERY_SIZE);
  const resp = await store.fetchList();
  if (!resp && store.errorCode) showAuthError(store.errorCode);
}

onMounted(loadMonth);
onUnmounted(() => store.clear());

function goPrev(): void {
  const next = shiftMonth(year.value, month.value, -1);
  year.value = next.year;
  month.value = next.month;
  void loadMonth();
}

function goNext(): void {
  const next = shiftMonth(year.value, month.value, 1);
  year.value = next.year;
  month.value = next.month;
  void loadMonth();
}

function goToday(): void {
  const t = dayjs();
  year.value = t.year();
  month.value = t.month() + 1;
  selectedDate.value = t.format('YYYY-MM-DD');
  void loadMonth();
}

function onSelectDay(date: string): void {
  selectedDate.value = date;
}

function openCreate(): void {
  dialogOpen.value = true;
}

async function onCreateSubmit(payload: PlanCreateRequest | PlanUpdateRequest): Promise<void> {
  submitting.value = true;
  try {
    await store.create(payload as PlanCreateRequest);
    dialogOpen.value = false;
    ElMessage({ message: '已创建', type: 'success' });
    await loadMonth();
  } catch (e) {
    // store.create 失败不写 errorCode（mutation 设计），直接从异常取业务码
    if (e instanceof ApiError) showAuthError(e.code);
    else ElMessage({ message: '创建失败', type: 'error' });
  } finally {
    submitting.value = false;
  }
}

function openDetail(id: number): void {
  void router.push(`/plans/${id}`);
}

/** 列表项时间摘要：全天显示「全天」，否则显示 HH:mm 起止（跨日附日期）。 */
function timeLabel(ev: PlanListItem): string {
  if (ev.allDay === PlanAllDayValue.ALL_DAY) return '全天';
  const start = dayjs(ev.startTime);
  const end = dayjs(ev.endTime);
  const sameDay = start.format('YYYY-MM-DD') === end.format('YYYY-MM-DD');
  return sameDay
    ? `${start.format('HH:mm')} – ${end.format('HH:mm')}`
    : `${start.format('MM-DD HH:mm')} – ${end.format('MM-DD HH:mm')}`;
}
</script>

<template>
  <main class="plan-calendar-view">
    <header class="header">
      <h1>日程</h1>
      <el-button type="primary" data-testid="new-plan" @click="openCreate">+ 新建事件</el-button>
    </header>

    <div v-if="store.loading" class="state">加载中…</div>

    <CalendarMonth
      :year="year"
      :month="month"
      :events="events"
      :selected-date="selectedDate"
      @prev="goPrev"
      @next="goNext"
      @today="goToday"
      @select="onSelectDay"
    />

    <section class="day-panel" data-testid="day-panel">
      <h2 class="day-title">{{ selectedDate }} 的事件</h2>
      <div v-if="selectedEvents.length === 0" class="state empty" data-testid="day-empty">
        当天没有事件
      </div>
      <ul v-else class="event-list" data-testid="event-list">
        <li
          v-for="ev in selectedEvents"
          :key="ev.id"
          class="event-row"
          data-testid="event-row"
          @click="openDetail(ev.id)"
        >
          <el-tag
            v-if="ev.allDay === PlanAllDayValue.ALL_DAY"
            type="success"
            size="small"
            class="allday-tag"
          >全天</el-tag>
          <span class="time">{{ timeLabel(ev) }}</span>
          <span class="title" :title="ev.title">{{ ev.title }}</span>
          <span v-if="ev.location" class="location">📍 {{ ev.location }}</span>
        </li>
      </ul>
    </section>

    <EventDialog
      v-model="dialogOpen"
      mode="create"
      :default-date="selectedDate"
      :submitting="submitting"
      @submit="onCreateSubmit"
    />
  </main>
</template>

<style scoped>
.plan-calendar-view {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.header h1 {
  margin: 0;
  font-size: 22px;
}
.state {
  padding: 24px 16px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.day-panel {
  margin-top: 24px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
}
.day-title {
  margin: 0 0 12px;
  font-size: 16px;
}
.event-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.event-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.event-row:hover {
  background: var(--el-fill-color-light);
}
.allday-tag {
  min-width: 44px;
  text-align: center;
}
.time {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  min-width: 120px;
}
.title {
  flex: 1;
  font-size: 15px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.location {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
