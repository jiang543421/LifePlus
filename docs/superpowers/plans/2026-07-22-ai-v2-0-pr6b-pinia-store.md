### Task 9.3: Pinia store

**Files:**
- Create: `frontend/src/stores/ai.ts`
- Create: `frontend/src/__tests__/stores/ai.test.ts`

- [ ] **Step 1: 写失败测试 `ai.test.ts`**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAiStore } from '@/stores/ai'
import * as aiApi from '@/api/ai'

describe('aiStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads insight and caches timestamp', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockResolvedValue({
      headline: 'test headline',
      chips: [],
      generatedAt: '2026-07-21T10:00:00Z',
      freshnessSeconds: 60,
    })

    const store = useAiStore()
    expect(store.insight).toBeNull()

    await store.loadInsight()

    expect(store.insight?.headline).toBe('test headline')
    expect(store.lastFetchedAt).toBeGreaterThan(0)
    expect(store.loading).toBe(false)
  })

  it('refreshes and replaces insight', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockResolvedValue({
      headline: 'old',
      chips: [],
      generatedAt: '2026-07-21T10:00:00Z',
      freshnessSeconds: 60,
    })
    vi.spyOn(aiApi, 'refreshInsight').mockResolvedValue({
      headline: 'new',
      chips: [],
      generatedAt: '2026-07-21T10:05:00Z',
      freshnessSeconds: 0,
    })

    const store = useAiStore()
    await store.loadInsight()
    expect(store.insight?.headline).toBe('old')

    await store.refresh()
    expect(store.insight?.headline).toBe('new')
    expect(aiApi.refreshInsight).toHaveBeenCalled()
  })

  it('sets error on 503', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockRejectedValue({
      code: 1501,
      message: 'AI 服务暂不可用',
    })

    const store = useAiStore()
    await store.loadInsight()

    expect(store.insight).toBeNull()
    expect(store.error).toContain('AI 服务暂不可用')
  })

  it('does not reload within 30 seconds', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockResolvedValue({
      headline: 'cached',
      chips: [],
      generatedAt: '2026-07-21T10:00:00Z',
      freshnessSeconds: 60,
    })

    const store = useAiStore()
    await store.loadInsight()
    await store.loadInsight()

    expect(aiApi.getTodayInsight).toHaveBeenCalledTimes(1)
  })
})
```

- [ ] **Step 2: 运行测试，预期 FAIL**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/stores/ai.test.ts
```

预期：`store not found`。

- [ ] **Step 3: 实现 `ai.ts` store**

```typescript
import { defineStore } from 'pinia'
import * as aiApi from '@/api/ai'
import type { AiInsight } from '@/types/ai'

interface State {
  insight: AiInsight | null
  lastFetchedAt: number
  loading: boolean
  error: string | null
}

export const useAiStore = defineStore('ai', {
  state: (): State => ({
    insight: null,
    lastFetchedAt: 0,
    loading: false,
    error: null,
  }),
  getters: {
    isFresh: (state) => {
      if (!state.insight) return false
      const elapsedSec = (Date.now() - state.lastFetchedAt) / 1000
      return elapsedSec < 30 && state.insight.freshnessSeconds < 30
    },
    hasError: (state) => state.error !== null,
  },
  actions: {
    async loadInsight(force = false) {
      const elapsedSec = (Date.now() - this.lastFetchedAt) / 1000
      if (!force && this.insight && elapsedSec < 30) return
      this.loading = true
      this.error = null
      try {
        this.insight = await aiApi.getTodayInsight()
        this.lastFetchedAt = Date.now()
      } catch (e: any) {
        this.error = e?.message ?? '加载失败'
      } finally {
        this.loading = false
      }
    },

    async refresh() {
      this.loading = true
      this.error = null
      try {
        this.insight = await aiApi.refreshInsight()
        this.lastFetchedAt = Date.now()
      } catch (e: any) {
        this.error = e?.message ?? '刷新失败'
      } finally {
        this.loading = false
      }
    },

    clearError() {
      this.error = null
    },
  },
})
```

- [ ] **Step 4: 运行测试，预期 PASS**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/stores/ai.test.ts
```

预期：`Tests run: 4`。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/stores/ai.ts frontend/src/__tests__/stores/ai.test.ts
git commit -m "feat(ai): add aiStore with loadInsight/refresh + tests"
```

---
