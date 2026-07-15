-- =============================================================
-- V1 — 初始化 t_user 与 t_refresh_token
-- Spec: docs/specs/02-database.md §2.1, §2.4, §3
-- 引擎：InnoDB；字符集：utf8mb4_0900_ai_ci
-- 全部 deleted 列：TINYINT NOT NULL DEFAULT 0（逻辑删除，DB 层不强制）
-- 时区：DATETIME 不带时区，应用层统一 Asia/Shanghai
-- =============================================================

-- ---------- t_user ----------
CREATE TABLE t_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_email        (email),
    KEY        idx_user_deleted (deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------- t_refresh_token ----------
CREATE TABLE t_refresh_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  DATETIME     NOT NULL,
    revoked_at  DATETIME     NULL,
    created_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_token_hash (token_hash),
    KEY        idx_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
