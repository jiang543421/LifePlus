<script setup lang="ts">
import { computed } from 'vue';
import { ElTag } from 'element-plus';
import type { TaskListItem, TaskStatus } from '@/types';
import { TaskStatusValue } from '@/types';

/**
 * 任务列表项（spec §04 §3）。
 *
 * <p>展示层组件：纯 props 输入 + emit 事件，**不**直接调 API；
 * 列表行点击进详情交给父视图（点击事件本身不在这层 emit，避免嵌套路由逻辑）。
 */
const props = defineProps<{ task: TaskListItem }>();
const emit = defineEmits<{
  (e: 'change-status', status: TaskStatus): void;
  (e: 'edit', id: number): void;
  (e: 'remove', id: number): void;
}>();

const statusType = computed(() => {
  switch (props.task.status) {
    case TaskStatusValue.TODO:
      return 'warning' as const;
    case TaskStatusValue.DONE:
      return 'success' as const;
    case TaskStatusValue.CANCELLED:
      return 'info' as const;
    default:
      return 'info' as const;
  }
});

const statusLabel = computed(() => {
  switch (props.task.status) {
    case TaskStatusValue.TODO:
      return '待办';
    case TaskStatusValue.DONE:
      return '已完成';
    case TaskStatusValue.CANCELLED:
      return '已取消';
    default:
      return '未知';
  }
});

const priorityLabel = computed(() => {
  switch (props.task.priority) {
    case 1:
      return '低';
    case 2:
      return '中';
    case 3:
      return '高';
    default:
      return '';
  }
});

function toggleStatus(): void {
  // TODO → DONE，DONE → TODO（按一下直接循环）；CANCELLED 不通过这里切换（详情页操作）。
  const next: TaskStatus =
    props.task.status === TaskStatusValue.TODO ? TaskStatusValue.DONE : TaskStatusValue.TODO;
  emit('change-status', next);
}
</script>

<template>
  <div class="task-item">
    <el-tag :type="statusType" size="small" class="status-tag">{{ statusLabel }}</el-tag>
    <div class="title" :title="task.title">{{ task.title }}</div>
    <div v-if="priorityLabel" class="priority" :data-priority="task.priority">P{{ priorityLabel }}</div>
    <div v-if="task.dueDate" class="due">📅 {{ task.dueDate }}</div>
    <div v-if="task.tag" class="tag">#{{ task.tag }}</div>
    <div class="actions">
      <button
        v-if="task.status !== TaskStatusValue.CANCELLED"
        class="link-btn"
        data-testid="toggle-status"
        @click="toggleStatus"
      >
        {{ task.status === TaskStatusValue.TODO ? '完成' : '重做' }}
      </button>
      <button class="link-btn" data-testid="edit" @click="emit('edit', task.id)">编辑</button>
      <button class="link-btn danger" data-testid="remove" @click="emit('remove', task.id)">删除</button>
    </div>
  </div>
</template>

<style scoped>
.task-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.task-item:hover {
  background: var(--el-fill-color-light);
}
.title {
  flex: 1;
  font-size: 15px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.priority {
  font-size: 12px;
  color: var(--el-color-warning);
  min-width: 36px;
}
.due,
.tag {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 80px;
}
.actions {
  display: flex;
  gap: 8px;
}
.link-btn {
  background: none;
  border: none;
  padding: 4px 8px;
  cursor: pointer;
  color: var(--el-color-primary);
  font-size: 13px;
}
.link-btn:hover {
  text-decoration: underline;
}
.link-btn.danger {
  color: var(--el-color-danger);
}
.status-tag {
  min-width: 56px;
  text-align: center;
}
</style>