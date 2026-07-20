-- =============================================================
-- V4 — 初始化 t_expense
-- Spec: docs/specs/06-expense-design.md §4
-- 引擎：InnoDB；字符集：utf8mb4_0900_ai_ci
-- 逻辑删除：deleted TINYINT NOT NULL DEFAULT 0
-- 时区：DATETIME 应用层 UTC 存储 / Asia/Shanghai 展示
-- 金额：DECIMAL(12,2)，CHECK 约束 amount > 0
-- 分类：CHECK 约束 category ∈ 5 字面值（与 ExpenseCategory 枚举一致）
-- =============================================================

CREATE TABLE t_expense (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    category    VARCHAR(16)   NOT NULL,
    note        VARCHAR(200)  NULL,
    occurred_at DATETIME(3)   NOT NULL,
    created_at  DATETIME(3)   NOT NULL,
    updated_at  DATETIME(3)   NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_occurred (user_id, occurred_at),
    KEY idx_user_category (user_id, category, occurred_at),
    CONSTRAINT chk_expense_amount_pos CHECK (amount > 0),
    CONSTRAINT chk_expense_category CHECK (category IN ('MEAL', 'SHOPPING', 'TRANSPORT', 'SUBSCRIPTION', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;