import { defineStore } from 'pinia';
import { aiTrendApi, type TrendWindowDays } from '@/api/ai-trend';
import { ApiError } from '@/api/http';
import type { AiTrendResponse } from '@/types';

/**
 * AI 趋势图 store（spec §v2.2 trend / CLAUDE.md §11.1 无新表）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>按 window 维度缓存 3 个槽位（7/14/30），用户切换档位时各档独立保留</li>
 *   <li>Redis 6h TTL 由后端控制；前端按窗口缓存避免重复打点</li>
 *   <li>失败时保留旧 trend 槽位，写 error + errorCode；视图层决定 toast / fallback</li>
 *   <li>不持久化到 localStorage — 跨页面保留无价值，重进页面重新拉</li>
 * </ul>
 *
 * <p>与 {@code stores/aiInsight.ts} 的差异：insight 是单条全局缓存，
 * trend 是按 window 分桶；切换 7↔14↔30 不应互相覆盖。
 */
type TrendByWindow = Record<TrendWindowDays, AiTrendResponse | null>;

interface AiTrendState {
  trend: TrendByWindow;
  loading: boolean;
  error: string | null;
  errorCode: number | null;
}

export const useAiTrendStore = defineStore('aiTrend', {
  state: (): AiTrendState => ({
    trend: { 7: null, 14: null, 30: null },
    loading: false,
    error: null,
    errorCode: null,
  }),
  actions: {
    /**
     * 拉取指定窗口的趋势数据，按 window 分桶缓存。
     *
     * <p>失败保留旧 trend 槽位；errorCode 解耦自 error message（参考
     * {@code stores/task.ts} captureError 模式）。
     */
    async load(windowDays: TrendWindowDays): Promise<AiTrendResponse | null> {
      this.loading = true;
      this.error = null;
      this.errorCode = null;
      try {
        const r = await aiTrendApi.trend(windowDays);
        this.trend[windowDays] = r;
        return r;
      } catch (e: unknown) {
        if (e instanceof ApiError) {
          this.error = e.message;
          this.errorCode = e.code;
        } else if (e instanceof Error) {
          this.error = e.message;
          this.errorCode = null;
        } else {
          this.error = 'unknown error';
          this.errorCode = null;
        }
        return null;
      } finally {
        this.loading = false;
      }
    },

    /** 视图层切换时清空所有状态（不持久化）。 */
    clear(): void {
      this.trend = { 7: null, 14: null, 30: null };
      this.error = null;
      this.errorCode = null;
    },
  },
});