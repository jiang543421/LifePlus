<!--
  HomeView — 首页仪表盘（spec §04 §4 + ui-prototype 04，Phase 5 AI 接入）。

  结构：
  - TopBar：全宽顶栏（顶栏内部已含汉堡响应式）
  - 问候头：时段问候 + 用户名 + 日期行
  - 6 张卡网格：响应式 1/2/3 列（xs=24 sm=12 md=8）
  - AI 卡（key='ai'）点击 → 走 aiApi.today() → 抽屉展示
  - 其余占位卡点击 → ElMessage.warning('即将上线')

  数据源：
  - HOME_CARDS：6 张冻结卡（types/home.ts）
  - authStore.greetingName（已有）
  - aiApi.today() / refresh()（src/api/ai.ts）

  Phase 5 仅做最小闭环：AI 卡真实联调 + 抽屉 + 错误 Toast，
  其他 5 张占位卡保持原占位文案。
-->
<template>
  <div class="home-view">
    <TopBar />

    <main class="home-view__main">
      <header class="home-view__greeting">
        <h1 class="home-view__greeting-title">
          {{ greeting() }}，{{ auth.greetingName }}
        </h1>
        <p class="home-view__greeting-date">{{ todayDateLine() }}</p>
      </header>

      <ElRow :gutter="20" class="home-view__grid" data-testid="home-view-grid">
        <ElCol
          v-for="card in HOME_CARDS"
          :key="card.key"
          :xs="24"
          :sm="12"
          :md="8"
          class="home-view__grid-col"
        >
          <div :data-testid="`home-card-${card.key}`">
            <ModuleCard
              :title="card.title"
              :icon="card.icon"
              :to="card.to"
              :placeholder="card.kind === 'placeholder'"
              @placeholder-click="() => onPlaceholderClick(card.key)"
            />
          </div>
        </ElCol>
      </ElRow>

      <AiDrawer
        v-model:show="aiDrawerOpen"
        :insight="aiInsight"
        :refreshing="aiRefreshing"
        @refresh="onAiRefresh"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage, ElRow, ElCol } from 'element-plus';
import TopBar from '@/components/TopBar.vue';
import ModuleCard from '@/components/ModuleCard.vue';
import AiDrawer from '@/components/AiDrawer.vue';
import { HOME_CARDS } from '@/types/home';
import {
  AiInsightResponse,
  AuthErrorCode,
  ExtraErrorCode,
} from '@/types';
import { useAuthStore } from '@/stores/auth';
import { greeting, todayDateLine } from '@/utils/time';
import { aiApi } from '@/api/ai';
import { ApiError } from '@/api/http';

const auth = useAuthStore();

/** AI 卡交互状态（抽屉 + 数据 + 加载）。 */
const aiDrawerOpen = ref(false);
const aiInsight = ref<AiInsightResponse | null>(null);
const aiRefreshing = ref(false);

/** 占位卡统一入口；当前只有 'ai' 走真实联调，其余保留「即将上线」。 */
function onPlaceholderClick(cardKey: string): void {
  if (cardKey === 'ai') {
    void openAiInsight();
    return;
  }
  ElMessage.warning('即将上线');
}

async function openAiInsight(): Promise<void> {
  aiDrawerOpen.value = true;
  if (aiInsight.value) {
    return;
  }
  try {
    aiInsight.value = await aiApi.today();
  } catch (error: unknown) {
    handleAiError(error, '加载');
  }
}

async function onAiRefresh(): Promise<void> {
  aiRefreshing.value = true;
  try {
    aiInsight.value = await aiApi.refresh();
  } catch (error: unknown) {
    handleAiError(error, '刷新');
  } finally {
    aiRefreshing.value = false;
  }
}

/**
 * 把 aiApi 抛出的 ApiError 翻译成 user-friendly Toast：
 * - 1501（AI_DEGRADED）→ 「AI 洞察数据暂时不可用，请稍后重试」（原占位文案 + Toast）
 * - 1006（限流）    → 「请求过于频繁，请稍后再试」
 * - 其它 code ≥ 0   → 后端 message
 * - code < 0        → 网络异常
 */
function handleAiError(error: unknown, actionLabel: '加载' | '刷新'): void {
  let message: string;
  if (error instanceof ApiError) {
    if (error.code === ExtraErrorCode.AiDegraded) {
      message = 'AI 洞察数据暂时不可用，请稍后重试';
    } else if (error.code === AuthErrorCode.RateLimit) {
      message = 'AI 洞察请求过于频繁，请稍后重试';
    } else if (error.code < 0) {
      // 网络异常：code=-1（http.ts 拦截器对非业务码错误的兜底）。
      // 故意不暴露 axios 原始 message，避免泄漏技术细节。
      message = `网络异常，AI 洞察${actionLabel}失败，请稍后重试`;
    } else if (error.message) {
      message = error.message;
    } else {
      message = `AI 洞察${actionLabel}失败（${error.code}）`;
    }
  } else {
    message = `AI 洞察${actionLabel}失败，请稍后重试`;
  }
  ElMessage.warning(message);
}
</script>

<style scoped>
.home-view {
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.home-view__main {
  flex: 1;
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.home-view__greeting {
  margin-bottom: 28px;
}

.home-view__greeting-title {
  font-size: 26px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 6px;
}

.home-view__greeting-date {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.home-view__grid {
  margin: 0 !important;
}

.home-view__grid-col {
  margin-bottom: 20px;
}
</style>
