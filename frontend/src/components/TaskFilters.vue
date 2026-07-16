<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import { ElSelect, ElOption, ElInput, ElDatePicker, ElButton } from 'element-plus';
import type { TaskFilter, TaskStatus, TaskPriority } from '@/types';
import { TaskStatusValue, TaskPriorityValue } from '@/types';

/**
 * 任务筛选条（spec §04 §3）。
 *
 * <p>props.filter 是 v-model 受控对象（page/size 由父视图管理）；
 * 子组件只在过滤维度变化时 emit `update:filter`（含 page=1 隐式重置在父层 setFilter 完成）。
 */
const props = defineProps<{ filter: TaskFilter }>();
const emit = defineEmits<{
  (e: 'update:filter', patch: Partial<Omit<TaskFilter, 'page' | 'size'>>): void;
  (e: 'reset'): void;
}>();

// 内部副本：避免直接 mutate props.filter（immutability 约束）
const local = reactive({
  status: props.filter.status,
  priority: props.filter.priority,
  tag: props.filter.tag ?? '',
  dueFrom: props.filter.dueFrom ?? '',
  dueTo: props.filter.dueTo ?? '',
});

// 父层外部重置 filter（如路由切换） → 同步本地副本
watch(
  () => props.filter,
  (next) => {
    local.status = next.status;
    local.priority = next.priority;
    local.tag = next.tag ?? '';
    local.dueFrom = next.dueFrom ?? '';
    local.dueTo = next.dueTo ?? '';
  },
  { deep: true },
);

const statusOptions = [
  { value: TaskStatusValue.TODO, label: '待办' },
  { value: TaskStatusValue.DONE, label: '已完成' },
  { value: TaskStatusValue.CANCELLED, label: '已取消' },
];

const priorityOptions = [
  { value: TaskPriorityValue.NONE, label: '无' },
  { value: TaskPriorityValue.LOW, label: '低' },
  { value: TaskPriorityValue.MEDIUM, label: '中' },
  { value: TaskPriorityValue.HIGH, label: '高' },
];

const statusModel = computed<TaskStatus | ''>({
  get: () => (local.status === undefined ? '' : (local.status as TaskStatus)),
  set: (v) => {
    local.status = v === '' ? undefined : (v as TaskStatus);
    emitPatch();
  },
});

const priorityModel = computed<TaskPriority | ''>({
  get: () => (local.priority === undefined ? '' : (local.priority as TaskPriority)),
  set: (v) => {
    local.priority = v === '' ? undefined : (v as TaskPriority);
    emitPatch();
  },
});

function onTagInput(value: string): void {
  local.tag = value;
  emitPatch();
}

function onDateChange(field: 'dueFrom' | 'dueTo', value: string | null): void {
  local[field] = value ?? '';
  emitPatch();
}

function emitPatch(): void {
  const patch: Partial<Omit<TaskFilter, 'page' | 'size'>> = {};
  if (local.status !== undefined) patch.status = local.status;
  if (local.priority !== undefined) patch.priority = local.priority;
  if (local.tag.trim()) patch.tag = local.tag.trim();
  if (local.dueFrom) patch.dueFrom = local.dueFrom;
  if (local.dueTo) patch.dueTo = local.dueTo;
  emit('update:filter', patch);
}

function onReset(): void {
  local.status = undefined;
  local.priority = undefined;
  local.tag = '';
  local.dueFrom = '';
  local.dueTo = '';
  emit('reset');
}
</script>

<template>
  <div class="task-filters" data-testid="task-filters">
    <div class="field" data-testid="filter-status">
      <label class="label">状态</label>
      <el-select
        v-model="statusModel"
        placeholder="全部"
        clearable
        style="width: 130px"
      >
        <el-option v-for="o in statusOptions" :key="o.value" :value="o.value" :label="o.label" />
      </el-select>
    </div>

    <div class="field" data-testid="filter-priority">
      <label class="label">优先级</label>
      <el-select
        v-model="priorityModel"
        placeholder="全部"
        clearable
        style="width: 110px"
      >
        <el-option v-for="o in priorityOptions" :key="o.value" :value="o.value" :label="o.label" />
      </el-select>
    </div>

    <div class="field" data-testid="filter-tag">
      <label class="label">标签</label>
      <el-input
        :model-value="local.tag"
        placeholder="如 work"
        clearable
        style="width: 160px"
        @update:model-value="onTagInput"
      />
    </div>

    <div class="field" data-testid="filter-due-from">
      <label class="label">截止从</label>
      <el-date-picker
        :model-value="local.dueFrom"
        type="date"
        placeholder="开始日期"
        value-format="YYYY-MM-DD"
        style="width: 150px"
        @update:model-value="(v: string | null) => onDateChange('dueFrom', v)"
      />
    </div>

    <div class="field" data-testid="filter-due-to">
      <label class="label">至</label>
      <el-date-picker
        :model-value="local.dueTo"
        type="date"
        placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 150px"
        @update:model-value="(v: string | null) => onDateChange('dueTo', v)"
      />
    </div>

    <el-button data-testid="filter-reset" @click="onReset">重置</el-button>
  </div>
</template>

<style scoped>
.task-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--el-fill-color-blank);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.field {
  display: flex;
  align-items: center;
  gap: 6px;
}
.label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>