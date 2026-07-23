<!--
  ExpenseView — 消费列表页（spec §06-expense section 5）。

  <p>布局：
  <ul>
    <li>顶部：分类过滤 + 新增按钮（顶部栏复用 TaskListView 风格）</li>
    <li>左侧（md+）：ExpenseList 列表 + 分页</li>
    <li>右侧（md+）：ExpenseSummaryCard 月汇总卡</li>
    <li>移动端（xs）：列表与汇总垂直堆叠（ElRow/ElCol 响应式）</li>
  </ul>

  <p>数据契约：
  <ul>
    <li>onMounted：fetchList（按当前 filter）+ fetchSummary（按当前 year/month）</li>
    <li>月汇总卡 change-month → 更新 filter.from/to + 重拉两份</li>
    <li>分类变化 / 分页变化 → 仅重拉列表</li>
    <li>列表行 edit 事件 → expenseApi.get(id) 取全字段 → store.openDialog('edit', item)</li>
    <li>列表行 delete 事件 → ElMessageBox 二次确认 → store.remove → refresh summary</li>
  </ul>

  <p>store.dialogVisible / dialogMode / currentItem 在 ExpenseDetailView 进入编辑时也会写，
  本视图的 ExpenseDialog 通过 v-model 响应式打开，符合"全站共用一个 dialog 实例"约定。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElCol,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElRow,
  ElSelect,
} from 'element-plus';
import { ApiError } from '@/api/http';
import { expenseApi } from '@/api/expense';
import { showAuthError } from '@/utils/error';
import { useExpenseStore } from '@/stores/expense';
import {
  CATEGORY_LABEL,
  EXPENSE_CATEGORIES,
  type ExpenseCategory,
} from '@/constants/expense';
import ExpenseList from '@/components/ExpenseList.vue';
import ExpenseSummaryCard from '@/components/ExpenseSummaryCard.vue';
import ExpenseDialog from '@/components/ExpenseDialog.vue';

const store = useExpenseStore();

/** 当前展示月份：默认本月，由 ExpenseSummaryCard 的 change-month 事件更新。 */
const now = dayjs();
const year = ref(now.year());
const month = ref(now.month() + 1);

/** 过滤区间（本月 1 号 00:00 → 本月最后一天 23:59:59.999，ISO with offset）。 */
function monthRangeISO(y: number, m: number): { from: string; to: string } {
  const start = dayjs(`${y}-${String(m).padStart(2, '0')}-01`).startOf('month');
  const end = start.endOf('month');
  return { from: start.toISOString(), to: end.toISOString() };
}

const submitting = ref(false);

/** store.fetchXxx 失败时把 errorCode 通过 showAuthError 暴露给用户。
 * 成功路径直接 return；null 表示失败。 */
async function refreshList(): Promise<void> {
  const result = await store.fetchList();
  if (result === null && store.errorCode !== null) {
    showAuthError(store.errorCode);
  }
}

async function refreshSummary(): Promise<void> {
  const result = await store.fetchSummary(year.value, month.value);
  if (result === null && store.errorCode !== null) {
    showAuthError(store.errorCode);
  }
}

onMounted(async () => {
  const r = monthRangeISO(year.value, month.value);
  store.filter.from = r.from;
  store.filter.to = r.to;
  await Promise.all([refreshList(), refreshSummary()]);
});

async function onMonthChange(y: number, m: number): Promise<void> {
  year.value = y;
  month.value = m;
  const r = monthRangeISO(y, m);
  store.filter.from = r.from;
  store.filter.to = r.to;
  await Promise.all([refreshList(), refreshSummary()]);
}

/** 分类过滤变化；v-model 已经写了 filter.category，这里只触发重拉 + 重置到第 1 页。 */
async function onCategoryChange(): Promise<void> {
  store.setPage(1);
  await refreshList();
}

/** 分页变化；同步更新 filter.page + page.current，再 fetchList。 */
async function onPageChange(page: number): Promise<void> {
  store.setPage(page);
  await refreshList();
}

/** 列表行"编辑"事件：先取全字段（spec §06-expense §5：edit 模式必须带 userId/createdAt/updatedAt），
 * 再打开 dialog（store 持有 dialogVisible/dialogMode/currentItem）。 */
async function onEdit(id: number): Promise<void> {
  try {
    const full = await expenseApi.get(id);
    store.openDialog('edit', full);
  } catch (e: unknown) {
    if (e instanceof ApiError) showAuthError(e.code);
  }
}

/** 列表行"删除"事件：二次确认 → store.remove（自动 refresh list）→ 手动 refresh summary。 */
async function onDelete(id: number): Promise<void> {
  try {
    await ElMessageBox.confirm('确定删除这笔消费？', '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return; // 用户点取消
  }
  submitting.value = true;
  try {
    await store.remove(id);
    await refreshSummary();
    ElMessage.success('已删除');
  } catch (e: unknown) {
    if (e instanceof ApiError) showAuthError(e.code);
  } finally {
    submitting.value = false;
  }
}

function onCreateClick(): void {
  store.openDialog('create');
}

/** dialog 提交成功：刷新当前月汇总（list 已被 store.create/update 内部刷新）。 */
async function onDialogSuccess(): Promise<void> {
  await refreshSummary();
  ElMessage.success('已保存');
}

/** 用于响应式 ElSelect 的 v-model setter。 */
const filterCategory = computed<ExpenseCategory | undefined>({
  get: () => store.filter.category,
  set: (v) => {
    store.filter.category = v ?? undefined;
  },
});

// 暴露 handler 给单元测试（避免直接触发 ElSelect/ElPagination 在 jsdom 下的复杂渲染分支）
defineExpose({
  onPageChange,
  onCategoryChange,
  onMonthChange,
  onEdit,
  onDelete,
  onCreateClick,
  onDialogSuccess,
});
</script>

<template>
  <div class="expense-view" data-testid="expense-view">
    <header class="expense-view__header">
      <ElSelect
        v-model="filterCategory"
        placeholder="全部分类"
        clearable
        class="expense-view__filter"
        data-testid="expense-filter-category"
        @change="onCategoryChange"
      >
        <ElOption
          v-for="c in EXPENSE_CATEGORIES"
          :key="c"
          :label="CATEGORY_LABEL[c]"
          :value="c"
        />
      </ElSelect>
      <ElButton
        type="primary"
        :loading="submitting"
        data-testid="expense-create-btn"
        @click="onCreateClick"
      >
        + 新增消费
      </ElButton>
    </header>

    <ElRow :gutter="16">
      <ElCol :xs="24" :md="16">
        <!-- v1.2.6 #3：首次加载骨架屏（loading=true && list===null）。
             refresh 阶段 list 仍保留旧数据，条件自动回落 → 渲染真实 ExpenseList + pagination，
             行为同 v1.2.5 AiDrawer。避免 list 与 pagination 在 layout 上抖动。 -->
        <div
          v-if="store.loading && store.list === null"
          class="expense-list-skeleton"
          data-testid="expense-list-skeleton"
        >
          <div
            v-for="g in 3"
            :key="g"
            class="expense-list-skeleton__day-group"
            data-testid="expense-list-skeleton-day-group"
          >
            <el-skeleton-item
              variant="text"
              style="width: 24%; height: 14px;"
              data-testid="expense-list-skeleton-day-header"
            />
            <div
              v-for="i in 3"
              :key="i"
              class="expense-list-skeleton__item"
              data-testid="expense-list-skeleton-item"
            >
              <el-skeleton-item variant="rect" style="width: 48px; height: 22px;" data-testid="expense-list-skeleton-category" />
              <el-skeleton-item variant="text" style="width: 16%;" data-testid="expense-list-skeleton-amount" />
              <el-skeleton-item variant="text" style="width: 30%;" data-testid="expense-list-skeleton-note" />
              <el-skeleton-item variant="text" style="width: 64px;" data-testid="expense-list-skeleton-time" />
            </div>
          </div>
        </div>
        <template v-else>
          <ExpenseList
            :items="store.list ?? []"
            @edit="onEdit"
            @delete="onDelete"
          />
          <ElPagination
            :current-page="store.page.current"
            :page-size="store.page.size"
            :total="store.page.total"
            layout="prev, pager, next"
            class="expense-view__pagination"
            data-testid="expense-pagination"
            @current-change="onPageChange"
          />
        </template>
      </ElCol>
      <ElCol :xs="24" :md="8">
        <ExpenseSummaryCard
          :summary="store.summary"
          :year="year"
          :month="month"
          @change-month="onMonthChange"
        />
      </ElCol>
    </ElRow>

    <ExpenseDialog
      v-model="store.dialogVisible"
      :mode="store.dialogMode"
      :item="store.currentItem"
      :submitting="submitting"
      @success="onDialogSuccess"
    />
  </div>
</template>

<style scoped>
.expense-view {
  padding: 16px;
  max-width: 1200px;
  margin: 0 auto;
}
.expense-view__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}
.expense-view__filter {
  min-width: 200px;
}
.expense-view__pagination {
  margin-top: 16px;
  justify-content: center;
  display: flex;
}

/* v1.2.6 #3：ExpenseView loading skeleton（与真实 ExpenseList 视觉对齐）。
   day-group 卡 + items 行；与真实结构 1:1，确保"骨架 → 真实"切换不抖动。 */
.expense-list-skeleton {
  display: flex;
  flex-direction: column;
}
.expense-list-skeleton__day-group {
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  padding: 10px 16px 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.expense-list-skeleton__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.expense-list-skeleton__item:last-child {
  border-bottom: none;
}
</style>
