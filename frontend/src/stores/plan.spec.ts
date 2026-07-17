import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { usePlanStore } from './plan';
import { ApiError } from '@/api/http';
import type { PlanListResponse, PlanResponse } from '@/types';

vi.mock('@/api/plan', () => ({
  planApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

import { planApi } from '@/api/plan';

const mockResp = (total: number, items: unknown[]): PlanListResponse => ({
  items: items as PlanListResponse['items'],
  total,
  page: 1,
  size: 20,
});

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(planApi.list).mockReset();
});

describe('usePlanStore / state default', () => {
  it('默认 filter 为 { page:1, size:20 }，list/total 为空', () => {
    const store = usePlanStore();
    expect(store.filter).toEqual({ page: 1, size: 20 });
    expect(store.list).toBeNull();
    expect(store.total).toBe(0);
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
  });

  it('hasFilter 在无过滤项时为 false', () => {
    const store = usePlanStore();
    expect(store.hasFilter).toBe(false);
  });
});

describe('usePlanStore / getters.hasFilter', () => {
  it('任一过滤项设置时为 true，单独翻页不算', () => {
    const store = usePlanStore();
    store.setPage(3);
    expect(store.hasFilter).toBe(false);

    store.setFilter({ from: '2026-08-01T00:00:00' });
    expect(store.hasFilter).toBe(true);
    store.resetFilter();

    store.setFilter({ to: '2026-08-31T23:59:00' });
    expect(store.hasFilter).toBe(true);
    store.resetFilter();

    store.setFilter({
      from: '2026-08-01T00:00:00',
      to: '2026-08-31T23:59:00',
    });
    expect(store.hasFilter).toBe(true);
  });

  it('空字符串视为未设置（避免 UI 上残留空 form 字段）', () => {
    const store = usePlanStore();
    store.setFilter({ from: '' });
    expect(store.hasFilter).toBe(false);
  });
});

describe('usePlanStore / setFilter', () => {
  it('部分 patch 后保持其他字段，重置 page=1', () => {
    const store = usePlanStore();
    store.setPage(5, 50);
    expect(store.filter.page).toBe(5);
    expect(store.filter.size).toBe(50);

    store.setFilter({ from: '2026-08-01T00:00:00' });
    expect(store.filter).toEqual({
      page: 1,
      size: 50,
      from: '2026-08-01T00:00:00',
    });
  });

  it('resetFilter 清空所有过滤但保留 size', () => {
    const store = usePlanStore();
    store.setFilter({
      from: '2026-08-01T00:00:00',
      to: '2026-08-31T23:59:00',
    });
    expect(store.filter.page).toBe(1);

    store.setPage(3, 50);
    store.resetFilter();
    expect(store.filter).toEqual({ page: 1, size: 50 });
  });
});

describe('usePlanStore / fetchList', () => {
  it('成功：list/total 更新，loading 翻转', async () => {
    const items = [
      {
        id: 1,
        title: '周会',
        startTime: '2026-08-01T10:00:00',
        endTime: '2026-08-01T11:00:00',
        allDay: 0,
        location: '会议室 A',
        reminderMin: 15,
      },
      {
        id: 2,
        title: '出差',
        startTime: '2026-08-15T00:00:00',
        endTime: '2026-08-16T23:59:59',
        allDay: 1,
        location: null,
        reminderMin: null,
      },
    ];
    vi.mocked(planApi.list).mockResolvedValue(mockResp(2, items));

    const store = usePlanStore();
    expect(store.loading).toBe(false);
    const resp = await store.fetchList();
    expect(store.loading).toBe(false);
    expect(store.list).toEqual(items);
    expect(store.total).toBe(2);
    expect(store.error).toBeNull();
    expect(resp).toEqual(mockResp(2, items));
  });

  it('失败：写 error message，list 保留旧值，loading 翻回 false', async () => {
    const items = [
      {
        id: 1,
        title: '旧',
        startTime: '2026-08-01T10:00:00',
        endTime: '2026-08-01T11:00:00',
        allDay: 0,
        location: null,
        reminderMin: null,
      },
    ];
    vi.mocked(planApi.list).mockResolvedValueOnce(mockResp(1, items));

    const store = usePlanStore();
    await store.fetchList();
    expect(store.list).toEqual(items);

    vi.mocked(planApi.list).mockRejectedValueOnce(
      new ApiError(1003, '无权操作该计划'),
    );
    const resp = await store.fetchList();
    expect(resp).toBeNull();
    expect(store.error).toBe('无权操作该计划');
    expect(store.list).toEqual(items); // 失败保留
    expect(store.loading).toBe(false);
  });

  it('失败非 ApiError：error 写通用消息', async () => {
    vi.mocked(planApi.list).mockRejectedValueOnce(new Error('network down'));

    const store = usePlanStore();
    await store.fetchList();
    expect(store.error).toBe('fetch plans failed');
  });
});

describe('usePlanStore / clear', () => {
  it('清空 list/total/error，filter 仅重置过滤项（保留 size）', async () => {
    const items = [
      {
        id: 1,
        title: 't',
        startTime: '2026-08-01T10:00:00',
        endTime: '2026-08-01T11:00:00',
        allDay: 0,
        location: null,
        reminderMin: null,
      },
    ];
    vi.mocked(planApi.list).mockResolvedValue(mockResp(1, items));

    const store = usePlanStore();
    store.setFilter({ from: '2026-08-01T00:00:00', to: '2026-08-31T23:59:00' });
    await store.fetchList();
    expect(store.list).not.toBeNull();

    store.clear();
    expect(store.list).toBeNull();
    expect(store.total).toBe(0);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
    expect(store.filter).toEqual({ page: 1, size: 20 });
  });
});

describe('usePlanStore / mutations (create / remove)', () => {
  beforeEach(() => {
    vi.mocked(planApi.create).mockReset();
    vi.mocked(planApi.delete).mockReset();
  });

  it('create：成功转发请求并清空旧 error/errorCode', async () => {
    const created: PlanResponse = {
      id: 7,
      userId: 1,
      title: '周会',
      startTime: '2026-08-01T10:00:00',
      endTime: '2026-08-01T11:00:00',
      allDay: 0,
      location: '会议室 A',
      note: null,
      reminderMin: 15,
      createdAt: '2026-07-17T10:00:00+08:00',
      updatedAt: '2026-07-17T10:00:00+08:00',
    };
    vi.mocked(planApi.create).mockResolvedValue(created);

    const store = usePlanStore();
    store.error = '旧错';
    store.errorCode = 1003;
    const resp = await store.create({
      title: '周会',
      startTime: '2026-08-01T10:00:00',
      endTime: '2026-08-01T11:00:00',
      location: '会议室 A',
    });
    expect(resp).toEqual(created);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
    expect(planApi.create).toHaveBeenCalledWith({
      title: '周会',
      startTime: '2026-08-01T10:00:00',
      endTime: '2026-08-01T11:00:00',
      location: '会议室 A',
    });
  });

  it('remove：失败抛错不写 errorCode（视图负责 toast）', async () => {
    vi.mocked(planApi.delete).mockRejectedValueOnce(
      new ApiError(1003, '无权操作'),
    );

    const store = usePlanStore();
    await expect(store.remove(99)).rejects.toBeInstanceOf(ApiError);
    // mutation action 设计上不写 errorCode（与 fetchList 不同）— 调用方 try/catch 自行处理
    expect(store.errorCode).toBeNull();
  });
});

describe('usePlanStore / fetchList errorCode 写入', () => {
  it('ApiError 时 errorCode 同步写入', async () => {
    vi.mocked(planApi.list).mockRejectedValueOnce(
      new ApiError(1004, '资源不存在'),
    );
    const store = usePlanStore();
    await store.fetchList();
    expect(store.error).toBe('资源不存在');
    expect(store.errorCode).toBe(1004);
  });

  it('成功时 errorCode 重置为 null', async () => {
    vi.mocked(planApi.list).mockRejectedValueOnce(
      new ApiError(1004, 'no'),
    );
    const store = usePlanStore();
    await store.fetchList();
    expect(store.errorCode).toBe(1004);

    vi.mocked(planApi.list).mockResolvedValueOnce(mockResp(0, []));
    await store.fetchList();
    expect(store.errorCode).toBeNull();
    expect(store.error).toBeNull();
  });
});