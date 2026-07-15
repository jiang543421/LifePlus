# 03 — API & Auth（后端 API + 鉴权流程 + 端点清单）

> 本文件为 LifePulse MVP1 设计规格的第 3 部分。
> 编码时单独加载本文，无需加载其他 4 个子文件。
>
> **索引**：[00-overview.md](./00-overview.md) · [01-architecture](./01-architecture.md) · [02-database](./02-database.md) · [04-frontend](./04-frontend.md) · [05-nfr-testing](./05-nfr-testing.md)

---

## 1. 全局约定

- 前缀：`/api/v1`
- `Content-Type: application/json; charset=utf-8`
- 时间格式：ISO-8601（`2026-07-15T09:30:00+08:00`）

## 2. 统一返回信封

```json
{
  "code": 0,
  "message": "ok",
  "data": <payload | null>,
  "traceId": "abc123"
}
```

## 3. 错误码表

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 1001 | 400 | 参数校验失败 |
| 1002 | 401 | 未登录 / token 失效 |
| 1003 | 403 | 无权访问（跨用户资源） |
| 1004 | 404 | 资源不存在 |
| 1005 | 409 | 资源冲突（如邮箱已注册） |
| 1006 | 429 | 登录限流 |
| 1401 | 401 | refresh token 失效 |
| 1500 | 500 | 系统异常 |

## 4. 鉴权流程

```
Client                                          Server
  │ POST /api/v1/auth/register                     │
  │ ────────────────────────────────────────────► │
  │ ◄────────────────────────────────────────────  │ 201 {userId}
  │ POST /api/v1/auth/login                        │
  │ ────────────────────────────────────────────► │
  │ ◄────────────────────────────────────────────  │ 200 {access, refresh, expiresIn}
  │ GET /api/v1/users/me  (Bearer access)          │
  │ ────────────────────────────────────────────► │
  │ ◄────────────────────────────────────────────  │ 200 {id,email,nickname}
  │  ←─── 任意接口返回 1002 ───                     │
  │ POST /api/v1/auth/refresh {refresh}            │
  │ ────────────────────────────────────────────► │
  │ ◄────────────────────────────────────────────  │ 200 {新 access, 新 refresh}
  │ POST /api/v1/auth/logout  {refresh}            │
  │   ↳ t_refresh_token.revoked_at = NOW           │
```

## 5. 端点清单

### 5.1 Auth

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/v1/auth/register` | `{email, password, nickname?}` | 201 `{userId}` |
| POST | `/api/v1/auth/login` | `{email, password}` | 200 `{accessToken, refreshToken, expiresIn(秒)}` |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` | 200 `{accessToken, refreshToken(新)}` |
| POST | `/api/v1/auth/logout` | `{refreshToken}` | 200 |

### 5.2 User

| Method | Path | Response |
|---|---|---|
| GET | `/api/v1/users/me` | 200 `{id, email, nickname, createdAt}` |

### 5.3 Task

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/v1/tasks` | 过滤：`status, priority, tag, dueFrom, dueTo, page, size` |
| GET | `/api/v1/tasks/{id}` | 单个 |
| POST | `/api/v1/tasks` | `{title, dueDate?, priority?, tag?, planId?}` |
| PUT | `/api/v1/tasks/{id}` | `{title?, status?, priority?, tag?, planId?}` |
| PATCH | `/api/v1/tasks/{id}/status` | `{status}` 状态切换 |
| DELETE | `/api/v1/tasks/{id}` | 逻辑删 |
| GET | `/api/v1/tasks/by-plan/{planId}` | 列某计划下的任务 |

### 5.4 Plan

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/v1/plans` | 过滤：`from, to, page, size`（日历范围查） |
| GET | `/api/v1/plans/{id}` | 单个 |
| POST | `/api/v1/plans` | `{title, startTime, endTime, allDay?, location?, note?, reminderMin?}` |
| PUT | `/api/v1/plans/{id}` | `{...同上部分字段}` |
| DELETE | `/api/v1/plans/{id}` | 逻辑删 |

## 6. 安全细节

- **越权**：每个 `{id}` 端点先按 `user_id` 过滤，越权返回 1003
- **登录限流**：同 IP/邮箱 5 次/分钟（Redis 计数），超限返回 1006
- **密码**：BCrypt(strength=10)；注册校验 ≥8 位 + 含字母 + 含数字
- **JWT**：HS256，载荷 `sub=userId, iat, exp, typ=access|refresh`
- **Refresh 旋转**：每次 refresh 颁发新 refresh，旧 refresh `revoked_at = NOW`
- **Refresh 哈希落库**：DB 只存 `token_hash = SHA-256(token)`
- **CORS**：开发允许 `http://localhost:5173`；生产收紧白名单
- **输入校验**：DTO 全量 `@Valid`，错误统一 1001
- **SQL 注入**：MyBatis-Plus + 参数绑定，禁止字符串拼接

## 7. 后端技术栈

- Spring Boot 3.x / Maven
- Spring Security + JJWT（HS256）
- BCrypt（密码）
- MyBatis-Plus 3.x（ORM + `@TableLogic` 逻辑删除）
- MySQL 8.0 / Flyway（迁移）
- Spring Data Redis（登录限流计数 + refresh 黑名单）
- Lombok / Jakarta Validation
- JUnit 5 + Mockito + Testcontainers + `@WebMvcTest` + `@SpringBootTest`（测试）

## 8. 后端约定补充

- 所有 Controller 必须 `@RestController` + 显式 `@RequestMapping("/api/v1/...")`
- 所有 DTO 用 `record` 或 `@Data` class（不可变优先，参考项目 `coding-style.md`）
- 业务异常统一抛 `BusinessException(code, msg)`，由 `@RestControllerAdvice` 转信封
- `traceId` 由入口拦截器生成（UUID），贯穿 MDC + 响应信封 + logback
