# LifePulse

> 个人多用户「数字生活」仪表盘 MVP1 — 邮箱+密码认证、任务 TODO、日历计划事件、首页 6 卡、**AI 分析 v2.1 LLM 增强**（tag `v2.1.0-ai` 发布中；前序 `v2.0.0-ai` 为 v2.0 智能卡 + 抽屉底座）。

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
LP_JWT_SECRET=local-dev-secret-please-change-in-production-12345 \
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

## 4. 种子账号（R-006 v1.2.2 重构）

dev / test 环境 **不通过 Flyway 自动注入**，由集成测试 `UserIT.@BeforeEach` 幂等 INSERT（BCrypt strength=10 与 AuthConstants 对齐）：

| email | nickname | 密码 |
|---|---|---|
| `demo@lifepulse.test` | `demo` | `Demo123!` |
| `alice@lifepulse.test` | `alice` | `Demo123!` |

**当前机制（v1.2.2 起）**：

- dev / test：种子账号不落数据库，只在跑 `UserIT` 集成测试时由 `@BeforeEach` 注入；本地手动 `mvn spring-boot:run` 不会创建 demo 账号
- 默认 `application.yml` 仅 `classpath:db/migration`（V1-V6），prod 不会加载 `db/seed`
- `application-dev.yml` 为空文件（仅保留作为 dev profile 标记），不再追加 `db/seed` Flyway 路径
- **prod opt-in**（仅当你想让生产 DB 自动注入 demo 账号时才需要；正常 prod 不推荐）：
  ```bash
  LP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
  ```

**幂等性**：`db/seed/V5__seed_demo_accounts.sql` 走 `INSERT ... WHERE NOT EXISTS`，多次启动不重复。

详见 `docs/issues/2026-07-18-r006-flyway-seed-account.md` 与
[`backend/src/test/java/com/lifepulse/auth/web/UserIT.java`](backend/src/test/java/com/lifepulse/auth/web/UserIT.java)（v1.2.2 重构后唯一启用入口）。

## 5. 安全提示

- `.env` / `.env.local` 已 `.gitignore` 排除；**严禁把 `.env` 加入版本控制**
- `LP_JWT_SECRET` 启动校验：≥ 32 字节，且不得包含 `replace-me` 子串
  （占位符串被 fail-fast 拒绝，防止误部署 — CLAUDE.md §7.1）。
  本地 dev 示例值见 §1.3。
- MySQL / Redis 容器内置的 `lp_dev_only` 密码**只用于本地**，生产必须独立配置

## 6. 文档导航

- 设计规格索引：`docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md`
- 实施计划：`docs/superpowers/plans/2026-07-15-lifepulse-mvp1.md`
- 项目规范：`CLAUDE.md`
- 提交规范：`CONTRIBUTING.md`

## 7. Release Notes

每个已发布 tag 对应的 release notes，按时间倒序：

| Tag | Date | 模块 / 摘要 |
|-----|------|------------|
| [v1.2.6](RELEASES/v1.2.6.md) | 2026-07-23 | UX 体验优化（三态模式扩散）：3 处 loading skeleton（TaskListView / PlanCalendarView / ExpenseView）+ TriStateEmpty + TriStateError 共享组件 + 7 个 view 空态 / 错误态迁移 + 2 枚三态设计 token（无依赖变更 / 无 schema 变更） |
| [v1.2.5](RELEASES/v1.2.5.md) | 2026-07-23 | UX 体验优化：AiDrawer loading skeleton + AI 角标 hover tooltip + DailyView 错误态 + 重试按钮（无依赖变更 / 无 schema 变更） |
| [v2.0.0-ai](RELEASES/v2.0.0-ai.md) | 2026-07-22 | MVP2 第四阶段：AI 分析 v2.0（智能卡 + 详情抽屉；5 Provider 聚合） |
| [v1.2.3](RELEASES/v1.2.3.md) | 2026-07-21 | MVP2 第三阶段：日报（daily）后端聚合端点 + 周报对照（PR #15） |
| [v1.2.2](RELEASES/v1.2.2.md) | 2026-07-21 | MVP2 第二阶段：饮食（diet）模块 + R-006 / Bug #1 收口 |
| [v1.2.1](RELEASES/v1.2.1.md) | 2026-07-20 | MVP2 第一阶段：消费（expense）模块 |
| [v1.0.0-mvp](RELEASES/v1.0.0-mvp.md) | 2026-07-15 | MVP1 首个 release：邮箱+密码认证、任务、计划、首页 6 卡 |

> 最新发布：`v1.2.6`（annotated tag @ `main` `4d3d65c` + release notes commit `pending`）— UX 优化 11 处（3 skeleton + 4 错误态 + 2 共享组件 + 2 token），详见 [RELEASES/v1.2.6.md](RELEASES/v1.2.6.md)。
>
> 上一稳定版：`v1.2.5`（annotated tag @ `main` `08be097` + release notes commit `5f0f0ae`）— UX 优化 3 处（AiDrawer skeleton + AI tooltip + DailyView 错误态），详见 [RELEASES/v1.2.5.md](RELEASES/v1.2.5.md)。

## 8. 路线图

- MVP1（✅）：邮箱+密码认证、任务、计划、首页 6 卡
- MVP2 第一阶段 v1.2.1（✅）：消费 expense
- MVP2 第二阶段 v1.2.2（✅）：饮食 diet
- MVP2 第三阶段 v1.2.3（✅ 部分）：日报 daily 后端能力（前端接入留待 v1.2.4）
- **AI 分析 v2.0 v2.0.0-ai（✅ 已发布）**：5 个 Provider（Task/Plan/Expense/Diet/Daily stub）+ Redis 缓存 30min + 模板引擎 + 降级 1501 + 限流（60/min GET、6/min POST） + 前端首页 AI 卡激活（抽屉 + Toast）。详见 [RELEASES/v2.0.0-ai.md](RELEASES/v2.0.0-ai.md) + [2026-07-21-ai-v2-design.md §18](docs/superpowers/specs/2026-07-21-ai-v2-design.md)。
- **UX 体验优化 v1.2.5（✅ 已发布）**：AiDrawer 首次加载 skeleton + AI 角标 hover tooltip（LLM 智能生成 vs 模板降级语义）+ DailyView 错误态友好提示（图标 + 文案 + 重试按钮）。详见 [RELEASES/v1.2.5.md](RELEASES/v1.2.5.md)。
- **UX 体验优化 v1.2.6（✅ 已发布）**：把 v1.2.5 验证过的「loading skeleton + 空态 + 错误态可恢复」三态模式从 DailyView / AiDrawer 扩散到全站高频视图 — TaskListView / PlanCalendarView / ExpenseView 新增 loading skeleton；TaskListView / PlanCalendarView / ExpenseView / DietView 新增「首次加载失败 + list===null → 重试」错误态；抽取 `TriStateEmpty` + `TriStateError` 两个共享组件；新增 `--tri-state-loading-bg` / `--tri-state-loading-radius` 两枚设计 token。详见 [RELEASES/v1.2.6.md](RELEASES/v1.2.6.md)。
- 后续：日报前端 v1.2.4、AI v2.1（独立分析页 + 趋势图）、设置页 actions v1.1

---

Built with Spring Boot 3 · Vue 3 · MySQL 8 · Redis 7 · Docker Compose.
