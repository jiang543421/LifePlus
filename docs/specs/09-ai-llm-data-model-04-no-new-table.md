## 3. 无新表声明（hard rule）

### 3.1 现状（v2.0 基线）

| 表 | 模块 | 用途 | AI 模块用法 |
|---|---|---|---|
| `t_user` | auth | 用户基本信息 | 不读（鉴权由 JwtAuthFilter 处理）|
| `t_refresh_token` | auth | refresh token 哈希 | 不读 |
| `t_task` | task | 任务 | **只读**（`TaskAiProvider` 读当日完成率）|
| `t_plan` | plan | 日程 | **只读**（`PlanAiProvider` 读当日活动分钟）|
| `t_expense` | expense | 消费 | **只读**（`ExpenseAiProvider` 读今日/本周）|
| `t_diet` | diet | 饮食 | **只读**（`DietAiProvider` 读今日热量）|
| `t_daily_report` | daily | 日报聚合 | **可选只读**（`DailyAiProvider` 开关由 `lp.ai.daily-enabled` 控制）|

### 3.2 v2.1 改动（与 v2.0 完全相同）

| 项 | v2.0 | v2.1 |
|---|---|---|
| 新增表 | ❌ 无 | ❌ **无** |
| 新增列 | ❌ 无 | ❌ **无** |
| 新增索引 | ❌ 无 | ❌ **无** |
| Flyway 迁移 | ❌ 无 | ❌ **无** |
| 数据模型语义变更 | ❌ 无 | ❌ **无** |

**结论**：v2.1 **不修改任何数据库结构**。零 Flyway 迁移，零迁移脚本文件。

### 3.3 跨用户访问约束（沿用 CLAUDE.md §7.2）

| 关注点 | 规则 |
|---|---|
| Provider Mapper 调用 | 必传 `UserContext.current()` 取出的 `userId`，**禁止从请求参数取** |
| 缓存键 | 全部按 `<userId>` 隔离：`lp:ai:insight:<userId>` / `lp:ai:quota:<userId>:<yyyymmdd>` |
| 跨用户访问 | 不可能发生（端点不接受 userId 参数；JWT 解析即得） |
| 越权响应 | 1003 / 403（防御性兜底，本期不触发） |

### 3.4 "无新表"约束的边界

| 不破 | 破 |
|---|---|
| ❌ 新增 `t_ai_insight` 表存历史 | — |
| ❌ 新增 `t_user.ai_llm_enabled` 字段 | — |
| ❌ 新增 `t_ai_feedback` 表存赞同/不赞同 | — |
| ❌ 新增 `t_ai_prompt_version` 表存 prompt 版本 | — |
| ❌ 任何 Flyway V*.sql 文件 | — |

> 任何"应该存 DB 的"内容，本期通过 Redis 短期缓存 + 日志/metrics 兜底；如必须存，留 v2.2+ 评估。

---
