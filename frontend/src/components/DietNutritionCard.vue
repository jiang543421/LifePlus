<!--
  DietNutritionCard — 当日营养汇总卡（spec §07-diet-design §6.3）。

  <p>4 项营养（kcal / proteinG / carbG / fatG）+ 推荐摄入常量（REC_DAILY_*）
  横向 HTML 进度条（与 ExpenseSummaryCard 同款；**不**引 ECharts —
  见 ExpenseSummaryCard 注释中"ECharts 决策"段落）。

  <p>vs 昨日 / vs 上周同日 kcal 差值：
  <ul>
    <li>delta != null → 显示带 ± 前缀的数字（spec §6.3 PRD §3.1）</li>
    <li>delta == null → 显示「无对比数据」</li>
  </ul>

  <p>纯展示组件；父视图（DietView T17）持有 summary 状态，
  切换日期后调 store.fetchSummary → 响应回灌到 summary prop。
-->
<script setup lang="ts">
import { computed } from 'vue';
import { formatAmount } from '@/utils/number';
import {
  REC_DAILY_CARB_G,
  REC_DAILY_FAT_G,
  REC_DAILY_KCAL,
  REC_DAILY_PROTEIN_G,
} from '@/constants/diet';
import type { DietSummary } from '@/types';

const props = defineProps<{
  summary: DietSummary | null;
}>();

interface NutritionRow {
  key: 'kcal' | 'proteinG' | 'carbG' | 'fatG';
  label: string;
  unit: string;
  value: number;
  recommended: number;
  percent: number;
}

const rows = computed<NutritionRow[]>(() => {
  const s = props.summary;
  const rec: Record<NutritionRow['key'], number> = {
    kcal: REC_DAILY_KCAL,
    proteinG: REC_DAILY_PROTEIN_G,
    carbG: REC_DAILY_CARB_G,
    fatG: REC_DAILY_FAT_G,
  };
  const value: Record<NutritionRow['key'], number> = {
    kcal: s?.kcal ?? 0,
    proteinG: s?.proteinG ?? 0,
    carbG: s?.carbG ?? 0,
    fatG: s?.fatG ?? 0,
  };
  return [
    { key: 'kcal', label: '热量', unit: 'kcal', recommended: rec.kcal, value: value.kcal, percent: 0 },
    { key: 'proteinG', label: '蛋白质', unit: 'g', recommended: rec.proteinG, value: value.proteinG, percent: 0 },
    { key: 'carbG', label: '碳水', unit: 'g', recommended: rec.carbG, value: value.carbG, percent: 0 },
    { key: 'fatG', label: '脂肪', unit: 'g', recommended: rec.fatG, value: value.fatG, percent: 0 },
  ].map((r) => ({
    ...r,
    percent: r.recommended > 0 ? (r.value / r.recommended) * 100 : 0,
  }));
});

/** 顶部大字号热量（kcal）数字。 */
const totalKcal = computed(() => formatAmount(props.summary?.kcal ?? 0));

/** delta 文本：null → "无对比数据"；否则 "+120" / "-80"。 */
function formatDelta(delta: number | null): string {
  if (delta == null) return '无对比数据';
  const sign = delta >= 0 ? '+' : '';
  return `${sign}${formatAmount(delta)}`;
}
</script>

<template>
  <div class="nutrition-card" data-testid="diet-nutrition-card">
    <header class="nutrition-card__header">
      <h3 class="nutrition-card__title">当日营养</h3>
    </header>

    <div class="nutrition-card__total" data-testid="diet-nutrition-total">
      <span class="nutrition-card__total-num">{{ totalKcal }}</span>
      <span class="nutrition-card__total-unit"> / {{ REC_DAILY_KCAL }} kcal</span>
    </div>

    <ul class="nutrition-card__rows" data-testid="diet-nutrition-rows">
      <li
        v-for="row in rows"
        :key="row.key"
        class="nutrition-row"
        :data-testid="`diet-nutrition-row-${row.key}`"
      >
        <div class="nutrition-row__top">
          <span class="nutrition-row__label">{{ row.label }}</span>
          <span class="nutrition-row__value" :data-testid="`diet-nutrition-value-${row.key}`">
            {{ formatAmount(row.value) }} / {{ row.recommended }} {{ row.unit }}
          </span>
        </div>
        <div class="nutrition-row__bar-track">
          <div
            class="nutrition-row__bar-fill"
            :data-testid="`diet-nutrition-bar-${row.key}`"
            :style="{ width: `${Math.min(row.percent, 100)}%` }"
          ></div>
        </div>
      </li>
    </ul>

    <footer class="nutrition-card__footer">
      <div class="delta-row" data-testid="diet-nutrition-delta-yesterday">
        <span class="delta-row__label">vs 昨日</span>
        <span
          class="delta-row__value"
          :data-testid="'diet-nutrition-delta-yesterday-value'"
        >{{ formatDelta(summary?.kcalDeltaYesterday ?? null) }}</span>
      </div>
      <div class="delta-row" data-testid="diet-nutrition-delta-last-week">
        <span class="delta-row__label">vs 上周同日</span>
        <span
          class="delta-row__value"
          :data-testid="'diet-nutrition-delta-last-week-value'"
        >{{ formatDelta(summary?.kcalDeltaLastWeek ?? null) }}</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.nutrition-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.nutrition-card__header {
  margin-bottom: 12px;
}
.nutrition-card__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  margin: 0;
}
.nutrition-card__total {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin-bottom: 16px;
}
.nutrition-card__total-unit {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.nutrition-card__rows {
  list-style: none;
  margin: 0;
  padding: 0;
  margin-bottom: 16px;
}
.nutrition-row {
  margin-bottom: 10px;
  font-size: 12px;
}
.nutrition-row:last-child {
  margin-bottom: 0;
}
.nutrition-row__top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}
.nutrition-row__label {
  color: var(--el-text-color-regular);
}
.nutrition-row__value {
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}
.nutrition-row__bar-track {
  height: 6px;
  background: var(--el-fill-color-light);
  border-radius: 3px;
  overflow: hidden;
}
.nutrition-row__bar-fill {
  height: 100%;
  background: var(--el-color-primary);
  transition: width 0.25s ease;
}
.nutrition-card__footer {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}
.delta-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
}
.delta-row:last-child {
  margin-bottom: 0;
}
.delta-row__label {
  color: var(--el-text-color-secondary);
}
.delta-row__value {
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}
</style>