<!--
  TriStateError — 统一错误态组件（v1.2.6 #4.1）。

  <p>设计动机：v1.2.5 仅 DailyView 有错误态（ElEmpty + ⚠️ + 重试按钮），其它视图
  失败后只走 showAuthError toast，用户无法主动恢复。本组件统一该模式：
  <ul>
    <li>ElEmpty 渲染 + ⚠️ 替换默认图标</li>
    <li>内置"重试"按钮（可改名），emit('retry') 给父组件</li>
    <li>外层 + retry 按钮 testId 命名由父组件提供</li>
  </ul>

  <p>视觉与 v1.2.5 DailyView 错误态 1:1 对齐，保持用户习惯。
-->
<template>
  <div :data-testid="testId" class="tri-state-error">
    <ElEmpty :description="description">
      <template #image>
        <div class="tri-state-error__icon">⚠️</div>
      </template>
      <template #default>
        <ElButton
          type="primary"
          :data-testid="`${testId}-retry`"
          @click="onRetry"
        >
          {{ retryLabel }}
        </ElButton>
      </template>
    </ElEmpty>
  </div>
</template>

<script setup lang="ts">
import { ElButton, ElEmpty } from 'element-plus';

/**
 * 公共 props。
 *
 * - `description`：错误文案；默认「暂时无法获取数据，请稍后重试」沿用 v1.2.5 DailyView。
 * - `testId`：外层容器 testId；retry 按钮 testId 自动拼接 `${testId}-retry`。
 * - `retryLabel`：重试按钮文案；默认「重试」，未来可改为「重新加载」等。
 */
interface Props {
  description?: string;
  testId?: string;
  retryLabel?: string;
}

withDefaults(defineProps<Props>(), {
  description: '暂时无法获取数据，请稍后重试',
  testId: 'tristate-error',
  retryLabel: '重试',
});

const emit = defineEmits<{
  /** 重试按钮点击事件；父组件重新触发 fetch / store.fetchXxx。 */
  (e: 'retry'): void;
}>();

function onRetry(): void {
  emit('retry');
}
</script>

<style scoped>
.tri-state-error {
  width: 100%;
}

/* ⚠️ icon 容器 — 与 v1.2.5 DailyView 错误态视觉一致 */
.tri-state-error__icon {
  font-size: 48px;
  line-height: 1;
  margin-bottom: 8px;
}
</style>