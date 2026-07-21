<!--
  DietDetailView — 饮食详情页（spec §07-diet-design §5 + §6.1）。

  <p>路由：{@code /diets/:id(\\d+)}；DietDayGroup 仅暴露编辑 / 删除（无「详情」按钮），
  本视图只服务深链 / 调试场景。

  <p>加载：onMounted 调 {@code dietApi.get(id)}；失败按 code 走 showAuthError + 重定向 /diets。
  1003/1004 都按权限类错误处理（避免暴露存在性 — review C-2 沿用）。

  <p>操作：
  <ul>
    <li>进入编辑：把当前 item 写入 store.currentItem → store.openDialog('edit') → 跳 /diets
        （共享同一个 DietDialog 实例，由 DietView 渲染）</li>
    <li>删除：ElMessageBox 二次确认 → dietApi.delete → 跳 /diets</li>
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
import dayjs from 'dayjs';
import { ApiError } from '@/api/http';
import { dietApi } from '@/api/diet';
import { showAuthError } from '@/utils/error';
import { useDietStore } from '@/stores/diet';
import { MEAL_LABEL } from '@/constants/diet';
import { formatAmount } from '@/utils/number';
import type { DietResponse } from '@/types';

const route = useRoute();
const router = useRouter();
const store = useDietStore();

const item = ref<DietResponse | null>(null);
const submitting = ref(false);

async function load(): Promise<void> {
  const id = Number(route.params.id);
  try {
    item.value = await dietApi.get(id);
  } catch (e: unknown) {
    if (e instanceof ApiError) showAuthError(e.code);
    await router.replace('/diets');
  }
}

onMounted(load);

function goBack(): void {
  void router.replace('/diets');
}

/** 进入编辑：写 store + 跳列表（DietView 渲染同一个 dialog）。 */
function onEdit(): void {
  if (!item.value) return;
  store.openDialog('edit', item.value);
  void router.replace('/diets');
}

async function onDelete(): Promise<void> {
  if (!item.value) return;
  try {
    await ElMessageBox.confirm('确定删除这笔饮食？', '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return; // 用户取消
  }
  submitting.value = true;
  try {
    await dietApi.delete(item.value.id);
    ElMessage.success('已删除');
    await router.replace('/diets');
  } catch (e: unknown) {
    if (e instanceof ApiError) showAuthError(e.code);
  } finally {
    submitting.value = false;
  }
}

/** 时间格式化：YYYY-MM-DD HH:mm。 */
function formatDateTime(s: string): string {
  return dayjs(s).format('YYYY-MM-DD HH:mm');
}

/** 备注占位：无备注时显示 "—"。 */
function noteText(): string {
  return item.value?.note ?? '—';
}
</script>

<template>
  <div class="diet-detail" data-testid="diet-detail">
    <ElPageHeader title="返回列表" content="饮食详情" @back="goBack" />
    <div
      v-if="item"
      class="diet-detail__content"
      data-testid="diet-detail-content"
    >
      <ElDescriptions :column="1" border>
        <ElDescriptionsItem label="食物名称">
          <span data-testid="diet-detail-name">{{ item.name }}</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="餐别">
          <span data-testid="diet-detail-meal-type">{{ MEAL_LABEL[item.mealType] }}</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="热量">
          <span data-testid="diet-detail-kcal">{{ formatAmount(item.kcal) }} kcal</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="蛋白质">
          <span data-testid="diet-detail-protein-g">{{ formatAmount(item.proteinG) }} g</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="碳水">
          <span data-testid="diet-detail-carb-g">{{ formatAmount(item.carbG) }} g</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="脂肪">
          <span data-testid="diet-detail-fat-g">{{ formatAmount(item.fatG) }} g</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="备注">
          <span data-testid="diet-detail-note">{{ noteText() }}</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="发生时间">
          <span data-testid="diet-detail-occurred-at">{{ formatDateTime(item.occurredAt) }}</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="创建于">
          <span data-testid="diet-detail-created-at">{{ formatDateTime(item.createdAt) }}</span>
        </ElDescriptionsItem>
      </ElDescriptions>
      <div class="diet-detail__actions">
        <ElButton data-testid="diet-detail-edit-btn" @click="onEdit">进入编辑</ElButton>
        <ElButton
          type="danger"
          :loading="submitting"
          data-testid="diet-detail-delete-btn"
          @click="onDelete"
        >
          删除
        </ElButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.diet-detail {
  padding: 16px;
  max-width: 720px;
  margin: 0 auto;
}
.diet-detail__content {
  margin-top: 16px;
}
.diet-detail__actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}
</style>