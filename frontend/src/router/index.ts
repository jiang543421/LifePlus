import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

// Phase 1.4 只落地 /login /register /home 占位；任务/日程/设置视图留到 Phase 2/3。
const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
  { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { public: true, title: '注册' } },
  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { title: '数字生活' } },
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
