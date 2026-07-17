<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { ElTag } from 'element-plus';
import { useTaskStore } from '@/stores/task';
import type { TaskListItem, TaskStatus } from '@/types';
import { TaskStatusValue } from '@/types';

/**
 * 日程详情页的「关联任务」区块（spec §04 §5 + PRD F-H03）。
 *
 * <p>展示层组件：内部触发 {@code taskStore.fetchByPlan(planId)}，通过 store 状态
 * 渲染 loading / empty / error / list 四态。点击任务行 {@code emit('open', id)}
 * 由父视图决定路由跳转（避免组件内嵌路由逻辑）。
 *
 * <p>不复用 {@link TaskItem} — TaskItem 内嵌"完成/编辑/删除"动作，不适合详情页的
 * 只读浏览语义；此处自渲染最小行：状态徽章 + 标题 + 优先级 + 截止日 + 标签。
 */
const props = defineProps<{ planId: number }>();
const emit = defineEmits<{
  (e: 'open', id: number): void;
}>();

const store = useTaskStore();
const { byPlanTasks, byPlanLoading, byPlanError } = storeToRefs(store);

onMounted(() => {
  void store.fetchByPlan(props.planId);
});

watch(
  () => props.planId,
  (next) => {
    void store.fetchByPlan(next);
  },
);

const statusType = (s: TaskStatus): 'warning' | 'success' | 'info' => {
  switch (s) {
    case TaskStatusValue.TODO:
      return 'warning';
    case TaskStatusValue.DONE:
      return 'success';
    default:
      return 'info';
  }
};

const statusLabel = (s: TaskStatus): string => {
  switch (s) {
    case TaskStatusValue.TODO:
      return '待办';
    case TaskStatusValue.DONE:
      return '已完成';
    case TaskStatusValue.CANCELLED:
      return '已取消';
    default:
      return '未知';
  }
};

const priorityLabel = (p: number): string => {
  switch (p) {
    case 1:
      return '低';
    case 2:
      return '中';
    case 3:
      return '高';
    default:
      return '';
  }
};

const items = computed<TaskListItem[]>(() => byPlanTasks.value ?? []);

function onRowClick(t: TaskListItem): void {
  emit('open', t.id);
}
</script>

<template>
  <section class="related-tasks" data-testid="related-tasks">
    <h3 class="section-title">关联任务</h3>

    <div v-if="byPlanLoading && items.length === 0" class="state" data-testid="related-loading">
      加载中…
    </div>
    <div v-else-if="byPlanError" class="state error" data-testid="related-error">
      加载失败：{{ byPlanError }}
    </div>
    <div v-else-if="items.length === 0" class="state" data-testid="related-empty">
      该日程暂无关联任务
    </div>
    <ul v-else class="rows">
      <li
        v-for="t in items"
        :key="t.id"
        class="row"
        :data-task-id="t.id"
        data-testid="related-task-row"
        @click="onRowClick(t)"
      >
        <el-tag :type="statusType(t.status)" size="small" class="status-tag">
          {{ statusLabel(t.status) }}
        </el-tag>
        <span class="title">{{ t.title }}</span>
        <span v-if="priorityLabel(t.priority)" class="priority">P{{ priorityLabel(t.priority) }}</span>
        <span v-if="t.dueDate" class="due">📅 {{ t.dueDate }}</span>
        <span v-if="t.tag" class="tag">#{{ t.tag }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.related-tasks {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.state {
  padding: 24px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.state.error {
  color: var(--el-color-danger);
}
.rows {
  list-style: none;
  margin: 0;
  padding: 0;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.15s;
}
.row:hover {
  background: var(--el-fill-color-light);
}
.status-tag {
  min-width: 56px;
  text-align: center;
  flex-shrink: 0;
}
.title {
  flex: 1;
  font-size: 14px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.priority {
  font-size: 12px;
  color: var(--el-color-warning);
  min-width: 28px;
}
.due,
.tag {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 80px;
}
</style>