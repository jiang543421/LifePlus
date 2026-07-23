### Task 9.7: HomeView 集成

**Files:**
- Modify: `frontend/src/views/HomeView.vue`（如不存在则新建）
- Create: `frontend/src/__tests__/views/HomeView.test.ts`

- [ ] **Step 1: 检查 HomeView 既有实现**

```powershell
Test-Path frontend/src/views/HomeView.vue
```

> 既有 HomeView 应已含 6 占位卡（per MVP1 spec）。

- [ ] **Step 2: 在 HomeView 加入 AiInsightCard（替换/新增第 1 卡）**

打开 `frontend/src/views/HomeView.vue`，在 6 卡网格**最前面**插入：

```vue
<template>
  <div class="home-view">
    <h2>LifePulse</h2>
    <div class="card-grid">
      <AiInsightCard @open="drawerOpen = true" class="card-featured" />

      <ModuleCard title="任务" icon="List" />
      <ModuleCard title="日程" icon="Calendar" />
      <ModuleCard title="消费" icon="Wallet" />
      <ModuleCard title="饮食" icon="Knife" />
      <ModuleCard title="日报" icon="Document" />
      <ModuleCard title="AI 分析" icon="MagicStick" highlight />
    </div>

    <AiInsightDrawer v-model="drawerOpen" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AiInsightCard from '@/components/AiInsightCard.vue'
import AiInsightDrawer from '@/components/AiInsightDrawer.vue'
import ModuleCard from '@/components/ModuleCard.vue'

const drawerOpen = ref(false)
</script>

<style scoped>
.card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.card-featured {
  grid-column: 1 / -1;
}
@media (max-width: 768px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 3: 写 HomeView 测试 `HomeView.test.ts`**

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import { setActivePinia, createPinia } from 'pinia'

describe('HomeView', () => {
  it('renders AiInsightCard', () => {
    setActivePinia(createPinia())
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: HomeView }],
    })
    const wrapper = mount(HomeView, {
      global: { plugins: [router] },
    })
    expect(wrapper.findComponent({ name: 'AiInsightCard' }).exists()).toBe(true)
  })
})
```

- [ ] **Step 4: 运行测试**

```powershell
cd frontend; pnpm exec vitest run src/__tests__/views/HomeView.test.ts
```

预期：`Tests run: 1`。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/HomeView.vue frontend/src/__tests__/views/HomeView.test.ts
git commit -m "feat(ai): integrate AiInsightCard into HomeView"
```

---
