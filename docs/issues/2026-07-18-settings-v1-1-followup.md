# Settings v1.1 — 评审 follow-up backlog

> 来源：settings v1.1 PR（`feat/settings-v1-1`）code review 结论。
> 状态：**HIGH-2 / HIGH-3 已 CLOSED**（HIGH-3 feat/auth ee396ce；HIGH-2 feat/auth TTL 缩短见下）。
> 剩余：MEDIUM（1002 错误码，已决策不引入）/ INFO（entity mutation，非本 PR）。

---

## 0. 背景

settings v1.1（4 个 commit，5b88dd2 / e081557 / 44f092f / +本文件）实现：
- `PATCH  /users/me` — 改昵称（10/min/userId）
- `POST   /users/me/password` — 改密码 + 撤销 refresh（5/min/userId）
- `DELETE /users/me` — 软删账号 + 撤销 refresh（3/min/userId）
- 对应前端 SettingsView 三卡片 + E2E

本次 PR 评审后确认 **HIGH-1（settings 写端点限流）+ HIGH-4（settings E2E）** 已纳入；
**HIGH-2（refresh-token / 旧 access-token deny-list）+ HIGH-3（密码策略收紧）**
属于安全策略级改动，scope 超出 settings v1.1，列入 v1.2 backlog 收尾。

---

## 1. ~~HIGH-2：access token deny-list~~  → **CLOSED via TTL 缩短（2026-07-20）**

| 维度 | 内容 |
|---|---|
| 触发 review | review 阶段 @chief-of-staff 提出 |
| 原方案 | 改密 / 注销时把 access `jti` 加入 Redis deny-list（TTL = 剩余 access-ttl） |
| 最终方案 | **不引入 deny-list**；把 `lp.jwt.access-ttl` 从 `PT1H` 缩短到 `PT15M` |
| 决策理由 | 1) MVP 个人单用户场景，盗用 access 风险窗口 1h → 15min 风险下降 75%，已与 trade-off 平衡；2) deny-list 需要 Redis deny-list 缓存层 + JwtAuthFilter 中间件 + jti 解析，跨模块改动成本 M 级；3) refresh 旋转（7d）与前端改密后立即 `auth.clear()` 已覆盖常见攻击面；4) 用户已确认 2026-07-20 |
| 剩余风险 | 改密后 ≤15min 窗口内旧 access token 仍有效；前端改密流程已强制 clear()，正常用户无感 |
| 关联代码 | `application.yml:29` `lp.jwt.access-ttl: PT15M`；`AuthService.issueAndPersist` 同步从 `JwtProperties.getAccessTtl()` 取 `expiresIn`（避免返回的 expiresIn 与 token 真实寿命不一致） |
| 验收 | `mvn verify` 全绿；前端 E2E 改密流程不退化（前端 auth.clear() 后旧 token 即使未到 15min 也已无法使用） |

---

## 2. ~~HIGH-3：密码策略收紧~~ → **CLOSED（commit ee396ce）**

| 维度 | 内容 |
|---|---|
| 触发 review | review 阶段 security reviewer 提出 |
| 原方案 | 8 位 + 字母数字 + 弱密码字典 |
| 最终方案 | 上述全部纳入；新增 `PasswordPolicy` / `@StrongPassword` / `PasswordPolicyValidator`；前端 `PASSWORD_RULES` 同步 4 条规则 |
| 关联 PR | `feat(auth): tighten password policy with weak dictionary (HIGH-3)` — `ee396ce` |
| 剩余项 | 无 |

---

## 3. MEDIUM：POST /users/me/password 错误码（1002 vs 新增 1007）

| 维度 | 内容 |
|---|---|
| 触发 review | code review 内部讨论 |
| 当前行为 | 旧密码错 → `BusinessException(BAD_CREDENTIALS, 1002)`，复用登录错误码 |
| 决策 | **保持 1002**（避免账号枚举）；不再追踪 |
| 后续 | 如有产品诉求区分，再开 issue 单独评估 |

---

## 4. INFO：UserService 使用 `u.setXxx()` mutation

| 维度 | 内容 |
|---|---|
| 触发 review | self-review |
| 状态 | 沿用 MVP1 AuthService.register 既有模式；非本 PR 引入；不阻塞 |
| 后续 | 全局重构时统一评估 Entity immutability 模型（详见 `docs/issues/2026-07-18-entity-immutability.md`，若届时创建） |

---

## 5. 后续

HIGH-2 / HIGH-3 已关闭；MEDIUM / INFO 不阻塞。settings v1.1 backlog 全部消化。

---

**Owner**: lp
**Created**: 2026-07-18
**Last updated**: 2026-07-20（HIGH-2 CLOSED via TTL 缩短）
**关联 PR**: `feat/settings-v1-1` (commit 5b88dd2 / e081557 / 44f092f / 本文件)
**Refs**: 2026-07-18-settings-v1-1