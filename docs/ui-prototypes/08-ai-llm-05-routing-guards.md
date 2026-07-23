## 5. 路由与守卫

### 5.1 路由表

```typescript
// frontend/src/router/index.ts
const routes = [
  // ... 既有路由
  {
    path: '/ai-analysis',
    name: 'AiAnalysis',
    component: () => import('@/views/AiAnalysisView.vue'),
    meta: { requiresAuth: true, title: 'AI 分析' },
  },
];
```

### 5.2 鉴权守卫

```typescript
router.beforeEach((to, _, next) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.fullPath } });
  } else {
    next();
  }
});
```

### 5.3 Pinia Store 复用

```typescript
// frontend/src/stores/aiInsight.ts（v2.0 沿用 + 扩展）
export const useAiInsightStore = defineStore('aiInsight', () => {
  const insight = ref<AiInsight | null>(null);
  const loading = ref(false);
  const error = ref<ApiError | null>(null);

  async function load() {
    loading.value = true;
    try {
      const r = await api.get('/api/v1/ai/insight/today');
      insight.value = r.data;
    } catch (e) {
      error.value = e as ApiError;
    } finally {
      loading.value = false;
    }
  }

  async function refresh() {
    loading.value = true;
    try {
      const r = await api.post('/api/v1/ai/insight/refresh');
      insight.value = r.data;
    } catch (e) {
      error.value = e as ApiError;
    } finally {
      loading.value = false;
    }
  }

  return { insight, loading, error, load, refresh };
});
```

> **AiDrawer 和 AiAnalysisView 共用同一 store**；抽屉打开不发起新请求；独立分析页通过 `insight.value` 直接读，避免重复请求。

---
