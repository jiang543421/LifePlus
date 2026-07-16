import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useTaskStore } from './task';
import { ApiError } from '@/api/http';
import type { TaskListResponse } from '@/types';

vi.mock('@/api/task', () => ({
  taskApi: {
    list: vi.fn(),
    byPlan: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    patchStatus: vi.fn(),
    delete: vi.fn(),
  },
}));

import { taskApi } from '@/api/task';

const mockResp = (total: number, items: unknown[]): TaskListResponse => ({
  items: items as TaskListResponse['items'],
  total,
  page: 1,
  size: 20,
});

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(taskApi.list).mockReset();
});

describe('useTaskStore / state default', () => {
  it('默认 filter 为 { page:1, size:20 }，list/total 为空', () => {
    const store = useTaskStore();
    expect(store.filter).toEqual({ page: 1, size: 20 });
    expect(store.list).toBeNull();
    expect(store.total).toBe(0);
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
  });

  it('hasFilter 在无过滤项时为 false', () => {
    const store = useTaskStore();
    expect(store.hasFilter).toBe(false);
  });
});

describe('useTaskStore / getters.hasFilter', () => {
  it('任一过滤项设置时为 true，单独翻页不算', () => {
    const store = useTaskStore();
    store.setPage(3);
    expect(store.hasFilter).toBe(false);

    store.setFilter({ status: 0 });
    expect(store.hasFilter).toBe(true);
    store.resetFilter();

    store.setFilter({ priority: 2 });
    expect(store.hasFilter).toBe(true);
    store.resetFilter();

    store.setFilter({ tag: 'work' });
    expect(store.hasFilter).toBe(true);
    store.resetFilter();

    store.setFilter({ dueFrom: '2026-08-01' });
    expect(store.hasFilter).toBe(true);
    store.resetFilter();

    store.setFilter({ dueTo: '2026-08-31' });
    expect(store.hasFilter).toBe(true);
  });
});

describe('useTaskStore / setFilter', () => {
  it('部分 patch 后保持其他字段，重置 page=1', () => {
    const store = useTaskStore();
    store.setPage(5, 50);
    expect(store.filter.page).toBe(5);
    expect(store.filter.size).toBe(50);

    store.setFilter({ tag: 'work' });
    expect(store.filter).toEqual({ page: 1, size: 50, tag: 'work' });
  });

  it('resetFilter 清空所有过滤但保留 size', () => {
    const store = useTaskStore();
    store.setFilter({ status: 1, priority: 2, tag: 'work', dueFrom: '2026-08-01', dueTo: '2026-08-31' });
    expect(store.filter.page).toBe(1);

    store.setPage(3, 50);
    store.resetFilter();
    expect(store.filter).toEqual({ page: 1, size: 50 });
  });
});

describe('useTaskStore / fetchList', () => {
  it('成功：list/total 更新，loading 翻转', async () => {
    const items = [
      { id: 1, title: '买菜', status: 0, priority: 2, dueDate: null, tag: null },
      { id: 2, title: '写日报', status: 1, priority: 0, dueDate: null, tag: null },
    ];
    vi.mocked(taskApi.list).mockResolvedValue(mockResp(2, items));

    const store = useTaskStore();
    expect(store.loading).toBe(false);
    const resp = await store.fetchList();
    expect(store.loading).toBe(false);
    expect(store.list).toEqual(items);
    expect(store.total).toBe(2);
    expect(store.error).toBeNull();
    expect(resp).toEqual(mockResp(2, items));
  });

  it('失败：写 error message，list 保留旧值，loading 翻回 false', async () => {
    const items = [{ id: 1, title: '旧', status: 0, priority: 0, dueDate: null, tag: null }];
    vi.mocked(taskApi.list).mockResolvedValueOnce(mockResp(1, items));

    const store = useTaskStore();
    await store.fetchList();
    expect(store.list).toEqual(items);

    vi.mocked(taskApi.list).mockRejectedValueOnce(new ApiError(1003, '无权操作该任务'));
    const resp = await store.fetchList();
    expect(resp).toBeNull();
    expect(store.error).toBe('无权操作该任务');
    expect(store.list).toEqual(items); // 失败保留
    expect(store.loading).toBe(false);
  });

  it('失败非 ApiError：error 写通用消息', async () => {
    vi.mocked(taskApi.list).mockRejectedValueOnce(new Error('network down'));

    const store = useTaskStore();
    await store.fetchList();
    expect(store.error).toBe('fetch tasks failed');
  });
});

describe('useTaskStore / clear', () => {
  it('清空 list/total/error，filter 仅重置过滤项（保留 size）', async () => {
    const items = [{ id: 1, title: 't', status: 0, priority: 0, dueDate: null, tag: null }];
    vi.mocked(taskApi.list).mockResolvedValue(mockResp(1, items));

    const store = useTaskStore();
    store.setFilter({ status: 0, tag: 'work' });
    await store.fetchList();
    expect(store.list).not.toBeNull();

    store.clear();
    expect(store.list).toBeNull();
    expect(store.total).toBe(0);
    expect(store.error).toBeNull();
    expect(store.filter).toEqual({ page: 1, size: 20 });
  });
});