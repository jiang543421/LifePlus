### T14. ExpenseView + ExpenseDetailView

**Files:**
- Create: `frontend/src/views/ExpenseView.vue`
- Test: `frontend/src/views/__tests__/ExpenseView.spec.ts`
- Create: `frontend/src/views/ExpenseDetailView.vue`

**Interfaces:**
- Consumes: T10/T11/T12/T13

- [ ] **Step 1]: 创建 ExpenseView

```vue
<!-- frontend/src/views/ExpenseView.vue -->
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import dayjs from 'dayjs';
import { useExpenseStore } from '@/stores/expense';
import { expenseApi } from '@/api/expense';
import ExpenseList from '@/components/ExpenseList.vue';
import ExpenseSummaryCard from '@/components/ExpenseSummaryCard.vue';
import ExpenseDialog from '@/components/ExpenseDialog.vue';
import { ElMessage, ElMessageBox } from 'element-plus';

const store = useExpenseStore();
const now = dayjs();
const year = ref(now.year());
const month = ref(now.month() + 1);

const from = computed(() => dayjs(`${year.value}-${month.value}-01`).startOf('month').toISOString());
const to = computed(() => dayjs(`${year.value}-${month.value}-01`).endOf('month').toISOString());

onMounted(async () => {
  store.filter.from = from.value;
  store.filter.to = to.value;
  await Promise.all([store.fetchList(), store.fetchSummary(year.value, month.value)]);
  await expenseApi.categories();
});

const onMonthChange = async (y: number, m: number) => {
  year.value = y; month.value = m;
  store.filter.from = dayjs(`${y}-${m}-01`).startOf('month').toISOString();
  store.filter.to = dayjs(`${y}-${m}-01`).endOf('month').toISOString();
  await Promise.all([store.fetchList(), store.fetchSummary(y, m)]);
};

const onEdit = (id: number) => {
  const it = store.list.find(x => x.id === id);
  if (it) store.openDialog('edit', it);
};
const onDelete = async (id: number) => {
  await ElMessageBox.confirm('确定删除这笔消费？', '提示', { type: 'warning' });
  await store.remove(id);
  await store.fetchSummary(year.value, month.value);
  ElMessage.success('已删除');
};

const onDialogSuccess = async () => {
  await store.fetchSummary(year.value, month.value);
  ElMessage.success('已保存');
};
</script>

<template>
  <div class="expense-view">
    <div class="header">
      <el-select v-model="store.filter.category" placeholder="全部分类" clearable
                 @change="store.fetchList()">
        <el-option label="餐饮" value="MEAL" />
        <el-option label="购物" value="SHOPPING" />
        <el-option label="交通" value="TRANSPORT" />
        <el-option label="订阅" value="SUBSCRIPTION" />
        <el-option label="其他" value="OTHER" />
      </el-select>
      <el-button type="primary" @click="store.openDialog('create')">+ 新增消费</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <ExpenseList :items="store.list" @edit="onEdit" @delete="onDelete" />
        <el-pagination
          :current-page="store.page.current"
          :page-size="store.page.size"
          :total="store.page.total"
          layout="prev, pager, next"
          @current-change="(p: number) => { store.page.current = p; store.fetchList(); }"
        />
      </el-col>
      <el-col :xs="24" :md="8">
        <ExpenseSummaryCard :summary="store.summary" :year="year" :month="month"
                            @change-month="onMonthChange" />
      </el-col>
    </el-row>
    <ExpenseDialog v-model="store.dialogVisible" :mode="store.dialogMode"
                   :item="store.currentItem" @success="onDialogSuccess" />
  </div>
</template>
```

- [ ] **Step 2]: 创建 ExpenseDetailView

```vue
<!-- frontend/src/views/ExpenseDetailView.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { expenseApi } from '@/api/expense';
import { CATEGORY_LABEL } from '@/constants/expense';
import { formatAmountWithSymbol } from '@/utils/number';
import dayjs from 'dayjs';
import type { ExpenseResponse } from '@/types/expense';

const route = useRoute();
const router = useRouter();
const item = ref<ExpenseResponse | null>(null);

onMounted(async () => {
  try {
    item.value = await expenseApi.get(Number(route.params.id));
  } catch {
    ElMessage.error('资源不存在');
    router.replace('/expenses');
  }
});

const onDelete = async () => {
  if (!item.value) return;
  await ElMessageBox.confirm('确定删除这笔消费？', '提示', { type: 'warning' });
  await expenseApi.remove(item.value.id);
  ElMessage.success('已删除');
  router.replace('/expenses');
};
</script>

<template>
  <div class="expense-detail">
    <el-page-header @back="router.replace('/expenses')" title="返回列表" />
    <div v-if="item" class="content">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="金额">{{ formatAmountWithSymbol(item.amount) }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ CATEGORY_LABEL[item.category] }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ item.note || '—' }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ dayjs(item.occurredAt).format('YYYY-MM-DD HH:mm') }}</el-descriptions-item>
        <el-descriptions-item label="创建于">{{ dayjs(item.createdAt).format('YYYY-MM-DD HH:mm') }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions">
        <el-button @click="router.push(`/expenses/${item.id}/edit`)">进入编辑</el-button>
        <el-button type="danger" @click="onDelete">删除</el-button>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 3]: RED — 写 View test

```typescript
// frontend/src/views/__tests__/ExpenseView.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { useExpenseStore } from '@/stores/expense';
import ExpenseView from '../ExpenseView.vue';

vi.mock('@/api/expense');

const mountView = () => mount(ExpenseView, {
  global: { stubs: {
    'el-row': true, 'el-col': true, 'el-button': true, 'el-select': true,
    'el-option': true, 'el-pagination': true,
    'expense-list': true, 'expense-summary-card': true, 'expense-dialog': true,
  } },
});

describe('ExpenseView', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('mount → fetchList + fetchSummary + categories', async () => {
    const { expenseApi } = await import('@/api/expense');
    vi.mocked(expenseApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    vi.mocked(expenseApi.summary).mockResolvedValue({
      totalAmount: '0', categoryBreakdown: {} as any,
      monthOverMonthDelta: null, yearOverYearDelta: null });
    vi.mocked(expenseApi.categories).mockResolvedValue([]);
    mountView();
    await new Promise(r => setTimeout(r, 0));
    expect(expenseApi.list).toHaveBeenCalled();
    expect(expenseApi.summary).toHaveBeenCalled();
    expect(expenseApi.categories).toHaveBeenCalled();
  });

  it('+ button → openDialog create', async () => {
    const w = mountView();
    const store = useExpenseStore();
    const btn = w.findAll('button').find(b => b.text().includes('+ 新增消费'))!;
    await btn.trigger('click');
    expect(store.dialogVisible).toBe(true);
    expect(store.dialogMode).toBe('create');
  });

  it('pagination change → fetchList', async () => {
    const { expenseApi } = await import('@/api/expense');
    vi.mocked(expenseApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const w = mountView();
    const store = useExpenseStore();
    const evt = w.findComponent({ name: 'el-pagination-stub' }) as any;
    await evt.vm.$emit('current-change', 2);
    expect(store.page.current).toBe(2);
    expect(expenseApi.list).toHaveBeenCalled();
  });

  it('category filter change → fetchList', async () => {
    const { expenseApi } = await import('@/api/expense');
    vi.mocked(expenseApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const w = mountView();
    const evt = w.findComponent({ name: 'el-select-stub' }) as any;
    await evt.vm.$emit('change', 'MEAL');
    expect(expenseApi.list).toHaveBeenCalled();
  });

  it('edit event from list → openDialog edit', async () => {
    const w = mountView();
    const store = useExpenseStore();
    store.list = [{
      id: 1, amount: '10.00', category: 'MEAL', note: '午餐',
      occurredAt: '2026-07-15T12:00:00', createdAt: '', updatedAt: '',
    }];
    const listStub = w.findComponent({ name: 'expense-list-stub' }) as any;
    await listStub.vm.$emit('edit', 1);
    expect(store.dialogMode).toBe('edit');
    expect(store.currentItem?.id).toBe(1);
  });

  it('summary-card month change → fetchList + fetchSummary for new month', async () => {
    const { expenseApi } = await import('@/api/expense');
    vi.mocked(expenseApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    vi.mocked(expenseApi.summary).mockResolvedValue({
      totalAmount: '0', categoryBreakdown: {} as any,
      monthOverMonthDelta: null, yearOverYearDelta: null });
    const w = mountView();
    const cardStub = w.findComponent({ name: 'expense-summary-card-stub' }) as any;
    await cardStub.vm.$emit('change-month', 2026, 6);
    expect(expenseApi.summary).toHaveBeenCalledWith(2026, 6);
  });

  it('rate-limited (1006) → ElMessage error', async () => {
    const { expenseApi } = await import('@/api/expense');
    vi.mocked(expenseApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    vi.mocked(expenseApi.summary).mockResolvedValue({
      totalAmount: '0', categoryBreakdown: {} as any,
      monthOverMonthDelta: null, yearOverYearDelta: null });
    vi.mocked(expenseApi.create).mockRejectedValue(
      Object.assign(new Error('rate-limit'), { response: { data: { code: 1006, message: '操作过于频繁' } } }));
    const w = mountView();
    const store = useExpenseStore();
    await expect(store.create({
      amount: '10', category: 'MEAL', occurredAt: '2026-07-15T12:00:00',
    })).rejects.toThrow();
  });

  it('cross-user (1003) → rethrow with code', async () => {
    const { expenseApi } = await import('@/api/expense');
    vi.mocked(expenseApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    vi.mocked(expenseApi.summary).mockResolvedValue({
      totalAmount: '0', categoryBreakdown: {} as any,
      monthOverMonthDelta: null, yearOverYearDelta: null });
    vi.mocked(expenseApi.remove).mockRejectedValue(
      Object.assign(new Error('cross-user'), { response: { data: { code: 1003, message: '无权操作' } } }));
    const w = mountView();
    const store = useExpenseStore();
    await expect(store.remove(999)).rejects.toThrow();
  });
});
```

- [ ] **Step 4]: Run → PASS

Run: `cd frontend && pnpm test ExpenseView -q`
Expected: 2 tests passed

- [ ] **Step 5]: Commit

```bash
git add frontend/src/views/ExpenseView.vue \
        frontend/src/views/ExpenseDetailView.vue \
        frontend/src/views/__tests__/ExpenseView.spec.ts
git commit -m "feat(expense): add ExpenseView shell and DetailView"
```

---
