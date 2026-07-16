# Implementation Plan: LifePulse MVP1 Phase 1.3 (A-008..A-011)

> 本计划仅依赖 `docs/specs/03-api-auth.md`（按 §8 编码期 sub-spec 单读规则）。
> 一切以 `CLAUDE.md` + 全局规则为准；Java 细则遵循 `~/.claude/rules/ecc/java/*`。
>
> **作者注**：本计划由主会话基于已落地的 `2026-07-15-lifepulse-phase-1-2.md` 与 `masterplan` § Phase 1 A-008..A-011 任务表产出。Phase 1.2 已 commit `471b2b3`，本计划承接其后的 4 个子阶段。

---

## 1. Goal Recap

- **Phase 1.3 ships**：AuthController（4 端点）+ `@RestControllerAdvice`（全局异常→信封）+ `TraceIdFilter`；JwtAuthFilter + UserContext + SecurityConfig 全量替换；`GET /users/me` + `UserResponse` DTO；`@WebMvcTest` 切片 + Testcontainers 端到端 IT 一并落地；SmokeTest 兼容回归。
- **Phase 1.3 defers to 1.4**：前端 `authStore`、`axios` 拦截器、LoginView/RegisterView、E2E、CORS（dev `http://localhost:5173`）。
- **承接 1.2**：`MyResponse`（已存在；master plan 名 `ApiResponse` 等同物，本计划沿用 `MyResponse` 命名一致）、`AuthConstants` 错误码常量、`AuthService.register(req,ip)` / `login(req,ip)` / `refresh(req,ip)` / `logout(req)` 签名（不动）、`SecurityConfig` 桥 stub（1.3 全量替换）。

---

## 2. Acceptance Criteria (A-008..A-011)

| 编号 | 验收点 | 测试要求 |
|---|---|---|
| **A-008** Controller + envelope | 4 端点（`/auth/{register,login,refresh,logout}`）走 `MyResponse` 信封；register 走 `201 Created`；`@Valid` 失败→`1001`/`400`；`BusinessException` 业务异常统一映射 | `AuthControllerWebTest` 7+ case：注册 201 / 重邮箱 1005 / 校验失败 1001 / 登录 200 / 错密码 1002 / 限流 1006 / refresh 旋转 200 / logout 200 |
| **A-008** TraceId | 入口 filter 生成 UUID 写 MDC + 写响应头 `X-Trace-Id`；response body `traceId` 字段回填 | `TraceIdFilterTest`（HTTP 拦截器直测） |
| **A-008** GlobalExceptionHandler | `BusinessException` → 信封 `{code, message, data:null, traceId}`；`MethodArgumentNotValidException` → `1001`；其他 `Exception` → `1500` | Handler 测试 + 端到端 IT 隐式覆盖 |
| **A-009** SecurityConfig 全量 | CSRF off、STATELESS、`/auth/**` + `/actuator/health` permitAll；其余 `authenticated()`；未授权 401 走统一信封；自定义 `AuthenticationEntryPoint` 输出 JSON | `AuthFlowIT` 隐式覆盖；`UserControllerWebTest` 验证 401 信封 |
| **A-009** JwtAuthFilter | 解析 `Authorization: Bearer <access>`；无效→`1401`/401；有效→`SecurityContextHolder` 注入 `Authentication(userId, "ROLE_USER")` | 单测（直接 new filter + 注入 mock `JwtService`）+ `AuthFlowIT` 端到端 |
| **A-009** UserContext | `ThreadLocal<Long>`；`current()` / `set(id)` / `clear()`；静态工具 | 单测 `UserContextTest` 覆盖并发场景（`@AfterEach clear`） |
| **A-009** GET /users/me | 鉴权后返回 `UserResponse(id, email, nickname, createdAt)`；token 失效→401 信封 | `UserControllerWebTest` 2 case（200/401） |
| **A-010** 切片测试 | `@WebMvcTest` 覆盖全部 auth 端点 + `/users/me`；不启动 Spring 全上下文，速度 < 1s | `AuthControllerWebTest` + `UserControllerWebTest` 12+ case |
| **A-011** 端到端 IT | Testcontainers MySQL + 本地 Redis：`register → login → /users/me → refresh → 再 refresh 旧 = 1401 → logout → refresh revoked = 1401` | `AuthFlowIT` 5 case 全绿 |
| **A-011** SmokeTest 兼容 | 加 `@MockBean UserMapper` 等 mocks 让应用上下文在 exclude DataSource 情况下仍可加载 | `mvn -q -B test` 全绿（含 8 个测试类） |

**覆盖率硬指标**：AuthController、JwtAuthFilter、UserContext、GlobalExceptionHandler 合计行覆盖 ≥80%；闸门 `mvn verify` + JaCoCo。

---

## 3. File-by-File Build Order

### Phase 1.3-A：Controller + envelope + 全局异常 + traceId（A-008）

| 路径 | 职责 |
|---|---|
| `backend\src\main\java\com\lifepulse\common\web\TraceIdFilter.java` | `OncePerRequestFilter`；`UUID.randomUUID().toString()` 写 `MDC("traceId")` + 响应头 `X-Trace-Id`；`finally` 清 MDC |
| `backend\src\main\java\com\lifepulse\common\exception\GlobalExceptionHandler.java` | `@RestControllerAdvice`；分别 `@ExceptionHandler({BusinessException, MethodArgumentNotValidException, HttpMessageNotReadableException, Exception})`；返回 `MyResponse.error(code, msg)`；`MethodArgumentNotValidException` → `1001`；其他 → `1500` |
| `backend\src\main\java\com\lifepulse\auth\dto\UserResponse.java` | record `{Long id, String email, String nickname, OffsetDateTime createdAt}`；静态 `from(User entity)` |
| `backend\src\main\java\com\lifepulse\auth\web\AuthController.java` | `@RestController @RequestMapping("/api/v1/auth")`；4 端点全部接 `@Valid @RequestBody`；每端点注入 `HttpServletRequest` 取 `getRemoteAddr()` 传给 `AuthService`；register `@ResponseStatus(HttpStatus.CREATED)` 返回 `MyResponse.ok(Map.of("userId", id))` |
| `backend\src\test\java\com\lifepulse\common\web\TraceIdFilterTest.java` | 用 `MockHttpServletRequest/Response` + `MockFilterChain` 验证响应头含 UUID |
| `backend\src\test\java\com\lifepulse\common\exception\GlobalExceptionHandlerTest.java` | `@WebMvcTest(controllers = DummyController.class)` 验证映射 |
| `backend\src\test\java\com\lifepulse\auth\web\AuthControllerWebTest.java` | `@WebMvcTest(AuthController.class) @MockBean AuthService`；`@AutoConfigureMockMvc(addFilters = false)` 避免 Security 链；7+ case |

> **命名一致性**：master plan 称 envelope 为 `ApiResponse`，1.2 已落地为 `MyResponse`，本计划沿用 `MyResponse` 不重命名（CLAUDE.md §4.3「不动既有数据模型的字段语义」）。

### Phase 1.3-B：Security 全量替换 + JwtAuthFilter + UserContext + users/me（A-009）

| 路径 | 职责 |
|---|---|
| `backend\src\main\java\com\lifepulse\security\UserContext.java` | 静态方法 `set(Long)` / `current() : Long` / `clear()`；`ThreadLocal<Long>` 包装；`@AfterEach` / `OncePerRequestFilter` 显式清 |
| `backend\src\main\java\com\lifepulse\security\JwtAuthFilter.java` | `OncePerRequestFilter`；从 header `Authorization: Bearer ...` 提 token；调 `jwtService.parse(token)`；成功→`UsernamePasswordAuthenticationToken(userId, null, ROLE_USER)` 注入 `SecurityContextHolder` + `UserContext.set(userId)`；失败→清空 Security + 不阻断（让后面 entry point 走 401） |
| `backend\src\main\java\com\lifepulse\security\JwtAuthEntryPoint.java` | `AuthenticationEntryPoint`；未授权时写 `MyResponse.error(1002, "未登录或凭证失效")` JSON + status 401 |
| `backend\src\main\java\com\lifepulse\security\SecurityConfig.java`（**全量替换**） | `@EnableWebSecurity`；`SecurityFilterChain`：permitAll `/api/v1/auth/**`, `/actuator/health`；其余 `authenticated()`；`csrf.disable()`；`sessionManagement(STATELESS)`；`addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`；`exceptionHandling.authenticationEntryPoint(jwtAuthEntryPoint)`；显式 `formLogin.disable()` / `httpBasic.disable()` / `logout.disable()` |
| `backend\src\main\java\com\lifepulse\auth\web\UserController.java` | `@RestController @RequestMapping("/api/v1/users")`；`@GetMapping("/me")` 返回 `UserResponse.from(user)`；userId 由 `UserContext.current()` 读取 |
| `backend\src\test\java\com\lifepulse\security\UserContextTest.java` | 设/读/清 + `CompletableFuture` 并发隔离 case |
| `backend\src\test\java\com\lifepulse\security\JwtAuthFilterTest.java` | 直接 `new JwtAuthFilter(mockJwtService)`；mock `MockHttpServletRequest` 头；mock `FilterChain`；验证 `SecurityContextHolder` 状态 |
| `backend\src\test\java\com\lifepulse\auth\web\UserControllerWebTest.java` | `@WebMvcTest(UserController.class) @Import(SecurityConfig.class)` + `@MockBean UserMapper`、`JwtAuthFilter`、`JwtAuthEntryPoint`；case：未带 token → 401 信封 `{code:1002}`；带有效 token → 200 UserResponse |

### Phase 1.3-C：端到端 IT（A-011）

| 路径 | 职责 |
|---|---|
| `backend\src\test\java\com\lifepulse\auth\web\AuthFlowIT.java` | `@SpringBootTest` + `Testcontainers`（复用 `AbstractIntegrationTest`） + `@AutoConfigureMockMvc`；5 case：register→login→/users/me→refresh→logout happyPath；refresh replay 1401；login 限流 1006；register 限流 1006；cross-user 防御（MVP1 不引入 task 跨用户，留 TODO） |
| `backend\src\test\java\com\lifepulse\SmokeTest.java`（**修补**） | 加 `@MockBean UserMapper` / `RefreshTokenMapper` / `RateLimiter` / `JwtService` / `AuthService` 让 exclude DataSource 时仍可构造应用上下文；`@AfterEach clear UserContext` |

> A-010 切片测试已在 1.3-A/B 文件清单中（AuthControllerWebTest + UserControllerWebTest）。

---

## 4. Test Strategy Table

| 新类 | 测试类 | 关键 case |
|---|---|---|
| `TraceIdFilter` | `TraceIdFilterTest` | `doFilterInternal_setsHeaderAndMdc`, `doFilterInternal_clearsMdcInFinally` |
| `GlobalExceptionHandler` | `GlobalExceptionHandlerTest` | `businessException_mapsToEnvelope`, `validationException_mapsTo1001`, `genericException_mapsTo1500` |
| `AuthController` | `AuthControllerWebTest` | `register_validRequest_returns201WithUserId`, `register_existingEmail_returns1005`, `register_blankEmail_returns1001`, `login_validRequest_returns200WithTokens`, `login_wrongPassword_returns1002`, `refresh_validToken_returns200WithNewTokens`, `logout_returns200` |
| `UserContext` | `UserContextTest` | `set_thenCurrent_returnsSame`, `clear_removesValue`, `differentThreads_haveIsolatedValues` |
| `JwtAuthFilter` | `JwtAuthFilterTest` | `doFilter_validToken_setsAuthenticationAndUserContext`, `doFilter_invalidToken_doesNotAuthenticate`, `doFilter_missingHeader_doesNotAuthenticate` |
| `UserController` | `UserControllerWebTest` | `getMe_noToken_returns401WithCode1002`, `getMe_validToken_returnsUserResponse` |
| `AuthFlow` | `AuthFlowIT` | `register_login_me_refresh_logout_happyPath`, `refresh_replayAfterRotate_returns1401`, `login_rateLimit_sixthAttempt_returns1006` |
| `SmokeTest` 兼容 | n/a（自检） | `mvn -q -B test` 8+ 测试类全绿 |

---

## 5. Constants Inventory（扩展 `AuthConstants`）

| 常量 | 值 | 用途 |
|---|---|---|
| `CODE_OK` | 0 | 信封成功 |
| `CODE_VALIDATION` | 1001 | GlobalExceptionHandler |
| `CODE_BAD_CREDENTIALS` | 1002 | 现有，JwtAuthEntryPoint 复用 |
| `CODE_CROSS_USER` | 1003 | 现有 |
| `CODE_NOT_FOUND` | 1004 | spec §3 表；1.3 不用，留位供 Phase 2/3 |
| `CODE_EMAIL_TAKEN` | 1005 | 现有 |
| `CODE_RATE_LIMIT` | 1006 | 现有 |
| `CODE_REFRESH_INVALID` | 1401 | 现有 |
| `CODE_SERVER` | 1500 | GlobalExceptionHandler 兜底 |

**HTTP 状态映射**（handler 内 switch `code → HttpStatus`，避免污染 `BusinessException`）：

```java
HttpStatus mapStatus(int code){
  return switch(code){
    case 1001 -> HttpStatus.BAD_REQUEST;          // 400
    case 1002, 1401 -> HttpStatus.UNAUTHORIZED;    // 401
    case 1003 -> HttpStatus.FORBIDDEN;             // 403
    case 1004 -> HttpStatus.NOT_FOUND;             // 404
    case 1005 -> HttpStatus.CONFLICT;              // 409
    case 1006 -> HttpStatus.TOO_MANY_REQUESTS;     // 429
    default  -> HttpStatus.INTERNAL_SERVER_ERROR;
  };
}
```

---

## 6. Design Decisions

### 6.1 UserContext vs `Authentication.principal`

**Decision**：双轨制——`Authentication` 走 Spring Security 标准（`principal = userId`）；`UserContext` 作为业务层静态读取入口。

**理由**：
- `@AuthenticationPrincipal Long userId` 在 `@RestController` 参数里也能读，但业务层（`TaskService`）通过 `UserContext.current()` 取更简洁
- 1.3 不写 TaskService，但需要为 Phase 2 留底
- `OncePerRequestFilter.finally` 必清 `UserContext`，避免线程池复用污染

### 6.2 `/users/me` token 类型

`/users/me` 只接 access token：JwtAuthFilter 不区分 access/refresh（解析通过即可）；访问 `/users/me` 时 controller 不需要额外校验——只要 token 合法即返回。未来可在 filter 检查 `typ=access`；MVP1 不实现。

### 6.3 1002 vs 1401 在 401 时的区分

| 触发 | code | 触发点 |
|---|---|---|
| `JwtAuthFilter` 解析失败（过期/伪造/格式错） | 1401 | filter catch + clear |
| 无 token / 已认证失败 | 1002 | `AuthenticationEntryPoint` |

前端拦截器按 spec §3：`1002 → 静默 refresh`，`1401 → 跳登录`。

### 6.4 SecurityConfig 全量替换

- 1.2 stub `permitAll(/auth/**, /actuator/health)` + stateless + csrf off 全部保留
- + `addFilterBefore(jwtAuthFilter, ...)` + `authenticationEntryPoint(jwtAuthEntryPoint)`
- + `formLogin.disable()` / `httpBasic.disable()` / `logout.disable()`（避免 Spring 默认 Basic 弹窗）

### 6.5 SmokeTest 兼容修复策略

**根因**：1.2-E SecurityConfig 桥 + `LifePulseApplication.@ConfigurationPropertiesScan` + `AuthService` eager（依赖 UserMapper / RefreshTokenMapper / JwtService / RateLimiter / PasswordEncoder）；`SmokeTest` 排除 `DataSourceAutoConfiguration` → MyBatis-Plus 无法构造 mapper → 上下文 `AuthService` 注入失败。

**修复方案**（在 1.3 全量 SecurityConfig + `@ConfigurationPropertiesScan` 仍在时）：
- `SmokeTest` 加 `@MockBean`：`UserMapper`、`RefreshTokenMapper`、`JwtService`、`AuthService`、`RateLimiter`
- 让 `AuthService` 在 SmokeTest 上下文中不实跑（应用启动时上下文中所有 Service 都构造成功即可）

**为什么不修 SecurityConfig / LifePulseApplication**：CLAUDE.md §4「不修改既有数据模型的字段语义」+ master plan 不允许改 `@ConfigurationPropertiesScan` 位置。

### 6.6 IP 取值

- 默认 `request.getRemoteAddr()` — dev 阶段 TCP 直连，等于 `127.0.0.1`
- 生产由 docker-compose 的 nginx `X-Forwarded-For` 头注入——`server.forward-headers-strategy: native` 已配（master plan T-004），Tomcat 自动识别
- 1.3 不解析 XFF；Phase 5 R-002 升级

---

## 7. Coverage Plan (≥80%)

| 模块 | 行数预估 | 单测 | IT | 合计 |
|---|---|---|---|---|
| `AuthController` | ~120 | `AuthControllerWebTest` 7+ case | `AuthFlowIT` 隐式 | ≥85% |
| `UserController` | ~40 | `UserControllerWebTest` 2 case | `AuthFlowIT` 隐式 | ≥90% |
| `JwtAuthFilter` | ~60 | `JwtAuthFilterTest` 3 case | `AuthFlowIT` | ≥85% |
| `JwtAuthEntryPoint` | ~30 | 隐式经 UserControllerWebTest | 同上 | ≥80% |
| `GlobalExceptionHandler` | ~80 | `GlobalExceptionHandlerTest` 3 case | 端到端 | ≥85% |
| `TraceIdFilter` | ~30 | `TraceIdFilterTest` 2 case | `AuthFlowIT` 头部断言 | ≥90% |
| `UserContext` | ~30 | `UserContextTest` 3 case | — | ≥95% |

---

## 8. TDD Task List（节选）

### TraceIdFilter
1. RED：setsHeaderAndMdc → 类不存在失败。
2. GREEN：extends OncePerRequestFilter；UUID → MDC + response header；finally clear。
3. RED：clearsMdcInFinally（异常路径）。
4. GREEN：try/finally。

### GlobalExceptionHandler
1. RED：throwaway controller 触发 3 类异常，断言 JSON。
2. GREEN：3 个 @ExceptionHandler，统一 MyResponse.error。
3. REFACTOR：抽 mapStatus(code) switch。

### AuthController
1. RED：register_validRequest_returns201WithUserId 失败。
2. GREEN：4 端点 + 注入 AuthService + HttpServletRequest。
3. RED：register_blankEmail_returns1001 → @Valid 失败。
4. GREEN：handler 映射 1001。

### SecurityConfig 全量
1. RED：getMe_noToken_returns401WithCode1002 失败。
2. GREEN：上 JwtAuthFilter + JwtAuthEntryPoint。

（更多 TDD 步骤见上文 §8 完整版）

---

## 9. Risks & Open Questions

| 风险 / 待确认 | 处置 |
|---|---|
| `JwtAuthFilter` 区分 access/refresh | MVP1 不区分；前端拦截器按 `code` 走不同分支 |
| 反向代理 IP 限流聚合 | 1.3 不动；6.6 节策略；Phase 5 接 `XFF` |
| `UserContext` 线程复用泄漏 | filter finally 清 + `@AfterEach clear`；CLAUDE.md §4.1 |
| Security 6 + `@WebMvcTest` 协作 | `@WebMvcTest` 默认 `addFilters=true`；需 `@Import(SecurityConfig.class)` + 对应 `@MockBean` filter bean |
| `MethodArgumentNotValidException` 取第一个错误还是全部 | 本计划取第一个 message 作顶层；后续可改 list |
| `MyResponse.traceId` 字段填法 | handler 取 `MDC.get("traceId")`；filter 已写 |
| `AuthFlowIT` vs 已落地 `AuthServiceIT` | `AuthFlowIT` 升级为 MockMvc 走 HTTP 全链路，新增 `/users/me` + envelope/traceId 覆盖 |

---

## 10. Out-of-Scope Confirmation

- **不写**：前端 / CORS / XFF / 修改昵称 / 修改密码 / 账号注销 / Task / Plan
- **不改**：User / RefreshToken entity、Mappers、Flyway V1、AuthService 4 方法签名、AuthConstants 已有常量、MyResponse、pom.xml、application.yml
- **不动**：LifePulseApplication

---

## 11. Commit Cadence (4 子阶段 × 1 commit)

| 子阶段 | commit 类型 | message |
|---|---|---|
| 1.3-A | feat(auth) | `feat(auth): controller, envelope, trace filter, exception handler` |
| 1.3-B | feat(security) | `feat(security): jwt filter, user context, full security config, users/me` |
| 1.3-C | test(auth) | `test(auth): web slice + e2e flow integration tests` |
| 1.3-D | chore(test) | `chore(test): smoke test compat + final verify gate` |

> 用户已授权 sub-phase commit cadence：每个子阶段完成后"message 确认通过"再 commit，不 push。

---

## 12. Pre-Acknowledgements

1. **不 push**：4 commits 全部本地
2. **每个 commit 前展示 message**：4 次确认
3. **sub-spec 单读**：仅 `docs/specs/03-api-auth.md`
4. **TDD Red→Green→Refactor**：每个新文件先 RED
5. **不自动 commit**：用户未确认前不 commit
6. **SmokeTest 兼容回归在 1.3-D** 解决，与 A-011 同步落

---

**Total LOC 估算**：~700 行（生产）+ ~900 行（测试）；约 8 个生产类 + 9 个测试类。

---

*Generated 2026-07-16 · Phase 1.3 planner output, ready for execution authorization.*
