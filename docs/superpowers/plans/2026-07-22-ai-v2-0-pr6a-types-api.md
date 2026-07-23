## PR 6 — 前端基础（types + api + store + AiChipItem + AiInsightCard）

涵盖 spec §13 T9.1 - T9.5。完成此 PR 后，前端可拉取洞察、显示 3 chip + 标题。

### Task 9.1: TypeScript 类型定义

**Files:**
- Create: `frontend/src/types/ai.ts`

- [ ] **Step 1: 创建 `frontend/src/types/ai.ts`**

```typescript
// 与后端 AiInsightResponse DTO 对齐（spec §6.1）

export type ChipKey = 'taskCompletion' | 'weeklyExpense' | 'planDensity'

export type Trend = 'up' | 'down' | 'flat' | 'none'

export interface AiChip {
  key: ChipKey
  label: string
  value: string | null  // null = 无数据
  unit: string
  trend: Trend
  deltaText: string | null  // null = 空 chip
}

export interface AiInsight {
  headline: string
  chips: AiChip[]
  generatedAt: string  // ISO 8601
  freshnessSeconds: number
}

export const CHIP_LABELS: Record<ChipKey, string> = {
  taskCompletion: '任务完成率',
  weeklyExpense: '本周消费',
  planDensity: '今日日程',
}

export const CHIP_UNITS: Record<ChipKey, string> = {
  taskCompletion: '%',
  weeklyExpense: '¥',
  planDensity: '项',
}

export function isEmptyChip(chip: AiChip): boolean {
  return chip.value === null
}
```

- [ ] **Step 2: 编译验证**

```powershell
cd frontend; pnpm exec tsc --noEmit
```

预期：无错误。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/ai.ts
git commit -m "feat(ai): add frontend AI types"
```

---

### Task 9.2: API 客户端

**Files:**
- Create: `frontend/src/api/ai.ts`

- [ ] **Step 1: 创建 `frontend/src/api/ai.ts`**

```typescript
import { http } from './http'
import type { AiInsight } from '@/types/ai'

/**
 * AI 洞察 REST 客户端（spec §6.1）。
 */

export async function getTodayInsight(): Promise<AiInsight> {
  const { data } = await http.get<{ code: number; data: AiInsight }>(
    '/api/v1/ai/insight/today'
  )
  return data.data
}

export async function refreshInsight(): Promise<AiInsight> {
  const { data } = await http.post<{ code: number; data: AiInsight }>(
    '/api/v1/ai/insight/refresh'
  )
  return data.data
}
```

> 复用项目既有 `http.ts`（已封装 axios + 拦截器 + token）。

- [ ] **Step 2: 编译验证**

```powershell
cd frontend; pnpm exec tsc --noEmit
```

预期：无错误。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/ai.ts
git commit -m "feat(ai): add frontend AI api client"
```

---
