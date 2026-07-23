# LifePulse 项目研发规范（CLAUDE.md）

> 本文件是 **LifePulse** 项目的项目级 CLAUDE.md。
> 优先级：本文档 > `~/.claude/rules/ecc/common/*` > `~/.claude/CLAUDE.md`。
> 一切以本文与落档 spec 为准；如冲突，以本文为准。

---

## 1. 项目概述

| 项 | 内容 |
|---|---|
| 项目名称 | LifePulse |
| 定位 | 个人多用户「数字生活」仪表盘 Web 应用 |
| 一句话 | 把分散的日常管理（任务、日程；未来扩展：日报 / 消费 / 饮食 / AI 分析）集中到一个轻量 Web 应用内 |
| 团队 | 当前 1 人（开发者本人） |
| 项目背景 | 从个人生活管理需求出发；以 MVP1 验证"单仓 Monorepo + Spring Boot 3 / Vue 3"的端到端工程链路，再分 Phase 增量扩展 |
| MVP1 范围 | 邮箱+密码认证、任务 TODO、日历计划事件、首页 6 卡占位 |
| 显式不做 | 团队/共享/权限分级、图片识别、离线缓存、支付订阅、多语言 |
| 设计规格 | `docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md`（索引）+ `docs/specs/01..05-*.md`（按章节拆分的子文件） |
| 实施计划 | `docs/superpowers/plans/2026-07-15-lifepulse-mvp1.md` |

---

## 2. 技术栈

### 2.1 后端

| 项 | 版本/范围 |
|---|---|
| JDK | OpenJDK 17（升级至 21 须开 issue + 写迁移说明） |
| 构建 | Maven 3.9+ |
| 框架 | Spring Boot 3.3.5（含 `web` / `validation` / `security` / `data-redis` / `actuator`） |
| ORM | MyBatis-Plus 3.5.6（含 `@TableLogic` 逻辑删除） |
| 迁移 | Flyway 10（`db/migration/V*.sql`） |
| DB | MySQL 8.0+（utf8mb4_0900_ai_ci） |
| 缓存 / 限流 | Spring Data Redis（Redis 7-alpine） |
| 安全 | Spring Security 6 + JJWT 0.12.6（HS256）+ BCrypt（strength=10） |
| 工具库 | Lombok、Jakarta Validation |
| 测试 | JUnit 5 + Mockito + `@WebMvcTest` + `@SpringBootTest` + Testcontainers（`mysql:8` / `redis:7`） |

### 2.2 前端

| 项 | 版本/范围 |
|---|---|
| 框架 | Vue 3.4+（Composition API + `<script setup>`） |
| 语言 | TypeScript 5+（strict） |
| 构建 | Vite 5+ |
| 状态 | Pinia 2+ + Vue Router 4 |
| HTTP | Axios 1.7+ |
| UI | Element Plus 2.8+ |
| 时间 | dayjs 1.11+（TZ `Asia/Shanghai`） |
| 工具 | `@vueuse/core` 11+ |
| 测试 | Vitest 2.0+ + `@vue/test-utils` 2.4+ |
| E2E | Playwright |

### 2.3 部署

- 后端：可执行 fat jar → `eclipse-temurin:21-jre`
- 前端：`pnpm build` 产物 → `nginx:alpine`，前端路由 fallthrough 到 `index.html`
- 编排：Docker Compose v2（`mysql:8.0` + `redis:7-alpine` + 自构 `backend` + 自构 `frontend`）
- 启动：`docker compose up -d --build`

> **升级主版本前必须开 issue、写迁移说明；不允许在未通知的情况下随便 bump。**

---

## 3. 目录结构规范

```
LifePulse/
├─ backend/
│  ├─ src/main/java/com/lifepulse/
│  │  ├─ LifePulseApplication.java
│  │  ├─ common/           # 跨模块：web 包装(MyResponse/Env)/ MyBatis / TraceId / 通用工具
│  │  ├─ security/         # SecurityConfig / JwtAuthFilter / UserContext / RateLimiter
│  │  ├─ auth/             # entity/ repository/ service/ web/ dto/ security(JwtService,PasswordEncoder)/ exception
│  │  ├─ task/             # entity/ repository/ service/ web/ dto
│  │  └─ plan/             # entity/ repository/ service/ web/ dto
│  ├─ src/main/resources/
│  │  ├─ application.yml
│  │  ├─ logback-spring.xml
│  │  └─ db/migration/     # V1__init_user_and_refresh_token.sql / V2__init_task.sql / V3__init_plan.sql
│  └─ src/test/java/com/lifepulse/
│     ├─ it/                # AbstractIntegrationTest + 各模块 *IT.java（Testcontainers）
│     └─ <module>/          # 单元/切片测试
├─ frontend/
│  └─ src/
│     ├─ main.ts / App.vue
│     ├─ api/              # http.ts（拦截器）+ auth.ts / user.ts / task.ts / plan.ts
│     ├─ router/           # 路由表 + 鉴权守卫
│     ├─ stores/           # Pinia: auth / task / plan
│     ├─ components/       # ModuleCard / PlaceholderCard / TopBar / UserMenu / TaskItem / TaskFilters / CalendarMonth / EventDialog
│     ├─ views/            # HomeView / LoginView / RegisterView / TaskListView / TaskDetailView / PlanCalendarView / PlanDetailView / SettingsView
│     ├─ types/            # 与后端 DTO 对齐
│     ├─ utils/            # time.ts / calendar.ts
│     ├─ assets/styles/    # 主题/响应式
│     ├─ __tests__/        # Vitest
│     └─ e2e/              # Playwright
├─ docs/
│  ├─ superpowers/specs/   # 索引 + 设计快照
│  ├─ specs/               # 拆分子文件
│  └─ superpowers/plans/   # 实施计划
├─ docker-compose.yml
├─ .env.example
├─ README.md
├─ CONTRIBUTING.md
└─ CLAUDE.md               # 本文件
```

**命名规则**

| 维度 | 规则 | 示例 |
|---|---|---|
| 仓库根目录 | 小写、可短横线 | `backend/`、`frontend/` |
| Java 包 | 全小写、点分 | `com.lifepulse.auth.entity` |
| Java 类 | PascalCase，文件名与类名一致 | `UserMapper.java` |
| 方法 / 变量 | camelCase | `findByEmail()`、`userId` |
| 常量 | UPPER_SNAKE_CASE | `MAX_LOGIN_ATTEMPTS` |
| 文件（前端非组件） | kebab-case | `time.ts` |
| Vue 组件（文件与类名） | PascalCase（多词） | `TaskListView.vue` |
| 路由名 | kebab-case | `task-list` |
| SQL 表 | t\_ 前缀 + 名词复数 | `t_user`、`t_task`、`t_plan`、`t_refresh_token` |
| SQL 列 | snake_case | `password_hash` |
| SQL 索引 | `idx_*` 普通 / `uq_*` UNIQUE | `idx_user_status_due`、`uq_email` |
| Vue 路由 path | kebab-case | `/tasks/:id` |

---

## 4. 代码风格规范

> 全局规则见 `~/.claude/rules/ecc/common/coding-style.md`，本节为项目级补充与硬性约束。

### 4.1 不变性（hard rule）

- **禁止 mutation**。所有修改返回新对象：`record`、`@Data` + builder、`{ ...obj, field: value }`、`Object.freeze`、Lombok `@Builder`。
- **集合返回不可变视图**：`List.copyOf(...)` / `Collections.unmodifiableList(...)`。

### 4.2 格式化

| 项 | 规则 |
|---|---|
| 缩进（Java / SQL / YAML） | 4 空格 |
| 缩进（TS / Vue / CSS / JSON） | 2 空格 |
| 引号（Java / TS 字符串） | 一律双引号 `"`；SQL 用单引号 |
| 分号（Java / TS） | 一律保留 |
| 行尾 | LF（仓库 `.gitattributes` 锁定） |
| 文件尾 | 必须以单个换行结束 |
| 函数最大行数 | 50 |
| 文件最大行数 | 800（超出立即拆分） |
| 最大嵌套深度 | 4（优先早返） |
| 魔法数字 | 必须抽出命名常量（如 `MAX_LOGIN_ATTEMPTS = 5`） |

### 4.3 命名

- 布尔：`is*` / `has*` / `should*` / `can*` 前缀
- 接口（TS）：无 `I` 前缀；类型用 `T...` 仅在泛型位置
- 枚举（TS）：成员 PascalCase；键名 `UPPER_SNAKE` 用于序列化/DB 对齐

### 4.4 注释规则

- **注释解释 why，不解释 what**。自描述代码不写注释。
- 必须写注释的情形：
  - 跨用户越权点（`// 必须按 user_id 过滤`）
  - 数据迁移的不可逆操作
  - 优化 hot-path 的非显然选择
  - TODO / FIXME 必须带 `owner=...` 与 issue 编号
- 禁写废话注释（`// loop over items` 这种解释代码本身的）
- 公共 API（controller / 函数 export）必须有简短 JavaDoc / JSDoc 说明参数与返回

### 4.5 错误处理

- **必须显式处理**：try / catch 不允许吞错
- 业务异常统一抛 `BusinessException(code, msg)`，由 `@RestControllerAdvice` 转统一信封
- 跨用户访问 → `BusinessException(1003)`，禁止用 `Optional.empty()` 隐式掩盖
- 错误日志禁止打印密码 / token / 邮箱完整内容（允许打 `userId` 与 `email` 前缀 2 位）

### 4.6 输入验证

- 所有 DTO（request）必须 `@Valid`
- `@NotBlank` / `@Email` / `@Size` / `@Pattern` 直接打在 record 字段上
- 控制器方法形参：`@Valid @RequestBody` 缺一不可

---

## 5. Git 工作流规范

> 全局规则见 `~/.claude/rules/ecc/common/git-workflow.md` + `~/.claude/CLAUDE.md` 红线。
> 本节为本项目具体约束。

### 5.1 分支命名

| 类型 | 命名格式 | 示例 |
|---|---|---|
| 主分支 | `main` | `main`（production-ready） |
| 功能 | `feat/<scope>-<short>` | `feat/auth-refresh-rotate` |
| 修复 | `fix/<scope>-<short>` | `fix/task-cross-user` |
| 重构 | `refactor/<scope>-<short>` | `refactor/plan-event-dialog` |
| 文档 | `docs/<scope>-<short>` | `docs/readme-seed-account` |
| 测试 | `test/<scope>-<short>` | `test/auth-rate-limit` |
| 杂项 | `chore/<scope>-<short>` | `chore/ci-gate` |
| 性能 | `perf/<scope>-<short>` | `perf/calendar-index` |

`<scope>` 取模块名（`auth` / `task` / `plan` / `home` / `infra` / ...）；`<short>` 简短动作名，全英文短横线分隔。

### 5.2 提交消息

格式：`<type>(<scope>): <subject>`（与分支一致），**`subject` 用英文、首字母不大写、不超过 72 字符、不加句号**。

允许的 `<type>`：`feat` / `fix` / `refactor` / `docs` / `test` / `chore` / `perf` / `ci`。

示例：

```
feat(auth): rotate refresh token on every refresh
fix(task): enforce user_id filter on get-by-id
test(plan): assert cross-user reject returns 1003
chore(infra): add docker compose healthchecks
docs(readme): document seed account and env vars
```

正文可选，但遇以下情况必填：
- **BREAKING CHANGE**（写在一行 `BREAKING CHANGE:` 引导段）
- 需要解释"为什么"的非平凡修改
- 关联 issue：`Refs: #123` / `Closes: #456`

### 5.3 PR 与合并流程

1. **创建 PR**：标题与 commit message 同格式；正文含：
   - 改了什么（≤ 5 bullet）
   - 为什么
   - 测试覆盖
   - 影响面（哪些模块、是否有 migration）
   - 是否 break change
2. **自审通过 + CI 绿**才允许 review
3. **合并方式**：默认 **squash merge**；标题即 commit 主题
4. **不允许**：
   - 强制推送（`git push --force`）
   - `git rebase -i` 重写已 push 分支
   - `git reset --hard` 在共享分支
   - `--no-verify` 跳钩
5. 合并后**删除远端分支**

---

## 6. 测试规范

> 全局规则见 `~/.claude/rules/ecc/common/testing.md`，本节是项目强制阈值。

### 6.1 覆盖率硬指标

| 层 | 工具 | 阈值 | 闸门 |
|---|---|---|---|
| 后端 Service | JUnit 5 + Mockito | **行覆盖 ≥ 80%** | `mvn verify` 中 JaCoCo 强制，不达标则构建失败 |
| 后端 Controller | `@WebMvcTest` | 鉴权 / 校验 / 越权路径 100% 覆盖 | 同上 |
| 后端集成 | `@SpringBootTest` + Testcontainers | 关键流程 register→login→refresh→logout 与 task/plan 闭环 | 同上 |
| 前端 stores / utils | Vitest | **行覆盖 ≥ 70%**；store 关键 getters 100% | `pnpm test` |
| 前端 components | Vitest | 关键组件（ModuleCard / TaskItem / EventDialog）100% | `pnpm test` |
| 前端 E2E | Playwright | 登录流、任务流、日历流、跨用户越权 1003、refresh 重放 1401、登录限流 1006 必须存在 | `pnpm exec playwright test` |

### 6.2 TDD（强制）

每个功能改动必须按 **Red → Green → Refactor**：

1. 先写测试，看着它失败
2. 用最小代码让它通过
3. 重构（保持测试绿）
4. commit

### 6.3 命名规则

- **Java 测试**：方法名 `methodName_stateUnderTest_expectedBehavior`，例：
  ```java
  @Test void login_wrongPassword_throws1002() {...}
  @Test void register_existingEmail_throws1005() {...}
  ```
- **TS 测试**：`describe('Component') + it('behavior')`，例：
  ```ts
  describe('TaskItem', () => {
    it('renders title and status badge', () => { ... })
  })
  ```
- **E2E**：文件名 `<feature>.spec.ts`，例：`login.spec.ts` / `task-flow.spec.ts` / `security.spec.ts`。

### 6.4 测试隔离

- 集成测试使用 **Testcontainers** MySQL + Redis，禁止依赖外部 DB
- 前端用 `axios-mock-adapter` / `vi.mock()` 隔离网络
- 不允许 `Thread.sleep(...)`，允许 `Awaitility.await().untilAsserted(...)` 或前端的 `waitFor`

### 6.5 禁做

- 禁止删除测试让红变绿（fail-to-pass 必须经 issue 评审）
- 禁止 `assertThat(true).isTrue()` 这种空断言
- 禁止 `@Disabled` / `xit()` 跳过失败的测试而不留跟踪 issue

---

## 7. 安全规范

> 全局规则见 `~/.claude/rules/ecc/common/security.md`，本节为本项目强制项。

### 7.1 禁做（hard block，即使 auto-accept）

- **禁止硬编码任何密钥**：JWT secret、DB 密码、Redis URL、SMTP 等都必须从 `application.yml` 经 `${LP_*}` 占位符或 secret manager 注入
- 仓库**只能**有 `.env.example`，真实 `.env` 必须 `.gitignore` 排除
- 任何 PR 中如出现新的 hex/base64/密码串嫌疑 → CRITICAL，立即拒绝
- 不允许 commit `lp.jwt.secret` 的真实值；本地开发用 placeholder 字符串 + 注释标明占位

### 7.2 鉴权与越权

- JWT 载荷：`sub=userId, iat, exp, typ ∈ {access, refresh}`
- HS256 密钥 ≥ 32 字节，应用启动校验
- **Refresh 旋转**：每次 refresh 颁发新 token，旧 token `revoked_at = NOW()`，DB 仅存 `SHA-256(token)` 哈希
- **跨用户越权必拦截**：所有 `{id}` 端点先按 `UserContext.current()` 过滤，越权一律 `BusinessException(1003)`
- **登录限流**：5 次/分钟（`lp:rl:login:<ip>:<email>` Redis 计数），第 6 次返回 `code=1006`

### 7.3 输入与输出

- 输入：所有 DTO `@Valid`；`@Email` / `@NotBlank` / `@Size` / `@Pattern`
- SQL：MyBatis-Plus 参数绑定，禁字符串拼接
- 输出：跨字段不做拼接（含用户输入的字段直接 HTML 渲染）→ 默认 Vue 转义，无 `v-html` 滥用
- 错误日志禁打 password / token / 完整 email（仅打 `userId` 与 `email` 前缀 2 位）

### 7.4 速率与防滥用

- 注册端点同样限流（≥ 3 次/分钟/IP）
- `users/me` 仍需 access token 校验，不豁免

### 7.5 CSP 与 CORS

- 前端 Nginx 配置 `Content-Security-Policy` 默认自洽（Phase 5 R-004 落地）
- CORS：dev 仅允许 `http://localhost:5173`，prod 收紧到具体域名白名单

### 7.6 审计与可观测

- `traceId` 贯穿 MDC + 响应信封 + 前端请求头
- 关键操作（登录 / 越权拒绝 / refresh 重放）打 WARN 日志
- 启动 banner 含 git commit-hash 与构建时间

---

## 8. Spec / Plan 引用与读取约定

| 阶段 | 必读的子文件 | 禁读 |
|---|---|---|
| 任意时刻背景 | `docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md` 索引 | — |
| Phase 0 / 5 | `docs/specs/01-architecture.md` + `05-nfr-testing.md` | 02 / 03 / 04 |
| Phase 1（Auth） | `docs/specs/02-database.md §2.1 §2.4` + `03-api-auth.md` | 04 |
| Phase 2（Task） | `docs/specs/02-database.md §2.2` + `03-api-auth.md §5.3` + `04-frontend.md`（Task 视图段） | — |
| Phase 3（Plan） | `docs/specs/02-database.md §2.3` + `03-api-auth.md §5.4` + `04-frontend.md`（Plan 视图段） | — |
| Phase 4（Home） | `docs/specs/04-frontend.md §4` | 02 / 03 |

> 编码执行阶段**严格遵循"每次只读 1 个 sub-spec"**——任何任务都不得擅自跨多 spec 一并加载。
> 这是用户硬约束，违反视同未遵守本规范。

---

## 9. 红线操作（即使 auto-accept 模式也禁止）

- 删除文件、目录、git 历史
- 修改 `.env`、密钥、token、证书、CI/CD 配置
- `git push` / `git rebase` / `git reset --hard` / 强制推送
- 公开 release（`npm publish`）或生产部署

> 与 `~/.claude/CLAUDE.md` 红线清单**强绑定**，任何一方更新都需要同步更新本文件。

---

## 10. 文档与本次会话的产出

| 文件 | 用途 |
|---|---|
| `CLAUDE.md`（本文件） | 项目级规范 |
| `README.md` | 项目介绍 + quickstart + seed 账号 |
| `CONTRIBUTING.md` | 分支命名 / commit message / TDD 节奏 |
| `docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md` | MVP1 设计索引 |
| `docs/superpowers/specs/2026-07-21-ai-v2-design.md` | AI v2.0 设计索引（已发布，tag `v2.0.0-ai`）|
| `docs/superpowers/specs/2026-07-22-ai-v2-1-llm-design.md` | AI v2.1 LLM 增强设计（已发布于 main via PR #18 squash merge `2aa2f53`，tag `v2.1.0-ai`）|
| `docs/specs/01-architecture..05-nfr-testing.md` | MVP1 设计拆分（编码按需读取）|
| `docs/specs/06-expense-design.md` ~ `08-daily-report-design.md` | MVP2 / v1.2 模块设计 |
| `docs/specs/09-ai-llm-data-model.md` | AI v2.1 数据模型（Java 类 + Redis 键）|
| `docs/ui-prototypes/00-index.md` + `01..08-*.md` | UI 原型（桌面 + 移动端 ASCII）|
| `docs/prd/00-index.md` + `01..07-*.md` | 产品 PRD（KANO / RICE / 用户故事）|
| `docs/superpowers/plans/2026-07-15-lifepulse-mvp1.md` | 实施计划 |
| `RELEASES/v1.2.5.md` | 纯前端 UX 体验优化 release（AiDrawer skeleton + AI 角标 tooltip + DailyView 错误态；tag `v1.2.5` @ `08be097`，2026-07-23 发布）|

---

## 11. AI 模块规范（v2.1+ LLM 增强）

> 本节为 v2.1+ AI 模块（引入 LLM）的项目级硬约束。
> v2.0（智能卡 + 抽屉 + 模板引擎，5 Provider 聚合）已发布于 tag `v2.0.0-ai`；v2.1 在其上叠加 LLM 与独立分析页。
> 详细设计：`docs/superpowers/specs/2026-07-22-ai-v2-1-llm-design.md` · 数据模型：`docs/specs/09-ai-llm-data-model.md` · UI 原型：`docs/ui-prototypes/08-ai-llm.md`

### 11.1 硬约束（不可破）

| # | 约束 | 含义 | 违反后果 |
|---|---|---|---|
| 1 | **无新表** | v2.1 不新增任何数据库表；零 Flyway 迁移 | CRITICAL |
| 2 | **不修改既有字段** | 5 张业务表（`t_task` / `t_plan` / `t_expense` / `t_diet` / `t_daily_report`）字段语义不变 | CRITICAL |
| 3 | **不引入新第三方依赖** | LLM 客户端用手写 Spring `RestClient` 调 OpenAI 兼容协议，不引入 Spring AI 库 | HIGH |
| 4 | **跨用户隔离** | 缓存键 / 配额键 / 熔断键全部按 `userId` 隔离；端点不接受 userId 参数 | CRITICAL |
| 5 | **依赖方向单向** | `Web → Service → LlmInsightGenerator → LlmClient`；反向禁止 | HIGH |
| 6 | **端点不接受 userId** | 所有 `{id}` 由 JWT 解析得；越权一律 `BusinessException(1003)`（CLAUDE.md §7.2）| CRITICAL |

### 11.2 密钥管理（`LP_LLM_API_KEY`）

`LP_LLM_API_KEY` 同样适用 §7.1 禁硬编码规则，本节仅列 LLM 特有项：

| 项 | 规则 |
|---|---|
| 配置注入 | `lp.ai.llm.api-key: ${LP_LLM_API_KEY:}` 占位符 |
| 必填场景 | `provider=deepseek` 时必填；`provider=ollama` 可空 |
| **启动期 fail fast** | 缺失 / 占位符（`sk-replace-` 前缀）/ 长度 < 20 → `IllegalStateException` → Spring 启动失败 |
| **Ollama 模式特殊** | `circuit-breaker.enabled=false` 自动禁用（本地进程死掉与远程故障语义不同）|
| **轮换流程** | DeepSeek 控制台撤销旧 key → 创建新 key → 改 `.env` → 重启（5 分钟闭环）|
| **提交期拦截** | `.githooks/pre-commit` + `.gitleaks.toml`（v2.1 PR4 引入）：`gitleaks protect --staged` 扫 staged diff，命中 `sk-` / `LP_JWT_SECRET` / 高熵 base64 模式即拒合；开发者克隆后跑 `./scripts/install-hooks.sh` 设置 `core.hooksPath`，CI 端单独配置或显式调 gitleaks |

### 11.3 3 层降级链路（hard rule）

```
L1 LLM 成功  → source="llm"，渲染完整 payload
L1 LLM 失败  → catch Llm*Exception → L2 模板 → source="template"
L2 模板失败  → throw BusinessException(1501) → HTTP 503
```

- **L1 失败不重试**（5s 超时已包含网络抖动；429 重试只会更糟）
- **L2 失败不返回部分数据**（1501 语义是完全不可用，部分数据会误导前端）
- **用户感知最小化**：source 标签 + 缺少 advice/highlight 段，**无错误弹窗**
- **两类触发场景**：
  - A 类（LLM 失败降级）：5xx / 429 / 4xx / 超时 / 解析失败 / 敏感词
  - B 类（绕开 L1）：4 chip 全 `none()` / `enabled=false` / 配额超限 / 熔断中

### 11.4 Redis 命名空间（沿用 `lp:*` 前缀）

| 键 | 类型 | TTL | 用途 | 来源 |
|---|---|---|---|---|
| `lp:ai:insight:<userId>` | String (JSON) | **6h**（v2.0 是 30min）| 洞察缓存（含 v2.1 新字段）| v2.0 沿用 + 字段扩展 |
| `lp:rl:ai:insight:<userId>` | String | 60s | GET 限流 30/min/user | v2.0 沿用 |
| `lp:rl:ai:refresh:<userId>` | String | 60s | POST refresh 限流 **3/min/user**（v2.0 是 6/min）| v2.0 沿用 + 阈值调整 |
| `lp:ai:quota:<userId>:<yyyymmdd>` | String | **25h**（首次 INCR 时 EXPIRE）| 每日 LLM 调用配额 | **v2.1 新增** |
| `lp:ai:circuit:state` + `:openedAt` | String | 永久 | 熔断状态 | **v2.1 新增** |
| `lp:ai:circuit:failures` | Sorted Set | 5min 滑动 | 失败时间戳窗口 | **v2.1 新增** |

> **注意**：缓存键与限流键命名以实际代码为准（v2.0 tech-arch 修正源 vs spec §18.2 不一致）；限流命名空间是 `lp:rl:*`（**不是** `lp:ai:rl:*`）。

### 11.5 配额与熔断（v2.1 默认值）

| 项 | 默认 | 范围 | 配置 |
|---|---|---|---|
| LLM 每日配额 | 50/用户/天 | 1-1000 | `lp.ai.llm.daily-quota` |
| 熔断阈值 | 10 失败/5min | 1-100 / 1-60min | `lp.ai.llm.circuit-breaker.*` |
| 熔断恢复 | 30min | 1-1440min | 同上 |
| 缓存 TTL | 6h | 60s-24h | 可扩展配置项 |

- **Redis 不可用降级**：quota **fail-open**（放行）/ circuit **fail-closed**（不熔断）/ 缓存 **MISS**（走计算）

### 11.6 错误码扩展（v2.1 新增 4 个）

| code | 含义 | HTTP | 是否直接返用户 |
|---|---|---|---|
| 1510 | LLM 配额超限 | 429 | ❌（Service catch → L2）|
| 1511 | LLM 熔断中 | 503 | ❌（Service catch → L2）|
| 1512 | LLM 响应解析失败 | 503 | ❌（Service catch → L2）|
| 1513 | LLM 不可用 | 503 | ❌（Service catch → L2）|

> **关键设计**：1510/1511/1512/1513 这 4 个错误码几乎不会直接出现在响应中——Service 内部全部 catch 走 L2 模板。它们主要在日志 / metrics 中体现，真实给用户看到的只有 0/1006/1501/500。

### 11.7 测试阈值（v2.1 强制）

| 层 | 工具 | 阈值 | 闸门 |
|---|---|---|---|
| `LlmInsightGenerator` | JUnit 5 + Mockito | **行覆盖 ≥ 90%** | `mvn verify` 中 JaCoCo 强制 |
| `LlmClient` 各实现 | 同上 | ≥ 80% | 同上 |
| `LlmCircuitBreaker` | 同上 | ≥ 85% | 同上 |
| `LlmQuotaGuard` | 同上 | ≥ 85% | 同上 |
| `AiInsightService` | 同上 | ≥ 85%（v2.0 基线 ≥ 80%）| 同上 |
| Controller 鉴权/错误码 | `@WebMvcTest` | 100% | 同上 |
| Testcontainers IT | `@SpringBootTest` | ≥ 6 用例（含 Ollama 模式端到端）| 同上 |
| 前端 store | Vitest | 100% 关键 action | `pnpm test` |
| `AiAnalysisView` | Vitest | 100% 关键组件 | 同上 |
| E2E `ai-v2-1-flow.spec.ts` | Playwright | 5 用例（AI 卡 source + 抽屉跳转 + 独立分析页 4 段 + Ollama 切换 + refresh）| `pnpm exec playwright test` |

### 11.8 禁止的反模式（v2.1 显式不做）

| 反模式 | 原因 |
|---|---|
| LLM 失败时**重试 / 异步重试 / 切备用 provider** | 复杂度不值；DeepSeek 抖动 5s 内重试大概率失败 |
| 降级时给前端**额外提示** | 用户无感最重要（source 标签 + 缺段已足够）|
| 1501 时**返回部分数据** | 1501 语义是完全不可用，部分数据会误导前端 |
| **用户级 LLM 开关**（`t_user.ai_llm_enabled`）| 1 人项目无价值；全局 `lp.ai.llm.enabled` 已够 |
| **月度预算上限** | DeepSeek 成本 < 5 元/月，不需要 |
| **Spring AI 库** | 手写 `RestClient` 200 行够用，0 新依赖 |
| **历史 insight 落 DB** | "无新表"硬约束 + Redis 6h 缓存够用 |
