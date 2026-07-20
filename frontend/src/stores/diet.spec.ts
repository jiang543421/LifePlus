import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useDietStore } from './diet';
import { ApiError } from '@/api/http';
import type {
  CreateDietRequest,
  DietFrequentItem,
  DietListItem,
  DietListResponse,
  DietResponse,
  DietSummary,
  UpdateDietRequest,
} from '@/types';

vi.mock('@/api/diet', () => ({
  dietApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    summary: vi.fn(),
    frequent: vi.fn(),
  },
}));

import { dietApi } from '@/api/diet';

const mockListResp = (total: number, items: unknown[]): DietListResponse => ({
  items: items as DietListResponse['items'],
  total,
  page: 1,
  size: 20,
});

const mockItem = (overrides: Partial<DietListItem> = {}): DietListItem => ({
  id: 1,
  mealType: 'LUNCH',
  name: '米饭',
  kcal: 230,
  proteinG: 5,
  carbG: 50,
  fatG: 1,
  note: null,
  occurredAt: '2026-07-15T12:00:00+08:00',
  ...overrides,
});

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(dietApi.list).mockReset();
  vi.mocked(dietApi.create).mockReset();
  vi.mocked(dietApi.update).mockReset();
  vi.mocked(dietApi.delete).mockReset();
  vi.mocked(dietApi.summary).mockReset();
  vi.mocked(dietApi.frequent).mockReset();
});

// ---------- state default ----------

describe('useDietStore / state default', () => {
  it('默认 filter 为 { page:1, size:20 }，list/summary/frequent 都为空', () => {
    const store = useDietStore();
    expect(store.filter).toEqual({ page: 1, size: 20 });
    expect(store.list).toBeNull();
    expect(store.summary).toBeNull();
    expect(store.summaryDate).toBeNull();
    expect(store.frequent).toEqual([]);
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
    expect(store.dialogVisible).toBe(false);
    expect(store.dialogMode).toBe('create');
    expect(store.currentItem).toBeNull();
  });

  it('hasFilter 在无过滤项时为 false', () => {
    const store = useDietStore();
    expect(store.hasFilter).toBe(false);
  });
});

// ---------- setFilter / resetFilter ----------

describe('useDietStore / setFilter + resetFilter', () => {
  it('setFilter 部分 patch 后保持其他字段并把 page 重置回 1', () => {
    const store = useDietStore();
    store.setPage(5, 50);
    expect(store.filter.page).toBe(5);

    store.setFilter({ mealType: 'LUNCH' });
    expect(store.filter).toEqual({ page: 1, size: 50, mealType: 'LUNCH' });
  });

  it('resetFilter 清空所有过滤但保留 size，并触发 fetchList', async () => {
    vi.mocked(dietApi.list).mockResolvedValueOnce(mockListResp(0, []));
    const store = useDietStore();
    store.setFilter({ mealType: 'DINNER', from: '2026-07-01T00:00:00+08:00', to: '2026-07-31T23:59:59+08:00' });
    expect(store.filter.mealType).toBe('DINNER');

    await store.resetFilter();
    expect(store.filter).toEqual({ page: 1, size: 20 });
    expect(dietApi.list).toHaveBeenCalledTimes(1);
  });
});

// ---------- fetchList ----------

describe('useDietStore / fetchList', () => {
  it('成功：list 更新、page 同步、loading 翻转', async () => {
    const items = [
      mockItem({ id: 1, name: '燕麦' }),
      mockItem({ id: 2, name: '米饭' }),
    ];
    vi.mocked(dietApi.list).mockResolvedValueOnce(mockListResp(2, items));

    const store = useDietStore();
    expect(store.loading).toBe(false);
    const resp = await store.fetchList();
    expect(store.loading).toBe(false);
    expect(store.list).toEqual(items);
    expect(store.page).toEqual({ current: 1, size: 20, total: 2 });
    expect(store.error).toBeNull();
    expect(resp).toEqual(mockListResp(2, items));
  });

  it('失败：写 error message，list 保留旧值，loading 翻回 false', async () => {
    const items = [mockItem({ id: 1 })];
    vi.mocked(dietApi.list).mockResolvedValueOnce(mockListResp(1, items));

    const store = useDietStore();
    await store.fetchList();
    expect(store.list).toEqual(items);

    vi.mocked(dietApi.list).mockRejectedValueOnce(new ApiError(1003, '无权操作该饮食'));
    const resp = await store.fetchList();
    expect(resp).toBeNull();
    expect(store.error).toBe('无权操作该饮食');
    expect(store.errorCode).toBe(1003);
    expect(store.list).toEqual(items); // 失败保留
    expect(store.loading).toBe(false);
  });

  it('失败非 ApiError：error 写通用消息', async () => {
    vi.mocked(dietApi.list).mockRejectedValueOnce(new Error('network down'));

    const store = useDietStore();
    await store.fetchList();
    expect(store.error).toBe('fetch diets failed');
    expect(store.errorCode).toBeNull();
  });
});

// ---------- fetchSummary / fetchFrequent ----------

describe('useDietStore / fetchSummary + fetchFrequent', () => {
  it('fetchSummary 成功：summary + summaryDate 同步写入', async () => {
    const summary: DietSummary = {
      kcal: 1600,
      proteinG: 65,
      carbG: 220,
      fatG: 40,
      kcalDeltaYesterday: -120,
      kcalDeltaLastWeek: null,
    };
    vi.mocked(dietApi.summary).mockResolvedValueOnce(summary);

    const store = useDietStore();
    const resp = await store.fetchSummary('2026-07-15');
    expect(store.summary).toEqual(summary);
    expect(store.summaryDate).toBe('2026-07-15');
    expect(resp).toEqual(summary);
    expect(dietApi.summary).toHaveBeenCalledWith('2026-07-15');
  });

  it('fetchSummary 失败 ApiError：写 error，summary 保留旧值', async () => {
    vi.mocked(dietApi.summary).mockRejectedValueOnce(new ApiError(1002, '未登录'));

    const store = useDietStore();
    const resp = await store.fetchSummary('2026-07-15');
    expect(resp).toBeNull();
    expect(store.error).toBe('未登录');
    expect(store.errorCode).toBe(1002);
    expect(store.summary).toBeNull(); // 失败保留 null
  });

  it('fetchFrequent 成功：frequent 数组写入', async () => {
    const items: DietFrequentItem[] = [
      { name: '米饭', avgKcal: 230, avgProteinG: 5, avgCarbG: 50, avgFatG: 1, hitCount: 12 },
      { name: '燕麦', avgKcal: 380, avgProteinG: 10, avgCarbG: 60, avgFatG: 5, hitCount: 8 },
    ];
    vi.mocked(dietApi.frequent).mockResolvedValueOnce(items);

    const store = useDietStore();
    const resp = await store.fetchFrequent();
    expect(store.frequent).toEqual(items);
    expect(resp).toEqual(items);
    expect(dietApi.frequent).toHaveBeenCalledWith(undefined, undefined, undefined);
  });

  it('fetchFrequent 带 from/to/limit 透传', async () => {
    vi.mocked(dietApi.frequent).mockResolvedValueOnce([]);

    const store = useDietStore();
    await store.fetchFrequent('2026-07-01T00:00:00Z', '2026-07-31T23:59:59Z', 20);
    expect(dietApi.frequent).toHaveBeenCalledWith('2026-07-01T00:00:00Z', '2026-07-31T23:59:59Z', 20);
  });
});

// ---------- groupedByDay getter ----------

describe('useDietStore / groupedByDay getter', () => {
  it('空 list 返回空数组', () => {
    const store = useDietStore();
    expect(store.groupedByDay).toEqual([]);
  });

  it('按 occurredAt 日期分组并倒序（最新在前）', async () => {
    const items: DietListItem[] = [
      mockItem({ id: 1, occurredAt: '2026-07-15T08:00:00+08:00', mealType: 'BREAKFAST' }),
      mockItem({ id: 2, occurredAt: '2026-07-15T12:00:00+08:00', mealType: 'LUNCH' }),
      mockItem({ id: 3, occurredAt: '2026-07-14T19:00:00+08:00', mealType: 'DINNER' }),
      mockItem({ id: 4, occurredAt: '2026-07-16T10:00:00+08:00', mealType: 'SNACK' }),
    ];
    vi.mocked(dietApi.list).mockResolvedValueOnce(mockListResp(4, items));

    const store = useDietStore();
    await store.fetchList();

    expect(store.groupedByDay).toEqual([
      { day: '2026-07-16', items: [items[3]] },
      { day: '2026-07-15', items: [items[0], items[1]] },
      { day: '2026-07-14', items: [items[2]] },
    ]);
  });
});

// ---------- mutations ----------

describe('useDietStore / mutations (create / update / remove)', () => {
  it('create：成功转发请求并 refresh list', async () => {
    const created: DietResponse = {
      id: 7,
      userId: 1,
      mealType: 'LUNCH',
      name: '米饭',
      kcal: 230,
      proteinG: 5,
      carbG: 50,
      fatG: 1,
      note: null,
      occurredAt: '2026-07-15T12:00:00+08:00',
      createdAt: '2026-07-15T12:00:00+08:00',
      updatedAt: '2026-07-15T12:00:00+08:00',
    };
    const req: CreateDietRequest = {
      mealType: 'LUNCH',
      name: '米饭',
      kcal: 230,
      proteinG: 5,
      carbG: 50,
      fatG: 1,
      occurredAt: '2026-07-15T12:00:00+08:00',
    };
    vi.mocked(dietApi.create).mockResolvedValueOnce(created);
    vi.mocked(dietApi.list).mockResolvedValueOnce(mockListResp(1, [mockItem({ id: 7 })]));

    const store = useDietStore();
    const resp = await store.create(req);
    expect(resp).toEqual(created);
    expect(dietApi.create).toHaveBeenCalledWith(req);
    expect(dietApi.list).toHaveBeenCalledTimes(1); // 自动 refresh
  });

  it('update：成功转发 id + req 并 refresh list', async () => {
    vi.mocked(dietApi.update).mockResolvedValueOnce(undefined);
    vi.mocked(dietApi.list).mockResolvedValueOnce(mockListResp(0, []));

    const store = useDietStore();
    const req: UpdateDietRequest = { mealType: 'DINNER' };
    await store.update(7, req);
    expect(dietApi.update).toHaveBeenCalledWith(7, req);
    expect(dietApi.list).toHaveBeenCalledTimes(1);
  });

  it('remove：失败抛错不写 errorCode', async () => {
    vi.mocked(dietApi.delete).mockRejectedValueOnce(new ApiError(1003, '无权操作'));

    const store = useDietStore();
    await expect(store.remove(99)).rejects.toBeInstanceOf(ApiError);
    expect(store.errorCode).toBeNull();
  });
});

// ---------- dialog ----------

describe('useDietStore / openDialog + closeDialog', () => {
  it('openDialog(create) 清空 currentItem 并显示', () => {
    const store = useDietStore();
    store.openDialog('edit', mockItem({ id: 9 }) as DietResponse);
    expect(store.dialogVisible).toBe(true);
    expect(store.dialogMode).toBe('edit');

    store.openDialog('create');
    expect(store.dialogMode).toBe('create');
    expect(store.currentItem).toBeNull();
    expect(store.dialogVisible).toBe(true);
  });

  it('closeDialog 隐藏并清空 currentItem', () => {
    const store = useDietStore();
    store.openDialog('edit', mockItem({ id: 9 }) as DietResponse);
    store.closeDialog();
    expect(store.dialogVisible).toBe(false);
    expect(store.currentItem).toBeNull();
  });
});