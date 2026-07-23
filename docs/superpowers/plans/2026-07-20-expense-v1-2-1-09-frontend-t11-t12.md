### T11. Router + Home card code update

**Files:**
- Modify: `frontend/src/router/index.ts`（新增 /expenses 路由 + lazy import）
- Modify: `frontend/src/views/HomeView.vue`（消费卡 placeholder=false，加 @click 跳 /expenses）

**Interfaces:**
- Consumes: T9/T10（视图壳 + store）

- [ ] **Step 1**: 修改 router

```typescript
// frontend/src/router/index.ts — 在 routes 数组加：
{
  path: '/expenses',
  name: 'expense-list',
  component: () => import('@/views/ExpenseView.vue'),
  meta: { requiresAuth: true, title: '消费' },
},
{
  path: '/expenses/:id(\\d+)',
  name: 'expense-detail',
  component: () => import('@/views/ExpenseDetailView.vue'),
  meta: { requiresAuth: true, title: '消费详情' },
},
```

- [ ] **Step 2**: 修改 HomeView（消费卡激活）

打开 `frontend/src/views/HomeView.vue`，找到「消费概览」卡：
- 把 `placeholder: true` 改为 `placeholder: false`
- 把 `disabled` 类移除
- 加 `@click="router.push('/expenses')"`
- 数据源改为 `await homeStore.fetchExpenseSummary()`

具体改动（参考现有 04-home.md §2.4）：
```vue
<el-col :xs="24" :sm="12" :md="8">
  <ModuleCard
    title="本月支出"
    :amount="expenseSummary?.totalAmount ?? '0.00'"
    :items="expenseRecent"
    cta="/expenses"
    cta-label="查看全部 →"
    @click="router.push('/expenses')"
  />
</el-col>
```

```typescript
// <script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
const router = useRouter();
const expenseSummary = ref<{ totalAmount: string } | null>(null);
const expenseRecent = ref<{ label: string; value: string }[]>([]);
onMounted(async () => {
  const now = new Date();
  const r = await expenseApi.summary(now.getFullYear(), now.getMonth() + 1);
  expenseSummary.value = r;
});
```

> 完整 HomeView 改造可能涉及更多字段；本任务只覆盖「消费概览」卡激活。

- [ ] **Step 3**: Type-check + lint

Run: `cd frontend && pnpm run type-check && pnpm run lint`
Expected: 0 errors

- [ ] **Step 4**: Commit

```bash
git add frontend/src/router/index.ts frontend/src/views/HomeView.vue
git commit -m "feat(expense): register routes and activate home expense card"
```

---

### T12. ExpenseList + ExpenseListItem + number utils

**Files:**
- Create: `frontend/src/utils/number.ts`
- Test: `frontend/src/utils/__tests__/number.spec.ts`
- Create: `frontend/src/components/ExpenseList.vue`
- Create: `frontend/src/components/ExpenseListItem.vue`
- Test: `frontend/src/components/__tests__/ExpenseList.spec.ts`

**Interfaces:**
- Consumes: T9 (constants, types), T10 (store)
- Produces: list 渲染 + 金额格式化

- [ ] **Step 1**: 创建 number utils + 测试

```typescript
// frontend/src/utils/number.ts
export function formatAmount(s: string | number | null | undefined): string {
  if (s == null) return '0.00';
  const n = typeof s === 'string' ? parseFloat(s) : s;
  if (!Number.isFinite(n)) return '0.00';
  return n.toFixed(2);
}

export function formatAmountWithSymbol(s: string | number | null | undefined): string {
  return '¥ ' + formatAmount(s);
}

export function compareDelta(current: string, previous: string | null): string | null {
  if (previous == null) return null;
  const c = parseFloat(current), p = parseFloat(previous);
  if (p === 0) return null;
  const diff = c - p;
  const pct = (diff / p) * 100;
  const sign = diff >= 0 ? '+' : '';
  return `${sign}${pct.toFixed(1)}%`;
}
```

```typescript
// frontend/src/utils/__tests__/number.spec.ts
import { describe, it, expect } from 'vitest';
import { formatAmount, formatAmountWithSymbol, compareDelta } from '../number';

describe('number utils', () => {
  it('formatAmount handles null/undefined', () => {
    expect(formatAmount(null)).toBe('0.00');
    expect(formatAmount(undefined)).toBe('0.00');
  });
  it('formatAmount handles string', () => {
    expect(formatAmount('12.5')).toBe('12.50');
  });
  it('formatAmountWithSymbol prepends ¥', () => {
    expect(formatAmountWithSymbol('10')).toBe('¥ 10.00');
  });
  it('compareDelta positive', () => {
    expect(compareDelta('120', '100')).toBe('+20.0%');
  });
  it('compareDelta negative', () => {
    expect(compareDelta('80', '100')).toBe('-20.0%');
  });
  it('compareDelta previous zero returns null', () => {
    expect(compareDelta('100', '0')).toBeNull();
  });
});
```

- [ ] **Step 2**: 创建 ExpenseListItem + ExpenseList

```vue
<!-- frontend/src/components/ExpenseListItem.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import { CATEGORY_LABEL } from '@/constants/expense';
import { formatAmountWithSymbol } from '@/utils/number';
import dayjs from 'dayjs';
import type { ExpenseResponse } from '@/types/expense';

const props = defineProps<{ item: ExpenseResponse }>();
const emit = defineEmits<{ (e: 'edit', id: number): void; (e: 'delete', id: number): void }>();

const categoryLabel = computed(() => CATEGORY_LABEL[props.item.category]);
const amountStr = computed(() => formatAmountWithSymbol(props.item.amount));
const time = computed(() => dayjs(props.item.occurredAt).format('MM-DD HH:mm'));
const note = computed(() => props.item.note && props.item.note.length > 20
  ? props.item.note.slice(0, 20) + '…' : props.item.note ?? '');
</script>

<template>
  <div class="expense-item">
    <span class="category">{{ categoryLabel }}</span>
    <span class="amount">{{ amountStr }}</span>
    <span class="note">「{{ note }}」</span>
    <span class="time">{{ time }}</span>
    <div class="actions">
      <el-button size="small" @click="emit('edit', item.id)">编辑</el-button>
      <el-button size="small" type="danger" @click="emit('delete', item.id)">删除</el-button>
    </div>
  </div>
</template>
```

```vue
<!-- frontend/src/components/ExpenseList.vue -->
<script setup lang="ts">
import { computed } from 'vue';
import dayjs from 'dayjs';
import ExpenseListItem from './ExpenseListItem.vue';
import type { ExpenseResponse } from '@/types/expense';

const props = defineProps<{ items: ExpenseResponse[] }>();
const emit = defineEmits<{ (e: 'edit', id: number): void; (e: 'delete', id: number): void }>();

const grouped = computed(() => {
  const map = new Map<string, ExpenseResponse[]>();
  for (const it of props.items) {
    const k = dayjs(it.occurredAt).format('YYYY-MM-DD ddd');
    if (!map.has(k)) map.set(k, []);
    map.get(k)!.push(it);
  }
  return Array.from(map.entries());
});
</script>

<template>
  <div class="expense-list">
    <div v-for="[day, items] in grouped" :key="day" class="day-group">
      <div class="day-header">▾ {{ day }}</div>
      <ExpenseListItem
        v-for="it in items"
        :key="it.id"
        :item="it"
        @edit="emit('edit', $event)"
        @delete="emit('delete', $event)"
      />
    </div>
  </div>
</template>
```

- [ ] **Step 3**: RED — 写 list test

```typescript
// frontend/src/components/__tests__/ExpenseList.spec.ts
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ExpenseList from '../ExpenseList.vue';
import type { ExpenseResponse } from '@/types/expense';

const mkItem = (id: number, occurredAt: string): ExpenseResponse => ({
  id, amount: '10.00', category: 'MEAL', note: 'test',
  occurredAt, createdAt: occurredAt, updatedAt: occurredAt,
});

describe('ExpenseList', () => {
  it('groups items by day', () => {
    const items = [
      mkItem(1, '2026-07-15T12:00:00'),
      mkItem(2, '2026-07-15T18:00:00'),
      mkItem(3, '2026-07-14T09:00:00'),
    ];
    const w = mount(ExpenseList, { props: { items } });
    expect(w.findAll('.day-group')).toHaveLength(2);
  });
  it('emits edit event', async () => {
    const w = mount(ExpenseList, { props: { items: [mkItem(1, '2026-07-15T12:00:00')] } });
    await w.findAll('button')[0].trigger('click');
    expect(w.emitted('edit')?.[0]).toEqual([1]);
  });
  it('renders empty state when items is []', () => {
    const w = mount(ExpenseList, { props: { items: [] });
    expect(w.findAll('.day-group')).toHaveLength(0);
  });
});
```

- [ ] **Step 4**: Run → PASS

Run: `cd frontend && pnpm test expense.spec utils/number.spec -q`
Expected: 9 tests passed (6 number + 3 list)

- [ ] **Step 5**: Commit

```bash
git add frontend/src/utils/ frontend/src/components/ExpenseList.vue \
        frontend/src/components/ExpenseListItem.vue
git commit -m "feat(expense): add ExpenseList/Item components and number utils"
```

---
