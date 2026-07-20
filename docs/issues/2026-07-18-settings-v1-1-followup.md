# Settings v1.1 — 评审 follow-up backlog

> 来源：settings v1.1 PR（`feat/settings-v1-1`）code review 结论。
> 状态：**OPEN** — 4 项 HIGH-2 / HIGH-3 暂不在本次 PR 处理，列入 v1.2 backlog。

---

## 0. 背景

settings v1.1（4 个 commit，5b88dd2 / e081557 / 44f092f / +本文件）实现：
- `PATCH  /users/me` — 改昵称（10/min/userId）
- `POST   /users/me/password` — 改密码 + 撤销 refresh（5/min/userId）
- `DELETE /users/me` — 软删账号 + 撤销 refresh（3/min/userId）
- 对应前端 SettingsView 三卡片 + E2E

本次 PR 评审后确认 **HIGH-1（settings 写端点限流）+ HIGH-4（settings E2E）** 已纳入；
**HIGH-2（refresh-token / 旧 access-token deny-list）+ HIGH-3（密码策略收紧）**
属于安全策略级改动，scope 超出 settings v1.1，暂不合并。

---

## 1. HIGH-2：access token deny-list（防止改密后旧 token 继续有效 ≤1h）

| 维度 | 内容 |
|---|---|
| 触发 review | review 阶段 @chief-of-staff 提出 |
| 当前行为 | access token 改密后仍可活至自然过期（≤1h，issue 决策记录于 UserService.changePassword JavaDoc） |
| 风险 | 改密后 1h 内，持有旧 access token 的攻击者仍可调 `/users/me` / `/tasks/*` / `/plans/*` |
| 范围 | 安全核心，跨模块改动（auth + security + 所有受保护资源） |
| 预估工作量 | M（3-5 天）：JWT 解析 + Redis SETNX jti + 中间件检查 + 单测 + IT + 文档 |
| 优先级 | **P1** — 安全闭环缺失项 |
| 关联 | plan §7.2（鉴权）、UserService.changePassword JavaDoc 已注明 TODO |

### 方案要点（待 v1.2 design review）

1. 改密 / 注销 / 主动 logout 时，把当前 access `jti` 加入 Redis deny-list
   （TTL = 剩余 access-ttl）。
2. `JwtAuthFilter` 在解析后查 deny-list，命中即 401。
3. `AuthService.logout` 同步撤销 refresh + 加 deny-list，刷新拿新 access
   的链路不受影响（仅旧 jti 进黑名单）。
4. 高频场景：用户多点登录 → 改密 → 只有持有旧 access token 的设备被踢；
   拿新 refresh token 换的 access 仍可用。

### 验收

- IT：改密后旧 access token 调 `/users/me` 401；新 token 调 200。
- 性能：deny-list 检查 P99 < 2ms（Redis pipeline）。
- 配置：`lp.jwt.denylist.enabled=false` 默认关，prod 必须开。

---

## 2. HIGH-3：密码策略收紧（8 位 + 字母数字混合 + 弱密码字典）

| 维度 | 内容 |
|---|---|
| 触发 review | review 阶段 security reviewer 提出 |
| 当前行为 | `auth/web/AuthController` 注册 / 改密 endpoint 仅 `@Size(min=8)`；无字符复杂度；无弱密码字典 |
| 风险 | `password123`、`11111111` 等 8 位全数字/纯字母通过；与同类应用最低安全基线不符 |
| 范围 | auth DTO 校验 + 共享规则常量 + 文档 |
| 预估工作量 | S（1-2 天）：共享规则 + 字典 + 单测 + E2E |
| 优先级 | **P2** — 安全基线收紧 |
| 关联 | PasswordRules.vue（前端 3 条规则已就位，后端需对齐） |

### 方案要点

1. 提取 `PasswordPolicy` 常量类：`MIN_LENGTH=8`、`REQUIRE_LETTERS=true`、`REQUIRE_DIGITS=true`、
   `WEAK_PASSWORDS`（top-100 字典 + 国内常用，如 `qwerty123`、`a12345678`）。
2. `RegisterRequest` / `ChangePasswordRequest` 加自定义 `@PasswordPolicy` 校验注解。
3. 错误码 1001（VALIDATION）保持不变，前端 `authErrorMessage(1001)` 落文案
   「密码不符合安全策略」。
4. 已注册用户不受影响；下次改密时强制新策略。

### 验收

- 单测：`PasswordPolicyValidatorTest` 至少 20 个边界用例。
- IT：注册 `12345678` 返回 400 / code=1001。
- 前端 PasswordRules.vue 与后端规则一一对应（不显示后端独有约束）。

---

## 3. MEDIUM：POST /users/me/password 错误码（1002 vs 新增 1007）

| 维度 | 内容 |
|---|---|
| 触发 review | code review 内部讨论 |
| 当前行为 | 旧密码错 → `BusinessException(BAD_CREDENTIALS, 1002)`，复用登录错误码 |
| 争议 | 用户视角：改密时「旧密码错」与登录时「密码错」语义不同；可考虑独立 1007 |
| 决策 | **保持 1002**：避免账号枚举（旧密码错 / 用户不存在应一致拒绝） |
| 后续 | 如有产品诉求区分，再开 issue 单独评估 |

---

## 4. INFO：UserService 使用 `u.setXxx()` mutation

| 维度 | 内容 |
|---|---|
| 触发 review | self-review |
| 状态 | 沿用 MVP1 AuthService.register 既有模式（`u.setEmail/setPasswordHash/setNickname`），非本 PR 引入；不阻塞 |
| 后续 | 全局重构时统一评估 Entity immutability 模型（详见 `docs/issues/2026-07-18-entity-immutability.md`，若届时创建） |

---

## 5. 测试与质量门槛（v1.2 落地时必须满足）

- 单测：UserService 现有 15 cases 全部保留 + 新增 deny-list / password-policy 覆盖 ≥ 10 cases
- IT：deny-list 跨端点生效；弱密码被拒
- E2E：settings.spec.ts 加 deny-list 验证（旧 token 调 /me 401）
- 覆盖率：后端 Service 行覆盖 ≥ 80%（CLAUDE.md §6.1）
- 文档：本文件更新 + README 安全策略段

---

## 6. 时间线建议

| Sprint | 项 | 备注 |
|---|---|---|
| v1.2 sprint 1 | HIGH-3（密码策略） | S 级，独立 PR，依赖低 |
| v1.2 sprint 2 | HIGH-2（deny-list） | M 级，跨模块，需 design review |
| v1.3+ | MEDIUM / INFO | 视情况 |

---

**Owner**: lp
**Created**: 2026-07-18
**关联 PR**: `feat/settings-v1-1` (commit 5b88dd2 / e081557 / 44f092f / 本文件)
**Refs**: 2026-07-18-settings-v1-1