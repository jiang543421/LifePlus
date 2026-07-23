### T13. ExpenseSummaryCard + ExpenseDialog

**Files:**
- Create: `frontend/src/components/ExpenseSummaryCard.vue`
- Create: `frontend/src/components/ExpenseDialog.vue`
- Test: `frontend/src/components/__tests__/ExpenseDialog.spec.ts`

**Interfaces:**
- Consumes: T9/T10/T12

- [ ] **Step 1**: 创建 ExpenseSummaryCard（ECharts 按需）

```vue
<!-- frontend/src/components/ExpenseSummaryCard.vue -->
<script setup lang="ts">
import { computed, ref, watch, onMounted, nextTick } from 'vue';
import { CATEGORY_LABEL, type ExpenseCategory } from '@/constants/expense';
import { formatAmountWithSymbol, compareDelta } from '@/utils/number';
import type { ExpenseSummary } from '@/types/expense';

const props = defineProps<{ summary: ExpenseSummary | null; year: number; month: number }>();
const emit = defineEmits<{ (e: 'change-month', y: number, m: number): void }>();

const chartEl = ref<HTMLDivElement>();

const ringData = computed(() => {
  if (!props.summary) return [];
  return Object.entries(props.summary.categoryBreakdown).map(([code, value]) => ({
    name: CATEGORY_LABEL[code as ExpenseCategory] ?? code,
    value: parseFloat(value),
  }));
});

const momDelta = computed(() => {
  if (!props.summary?.monthOverMonthDelta) return null;
  return compareDelta(props.summary.totalAmount, props.summary.monthOverMonthDelta);
});
const yoyDelta = computed(() => {
  if (!props.summary?.yearOverYearDelta) return null;
  return compareDelta(props.summary.totalAmount, props.summary.yearOverYearDelta);
});

const prevMonth = () => {
  let m = props.month - 1, y = props.year;
  if (m < 1) { m = 12; y--; }
  emit('change-month', y, m);
};
const nextMonth = () => {
  let m = props.month + 1, y = props.year;
  if (m > 12) { m = 1; y++; }
  emit('change-month', y, m);
};

onMounted(renderChart);
watch(() => props.summary, renderChart);

async function renderChart() {
  await nextTick();
  if (!chartEl.value) return;
  const echarts = await import('echarts/core');
  const { PieChart } = await import('echarts/charts');
  const { TitleComponent, TooltipComponent, LegendComponent } =
    await import('echarts/components');
  const { CanvasRenderer } = await import('echarts/renderers');
  echarts.use([PieChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer]);
  const chart = echarts.init(chartEl.value);
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    series: [{
      type: 'pie', radius: ['40%', '70%'], avoidLabelOverlap: false,
      data: ringData.value,
    }],
  });
}
</script>

<template>
  <div class="summary-card">
    <div class="header">本月支出</div>
    <div class="total">{{ formatAmountWithSymbol(summary?.totalAmount) }}</div>
    <div ref="chartEl" class="chart"></div>
    <div class="deltas">
      <div>同比上月 {{ momDelta ?? '—' }}</div>
      <div>同比去年 {{ yoyDelta ?? '—' }}</div>
    </div>
    <div class="month-nav">
      <el-button @click="prevMonth">◀</el-button>
      <span>{{ year }}-{{ String(month).padStart(2, '0') }}</span>
      <el-button @click="nextMonth">▶</el-button>
    </div>
  </div>
</template>
```

- [ ] **Step 2**: 创建 ExpenseDialog

```vue
<!-- frontend/src/components/ExpenseDialog.vue -->
<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import dayjs from 'dayjs';
import { EXPENSE_CATEGORIES, CATEGORY_LABEL } from '@/constants/expense';
import type { ExpenseResponse, CreateExpenseRequest } from '@/types/expense';
import { useExpenseStore } from '@/stores/expense';

const props = defineProps<{ modelValue: boolean; mode: 'create' | 'edit'; item: ExpenseResponse | null }>();
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void;
  (e: 'success'): void;
}>();

const store = useExpenseStore();
const formRef = ref();

const form = ref<CreateExpenseRequest>({
  amount: '', category: 'MEAL' as any, note: '', occurredAt: dayjs().subtract(5, 'minute').toISOString(),
});

const title = computed(() => props.mode === 'create' ? '新增消费' : '编辑消费');
const isEdit = computed(() => props.mode === 'edit');

watch(() => props.modelValue, (open) => {
  if (!open) return;
  if (props.mode === 'edit' && props.item) {
    form.value = {
      amount: props.item.amount,
      category: props.item.category,
      note: props.item.note ?? '',
      occurredAt: props.item.occurredAt,
    };
  } else {
    form.value = {
      amount: '', category: 'MEAL' as any, note: '',
      occurredAt: dayjs().subtract(5, 'minute').toISOString(),
    };
  }
});

const rules = {
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: (_: any, v: string, cb: any) =>
        parseFloat(v) > 0 ? cb() : cb(new Error('金额必须 > 0')), trigger: 'blur' },
  ],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  occurredAt: [{ required: true, message: '请选择发生时间', trigger: 'change' }],
};

const submit = async () => {
  const ok = await formRef.value.validate().catch(() => false);
  if (!ok) return;
  if (props.mode === 'create') {
    await store.create(form.value);
  } else if (props.item) {
    await store.update(props.item.id, form.value);
  }
  emit('update:modelValue', false);
  emit('success');
};
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="500px"
             @update:model-value="emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="金额" prop="amount">
        <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="1" />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select v-model="form.category">
          <el-option v-for="c in EXPENSE_CATEGORIES" :key="c" :label="CATEGORY_LABEL[c]" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.note" :maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="发生时间" prop="occurredAt">
        <el-date-picker v-model="form.occurredAt" type="datetime" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
```

- [ ] **Step 3]: RED — 写 dialog test

```typescript
// frontend/src/components/__tests__/ExpenseDialog.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import ExpenseDialog from '../ExpenseDialog.vue';
import { useExpenseStore } from '@/stores/expense';

vi.mock('@/api/expense');

describe('ExpenseDialog', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('renders create mode title', () => {
    const w = mount(ExpenseDialog, {
      props: { modelValue: true, mode: 'create', item: null },
      global: { stubs: { ElDialog: true, ElForm: true, ElFormItem: true,
        ElInputNumber: true, ElSelect: true, ElOption: true,
        ElInput: true, ElDatePicker: true, ElButton: true } },
    });
    expect(w.text()).toContain('新增消费');
  });
  it('prefills form in edit mode', () => {
    const item = { id: 1, amount: '20.00', category: 'TRANSPORT' as const,
                   note: '地铁', occurredAt: '2026-07-15T09:00:00',
                   createdAt: '', updatedAt: '' };
    const w = mount(ExpenseDialog, {
      props: { modelValue: true, mode: 'edit', item },
      global: { stubs: { ElDialog: true, ElForm: true, ElFormItem: true,
        ElInputNumber: true, ElSelect: true, ElOption: true,
        ElInput: true, ElDatePicker: true, ElButton: true } },
    });
    expect(w.vm.form.amount).toBe('20.00');
  });
  it('calls store.create on submit', async () => {
    const w = mount(ExpenseDialog, {
      props: { modelValue: true, mode: 'create', item: null },
      global: { stubs: { ElDialog: true, ElForm: true, ElFormItem: true,
        ElInputNumber: true, ElSelect: true, ElOption: true,
        ElInput: true, ElDatePicker: true, ElButton: true } },
    });
    const store = useExpenseStore();
    vi.spyOn(store, 'create').mockResolvedValue();
    w.vm.form = { amount: '10.00', category: 'MEAL' as any, note: '', occurredAt: '2026-07-15T12:00:00' };
    await w.vm.submit();
    expect(store.create).toHaveBeenCalled();
  });
  it('does not submit when validation fails', async () => {
    const w = mount(ExpenseDialog, {
      props: { modelValue: true, mode: 'create', item: null },
      global: { stubs: { ElDialog: true, ElForm: true, ElFormItem: true,
        ElInputNumber: true, ElSelect: true, ElOption: true,
        ElInput: true, ElDatePicker: true, ElButton: true } },
    });
    w.vm.formRef = { validate: () => Promise.reject(new Error('invalid')) };
    const store = useExpenseStore();
    vi.spyOn(store, 'create');
    await w.vm.submit();
    expect(store.create).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 4]: Run → PASS

Run: `cd frontend && pnpm test ExpenseDialog -q`
Expected: 4 tests passed

- [ ] **Step 5**: Commit

```bash
git add frontend/src/components/ExpenseSummaryCard.vue \
        frontend/src/components/ExpenseDialog.vue \
        frontend/src/components/__tests__/ExpenseDialog.spec.ts
git commit -m "feat(expense): add SummaryCard with ECharts ring chart and Dialog component"
```

---
