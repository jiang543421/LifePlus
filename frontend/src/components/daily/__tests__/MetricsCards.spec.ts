import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import TaskMetricsCard from '@/components/daily/TaskMetricsCard.vue';
import PlanMetricsCard from '@/components/daily/PlanMetricsCard.vue';
import ExpenseMetricsCard from '@/components/daily/ExpenseMetricsCard.vue';
import DietMetricsCard from '@/components/daily/DietMetricsCard.vue';
import type {
  DietMetrics,
  ExpenseMetrics,
  PlanMetrics,
  TaskMetrics,
} from '@/types';

// ---------------- helpers ----------------

const mkTask = (over: Partial<TaskMetrics> = {}): TaskMetrics => ({
  completedCount: 3,
  totalCount: 5,
  completionRate: 0.6,
  statusDistribution: { TODO: 2, DONE: 3, CANCELLED: 0 },
  priorityDistribution: { NONE: 1, LOW: 1, MEDIUM: 2, HIGH: 1 },
  ...over,
});

const mkPlan = (over: Partial<PlanMetrics> = {}): PlanMetrics => ({
  eventCount: 2,
  totalMinutes: 90,
  categoryDistribution: {},
  busiestHour: 10,
  ...over,
});

const mkExpense = (over: Partial<ExpenseMetrics> = {}): ExpenseMetrics => ({
  totalAmount: 128.5,
  count: 3,
  categoryBreakdown: {
    MEAL: 78.5,
    SHOPPING: 0,
    TRANSPORT: 50,
    SUBSCRIPTION: 0,
    OTHER: 0,
  },
  topCategories: [
    { code: 'MEAL', amount: 78.5 },
    { code: 'TRANSPORT', amount: 50 },
  ],
  ...over,
});

const mkDiet = (over: Partial<DietMetrics> = {}): DietMetrics => ({
  enabled: false,
  value: null,
  reason: '饮食模块暂未启用（v1.2.4+ 启用）',
  ...over,
});

// ---------------- TaskMetricsCard ----------------

describe('TaskMetricsCard', () => {
  it('显示完成数 / 总数（"3 / 5"）', () => {
    const w = mount(TaskMetricsCard, {
      props: { task: mkTask() },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-task-summary"]').text()).toContain('3 / 5');
  });

  it('completed/total=3/5 → 进度条 60%', () => {
    const w = mount(TaskMetricsCard, {
      props: { task: mkTask() },
      global: { plugins: [ElementPlus] },
    });
    const el = w.find('[data-testid="daily-task-progress"]').element as HTMLElement;
    // el-progress 内部用 lineWidth 控制宽度；用 progress 文本断言（el-progress 显示百分比）
    expect(w.find('[data-testid="daily-task-progress"]').text()).toContain('60%');
  });

  it('total=0 → 显示 "0 / 0" + 进度 0%（无除零）', () => {
    const w = mount(TaskMetricsCard, {
      props: { task: mkTask({ completedCount: 0, totalCount: 0, completionRate: 0 }) },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-task-summary"]').text()).toContain('0 / 0');
    expect(w.find('[data-testid="daily-task-progress"]').text()).toContain('0%');
  });

  it('渲染 statusDistribution 三项 TODO / DONE / CANCELLED', () => {
    const w = mount(TaskMetricsCard, {
      props: { task: mkTask() },
      global: { plugins: [ElementPlus] },
    });
    const dist = w.find('[data-testid="daily-task-status-dist"]').text();
    expect(dist).toContain('TODO');
    expect(dist).toContain('DONE');
    expect(dist).toContain('CANCELLED');
  });
});

// ---------------- PlanMetricsCard ----------------

describe('PlanMetricsCard', () => {
  it('显示事件数 "2"', () => {
    const w = mount(PlanMetricsCard, {
      props: { plan: mkPlan() },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-plan-event-count"]').text()).toContain('2');
  });

  it('显示总分钟数 "90 分钟"', () => {
    const w = mount(PlanMetricsCard, {
      props: { plan: mkPlan() },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-plan-total-minutes"]').text()).toContain('90');
    expect(w.find('[data-testid="daily-plan-total-minutes"]').text()).toContain('分钟');
  });

  it('busiestHour=10 → 显示 "10:00"', () => {
    const w = mount(PlanMetricsCard, {
      props: { plan: mkPlan({ busiestHour: 10 }) },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-plan-busiest-hour"]').text()).toContain('10:00');
  });

  it('busiestHour=null → 显示 "—"', () => {
    const w = mount(PlanMetricsCard, {
      props: { plan: mkPlan({ busiestHour: null }) },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-plan-busiest-hour"]').text()).toContain('—');
  });

  it('eventCount=0 → 事件数显示 "0" + 无 busiestHour 文案', () => {
    const w = mount(PlanMetricsCard, {
      props: { plan: mkPlan({ eventCount: 0, totalMinutes: 0, busiestHour: null }) },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-plan-event-count"]').text()).toContain('0');
    expect(w.find('[data-testid="daily-plan-busiest-hour"]').text()).toContain('—');
  });
});

// ---------------- ExpenseMetricsCard ----------------

describe('ExpenseMetricsCard', () => {
  it('显示当日总额 "128.50"', () => {
    const w = mount(ExpenseMetricsCard, {
      props: { expense: mkExpense() },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-expense-total"]').text()).toContain('128.50');
  });

  it('topCategories 按 amount 降序渲染（MEAL 78.5 在 TRANSPORT 50 之前）', () => {
    const w = mount(ExpenseMetricsCard, {
      props: { expense: mkExpense() },
      global: { plugins: [ElementPlus] },
    });
    const html = w.find('[data-testid="daily-expense-top"]').text();
    const mealIdx = html.indexOf('MEAL');
    const transportIdx = html.indexOf('TRANSPORT');
    expect(mealIdx).toBeGreaterThanOrEqual(0);
    expect(transportIdx).toBeGreaterThan(mealIdx);
  });

  it('categoryBreakdown 固定 5 键全渲染（MEAL/SHOPPING/TRANSPORT/SUBSCRIPTION/OTHER）', () => {
    const w = mount(ExpenseMetricsCard, {
      props: { expense: mkExpense() },
      global: { plugins: [ElementPlus] },
    });
    const html = w.find('[data-testid="daily-expense-breakdown"]').text();
    for (const k of ['MEAL', 'SHOPPING', 'TRANSPORT', 'SUBSCRIPTION', 'OTHER']) {
      expect(html).toContain(k);
    }
  });

  it('零消费场景：totalAmount=0 + count=0 → 总额 "0.00"', () => {
    const w = mount(ExpenseMetricsCard, {
      props: {
        expense: mkExpense({
          totalAmount: 0,
          count: 0,
          categoryBreakdown: { MEAL: 0, SHOPPING: 0, TRANSPORT: 0, SUBSCRIPTION: 0, OTHER: 0 },
          topCategories: [],
        }),
      },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-expense-total"]').text()).toContain('0.00');
  });
});

// ---------------- DietMetricsCard ----------------

describe('DietMetricsCard', () => {
  it('enabled=false → 渲染占位文案「暂未启用」', () => {
    const w = mount(DietMetricsCard, {
      props: { diet: mkDiet() },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-diet-placeholder"]').exists()).toBe(true);
    expect(w.find('[data-testid="daily-diet-placeholder"]').text()).toContain('暂未启用');
  });

  it('enabled=false → 显示 reason 副文案（来自后端 frozen reason）', () => {
    const w = mount(DietMetricsCard, {
      props: { diet: mkDiet({ reason: '饮食模块暂未启用（v1.2.4+ 启用）' }) },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="daily-diet-placeholder"]').text()).toContain('v1.2.4+ 启用');
  });

  it('enabled=true 时不渲染占位卡（spec §3.1：v1.2.4 冻结，本测试仅占位；当前不会触发）', () => {
    const w = mount(DietMetricsCard, {
      props: {
        diet: mkDiet({
          enabled: true,
          value: { kcal: 1680, proteinG: 55, carbG: 220, fatG: 50 },
          reason: '',
        }),
      },
      global: { plugins: [ElementPlus] },
    });
    // 占位卡不渲染；具体 enabled=true 的真实渲染留待饮食解冻后单独写组件 spec
    expect(w.find('[data-testid="daily-diet-placeholder"]').exists()).toBe(false);
  });
});
