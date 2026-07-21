-- =============================================================
-- V5 — 种子 demo 账号（opt-in；v1.2.2 后默认不自动加载）
-- Issue: docs/issues/2026-07-18-r006-flyway-seed-account.md（R-006）
-- 幂等：WHERE NOT EXISTS — 重跑不冲突；email 已建唯一索引可兜底
-- 默认密码：Demo123!（BCrypt strength=10，与 AuthConstants.BCRYPT_STRENGTH 一致）
--
-- 加载机制（v1.2.2 重构后）：
--   - 默认 application.yml 仅 classpath:db/migration（V1-V6），prod 不会加载本文件
--   - application-dev.yml 为空文件，不再追加 db/seed 路径
--   - dev / test 集成测试 UserIT.@BeforeEach 走幂等 INSERT（WHERE NOT EXISTS，
--     BCrypt 重哈希），不依赖本迁移；与默认 profile 共享 Testcontainers MySQL
--     时不再触发 Flyway 跨 profile 校验失败
--   - 真正想跑本 V5：显式注入
--       LP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
--     （不推荐用于生产 demo 账号；生产 demo 应走 UserIT 同款 INSERT 脚本或手动）
-- =============================================================

INSERT INTO t_user (email, password_hash, nickname, created_at, updated_at, deleted)
SELECT 'demo@lifepulse.test',
       '$2a$10$ZsP/RRo1qcdcBLnInqhmHeh1wDYhRWa6wXzu.djwlCccY1u5KNDMi',
       'demo',
       NOW(3),
       NOW(3),
       0
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE email = 'demo@lifepulse.test');

INSERT INTO t_user (email, password_hash, nickname, created_at, updated_at, deleted)
SELECT 'alice@lifepulse.test',
       '$2a$10$8c4re7WtonPfmM34T7ic0OKlRc1yds4yjqtMDc2CqyAuw.eRmY7bK',
       'alice',
       NOW(3),
       NOW(3),
       0
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE email = 'alice@lifepulse.test');