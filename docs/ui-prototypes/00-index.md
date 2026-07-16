# LifePulse UI 原型（ASCII Wireframe · Phase 1.4 前置）

> 版本：v0.1 · 日期：2026-07-15 · 范围：Phase 1.4 涉及的 4 个核心 view
> 方案：**A → B**（本轮 ASCII 落 IA 与字段；Phase 1.4 把 ASCII 转成 Vue 3 + Element Plus 模板）

---

## 1. 输入与原则

### 1.1 设计输入优先级（PRD 优先）

| 优先级 | 输入 | 用途 |
|---|---|---|
| ① | `docs/prd/00-index.md` | 用户画像（IA、信息密度、友好度） |
| ① | `docs/prd/01-auth-prd.md` | 认证模块用户故事 + AC + 功能清单 → Login/Register/TopBar |
| ② | `docs/prd/02-task-prd.md` | 任务模块 AC（仅用于 HomeView 上「今日待办卡」字段） |
| ② | `docs/prd/03-plan-prd.md` | 日程模块 AC（仅用于 HomeView 上「今日事件卡」字段） |
| ③ | `docs/specs/04-frontend.md` | 仅作技术约束（路由表 / 拦截器 / 响应式断点 / Element Plus 选型） |

### 1.2 设计原则

- **PRD 用户故事 → 决定页面有哪些功能按钮/入口**（每按钮必须在某故事 AC 内有出处）
- **PRD 验收标准 → 决定页面字段、提示文案、操作流程**（AC 反向校验 UI 完整性）
- **PRD 功能清单 → 决定路由与导航结构**（不在 MVP1 范围的不画独立 view，挂占位卡）
- **用户画像 → 决定交互密度**：3 类画像均为「成年个人用户」，默认信息密度 = 中（关键信息首屏可见）
- **不做**：原型不含具体色值/字体/间距，留给 1.4 落地时与 Element Plus 主题对齐

### 1.3 范围声明

| In（本次原型） | Out（不在本次原型） |
|---|---|
| LoginView / RegisterView / HomeView / TopBar + UserMenu | TaskListView / TaskDetailView / PlanCalendarView / PlanDetailView / SettingsView |
| 鉴权 4 个交互闭环（注册→登录→续期→退出） | 任务/日程内部交互 |
| 首页 6 卡的占位 | 任务/日程真实数据展示 |

任务/日程 view 原型推迟到 Phase 2/3 启动前再做。

---

## 2. 原型清单

| 文件 | view | 关键 AC 反查来源 |
|---|---|---|
| [01-login.md](./01-login.md) | LoginView | AUTH-2 AC1-3 + §2.1 登录限流 |
| [02-register.md](./02-register.md) | RegisterView | AUTH-1 AC1-3 |
| [03-topbar-usermenu.md](./03-topbar-usermenu.md) | TopBar + UserMenu | AUTH-3 AC2-3 + §2.2 修改昵称/密码/注销入口 |
| [04-home.md](./04-home.md) | HomeView | §2.1 自动续期 + AUTH-3 AC1 + 任务/日程首页集成字段 |

---

## 3. 信息架构与路由

依据 PRD §2.1（In Scope）反查路由需求：

```
                    ┌──────────────────┐
                    │   未登录入口      │
                    │  /login · /register│
                    └────────┬─────────┘
                             │ 登录成功
                             ▼
   ┌──────────────────────────────────────────────┐
   │  已登录壳层：TopBar + UserMenu                │
   │  ┌─────┐  ┌──────────────┐  ┌──────────┐    │
   │  │头像 │  │ 数字生活       │  │ 设置 ⚙    │    │
   │  │▾   │  │              │  └──────────┘    │
   │  └─────┘  └──────────────┘                    │
   │  路由：/（首页） /tasks /plans /settings      │
   └──────────────────────────────────────────────┘
```

- 鉴权守卫：`/` `/tasks*` `/plans*` `/settings` → 未登录跳 `/login?redirect=<原路径>`（PRD AUTH-3 AC1 + spec §2 守卫）
- 退出登录：从 UserMenu 触发 → 跳 `/login`（PRD AUTH-3 AC2-3）

---

## 4. 通用交互细节（贯穿 4 个 view）

| 细节 | 规则 | PRD 出处 |
|---|---|---|
| 错误码文案 | 1002 = "邮箱或密码错误"（不区分账号枚举） | AUTH-2 AC3 |
| 1006 限流 | "请求过于频繁，请稍后再试" | AUTH-2 AC2 |
| 1401 凭证失效 | 拦截器静默 refresh；失败统一跳登录 | spec §3 拦截器 |
| 1005 邮箱已注册 | "该邮箱已注册"（不暴露是否可登录） | AUTH-1 AC2 |
| 1004 密码强度 | 列出未满足的规则（长度 / 必须含字母 / 必须含数字） | AUTH-1 AC3 |
| 按钮 loading | 提交期间禁用 + spinner；防止重复提交 | 通用 |
| 成功反馈 | 注册成功 → 自动登录 + 跳 `/`；登录成功 → 跳 redirect 或 `/` | AUTH-1 AC1 / AUTH-2 AC1 |

---

## 5. 响应式断点（从 spec §4 反推）

| 断点 | 行为 |
|---|---|
| ≥1024px | 首页 3 列卡片网格；TopBar 全展开 |
| 768–1023px | 首页 2 列卡片网格；TopBar 全展开 |
| <768px | 首页 1 列卡片网格；TopBar 头像与标题保留，设置入口移到 UserMenu 内 |

---

## 6. 落地到 1.4 的交接清单

Phase 1.4 启动时，从本目录 4 份 ASCII 转成 Vue 组件时需要：

- [ ] Element Plus 组件映射表（每 ASCII 区块 → 对应 `el-*` 组件）
- [ ] 字段 → 后端 DTO 反查（与 `backend/.../auth/dto/*` 字段名对齐）
- [ ] Pinia `authStore` 字段定义（accessToken / refreshToken / user）
- [ ] axios 拦截器伪代码（1002 静默 refresh 队列）
- [ ] 受保护路由守卫逻辑

---

*本目录是 UI 原型（A 阶段）。1.4 启动后会创建 `frontend/src/_prototypes/` 作为半成品代码（B 阶段），最终并入正式 view 文件。*