import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

// Phase 1.4 只落地 /login /register /home 占位；任务/日程/设置视图留到 Phase 2/3/4。
const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
  { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { public: true, title: '注册' } },
  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { title: '数字生活' } },
  // spec §04 §2：/tasks /tasks/:id 都需登录；:id 限定数字避免 catch-all 误吞。
  { path: '/tasks', name: 'tasks', component: () => import('@/views/TaskListView.vue'), meta: { title: '任务' } },
  { path: '/tasks/:id(\\d+)', name: 'task-detail', component: () => import('@/views/TaskDetailView.vue'), meta: { title: '任务详情' } },
  // spec §04 §2：/plans /plans/:id 都需登录；:id 限定数字避免 catch-all 误吞。
  { path: '/plans', name: 'plans', component: () => import('@/views/PlanCalendarView.vue'), meta: { title: '日程' } },
  { path: '/plans/:id(\\d+)', name: 'plan-detail', component: () => import('@/views/PlanDetailView.vue'), meta: { title: '日程详情' } },
  // Phase 4 P4-5：/settings MVP1 空态，需登录；具体功能 v1.1 上线。
  { path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: '设置' } },
  // spec §06-expense：/expenses 与 /expenses/:id 都需登录；:id 限定数字避免 catch-all 误吞。
  { path: '/expenses', name: 'expense-list', component: () => import('@/views/ExpenseView.vue'), meta: { title: '消费' } },
  { path: '/expenses/:id(\\d+)', name: 'expense-detail', component: () => import('@/views/ExpenseDetailView.vue'), meta: { title: '消费详情' } },
  // spec §07-diet：/diets 与 /diets/:id 都需登录；:id 限定数字避免 catch-all 误吞。
  { path: '/diets', name: 'diet-list', component: () => import('@/views/DietView.vue'), meta: { title: '饮食' } },
  { path: '/diets/:id(\\d+)', name: 'diet-detail', component: () => import('@/views/DietDetailView.vue'), meta: { title: '饮食详情' } },
  // v2.1 PR3：独立 AI 分析页（spec §7.3 / CLAUDE.md §11.3）。
  // 与 Home 共享 6h Redis 缓存；需登录，由 beforeEach 统一拦截。
  { path: '/ai-analysis', name: 'ai-analysis', component: () => import('@/views/AiAnalysisView.vue'), meta: { title: 'AI 分析' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({
  history: createWebHistory(),
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

export default router;
