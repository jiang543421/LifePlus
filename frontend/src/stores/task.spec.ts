import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useTaskStore } from './task';
import { ApiError } from '@/api/http';
import type { TaskListItem, TaskListResponse, TaskResponse, TaskStatus } from '@/types';

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
    expect(store.errorCode).toBeNull();
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
    expect(store.errorCode).toBeNull();
    expect(store.filter).toEqual({ page: 1, size: 20 });
  });
});

describe('useTaskStore / mutations (create / patchStatus / remove)', () => {
  beforeEach(() => {
    vi.mocked(taskApi.create).mockReset();
    vi.mocked(taskApi.patchStatus).mockReset();
    vi.mocked(taskApi.delete).mockReset();
  });

  it('create：成功转发请求并清空旧 error/errorCode', async () => {
    const created: TaskResponse = {
      id: 7,
      userId: 1,
      planId: null,
      title: '买牛奶',
      status: 0 as TaskStatus,
      priority: 2,
      dueDate: null,
      tag: null,
      createdAt: '2026-07-16T10:00:00+08:00',
      updatedAt: '2026-07-16T10:00:00+08:00',
    };
    vi.mocked(taskApi.create).mockResolvedValue(created);

    const store = useTaskStore();
    store.error = '旧错';
    store.errorCode = 1003;
    const resp = await store.create({ title: '买牛奶', priority: 2 });
    expect(resp).toEqual(created);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
    expect(taskApi.create).toHaveBeenCalledWith({ title: '买牛奶', priority: 2 });
  });

  it('patchStatus：转发 id + status', async () => {
    vi.mocked(taskApi.patchStatus).mockResolvedValue(undefined);

    const store = useTaskStore();
    await store.patchStatus(7, 1);
    expect(taskApi.patchStatus).toHaveBeenCalledWith(7, { status: 1 });
  });

  it('remove：失败抛错不写 errorCode（视图负责 toast）', async () => {
    vi.mocked(taskApi.delete).mockRejectedValueOnce(new ApiError(1003, '无权操作'));

    const store = useTaskStore();
    await expect(store.remove(99)).rejects.toBeInstanceOf(ApiError);
    // mutation action 设计上不写 errorCode（与 fetchList 不同）— 调用方 try/catch 自行处理
    expect(store.errorCode).toBeNull();
  });
});

describe('useTaskStore / fetchList errorCode 写入', () => {
  it('ApiError 时 errorCode 同步写入', async () => {
    vi.mocked(taskApi.list).mockRejectedValueOnce(new ApiError(1004, '资源不存在'));
    const store = useTaskStore();
    await store.fetchList();
    expect(store.error).toBe('资源不存在');
    expect(store.errorCode).toBe(1004);
  });

  it('成功时 errorCode 重置为 null', async () => {
    vi.mocked(taskApi.list).mockRejectedValueOnce(new ApiError(1004, 'no'));
    const store = useTaskStore();
    await store.fetchList();
    expect(store.errorCode).toBe(1004);

    vi.mocked(taskApi.list).mockResolvedValueOnce(mockResp(0, []));
    await store.fetchList();
    expect(store.errorCode).toBeNull();
    expect(store.error).toBeNull();
  });
});

describe('useTaskStore / fetchByPlan', () => {
  beforeEach(() => {
    vi.mocked(taskApi.byPlan).mockReset();
  });

  it('默认 byPlanTasks=null，byPlanLoading=false，byPlanError=null', () => {
    const store = useTaskStore();
    expect(store.byPlanTasks).toBeNull();
    expect(store.byPlanLoading).toBe(false);
    expect(store.byPlanError).toBeNull();
  });

  it('成功：byPlanTasks 更新、byPlanLoading 翻转、byPlanError 清空', async () => {
    const items: TaskListItem[] = [
      { id: 1, title: '准备材料', status: 0, priority: 2, dueDate: '2026-08-15', tag: null },
      { id: 2, title: '预订会议室', status: 1, priority: 1, dueDate: null, tag: 'work' },
    ];
    vi.mocked(taskApi.byPlan).mockResolvedValue(items);

    const store = useTaskStore();
    expect(store.byPlanLoading).toBe(false);
    const resp = await store.fetchByPlan(7);
    expect(store.byPlanLoading).toBe(false);
    expect(store.byPlanTasks).toEqual(items);
    expect(store.byPlanError).toBeNull();
    expect(resp).toEqual(items);
    expect(taskApi.byPlan).toHaveBeenCalledWith(7);
  });

  it('失败 ApiError：写 byPlanError、保留旧 byPlanTasks、loading 翻回 false', async () => {
    const items: TaskListItem[] = [
      { id: 1, title: '旧', status: 0, priority: 0, dueDate: null, tag: null },
    ];
    vi.mocked(taskApi.byPlan).mockResolvedValueOnce(items);

    const store = useTaskStore();
    await store.fetchByPlan(7);
    expect(store.byPlanTasks).toEqual(items);

    vi.mocked(taskApi.byPlan).mockRejectedValueOnce(new ApiError(1003, '无权操作该任务'));
    const resp = await store.fetchByPlan(7);
    expect(resp).toBeNull();
    expect(store.byPlanError).toBe('无权操作该任务');
    expect(store.byPlanTasks).toEqual(items);
    expect(store.byPlanLoading).toBe(false);
  });

  it('失败非 ApiError：byPlanError 写通用消息', async () => {
    vi.mocked(taskApi.byPlan).mockRejectedValueOnce(new Error('network down'));

    const store = useTaskStore();
    await store.fetchByPlan(7);
    expect(store.byPlanError).toBe('fetch by-plan tasks failed');
  });

  it('再次成功：byPlanError 重置为 null', async () => {
    vi.mocked(taskApi.byPlan).mockRejectedValueOnce(new ApiError(1004, 'no'));
    const store = useTaskStore();
    await store.fetchByPlan(7);
    expect(store.byPlanError).toBe('no');

    vi.mocked(taskApi.byPlan).mockResolvedValueOnce([]);
    await store.fetchByPlan(7);
    expect(store.byPlanError).toBeNull();
    expect(store.byPlanTasks).toEqual([]);
  });
});