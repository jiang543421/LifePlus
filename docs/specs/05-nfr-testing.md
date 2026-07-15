# 05 — NFR & Testing（非功能 + 测试 + 任务清单）

> 本文件为 LifePulse MVP1 设计规格的第 5 部分。
> 编码时单独加载本文，无需加载其他 4 个子文件。
>
> **索引**：[00-overview.md](./00-overview.md) · [01-architecture](./01-architecture.md) · [02-database](./02-database.md) · [03-api-auth](./03-api-auth.md) · [04-frontend](./04-frontend.md)

---

## 1. 性能

| 指标 | 目标 |
|---|---|
| 首页加载 P95 | ≤ 2.0s（4G） |
| 列表接口 P95 | ≤ 300ms（本地 docker compose） |
| 日历按月查 P95 | ≤ 100ms（命中 `t_plan(user_id, start_time)`） |

## 2. 安全 Checklist

- [x] 密码 BCrypt 存储，绝不明文
- [x] JWT HS256 + 强密钥（≥32 字节，`application.yml` 注入，禁止 commit）
- [x] refresh token 哈希落库 + 旋转（每次 refresh 撤销旧 token）
- [x] 登录限流（同 IP/邮箱 5 次/分钟，Redis 计数）
- [x] CORS 白名单（开发 `http://localhost:5173`，生产具体域名）
- [x] DTO `@Valid` 校验
- [x] 越权检查：所有 `{id}` 端点强制 `user_id` 过滤
- [x] MyBatis-Plus 参数绑定（禁字符串拼接）
- [x] Vue 模板默认转义防 XSS
- [x] 错误日志不泄露密码/token/邮箱
- [x] `.env` / `application-prod.yml`，仓库只留 `.example`

## 3. 可观测

- 日志：`logback-spring.xml`，JSON 行格式
- 全链路 `traceId`：MDC 贯穿 + 响应信封 + 前端请求头
- `GET /actuator/health`（K8s / Docker 探活）
- 启动 banner 含 commit-hash、构建时间

## 4. 错误处理

- 后端：`@RestControllerAdvice` 捕获 `BusinessException(code, msg)` → 转信封
- 前端：`errorHandler.handle(code, message)` → `ElMessage` 提示 + 必要跳转

## 5. 测试策略

### 5.1 覆盖目标

| 层 | 工具 | 覆盖 |
|---|---|---|
| 后端单元 | JUnit 5 + Mockito | Service ≥ 80% 行 |
| 后端切片 | `@WebMvcTest` + MockMvc | 鉴权 / 校验 / 越权 路径 100% |
| 后端集成 | `@SpringBootTest` + Testcontainers (MySQL/Redis) | 端到端 |
| 前端单元 | Vitest + @vue/test-utils | stores / utils ≥ 70% |
| 前端组件 | Vitest | 关键组件 100% |
| 前端 E2E | Playwright | 关键流 |

### 5.2 必备测试用例清单

- **单元**
  - 登录密码校验（错误密码、强度不足、BCrypt round-trip）
  - JWT 生成与解析（类型、过期、密钥错误）
  - refresh 撤销（重放拒绝）
  - 跨用户越权拒绝（A 用户 token 访问 B 用户资源 → 1003）
  - 过滤器 SQL 包含 `deleted=0`
  - 时区格式化（UTC ↔ Asia/Shanghai）
- **切片**
  - 每个 Controller ≥ 1 个鉴权 + 1 个参数校验 case
- **集成**
  - register → login → create-task → list → refresh → logout 闭环
  - create-plan → 关联 task via plan_id → 列某计划下任务
- **E2E**
  - Playwright：新用户完整流程（注册 → 建任务 → 建计划 → 登出）1
  - Playwright：日历查看流程 1

### 5.3 流水线闸门

- 后端：`mvn verify` 必须通过；JaCoCo 行覆盖 < 80% 失败
- 前端：`pnpm run lint && pnpm run test` 必过
- 同一 PR 两个仓都绿才可合并

## 6. 任务清单（可追踪）

> writing-plans skill 会基于此生成逐步任务分解。

### Phase 0：基础设施

- [ ] T-001 初始化 git 仓库 + Monorepo 目录骨架
- [ ] T-002 写 `docker-compose.yml`（MySQL 8 + Redis + backend + nginx）
- [ ] T-003 写 `.gitignore`、`.env.example`、`README.md`、贡献规范
- [ ] T-004 后端 Spring Boot 初始化（`backend/pom.xml`、启动类、`application.yml`）
- [ ] T-005 前端 Vue 3 + Vite + TS 初始化（`frontend/package.json`、路由、Pinia）

### Phase 1：认证

- [ ] A-001 设计并迁移 `t_user` / `t_refresh_token` 表（Flyway V1）
- [ ] A-002 `User`、`RefreshToken` 实体 + Mapper
- [ ] A-003 BCrypt 密码服务 + 参数校验 DTO
- [ ] A-004 JWT 签发/解析服务（含 `typ=access|refresh`）
- [ ] A-005 `AuthService`：register/login/refresh/logout
- [ ] A-006 `AuthController` 4 个端点
- [ ] A-007 Spring Security 配置（无状态 + JWT 过滤器）
- [ ] A-008 全局异常 + 统一返回信封（`@RestControllerAdvice`）
- [ ] A-009 登录限流（Redis 5 次/分）
- [ ] A-010 鉴权切片测试
- [ ] A-011 端到端集成测试（register→login→refresh→logout）
- [ ] F-A01 前端 `stores/auth.ts`
- [ ] F-A02 前端 `api/http.ts` 拦截器（含 1002 → refresh 队列）
- [ ] F-A03 前端 `LoginView` + `RegisterView`
- [ ] F-A04 前端 `users/me` 接通，登录回跳
- [ ] F-A05 前端 Playwright 登录流程

### Phase 2：任务模块

- [ ] T-T01 设计并迁移 `t_task` 表（Flyway V2）
- [ ] T-T02 `Task` 实体 + Mapper + 逻辑删除
- [ ] T-T03 `TaskService`：CRUD + 状态切换 + 跨用户越权检查
- [ ] T-T04 `TaskController` 7 个端点（含 `/by-plan/{planId}`）
- [ ] T-T05 任务切片/集成测试
- [ ] F-T01 前端 `stores/task.ts`
- [ ] F-T02 前端 `api/task.ts`
- [ ] F-T03 前端 `TaskListView`（列表 + 过滤 + 分页）
- [ ] F-T04 前端 `TaskDetailView`（查看 / 编辑 / 完成 / 删除）
- [ ] F-T05 任务 E2E

### Phase 3：计划模块

- [ ] T-P01 设计并迁移 `t_plan` 表（Flyway V3）
- [ ] T-P02 `Plan` 实体 + Mapper + 逻辑删除
- [ ] T-P03 `PlanService` + 越权检查
- [ ] T-P04 `PlanController` 5 个端点（含日历范围查）
- [ ] T-P05 计划切片/集成测试
- [ ] F-P01 前端 `stores/plan.ts`
- [ ] F-P02 前端 `api/plan.ts`
- [ ] F-P03 前端 `PlanCalendarView`（月视图 + 事件标记）
- [ ] F-P04 前端 `PlanDetailView` + `EventDialog`
- [ ] F-P05 日历 E2E

### Phase 4：首页与跨模块

- [ ] F-H01 前端 `HomeView`（截图卡片网格 + 占位卡）
- [ ] F-H02 `TopBar`（头像/菜单/设置）
- [ ] F-H03 任务/计划关联打通（"列出该计划下的任务"联动）
- [ ] F-H04 响应式断点（3/2/1 列）

### Phase 5：打磨与发布

- [ ] R-001 性能调优（首页加载、列表 P95、日历查询）
- [ ] R-002 安全 checklist 复测
- [ ] R-003 日志与 traceId 全链路
- [ ] R-004 `actuator/health` + 探活
- [ ] R-005 Docker Compose 一键启停验证（空环境一次拉起）
- [ ] R-006 README.md 部署指引 + 默认账号 + 烟雾测试脚本
