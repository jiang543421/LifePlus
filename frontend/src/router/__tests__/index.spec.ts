import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import TaskListView from '@/views/TaskListView.vue';
import TaskDetailView from '@/views/TaskDetailView.vue';
import HomeView from '@/views/HomeView.vue';
import LoginView from '@/views/LoginView.vue';
import RegisterView from '@/views/RegisterView.vue';

/**
 * 用 fresh router 实例跑守卫，避免 import router 单例在多个测试间相互污染。
 * 路由表 + 守卫逻辑必须与 src/router/index.ts 完全一致（这是有意重复 — 单测用 memory history）。
 */
function buildGuardRouter() {
  const routes = [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true, title: '登录' } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true, title: '注册' } },
    { path: '/', name: 'home', component: HomeView, meta: { title: '数字生活' } },
    { path: '/tasks', name: 'tasks', component: TaskListView, meta: { title: '任务' } },
    { path: '/tasks/:id(\\d+)', name: 'task-detail', component: TaskDetailView, meta: { title: '任务详情' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ];

  const router = createRouter({
    history: createMemoryHistory(),
    routes,
  });

  router.beforeEach((to, _from, next) => {
    const auth = useAuthStore();
    if (auth.isLoggedIn && (to.name === 'login' || to.name === 'register')) {
      return next({ name: 'home' });
    }
    if (to.meta.public) return next();
    if (!auth.isLoggedIn) {
      return next({ name: 'login', query: { redirect: to.fullPath } });
    }
    next();
  });

  return router;
}

beforeEach(() => {
  setActivePinia(createPinia());
  // auth store 在 state() 时从 localStorage 读取，跨测试必须清空避免污染
  localStorage.clear();
});

describe('router / guard', () => {
  it('未登录访问 /tasks → 跳 /login?redirect=/tasks', async () => {
    const router = buildGuardRouter();
    await router.push('/tasks');
    await router.isReady();
    expect(router.currentRoute.value.name).toBe('login');
    expect(router.currentRoute.value.query.redirect).toBe('/tasks');
  });

  it('已登录访问 /tasks 不重定向', async () => {
    const auth = useAuthStore();
    auth.setTokens('access', 'refresh');
    auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });

    const router = buildGuardRouter();
    await router.push('/tasks');
    await router.isReady();
    expect(router.currentRoute.value.name).toBe('tasks');
  });

  it('已登录访问 /login → 重定向到 /', async () => {
    const auth = useAuthStore();
    auth.setTokens('access', 'refresh');
    auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });

    const router = buildGuardRouter();
    await router.push('/login');
    await router.isReady();
    expect(router.currentRoute.value.name).toBe('home');
  });

  it('未登录访问 /login 不重定向（public）', async () => {
    const router = buildGuardRouter();
    await router.push('/login');
    await router.isReady();
    expect(router.currentRoute.value.name).toBe('login');
  });

  it('路由表包含 /tasks/:id(\\d+) 数字约束', async () => {
    const auth = useAuthStore();
    auth.setTokens('access', 'refresh');
    auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });

    const router = buildGuardRouter();
    await router.push('/tasks/42');
    await router.isReady();
    expect(router.currentRoute.value.name).toBe('task-detail');
    expect(router.currentRoute.value.params.id).toBe('42');
  });

  it('/tasks/abc（非数字）被 catch-all 重定向到 /', async () => {
    const auth = useAuthStore();
    auth.setTokens('access', 'refresh');
    auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });

    const router = buildGuardRouter();
    await router.push('/tasks/abc');
    await router.isReady();
    expect(router.currentRoute.value.path).toBe('/');
  });
});