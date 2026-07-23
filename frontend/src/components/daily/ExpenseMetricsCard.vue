<!--
  ExpenseMetricsCard — 日报消费指标卡（spec §08-daily-report-design §7.2）。

  <p>展示 3 段：
  <ul>
    <li>顶部：当日总额（formatAmount 保留 2 位小数）</li>
    <li>中部：topCategories top-3（"分类 金额"，按 amount 降序）</li>
    <li>底部：categoryBreakdown 5 键全展开（MEAL / SHOPPING / TRANSPORT / SUBSCRIPTION / OTHER）</li>
  </ul>

  <p>Pure presentational；formatAmount 走 utils/number 保证 2 位小数与
  Expense 模块一致（CLAUDE.md §4.1 复用现有工具）。
-->
<script setup lang="ts">
import { computed } from 'vue';
import { formatAmount } from '@/utils/number';
import type { ExpenseMetrics } from '@/types';

const props = defineProps<{
  expense: ExpenseMetrics;
}>();

/** breakdown 5 键固定顺序（与 ExpenseCategory 枚举对齐）。 */
const BREAKDOWN_KEYS = ['MEAL', 'SHOPPING', 'TRANSPORT', 'SUBSCRIPTION', 'OTHER'] as const;

/** 中文 label（与 Expense 模块 CATEGORY_LABEL 复用文案）。 */
const CATEGORY_LABEL: Record<string, string> = {
  MEAL: '餐饮',
  SHOPPING: '购物',
  TRANSPORT: '交通',
  SUBSCRIPTION: '订阅',
  OTHER: '其他',
};

const totalText = computed(() => formatAmount(props.expense.totalAmount));
</script>

<template>
  <div class="expense-card" data-testid="daily-expense-card">
    <header class="expense-card__header">
      <h3 class="expense-card__title">消费</h3>
    </header>

    <div class="expense-card__total" data-testid="daily-expense-total">
      <span class="expense-card__total-symbol">¥</span>
      <span class="expense-card__total-num">{{ totalText }}</span>
    </div>

    <ul
      v-if="expense.topCategories.length > 0"
      class="expense-card__top"
      data-testid="daily-expense-top"
    >
      <li
        v-for="top in expense.topCategories"
        :key="top.code"
        class="expense-card__top-item"
      >
        <span class="expense-card__top-label">{{ CATEGORY_LABEL[top.code] ?? top.code }}</span>
        <span class="expense-card__top-code">{{ top.code }}</span>
        <span class="expense-card__top-amount">¥{{ formatAmount(top.amount) }}</span>
      </li>
    </ul>

    <ul class="expense-card__breakdown" data-testid="daily-expense-breakdown">
      <li
        v-for="key in BREAKDOWN_KEYS"
        :key="key"
        class="expense-card__breakdown-item"
      >
        <span class="expense-card__breakdown-label">{{ CATEGORY_LABEL[key] }}</span>
        <span class="expense-card__breakdown-code">{{ key }}</span>
        <span class="expense-card__breakdown-amount">¥{{ formatAmount(expense.categoryBreakdown[key] ?? 0) }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.expense-card {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.expense-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.expense-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.expense-card__total {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: #303133;
}
.expense-card__total-symbol {
  font-size: 18px;
  font-weight: 500;
  color: #606266;
}
.expense-card__total-num {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
}
.expense-card__top,
.expense-card__breakdown {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.expense-card__top-item,
.expense-card__breakdown-item {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #606266;
}
.expense-card__top-label,
.expense-card__breakdown-label {
  flex: 1;
  color: #303133;
}
.expense-card__top-code,
.expense-card__breakdown-code {
  flex: 0 0 auto;
  width: 96px;
  color: #909399;
  font-size: 12px;
}
.expense-card__top-amount,
.expense-card__breakdown-amount {
  flex: 0 0 auto;
  width: 80px;
  text-align: right;
  color: #303133;
  font-weight: 500;
}
</style>
