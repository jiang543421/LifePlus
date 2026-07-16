<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, ElButton, ElInput, ElDialog, ElForm, ElFormItem, ElSelect, ElOption, ElDatePicker } from 'element-plus';
import { useTaskStore } from '@/stores/task';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import TaskFilters from '@/components/TaskFilters.vue';
import TaskItem from '@/components/TaskItem.vue';
import type { TaskCreateRequest, TaskStatus, TaskListItem } from '@/types';
import { TaskPriorityValue } from '@/types';

const router = useRouter();
const store = useTaskStore();

const items = computed<TaskListItem[]>(() => store.list ?? []);
const total = computed(() => store.total);

async function refresh(): Promise<void> {
  const resp = await store.fetchList();
  if (!resp && store.error) {
    // store 已写 error.message；1003/1004 等统一展示
    const code = store.errorCode;
    if (code) showAuthError(code);
  }
}

onMounted(refresh);

function onFilterUpdate(patch: Partial<Omit<typeof store.filter, 'page' | 'size'>>): void {
  store.setFilter(patch);
  void refresh();
}

function onResetFilter(): void {
  store.resetFilter();
  void refresh();
}

function onPageChange(page: number): void {
  store.setPage(page);
  void refresh();
}

function onSizeChange(size: number): void {
  store.setPage(1, size);
  void refresh();
}

// 行内事件
async function onToggleStatus(item: TaskListItem, status: TaskStatus): Promise<void> {
  try {
    await store.patchStatus(item.id, status);
    void refresh();
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
  }
}

function onEdit(id: number): void {
  void router.push(`/tasks/${id}`);
}

async function onRemove(id: number): Promise<void> {
  try {
    await ElMessageBox.confirm('确定删除该任务？此操作不可恢复', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return; // 用户取消
  }
  try {
    await store.remove(id);
    ElMessage({ message: '已删除', type: 'success' });
    void refresh();
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
  }
}

// 新建任务 dialog
const dialogOpen = ref(false);
const submitting = ref(false);
const newTask = reactive<TaskCreateRequest>({
  title: '',
  priority: TaskPriorityValue.NONE,
  dueDate: null,
  tag: null,
});

function openCreate(): void {
  newTask.title = '';
  newTask.priority = TaskPriorityValue.NONE;
  newTask.dueDate = null;
  newTask.tag = null;
  dialogOpen.value = true;
}

async function submitCreate(): Promise<void> {
  const title = newTask.title.trim();
  if (!title) {
    ElMessage({ message: '请输入任务标题', type: 'warning' });
    return;
  }
  submitting.value = true;
  try {
    await store.create({
      title,
      priority: newTask.priority,
      dueDate: newTask.dueDate,
      tag: newTask.tag?.trim() || null,
    });
    dialogOpen.value = false;
    ElMessage({ message: '已创建', type: 'success' });
    void refresh();
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="task-list-view">
    <header class="header">
      <h1>任务</h1>
      <el-button type="primary" data-testid="new-task" @click="openCreate">+ 新建任务</el-button>
    </header>

    <TaskFilters :filter="store.filter" @update:filter="onFilterUpdate" @reset="onResetFilter" />

    <div v-if="store.loading" class="state">加载中…</div>
    <div v-else-if="items.length === 0" class="state empty" data-testid="empty-state">
      {{ store.hasFilter ? '没有符合条件的任务' : '还没有任务，点右上角新建一个吧' }}
    </div>
    <div v-else class="rows" data-testid="task-rows">
      <TaskItem
        v-for="t in items"
        :key="t.id"
        :task="t"
        @change-status="(s) => onToggleStatus(t, s)"
        @edit="onEdit"
        @remove="onRemove"
      />
    </div>

    <footer v-if="total > 0" class="footer">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-size="store.filter.size"
        :current-page="store.filter.page"
        :page-sizes="[10, 20, 50, 100]"
        data-testid="pagination"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </footer>

    <el-dialog v-model="dialogOpen" title="新建任务" width="480px" data-testid="new-task-dialog">
      <el-form label-position="top">
        <div data-testid="new-title">
          <el-form-item label="标题" required>
            <el-input v-model="newTask.title" placeholder="如 买菜" maxlength="200" show-word-limit />
          </el-form-item>
        </div>
        <el-form-item label="优先级">
          <el-select v-model="newTask.priority" style="width: 140px">
            <el-option :value="TaskPriorityValue.NONE" label="无" />
            <el-option :value="TaskPriorityValue.LOW" label="低" />
            <el-option :value="TaskPriorityValue.MEDIUM" label="中" />
            <el-option :value="TaskPriorityValue.HIGH" label="高" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="newTask.dueDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="newTask.tag" placeholder="如 work" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" data-testid="new-submit" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.task-list-view {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.header h1 {
  margin: 0;
  font-size: 22px;
}
.state {
  padding: 48px 16px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.rows {
  background: var(--el-fill-color-blank);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}
.footer {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>