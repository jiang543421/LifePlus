import { defineStore } from 'pinia';
import { expenseApi } from '@/api/expense';
import type {
  CreateExpenseRequest,
  ExpenseFilter,
  ExpenseListItem,
  ExpenseListResponse,
  ExpenseResponse,
  ExpenseSummary,
  UpdateExpenseRequest,
} from '@/types';
import { ApiError } from '@/api/http';

/** 当前页状态；与 list response 的 page/size/total 三件套对应。 */
interface ExpensePage {
  current: number;
  size: number;
  total: number;
}

/** ExpenseDialog 模式（spec §06-expense §5）。 */
export type DialogMode = 'create' | 'edit';

/** 默认 page size；与后端 ExpenseConstants.DEFAULT_PAGE_SIZE 对齐。 */
const DEFAULT_SIZE = 20;

interface ExpenseState {
  /** 列表过滤条件；page/size 必有。 */
  filter: ExpenseFilter;
  /** 当前页列表项；null = 未拉过。 */
  list: ExpenseListItem[] | null;
  /** 当前选定月汇总；null = 未拉过。 */
  summary: ExpenseSummary | null;
  /** 分页元信息；与 fetchList 响应同步。 */
  page: ExpensePage;
  loading: boolean;
  /** 最近一次失败的 ApiError.message；null = 无错。 */
  error: string | null;
  /** 最近一次失败的 ApiError.code（用于视图 toast 选择文案）；null = 无业务码。 */
  errorCode: number | null;
  /** ExpenseDialog 显示标志。 */
  dialogVisible: boolean;
  /** ExpenseDialog 当前模式（"新增" / "编辑"）。 */
  dialogMode: DialogMode;
  /** ExpenseDialog 编辑模式的当前条目；create 模式为 null。 */
  currentItem: ExpenseResponse | null;
}

/**
 * Expense store（spec §06-expense section 5）。
 *
 * <p>设计原则与 {@code usePlanStore} / {@code useTaskStore} 一致：
 * <ul>
 *   <li>不持久化到 localStorage — 每次 mount 重置 filter、重新拉列表</li>
 *   <li>filter 是 UI 状态（用户已选 category/from/to），内存持有 — spec 不要求跨页面保留筛选</li>
 *   <li>失败写 error/errorCode（与 plan store 同款），mutation 失败不写（调用方 try/catch）</li>
 * </ul>
 *
 * <p>对话状态（dialogVisible / dialogMode / currentItem）也在 store 里维护：
 * HomeView 的 "添加" CTA 与 ExpenseView 的 "编辑" 共用同一个 ExpenseDialog 实例，
 * store 持有可见性避免两个视图各持一份本地 ref 出现双弹窗。
 */
export const useExpenseStore = defineStore('expense', {
  state: (): ExpenseState => ({
    filter: { page: 1, size: DEFAULT_SIZE },
    list: null,
    summary: null,
    page: { current: 1, size: DEFAULT_SIZE, total: 0 },
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
      return !!f.category || !!f.from || !!f.to;
    },
    /** 是否有任意已加载数据。 */
    hasData: (s): boolean => (s.list?.length ?? 0) > 0,
  },

  actions: {
    /**
     * 拉取当前 filter 对应的列表（GET /expenses）。失败保留旧 list，写 error。
     * 与 {@code usePlanStore.fetchList} 同款契约。
     */
    async fetchList(): Promise<ExpenseListResponse | null> {
      this.loading = true;
      this.error = null;
      this.errorCode = null;
      try {
        const resp = await expenseApi.list(this.filter);
        this.list = resp.items;
        this.page = { current: resp.page, size: resp.size, total: resp.total };
        return resp;
      } catch (e: unknown) {
        if (e instanceof ApiError) {
          this.error = e.message;
          this.errorCode = e.code;
        } else {
          this.error = 'fetch expenses failed';
          this.errorCode = null;
        }
        return null;
      } finally {
        this.loading = false;
      }
    },

    /**
     * 拉取指定月份汇总（GET /expenses/summary）。失败写 error，summary 保留旧值。
     */
    async fetchSummary(year: number, month: number): Promise<ExpenseSummary | null> {
      this.error = null;
      this.errorCode = null;
      try {
        this.summary = await expenseApi.summary(year, month);
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
     * 创建消费（POST /expenses）。成功后自动 refresh list。
     * 失败抛 ApiError（与 plan store.create 行为一致 — 视图 toast）。
     */
    async create(req: CreateExpenseRequest): Promise<ExpenseResponse> {
      this.error = null;
      this.errorCode = null;
      const resp = await expenseApi.create(req);
      await this.fetchList();
      return resp;
    },

    /**
     * 局部更新（PATCH /expenses/{id}）。成功后自动 refresh list。
     */
    async update(id: number, req: UpdateExpenseRequest): Promise<void> {
      this.error = null;
      this.errorCode = null;
      await expenseApi.update(id, req);
      await this.fetchList();
    },

    /**
     * 软删（DELETE /expenses/{id}）。成功后自动 refresh list。
     */
    async remove(id: number): Promise<void> {
      this.error = null;
      this.errorCode = null;
      await expenseApi.delete(id);
      await this.fetchList();
    },

    /**
     * 清空所有过滤项 + 回到第 1 页 + 重新拉列表（plan §T10 行为）。
     * 与 {@code usePlanStore.resetFilter} 不同：plan store 的 resetFilter 仅清状态，
     * 视图负责 fetchList；本 store 把 refetch 合并进来简化视图代码。
     */
    async resetFilter(): Promise<void> {
      this.filter = { page: 1, size: this.filter.size };
      this.page = { ...this.page, current: 1 };
      await this.fetchList();
    },

    /**
     * 打开 ExpenseDialog。
     * @param mode 'create' = 新增（忽略 item）；'edit' = 编辑（item 必填）
     * @param item 仅 edit 模式使用；当前规范要求 ExpenseResponse 全字段，
     *             视图从列表点编辑时需先 expenseApi.get(id) 取全字段
     */
    openDialog(mode: DialogMode, item?: ExpenseResponse): void {
      this.dialogMode = mode;
      this.currentItem = item ?? null;
      this.dialogVisible = true;
    },

    /**
     * 关闭 ExpenseDialog 并清空 currentItem（避免下次 openDialog('create')
     * 残留旧 item 字段）。
     */
    closeDialog(): void {
      this.dialogVisible = false;
      this.currentItem = null;
    },
  },
});