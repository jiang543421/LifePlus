<!--
  ExpenseDetailView — 消费详情页（spec §06-expense section 5）。

  <p>路由：{@code /expenses/:id(\\d+)}；不在列表中提供"详情"按钮（ExpenseListItem 仅暴露编辑/删除），
  详情页只服务深链 / 调试场景。

  <p>加载：onMounted 调 {@code expenseApi.get(id)}；失败按 code 走 showAuthError + 重定向 /expenses。
  1003/1004 都按权限类错误处理（避免暴露存在性 — review C-2）。

  <p>操作：
  <ul>
    <li>进入编辑：把当前 item 写入 store.currentItem → store.openDialog('edit') → 跳 /expenses
        （共享同一个 ExpenseDialog 实例，由 ExpenseView 渲染）</li>
    <li>删除：ElMessageBox 二次确认 → expenseApi.delete → 跳 /expenses</li>
  </ul>
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElMessage,
  ElMessageBox,
  ElPageHeader,
} from 'element-plus';
import { ApiError } from '@/api/http';
import { expenseApi } from '@/api/expense';
import { showAuthError } from '@/utils/error';
import { useExpenseStore } from '@/stores/expense';
import { CATEGORY_LABEL } from '@/constants/expense';
import { formatAmountWithSymbol } from '@/utils/number';
import dayjs from 'dayjs';
import type { ExpenseResponse } from '@/types';

const route = useRoute();
const router = useRouter();
const store = useExpenseStore();

const item = ref<ExpenseResponse | null>(null);
const submitting = ref(false);

async function load(): Promise<void> {
  const id = Number(route.params.id);
  try {
    item.value = await expenseApi.get(id);
  } catch (e: unknown) {
    if (e instanceof ApiError) showAuthError(e.code);
    await router.replace('/expenses');
  }
}

onMounted(load);

function goBack(): void {
  void router.replace('/expenses');
}

/** 进入编辑：写 store + 跳列表（ExpenseView 渲染同一个 dialog）。 */
function onEdit(): void {
  if (!item.value) return;
  store.openDialog('edit', item.value);
  void router.replace('/expenses');
}

async function onDelete(): Promise<void> {
  if (!item.value) return;
  try {
    await ElMessageBox.confirm('确定删除这笔消费？', '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return; // 用户取消
  }
  submitting.value = true;
  try {
    await expenseApi.delete(item.value.id);
    ElMessage.success('已删除');
    await router.replace('/expenses');
  } catch (e: unknown) {
    if (e instanceof ApiError) showAuthError(e.code);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="expense-detail" data-testid="expense-detail">
    <ElPageHeader
      title="返回列表"
      content="消费详情"
      @back="goBack"
    />
    <div v-if="item" class="expense-detail__content" data-testid="expense-detail-content">
      <ElDescriptions :column="1" border>
        <ElDescriptionsItem label="金额">
          <span data-testid="expense-detail-amount">{{ formatAmountWithSymbol(item.amount) }}</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="分类">
          <span data-testid="expense-detail-category">{{ CATEGORY_LABEL[item.category] }}</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="备注">
          <span data-testid="expense-detail-note">{{ item.note ?? '—' }}</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="发生时间">
          <span data-testid="expense-detail-occurred-at">
            {{ dayjs(item.occurredAt).format('YYYY-MM-DD HH:mm') }}
          </span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="创建于">
          <span data-testid="expense-detail-created-at">
            {{ dayjs(item.createdAt).format('YYYY-MM-DD HH:mm') }}
          </span>
        </ElDescriptionsItem>
      </ElDescriptions>
      <div class="expense-detail__actions">
        <ElButton data-testid="expense-detail-edit-btn" @click="onEdit">进入编辑</ElButton>
        <ElButton
          type="danger"
          :loading="submitting"
          data-testid="expense-detail-delete-btn"
          @click="onDelete"
        >
          删除
        </ElButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.expense-detail {
  padding: 16px;
  max-width: 720px;
  margin: 0 auto;
}
.expense-detail__content {
  margin-top: 16px;
}
.expense-detail__actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}
</style>
