<!--
  ExpenseSummaryCard — 月度支出汇总卡（spec §06-expense section 5）。

  <p>展示层组件：
  <ul>
    <li>总金额（大字号 + ¥ 前缀）</li>
    <li>5 个分类占比（HTML 进度条，**不**引 ECharts — 避免 ~300kb bundle 增量）</li>
    <li>月份导航（前/后一个月，跨年自动回卷）</li>
  </ul>

  <p>父视图（ExpenseView，T14）持有 year/month 状态；切换月份 emit `change-month(y, m)`，
  父视图收到后调 store.fetchSummary + fetchList。

  <p>ECharts 决策：plan §T13 示例用 ECharts pie chart，但项目 package.json 没有 echarts 依赖，
  且 MVP1 阶段消费数据量小（<100 条/月），HTML 进度条已能满足"按分类看占比"诉求。
  留待 Phase 5（chart-budget 议题）再评估引入。
-->
<script setup lang="ts">
import { computed } from 'vue';
import { ElButton } from 'element-plus';
import { CATEGORY_LABEL } from '@/constants/expense';
import type { ExpenseCategory } from '@/constants/expense';
import { formatAmountWithSymbol } from '@/utils/number';
import type { ExpenseSummary } from '@/types';

const props = defineProps<{
  summary: ExpenseSummary | null;
  year: number;
  month: number;
}>();

const emit = defineEmits<{
  (e: 'change-month', y: number, m: number): void;
}>();

/** 大字号总额；null 时展示 ¥ 0.00（首次进入未加载态）。 */
const totalAmount = computed(() =>
  formatAmountWithSymbol(props.summary?.totalAmount ?? 0),
);

/** "YYYY-MM" 月份标签；用于导航中间展示。 */
const monthLabel = computed(() =>
  `${props.year}-${String(props.month).padStart(2, '0')}`,
);

/** 单行占比数据；总金额为 0 时 percent 强制 0（避免除零）。 */
interface BreakdownRow {
  code: ExpenseCategory;
  label: string;
  amount: number;
  percent: number;
}

const CATEGORY_ORDER: ReadonlyArray<ExpenseCategory> = [
  'MEAL',
  'SHOPPING',
  'TRANSPORT',
  'SUBSCRIPTION',
  'OTHER',
] as const;

const rows = computed<BreakdownRow[]>(() => {
  const total = props.summary?.totalAmount ?? 0;
  const buckets = props.summary?.amountByCategory;
  return CATEGORY_ORDER.map((code) => {
    const amount = buckets ? buckets[code] ?? 0 : 0;
    const percent = total > 0 ? (amount / total) * 100 : 0;
    return { code, label: CATEGORY_LABEL[code], amount, percent };
  });
});

function prevMonth(): void {
  let m = props.month - 1;
  let y = props.year;
  if (m < 1) {
    m = 12;
    y--;
  }
  emit('change-month', y, m);
}

function nextMonth(): void {
  let m = props.month + 1;
  let y = props.year;
  if (m > 12) {
    m = 1;
    y++;
  }
  emit('change-month', y, m);
}
</script>

<template>
  <div class="summary-card" data-testid="expense-summary-card">
    <header class="summary-card__header">
      <h3 class="summary-card__title">本月支出</h3>
      <div class="summary-card__month-nav">
        <ElButton
          size="small"
          data-testid="summary-prev-month"
          aria-label="上一月"
          @click="prevMonth"
        >
          ◀
        </ElButton>
        <span class="summary-card__month" data-testid="summary-month">{{ monthLabel }}</span>
        <ElButton
          size="small"
          data-testid="summary-next-month"
          aria-label="下一月"
          @click="nextMonth"
        >
          ▶
        </ElButton>
      </div>
    </header>

    <div class="summary-card__total" data-testid="summary-total">{{ totalAmount }}</div>

    <ul class="summary-card__breakdown" data-testid="summary-breakdown">
      <li
        v-for="row in rows"
        :key="row.code"
        class="summary-row"
        :data-testid="`summary-row-${row.code}`"
      >
        <span class="summary-row__label">{{ row.label }}</span>
        <div class="summary-row__bar-track">
          <div
            class="summary-row__bar-fill"
            :data-testid="`summary-bar-${row.code}`"
            :style="{ width: `${row.percent}%` }"
          ></div>
        </div>
        <span class="summary-row__amount" :data-testid="`summary-amount-${row.code}`">
          {{ formatAmountWithSymbol(row.amount) }}
        </span>
        <span class="summary-row__percent" :data-testid="`summary-percent-${row.code}`">
          {{ row.percent.toFixed(1) }}%
        </span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.summary-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.summary-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.summary-card__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  margin: 0;
}
.summary-card__month-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}
.summary-card__month {
  font-size: 13px;
  color: var(--el-text-color-primary);
  min-width: 70px;
  text-align: center;
}
.summary-card__total {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin-bottom: 16px;
}
.summary-card__breakdown {
  list-style: none;
  margin: 0;
  padding: 0;
}
.summary-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}
.summary-row__label {
  min-width: 40px;
  color: var(--el-text-color-regular);
}
.summary-row__bar-track {
  flex: 1;
  height: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  overflow: hidden;
}
.summary-row__bar-fill {
  height: 100%;
  background: var(--el-color-primary);
  transition: width 0.2s ease;
}
.summary-row__amount {
  min-width: 64px;
  text-align: right;
  color: var(--el-text-color-primary);
}
.summary-row__percent {
  min-width: 48px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>