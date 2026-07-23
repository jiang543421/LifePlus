<!--
  TrendPanel — AI 趋势图组合组件（spec §v2.2 trend）。

  <p>设计动机：4 槽 metrics（task/plan/expense/diet）每个一个 sparkline，
  桌面 2×2 网格 + 移动单列堆叠；窗口切换走 ElRadioButton（7/14/30），
  URL 同步 ?window=N 便于分享。

  <p>约束：
  <ul>
    <li>0 新依赖（复用 ElRadioGroup / ElRadioButton / ElSkeleton / SparklineChart / TriStateError）</li>
    <li>3 状态：loading skeleton（v1.2.6 #5 --tri-state-loading-bg） / rendered / error+retry</li>
    <li>URL ?window= 解析非法值回退默认 14</li>
    <li>切换窗口不重置旧数据：旧槽位保留，新数据到达前不闪 skeleton（仅初次加载显示）</li>
  </ul>
-->
<template>
  <section class="trend-panel" data-testid="trend-panel">
    <header class="header">
      <h2 class="title">趋势</h2>
      <ElRadioGroup
        v-model="selectedWindow"
        size="small"
        data-testid="trend-panel-window-switch"
      >
        <ElRadioButton
          v-for="opt in WINDOW_OPTIONS"
          :key="opt.value"
          :value="opt.value"
          :data-testid="`trend-panel-window-${opt.value}`"
        >
          {{ opt.label }}
        </ElRadioButton>
      </ElRadioGroup>
    </header>

    <div
      v-if="store.loading && !currentTrend"
      class="loading-block"
      data-testid="trend-panel-loading"
    >
      <ElSkeleton :rows="4" animated />
    </div>

    <TriStateError
      v-else-if="store.error && !currentTrend"
      test-id="trend-panel-error"
      @retry="onRetry"
    />

    <div
      v-else-if="currentTrend"
      class="grid"
      data-testid="trend-panel-grid"
    >
      <SparklineChart
        v-for="cfg in SERIES_CONFIG"
        :key="cfg.key"
        :points="currentTrend.series[cfg.key]?.points ?? []"
        :color="cfg.color"
        :title="cfg.label"
        :unit="currentTrend.series[cfg.key]?.unit ?? ''"
        :test-id="`trend-panel-sparkline-${cfg.key}`"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElRadioGroup, ElRadioButton, ElSkeleton } from 'element-plus';
import SparklineChart from './SparklineChart.vue';
import TriStateError from '@/components/TriStateError.vue';
import { useAiTrendStore } from '@/stores/aiTrend';
import type { AiTrendResponse } from '@/types';
import type { TrendWindowDays } from '@/api/ai-trend';

interface WindowOption {
  readonly value: TrendWindowDays;
  readonly label: string;
}

const WINDOW_OPTIONS: readonly WindowOption[] = [
  { value: 7, label: '7 天' },
  { value: 14, label: '14 天' },
  { value: 30, label: '30 天' },
] as const;

interface SeriesConfig {
  readonly key: string;
  readonly label: string;
  readonly color: string;
}

/** 4 槽 series 配置（颜色 + 中文 label，与后端 MetricSeriesDto 对齐）。 */
const SERIES_CONFIG: readonly SeriesConfig[] = [
  { key: 'task', label: '任务完成率', color: '#4A90D9' },
  { key: 'plan', label: '日程事件', color: '#52C41A' },
  { key: 'expense', label: '消费金额', color: '#FA8C16' },
  { key: 'diet', label: '饮食', color: '#D9D9D9' },
] as const;

const route = useRoute();
const router = useRouter();
const store = useAiTrendStore();

/**
 * 解析 URL ?window= 参数；非法值回退默认 14。
 * 防御性 narrow：只有 {7,14,30} 才返回字面量类型。
 */
function parseWindow(v: unknown): TrendWindowDays {
  const n = Number(v);
  if (n === 7 || n === 14 || n === 30) {
    return n;
  }
  return 14;
}

const selectedWindow = ref<TrendWindowDays>(parseWindow(route.query.window));

/** 切换窗口：URL 同步（replace 不增加历史）+ 重新拉数据。 */
watch(selectedWindow, async (w) => {
  await router.replace({
    query: { ...route.query, window: String(w) },
  });
  await store.load(w);
});

/** 当前窗口的趋势数据（store 按 window 分桶）。 */
const currentTrend = computed<AiTrendResponse | null>(
  () => store.trend[selectedWindow.value],
);

/** 挂载时拉一次（按 URL 解析后的窗口）。 */
onMounted(() => {
  void store.load(selectedWindow.value);
});

/** 重试按钮：当前窗口重新拉。 */
function onRetry(): void {
  void store.load(selectedWindow.value);
}
</script>

<style scoped>
.trend-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: #303133;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.loading-block {
  background: var(--tri-state-loading-bg);
  border-radius: var(--tri-state-loading-radius);
  padding: 24px;
}

@media (max-width: 768px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>