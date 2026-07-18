<!--
  HomeView — 首页仪表盘（spec §04 §4 + ui-prototype 04）。

  结构：
  - TopBar：全宽顶栏（顶栏内部已含汉堡响应式）
  - 问候头：时段问候 + 用户名 + 日期行
  - 6 张卡网格：响应式 1/2/3 列（xs=24 sm=12 md=8）
  - 模块卡 → 路由跳转；占位卡 → ElMessage.warning('即将上线')

  数据源：
  - HOME_CARDS：6 张冻结卡（types/home.ts）
  - authStore.greetingName / isLoggedIn（已有）

  不引入新 store / 不调后端接口（MVP1 阶段首页纯展示，Phase 4 仅前端壳层）。
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
              @placeholder-click="onPlaceholderClick"
            />
          </div>
        </ElCol>
      </ElRow>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElRow, ElCol } from 'element-plus';
import TopBar from '@/components/TopBar.vue';
import ModuleCard from '@/components/ModuleCard.vue';
import { HOME_CARDS } from '@/types/home';
import { useAuthStore } from '@/stores/auth';
import { greeting, todayDateLine } from '@/utils/time';

const auth = useAuthStore();

function onPlaceholderClick(): void {
  ElMessage.warning('即将上线');
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
