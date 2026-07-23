<!--
  PlanMetricsCard — 日报日程指标卡（spec §08-daily-report-design §7.2）。

  <p>展示 3 段：
  <ul>
    <li>顶部：事件数（"N 项"）</li>
    <li>中部：总分钟数（"M 分钟"，排除全天事件）</li>
    <li>底部：最忙时段（"HH:00"，无事件时显示 "—"）</li>
  </ul>

  <p>Pure presentational。
-->
<script setup lang="ts">
import { computed } from 'vue';
import type { PlanMetrics } from '@/types';

const props = defineProps<{
  plan: PlanMetrics;
}>();

/** 最忙时段格式化：0..23 → "HH:00"；null → "—" */
function formatHour(hour: number | null): string {
  if (hour === null) return '—';
  return `${String(hour).padStart(2, '0')}:00`;
}

const busiestHourText = computed(() => formatHour(props.plan.busiestHour));
</script>

<template>
  <div class="plan-card" data-testid="daily-plan-card">
    <header class="plan-card__header">
      <h3 class="plan-card__title">日程</h3>
    </header>

    <div class="plan-card__event-count" data-testid="daily-plan-event-count">
      <span class="plan-card__num">{{ plan.eventCount }}</span>
      <span class="plan-card__unit">项</span>
    </div>

    <div class="plan-card__total-minutes" data-testid="daily-plan-total-minutes">
      总计 {{ plan.totalMinutes }} 分钟
    </div>

    <div class="plan-card__busiest-hour" data-testid="daily-plan-busiest-hour">
      最忙时段 {{ busiestHourText }}
    </div>
  </div>
</template>

<style scoped>
.plan-card {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.plan-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.plan-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.plan-card__event-count {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.plan-card__num {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}
.plan-card__unit {
  font-size: 14px;
  color: #606266;
}
.plan-card__total-minutes,
.plan-card__busiest-hour {
  font-size: 14px;
  color: #606266;
}
</style>
