-- =============================================================
-- V5 — 种子 demo 账号（dev/test 专用；prod 不自动加载）
-- Issue: docs/issues/2026-07-18-r006-flyway-seed-account.md（R-006）
-- 幂等：WHERE NOT EXISTS — 重跑不冲突；FK email 已建唯一索引可兜底
-- 默认密码：Demo123!（BCrypt strength=10，与 AuthConstants.BCRYPT_STRENGTH 一致）
-- 加载机制：
--   - classpath:db/seed 由 application-dev.yml 显式加入 spring.flyway.locations
--   - 默认 application.yml 仅 classpath:db/migration（V1-V4），prod 不会加载
--   - prod 想启用：注入 LP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
--   - 重新生成 hash：mvn -q test -Dtest=HashGen（src/test/.../HashGen.java）
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