-- =============================================================
-- V5 — 初始化 t_diet
-- Spec: docs/specs/07-diet-design.md §4
-- 引擎：InnoDB；字符集：utf8mb4_0900_ai_ci
-- 逻辑删除：deleted TINYINT NOT NULL DEFAULT 0
-- 时区：DATETIME 应用层 UTC 存储 / Asia/Shanghai 展示
-- 营养字段：DECIMAL(7,2) kcal / DECIMAL(6,2) protein_g/carb_g/fat_g，CHECK 非负
-- 餐别：CHECK 约束 meal_type ∈ 4 字面值（与 MealType 枚举一致）
-- 索引：idx_user_occurred 用于列表按时间倒序 + 当日 summary 聚合
--       idx_user_meal 用于按餐别筛选 + frequent 聚合（user_id 优先于 name 聚合的过滤）
-- =============================================================

CREATE TABLE t_diet (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    meal_type   VARCHAR(16)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    kcal        DECIMAL(7,2) NOT NULL DEFAULT 0,
    protein_g   DECIMAL(6,2) NOT NULL DEFAULT 0,
    carb_g      DECIMAL(6,2) NOT NULL DEFAULT 0,
    fat_g       DECIMAL(6,2) NOT NULL DEFAULT 0,
    occurred_at DATETIME(3)  NOT NULL,
    note        VARCHAR(200) NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_occurred (user_id, occurred_at),
    KEY idx_user_meal (user_id, meal_type, occurred_at),
    CONSTRAINT chk_diet_meal         CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    CONSTRAINT chk_diet_kcal_nonneg  CHECK (kcal >= 0),
    CONSTRAINT chk_diet_protein_nonneg CHECK (protein_g >= 0),
    CONSTRAINT chk_diet_carb_nonneg    CHECK (carb_g >= 0),
    CONSTRAINT chk_diet_fat_nonneg     CHECK (fat_g >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;