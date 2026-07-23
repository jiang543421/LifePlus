## 6. Frontend Tasks

### T9. Constants + Types + API client

**Files:**
- Create: `frontend/src/constants/expense.ts`
- Create: `frontend/src/types/expense.ts`
- Create: `frontend/src/api/expense.ts`

**Interfaces:**
- Consumes: T4 DTO shapes
- Produces: typed API client

- [ ] **Step 1**: 创建 constants

```typescript
// frontend/src/constants/expense.ts
export const EXPENSE_CATEGORIES = ['MEAL', 'SHOPPING', 'TRANSPORT', 'SUBSCRIPTION', 'OTHER'] as const;
export type ExpenseCategory = typeof EXPENSE_CATEGORIES[number];

export const CATEGORY_LABEL: Record<ExpenseCategory, string> = {
  MEAL: '餐饮',
  SHOPPING: '购物',
  TRANSPORT: '交通',
  SUBSCRIPTION: '订阅',
  OTHER: '其他',
};

export const EXPENSE_PAGE_SIZE = 20;
export const EXPENSE_MAX_PAGE_SIZE = 100;
```

- [ ] **Step 2**: 创建 types

```typescript
// frontend/src/types/expense.ts
import type { ExpenseCategory } from '@/constants/expense';

export interface ExpenseResponse {
  id: number;
  amount: string;          // BigDecimal 序列化为字符串
  category: ExpenseCategory;
  note: string | null;
  occurredAt: string;      // ISO datetime
  createdAt: string;
  updatedAt: string;
}

export interface CreateExpenseRequest {
  amount: string;
  category: ExpenseCategory;
  note?: string;
  occurredAt: string;
}

export interface UpdateExpenseRequest {
  amount?: string;
  category?: ExpenseCategory;
  note?: string;
  occurredAt?: string;
}

export interface ExpenseSummary {
  totalAmount: string;
  categoryBreakdown: Record<ExpenseCategory, string>;
  monthOverMonthDelta: string | null;
  yearOverYearDelta: string | null;
}

export interface CategoryItem {
  code: ExpenseCategory;
  label: string;
}
```

- [ ] **Step 3**: 创建 API client

```typescript
// frontend/src/api/expense.ts
import { http } from './http';
import type {
  CreateExpenseRequest, UpdateExpenseRequest,
  ExpenseResponse, ExpenseSummary, CategoryItem,
} from '@/types/expense';

export interface Page<T> { items: T[]; total: number; page: number; size: number }

export const expenseApi = {
  list: (params: { category?: string; from?: string; to?: string; page?: number; size?: number }) =>
    http.get<Page<ExpenseResponse>>('/api/v1/expenses', { params }),
  get: (id: number) => http.get<ExpenseResponse>(`/api/v1/expenses/${id}`),
  create: (req: CreateExpenseRequest) => http.post<ExpenseResponse>('/api/v1/expenses', req),
  update: (id: number, req: UpdateExpenseRequest) => http.patch<void>(`/api/v1/expenses/${id}`, req),
  remove: (id: number) => http.delete<void>(`/api/v1/expenses/${id}`),
  summary: (year: number, month: number) =>
    http.get<ExpenseSummary>('/api/v1/expenses/summary', { params: { year, month } }),
  categories: () => http.get<CategoryItem[]>('/api/v1/expenses/categories'),
};
```

- [ ] **Step 4**: Type-check 验证

Run: `cd frontend && pnpm run type-check`
Expected: 0 errors

- [ ] **Step 5**: Commit

```bash
git add frontend/src/constants/expense.ts \
        frontend/src/types/expense.ts \
        frontend/src/api/expense.ts
git commit -m "feat(expense): add constants, types, and API client"
```

---

### T10. Pinia store

**Files:**
- Create: `frontend/src/stores/expense.ts`
- Test: `frontend/src/stores/__tests__/expense.spec.ts`

**Interfaces:**
- Consumes: T9 (api)
- Produces: useExpenseStore

- [ ] **Step 1**: RED — 写 store test

```typescript
// frontend/src/stores/__tests__/expense.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useExpenseStore } from '../expense';
import { expenseApi } from '@/api/expense';

vi.mock('@/api/expense');

describe('useExpenseStore', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('fetchList populates list and total', async () => {
    vi.mocked(expenseApi.list).mockResolvedValue({
      items: [{ id: 1, amount: '10.00', category: 'MEAL', note: null,
                occurredAt: '2026-07-15T12:00:00', createdAt: '', updatedAt: '' }],
      total: 1, page: 1, size: 20 });
    const s = useExpenseStore();
    await s.fetchList();
    expect(s.list).toHaveLength(1);
    expect(s.page.total).toBe(1);
  });

  it('fetchSummary populates summary', async () => {
    vi.mocked(expenseApi.summary).mockResolvedValue({
      totalAmount: '100.00',
      categoryBreakdown: { MEAL: '60.00', SHOPPING: '40.00' } as any,
      monthOverMonthDelta: '20.00',
      yearOverYearDelta: null,
    });
    const s = useExpenseStore();
    await s.fetchSummary(2026, 7);
    expect(s.summary?.totalAmount).toBe('100.00');
  });

  it('create appends and refreshes', async () => {
    vi.mocked(expenseApi.create).mockResolvedValue({
      id: 99, amount: '5.00', category: 'MEAL', note: null,
      occurredAt: '', createdAt: '', updatedAt: '' });
    vi.mocked(expenseApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const s = useExpenseStore();
    await s.create({
      amount: '5.00', category: 'MEAL', occurredAt: '2026-07-15T12:00:00',
    });
    expect(expenseApi.create).toHaveBeenCalled();
    expect(expenseApi.list).toHaveBeenCalled();
  });

  it('resetFilter clears filter and refetches', async () => {
    vi.mocked(expenseApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const s = useExpenseStore();
    s.filter.category = 'MEAL';
    await s.resetFilter();
    expect(s.filter.category).toBeNull();
  });

  it('openDialog sets dialogVisible and mode', () => {
    const s = useExpenseStore();
    s.openDialog('create');
    expect(s.dialogVisible).toBe(true);
    expect(s.dialogMode).toBe('create');
  });

  it('closeDialog clears dialog state', () => {
    const s = useExpenseStore();
    s.openDialog('edit');
    s.closeDialog();
    expect(s.dialogVisible).toBe(false);
    expect(s.currentItem).toBeNull();
  });
});
```

- [ ] **Step 2**: Run → FAIL

Run: `cd frontend && pnpm test expense.spec -t useExpenseStore -q`
Expected: FAIL (module not found)

- [ ] **Step 3**: GREEN — 实现 store

```typescript
// frontend/src/stores/expense.ts
import { defineStore } from 'pinia';
import { expenseApi } from '@/api/expense';
import type {
  ExpenseResponse, ExpenseSummary,
  CreateExpenseRequest, UpdateExpenseRequest,
} from '@/types/expense';

export interface ExpenseFilter {
  category: string | null;
  from: string | null;
  to: string | null;
}

export const useExpenseStore = defineStore('expense', {
  state: () => ({
    list: [] as ExpenseResponse[],
    filter: { category: null, from: null, to: null } as ExpenseFilter,
    summary: null as ExpenseSummary | null,
    page: { current: 1, size: 20, total: 0 },
    loading: false,
    dialogVisible: false,
    dialogMode: 'create' as 'create' | 'edit',
    currentItem: null as ExpenseResponse | null,
  }),
  getters: {
    hasData: (s) => s.list.length > 0,
  },
  actions: {
    async fetchList() {
      this.loading = true;
      try {
        const r = await expenseApi.list({
          category: this.filter.category ?? undefined,
          from: this.filter.from ?? undefined,
          to: this.filter.to ?? undefined,
          page: this.page.current,
          size: this.page.size,
        });
        this.list = r.items;
        this.page.total = r.total;
      } finally { this.loading = false; }
    },
    async fetchSummary(year: number, month: number) {
      this.summary = await expenseApi.summary(year, month);
    },
    async create(req: CreateExpenseRequest) {
      await expenseApi.create(req);
      await this.fetchList();
    },
    async update(id: number, req: UpdateExpenseRequest) {
      await expenseApi.update(id, req);
      await this.fetchList();
    },
    async remove(id: number) {
      await expenseApi.remove(id);
      await this.fetchList();
    },
    async resetFilter() {
      this.filter = { category: null, from: null, to: null };
      this.page.current = 1;
      await this.fetchList();
    },
    openDialog(mode: 'create' | 'edit', item?: ExpenseResponse) {
      this.dialogMode = mode;
      this.currentItem = item ?? null;
      this.dialogVisible = true;
    },
    closeDialog() {
      this.dialogVisible = false;
      this.currentItem = null;
    },
  },
});
```

- [ ] **Step 4**: Run → PASS

Run: `cd frontend && pnpm test expense.spec -q`
Expected: 6 tests passed

- [ ] **Step 5**: Commit

```bash
git add frontend/src/stores/expense.ts \
        frontend/src/stores/__tests__/expense.spec.ts
git commit -m "feat(expense): add Pinia store with 6 actions and Vitest coverage"
```

---
