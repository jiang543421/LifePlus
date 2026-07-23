### Task 9.5: AiInsightCard 组件

**Files:**
- Create: `frontend/src/components/AiInsightCard.vue`
- Create: `frontend/src/__tests__/components/AiInsightCard.test.ts`

- [ ] **Step 1: 写失败测试 `AiInsightCard.test.ts`**

```typescript
import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import AiInsightCard from '@/components/AiInsightCard.vue'
import { useAiStore } from '@/stores/ai'
import * as aiApi from '@/api/ai'

describe('AiInsightCard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows loading skeleton initially', () => {
    const wrapper = mount(AiInsightCard)
    expect(wrapper.find('[data-test="skeleton"]').exists()).toBe(true)
  })

  it('shows insight after load', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockResolvedValue({
      headline: '今日完成 5 个任务',
      chips: [{
        key: 'taskCompletion', label: '任务完成率',
        value: '80', unit: '%', trend: 'up', deltaText: '↑ 比昨天',
      }],
      generatedAt: '2026-07-21T10:00:00Z',
      freshnessSeconds: 60,
    })

    const wrapper = mount(AiInsightCard)
    await flushPromises()

    expect(wrapper.text()).toContain('今日完成 5 个任务')
    expect(wrapper.text()).toContain('80')
  })

  it('opens drawer on click', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockResolvedValue({
      headline: 'h',
      chips: [],
      generatedAt: '2026-07-21T10:00:00Z',
      freshnessSeconds: 60,
    })
    const wrapper = mount(AiInsightCard)
    await flushPromises()

    await wrapper.find('[data-test="card-root"]').trigger('click')
    expect(wrapper.emitted('open')).toBeTruthy()
  })

  it('shows error message when load fails', async () => {
    vi.spyOn(aiApi, 'getTodayInsight').mockRejectedValue({
      code: 1501, message: 'AI 服务暂不可用',
    })
    const wrapper = mount(AiInsightCard)
    await flushPromises()

    expect(wrapper.find('[data-test="error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('AI 服务暂不可用')
  })
})
```

- [ ] **Step 2: 运行测试，预期 FAIL**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/components/AiInsightCard.test.ts
```

预期：`Component not found`。

- [ ] **Step 3: 实现 `AiInsightCard.vue`**

```vue
<template>
  <div
    class="ai-insight-card"
    data-test="card-root"
    @click="handleOpen"
  >
    <div v-if="store.loading && !store.insight" data-test="skeleton">
      <el-skeleton :rows="2" animated />
    </div>

    <div v-else-if="store.hasError" data-test="error" class="error">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ store.error }}</span>
      <el-button size="small" @click.stop="store.refresh()">重试</el-button>
    </div>

    <template v-else-if="store.insight">
      <div class="headline" data-test="headline">
        {{ store.insight.headline }}
      </div>
      <div v-if="store.insight.chips.length" class="chips">
        <AiChipItem
          v-for="chip in store.insight.chips"
          :key="chip.key"
          :chip="chip"
        />
      </div>
      <div class="footer">
        <span class="freshness">
          {{ freshnessLabel }}
        </span>
        <el-button
          size="small"
          link
          data-test="refresh-btn"
          @click.stop="store.refresh()"
        >
          刷新
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAiStore } from '@/stores/ai'
import AiChipItem from './AiChipItem.vue'
import { WarningFilled } from '@element-plus/icons-vue'

const emit = defineEmits<{ open: [] }>()
const store = useAiStore()

const freshnessLabel = computed(() => {
  if (!store.insight) return ''
  const sec = store.insight.freshnessSeconds
  if (sec < 60) return `${sec} 秒前`
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  return `${Math.floor(sec / 3600)} 小时前`
})

onMounted(() => store.loadInsight())

function handleOpen() {
  emit('open')
}
</script>

<style scoped>
.ai-insight-card {
  cursor: pointer;
  padding: 16px;
  border-radius: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  transition: box-shadow 0.2s;
}
.ai-insight-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.headline {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  line-height: 1.4;
}
.chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.error {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-color-warning);
}
</style>
```

- [ ] **Step 4: 运行测试，预期 PASS**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/components/AiInsightCard.test.ts
```

预期：`Tests run: 4`。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/AiInsightCard.vue frontend/src/__tests__/components/AiInsightCard.test.ts
git commit -m "feat(ai): add AiInsightCard component with loading/error states"
```

---
