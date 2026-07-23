<!--
  AiAnalysisView — v2.1 PR3 独立分析页（spec §7.3 / CLAUDE.md §11.3）。

  4 段：headline / advice / highlight / chips；mood 嵌在 highlight 段下。
  4 状态：loading / empty / rendered / degraded（template 降级）。

  数据：useAiInsightStore.loadAnalysis()。Store 已有 freshness-aware 缓存复用
  （6h TTL，CLAUDE.md §11.4），所以从首页抽屉进入本页面通常不发请求。
-->
<template>
  <div class="ai-analysis">
    <header class="page-header">
      <h1>AI 分析</h1>
      <ElButton
        :loading="store.loading"
        data-testid="ai-analysis-refresh"
        @click="onRefresh"
      >
        刷新
      </ElButton>
    </header>

    <div v-if="store.loading && !store.insight" class="loading-block">
      <ElSkeleton :rows="6" animated />
    </div>

    <TriStateEmpty
      v-else-if="!store.insight"
      test-id="ai-analysis-empty"
      description="暂无分析数据，请点击刷新重试"
    />

    <template v-else>
      <!-- 用 v-if="insight" 内嵌一层，让 vue-tsc 在其子树内把 insight 收窄为
           non-null；否则 v-else 内的多个 section 全部要重复 ?. 解引用。 -->
      <template v-if="insight">
        <!-- headline 段 -->
        <section class="section headline" data-testid="ai-analysis-headline">
          <div class="headline-row">
            <h2 class="headline-title">{{ insight.headline }}</h2>
            <ElTag
              :type="insight.source === 'llm' ? 'success' : 'info'"
              size="small"
              effect="plain"
              data-testid="ai-analysis-source-tag"
            >
              {{ insight.source === 'llm' ? 'AI 生成' : '模板生成' }}
            </ElTag>
          </div>
          <p v-if="insight.generatedAt" class="meta">
            生成于 {{ formatTime(insight.generatedAt) }} · 缓存 {{ formatAge(insight.freshnessSeconds) }} 前
          </p>
        </section>

        <!-- advice 段 -->
        <section class="section" data-testid="ai-analysis-advice" aria-label="建议">
          <h3>建议</h3>
          <p>{{ insight.advice || '（暂无建议）' }}</p>
        </section>

        <!-- highlight 段 -->
        <section class="section" data-testid="ai-analysis-highlight" aria-label="亮点">
          <h3>亮点</h3>
          <p>{{ insight.highlight || '（暂无亮点）' }}</p>
          <span v-if="insight.mood" class="mood" data-testid="ai-analysis-mood">
            心情：{{ moodLabel(insight.mood) }}
          </span>
        </section>

        <!-- chips 段 -->
        <section class="section chips" data-testid="ai-analysis-chips" aria-label="数据指标">
          <h3>今日数据</h3>
          <div class="chip-row">
            <div
              v-for="chip in insight.chips"
              :key="chip.key"
              class="chip"
              :class="`chip--${chip.trend.toLowerCase()}`"
              data-testid="ai-analysis-chip"
            >
              <span class="chip-label">{{ chip.label }}</span>
              <span class="chip-value">
                {{ chip.value }}<span v-if="chip.unit" class="chip-unit">{{ chip.unit }}</span>
              </span>
              <span v-if="chip.deltaText" class="chip-delta">{{ chip.deltaText }}</span>
            </div>
          </div>
        </section>

        <!-- template 降级提示 -->
        <ElAlert
          v-if="insight.source === 'template'"
          type="info"
          :closable="false"
          show-icon
          data-testid="ai-analysis-degraded-hint"
        >
          AI 服务暂不可用，已展示模板结果
        </ElAlert>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { ElButton, ElSkeleton, ElTag, ElAlert } from 'element-plus';
import TriStateEmpty from '@/components/TriStateEmpty.vue';
import { useAiInsightStore } from '@/stores/aiInsight';
import type { Mood } from '@/types';

const store = useAiInsightStore();

/** 取 store 中的 insight；为空时所有 section 短路（empty state 接管）。 */
const insight = computed(() => store.insight);

/** 挂载时若没缓存则拉一次（freshness-aware，由 store 内部判断 6h）。 */
onMounted(() => {
  void store.loadAnalysis();
});

/** 刷新按钮：用户主动场景，强制 POST /refresh 重新计算。 */
function onRefresh(): void {
  void store.refresh();
}

/** Mood enum → 中文标签（与后端 Mood.java 对齐：POSITIVE/NEUTRAL/CAUTIOUS）。 */
function moodLabel(m: Mood): string {
  const map: Record<Mood, string> = {
    POSITIVE: '积极',
    NEUTRAL: '平稳',
    CAUTIOUS: '谨慎',
  };
  return map[m] ?? m;
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN', { hour12: false });
}

function formatAge(secs: number): string {
  const s = Math.max(secs, 0);
  if (s < 60) return `${s} 秒`;
  if (s < 3600) return `${Math.floor(s / 60)} 分钟`;
  if (s < 86400) return `${Math.floor(s / 3600)} 小时`;
  return `${Math.floor(s / 86400)} 天`;
}
</script>

<style scoped>
.ai-analysis {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 22px;
  font-weight: 600;
  margin: 0;
  color: #303133;
}

.section {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.06);
}

.section h3 {
  font-size: 14px;
  font-weight: 600;
  color: #909399;
  margin: 0 0 12px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.headline-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.headline-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: #303133;
  flex: 1;
  min-width: 0;
}

.meta {
  color: #909399;
  font-size: 12px;
  margin: 8px 0 0;
}

.chip-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.chip {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 14px;
}

.chip-label {
  display: block;
  font-size: 12px;
  color: #606266;
}

.chip-value {
  display: block;
  font-size: 18px;
  font-weight: 600;
  margin: 4px 0;
  color: #303133;
}

.chip-unit {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-left: 2px;
}

.chip-delta {
  display: block;
  font-size: 11px;
  color: #909399;
}

.chip--up .chip-value {
  color: #67c23a;
}
.chip--down .chip-value {
  color: #f56c6c;
}

.mood {
  display: inline-block;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
}

.loading-block {
  background: var(--tri-state-loading-bg);
  border-radius: var(--tri-state-loading-radius);
  padding: 24px;
}

@media (max-width: 600px) {
  .ai-analysis {
    padding: 16px 12px;
  }
  .chip-row {
    grid-template-columns: 1fr 1fr;
  }
  .headline-title {
    font-size: 18px;
  }
}
</style>