## PR 7 — Drawer 详情页 + HomeView 集成

涵盖 spec §13 T9.6 - T9.7。完成此 PR 后，UI 全流程可用：首页卡片 → 点击 → drawer 详情。

### Task 9.6: AiInsightDrawer 组件

**Files:**
- Create: `frontend/src/components/AiInsightDrawer.vue`
- Create: `frontend/src/__tests__/components/AiInsightDrawer.test.ts`

- [ ] **Step 1: 写失败测试 `AiInsightDrawer.test.ts`**

```typescript
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import AiInsightDrawer from '@/components/AiInsightDrawer.vue'
import { useAiStore } from '@/stores/ai'
import * as aiApi from '@/api/ai'

describe('AiInsightDrawer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('is hidden when modelValue is false', () => {
    const wrapper = mount(AiInsightDrawer, {
      props: { modelValue: false },
    })
    expect(wrapper.find('[data-test="drawer"]').exists()).toBe(false)
  })

  it('shows insight detail when opened', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockResolvedValue({
      headline: 'detail headline',
      chips: [
        { key: 'taskCompletion', label: '任务完成率', value: '80', unit: '%',
          trend: 'up', deltaText: '↑ 比昨天 +20%' },
      ],
      generatedAt: '2026-07-21T10:00:00Z',
      freshnessSeconds: 60,
    })

    const wrapper = mount(AiInsightDrawer, {
      props: { modelValue: true },
    })
    await wrapper.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))

    expect(wrapper.text()).toContain('detail headline')
    expect(wrapper.text()).toContain('↑ 比昨天 +20%')
  })

  it('emits update:modelValue on close', async () => {
    const wrapper = mount(AiInsightDrawer, {
      props: { modelValue: true },
    })
    await wrapper.findComponent({ name: 'ElDrawer' }).vm.$emit('update:modelValue', false)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('shows refresh button + loading state', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockResolvedValue({
      headline: 'h', chips: [], generatedAt: '', freshnessSeconds: 0,
    })
    const wrapper = mount(AiInsightDrawer, {
      props: { modelValue: true },
    })
    await wrapper.vm.$nextTick()
    await new Promise(r => setTimeout(r, 0))

    expect(wrapper.find('[data-test="refresh-btn"]').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: 运行测试，预期 FAIL**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/components/AiInsightDrawer.test.ts
```

预期：`Component not found`。

- [ ] **Step 3: 实现 `AiInsightDrawer.vue`**

```vue
<template>
  <el-drawer
    :model-value="modelValue"
    @update:model-value="(v) => $emit('update:modelValue', v)"
    title="AI 洞察详情"
    direction="rtl"
    size="480px"
    data-test="drawer"
  >
    <div v-if="store.loading && !store.insight" data-test="loading">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else-if="store.hasError" class="error" data-test="error">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ store.error }}</span>
    </div>

    <template v-else-if="store.insight">
      <h3 class="headline">{{ store.insight.headline }}</h3>

      <div class="chips-detail">
        <div
          v-for="chip in store.insight.chips"
          :key="chip.key"
          class="chip-row"
          :data-test="`chip-${chip.key}`"
        >
          <div class="chip-label">{{ chip.label }}</div>
          <div class="chip-value" :class="`trend-${chip.trend}`">
            <template v-if="chip.value !== null">
              {{ chip.value }} {{ chip.unit }}
            </template>
            <template v-else>
              <span class="placeholder">—</span>
            </template>
          </div>
          <div v-if="chip.deltaText" class="chip-delta">
            {{ chip.deltaText }}
          </div>
        </div>
      </div>

      <el-divider />

      <div class="meta">
        生成时间：{{ formatTime(store.insight.generatedAt) }}
      </div>

      <div class="actions">
        <el-button
          data-test="refresh-btn"
          :loading="store.loading"
          @click="store.refresh()"
        >
          立即刷新
        </el-button>
      </div>
    </template>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAiStore } from '@/stores/ai'
import { WarningFilled } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

defineProps<{ modelValue: boolean }>()
defineEmits<{ 'update:modelValue': [value: boolean] }>()

const store = useAiStore()

onMounted(() => {
  if (!store.insight) {
    store.loadInsight()
  }
})

function formatTime(iso: string): string {
  if (!iso) return ''
  return dayjs(iso).format('YYYY-MM-DD HH:mm:ss')
}
</script>

<style scoped>
.headline {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 24px;
  line-height: 1.5;
}
.chips-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chip-row {
  padding: 16px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}
.chip-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.chip-value {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 4px;
}
.trend-up { color: var(--el-color-success); }
.trend-down { color: var(--el-color-danger); }
.trend-flat { color: var(--el-text-color-regular); }
.trend-none { color: var(--el-text-color-placeholder); }
.placeholder { color: var(--el-text-color-placeholder); }
.chip-delta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.actions {
  margin-top: 16px;
}
.error {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-color-warning);
  padding: 16px;
}
</style>
```

- [ ] **Step 4: 运行测试，预期 PASS**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/components/AiInsightDrawer.test.ts
```

预期：`Tests run: 4`。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/AiInsightDrawer.vue frontend/src/__tests__/components/AiInsightDrawer.test.ts
git commit -m "feat(ai): add AiInsightDrawer detail component"
```

---
