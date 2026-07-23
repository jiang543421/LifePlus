import { defineStore } from 'pinia';
import { dailyApi } from '@/api/daily';
import type { DailyReportPayload, WeeklyReportPayload } from '@/types';
import { ApiError } from '@/api/http';

/** Daily view 单一过滤态：当前展示日期（YYYY-MM-DD）；空串 = 未选（取今日）。 */
interface DailyFilter {
  date: string;
}

interface DailyState {
  /** 单日日报响应；null = 未拉过。 */
  daily: DailyReportPayload | null;
  /** 周报响应；null = 未拉过。 */
  week: WeeklyReportPayload | null;
  /** 当前过滤态。 */
  filter: DailyFilter;
  loading: boolean;
  /** 最近一次失败的 ApiError.message；null = 无错。 */
  error: string | null;
  /** 最近一次失败的 ApiError.code（用于视图 toast 选择文案）；null = 无业务码。 */
  errorCode: number | null;
}

/**
 * Daily store（spec §08-daily-report-design §7.4 + 后端 DailyReportController）。
 *
 * <p>设计原则与 {@code useDietStore} / {@code useExpenseStore} 一致：
 * <ul>
 *   <li>不持久化（每次 mount 重置 filter、重新拉数据）</li>
 *   <li>不缓存（v1.2.3 后端实时聚合，store 只持有当前展示数据）</li>
 *   <li>失败写 error/errorCode，成功路径写数据并清错</li>
 * </ul>
 *
 * <p>不持有 week 切换按钮的"当前 ISO 周"状态——{@link #syncFromRoute} 只把
 * URL {@code ?date=YYYY-MM-DD} 同步到 filter.date；{@code ?week=YYYY-Www} 仅
 * 用于路由分享，视图按需决定是否取整周并 fetchWeek（spec §7.3 周报按钮说明）。
 */
export const useDailyStore = defineStore('daily', {
  state: (): DailyState => ({
    daily: null,
    week: null,
    filter: { date: '' },
    loading: false,
    error: null,
    errorCode: null,
  }),

  getters: {
    /**
     * 任务完成率（0..1）。
     * <p>daily=null 时返回 0；{@code task.totalCount=0} 时同样兜底 0（避免除零）。
     * <p>注意：后端已计算 {@code task.completionRate}，本 getter 与之等价但
     * 在 daily=null 场景下也能给出稳定值，渲染侧可放心使用。
     */
    taskCompletionRate: (s): number => {
      if (!s.daily) return 0;
      const total = s.daily.task.totalCount;
      if (total === 0) return 0;
      return s.daily.task.completedCount / total;
    },
    /** 饮食指标是否启用（v1.2.3 冻结：永远 false；解冻饮食后透传后端 enabled）。 */
    dietEnabled: (s): boolean => s.daily?.diet.enabled ?? false,
  },

  actions: {
    /**
     * 拉取单日日报（GET /daily）。
     *
     * @param date 可选 YYYY-MM-DD；缺省由后端按 Asia/Shanghai 当日兜底
     * @returns 成功返回 payload；失败返回 null（error/errorCode 已写入）
     */
    async fetchDaily(date?: string): Promise<DailyReportPayload | null> {
      this.loading = true;
      this.error = null;
      this.errorCode = null;
      try {
        const payload = await dailyApi.daily(date);
        this.daily = payload;
        // 同步 filter.date：传入值回显；未传则保留空串（表示"今日"，与 store 初始一致）
        this.filter = { date: date ?? '' };
        return payload;
      } catch (e: unknown) {
        if (e instanceof ApiError) {
          this.error = e.message;
          this.errorCode = e.code;
        } else {
          this.error = 'fetch daily failed';
          this.errorCode = null;
        }
        return null;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 拉取周报（GET /daily/week）。
     *
     * @param date 可选 YYYY-MM-DD（目标周内任意一天）
     */
    async fetchWeek(date?: string): Promise<WeeklyReportPayload | null> {
      this.error = null;
      this.errorCode = null;
      try {
        const payload = await dailyApi.week(date);
        this.week = payload;
        return payload;
      } catch (e: unknown) {
        if (e instanceof ApiError) {
          this.error = e.message;
          this.errorCode = e.code;
        } else {
          this.error = 'fetch week failed';
          this.errorCode = null;
        }
        return null;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 从路由 query 同步到 filter（spec §7.3 + §9.2 URL 可分享）。
     *
     * <p>支持的 query：
     * <ul>
     *   <li>{@code ?date=YYYY-MM-DD} → 写入 {@link DailyFilter#date}</li>
     *   <li>{@code ?week=YYYY-Www}   → 仅作路由分享标记，不写入 store（视图按需 fetchWeek）</li>
     * </ul>
     * 本 action 不触发 fetch，由视图 onMounted / watch 显式调用 fetchDaily / fetchWeek。
     */
    syncFromRoute(query: Record<string, string | undefined>): void {
      const date = query.date;
      this.filter = { date: date ?? '' };
    },

    /**
     * 清空 filter 并重新拉今日（不带 date 入参，由后端兜底）。
     */
    async resetFilter(): Promise<void> {
      this.filter = { date: '' };
      await this.fetchDaily();
    },
  },
});
