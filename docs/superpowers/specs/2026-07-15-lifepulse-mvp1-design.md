# LifePulse MVP1 Design Spec — 汇总索引

- **Status:** Draft（待用户审阅）
- **Date:** 2026-07-15
- **Author:** Senior PM 视角
- **Scope:** 仅 MVP1
- **Source of truth:** 本索引 + `docs/specs/01..05-*.md`

---

## 1. 本文件性质

本文件是 **MVP1 设计的汇总索引**，并不直接包含设计细节。设计细节已按章节拆分成 5 个独立子文件存放到 [`docs/specs/`](../../specs/) 目录下，每个子文件在编码时独立加载。

## 2. 子文件导航（编码时按需加载）

| # | 文件 | 章节覆盖 | 何时加载 |
|---|---|---|---|
| 01 | [docs/specs/01-architecture.md](../../specs/01-architecture.md) | §0 产品定位与边界、§1 架构总览、§1.4 一键启动 | 任何时刻需要了解项目边界、目录、运行时架构、容器清单时 |
| 02 | [docs/specs/02-database.md](../../specs/02-database.md) | §3 数据模型 + DDL + 索引清单 | 写迁移 / 实体 / Mapper 时 |
| 03 | [docs/specs/03-api-auth.md](../../specs/03-api-auth.md) | §4 鉴权 + API 全量端点 + 安全细节 | 写 Controller / Service / 鉴权 / DTO 时 |
| 04 | [docs/specs/04-frontend.md](../../specs/04-frontend.md) | §5 前端目录 + 路由 + Axios 拦截器 + Pinia | 写 Vue 视图 / store / api 客户端时 |
| 05 | [docs/specs/05-nfr-testing.md](../../specs/05-nfr-testing.md) | §6 非功能 + §7 测试 + §8 任务清单 | 写测试 / 调优 / 发布 / 跟踪任务进度时 |

> **编码约定**：每次只 `Read` 上表中的 1 个子文件，避免上下文被全文淹没。

## 3. MVP1 一句话定位

个人多用户「数字生活」仪表盘 Web 应用：认证（邮箱+密码）+ 任务（TODO 列表）+ 计划（日历事件），多用户数据严格隔离。其余 4 模块（日报/消费/饮食/AI 分析）首页占位，Phase 2。

## 4. 模块边界一览

**必做**：邮箱+密码认证、任务（关联日程可选）、计划（日历事件 + 提醒字段占位）

**占位不实现**：日报、消费、饮食、AI 分析

**显式不做**：团队/共享、图片识别、离线缓存、支付订阅、多语言

## 5. 技术栈快照

- 后端：Spring Boot 3.x + Maven + Spring Security + JJWT + BCrypt + MyBatis-Plus + MySQL 8 + Redis + Flyway + Lombok
- 前端：Vue 3 + TS + Vite + Pinia + Vue Router 4 + Axios + Element Plus + dayjs
- 部署：Docker Compose（MySQL 8 + Redis + backend + nginx）
- 详情：每个子文件内嵌详细版本与配置

## 6. 端点总览（MVP1 共 16 个）

- Auth: 4 个（register / login / refresh / logout）
- User: 1 个（me）
- Task: 7 个（list / get / create / update / patch-status / delete / by-plan）
- Plan: 5 个（list / get / create / update / delete）

详细载荷与字段：[03-api-auth](../../specs/03-api-auth.md)

## 7. 数据表总览

4 张表：`t_user` / `t_task` / `t_plan` / `t_refresh_token`

关键索引：`uq_email`、`idx_user_status_due`、`idx_user_plan`、`idx_user_start`、`uq_token_hash`、`idx_expires_at`

详细 DDL：[02-database](../../specs/02-database.md)

## 8. 任务清单（42 条，分 6 个 Phase）

见 [05-nfr-testing §6](../../specs/05-nfr-testing.md)

## 9. 开放问题 / 后续

| # | 问题 | 何时决定 |
|---|---|---|
| Q1 | 是否升级 `task ↔ plan` 为多对多 | Phase 2 |
| Q2 | `reminder_min` 何时真正接入推送 | Phase 2 |
| Q3 | `tag` 何时升级为字典表 | Phase 2 |
| Q4 | 是否增加"今日任务"独立接口 | 待统计 |
| Q5 | Phase 2 引入消费/饮食模块优先级 | Phase 2 启动时 |

## 10. 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| JWT 密钥泄露 | 高 | 强密钥 + 注入而非硬编码 + 定期轮换 |
| MyBatis-Plus 关联查询 N+1 | 中 | 列表查询强制 `Page<T>` 加 fetch join 或二次 IN 查询 |
| 时间字段不一致（UTC vs CST） | 中 | 应用层强制统一，DTO 序列化用 ISO-8601 携带时区 |
| 跨用户越权 | 高 | 所有 `{id}` 端点统一 `UserContext` 拦截 |
| Docker Compose 启动顺序 | 中 | `depends_on` + 健康检查 |
| 截图视觉与组件库默认风格不一致 | 低 | TopBar / Card 自研样式覆盖 Element Plus 默认 |

---

## 11. 写本索引的策略

- 本索引是设计快照，目标是**让读者一眼看见全貌**并按需进入子文件
- 任何具体实现细节**不在本索引复制**，全部下沉到子文件
- 当子文件内容更新到一定规模时，本索引的对应章节保持简短即可

---

## 12. 后续步骤

1. 用户审阅本索引 + 5 个子文件
2. 用户认可后调用 `superpowers:writing-plans` 拆分实施任务
3. 不直接进入实现；先有 plan，再按 TDD 落地
