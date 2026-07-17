<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox, ElButton, ElTag } from 'element-plus';
import { planApi } from '@/api/plan';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import { formatInShanghai } from '@/utils/time';
import { PlanAllDayValue } from '@/types';
import type { PlanCreateRequest, PlanResponse, PlanUpdateRequest } from '@/types';
import EventDialog from '@/components/EventDialog.vue';
import PlanTaskList from '@/components/PlanTaskList.vue';

/**
 * 事件详情页（spec §04 §5 + PRD PLAN-2/PLAN-3）。
 *
 * <p>直接经 planApi 读写（与 TaskDetailView 一致，不经 store —— 详情不进列表缓存）。
 * 编辑复用 EventDialog（mode=edit）；越权 1003 / 不存在 1004 直接回列表。
 */
const route = useRoute();
const router = useRouter();

const plan = ref<PlanResponse | null>(null);
const loading = ref(false);
const dialogOpen = ref(false);
const submitting = ref(false);

const planId = computed<number | null>(() => {
  const raw = route.params.id;
  if (typeof raw !== 'string') return null;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : null;
});

const isAllDay = computed(() => plan.value?.allDay === PlanAllDayValue.ALL_DAY);

/** 起止时间展示：全天显示日期区间，否则显示完整 datetime。 */
const timeText = computed(() => {
  if (!plan.value) return '';
  const start = dayjs(plan.value.startTime);
  const end = dayjs(plan.value.endTime);
  if (isAllDay.value) {
    const s = start.format('YYYY-MM-DD');
    const e = end.format('YYYY-MM-DD');
    return s === e ? s : `${s} – ${e}`;
  }
  return `${start.format('YYYY-MM-DD HH:mm')} – ${end.format('YYYY-MM-DD HH:mm')}`;
});

const reminderText = computed(() => {
  const m = plan.value?.reminderMin;
  if (m === null || m === undefined) return '不提醒';
  return m >= 60 ? `提前 ${m / 60} 小时` : `提前 ${m} 分钟`;
});

async function loadPlan(): Promise<void> {
  const id = planId.value;
  if (id === null) {
    showAuthError(1001);
    return;
  }
  loading.value = true;
  try {
    plan.value = await planApi.get(id);
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 1003 || e.code === 1004) {
        showAuthError(e.code);
        await router.replace('/plans');
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

onMounted(loadPlan);

function startEdit(): void {
  if (plan.value) dialogOpen.value = true;
}

async function onEditSubmit(payload: PlanCreateRequest | PlanUpdateRequest): Promise<void> {
  if (!plan.value) return;
  submitting.value = true;
  try {
    await planApi.update(plan.value.id, payload as PlanUpdateRequest);
    dialogOpen.value = false;
    ElMessage({ message: '已保存', type: 'success' });
    await loadPlan();
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
    else ElMessage({ message: '保存失败', type: 'error' });
  } finally {
    submitting.value = false;
  }
}

async function removePlan(): Promise<void> {
  if (!plan.value) return;
  try {
    await ElMessageBox.confirm('确定删除该事件？此操作不可恢复', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return; // 用户取消
  }
  try {
    await planApi.delete(plan.value.id);
    ElMessage({ message: '已删除', type: 'success' });
    await router.replace('/plans');
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
  }
}

function goBack(): void {
  void router.push('/plans');
}

function onTaskOpen(taskId: number): void {
  void router.push(`/tasks/${taskId}`);
}
</script>

<template>
  <main class="plan-detail-view">
    <header class="header">
      <el-button text @click="goBack">← 返回日历</el-button>
    </header>

    <div v-if="loading" class="state">加载中…</div>
    <div v-else-if="!plan" class="state">事件不存在</div>
    <article v-else class="content" data-testid="plan-detail">
      <div class="title-row">
        <h1 class="title">{{ plan.title }}</h1>
        <el-tag v-if="isAllDay" type="success" data-testid="allday-badge">全天</el-tag>
      </div>

      <dl class="meta">
        <div class="row">
          <dt>时间</dt>
          <dd data-testid="time-text">{{ timeText }}</dd>
        </div>
        <div class="row">
          <dt>地点</dt>
          <dd data-testid="location-text">{{ plan.location ?? '—' }}</dd>
        </div>
        <div class="row">
          <dt>提醒</dt>
          <dd data-testid="reminder-text">{{ reminderText }}</dd>
        </div>
        <div class="row">
          <dt>备注</dt>
          <dd data-testid="note-text">{{ plan.note ?? '—' }}</dd>
        </div>
        <div class="row">
          <dt>创建时间</dt>
          <dd>{{ formatInShanghai(plan.createdAt) }}</dd>
        </div>
        <div class="row">
          <dt>更新时间</dt>
          <dd>{{ formatInShanghai(plan.updatedAt) }}</dd>
        </div>
      </dl>

      <div class="actions bottom-actions">
        <el-button type="primary" data-testid="edit-start" @click="startEdit">编辑</el-button>
        <el-button type="danger" data-testid="delete-btn" @click="removePlan">删除</el-button>
      </div>

      <PlanTaskList :plan-id="plan.id" @open="onTaskOpen" />
    </article>

    <EventDialog
      v-model="dialogOpen"
      mode="edit"
      :initial="plan"
      :submitting="submitting"
      @submit="onEditSubmit"
    />
  </main>
</template>

<style scoped>
.plan-detail-view {
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
  white-space: pre-wrap;
}
.actions {
  display: flex;
  gap: 12px;
}
.bottom-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
