import { defineStore } from 'pinia';
import { dietApi } from '@/api/diet';
import type {
  CreateDietRequest,
  DietFrequentItem,
  DietFilter,
  DietListItem,
  DietListResponse,
  DietResponse,
  DietSummary,
  MealType,
  UpdateDietRequest,
} from '@/types';
import { ApiError } from '@/api/http';
import { DIET_PAGE_SIZE } from '@/constants/diet';

/** 当前页状态；与 list response 的 page/size/total 三件套对应。 */
interface DietPage {
  current: number;
  size: number;
  total: number;
}

/** DietDialog 模式（spec §07-diet section 5）。 */
export type DietDialogMode = 'create' | 'edit';

interface DietState {
  /** 列表过滤条件；page/size 必有，mealType/from/to 可选。 */
  filter: DietFilter;
  /** 当前页列表项；null = 未拉过。 */
  list: DietListItem[] | null;
  /** 当日营养汇总；null = 未拉过。 */
  summary: DietSummary | null;
  /** summary 查询日期（YYYY-MM-DD）；null = 未拉过。 */
  summaryDate: string | null;
  /** 一键复用候选（frequent 接口结果）；空数组 = 已拉但无数据。 */
  frequent: DietFrequentItem[];
  /** 分页元信息；与 fetchList 响应同步。 */
  page: DietPage;
  loading: boolean;
  /** 最近一次失败的 ApiError.message；null = 无错。 */
  error: string | null;
  /** 最近一次失败的 ApiError.code（用于视图 toast 选择文案）；null = 无业务码。 */
  errorCode: number | null;
  /** DietDialog 显示标志。 */
  dialogVisible: boolean;
  /** DietDialog 当前模式（"新增" / "编辑"）。 */
  dialogMode: DietDialogMode;
  /** DietDialog 编辑模式的当前条目；create 模式为 null。 */
  currentItem: DietResponse | null;
}

/**
 * Diet store（spec §07-diet-design section 5 + §6.4）。
 *
 * <p>设计原则与 {@code useExpenseStore} 一致：
 * <ul>
 *   <li>不持久化到 localStorage — 每次 mount 重置 filter、重新拉列表</li>
 *   <li>filter 是 UI 状态（用户已选 mealType/from/to），内存持有</li>
 *   <li>失败写 error/errorCode（与 expense store 同款），mutation 失败不写（调用方 try/catch）</li>
 * </ul>
 *
 * <p>对话状态（dialogVisible / dialogMode / currentItem）也在 store 里维护：
 * HomeView 的"添加"CTA 与 DietView 的"编辑"共用同一个 DietDialog 实例，
 * store 持有可见性避免两个视图各持一份本地 ref 出现双弹窗。
 *
 * <p>{@link #groupedByDay} getter（spec §6.4）：按 occurredAt 日期分组的二维数组，
 * 用于 DietDayGroup 渲染；与 expense store 不同（expense 按月分组，diet 按日）。
 */
export const useDietStore = defineStore('diet', {
  state: (): DietState => ({
    filter: { page: 1, size: DIET_PAGE_SIZE },
    list: null,
    summary: null,
    summaryDate: null,
    frequent: [],
    page: { current: 1, size: DIET_PAGE_SIZE, total: 0 },
    loading: false,
    error: null,
    errorCode: null,
    dialogVisible: false,
    dialogMode: 'create',
    currentItem: null,
  }),

  getters: {
    /** 列表过滤项非空（page/size 单独不算）。 */
    hasFilter: (s): boolean => {
      const f = s.filter;
      return !!f.mealType || !!f.from || !!f.to;
    },
    /** 是否有任意已加载数据。 */
    hasData: (s): boolean => (s.list?.length ?? 0) > 0,
    /**
     * 按日期分组的列表（spec §6.4）。
     * 输出 [{ day: 'YYYY-MM-DD', items: DietListItem[] }, ...]，day DESC 排序。
     * occurredAt 是 ISO-8601 datetime with offset，取前 10 位即 YYYY-MM-DD（TZ 与 offset 一致时）
     * ；为避免本地时区漂移，沿用 expense 模式统一用 occurredAt.slice(0, 10)。
     */
    groupedByDay: (s): Array<{ day: string; items: DietListItem[] }> => {
      if (!s.list || s.list.length === 0) return [];
      const map = new Map<string, DietListItem[]>();
      for (const item of s.list) {
        const day = item.occurredAt.slice(0, 10);
        const bucket = map.get(day);
        if (bucket) bucket.push(item);
        else map.set(day, [item]);
      }
      // 倒序：日期最新的在前
      return Array.from(map.entries())
        .sort(([a], [b]) => (a < b ? 1 : a > b ? -1 : 0))
        .map(([day, items]) => ({ day, items }));
    },
  },

  actions: {
    /**
     * 拉取当前 filter 对应的列表（GET /diets）。失败保留旧 list，写 error。
     */
    async fetchList(): Promise<DietListResponse | null> {
      this.loading = true;
      this.error = null;
      this.errorCode = null;
      try {
        const resp = await dietApi.list(this.filter);
        this.list = resp.items;
        this.page = { current: resp.page, size: resp.size, total: resp.total };
        return resp;
      } catch (e: unknown) {
        if (e instanceof ApiError) {
          this.error = e.message;
          this.errorCode = e.code;
        } else {
          this.error = 'fetch diets failed';
          this.errorCode = null;
        }
        return null;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 拉取指定日期汇总（GET /diets/summary?date=YYYY-MM-DD）。
     * 失败写 error，summary 保留旧值。
     */
    async fetchSummary(date: string): Promise<DietSummary | null> {
      this.error = null;
      this.errorCode = null;
      try {
        this.summary = await dietApi.summary(date);
        this.summaryDate = date;
        return this.summary;
      } catch (e: unknown) {
        if (e instanceof ApiError) {
          this.error = e.message;
          this.errorCode = e.code;
        } else {
          this.error = 'fetch summary failed';
          this.errorCode = null;
        }
        return null;
      }
    },

    /**
     * 拉取高频名称聚合（GET /diets/frequent）。
     * 失败保留旧 frequent 数组，写 error；空响应写入空数组（用于"无候选"提示）。
     */
    async fetchFrequent(from?: string, to?: string, limit?: number): Promise<DietFrequentItem[] | null> {
      this.error = null;
      this.errorCode = null;
      try {
        this.frequent = await dietApi.frequent(from, to, limit);
        return this.frequent;
      } catch (e: unknown) {
        if (e instanceof ApiError) {
          this.error = e.message;
          this.errorCode = e.code;
        } else {
          this.error = 'fetch frequent failed';
          this.errorCode = null;
        }
        return null;
      }
    },

    /**
     * 创建饮食（POST /diets）。成功后自动 refresh list。
     * 失败抛 ApiError（与 expense store.create 行为一致 — 视图 toast）。
     */
    async create(req: CreateDietRequest): Promise<DietResponse> {
      this.error = null;
      this.errorCode = null;
      const resp = await dietApi.create(req);
      await this.fetchList();
      return resp;
    },

    /**
     * 局部更新（PATCH /diets/{id}）。成功后自动 refresh list。
     */
    async update(id: number, req: UpdateDietRequest): Promise<void> {
      this.error = null;
      this.errorCode = null;
      await dietApi.update(id, req);
      await this.fetchList();
    },

    /**
     * 软删（DELETE /diets/{id}）。成功后自动 refresh list。
     */
    async remove(id: number): Promise<void> {
      this.error = null;
      this.errorCode = null;
      await dietApi.delete(id);
      await this.fetchList();
    },

    /**
     * 仅翻页：同步更新 filter.page + page.current；视图调 setPage 后再 fetchList。
     */
    setPage(page: number, size?: number): void {
      const newSize = size ?? this.filter.size;
      this.filter = { ...this.filter, page, size: newSize };
      this.page = { ...this.page, current: page, size: newSize };
    },

    /**
     * 部分更新过滤项并把 page 重置回 1（task store.setFilter 行为）。
     */
    setFilter(patch: Partial<Pick<DietFilter, 'mealType' | 'from' | 'to'>>): void {
      this.filter = { ...this.filter, ...patch, page: 1 };
      this.page = { ...this.page, current: 1 };
    },

    /**
     * 清空所有过滤项 + 回到第 1 页 + 重新拉列表（expense store.resetFilter 同款）。
     */
    async resetFilter(): Promise<void> {
      this.filter = { page: 1, size: this.filter.size };
      this.page = { ...this.page, current: 1 };
      await this.fetchList();
    },

    /**
     * 打开 DietDialog。
     * @param mode 'create' = 新增（忽略 item）；'edit' = 编辑（item 必填）
     * @param item 仅 edit 模式使用；视图从列表点编辑时需先 dietApi.get(id) 取全字段
     */
    openDialog(mode: DietDialogMode, item?: DietResponse): void {
      this.dialogMode = mode;
      this.currentItem = item ?? null;
      this.dialogVisible = true;
    },

    /**
     * 关闭 DietDialog 并清空 currentItem（避免下次 openDialog('create') 残留旧 item）。
     */
    closeDialog(): void {
      this.dialogVisible = false;
      this.currentItem = null;
    },
  },
});

/** 暴露 MealType 类型供视图 import 时类型断言用（不导出 action 类型，保持 store 模块紧凑）。 */
export type { MealType };