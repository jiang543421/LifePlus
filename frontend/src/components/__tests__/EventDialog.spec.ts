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

  // C-6：补齐 watch else 分支（ALL_DAY → TIMED 回切）+ buildPayload 缺起止校验
  it('从全天再切回时段时起止补齐 T09:00:00 / T10:00:00 边界（watch else 分支）', async () => {
    // allDayInitial 起止为 '2026-08-20' / '2026-08-21'（slice 0,10 后是日期），切回 timed 要补默认时刻
    const w = await openDialog({ mode: 'edit', initial: allDayInitial });
    expect(isAllDayStart(w)).toBe(true);
    // 关掉全天开关 → TIMED
    await w.find('[data-testid="event-allday"]').trigger('click');
    await flushPromises();
    expect(isAllDayStart(w)).toBe(false);
    // 提交应得到补齐时刻的 payload
    await w.find('[data-testid="event-title"] input').setValue('出差');
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();
    expect(w.emitted('submit')?.[0]?.[0]).toMatchObject({
      title: '出差',
      allDay: 0,
      startTime: '2026-08-20T09:00:00',
      endTime: '2026-08-21T10:00:00',
    });
  });

  it('起止时间为空时校验失败不 emit submit（buildPayload 第二个 return null）', async () => {
    const w = await openDialog({ mode: 'edit', initial: timedInitial });
    // 直接清空表单的 start/end，触发「请选择起止时间」分支
    const form = (w.vm as unknown as { form: { start: string; end: string } }).form;
    form.start = '';
    form.end = '';
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();
    expect(w.emitted('submit')).toBeFalsy();
  });

  // C-6：补 reminderModel computed set 分支（null <-> REMINDER_NONE 哨兵）
  it('reminderModel setter：v === REMINDER_NONE → form.reminderMin = null', async () => {
    // initial.reminderMin = 15，先重置为 null，再触发 setter 走 REMINDER_NONE 分支
    const w = await openDialog({ mode: 'edit', initial: timedInitial });
    const form = (w.vm as unknown as { form: { reminderMin: number | null } }).form;
    form.reminderMin = 15;
    // reminderModel 在 vm proxy 上是 computed，通过赋值触发 setter
    (w.vm as unknown as { reminderModel: number }).reminderModel = -1;
    expect(form.reminderMin).toBeNull();
  });

  it('reminderModel setter：v !== REMINDER_NONE → form.reminderMin = v', async () => {
    const w = await openDialog({ mode: 'edit', initial: timedInitial });
    const form = (w.vm as unknown as { form: { reminderMin: number | null } }).form;
    form.reminderMin = null;
    (w.vm as unknown as { reminderModel: number }).reminderModel = 30;
    expect(form.reminderMin).toBe(30);
  });

  // C-6：补 toAllDay / toTimed 内部边界分支 + resetForm defaultDate ?? fallback
  it("form.start 为空时切换全天：toAllDay('') → ''；切回时段 toTimed('') → ''（falsy 分支）", async () => {
    const w = await openDialog({ mode: 'edit', initial: timedInitial });
    const form = (w.vm as unknown as { form: { start: string; end: string } }).form;
    form.start = '';
    form.end = '';
    // TIMED → ALL_DAY：触发 toAllDay('') → ''
    await w.find('[data-testid="event-allday"]').trigger('click');
    await flushPromises();
    expect(form.start).toBe('');
    expect(form.end).toBe('');
    // ALL_DAY → TIMED：触发 toTimed('', hm) → ''
    await w.find('[data-testid="event-allday"]').trigger('click');
    await flushPromises();
    expect(form.start).toBe('');
    expect(form.end).toBe('');
  });

  it('10 字符 start 来回切换全天：toTimed 走 length<=10 真分支（拼接 hm）', async () => {
    // resetForm 在 timed 模式下 form.start = '2026-08-15' (10 字符)，
    // 切全天 → toAllDay → 不变；切回 → toTimed → length=10 → 拼接默认时刻
    const shortTimedInitial: PlanResponse = {
      ...timedInitial,
      startTime: '2026-08-15',
      endTime: '2026-08-15',
    };
    const w = await openDialog({ mode: 'edit', initial: shortTimedInitial });
    const form = (w.vm as unknown as { form: { start: string; end: string; title: string } }).form;
    expect(form.start).toBe('2026-08-15');
    // TIMED → ALL_DAY
    await w.find('[data-testid="event-allday"]').trigger('click');
    await flushPromises();
    expect(form.start).toBe('2026-08-15');
    // ALL_DAY → TIMED：触发 toTimed 拼接分支
    await w.find('[data-testid="event-allday"]').trigger('click');
    await flushPromises();
    expect(form.start).toBe('2026-08-15T09:00:00');
    expect(form.end).toBe('2026-08-15T10:00:00');
  });

  it('create 模式无 defaultDate → resetForm 走 dayjs().format fallback', async () => {
    // props.defaultDate 为 null/undefined 触发 ?? 右边
    const w = await openDialog({ mode: 'create' });
    const form = (w.vm as unknown as { form: { start: string; end: string; title: string } }).form;
    // 起止必须是非空字符串（YYYY-MM-DDTHH:mm:ss），证明 dayjs fallback 工作
    expect(form.start).toMatch(/^\d{4}-\d{2}-\d{2}T09:00:00$/);
    expect(form.end).toMatch(/^\d{4}-\d{2}-\d{2}T10:00:00$/);
    // 标题保持为空字符串
    expect(form.title).toBe('');
  });
});