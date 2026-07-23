## 6. Frontend Tasks(M3-M4)

### M3 阶段

#### T9. types/daily.ts + api/daily.ts

**Files:**
- Create: `frontend/src/types/daily.ts`
- Create: `frontend/src/api/daily.ts`

- [ ] **Step 1**: 创建 types

```typescript
// frontend/src/types/daily.ts
export interface TaskMetrics {
  completedCount: number;
  totalCount: number;
  completionRate: number;        // 0.0 ~ 1.0
  statusDistribution: Record<string, number>;
  priorityDistribution: Record<string, number>;
}

export interface PlanMetrics {
  eventCount: number;
  totalMinutes: number;          // 排除全天事件
  categoryDistribution: Record<string, number>;
  busiestHour: number | null;
}

export interface ExpenseMetrics {
  totalAmount: string;           // BigDecimal 序列化为字符串
  count: number;
  categoryBreakdown: Record<string, string>;
  topCategories: Array<{ code: string; amount: string }>;
}

export interface DietValue {
  kcal: string;
  proteinG: string;
  carbG: string;
  fatG: string;
}

export interface DietMetrics {
  enabled: boolean;              // v1.2.3 永远 false
  value: DietValue | null;       // enabled=false 时 null
  reason: string | null;         // "饮食模块未上线"
}

export interface DailyReportPayload {
  date: string;                  // "YYYY-MM-DD"
  task: TaskMetrics;
  plan: PlanMetrics;
  expense: ExpenseMetrics;
  diet: DietMetrics;
}

export interface WeeklyTriplet {
  current: number;
  previous: number;
  delta: number | null;          // null 表示 previous=0
}

export interface WeeklyComparison {
  taskCompletion: WeeklyTriplet;
  planEvents: WeeklyTriplet;
  expenseAmount: WeeklyTriplet;
}

export interface WeeklyReportPayload {
  isoWeek: string;               // "2026-W29"
  weekStart: string;             // "YYYY-MM-DD" 周一
  weekEnd: string;               // "YYYY-MM-DD" 周日
  comparison: WeeklyComparison;
}
```

- [ ] **Step 2**: 创建 API client

```typescript
// frontend/src/api/daily.ts
import { http } from './http';
import type { DailyReportPayload, WeeklyReportPayload } from '@/types/daily';

export const dailyApi = {
  getDailyReport: (date: string) =>
    http.get<DailyReportPayload>('/api/daily', { params: { date } }),
  getWeeklyReport: (isoWeek: string) =>
    http.get<WeeklyReportPayload>('/api/daily/week', { params: { week: isoWeek } }),
};
```

- [ ] **Step 3**: Type-check

Run: `cd frontend && pnpm run type-check`
Expected: 0 errors

- [ ] **Step 4**: Commit

```bash
git add frontend/src/types/daily.ts frontend/src/api/daily.ts
git commit -m "feat(daily): add types and API client"
```

---
