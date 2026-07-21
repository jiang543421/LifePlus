-- =============================================================
-- V5 — 日报模块复合索引
-- Spec: docs/specs/08-daily-report-design.md(落档中) / docs/prd/05-daily-report.md §3
-- 引擎:InnoDB;字符集:utf8mb4_0900_ai_ci
-- 目标查询:TaskMetricProvider.aggregateDaily(userId, date)
--   WHERE user_id = ? AND completed_at BETWEEN ? AND ? AND deleted = 0
--
-- 关键说明:
-- 1. 本迁移仅 ADD INDEX,不建任何业务表(与 PRD §3.1 M0-1 / §5.1 一致)
-- 2. t_task 原 V2 迁移仅有 idx_user_status_due / idx_user_plan,
--    无 (user_id, completed_at) 复合索引,日报完成数聚合会全表扫描
-- 3. t_plan V3 已有 idx_user_start (user_id, start_time) 可直接复用
--    (日报日程按 start_time 聚合) —— 本迁移不复建
-- 4. t_expense V4 已有 idx_user_occurred (user_id, occurred_at) 可直接复用
--    (日报消费按 occurred_at 聚合) —— 本迁移不复建
-- 5. 若生产环境 Flyway 历史已含 V5 (例如早期 diet 迁移曾用 V5 后改 V6),
--    部署时本迁移会冲突 —— 需先用 Flyway repair 或手动回滚历史记录
-- =============================================================

ALTER TABLE t_task
    ADD INDEX idx_user_completed_at (user_id, completed_at);
