# [MVP1 遗留] R-006 Flyway seed 账号脚本

**描述**：MVP1 缺失首登账号的 seed 脚本；本地起服务后必须走 `/auth/register` 注册首个用户才能用，对 CI 与 demo 体验不友好。

**Acceptance Criteria**：
- [ ] 新增 `src/main/resources/db/migration/V4__seed_demo_accounts.sql`，幂等（已存在则跳过），含 ≥ 2 个 demo 账号（`demo@lifepulse.test` / `alice@lifepulse.test`，密码 `Demo123!` 或 README 明确注明）
- [ ] 密码以 BCrypt（strength=10）哈希写入 `password_hash`，禁止明文
- [ ] README 「快速开始」段加一段：seed 已存在则登录；想换账号用 `DELETE FROM t_user WHERE email='demo@lifepulse.test'`
- [ ] CI（`mvn verify`）能跑过；seed 仅在 `application-dev.yml` 启用，prod 由 ops 自行注入
- [ ] 新增 `UserIT.seedAccounts_loginWithSeededEmail_returns200` 覆盖 happy path

**Refs**：RELEASES/v1.0.0-mvp.md §5.1 / #5 R-006
