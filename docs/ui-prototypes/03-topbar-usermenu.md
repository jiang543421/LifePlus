# UI 原型 · 03 — TopBar + UserMenu

> 版本：v0.1 · 日期：2026-07-15 · 全局壳层组件（已登录态专用）
> 输入：[01-auth-prd.md §3 AUTH-3](../prd/01-auth-prd.md#3-用户故事) · [04-frontend.md §4 路由表](../specs/04-frontend.md#4-前端实现约束)

---

## 1. 故事 → 视图要素映射

| AC | UI 必须呈现 |
|---|---|
| AUTH-3 AC1：未登录访问受保护页跳登录 | 路由守卫（在 `router/index.ts` 实现，不画在本组件） |
| AUTH-3 AC2：退出登录入口清晰 | UserMenu 顶部显眼的「退出登录」项 |
| AUTH-3 AC3：退出后清除凭证 + 跳登录 | 调 `authStore.logout()` → 清 localStorage + 跳 `/login` |

### 1.1 与登录态的反向映射

| 路径 | 未登录 | 已登录 |
|---|---|---|
| `/login` `/register` | 正常显示 | 路由守卫直接跳 `/` |
| `/` `/tasks/*` `/plans/*` `/settings` | 跳 `/login?redirect=...` | TopBar + UserMenu + 主区域 |

---

## 2. TopBar 桌面 ASCII（≥1024px）

```
┌────────────────────────────────────────────────────────────────────┐
│  ┌────┐                                                            │
│  │ LP │  LifePulse                                                  │
│  └────┘                                                            │
│                       首页  待办  日程         ┌──────┐  ⚙ 设置      │
│                                                  │ 头像 │            │
│                                                  │  ▾   │            │
│                                                  └──────┘            │
└────────────────────────────────────────────────────────────────────┘
```

布局：
- 左：`LP` 缩写方块（占位 logo）+ `LifePulse` 标题
- 中：横向 nav（`/`、`/tasks`、`/plans`），当前路由高亮
- 右：头像 + UserMenu 触发器；齿轮 ⚙ 单独 router-link 到 `/settings`
- 全局搜索：MVP1 **不实现**（PRD Out of Scope），占位灰底

### 2.1 为什么 nav 只 3 项

首页 + 任务 + 日程。`/settings` 进 UserMenu/齿轮，避免 5 个横向入口在桌面挤。

---

## 3. TopBar 移动 ASCII（<768px）

```
┌────────────────────────┐
│  LP  LifePulse    ☰  │
└────────────────────────┘
```

布局：
- 左：`LP` + 标题
- 右：`☰` 汉堡按钮 → 展开 Drawer
- 抽屉内容（自上而下）：
  1. 头像 + 昵称 + email（掩码）
  2. ────
  3. 导航：首页 / 待办 / 日程
  4. ────
  5. 个人设置 / 修改昵称 / 修改密码
  6. 注销账号（灰禁用）
  7. ────
  8. 退出登录

---

## 4. UserMenu 桌面 ASCII（点击头像展开）

触发：右上角头像。下拉用 `el-dropdown`（trigger=click）。

```
┌────────────────────────────────┐
│  ┌────┐                        │
│  │头像│  张三                    │
│  └────┘  zs***@example.com      │
│                                │
│  ─────────────────────         │
│  👤  个人设置       →           │  → /settings
│  ✏   修改昵称       →           │  → /settings/profile
│  🔒 修改密码       →           │  → /settings/security
│  ⚠   注销账号   （灰禁用）      │  → MVP1 不做
│  ─────────────────────         │
│  🚪  退出登录                  │  → authStore.logout()
└────────────────────────────────┘
```

### 4.1 注销账号（灰禁用项）

- MVP1 不做账号注销（PRD §6 Out of Scope：单用户弱场景）
- **灰禁用而非隐藏**：让用户知道"该操作暂未开放"
- 1.4 落地：`el-dropdown-item disabled`

### 4.2 退出登录的视觉权重

- 与"账号信息区"之间有分割线（用户操作 ≠ 用户数据展示）
- 不放图标只用文字，避免误触
- 桌面：直接见；移动：抽屉最底

---

## 5. UserMenu 展开交互细节

| 交互 | 行为 |
|---|---|
| 点击头像 | 切换展开/收起 |
| 点击外部区域 | 自动收起 |
| ESC 键 | 收起 |
| 路由跳转（点任一条目） | 收起后再跳转 |
| 退出登录 | 立即执行 → 清 session → 跳 `/login` |

### 5.1 退出登录的确认

**MVP1 不画二次确认弹窗**（PRD 未提；退出是幂等低风险操作；用户误触可重新登录）。

---

## 6. 字段清单

TopBar/UserMenu 本身**不收集新字段**，仅展示用户态：

| 展示项 | 来源 | 备注 |
|---|---|---|
| nickname | `authStore.user.nickname` | 缺失则 fallback 到 email 前缀 |
| email | `authStore.user.email` | 前 2 位 + `***@example.com`（CLAUDE.md §7.3 不打完整 email） |
| 头像 | MVP1 用首字母占位 | 不实现上传头像（PRD Out of Scope） |

### 6.1 头像占位

- 默认展示 nickname 首字母（如 `张三` → `张`）
- 缺失 nickname 时落到 email 首字符（如 `zs@example.com` → `z`）
- 圆形 36×36，使用 `el-avatar`

---

## 7. 状态机（TopBar 视角）

```
        ┌──────────┐
   →    │ CHECKING │ ← 应用启动 / 路由跳转前
        └────┬─────┘
             │ authStore 是否有 accessToken
   ┌─────────┴──────────┐
   ▼                    ▼
┌──────────┐         ┌──────────┐
│ 未登录   │         │ 已登录   │
│ 隐藏     │         │ 显示     │
│ TopBar   │         │ TopBar   │
│ 仅看路由 │         │ + 内容区 │
└──────────┘         └──────────┘
```

退出登录：
```
   已登录 → 点击「退出登录」 → 调 POST /auth/logout
                                 ↓
                       清 localStorage + Pinia
                                 ↓
                          跳 /login
```

---

## 8. 与 1.4 落地的关联

- **TopBar**：`frontend/src/components/TopBar.vue`
- **UserMenu**：`frontend/src/components/UserMenu.vue`（包装 `el-dropdown`）
- **MobileNavDrawer**：`frontend/src/components/MobileNavDrawer.vue`（`el-drawer`）
- **Pinia store**：`authStore` 提供 `user / accessToken / refreshToken / logout()`
- **路由守卫**：`router/index.ts` 全局 `beforeEach`：未登录且目标 ≠ `/login`/`/register` → `next('/login?redirect=' + encodeURIComponent(to.fullPath))`

### 8.1 文件拆分 vs 单文件

| 方案 | 优劣 |
|---|---|
| A. TopBar + UserMenu 同文件 | 简单，但移动 Drawer 没法复用 |
| **B（采用）**. TopBar / UserMenu / MobileNavDrawer 三文件 | 各 30–80 行，便于 1.4 各自迭代 |

---

## 9. 不在本原型范围

- 全局搜索框（PRD Out of Scope）
- 头像上传（PRD Out of Scope）
- 账号注销流程（PRD Out of Scope）
- 通知 / 消息中心（PRD 未提）
- 深色模式切换（PRD 未提，Phase 4 评估）
- 多角色切换（单用户单角色，无此概念）