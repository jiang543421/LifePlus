<!--
  TaskMetricsCard — 日报任务指标卡（spec §08-daily-report-design §7.2）。

  <p>展示 3 段：
  <ul>
    <li>顶部："完成 N / M" 大字号</li>
    <li>中部：el-progress 完成率进度条</li>
    <li>底部：statusDistribution 三项徽章（TODO / DONE / CANCELLED）</li>
  </ul>

  <p>Pure presentational；父视图 DailyView 持有 task 数据，本组件不做 fetch。
-->
<script setup lang="ts">
import { computed } from 'vue';
import { ElProgress, ElTag } from 'element-plus';
import type { TaskMetrics } from '@/types';

defineProps<{
  task: TaskMetrics;
}>();

/** 完成率百分比（0..100 的整数）。el-progress 用整数百分比。 */
function percentText(completed: number, total: number): string {
  if (total === 0) return '0%';
  return `${Math.round((completed / total) * 100)}%`;
}

/** statusDistribution 三键固定顺序（与后端 TaskMetrics statusDistribution 对齐）。 */
const STATUS_KEYS = ['TODO', 'DONE', 'CANCELLED'] as const;

/** statusDistribution 中文 label（spec §04-frontend TaskStatus 字面值）。 */
const STATUS_LABEL: Record<string, string> = {
  TODO: '待办',
  DONE: '已完成',
  CANCELLED: '已取消',
};

/** statusDistribution 颜色（el-tag type）。 */
const STATUS_TYPE: Record<string, 'info' | 'success' | 'warning'> = {
  TODO: 'info',
  DONE: 'success',
  CANCELLED: 'warning',
};
</script>

<template>
  <div class="task-card" data-testid="daily-task-card">
    <header class="task-card__header">
      <h3 class="task-card__title">任务</h3>
    </header>

    <div class="task-card__summary" data-testid="daily-task-summary">
      完成 {{ task.completedCount }} / {{ task.totalCount }}
    </div>

    <div class="task-card__progress" data-testid="daily-task-progress">
      <ElProgress
        :percentage="task.totalCount === 0 ? 0 : Math.round((task.completedCount / task.totalCount) * 100)"
        :stroke-width="12"
        :show-text="true"
        :format="() => percentText(task.completedCount, task.totalCount)"
      />
    </div>

    <ul class="task-card__dist" data-testid="daily-task-status-dist">
      <li v-for="key in STATUS_KEYS" :key="key" class="task-card__dist-item">
        <ElTag :type="STATUS_TYPE[key]" size="small">
          {{ STATUS_LABEL[key] }} {{ task.statusDistribution[key] ?? 0 }} ({{ key }})
        </ElTag>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.task-card {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.task-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.task-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.task-card__summary {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}
.task-card__dist {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  list-style: none;
  padding: 0;
  margin: 0;
}
.task-card__dist-item {
  display: inline-flex;
}
</style>
