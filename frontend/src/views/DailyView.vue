<!--
  DailyView — 日报视图（spec §08-daily-report-design §7.3）。

  <p>布局：
  <ul>
    <li>TopBar 全宽顶栏</li>
    <li>头部：标题「日报」+ 日期行 + 上一周/本周/下一周 切换按钮</li>
    <li>主体：2×2 网格 4 张卡（TaskMetricsCard / PlanMetricsCard / ExpenseMetricsCard / DietMetricsCard）</li>
    <li>底部：周报 triplet 区（仅在加载周报后展示）</li>
  </ul>

  <p>URL 双向绑定：
  <ul>
    <li>?date=YYYY-MM-DD  → store.filter.date（影响 daily fetch）</li>
    <li>?week=YYYY-Www    → 当前展示周（影响 week fetch）</li>
  </ul>
  切换日期/周时 router.replace 更新 query，刷新页面可恢复状态（spec §9.2 E2E "登录 → 看到 4 卡"覆盖）。

  <p>store.fetchDaily / fetchWeek 失败 → store.errorCode 写入；DailyView 不主动 toast
  （与 AiAnalysisView 同款：留给调用方按场景展示；S5 spec 覆盖基础渲染 + URL 同步）。
-->
<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
// side-effect import 让 TS 拿到 plugin 对 Dayjs 的方法增强（isoWeekYear/isoWeek overload）
import 'dayjs/plugin/isoWeek';
import isoWeek from 'dayjs/plugin/isoWeek';
// isoWeek 插件仅本视图用到，局部 extend 避免污染全局 utils/time.ts
dayjs.extend(isoWeek);
import { ElButton, ElEmpty, ElRow, ElCol, ElSkeleton } from 'element-plus';
import TopBar from '@/components/TopBar.vue';
import TaskMetricsCard from '@/components/daily/TaskMetricsCard.vue';
import PlanMetricsCard from '@/components/daily/PlanMetricsCard.vue';
import ExpenseMetricsCard from '@/components/daily/ExpenseMetricsCard.vue';
import DietMetricsCard from '@/components/daily/DietMetricsCard.vue';
import { useDailyStore } from '@/stores/daily';

const store = useDailyStore();
const route = useRoute();
const router = useRouter();

/** 当前展示周（route.query.week），无值时按今日对齐（store.fetchWeek 后端会自动按 ISO 周）。 */
const currentWeek = computed<string | undefined>(() => {
  const w = route.query.week;
  return typeof w === 'string' ? w : undefined;
});

/** 当前展示日期（route.query.date），无值时为空字符串（=今日，后端兜底）。 */
const currentDate = computed<string>(() => {
  const d = route.query.date;
  return typeof d === 'string' ? d : '';
});

/** 上一周/下一周按钮的 ISO 周字符串（基于当前 week 或今日）。 */
function isoWeekOffset(baseWeek: string | undefined, weeks: number): string {
  // baseWeek 形如 "2026-W30"；解析失败时按今日对齐
  const anchor = baseWeek ? parseIsoWeek(baseWeek) : dayjs();
  const shifted = anchor.add(weeks, 'week');
  const year = shifted.isoWeekYear();
  const week = shifted.isoWeek();
  return `${year}-W${String(week).padStart(2, '0')}`;
}

/** 把 "YYYY-Www" 解析为 dayjs（按周一）；解析失败返回今日。 */
function parseIsoWeek(s: string): dayjs.Dayjs {
  const m = /^(\d{4})-W(\d{2})$/.exec(s);
  if (!m) return dayjs();
  const year = parseInt(m[1], 10);
  const week = parseInt(m[2], 10);
  // ISO 8601 规定：包含 1 月 4 日的那一周就是 week 1；week 1 的周一为锚点
  // 避免用 isoWeekYear(year) setter（dayjs d.ts 未声明该 setter，TS 报错）
  const jan4 = dayjs(`${year}-01-04`);
  const week1Monday = jan4.startOf('isoWeek');
  return week1Monday.add((week - 1) * 7, 'day');
}

onMounted(async () => {
  store.syncFromRoute({
    date: currentDate.value || undefined,
    week: currentWeek.value,
  });
  await store.fetchDaily(currentDate.value || undefined);
  // 如果 URL 带 ?week= 同时拉周报，否则等用户点"本周/上一周/下一周"再拉
  if (currentWeek.value) {
    await store.fetchWeek(currentDate.value || undefined);
  }
});

/** 监听 URL ?date= 变化 → 重新拉 daily（避免点浏览器后退时数据不刷新） */
watch(currentDate, async (newDate) => {
  await store.fetchDaily(newDate || undefined);
});

/** 切换到本周（按钮）：同步 URL ?week=YYYY-Www + 拉周报。 */
async function goToCurrentWeek(): Promise<void> {
  const w = isoWeekOffset(currentWeek.value, 0);
  await router.replace({ query: { ...route.query, week: w } });
  await store.fetchWeek(currentDate.value || undefined);
}

/** 上一周按钮：week-1 + 同步 URL + 拉周报。 */
async function goToPrevWeek(): Promise<void> {
  const w = isoWeekOffset(currentWeek.value, -1);
  await router.replace({ query: { ...route.query, week: w } });
  await store.fetchWeek(currentDate.value || undefined);
}

/** 下一周按钮：week+1 + 同步 URL + 拉周报。 */
async function goToNextWeek(): Promise<void> {
  const w = isoWeekOffset(currentWeek.value, 1);
  await router.replace({ query: { ...route.query, week: w } });
  await store.fetchWeek(currentDate.value || undefined);
}

/**
 * v1.2.5 #3：错误态「重试」按钮 → 同时拉 daily 与当前 week。
 * 复用现有 onMounted 调用的同一对 fetch，与 URL 双向绑定一致。
 */
async function onRetry(): Promise<void> {
  await store.fetchDaily(currentDate.value || undefined);
  if (currentWeek.value) {
    await store.fetchWeek(currentDate.value || undefined);
  }
}

/** delta=null → "—"（spec §4.1）。 */
function formatDelta(delta: number | null): string {
  return delta === null ? '—' : delta.toFixed(2);
}

/** 任务完成率 delta 转百分比（0.15 → "+15%"）。 */
function formatTaskDelta(delta: number | null): string {
  if (delta === null) return '—';
  const pct = Math.round(delta * 100);
  return `${pct >= 0 ? '+' : ''}${pct}%`;
}

/** 金额 delta 转 "+¥X.XX"。 */
function formatAmountDelta(delta: number | null): string {
  if (delta === null) return '—';
  return `${delta >= 0 ? '+' : ''}¥${delta.toFixed(2)}`;
}

/** 整数 delta 转 "+N"。 */
function formatIntDelta(delta: number | null): string {
  if (delta === null) return '—';
  return `${delta >= 0 ? '+' : ''}${delta}`;
}
</script>

<template>
  <div class="daily-view">
    <TopBar />

    <main class="daily-view__main">
      <header class="daily-view__header">
        <div class="daily-view__title-row">
          <h1 class="daily-view__title">日报</h1>
          <span
            v-if="store.daily"
            class="daily-view__date"
            data-testid="daily-view-date"
          >{{ store.daily.date }}</span>
        </div>

        <div class="daily-view__week-buttons" data-testid="daily-week-buttons">
          <ElButton
            size="small"
            data-testid="daily-week-prev"
            @click="goToPrevWeek"
          >上一周</ElButton>
          <ElButton
            type="primary"
            size="small"
            data-testid="daily-week-current"
            @click="goToCurrentWeek"
          >本周</ElButton>
          <ElButton
            size="small"
            data-testid="daily-week-next"
            @click="goToNextWeek"
          >下一周</ElButton>
        </div>
      </header>

      <div v-if="store.loading && !store.daily" class="daily-view__loading">
        <ElSkeleton :rows="6" animated />
      </div>

      <template v-else-if="store.daily">
        <ElRow :gutter="20" class="daily-view__grid" data-testid="daily-view-grid">
          <ElCol :xs="24" :sm="12" class="daily-view__grid-col">
            <TaskMetricsCard :task="store.daily.task" />
          </ElCol>
          <ElCol :xs="24" :sm="12" class="daily-view__grid-col">
            <PlanMetricsCard :plan="store.daily.plan" />
          </ElCol>
          <ElCol :xs="24" :sm="12" class="daily-view__grid-col">
            <ExpenseMetricsCard :expense="store.daily.expense" />
          </ElCol>
          <ElCol :xs="24" :sm="12" class="daily-view__grid-col">
            <DietMetricsCard :diet="store.daily.diet" />
          </ElCol>
        </ElRow>

        <section
          v-if="store.week"
          class="daily-view__week"
          data-testid="daily-view-week"
        >
          <h2 class="daily-view__week-title">
            周报
            <span class="daily-view__week-label" data-testid="daily-week-label">
              {{ store.week.isoWeek }}
              ({{ store.week.weekStart }} ~ {{ store.week.weekEnd }})
            </span>
          </h2>

          <ul class="daily-view__week-list" data-testid="daily-week-list">
            <li class="daily-view__week-item">
              <span class="daily-view__week-key">任务完成率</span>
              <span class="daily-view__week-current">
                {{ Math.round(store.week.comparison.taskCompletion.current * 100) }}%
              </span>
              <span class="daily-view__week-arrow">→</span>
              <span class="daily-view__week-previous">
                上周 {{ Math.round(store.week.comparison.taskCompletion.previous * 100) }}%
              </span>
              <span
                class="daily-view__week-delta"
                :data-testid="`daily-week-delta-task`"
              >{{ formatTaskDelta(store.week.comparison.taskCompletion.delta) }}</span>
            </li>
            <li class="daily-view__week-item">
              <span class="daily-view__week-key">日程事件数</span>
              <span class="daily-view__week-current">
                {{ store.week.comparison.planEvents.current }}
              </span>
              <span class="daily-view__week-arrow">→</span>
              <span class="daily-view__week-previous">
                上周 {{ store.week.comparison.planEvents.previous }}
              </span>
              <span
                class="daily-view__week-delta"
                :data-testid="`daily-week-delta-plan`"
              >{{ formatIntDelta(store.week.comparison.planEvents.delta) }}</span>
            </li>
            <li class="daily-view__week-item">
              <span class="daily-view__week-key">消费总额</span>
              <span class="daily-view__week-current">
                ¥{{ store.week.comparison.expenseAmount.current.toFixed(2) }}
              </span>
              <span class="daily-view__week-arrow">→</span>
              <span class="daily-view__week-previous">
                上周 ¥{{ store.week.comparison.expenseAmount.previous.toFixed(2) }}
              </span>
              <span
                class="daily-view__week-delta"
                :data-testid="`daily-week-delta-expense`"
              >{{ formatAmountDelta(store.week.comparison.expenseAmount.delta) }}</span>
            </li>
          </ul>
        </section>
      </template>

      <div
        v-else-if="store.error"
        class="daily-view__error"
        data-testid="daily-view-error"
      >
        <ElEmpty
          :description="'暂时无法获取日报数据，请稍后重试'"
          data-testid="daily-view-error-description"
        >
          <template #image>
            <div class="daily-view__error-icon">⚠️</div>
          </template>
          <template #default>
            <ElButton
              type="primary"
              data-testid="daily-view-error-retry"
              @click="onRetry"
            >
              重试
            </ElButton>
          </template>
        </ElEmpty>
      </div>

      <ElEmpty
        v-else
        description="暂无日报数据，请稍后重试"
        data-testid="daily-view-empty"
      />
    </main>
  </div>
</template>

<style scoped>
.daily-view {
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}
.daily-view__main {
  flex: 1;
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}
.daily-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.daily-view__title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.daily-view__title {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  color: #303133;
}
.daily-view__date {
  font-size: 14px;
  color: #909399;
}
.daily-view__week-buttons {
  display: flex;
  gap: 8px;
}
.daily-view__grid {
  margin: 0 !important;
}
.daily-view__grid-col {
  margin-bottom: 20px;
}
.daily-view__week {
  margin-top: 24px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.daily-view__week-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.daily-view__week-label {
  font-size: 14px;
  font-weight: 400;
  color: #606266;
}
.daily-view__week-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.daily-view__week-item {
  display: grid;
  grid-template-columns: 1fr auto auto auto auto;
  gap: 12px;
  align-items: baseline;
  padding: 8px 12px;
  background: #fafbfc;
  border-radius: 6px;
  font-size: 14px;
  color: #606266;
}
.daily-view__week-key {
  color: #303133;
  font-weight: 500;
}
.daily-view__week-current {
  font-weight: 600;
  color: #303133;
}
.daily-view__week-arrow {
  color: #909399;
}
.daily-view__week-previous {
  color: #909399;
  font-size: 12px;
}
.daily-view__week-delta {
  font-weight: 600;
  color: #67c23a;
  min-width: 64px;
  text-align: right;
}
.daily-view__week-delta[data-testid$="-task"],
.daily-view__week-delta[data-testid$="-plan"],
.daily-view__week-delta[data-testid$="-expense"] {
  /* 共享样式已生效；此处为占位便于未来按类型差异化颜色 */
}
.daily-view__loading {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

/* v1.2.5 #3：错误态容器 — ElEmpty 自带 padding 不重复；居中放图标 + 文案 + 重试 */
.daily-view__error {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
}
.daily-view__error-icon {
  font-size: 48px;
  line-height: 1;
  margin-bottom: 8px;
}
</style>
