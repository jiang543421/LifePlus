# LifePulse

> 个人多用户「数字生活」仪表盘 MVP1 — 邮箱+密码认证、任务 TODO、日历计划事件、首页 6 卡。

## 1. 快速上手

### 1.1 环境依赖

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | OpenJDK **17** | 后端编译运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+ | 前端构建 |
| pnpm | 9+ | 前端包管理（`npm i -g pnpm` 或 `corepack enable`） |
| Docker / docker compose | 24+ / v2 | 一键启动 MySQL + Redis + 全部服务 |

### 1.2 一键启动（推荐）

```bash
docker compose up -d --build     # 同时起 mysql + redis + backend + frontend
```

启动完成后：

- 前端 SPA：`http://localhost`
- 后端 API：`http://localhost:8080/api/v1`
- 健康检查：`http://localhost:8080/actuator/health`
- MySQL：`localhost:3306`（库 `lifepulse`，用户 `lp` / `lp_dev_only`）
- Redis：`localhost:6379`

### 1.3 拆分开发（前端/后端独立迭代）

```bash
# 终端 1：起 DB / Redis 容器
docker compose up -d db redis

# 终端 2：后端
cd backend
LP_DB_URL=jdbc:mysql://localhost:3306/lifepulse?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai \
LP_DB_USER=lp LP_DB_PASS=lp_dev_only \
LP_REDIS_URL=redis://localhost:6379 \
LP_JWT_SECRET=dev-only-secret-replace-me-32bytes-xxx \
mvn spring-boot:run

# 终端 3：前端
cd frontend
pnpm install
pnpm dev          # http://localhost:5173
```

> Frontend dev 代理 `/api` → `http://localhost:8080`，可省略手工配 base URL。

## 2. 项目结构

```
LifePulse/
├─ backend/                # Spring Boot 3 + Maven（单模块）
├─ frontend/               # Vue 3 + TS + Vite
├─ docs/specs/             # 设计规格（按章节拆分）
├─ docs/superpowers/       # 设计索引 + 实施计划
├─ docker-compose.yml      # 一键编排
├─ .env.example            # 环境变量样例（复制为 .env 使用）
├─ CLAUDE.md               # 项目研发规范（高优先级）
└─ CONTRIBUTING.md         # 提交与分支规范
```

## 3. 核心命令

### 3.1 后端

| 命令 | 作用 |
|---|---|
| `mvn -B -DskipTests package` | 编译打包（输出 `target/lifepulse-backend-*.jar`） |
| `mvn -B test` | 单元/切片测试 |
| `mvn -B verify` | 测试 + JaCoCo 覆盖率闸门（≥ 80%） |

> **集成测试（`*IT.java`）使用 Testcontainers**，需要能拉取 `mysql:8.0` / `redis:7` 镜像；CI/Docker 环境下会自动跑，本地若离线可加 `-DskipITs` 跳过。

### 3.2 前端

| 命令 | 作用 |
|---|---|
| `pnpm install` | 安装依赖 |
| `pnpm dev` | 启动 Vite 开发服务器（5173） |
| `pnpm test` | Vitest 单元/组件测试 |
| `pnpm lint` | ESLint + vue-tsc 类型检查 |
| `pnpm build` | 构建生产产物（输出 `dist/`） |
| `pnpm exec playwright test` | E2E（须 backend 已在 8080 监听） |

## 4. 种子账号

种子账号将在 Phase 5（R-006）随 Flyway V4 引入；目前请通过注册接口创建首个账号。

## 5. 安全提示

- `.env` / `.env.local` 已 `.gitignore` 排除；**严禁把 `.env` 加入版本控制**
- `LP_JWT_SECRET` 生产部署前必须替换为 ≥ 32 字节随机串；本地默认值仅供开发
- MySQL / Redis 容器内置的 `lp_dev_only` 密码**只用于本地**，生产必须独立配置

## 6. 文档导航

- 设计规格索引：`docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md`
- 实施计划：`docs/superpowers/plans/2026-07-15-lifepulse-mvp1.md`
- 项目规范：`CLAUDE.md`
- 提交规范：`CONTRIBUTING.md`

## 7. 路线图

- MVP1（当前）：邮箱+密码认证、任务、计划、首页 6 卡
- Phase 2：日报 / 消费 / 饮食 / AI 分析占位转为真实模块

---

Built with Spring Boot 3 · Vue 3 · MySQL 8 · Redis 7 · Docker Compose.
