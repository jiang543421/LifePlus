<!--
  DietView — 饮食列表页（spec §07-diet-design §5 + §6.3）。

  <p>布局：
  <ul>
    <li>顶部：餐别过滤 + 日期切换 + 新增按钮</li>
    <li>左侧（md+，16 份宽）：DietDayGroup 按天分组列表 + 分页</li>
    <li>右侧（md+，8 份宽）：DietNutritionCard 当日营养汇总</li>
    <li>移动端（xs）：列表与汇总垂直堆叠（ElRow/ElCol 响应式）</li>
  </ul>

  <p>数据契约：
  <ul>
    <li>onMounted：fetchList（按当前 filter）+ fetchSummary（按当前 date）</li>
    <li>日期变化 / 餐别变化 / 分页变化 → 仅重拉列表；日期变化额外重拉 summary</li>
    <li>列表 edit 事件 → dietApi.get(id) 取全字段 → store.openDialog('edit', item)</li>
    <li>列表 delete 事件 → ElMessageBox 二次确认 → store.remove → refresh summary</li>
  </ul>

  <p>store.dialogVisible / dialogMode / currentItem 全站共用一个 DietDialog 实例，
  切到 DietDetailView 编辑时也会写入；本视图通过 v-model 响应式打开。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElCol,
  ElDatePicker,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElRow,
  ElSelect,
} from 'element-plus';
import { ApiError } from '@/api/http';
import { dietApi } from '@/api/diet';
import { showAuthError } from '@/utils/error';
import { useDietStore } from '@/stores/diet';
import { MEAL_LABEL, MEAL_TYPES } from '@/constants/diet';
import type { MealType } from '@/types';
import DietDayGroup from '@/components/DietDayGroup.vue';
import DietNutritionCard from '@/components/DietNutritionCard.vue';
import DietDialog from '@/components/DietDialog.vue';

const store = useDietStore();

/** 当前展示日期（YYYY-MM-DD）；默认今天。 */
const date = ref<string>(dayjs().format('YYYY-MM-DD'));

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
  const result = await store.fetchSummary(date.value);
  if (result === null && store.errorCode !== null) {
    showAuthError(store.errorCode);
  }
}

onMounted(async () => {
  // 按日期设 from/to 窗口（当天 00:00 ~ 23:59:59.999，ISO with offset）
  store.filter.from = dayjs(date.value).startOf('day').toISOString();
  store.filter.to = dayjs(date.value).endOf('day').toISOString();
  await Promise.all([refreshList(), refreshSummary()]);
});

/** 日期变化：同步更新 filter.from/to，重拉列表 + summary。 */
async function onDateChange(newDate: string | null): Promise<void> {
  if (!newDate) return;
  date.value = newDate;
  store.filter.from = dayjs(newDate).startOf('day').toISOString();
  store.filter.to = dayjs(newDate).endOf('day').toISOString();
  store.setPage(1);
  await Promise.all([refreshList(), refreshSummary()]);
}

/** 餐别过滤变化。 */
async function onMealTypeChange(): Promise<void> {
  store.setPage(1);
  await refreshList();
}

/** 分页变化。 */
async function onPageChange(page: number): Promise<void> {
  store.setPage(page);
  await refreshList();
}

/** 列表行「编辑」事件：先取全字段（DietResponse 需要 userId/createdAt/updatedAt），
 * 再打开 dialog。 */
async function onEdit(id: number): Promise<void> {
  try {
    const full = await dietApi.get(id);
    store.openDialog('edit', full);
  } catch (e: unknown) {
    if (e instanceof ApiError) showAuthError(e.code);
  }
}

/** 列表行「删除」事件：二次确认 → store.remove（自动 refresh list）→ 手动 refresh summary。 */
async function onDelete(id: number): Promise<void> {
  try {
    await ElMessageBox.confirm('确定删除这笔饮食？', '提示', {
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

/** dialog 提交成功：refresh summary（list 已被 store.create/update 内部刷新）。 */
async function onDialogSuccess(): Promise<void> {
  await refreshSummary();
  ElMessage.success('已保存');
}

/** 餐别 select v-model setter（与 ExpenseView.filterCategory 同款）。 */
const filterMealType = computed<MealType | undefined>({
  get: () => store.filter.mealType,
  set: (v) => {
    store.filter.mealType = v ?? undefined;
  },
});

// 暴露 handler 给单元测试（避免直接触发 ElSelect/ElPagination 在 jsdom 下的复杂渲染分支）
defineExpose({
  onPageChange,
  onMealTypeChange,
  onDateChange,
  onEdit,
  onDelete,
  onCreateClick,
  onDialogSuccess,
});
</script>

<template>
  <div class="diet-view" data-testid="diet-view">
    <header class="diet-view__header">
      <ElSelect
        v-model="filterMealType"
        placeholder="全部餐别"
        clearable
        class="diet-view__filter-meal"
        data-testid="diet-filter-meal"
        @change="onMealTypeChange"
      >
        <ElOption
          v-for="m in MEAL_TYPES"
          :key="m"
          :label="MEAL_LABEL[m]"
          :value="m"
        />
      </ElSelect>
      <ElDatePicker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        class="diet-view__date-picker"
        data-testid="diet-date-picker"
        @change="onDateChange"
      />
      <ElButton
        type="primary"
        :loading="submitting"
        class="diet-view__create-btn"
        data-testid="diet-create-btn"
        @click="onCreateClick"
      >
        + 新增饮食
      </ElButton>
    </header>

    <ElRow :gutter="16">
      <ElCol :xs="24" :md="16">
        <DietDayGroup
          v-for="g in store.groupedByDay"
          :key="g.day"
          :day="g.day"
          :items="g.items"
          @edit="onEdit"
          @delete="onDelete"
        />
        <div
          v-if="(store.list?.length ?? 0) === 0"
          class="diet-view__empty"
          data-testid="diet-view-empty"
        >
          当天暂无饮食记录
        </div>
        <ElPagination
          v-if="store.page.total > store.page.size"
          :current-page="store.page.current"
          :page-size="store.page.size"
          :total="store.page.total"
          layout="prev, pager, next"
          class="diet-view__pagination"
          data-testid="diet-pagination"
          @current-change="onPageChange"
        />
      </ElCol>
      <ElCol :xs="24" :md="8">
        <DietNutritionCard :summary="store.summary" />
      </ElCol>
    </ElRow>

    <DietDialog
      v-model="store.dialogVisible"
      :mode="store.dialogMode"
      :item="store.currentItem"
      :submitting="submitting"
      @success="onDialogSuccess"
    />
  </div>
</template>

<style scoped>
.diet-view {
  padding: 16px;
  max-width: 1200px;
  margin: 0 auto;
}
.diet-view__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.diet-view__filter-meal {
  min-width: 140px;
}
.diet-view__date-picker {
  width: 180px;
}
.diet-view__create-btn {
  margin-left: auto;
}
.diet-view__empty {
  padding: 48px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.diet-view__pagination {
  margin-top: 16px;
  justify-content: center;
  display: flex;
}
</style>