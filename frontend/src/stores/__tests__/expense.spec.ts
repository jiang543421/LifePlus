import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useExpenseStore } from '../expense';
import { ApiError } from '@/api/http';
import type {
  CreateExpenseRequest,
  ExpenseListItem,
  ExpenseListResponse,
  ExpenseResponse,
  ExpenseSummary,
  UpdateExpenseRequest,
} from '@/types';

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

import { expenseApi } from '@/api/expense';

const mockListResp = (total: number, items: unknown[], page = 1, size = 20): ExpenseListResponse => ({
  items: items as ExpenseListItem[],
  total,
  page,
  size,
});

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(expenseApi.list).mockReset();
  vi.mocked(expenseApi.summary).mockReset();
  vi.mocked(expenseApi.create).mockReset();
  vi.mocked(expenseApi.update).mockReset();
  vi.mocked(expenseApi.delete).mockReset();
});

// ---------------------------------------------------------------
// 1. state default
// ---------------------------------------------------------------
describe('useExpenseStore / state default', () => {
  it('默认 filter={page:1,size:20}，list/summary/dialog 全空', () => {
    const s = useExpenseStore();
    expect(s.filter).toEqual({ page: 1, size: 20 });
    expect(s.list).toBeNull();
    expect(s.summary).toBeNull();
    expect(s.page).toEqual({ current: 1, size: 20, total: 0 });
    expect(s.loading).toBe(false);
    expect(s.error).toBeNull();
    expect(s.errorCode).toBeNull();
    expect(s.dialogVisible).toBe(false);
    expect(s.dialogMode).toBe('create');
    expect(s.currentItem).toBeNull();
  });
});

// ---------------------------------------------------------------
// 2. getters
// ---------------------------------------------------------------
describe('useExpenseStore / getters', () => {
  it('hasFilter 任一过滤项设置时为 true，仅翻页不算', () => {
    const s = useExpenseStore();
    expect(s.hasFilter).toBe(false);

    s.filter.page = 3;
    expect(s.hasFilter).toBe(false);

    s.filter.category = 'MEAL';
    expect(s.hasFilter).toBe(true);

    s.filter.category = undefined;
    s.filter.from = '2026-07-01T00:00:00+08:00';
    expect(s.hasFilter).toBe(true);

    s.filter.from = undefined;
    s.filter.to = '2026-07-31T23:59:59+08:00';
    expect(s.hasFilter).toBe(true);
  });

  it('hasData 在 list 为空/null 时为 false，有数据时为 true', () => {
    const s = useExpenseStore();
    expect(s.hasData).toBe(false);

    s.list = [];
    expect(s.hasData).toBe(false);

    s.list = [
      {
        id: 1,
        amount: 10.5,
        category: 'MEAL',
        note: null,
        occurredAt: '2026-07-15T12:00:00+08:00',
      },
    ];
    expect(s.hasData).toBe(true);
  });
});

// ---------------------------------------------------------------
// 3. fetchList
// ---------------------------------------------------------------
describe('useExpenseStore / fetchList', () => {
  it('成功：list/page 更新，loading 翻转', async () => {
    const items = [
      {
        id: 1,
        amount: 10.5,
        category: 'MEAL',
        note: '午饭',
        occurredAt: '2026-07-15T12:00:00+08:00',
      },
      {
        id: 2,
        amount: 30.0,
        category: 'TRANSPORT',
        note: null,
        occurredAt: '2026-07-15T18:00:00+08:00',
      },
    ];
    vi.mocked(expenseApi.list).mockResolvedValueOnce(mockListResp(2, items, 1, 20));

    const s = useExpenseStore();
    expect(s.loading).toBe(false);
    const resp = await s.fetchList();
    expect(s.loading).toBe(false);
    expect(s.list).toEqual(items);
    expect(s.page).toEqual({ current: 1, size: 20, total: 2 });
    expect(s.error).toBeNull();
    expect(resp).toEqual(mockListResp(2, items, 1, 20));
  });

  it('失败 ApiError：写 error/errorCode，list 保留旧值', async () => {
    const items = [
      {
        id: 1,
        amount: 10.5,
        category: 'MEAL',
        note: null,
        occurredAt: '2026-07-15T12:00:00+08:00',
      },
    ];
    vi.mocked(expenseApi.list).mockResolvedValueOnce(mockListResp(1, items));

    const s = useExpenseStore();
    await s.fetchList();
    expect(s.list).toEqual(items);

    vi.mocked(expenseApi.list).mockRejectedValueOnce(
      new ApiError(1003, '无权操作'),
    );
    const resp = await s.fetchList();
    expect(resp).toBeNull();
    expect(s.error).toBe('无权操作');
    expect(s.errorCode).toBe(1003);
    expect(s.list).toEqual(items); // 保留
    expect(s.loading).toBe(false);
  });

  it('失败非 ApiError：error 写通用消息，errorCode 为 null', async () => {
    vi.mocked(expenseApi.list).mockRejectedValueOnce(new Error('network down'));

    const s = useExpenseStore();
    await s.fetchList();
    expect(s.error).toBe('fetch expenses failed');
    expect(s.errorCode).toBeNull();
  });
});

// ---------------------------------------------------------------
// 4. fetchSummary
// ---------------------------------------------------------------
describe('useExpenseStore / fetchSummary', () => {
  it('成功：summary 写入', async () => {
    const summary: ExpenseSummary = {
      startMonth: '2026-07-01',
      endMonth: '2026-07-01',
      amountByCategory: {
        MEAL: 300,
        SHOPPING: 0,
        TRANSPORT: 50,
        SUBSCRIPTION: 0,
        OTHER: 0,
      },
      totalAmount: 350,
    };
    vi.mocked(expenseApi.summary).mockResolvedValueOnce(summary);

    const s = useExpenseStore();
    const resp = await s.fetchSummary(2026, 7);
    expect(resp).toEqual(summary);
    expect(s.summary).toEqual(summary);
    expect(s.error).toBeNull();
  });

  it('失败 ApiError：写 error/errorCode', async () => {
    vi.mocked(expenseApi.summary).mockRejectedValueOnce(
      new ApiError(1004, '资源不存在'),
    );

    const s = useExpenseStore();
    const resp = await s.fetchSummary(2026, 7);
    expect(resp).toBeNull();
    expect(s.error).toBe('资源不存在');
    expect(s.errorCode).toBe(1004);
  });
});

// ---------------------------------------------------------------
// 5. create — plan T10 case 3
// ---------------------------------------------------------------
describe('useExpenseStore / create', () => {
  it('成功：转发 create，自动 refetch list', async () => {
    const created: ExpenseResponse = {
      id: 99,
      userId: 1,
      amount: 50.0,
      category: 'MEAL',
      note: '晚饭',
      occurredAt: '2026-07-15T19:00:00+08:00',
      createdAt: '2026-07-15T19:00:01+08:00',
      updatedAt: '2026-07-15T19:00:01+08:00',
    };
    const req: CreateExpenseRequest = {
      amount: 50.0,
      category: 'MEAL',
      note: '晚饭',
      occurredAt: '2026-07-15T19:00:00+08:00',
    };
    vi.mocked(expenseApi.create).mockResolvedValueOnce(created);
    vi.mocked(expenseApi.list).mockResolvedValueOnce(mockListResp(1, [created]));

    const s = useExpenseStore();
    const resp = await s.create(req);
    expect(resp).toEqual(created);
    expect(expenseApi.create).toHaveBeenCalledWith(req);
    expect(expenseApi.list).toHaveBeenCalledTimes(1);
    expect(s.list).toEqual([created]);
  });

  it('失败 ApiError：抛错不写 errorCode（视图 toast），不触发 refetch', async () => {
    vi.mocked(expenseApi.create).mockRejectedValueOnce(
      new ApiError(1001, '参数校验失败'),
    );

    const s = useExpenseStore();
    await expect(
      s.create({
        amount: 0,
        category: 'MEAL',
        occurredAt: '2026-07-15T19:00:00+08:00',
      }),
    ).rejects.toBeInstanceOf(ApiError);
    expect(s.errorCode).toBeNull();
    expect(expenseApi.list).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------
// 6. update
// ---------------------------------------------------------------
describe('useExpenseStore / update', () => {
  it('成功：转发 update 并 refetch', async () => {
    vi.mocked(expenseApi.update).mockResolvedValueOnce(undefined);
    vi.mocked(expenseApi.list).mockResolvedValueOnce(mockListResp(0, []));

    const s = useExpenseStore();
    const req: UpdateExpenseRequest = { amount: 99.9 };
    await s.update(7, req);
    expect(expenseApi.update).toHaveBeenCalledWith(7, req);
    expect(expenseApi.list).toHaveBeenCalledTimes(1);
  });

  it('失败：抛错不触发 refetch', async () => {
    vi.mocked(expenseApi.update).mockRejectedValueOnce(
      new ApiError(1003, '无权操作'),
    );

    const s = useExpenseStore();
    await expect(s.update(7, { amount: 1 })).rejects.toBeInstanceOf(ApiError);
    expect(expenseApi.list).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------
// 7. remove
// ---------------------------------------------------------------
describe('useExpenseStore / remove', () => {
  it('成功：转发 delete 并 refetch', async () => {
    vi.mocked(expenseApi.delete).mockResolvedValueOnce(undefined);
    vi.mocked(expenseApi.list).mockResolvedValueOnce(mockListResp(0, []));

    const s = useExpenseStore();
    await s.remove(7);
    expect(expenseApi.delete).toHaveBeenCalledWith(7);
    expect(expenseApi.list).toHaveBeenCalledTimes(1);
  });

  it('失败：抛错不触发 refetch', async () => {
    vi.mocked(expenseApi.delete).mockRejectedValueOnce(
      new ApiError(1003, '无权操作'),
    );

    const s = useExpenseStore();
    await expect(s.remove(99)).rejects.toBeInstanceOf(ApiError);
    expect(expenseApi.list).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------
// 8. resetFilter — plan T10 case 4
// ---------------------------------------------------------------
describe('useExpenseStore / resetFilter', () => {
  it('清空过滤项 + 回到第 1 页 + 重新拉列表', async () => {
    const s = useExpenseStore();
    s.filter = {
      category: 'MEAL',
      from: '2026-07-01T00:00:00+08:00',
      to: '2026-07-31T23:59:59+08:00',
      page: 3,
      size: 50,
    };
    s.page = { current: 3, size: 50, total: 100 };
    vi.mocked(expenseApi.list).mockResolvedValueOnce(mockListResp(0, []));

    await s.resetFilter();

    expect(s.filter).toEqual({ page: 1, size: 50 });
    expect(s.page.current).toBe(1);
    expect(expenseApi.list).toHaveBeenCalledWith({ page: 1, size: 50 });
  });
});

// ---------------------------------------------------------------
// 9. dialog state — plan T10 case 5/6
// ---------------------------------------------------------------
describe('useExpenseStore / dialog state', () => {
  it('openDialog(create) 设 visible + mode，currentItem 为 null', () => {
    const s = useExpenseStore();
    s.openDialog('create');
    expect(s.dialogVisible).toBe(true);
    expect(s.dialogMode).toBe('create');
    expect(s.currentItem).toBeNull();
  });

  it('openDialog(edit, item) 设 visible + mode + currentItem', () => {
    const s = useExpenseStore();
    const item: ExpenseResponse = {
      id: 5,
      userId: 1,
      amount: 30.0,
      category: 'TRANSPORT',
      note: '地铁',
      occurredAt: '2026-07-15T18:00:00+08:00',
      createdAt: '2026-07-15T18:00:01+08:00',
      updatedAt: '2026-07-15T18:00:01+08:00',
    };
    s.openDialog('edit', item);
    expect(s.dialogVisible).toBe(true);
    expect(s.dialogMode).toBe('edit');
    expect(s.currentItem).toEqual(item);
  });

  it('closeDialog 清 visible + currentItem，避免残留', () => {
    const s = useExpenseStore();
    const item: ExpenseResponse = {
      id: 5,
      userId: 1,
      amount: 30.0,
      category: 'TRANSPORT',
      note: null,
      occurredAt: '2026-07-15T18:00:00+08:00',
      createdAt: '2026-07-15T18:00:01+08:00',
      updatedAt: '2026-07-15T18:00:01+08:00',
    };
    s.openDialog('edit', item);
    expect(s.currentItem).not.toBeNull();

    s.closeDialog();
    expect(s.dialogVisible).toBe(false);
    expect(s.currentItem).toBeNull();
    // closeDialog 不改 mode（下次 openDialog 时由调用方决定）
  });

  it('closeDialog 后再 openDialog(create) 不会带回旧 item', () => {
    const s = useExpenseStore();
    s.openDialog('edit', {
      id: 5,
      userId: 1,
      amount: 30.0,
      category: 'TRANSPORT',
      note: 'old',
      occurredAt: '2026-07-15T18:00:00+08:00',
      createdAt: '2026-07-15T18:00:01+08:00',
      updatedAt: '2026-07-15T18:00:01+08:00',
    });
    s.closeDialog();
    s.openDialog('create');
    expect(s.dialogMode).toBe('create');
    expect(s.currentItem).toBeNull();
    expect(s.dialogVisible).toBe(true);
  });
});