<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, ElButton, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElDatePicker, ElTag } from 'element-plus';
import { taskApi } from '@/api/task';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import { formatInShanghai } from '@/utils/time';
import type { TaskResponse, TaskStatus, TaskUpdateRequest } from '@/types';
import { TaskStatusValue, TaskPriorityValue } from '@/types';

const route = useRoute();
const router = useRouter();

const task = ref<TaskResponse | null>(null);
const loading = ref(false);
const editing = ref(false);
const submitting = ref(false);
const errorCode = ref<number | null>(null);

// 编辑表单副本（null-skip：等于 undefined 的字段不送后端）
const editForm = reactive<TaskUpdateRequest>({});

const taskId = computed<number | null>(() => {
  const raw = route.params.id;
  if (typeof raw !== 'string') return null;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : null;
});

const statusLabel = computed(() => {
  if (!task.value) return '';
  switch (task.value.status) {
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

const statusType = computed(() => {
  if (!task.value) return 'info' as const;
  switch (task.value.status) {
    case TaskStatusValue.TODO:
      return 'warning' as const;
    case TaskStatusValue.DONE:
      return 'success' as const;
    default:
      return 'info' as const;
  }
});

const priorityLabel = computed(() => {
  if (!task.value) return '';
  switch (task.value.priority) {
    case 1:
      return '低';
    case 2:
      return '中';
    case 3:
      return '高';
    default:
      return '无';
  }
});

async function loadTask(): Promise<void> {
  const id = taskId.value;
  if (id === null) {
    errorCode.value = 1001;
    showAuthError(1001);
    return;
  }
  loading.value = true;
  errorCode.value = null;
  try {
    task.value = await taskApi.get(id);
  } catch (e) {
    if (e instanceof ApiError) {
      errorCode.value = e.code;
      // 1003/1004 → 直接回列表
      if (e.code === 1003 || e.code === 1004) {
        showAuthError(e.code);
        await router.replace('/tasks');
        return;
      }
      showAuthError(e.code);
    } else {
      ElMessage({ message: '加载失败', type: 'error' });
    }
  } finally {
    loading.value = false;
  }
}

onMounted(loadTask);

function startEdit(): void {
  if (!task.value) return;
  editForm.title = task.value.title;
  editForm.status = task.value.status;
  editForm.priority = task.value.priority;
  editForm.dueDate = task.value.dueDate;
  editForm.tag = task.value.tag;
  editing.value = true;
}

function cancelEdit(): void {
  editing.value = false;
}

async function saveEdit(): Promise<void> {
  if (!task.value) return;
  const id = task.value.id;
  const title = (editForm.title ?? '').trim();
  if (!title) {
    ElMessage({ message: '请输入任务标题', type: 'warning' });
    return;
  }
  submitting.value = true;
  try {
    // 仅发送变化字段（null-skip）— 与后端 null-skip 语义一致
    const req: TaskUpdateRequest = { title };
    if (editForm.status !== undefined) req.status = editForm.status;
    if (editForm.priority !== undefined) req.priority = editForm.priority;
    if (editForm.dueDate !== undefined) req.dueDate = editForm.dueDate;
    if (editForm.tag !== undefined) req.tag = editForm.tag?.trim() || null;
    await taskApi.update(id, req);
    editing.value = false;
    ElMessage({ message: '已保存', type: 'success' });
    await loadTask();
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
  } finally {
    submitting.value = false;
  }
}

async function toggleDone(): Promise<void> {
  if (!task.value) return;
  const next: TaskStatus =
    task.value.status === TaskStatusValue.DONE ? TaskStatusValue.TODO : TaskStatusValue.DONE;
  try {
    await taskApi.patchStatus(task.value.id, { status: next });
    await loadTask();
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
  }
}

async function removeTask(): Promise<void> {
  if (!task.value) return;
  try {
    await ElMessageBox.confirm('确定删除该任务？此操作不可恢复', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await taskApi.delete(task.value.id);
    ElMessage({ message: '已删除', type: 'success' });
    await router.replace('/tasks');
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
  }
}

function goBack(): void {
  void router.push('/tasks');
}
</script>

<template>
  <main class="task-detail-view">
    <header class="header">
      <el-button text @click="goBack">← 返回列表</el-button>
    </header>

    <div v-if="loading" class="state">加载中…</div>
    <div v-else-if="!task" class="state">任务不存在</div>
    <article v-else class="content" data-testid="task-detail">
      <div class="title-row">
        <h1 v-if="!editing" class="title">{{ task.title }}</h1>
        <div v-else data-testid="edit-title">
          <el-input v-model="editForm.title" maxlength="200" show-word-limit />
        </div>
        <el-tag :type="statusType" data-testid="status-badge">{{ statusLabel }}</el-tag>
      </div>

      <el-form v-if="editing" label-position="top" class="edit-form">
        <el-form-item label="状态">
          <el-select v-model="editForm.status" data-testid="edit-status" style="width: 160px">
            <el-option :value="TaskStatusValue.TODO" label="待办" />
            <el-option :value="TaskStatusValue.DONE" label="已完成" />
            <el-option :value="TaskStatusValue.CANCELLED" label="已取消" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="editForm.priority" data-testid="edit-priority" style="width: 160px">
            <el-option :value="TaskPriorityValue.NONE" label="无" />
            <el-option :value="TaskPriorityValue.LOW" label="低" />
            <el-option :value="TaskPriorityValue.MEDIUM" label="中" />
            <el-option :value="TaskPriorityValue.HIGH" label="高" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="editForm.dueDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            data-testid="edit-due"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tag" placeholder="如 work" maxlength="64" data-testid="edit-tag" />
        </el-form-item>
        <div class="actions">
          <el-button @click="cancelEdit">取消</el-button>
          <el-button type="primary" :loading="submitting" data-testid="edit-save" @click="saveEdit">保存</el-button>
        </div>
      </el-form>

      <dl v-else class="meta">
        <div class="row">
          <dt>优先级</dt>
          <dd data-testid="priority-text">{{ priorityLabel }}</dd>
        </div>
        <div class="row">
          <dt>截止日期</dt>
          <dd data-testid="due-text">{{ task.dueDate ?? '—' }}</dd>
        </div>
        <div class="row">
          <dt>标签</dt>
          <dd data-testid="tag-text">{{ task.tag ? `#${task.tag}` : '—' }}</dd>
        </div>
        <div class="row">
          <dt>创建时间</dt>
          <dd>{{ formatInShanghai(task.createdAt) }}</dd>
        </div>
        <div class="row">
          <dt>更新时间</dt>
          <dd>{{ formatInShanghai(task.updatedAt) }}</dd>
        </div>
      </dl>

      <div v-if="!editing" class="actions bottom-actions">
        <el-button v-if="task.status === TaskStatusValue.TODO" type="primary" data-testid="mark-done" @click="toggleDone">标记完成</el-button>
        <el-button v-else-if="task.status === TaskStatusValue.DONE" data-testid="mark-undone" @click="toggleDone">取消完成</el-button>
        <el-button data-testid="edit-start" @click="startEdit">编辑</el-button>
        <el-button type="danger" data-testid="delete-btn" @click="removeTask">删除</el-button>
      </div>
    </article>
  </main>
</template>

<style scoped>
.task-detail-view {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px;
}
.header {
  margin-bottom: 16px;
}
.state {
  padding: 48px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.content {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 24px;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.title {
  margin: 0;
  font-size: 22px;
  flex: 1;
}
.edit-form {
  margin-top: 12px;
}
.meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  margin: 0;
}
.row {
  display: flex;
  gap: 12px;
}
.row dt {
  width: 96px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
.row dd {
  margin: 0;
  flex: 1;
  color: var(--el-text-color-primary);
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
.bottom-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>