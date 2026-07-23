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
          <div :data-testid="`home-card-${card.key}`" class="home-view__card-wrapper">
            <ModuleCard
              :title="card.title"
              :icon="card.icon"
              :to="card.to"
              :placeholder="card.kind === 'placeholder'"
              @placeholder-click="() => onPlaceholderClick(card.key)"
            />
            <!-- v2.1 PR3：AI 卡右上角 source 角标（spec §7.3 / CLAUDE.md §11.3）。
                 加载成功且后端 source 字段存在时显示；点击 AI 卡 → 抽屉打开时
                 已可在 AiDrawer 顶部看到完整 loading skeleton，因此卡本体不再
                 重复渲染骨架屏。 -->
            <span
              v-if="card.key === 'ai' && aiInsight?.source"
              :class="['home-view__source-badge', `home-view__source-badge--${aiInsight.source}`]"
              :data-testid="`home-card-source-badge`"
              role="status"
              :aria-label="aiInsight.source === 'llm' ? 'AI 智能生成' : '模板生成'"
            >
              {{ aiInsight.source === 'llm' ? 'AI 智能' : '模板' }}
            </span>
          </div>
        </ElCol>
      </ElRow>

      <AiDrawer
        v-model:show="aiDrawerOpen"
        :insight="aiInsight"
        :refreshing="aiRefreshing"
        @refresh="onAiRefresh"
        @open-analysis="onAiOpenAnalysis"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
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
const router = useRouter();

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
 * v2.1 PR3：抽屉内点击「查看完整分析 →」→ 关抽屉 + 跳独立分析页。
 * 独立页本身在 Task 24 创建并在 Task 25 加路由；此处先 emit handler。
 */
function onAiOpenAnalysis(): void {
  aiDrawerOpen.value = false;
  void router.push({ name: 'ai-analysis' }).catch(() => {
    // 路由尚未注册（PR3 Task 25 落地前）静默吞掉；后续 Task 25 后会真正跳转。
  });
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

/* v2.1 PR3：AI 卡 source 角标（LLM 绿色 / 模板灰色，绝对定位右上角）。 */
.home-view__card-wrapper {
  position: relative;
}

.home-view__source-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 999px;
  line-height: 1.5;
  pointer-events: none;
  /* 不阻挡卡片 click；视觉权重低，避免与 ModuleCard 标题争抢注意力 */
}

.home-view__source-badge--llm {
  background: #e7f5ec;
  color: #2c9c5b;
}

.home-view__source-badge--template {
  background: #eef0f3;
  color: #606266;
}
</style>
