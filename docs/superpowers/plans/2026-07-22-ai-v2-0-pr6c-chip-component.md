### Task 9.4: AiChipItem 组件

**Files:**
- Create: `frontend/src/components/AiChipItem.vue`
- Create: `frontend/src/__tests__/components/AiChipItem.test.ts`

- [ ] **Step 1: 写失败测试 `AiChipItem.test.ts`**

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AiChipItem from '@/components/AiChipItem.vue'
import type { AiChip } from '@/types/ai'

describe('AiChipItem', () => {
  const baseChip: AiChip = {
    key: 'taskCompletion',
    label: '任务完成率',
    value: '80',
    unit: '%',
    trend: 'up',
    deltaText: '↑ 比昨天',
  }

  it('renders label, value and unit', () => {
    const wrapper = mount(AiChipItem, { props: { chip: baseChip } })
    expect(wrapper.text()).toContain('任务完成率')
    expect(wrapper.text()).toContain('80')
    expect(wrapper.text()).toContain('%')
  })

  it('renders delta text when present', () => {
    const wrapper = mount(AiChipItem, { props: { chip: baseChip } })
    expect(wrapper.text()).toContain('↑ 比昨天')
  })

  it('renders placeholder for empty chip', () => {
    const empty: AiChip = { ...baseChip, value: null, deltaText: null }
    const wrapper = mount(AiChipItem, { props: { chip: empty } })
    expect(wrapper.text()).toContain('—')
    expect(wrapper.find('[data-test="empty"]').exists()).toBe(true)
  })

  it('applies trend color class', () => {
    const wrapper = mount(AiChipItem, { props: { chip: baseChip } })
    expect(wrapper.classes()).toContain('trend-up')
  })
})
```

- [ ] **Step 2: 运行测试，预期 FAIL**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/components/AiChipItem.test.ts
```

预期：`Component not found`。

- [ ] **Step 3: 实现 `AiChipItem.vue`**

```vue
<template>
  <div
    class="ai-chip"
    :class="`trend-${chip.trend}`"
    :data-test="chip.value === null ? 'empty' : 'filled'"
  >
    <div class="label">{{ chip.label }}</div>
    <div class="value">
      <template v-if="chip.value !== null">
        <span class="num">{{ chip.value }}</span>
        <span class="unit">{{ chip.unit }}</span>
      </template>
      <template v-else>
        <span class="placeholder">—</span>
      </template>
    </div>
    <div v-if="chip.deltaText" class="delta">{{ chip.deltaText }}</div>
  </div>
</template>

<script setup lang="ts">
import type { AiChip } from '@/types/ai'

defineProps<{ chip: AiChip }>()
</script>

<style scoped>
.ai-chip {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  min-width: 96px;
}
.label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.value {
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.num {
  font-size: 20px;
  font-weight: 600;
}
.unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.placeholder {
  font-size: 20px;
  color: var(--el-text-color-placeholder);
}
.delta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.trend-up .num { color: var(--el-color-success); }
.trend-down .num { color: var(--el-color-danger); }
.trend-flat .num { color: var(--el-text-color-regular); }
.trend-none .num { color: var(--el-text-color-placeholder); }
</style>
```

- [ ] **Step 4: 运行测试，预期 PASS**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/components/AiChipItem.test.ts
```

预期：`Tests run: 4`。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/AiChipItem.vue frontend/src/__tests__/components/AiChipItem.test.ts
git commit -m "feat(ai): add AiChipItem component + tests"
```

---
