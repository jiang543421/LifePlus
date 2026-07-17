-- =============================================================
-- V2 — 初始化 t_task
-- Spec: docs/specs/02-database.md §2.2, §3
-- 引擎：InnoDB；字符集：utf8mb4_0900_ai_ci
-- 逻辑删除：deleted TINYINT NOT NULL DEFAULT 0
-- 时区：DATETIME 不带时区，应用层统一 Asia/Shanghai
-- plan_id 为可空语义性 FK（应用层校验所属 user），DB 层不强制
-- 状态/优先级枚举字面值：status 0=待办 1=已完成 2=已取消
--                        priority 0=无 1=低 2=中 3=高
-- =============================================================

CREATE TABLE t_task (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    plan_id    BIGINT       NULL,
    title      VARCHAR(200) NOT NULL,
    status     TINYINT      NOT NULL DEFAULT 0,
    priority   TINYINT      NOT NULL DEFAULT 0,
    due_date   DATE         NULL,
    tag        VARCHAR(64)  NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_status_due (user_id, status, due_date),
    KEY idx_user_plan       (user_id, plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;