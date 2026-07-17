import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import EventDialog from '@/components/EventDialog.vue';
import type { PlanResponse } from '@/types';

const timedInitial: PlanResponse = {
  id: 3,
  userId: 1,
  title: '周会',
  startTime: '2026-08-10T10:00:00',
  endTime: '2026-08-10T11:00:00',
  allDay: 0,
  location: '会议室 A',
  note: '带纪要',
  reminderMin: 15,
  createdAt: '2026-07-17T10:00:00+08:00',
  updatedAt: '2026-07-17T10:00:00+08:00',
};

const allDayInitial: PlanResponse = {
  ...timedInitial,
  id: 4,
  title: '出差',
  startTime: '2026-08-20T00:00:00',
  endTime: '2026-08-21T23:59:59',
  allDay: 1,
  location: null,
  note: null,
  reminderMin: null,
};

interface DialogProps {
  mode: 'create' | 'edit';
  initial?: PlanResponse | null;
  defaultDate?: string | null;
}

async function openDialog(props: DialogProps) {
  const w = mount(EventDialog, {
    props: { modelValue: false, ...props },
    global: { plugins: [ElementPlus] },
  });
  await w.setProps({ modelValue: true });
  await flushPromises();
  return w;
}

/** 通过 el-date-editor 类型 class 判断当前模式（el-date-picker 不透传 data-*）。 */
function isAllDayStart(w: ReturnType<typeof mount>): boolean {
  return w.find('[data-testid="event-start"] .el-date-editor--date').exists();
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('EventDialog / create', () => {
  it('默认日期预填起止时段，标题为空（timed 模式）', async () => {
    const w = await openDialog({ mode: 'create', defaultDate: '2026-08-15' });
    const title = w.find('[data-testid="event-title"] input');
    expect((title.element as HTMLInputElement).value).toBe('');
    expect(isAllDayStart(w)).toBe(false);
  });

  it('填标题后提交 emit submit 携带完整 payload', async () => {
    const w = await openDialog({ mode: 'create', defaultDate: '2026-08-15' });
    await w.find('[data-testid="event-title"] input').setValue('午餐');
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();
    expect(w.emitted('submit')?.[0]?.[0]).toEqual({
      title: '午餐',
      startTime: '2026-08-15T09:00:00',
      endTime: '2026-08-15T10:00:00',
      allDay: 0,
      location: null,
      note: null,
      reminderMin: null,
    });
  });

  it('标题为空时校验失败不 emit submit', async () => {
    const w = await openDialog({ mode: 'create', defaultDate: '2026-08-15' });
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();
    expect(w.emitted('submit')).toBeFalsy();
  });

  it('点击取消 emit update:modelValue=false', async () => {
    const w = await openDialog({ mode: 'create', defaultDate: '2026-08-15' });
    await w.find('[data-testid="event-cancel"]').trigger('click');
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([false]);
  });
});

describe('EventDialog / edit', () => {
  it('用 initial 预填标题与时段（timed 模式）', async () => {
    const w = await openDialog({ mode: 'edit', initial: timedInitial });
    expect((w.find('[data-testid="event-title"] input').element as HTMLInputElement).value).toBe('周会');
    expect(isAllDayStart(w)).toBe(false);
  });

  it('保存按钮文案为「保存」并提交保留原时段与 reminder', async () => {
    const w = await openDialog({ mode: 'edit', initial: timedInitial });
    expect(w.find('[data-testid="event-submit"]').text()).toBe('保存');
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();
    expect(w.emitted('submit')?.[0]?.[0]).toEqual({
      title: '周会',
      startTime: '2026-08-10T10:00:00',
      endTime: '2026-08-10T11:00:00',
      allDay: 0,
      location: '会议室 A',
      note: '带纪要',
      reminderMin: 15,
    });
  });

  it('全天事件渲染 date picker（带 el-date-editor--date 类）并补齐边界秒', async () => {
    const w = await openDialog({ mode: 'edit', initial: allDayInitial });
    expect(isAllDayStart(w)).toBe(true);
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();
    expect(w.emitted('submit')?.[0]?.[0]).toMatchObject({
      allDay: 1,
      startTime: '2026-08-20T00:00:00',
      endTime: '2026-08-21T23:59:59',
    });
  });

  it('结束不晚于开始时校验失败不 emit submit', async () => {
    const bad: PlanResponse = {
      ...timedInitial,
      startTime: '2026-08-10T11:00:00',
      endTime: '2026-08-10T10:00:00',
    };
    const w = await openDialog({ mode: 'edit', initial: bad });
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();
    expect(w.emitted('submit')).toBeFalsy();
  });

  it('切换全天开关时起止粒度从 datetime 转为 date', async () => {
    const w = await openDialog({ mode: 'edit', initial: timedInitial });
    expect(isAllDayStart(w)).toBe(false);
    await w.find('[data-testid="event-allday"]').trigger('click');
    await flushPromises();
    expect(isAllDayStart(w)).toBe(true);
  });
});