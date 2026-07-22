<!--
  AiDrawer — AI 洞察抽屉（spec docs/ui-prototypes/07-ai.md）。

  父组件传入 v-model:show + insight（AiInsightResponse），支持 emit('refresh')。
  内容：
  - 主文 headline（大字号中性灰）
  - 3 个 chip（label + value+unit + deltaText）
  - 「刷新」按钮，emit refresh 让父走 aiApi.refresh() 重算
  - 「上次生成」时间（generatedAt + freshnessSeconds）
-->
<template>
  <ElDrawer
    :model-value="show"
    title="AI 洞察"
    direction="rtl"
    size="420px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:show', v)"
  >
    <div v-if="insight" class="ai-drawer" data-testid="ai-drawer">
      <p class="ai-drawer__headline" data-testid="ai-drawer-headline">
        {{ insight.headline }}
      </p>

      <ul class="ai-drawer__chips" data-testid="ai-drawer-chips">
        <li
          v-for="chip in insight.chips"
          :key="chip.key"
          class="ai-drawer__chip"
          :class="`ai-drawer__chip--${chip.trend.toLowerCase()}`"
          data-testid="ai-drawer-chip"
        >
          <span class="ai-drawer__chip-label">{{ chip.label }}</span>
          <span class="ai-drawer__chip-value">
            {{ chip.value }}<span v-if="chip.unit" class="ai-drawer__chip-unit">{{ chip.unit }}</span>
          </span>
          <span v-if="chip.deltaText" class="ai-drawer__chip-delta">{{ chip.deltaText }}</span>
        </li>
      </ul>

      <footer class="ai-drawer__footer">
        <span class="ai-drawer__freshness">
          {{ freshnessLabel }}
        </span>
        <ElButton
          type="primary"
          :loading="refreshing"
          data-testid="ai-drawer-refresh"
          @click="onRefreshClick"
        >
          刷新
        </ElButton>
      </footer>
    </div>
  </ElDrawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { ElDrawer, ElButton } from 'element-plus';
import type { AiInsightResponse } from '@/types';

interface Props {
  show: boolean;
  insight: AiInsightResponse | null;
  /** true 时「刷新」按钮转圈，禁用重复点击。 */
  refreshing?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  refreshing: false,
});

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  /** 用户点击「刷新」按钮，父组件触发 aiApi.refresh() 并回传新值。 */
  (e: 'refresh'): void;
}>();

/** 把 freshnessSeconds 渲染为「X 秒前 / X 分钟前 / X 小时前」。 */
const freshnessLabel = computed(() => {
  const insight = props.insight;
  if (!insight) return '';
  const sec = Math.max(insight.freshnessSeconds, 0);
  if (sec < 60) return `${sec} 秒前生成`;
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前生成`;
  return `${Math.floor(sec / 3600)} 小时前生成`;
});

function onRefreshClick(): void {
  emit('refresh');
}
</script>

<style scoped>
.ai-drawer {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 8px 4px;
}

.ai-drawer__headline {
  font-size: 16px;
  line-height: 1.6;
  color: #303133;
  margin: 0;
}

.ai-drawer__chips {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-drawer__chip {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 4px 12px;
  padding: 12px 14px;
  background: #f5f7fa;
  border-radius: 8px;
}

.ai-drawer__chip-label {
  font-size: 13px;
  color: #606266;
}

.ai-drawer__chip-value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  grid-column: 2;
  grid-row: 1 / 3;
  align-self: center;
}

.ai-drawer__chip-unit {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-left: 2px;
}

.ai-drawer__chip-delta {
  grid-column: 1;
  font-size: 12px;
  color: #909399;
}

/* 趋势颜色：UP=绿 / DOWN=红 / FLAT=灰 / NONE=浅灰（spec §04 chip 视觉） */
.ai-drawer__chip--up .ai-drawer__chip-value {
  color: #67c23a;
}
.ai-drawer__chip--down .ai-drawer__chip-value {
  color: #f56c6c;
}
.ai-drawer__chip--flat .ai-drawer__chip-value {
  color: #606266;
}
.ai-drawer__chip--none .ai-drawer__chip-value {
  color: #c0c4cc;
}

.ai-drawer__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.ai-drawer__freshness {
  font-size: 12px;
  color: #909399;
}
</style>
