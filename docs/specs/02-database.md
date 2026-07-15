# 02 — Database（数据模型 + DDL + 索引）

> 本文件为 LifePulse MVP1 设计规格的第 2 部分。
> 编码时单独加载本文，无需加载其他 4 个子文件。
>
> **索引**：[00-overview.md](./00-overview.md) · [01-architecture](./01-architecture.md) · [03-api-auth](./03-api-auth.md) · [04-frontend](./04-frontend.md) · [05-nfr-testing](./05-nfr-testing.md)

---

## 1. 设计原则

- **全表逻辑删除**：`deleted TINYINT(0/1)`，MyBatis-Plus `@TableLogic` 自动加 `WHERE deleted=0`
- **时区**：DB 全部 `DATETIME`（不带时区），应用层 UTC 存储 / `Asia/Shanghai` 展示
- **外键**：DB 层不强制（运维与性能考虑），应用层按语义性 FK 关联查询
- **迁移工具**：Flyway（`backend/src/main/resources/db/migration/V*.sql`）
- **字符集**：`utf8mb4` / `utf8mb4_0900_ai_ci`

## 2. 表结构

### 2.1 `t_user`

| 字段 | 类型 | 约束 | 索引 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| email | VARCHAR(120) | NOT NULL, UNIQUE | UNIQUE |
| password_hash | VARCHAR(255) | NOT NULL | |
| nickname | VARCHAR(64) | | |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| deleted | TINYINT | NOT NULL DEFAULT 0 | 普通索引 |

### 2.2 `t_task`

| 字段 | 类型 | 约束 | 索引 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL | 联合索引 (user_id, status, due_date) |
| plan_id | BIGINT | NULL, FK 语义 → t_plan.id | 联合索引 (user_id, plan_id) |
| title | VARCHAR(200) | NOT NULL | |
| status | TINYINT | NOT NULL DEFAULT 0 | 0=待办，1=已完成，2=已取消 |
| priority | TINYINT | NOT NULL DEFAULT 0 | 0=无，1=低，2=中，3=高 |
| due_date | DATE | NULL | |
| tag | VARCHAR(64) | NULL | **MVP1 字符串字段**，Phase 2 再升级字典表 |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| deleted | TINYINT | NOT NULL DEFAULT 0 | |

> **设计决策**：`plan_id` 为可空语义性外键（DB 层不强制）。一条任务可挂到某日程下；MVP1 不做反向多对多。

枚举字面值（应用层 Java `enum` + TS `enum` 双侧对齐）：

```java
enum TaskStatus { TODO=0, DONE=1, CANCELLED=2 }
enum TaskPriority { NONE=0, LOW=1, MEDIUM=2, HIGH=3 }
```

### 2.3 `t_plan`

| 字段 | 类型 | 约束 | 索引 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL | 联合索引 (user_id, start_time) |
| title | VARCHAR(200) | NOT NULL | |
| start_time | DATETIME | NOT NULL | |
| end_time | DATETIME | NOT NULL | |
| all_day | TINYINT | NOT NULL DEFAULT 0 | |
| location | VARCHAR(200) | NULL | |
| note | TEXT | NULL | |
| reminder_min | INT | NULL DEFAULT 15 | **MVP1 字段占位，不实现推送逻辑** |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |
| deleted | TINYINT | NOT NULL DEFAULT 0 | |

> `end_time > start_time` 在应用层校验。

### 2.4 `t_refresh_token`

| 字段 | 类型 | 约束 | 索引 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL | |
| token_hash | VARCHAR(255) | NOT NULL | **UNIQUE** |
| expires_at | DATETIME | NOT NULL | **普通索引（用于定时清理）** |
| revoked_at | DATETIME | NULL | |
| created_at | DATETIME | NOT NULL | |

## 3. 索引清单（穷举）

| 索引 | 表 | 列 | 类型 | 用途 |
|---|---|---|---|---|
| `uq_email` | t_user | (email) | UNIQUE | 登录查重 |
| `idx_user_status_due` | t_task | (user_id, status, due_date) | 普通 | "今日任务" / 列表 |
| `idx_user_plan` | t_task | (user_id, plan_id) | 普通 | 列某计划下任务 |
| `idx_user_start` | t_plan | (user_id, start_time) | 普通 | 日历按月查 |
| `uq_token_hash` | t_refresh_token | (token_hash) | UNIQUE | 快速查重与撤销校验 |
| `idx_expires_at` | t_refresh_token | (expires_at) | 普通 | 定时清理过期 token |

## 4. Flyway 迁移顺序

```
V1__init_user_and_refresh_token.sql
V2__init_task.sql
V3__init_plan.sql
```

每个 V* 必须含 `CREATE TABLE` + 对应索引 + 默认 `deleted=0`。

## 5. 跨字段应用层约束（DB 层不强制）

- `t_user.password_hash` 永远 BCrypt，password 字段不进 DB
- `t_plan.start_time < end_time` 在 Service 层校验
- `t_task.plan_id` 若非 NULL，必须存在且属于当前 user
