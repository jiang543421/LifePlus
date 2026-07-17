import { defineStore } from 'pinia';
import { planApi } from '@/api/plan';
import type {
  PlanCreateRequest,
  PlanFilter,
  PlanListItem,
  PlanListResponse,
  PlanResponse,
} from '@/types';
import { ApiError } from '@/api/http';

interface PlanState {
  /** 当前过滤条件（page/size 必有；from/to 可选）。 */
  filter: PlanFilter;
  /** 当前页列表项；null = 未拉过。 */
  list: PlanListItem[] | null;
  total: number;
  loading: boolean;
  error: string | null;
  /** 最近一次失败的 ApiError.code（用于视图 toast 选择文案）；null = 无业务码。 */
  errorCode: number | null;
}

/**
 * Plan store（spec §04 §5：列表不做长缓存）。
 *
 * <p>设计原则与 {@code useTaskStore} 一致：
 * <ul>
 *   <li>不持久化到 localStorage — 每次 mount 重置 filter、重新拉列表</li>
 *   <li>仅在内存保留"当前页"用于日历视图渲染，刷新页面后重置（预期行为）</li>
 *   <li>filter 是 UI 状态（用户已选 from/to），同样内存持有 — spec 不要求跨页面保留筛选</li>
 * </ul>
 */
export const usePlanStore = defineStore('plan', {
  state: (): PlanState => ({
    filter: { page: 1, size: 20 },
    list: null,
    total: 0,
    loading: false,
    error: null,
    errorCode: null,
  }),
  getters: {
    /** 任意过滤项非空 → true（page/size 单独不算过滤）。 */
    hasFilter: (s): boolean => {
      const f = s.filter;
      return !!f.from || !!f.to;
    },
  },
  actions: {
    /**
     * 拉取当前 filter 对应的列表（GET /plans）。失败时保留旧 list，写 error。
     * 调用方负责 navigate / toast；store 仅承载状态。
     */
    async fetchList(): Promise<PlanListResponse | null> {
      this.loading = true;
      this.error = null;
      this.errorCode = null;
      try {
        const resp = await planApi.list(this.filter);
        this.list = resp.items;
        this.total = resp.total;
        return resp;
      } catch (e: unknown) {
        if (e instanceof ApiError) {
          this.error = e.message;
          this.errorCode = e.code;
        } else {
          this.error = 'fetch plans failed';
          this.errorCode = null;
        }
        // 失败保留旧 list（避免空闪烁）；调用方按需清空
        return null;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 创建计划（POST /plans）。成功后由视图触发 fetchList() 拉新列表。
     * 失败抛 ApiError / Error（与 fetchList 行为一致 — 调用方 toast）。
     */
    async create(req: PlanCreateRequest): Promise<PlanResponse> {
      this.error = null;
      this.errorCode = null;
      return planApi.create(req);
    },

    /**
     * 软删（DELETE /plans/{id}）。错误向上抛。
     */
    async remove(id: number): Promise<void> {
      this.error = null;
      this.errorCode = null;
      await planApi.delete(id);
    },

    /**
     * 浅合并 filter 段并重置 page=1（任何过滤项变化都从第 1 页开始）。
     */
    setFilter(patch: Partial<Omit<PlanFilter, 'page' | 'size'>>): void {
      this.filter = { ...this.filter, ...patch, page: 1 };
    },

    /** 仅翻页：page/size 变更。 */
    setPage(page: number, size?: number): void {
      this.filter = { ...this.filter, page, size: size ?? this.filter.size };
    },

    /** 清空所有过滤项 + 回到第 1 页。 */
    resetFilter(): void {
      this.filter = { page: 1, size: this.filter.size };
    },

    /** 视图卸载时清理（避免内存持有大列表）。 */
    clear(): void {
      this.list = null;
      this.total = 0;
      this.error = null;
      this.errorCode = null;
      this.filter = { page: 1, size: this.filter.size };
    },
  },
});