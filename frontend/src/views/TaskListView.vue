<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, ElButton, ElInput, ElDialog, ElForm, ElFormItem, ElSelect, ElOption, ElDatePicker } from 'element-plus';
import { useTaskStore } from '@/stores/task';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import TaskFilters from '@/components/TaskFilters.vue';
import TaskItem from '@/components/TaskItem.vue';
import TriStateEmpty from '@/components/TriStateEmpty.vue';
import TriStateError from '@/components/TriStateError.vue';
import type { TaskCreateRequest, TaskStatus, TaskListItem } from '@/types';
import { TaskPriorityValue } from '@/types';

const router = useRouter();
const store = useTaskStore();

const items = computed<TaskListItem[]>(() => store.list ?? []);
const total = computed(() => store.total);

/** v1.2.6 #4.4：错误态文案根据 errorCode 给具体场景提示；无业务码回落通用文案。 */
const errorDescription = computed<string>(() => {
  switch (store.errorCode) {
    case 1003:
      return '无权访问该任务列表';
    case 1006:
      return '操作过于频繁，请稍后再试';
    default:
      return '暂时无法获取任务列表，请稍后重试';
  }
});

async function refresh(): Promise<void> {
  const resp = await store.fetchList();
  if (!resp && store.error) {
    // store 已写 error.message；1003/1004 等统一展示
    const code = store.errorCode;
    if (code) showAuthError(code);
  }
}

/** v1.2.6 #4.4：错误态「重试」按钮 → 直接复用 refresh（与 DailyView 同款）。 */
function onRetry(): void {
  void refresh();
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
// CLAUDE.md §4.1 + Review C-5: 用整体替换而非逐字段 mutation，
// 避免 dialog 状态脏读（再次打开看到上次输入）。
const dialogOpen = ref(false);
const submitting = ref(false);

function createDefaultTask(): TaskCreateRequest {
  return {
    title: '',
    priority: TaskPriorityValue.NONE,
    dueDate: null,
    tag: null,
  };
}

const newTask = ref<TaskCreateRequest>(createDefaultTask());

function openCreate(): void {
  newTask.value = createDefaultTask();
  dialogOpen.value = true;
}

async function submitCreate(): Promise<void> {
  const title = newTask.value.title.trim();
  if (!title) {
    ElMessage({ message: '请输入任务标题', type: 'warning' });
    return;
  }
  submitting.value = true;
  try {
    await store.create({
      title,
      priority: newTask.value.priority,
      dueDate: newTask.value.dueDate,
      tag: newTask.value.tag?.trim() || null,
    });
    dialogOpen.value = false;
    // 成功后整体替换，避免下次打开 dialog 时残留陈旧字段
    newTask.value = createDefaultTask();
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

    <!-- v1.2.6 #1：首次加载骨架屏（loading=true && list===null）。
         refresh 阶段 list 仍保留旧数据，条件自动回落 → 渲染真实 rows / empty-state，行为同 v1.2.5 AiDrawer。 -->
    <div v-if="store.loading && store.list === null" class="task-list-view__skeleton" data-testid="task-list-skeleton">
      <ul class="task-list-view__skeleton-list">
        <li
          v-for="n in 6"
          :key="n"
          class="task-list-view__skeleton-item"
          data-testid="task-list-skeleton-item"
        >
          <el-skeleton-item variant="rect" style="width: 56px; height: 22px;" data-testid="task-list-skeleton-status" />
          <el-skeleton-item variant="text" style="width: 38%;" data-testid="task-list-skeleton-title" />
          <el-skeleton-item variant="text" style="width: 48px;" data-testid="task-list-skeleton-meta-priority" />
          <el-skeleton-item variant="text" style="width: 88px;" data-testid="task-list-skeleton-meta-due" />
        </li>
      </ul>
    </div>
    <!-- v1.2.6 #4.4：错误态（首次加载失败且 list 为空）→ TriStateError 重试。 -->
    <TriStateError
      v-else-if="store.error && store.list === null"
      test-id="task-list-error"
      :description="errorDescription"
      @retry="onRetry"
    />
    <TriStateEmpty
      v-else-if="items.length === 0"
      test-id="task-list-empty"
      :description="store.hasFilter ? '没有符合条件的任务' : '还没有任务，点右上角新建一个吧'"
    />
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

/* v1.2.6 #1：TaskListView loading skeleton 容器 + 行模板。
   视觉密度与真实 .rows 保持一致（48px 上下 padding + 6 行）。 */
.task-list-view__skeleton {
  background: var(--el-fill-color-blank);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  padding: 4px 0;
}
.task-list-view__skeleton-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.task-list-view__skeleton-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.task-list-view__skeleton-item:last-child {
  border-bottom: none;
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