# 04 — Frontend（前端结构 + 路由 + 拦截器）

> 本文件为 LifePulse MVP1 设计规格的第 4 部分。
> 编码时单独加载本文，无需加载其他 4 个子文件。
>
> **索引**：[00-overview.md](./00-overview.md) · [01-architecture](./01-architecture.md) · [02-database](./02-database.md) · [03-api-auth](./03-api-auth.md) · [05-nfr-testing](./05-nfr-testing.md)

---

## 1. 目录结构

```
frontend/
├─ index.html
├─ vite.config.ts
├─ package.json
├─ tsconfig.json
└─ src/
   ├─ main.ts                # axios/router/pinia 装配
   ├─ App.vue
   ├─ router/
   │  └─ index.ts            # 路由 + 鉴权守卫
   ├─ stores/
   │  ├─ auth.ts             # token/user/refresh
   │  ├─ task.ts
   │  └─ plan.ts
   ├─ api/
   │  ├─ http.ts             # axios 实例 + 拦截器
   │  ├─ auth.ts
   │  ├─ task.ts
   │  └─ plan.ts
   ├─ views/
   │  ├─ LoginView.vue
   │  ├─ RegisterView.vue
   │  ├─ HomeView.vue        # 截图的「数字生活」首页
   │  ├─ TaskListView.vue
   │  ├─ TaskDetailView.vue
   │  ├─ PlanCalendarView.vue
   │  └─ PlanDetailView.vue
   ├─ components/
   │  ├─ ModuleCard.vue
   │  ├─ PlaceholderCard.vue
   │  ├─ TopBar.vue
   │  ├─ TaskItem.vue
   │  ├─ TaskFilters.vue
   │  ├─ CalendarMonth.vue
   │  └─ EventDialog.vue
   ├─ types/                 # 与后端 DTO 对齐
   ├─ utils/
   │  └─ time.ts             # formatInShanghai 等
   └─ assets/styles/         # 主题、卡片视觉
```

## 2. 路由 + 守卫

| 路径 | 视图 | 守卫 |
|---|---|---|
| `/login` | LoginView | 公开 |
| `/register` | RegisterView | 公开 |
| `/` | HomeView | 需登录 |
| `/tasks` | TaskListView | 需登录 |
| `/tasks/:id` | TaskDetailView | 需登录 |
| `/plans` | PlanCalendarView | 需登录 |
| `/plans/:id` | PlanDetailView | 需登录 |
| `/settings` | SettingsView | 需登录 |

守卫逻辑：

- 未登录访问受保护页 → 跳 `/login`，原路径存 query，回登后回跳
- 收到后端 `code=1002` → 拦截器尝试静默 refresh；失败统一清 store + 跳 `/login`

## 3. Axios 拦截器

**请求拦截**：

- 从 Pinia auth store 读 accessToken，挂 `Authorization: Bearer <token>`
- 注入 `traceId` 头（与服务端 MDC 对应）

**响应拦截**：

```
code === 0          → return response.data
code === 1002       → 触发静默 refresh（详见下）
其他 code           → errorHandler.handle(code, message) → ElMessage 提示
HTTP 401 / 网络错   → 同 1002 处理
```

**Refresh 队列机制**（避免并发刷新雪崩）：

```ts
let isRefreshing = false
const pendingQueue: Array<(t: string) => void> = []

async function handle1002() {
  if (isRefreshing) {
    return new Promise((resolve) => pendingQueue.push(resolve))
  }
  isRefreshing = true
  try {
    const newToken = await callRefresh()
    pendingQueue.forEach((cb) => cb(newToken))
    pendingQueue.length = 0
    return newToken
  } catch (e) {
    pendingQueue.length = 0
    authStore.clear()
    router.push('/login')
    throw e
  } finally {
    isRefreshing = false
  }
}
```

## 4. 首页（HomeView）映射

- `TopBar`：左侧头像下拉（账号 / 退出），中间「数字生活」，右侧设置图标
- 卡片网格（响应式断点）
  - 桌面 ≥1024px：3 列
  - 平板 768–1023px：2 列
  - 手机 <768px：1 列；TopBar 收起为汉堡按钮

| 卡片 | 行为 |
|---|---|
| 任务 | → `/tasks` |
| 计划 | → `/plans` |
| 日报 / 消费 / 饮食 / AI 分析 | → `PlaceholderCard`，点击 `ElMessage.warning('即将上线')` |

卡片视觉延续截图风格：白底、圆角 12px、淡蓝阴影、图标 + 中文标签。

## 5. Pinia 状态管理

**`stores/auth.ts`**：

```ts
state: {
  accessToken: string | null
  refreshToken: string | null
  user: { id, email, nickname } | null
}
getters: { isLoggedIn: (s) => !!s.accessToken && !!s.user }
actions: {
  setTokens(access, refresh)
  setUser(user)
  clear()
}
```

**持久化**：`accessToken / refreshToken / user` 在 `set/clear` 时同步写 `localStorage`（key 前缀 `lp_`），刷新保留；启动时 `main.ts` 读取并 hydrate store。

**任务/计划 store**：列表**不**做长缓存，每次进页面重新拉；保留 filter 状态。

## 6. 时区与时间

- 统一工具：`utils/time.ts → formatInShanghai(iso: string)`
- 后端 DTO 时间字段统一 ISO-8601 with offset（`+08:00`）
- store / view 一律走 `dayjs`（设 `dayjs.locale('zh-cn')`）

## 7. 前端技术栈

- Vue 3（Composition API + `<script setup>`）
- TypeScript（strict）
- Vite
- Pinia
- Vue Router 4
- Axios
- Element Plus（UI 组件）
- dayjs（时区/格式化）
- @vueuse/core
- Vitest + @vue/test-utils（测试）
- Playwright（E2E）

## 8. 代码风格（与项目 coding-style.md 对齐）

- Composition API + `<script setup>` 全程
- 禁止 mutation：用 `{ ...obj, field: value }` 或 store 的 action 返回新对象
- props / emits 用 TS 类型
- 组件 < 200 行，文件 < 400 行（拆分原则）
- 命名：组件 PascalCase，函数 camelCase，常量 UPPER_SNAKE_CASE
- 错误优先处理：try/catch 中捕获的 error 必须显式处理或抛出
