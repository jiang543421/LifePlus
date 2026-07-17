-- =============================================================
-- V3 — 初始化 t_plan
-- Spec: docs/specs/02-database.md §2.3, §3
-- 引擎：InnoDB；字符集：utf8mb4_0900_ai_ci
-- 逻辑删除：deleted TINYINT NOT NULL DEFAULT 0
-- 时区：DATETIME 不带时区，应用层 UTC 存储 / Asia/Shanghai 展示
-- MVP1 字段占位：reminder_min 不实现推送逻辑，仅保留列与读写
-- =============================================================

CREATE TABLE t_plan (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    start_time   DATETIME     NOT NULL,
    end_time     DATETIME     NOT NULL,
    all_day      TINYINT      NOT NULL DEFAULT 0,
    location     VARCHAR(200) NULL,
    note         TEXT         NULL,
    reminder_min INT          NULL     DEFAULT 15,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_start (user_id, start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;