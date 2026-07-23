import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import dayjs from 'dayjs';
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus';
import ExpenseView from '@/views/ExpenseView.vue';
import ExpenseList from '@/components/ExpenseList.vue';
import ExpenseSummaryCard from '@/components/ExpenseSummaryCard.vue';
import ExpenseDialog from '@/components/ExpenseDialog.vue';
import { useAuthStore } from '@/stores/auth';
import { useExpenseStore } from '@/stores/expense';
import { expenseApi } from '@/api/expense';
import { showAuthError } from '@/utils/error';
import { ApiError } from '@/api/http';
import type { ExpenseListItem, ExpenseResponse, ExpenseSummary } from '@/types';

vi.mock('@/api/expense', () => ({
  expenseApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    summary: vi.fn(),
    categories: vi.fn(),
  },
}));

vi.mock('@/utils/error', () => ({
  showAuthError: vi.fn(),
  authErrorMessage: vi.fn(),
}));

// ElMessageBox.confirm 在 jsdom 下渲染 modal 用户无法交互；stub 为「确认」。
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus');
  return {
    ...actual,
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm' as unknown as Awaited<ReturnType<typeof ElMessageBox.confirm>>),
    },
  };
});

function buildRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/expenses', component: ExpenseView },
      { path: '/expenses/:id(\\d+)', component: { template: '<div>detail</div>' } },
    ],
  });
}

async function mountView() {
  const router = buildRouter();
  router.push('/expenses');
  await router.isReady();
  const wrapper = mount(ExpenseView, {
    global: { plugins: [ElementPlus, router] },
  });
  await flushPromises();
  return wrapper;
}

const sampleList: ExpenseListItem[] = [
  {
    id: 1,
    amount: 35.5,
    category: 'MEAL',
    note: '午饭',
    occurredAt: '2026-07-15T12:00:00+08:00',
  },
];

const sampleSummary: ExpenseSummary = {
  startMonth: '2026-07-01',
  endMonth: '2026-07-01',
  amountByCategory: {
    MEAL: 35.5,
    SHOPPING: 0,
    TRANSPORT: 0,
    SUBSCRIPTION: 0,
    OTHER: 0,
  },
  totalAmount: 35.5,
};

const fullItem: ExpenseResponse = {
  id: 1,
  userId: 1,
  amount: 35.5,
  category: 'MEAL',
  note: '午饭',
  occurredAt: '2026-07-15T12:00:00+08:00',
  createdAt: '2026-07-15T12:00:01+08:00',
  updatedAt: '2026-07-15T12:00:01+08:00',
};

beforeEach(() => {
  setActivePinia(createPinia());
  const auth = useAuthStore();
  auth.setTokens('access', 'refresh');
  auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });

  vi.mocked(expenseApi.list).mockReset();
  vi.mocked(expenseApi.get).mockReset();
  vi.mocked(expenseApi.create).mockReset();
  vi.mocked(expenseApi.update).mockReset();
  vi.mocked(expenseApi.delete).mockReset();
  vi.mocked(expenseApi.summary).mockReset();
  vi.mocked(showAuthError).mockReset();
  vi.mocked(ElMessageBox.confirm).mockReset();
  vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as unknown as Awaited<ReturnType<typeof ElMessageBox.confirm>>);

  // list 响应回显请求的 page（与真实后端 PageResponse 行为一致），
  // 便于断言 store.page.current 在 onPageChange 后保持新值。
  vi.mocked(expenseApi.list).mockImplementation(async (filter) => ({
    items: sampleList,
    total: sampleList.length,
    page: filter.page,
    size: filter.size,
  }));
  vi.mocked(expenseApi.summary).mockResolvedValue(sampleSummary);
});

// ---------------------------------------------------------------
// onMounted 数据加载
// ---------------------------------------------------------------
describe('ExpenseView / 加载', () => {
  it('onMounted 调 fetchList + fetchSummary（不调 categories — YAGNI）', async () => {
    await mountView();
    expect(expenseApi.list).toHaveBeenCalledTimes(1);
    expect(expenseApi.summary).toHaveBeenCalledTimes(1);
  });

  it('onMounted 后 filter.from 落在当月 1 号本地 00:00；to 落在当月最后一天本地 23:59', async () => {
    const w = await mountView();
    const store = useExpenseStore();
    const y = dayjs().year();
    const m = dayjs().month() + 1;
    const lastDay = dayjs(`${y}-${String(m).padStart(2, '0')}-01`).endOf('month').format('YYYY-MM-DD');
    expect(dayjs(store.filter.from).format('YYYY-MM-DD HH:mm')).toBe(
      `${y}-${String(m).padStart(2, '0')}-01 00:00`,
    );
    expect(dayjs(store.filter.to).format('YYYY-MM-DD HH:mm')).toBe(`${lastDay} 23:59`);
  });

  it('渲染 ExpenseList / ExpenseSummaryCard / ExpenseDialog', async () => {
    const w = await mountView();
    expect(w.findComponent(ExpenseList).exists()).toBe(true);
    expect(w.findComponent(ExpenseSummaryCard).exists()).toBe(true);
    expect(w.findComponent(ExpenseDialog).exists()).toBe(true);
  });
});

// ---------------------------------------------------------------
// 顶部操作
// ---------------------------------------------------------------
describe('ExpenseView / 顶部操作', () => {
  it('点击「+ 新增消费」→ store.openDialog create', async () => {
    const w = await mountView();
    const store = useExpenseStore();
    await w.find('[data-testid="expense-create-btn"]').trigger('click');
    expect(store.dialogVisible).toBe(true);
    expect(store.dialogMode).toBe('create');
    expect(store.currentItem).toBeNull();
  });

  it('分类过滤变化 → fetchList 重拉，page 重置 1', async () => {
    const w = await mountView();
    const store = useExpenseStore();
    store.page.current = 3;
    const before = vi.mocked(expenseApi.list).mock.calls.length;
    await w.vm.onCategoryChange();
    expect(vi.mocked(expenseApi.list).mock.calls.length).toBeGreaterThan(before);
    expect(store.page.current).toBe(1);
  });

  it('分页变化 → fetchList 带新 page', async () => {
    const w = await mountView();
    await w.vm.onPageChange(3);
    expect(useExpenseStore().page.current).toBe(3);
    expect(expenseApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 3 }),
    );
  });
});

// ---------------------------------------------------------------
// 列表行事件
// ---------------------------------------------------------------
describe('ExpenseView / 列表行事件', () => {
  it('edit 事件 → 先 expenseApi.get(id) 取全字段 → store.openDialog edit', async () => {
    vi.mocked(expenseApi.get).mockResolvedValue(fullItem);
    const w = await mountView();
    const store = useExpenseStore();
    await w.vm.onEdit(1);
    await flushPromises();
    expect(expenseApi.get).toHaveBeenCalledWith(1);
    expect(store.dialogVisible).toBe(true);
    expect(store.dialogMode).toBe('edit');
    expect(store.currentItem).toEqual(fullItem);
  });

  it('edit 抛 ApiError(1003) → showAuthError(1003)，dialog 不开', async () => {
    vi.mocked(expenseApi.get).mockRejectedValue(new ApiError(1003, '无权操作'));
    const w = await mountView();
    const store = useExpenseStore();
    await w.vm.onEdit(999);
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1003);
    expect(store.dialogVisible).toBe(false);
  });

  it('delete 事件 → 确认 → store.remove → 刷新 summary', async () => {
    vi.mocked(expenseApi.delete).mockResolvedValue(undefined);
    const w = await mountView();
    const before = vi.mocked(expenseApi.summary).mock.calls.length;
    await w.vm.onDelete(1);
    await flushPromises();
    expect(ElMessageBox.confirm).toHaveBeenCalled();
    expect(expenseApi.delete).toHaveBeenCalledWith(1);
    // store.remove 内部已调一次 fetchList；onDelete 再 refresh summary
    expect(vi.mocked(expenseApi.summary).mock.calls.length).toBeGreaterThan(before);
  });

  it('delete 取消（ElMessageBox 抛 reject）→ 不调 remove', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce(new Error('cancel'));
    const w = await mountView();
    await w.vm.onDelete(1);
    await flushPromises();
    expect(expenseApi.delete).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------
// 汇总卡月切换
// ---------------------------------------------------------------
describe('ExpenseView / 月份切换', () => {
  it('change-month 事件 → 切 year/month + filter 区间 + 重拉 list + summary', async () => {
    const w = await mountView();
    const store = useExpenseStore();
    const beforeSummaryCalls = vi.mocked(expenseApi.summary).mock.calls.length;
    const beforeListCalls = vi.mocked(expenseApi.list).mock.calls.length;

    await w.vm.onMonthChange(2026, 6);
    await flushPromises();

    expect(dayjs(store.filter.from).format('YYYY-MM-DD HH:mm')).toBe('2026-06-01 00:00');
    expect(dayjs(store.filter.to).format('YYYY-MM-DD HH:mm')).toBe('2026-06-30 23:59');
    expect(vi.mocked(expenseApi.summary).mock.calls.length).toBe(beforeSummaryCalls + 1);
    expect(expenseApi.summary).toHaveBeenLastCalledWith(2026, 6);
    expect(vi.mocked(expenseApi.list).mock.calls.length).toBeGreaterThan(beforeListCalls);
  });
});

// ---------------------------------------------------------------
// 错误码映射
// ---------------------------------------------------------------
describe('ExpenseView / 错误码', () => {
  it('create 抛 1006（rate-limit）→ store.create 抛 ApiError 透传', async () => {
    vi.mocked(expenseApi.create).mockRejectedValue(
      new ApiError(1006, '操作过于频繁'),
    );
    await mountView();
    const store = useExpenseStore();
    await expect(
      store.create({
        amount: 10,
        category: 'MEAL',
        occurredAt: '2026-07-15T12:00:00+08:00',
      }),
    ).rejects.toThrow();
  });

  it('remove 抛 1003（cross-user）→ store.remove 抛 ApiError 透传', async () => {
    vi.mocked(expenseApi.delete).mockRejectedValue(
      new ApiError(1003, '无权操作'),
    );
    await mountView();
    const store = useExpenseStore();
    await expect(store.remove(999)).rejects.toThrow();
  });

  it('fetchList 抛 1003 → refreshList 调 showAuthError(1003)', async () => {
    vi.mocked(expenseApi.list).mockRejectedValueOnce(new ApiError(1003, '无权操作'));
    await mountView();
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1003);
  });
});

// ---------------------------------------------------------------
// Dialog 集成
// ---------------------------------------------------------------
describe('ExpenseView / dialog 集成', () => {
  it('dialog success 后 → refreshSummary', async () => {
    const w = await mountView();
    const beforeSummary = vi.mocked(expenseApi.summary).mock.calls.length;
    await w.vm.onDialogSuccess();
    expect(vi.mocked(expenseApi.summary).mock.calls.length).toBe(beforeSummary + 1);
  });
});

// ---------------------------------------------------------------
// v1.2.6 #3：ExpenseView loading skeleton
// ---------------------------------------------------------------
describe('ExpenseView / loading skeleton', () => {
  it('首次加载（loading=true && list=null）→ 渲染 expense-list-skeleton + 不渲染真实 ExpenseList', async () => {
    // 让 list 永不 resolve → store.loading=true, store.list=null
    vi.mocked(expenseApi.list).mockReturnValue(new Promise(() => {}));
    vi.mocked(expenseApi.summary).mockReturnValue(new Promise(() => {}));
    const w = await mountView();
    await flushPromises();

    const skel = w.find('[data-testid="expense-list-skeleton"]');
    expect(skel.exists()).toBe(true);
    // 真实 ExpenseList 不渲染（因为整个左侧被 skeleton 替换）
    expect(w.findComponent(ExpenseList).exists()).toBe(false);
  });

  it('skeleton 含 3 个 day-group + 每个含 day-header + 3 行 item skeleton（含 category / amount / note / time 四段）', async () => {
    vi.mocked(expenseApi.list).mockReturnValue(new Promise(() => {}));
    vi.mocked(expenseApi.summary).mockReturnValue(new Promise(() => {}));
    const w = await mountView();
    await flushPromises();

    const groups = w.findAll('[data-testid="expense-list-skeleton-day-group"]');
    expect(groups).toHaveLength(3);
    expect(groups[0].find('[data-testid="expense-list-skeleton-day-header"]').exists()).toBe(true);
    const items = groups[0].findAll('[data-testid="expense-list-skeleton-item"]');
    expect(items).toHaveLength(3);
    const firstItem = items[0];
    expect(firstItem.find('[data-testid="expense-list-skeleton-category"]').exists()).toBe(true);
    expect(firstItem.find('[data-testid="expense-list-skeleton-amount"]').exists()).toBe(true);
    expect(firstItem.find('[data-testid="expense-list-skeleton-note"]').exists()).toBe(true);
    expect(firstItem.find('[data-testid="expense-list-skeleton-time"]').exists()).toBe(true);
  });

  it('refresh 阶段（loading=true && list 已有）→ 渲染真实 ExpenseList 不渲染 skeleton', async () => {
    // 首次加载完成
    vi.mocked(expenseApi.list).mockResolvedValueOnce({ items: sampleList, total: sampleList.length, page: 1, size: 20 });
    const w = await mountView();
    await flushPromises();
    const store = useExpenseStore();
    expect(store.list).not.toBeNull();

    // refresh：loading=true, list 仍保留
    store.$patch({ loading: true, list: [] });
    await flushPromises();

    expect(w.find('[data-testid="expense-list-skeleton"]').exists()).toBe(false);
    expect(w.findComponent(ExpenseList).exists()).toBe(true);
  });

  it('首次加载（loading）时不渲染 pagination（避免 layout 抖动）', async () => {
    vi.mocked(expenseApi.list).mockReturnValue(new Promise(() => {}));
    vi.mocked(expenseApi.summary).mockReturnValue(new Promise(() => {}));
    const w = await mountView();
    await flushPromises();
    expect(w.find('[data-testid="expense-pagination"]').exists()).toBe(false);
  });
});
