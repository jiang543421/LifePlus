import { defineStore } from 'pinia';
import { aiApi } from '@/api/ai';
import type { AiInsightResponse } from '@/types';
import { ApiError } from '@/api/http';

/**
 * AI 洞察缓存 TTL（与后端 CLAUDE.md §11.4 对齐：v2.1 6h = 21600s）。
 * 仅用于 store 层 freshness-aware reuse；真正权威的 TTL 由后端 Redis 写入。
 */
const CACHE_TTL_SECONDS = 6 * 60 * 60;

interface AiInsightState {
  /** 最近一次成功获取的洞察；null = 未拉过或最近一次失败。 */
  insight: AiInsightResponse | null;
  loading: boolean;
  error: string | null;
  /** 最近一次失败的 ApiError.code；null = 无业务码（如 -1 网络错误）。 */
  errorCode: number | null;
}

/**
 * AI 洞察 store（v2.1 PR3）。
 *
 * <p>职责：
 * <ul>
 *   <li>首页 {@link loadToday}：拉 /today 简化卡片数据</li>
 *   <li>独立分析页 {@link loadAnalysis}：若缓存新鲜（< 6h）复用，否则拉 /analysis</li>
 *   <li>{@link refresh}：POST /refresh 强制重算并覆写缓存</li>
 * </ul>
 *
 * <p>设计原则（与 {@code stores/task.ts} 一致）：
 * <ul>
 *   <li>不持久化到 localStorage — 首页 / 独立分析页生命周期不同，跨页面保留无价值</li>
 *   <li>失败时保留旧 insight，写 error + errorCode；视图层决定 toast / fallback</li>
 *   <li>同后端"无新表"硬约束：缓存只在内存，不写 IndexedDB / localStorage</li>
 * </ul>
 */
export const useAiInsightStore = defineStore('aiInsight', {
  state: (): AiInsightState => ({
    insight: null,
    loading: false,
    error: null,
    errorCode: null,
  }),
  getters: {
    /**
     * 当前缓存是否"新鲜"（< CACHE_TTL_SECONDS）。
     * 用于 {@link loadAnalysis} 决定走本地缓存还是再发请求，
     * 避免打开独立分析页时无意义再打一次 /today 同样的后端缓存。
     */
    isFresh: (s): boolean => {
      if (!s.insight) return false;
      return s.insight.freshnessSeconds >= 0 && s.insight.freshnessSeconds < CACHE_TTL_SECONDS;
    },
  },
  actions: {
    /** 拉取 /today（首页卡片入口）。失败保留旧 insight。 */
    async loadToday(): Promise<AiInsightResponse | null> {
      this.loading = true;
      this.error = null;
      this.errorCode = null;
      try {
        const r = await aiApi.today();
        this.insight = r;
        return r;
      } catch (e: unknown) {
        this.captureError(e);
        return null;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 拉取 /analysis（v2.1 独立分析页入口）。
     *
     * <p>若已有缓存且 {@link isFresh}，直接复用不发请求；
     * 否则发请求拿到完整 v2.1 字段（source / advice / highlight / mood / llmMeta）。
     */
    async loadAnalysis(): Promise<AiInsightResponse | null> {
      if (this.isFresh && this.insight) {
        return this.insight;
      }
      this.loading = true;
      this.error = null;
      this.errorCode = null;
      try {
        const r = await aiApi.analysis();
        this.insight = r;
        return r;
      } catch (e: unknown) {
        this.captureError(e);
        return null;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 强制刷新（POST /refresh）。
     * 失败保留旧 insight，由视图层 toast 提示"刷新失败，请稍后重试"。
     */
    async refresh(): Promise<AiInsightResponse | null> {
      this.loading = true;
      this.error = null;
      this.errorCode = null;
      try {
        const r = await aiApi.refresh();
        this.insight = r;
        return r;
      } catch (e: unknown) {
        this.captureError(e);
        return null;
      } finally {
        this.loading = false;
      }
    },

    /** 视图层切换时清空状态（不持久化）。 */
    clear(): void {
      this.insight = null;
      this.error = null;
      this.errorCode = null;
    },

    /** 统一错误捕获：与 stores/task.ts 同模式（errorCode 解耦自 error message）。 */
    captureError(e: unknown): void {
      if (e instanceof ApiError) {
        this.error = e.message;
        this.errorCode = e.code;
      } else {
        this.error = e instanceof Error ? e.message : 'unknown error';
        this.errorCode = null;
      }
    },
  },
});