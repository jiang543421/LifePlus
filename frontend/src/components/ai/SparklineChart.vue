<!--
  SparklineChart — AI 趋势图最小折线组件（v2.2 trend spec）。

  <p>设计动机：4 槽 metrics（task/plan/expense/diet）每个一个 sparkline，
  复用单一组件 + props 路由。0 新依赖（手写 SVG）。

  <p>约束：
  <ul>
    <li>viewBox 100×48，宽度自适应 100%、高度 48px（spec §v2.2 trend）</li>
    <li>归一化 min/max → 0..48；n=1 时固定中线；n=0 走 TriStateEmpty</li>
    <li>最后一个点用 fill 圆高亮（端点 marker）</li>
    <li>空数据走 TriStateEmpty（v1.2.6 #4.1 复用）</li>
    <li>hover 触发 ElTooltip：日期 + 标签 + 单位</li>
    <li>复用 Element Plus ElTooltip（已有依赖，0 新依赖）</li>
  </ul>
-->
<template>
  <div :data-testid="testId" class="sparkline-chart">
    <header class="header">
      <span class="title">{{ title }}</span>
      <span v-if="lastLabel" class="latest" :style="{ color }">{{ lastLabel }}</span>
    </header>

    <TriStateEmpty
      v-if="!hasData"
      :description="emptyText"
      :test-id="`${testId}-empty`"
    />

    <div v-else class="chart">
      <svg
        class="line"
        viewBox="0 0 100 48"
        preserveAspectRatio="none"
        role="img"
        :aria-label="`${title} 趋势图`"
      >
        <polyline
          :points="polylinePoints"
          :stroke="color"
          fill="none"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <circle
          v-if="endpoint"
          :cx="endpoint.x"
          :cy="endpoint.y"
          r="2"
          :fill="color"
        />
      </svg>

      <div class="markers">
        <ElTooltip
          v-for="p in positionedPoints"
          :key="p.date"
          placement="top"
          :content="formatTooltip(p)"
        >
          <span
            class="marker"
            :style="{ left: p.pctX + '%', top: p.pctY + '%' }"
          />
        </ElTooltip>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ElTooltip } from 'element-plus';
import TriStateEmpty from '@/components/TriStateEmpty.vue';

/**
 * 单点类型，与后端 MetricPointDto 对齐（v2.2 trend spec）。
 */
export interface SparklinePoint {
  date: string;
  value: number;
  label: string;
}

/** viewBox 高度，单位 px（外层 DOM 实际高度也是 48px）。 */
const VIEW_HEIGHT = 48;

/** viewBox 宽度，外层 width: 100%。 */
const VIEW_WIDTH = 100;

const props = withDefaults(
  defineProps<{
    points: SparklinePoint[];
    color?: string;
    title: string;
    unit?: string;
    testId?: string;
    emptyText?: string;
  }>(),
  {
    color: '#4A90D9',
    unit: '',
    testId: 'sparkline-chart',
    emptyText: '暂无数据',
  },
);

const hasData = computed(() => props.points.length > 0);

const lastLabel = computed(() => {
  if (!hasData.value) return '';
  const last = props.points[props.points.length - 1];
  return last.label + props.unit;
});

/**
 * 计算每个点归一化坐标（{date, value, label, x, y, pctX, pctY}）。
 * y 轴反转：value 高 → y 小（SVG 原点在左上）。
 */
const positionedPoints = computed(() => {
  const pts = props.points;
  if (pts.length === 0) return [];

  const values = pts.map((p) => p.value);
  let min = Math.min(...values);
  let max = Math.max(...values);
  if (min === max) {
    // 全部相同值 → 中线，避免除零；n=1 也走这条
    min -= 1;
    max += 1;
  }

  return pts.map((p, i) => {
    const x = pts.length === 1
      ? VIEW_WIDTH / 2
      : (i / (pts.length - 1)) * VIEW_WIDTH;
    const y = VIEW_HEIGHT - ((p.value - min) / (max - min)) * VIEW_HEIGHT;
    return {
      ...p,
      x,
      y,
      pctX: (x / VIEW_WIDTH) * 100,
      pctY: (y / VIEW_HEIGHT) * 100,
    };
  });
});

/** polyline points 字符串："x1,y1 x2,y2 ..."。 */
const polylinePoints = computed(() =>
  positionedPoints.value.map((p) => `${p.x},${p.y}`).join(' '),
);

/** 最后一个点（端点高亮）。 */
const endpoint = computed(() => {
  const arr = positionedPoints.value;
  return arr.length === 0 ? null : arr[arr.length - 1];
});

function formatTooltip(p: SparklinePoint & { x: number; y: number }): string {
  return `${p.date}  ${p.label}${props.unit}`;
}
</script>

<style scoped>
.sparkline-chart {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 13px;
  color: var(--lp-text-secondary, #606266);
}

.title {
  font-weight: 500;
}

.latest {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.chart {
  position: relative;
  width: 100%;
  height: 48px;
}

.line {
  width: 100%;
  height: 48px;
  display: block;
}

.markers {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.marker {
  position: absolute;
  width: 12px;
  height: 12px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  pointer-events: auto;
  cursor: pointer;
  background: transparent;
}

.marker:hover {
  background: rgba(0, 0, 0, 0.04);
}
</style>