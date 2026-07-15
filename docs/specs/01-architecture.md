# 01 — Architecture（架构 + 模块边界 + 范围）

> 本文件为 LifePulse MVP1 设计规格的第 1 部分。
> 编码时单独加载本文，无需加载其他 4 个子文件。
>
> **索引**：[00-overview.md](./00-overview.md) · [02-database](./02-database.md) · [03-api-auth](./03-api-auth.md) · [04-frontend](./04-frontend.md) · [05-nfr-testing](./05-nfr-testing.md)

---

## 0. 产品定位与边界

### 0.1 一句话定位

个人多用户「数字生活」仪表盘：把分散的日常管理（任务、日程、未来扩展：日报/消费/饮食/AI 分析）集中到一个轻量 Web 应用内。

### 0.2 MVP1 范围（只承诺这些）

| 模块 | 状态 | 备注 |
|---|---|---|
| 邮箱/密码认证 | ✅ 必做 | 注册、登录、refresh、登出、当前用户 |
| 任务 | ✅ 必做 | TODO 列表（标题、状态、截止日期、优先级、标签、可关联到日程） |
| 计划 | ✅ 必做 | 日历日程事件（标题、起止时间、地点、备注、提醒字段占位） |
| 日报 / 消费 / 饮食 / AI 分析 | ⏸ Phase 2 | 首页保留占位卡，点击弹出 toast「即将上线」 |

### 0.3 显式不做（避免越界）

- 不做团队/共享/权限分级
- 不做图片上传、视觉识别
- 不做离线缓存
- 不做支付/订阅
- 不做多语言（仅中文）

---

## 1. 架构总览

### 1.1 部署形态

单仓 Monorepo，`docker compose up` 一键起：

```
LifePulse/
├─ backend/           # Spring Boot 3 + Maven
├─ frontend/          # Vue 3 + TS + Vite
├─ docker-compose.yml # MySQL 8 + Redis + backend + nginx(前端)
└─ README.md
```

### 1.2 运行时架构

```
┌─────────────────────────────────────┐
│  Frontend (Vue 3 SPA, Nginx 静态托管) │
│  Pinia + Axios + Vue Router         │
└──────────────┬──────────────────────┘
               │  /api/* (JWT in Authorization header)
┌──────────────▼──────────────────────┐
│  Backend (Spring Boot 3, JAR)       │
│  Controllers → Services → Mappers   │
│  Spring Security + JWT + BCrypt     │
│  MyBatis-Plus → MySQL 8.0           │
│  Spring Data Redis → Redis          │
└─────────────────────────────────────┘
```

### 1.3 模块边界

- 多用户之间数据严格隔离（所有查询按 `user_id` 过滤）
- 首页 6 张卡片：任务、计划可点；日报、消费、饮食、AI 分析为占位
- 截图中的视觉样式（白底圆角、淡蓝阴影、3×2 网格）作为首页 UI 基线

### 1.4 一键启动

```bash
docker compose up -d     # 起 MySQL + Redis + backend + nginx
# 前端单独开发：
cd frontend && pnpm dev  # http://localhost:5173
# 后端单独开发：
cd backend  && ./mvnw spring-boot:run   # http://localhost:8080
```

部署容器清单：
- `mysql:8.0`
- `redis:7-alpine`
- 自构 `backend` 镜像（`Dockerfile` 多阶段：`build` → `eclipse-temurin:21-jre` + fat jar）
- 自构 `frontend` 镜像（`nginx:alpine` + `pnpm build` 静态产物）
