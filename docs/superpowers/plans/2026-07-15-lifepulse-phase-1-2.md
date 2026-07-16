# Implementation Plan: LifePulse MVP1 Phase 1.2 (A-004..A-007)

> 本计划仅依赖 `docs/specs/03-api-auth.md`（按 §8 编码期 sub-spec 单读规则）。
> 一切以 `CLAUDE.md` + 全局规则为准；Java 细则（record / 构造器注入 / AssertJ）遵循 `~/.claude/rules/ecc/java/*`。
>
> **作者注**：本计划由 planner subagent 输出，主会话已审阅并修正一处偏差（原 §3 误列 `UserResponse` 为 1.2 文件，**已删除**——`users/me` 推迟到 1.3，其 DTO 同批落地）。

---

## 1. Goal Recap

- **Phase 1.2 ships**：通用业务异常 + 信封占位、BCrypt + PasswordEncoder 配置、RateLimiter（Redis Lua）、JwtService（HS256 issue/parse + 启动校验密钥 ≥32B）、AuthService（register/login/refresh/logout）、配套单测 + Testcontainers IT；最小 SecurityConfig 桥（permitAll `/auth/**`、stateless），让 IT 跑得起来。
- **Phase 1.2 defers to 1.3**：AuthController + `@RestControllerAdvice`、`JwtAuthFilter` 接入请求链、`GET /users/me`、`UserResponse` DTO、完整 SecurityConfig、CORS。

---

## 2. Acceptance Criteria (A-004..A-007)

| 编号 | 验收点 | 测试要求 |
|---|---|---|
| A-004 DTOs | 5 个 record DTO（不含 `UserResponse`），`@Valid` 全部生效；email 正则；密码 ≥8 且含字母+数字；nickname ≤32 | `DtoValidationTest`（Bean Validation 触发断言） |
| A-005 BCrypt | `PasswordEncoder` bean，strength=10；同 password → 两次 hash 不等；`matches` 命中 | `AuthServiceIT` roundtrip + `AuthServiceTest` |
| A-006 AuthService | register/login/refresh/logout 4 方法行为符合 03-api-auth.md；rate-limit 5/min/IP+email 命中抛 1006 | 单测（Mockito）+ IT（TC MySQL + 本地 Redis）覆盖 happy path + 全部错误码 |
| A-007 JwtService | HS256 issue/parse；secret <32B 启动失败；payload 含 `sub/iat/exp/typ`；过期/伪造抛 1401 | `JwtServiceTest` + `AuthServiceIT` refresh 旋转与重放 |

**覆盖率硬指标**：AuthService、JwtService、RateLimiter 行覆盖 ≥80%；闸门 `mvn verify` + JaCoCo。

---

## 3. File-by-File Build Order

### Phase 1.2-A：通用基础设施（4 文件）

| 路径 | 职责 |
|---|---|
| `backend\src\main\java\com\lifepulse\common\exception\BusinessException.java` | `RuntimeException` 子类，承载 `code + message`；常量集中定义错误码 1001/1002/1003/1005/1006/1401 |
| `backend\src\main\java\com\lifepulse\common\web\MyResponse.java` | envelope record `{int code, String message, T data, String traceId}`；含 `ok(T)` / `error(int,String)` 静态工厂（handler 在 1.3 落地，1.2 仅供 IT 断言） |
| `backend\src\main\java\com\lifepulse\common\security\PasswordEncoderConfig.java` | `@Configuration` 注册 `BCryptPasswordEncoder(10)`；常量从 `AuthConstants` 引用 |
| `backend\src\main\java\com\lifepulse\common\security\RateLimiter.java` | `@Component`；`boolean hit(String key, int max, Duration window)`；`StringRedisTemplate` + Lua 脚本 `INCR + EXPIRE NX` 原子；返回 `count > max` |

### Phase 1.2-B：DTOs（5 个 record；不含 `UserResponse`）

| 路径 | 字段 + 校验 |
|---|---|
| `backend\src\main\java\com\lifepulse\auth\dto\RegisterRequest.java` | `@Email @NotBlank @Size(max=128)` email；`@NotBlank @Size(min=8,max=64) @Pattern` 密码（letter+digit）；`@Size(max=32) String nickname` 可选 |
| `backend\src\main\java\com\lifepulse\auth\dto\LoginRequest.java` | email + password（`@NotBlank`） |
| `backend\src\main\java\com\lifepulse\auth\dto\RefreshRequest.java` | `@NotBlank String refreshToken` |
| `backend\src\main\java\com\lifepulse\auth\dto\LogoutRequest.java` | `@NotBlank String refreshToken` |
| `backend\src\main\java\com\lifepulse\auth\dto\AuthResponse.java` | record `{String accessToken, String refreshToken, Duration expiresIn}`；`static AuthResponse of(jwt, jwt, ttl)` |

> **`UserResponse` 不在 1.2 创建**——`GET /users/me` 与完整 SecurityConfig、`@RestControllerAdvice`、`JwtAuthFilter` 同批推迟到 Phase 1.3。1.2 IT 通过 entity 直接断言，避免死代码。

### Phase 1.2-C：JwtService + 启动校验（1 文件 + 单测）

| 路径 | 职责 |
|---|---|
| `backend\src\main\java\com\lifepulse\auth\service\JwtService.java` | `@Service`；`@PostConstruct` 校验 `secret.getBytes(UTF_8).length >= 32`，否则抛 `IllegalStateException`；`String issueAccess(Long userId)` / `String issueRefresh(Long userId)` / `Claims parse(String token)`；payload `{sub:userId(String), iat, exp, typ}`；TTL 从 `@ConfigurationProperties("lp.jwt")` 注入 |

### Phase 1.2-D：AuthService（1 文件 + 单测）

| 路径 | 职责 |
|---|---|
| `backend\src\main\java\com\lifepulse\auth\service\AuthService.java` | 构造器注入：UserMapper / RefreshTokenMapper / PasswordEncoder / JwtService / RateLimiter；方法 `register(req,ip)` / `login(req,ip)` / `refresh(req,ip)` / `logout(req)`；常量集中到 `AuthConstants`；refresh 流程 SHA-256(old) → 撤销 → 发新 → 持久化新行（事务） |

### Phase 1.2-E：桥接 SecurityConfig + 集成测试（6 文件）

| 路径 | 职责 |
|---|---|
| `backend\src\main\java\com\lifepulse\security\SecurityConfig.java` | `@EnableWebSecurity`；`SecurityFilterChain`：`permitAll /auth/**`, `/actuator/health`；`csrf().disable()`；`sessionCreationPolicy(STATELESS)`；显式 `// TODO(phase=1.3)` 注释。理由见 §7 |
| `backend\src\test\java\com\lifepulse\it\AuthServiceIT.java` | register→login→refresh→logout happy path；BCrypt roundtrip；refresh 重放 1401；login 限流 1006 |
| `backend\src\test\java\com\lifepulse\it\RateLimiterIT.java` | 真 Redis：第 6 次 `hit()` 返回 true；窗口过期后重置 |
| `backend\src\test\java\com\lifepulse\auth\service\AuthServiceTest.java` | Mockito 单测：各错误码分支（不含 1006） |
| `backend\src\test\java\com\lifepulse\auth\service\JwtServiceTest.java` | issue/parse 字段正确；过期 token 抛 1401；篡改签名抛 1401；启动 secret <32B 抛 IllegalStateException |
| `backend\src\test\java\com\lifepulse\auth\dto\DtoValidationTest.java` | `@Valid Validator` 触发各 record 字段非法值，断言 ConstraintViolation |

另增：
- `backend\src\main\java\com\lifepulse\auth\AuthConstants.java`（集中所有魔术数字）
- `backend\src\main\java\com\lifepulse\auth\config\JwtProperties.java`（`@ConfigurationProperties("lp.jwt")`）

---

## 4. Test Strategy Table

| 新类 | 测试类 | 关键用例（methodName_stateUnderTest_expectedBehavior） |
|---|---|---|
| `BusinessException` | `BusinessExceptionTest` | `ctor_setsCodeAndMessage` |
| `MyResponse` | `MyResponseTest` | `ok_wrapsData`, `error_setsCodeAndMessage` |
| `PasswordEncoderConfig` | 间接经 `AuthServiceIT` 覆盖 | — |
| `RateLimiter` | `RateLimiterIT`（真 Redis） | `hit_underMax_returnsFalse`, `hit_overMax_returnsTrue`, `hit_afterWindowExpires_returnsFalse` |
| `JwtService` | `JwtServiceTest` | `issueAccess_containsSubjectUserIdAndTyp`, `issueRefresh_typIsRefresh`, `parse_expiredToken_throws1401`, `parse_tamperedSignature_throws1401`, `init_shortSecret_throwsIllegalState` |
| `AuthService` | `AuthServiceTest`（Mockito） | `register_newEmail_persistsUserAndReturnsUserId`, `register_existingEmail_throws1005`, `login_wrongPassword_throws1002`, `login_unknownEmail_throws1002`, `refresh_unknownToken_throws1401`, `refresh_revokedToken_throws1401`, `refresh_expiredToken_throws1401`, `logout_unknownToken_isNoop` |
| `AuthService` | `AuthServiceIT`（TC MySQL + 本地 Redis） | `register_login_refresh_logout_happyPath`, `refresh_replayAfterRotate_throws1401`, `login_rateLimit_sixthAttempt_throws1006`, `register_rateLimit_fourthAttempt_throws1006`, `bcrypt_roundtrip_loginMatchesOriginal` |
| DTOs | `DtoValidationTest`（Validator） | `register_blankEmail_violates`, `register_shortPassword_violates`, `register_passwordNoDigit_violates`, `login_blankField_violates` |
| `SecurityConfig` 桥 | 不单测；由 `AuthServiceIT` 通过 `@SpringBootTest` 隐式覆盖 |

---

## 5. Constants Inventory（集中到 `AuthConstants.java`）

| 常量 | 值 | 用途 |
|---|---|---|
| `BCRYPT_STRENGTH` | 10 | `PasswordEncoderConfig` |
| `ACCESS_TTL` | `Duration.ofHours(1)` | JwtService 缺省（`lp.jwt.access-ttl` 覆盖） |
| `REFRESH_TTL` | `Duration.ofDays(7)` | JwtService 缺省 |
| `LOGIN_RL_MAX` | 5 | AuthService.login |
| `LOGIN_RL_WINDOW` | `Duration.ofMinutes(1)` | 同上 |
| `REGISTER_RL_MAX` | 3 | AuthService.register |
| `REGISTER_RL_WINDOW` | `Duration.ofMinutes(1)` | 同上 |
| `REFRESH_TOKEN_BYTES` | 32 | SecureRandom 生成 raw token 长度 |
| `JWT_SECRET_MIN_BYTES` | 32 | JwtService 启动校验 |
| `LOGIN_RL_KEY_PREFIX` | `"lp:rl:login:"` | RateLimiter |
| `REGISTER_RL_KEY_PREFIX` | `"lp:rl:register:"` | 同上 |
| `RATE_LIMIT_LUA` | Lua 脚本字面量 | `RateLimiter` 私有 `static final` |

> `application.yml` 中 `lp.jwt.access-ttl` / `lp.jwt.refresh-ttl` 仍由 `@ConfigurationProperties("lp.jwt")` 注入（spec 入口）；`AuthConstants` 中的同名常量是**编程内默认 / 单测用值**，避免单测读 YAML。

---

## 6. Rate-Limit Design

**选择 Lua INCR + EXPIRE NX 原子脚本**：

```
local cur = redis.call('INCR', KEYS[1])
if cur == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
return cur
```

- 不用纯 StringRedisTemplate 多次调用：`INCR` 后 `EXPIRE` 之间存在崩溃窗口（`INCR=1` 但 `EXPIRE` 未设即异常 → key 永久不过期）。
- 不用 Redis 7+ 原生 token bucket：spec 明确 5/min 固定窗口，Lua INCR+EXPIRE 语义更直接。
- **key 设计**：`lp:rl:login:<ip>:<email-sha256-prefix-8>`（email 哈希前缀 8 字符避免日志/Redis key 泄漏完整邮箱，CLAUDE.md §7.3 禁打完整 email）；注册端点 `lp:rl:register:<ip>`。
- **失败开放策略**：Redis 不可达 → `catch(DataAccessException)`，记 WARN，降级放行；1.3 接告警。

---

## 7. SecurityConfig Stub in 1.2 — Why Now, Not 1.3

**Decision**：1.2 必须落地最小 `SecurityConfig` 桥。

**理由**：
1. `AuthServiceIT` 用 `@SpringBootTest` 拉起完整 Spring 上下文；1.3 之前无 `SecurityFilterChain` bean 时，Spring Security 6 默认 `defaultSecurityFilterChain` 对所有请求要求 Basic Auth —— 一旦 1.3 启用 `@AutoConfigureMockMvc` 即崩。
2. 1.2 阶段没有 `JwtAuthFilter`，任何带 JWT 的请求也无法被解析；最小桥保证 `/auth/**` 全放行 + `STATELESS` 与 1.3 接入 filter 后语义一致。
3. 提前暴露 Security 链潜在冲突（如 CSRF 默认开启让 POST 失败），避免 1.3 一次性排雷。
4. SecurityConfig 桥 <40 行，不引入新概念；显式 `// TODO(phase=1.3): replace with JwtAuthFilter chain + /users/me` 注释。

**实现要点**：`permitAll("/auth/**", "/actuator/health")`，其余 `authenticated()`；`csrf.disable()`；`sessionManagement(STATELESS)`；**不引入** `JwtAuthFilter`（1.3 才有）。

---

## 8. Coverage Plan (≥80% Service Layer)

| 服务 | 行数预估 | 单测覆盖 | IT 覆盖 | 合计估算 |
|---|---|---|---|---|
| `JwtService` | ~80 | `JwtServiceTest`：5 case 100% | 经 AuthServiceIT 间接覆盖 | ≥85% |
| `AuthService` | ~150 | `AuthServiceTest`：8 case 覆盖所有分支（含异常） | `AuthServiceIT` 5 case 覆盖 happy + 限流 + 重放 | ≥85% |
| `RateLimiter` | ~50 | 无（避免 mock Redis） | `RateLimiterIT` 3 case 100% | ≥85% |

**JaCoCo 闸门**：`mvn verify` 失败即阻塞 CI（pom 已配置）。

---

## 9. TDD Task List per File

### BusinessException
1. **RED**：`BusinessExceptionTest.constructor_setsCodeAndMessage` → 类不存在失败。
2. **GREEN**：`BusinessException extends RuntimeException` + final `code` + 构造器。
3. **REFACTOR**：无。

### MyResponse
1. **RED**：`MyResponseTest.ok_wrapsData` + `error_setsCodeAndMessage`。
2. **GREEN**：record + 静态工厂。
3. **REFACTOR**：无。

### PasswordEncoderConfig
1. **RED**：`@SpringBootTest` contextLoads 失败。
2. **GREEN**：`@Configuration` + `@Bean PasswordEncoder`。
3. **REFACTOR**：抽 `BCRYPT_STRENGTH` 到 `AuthConstants`。

### RateLimiter
1. **RED**：`RateLimiterIT.hit_underMax_returnsFalse`。
2. **GREEN**：实现 `hit()` 用 Lua 脚本。
3. **RED**：`hit_overMax_returnsTrue` 第 6 次 true。
4. **RED**：`hit_afterWindowExpires_returnsFalse` 等窗口过期。
5. **REFACTOR**：抽 Lua 为 `static final` + key prefix 常量。

### DTOs（先批量 RED）
1. **RED**：`DtoValidationTest` 6 case：blank email / invalid email / short password / password no digit / oversized nickname / null password。
2. **GREEN**：5 个 record 加校验注解直到全绿。
3. **REFACTOR**：公共错误信息文本（message key 化留给 1.3 handler）。

### JwtService
1. **RED**：`init_shortSecret_throwsIllegalState`（构造时 secret=16B）。
2. **GREEN**：`@PostConstruct` 校验长度。
3. **RED**：`issueAccess_containsSubjectUserIdAndTyp`。
4. **GREEN**：`Jwts.builder().subject().issuedAt().expiration().claim("typ","access").signWith(Keys.hmacShaKeyFor(...))`。
5. **RED**：`parse_expiredToken_throws1401`。
6. **GREEN**：catch `ExpiredJwtException` → 抛 `BusinessException(1401)`。
7. **RED**：`parse_tamperedSignature_throws1401`。
8. **GREEN**：catch `JwtException` → 1401。
9. **REFACTOR**：合并到 `private Claims parseOrThrow(String)` helper。

### AuthService
1. **RED**：`register_newEmail_persistsUserAndReturnsUserId`（mock UserMapper.findByEmail → null；mock insert 回填 id）。
2. **GREEN**：register：查 email 重 → 哈希 → insert → 返回 id。
3. **RED**：`register_existingEmail_throws1005`。
4. **GREEN**：命中后抛 `BusinessException(1005)`。
5. **RED**：`login_wrongPassword_throws1002`。
6. **GREEN**：`passwordEncoder.matches` false → 1002。
7. **RED**：`refresh_revokedToken_throws1401`。
8. **GREEN**：refresh 流程：查 hash → 检查 `revokedAt==null && expiresAt>now` → 生成新对 → `revokeByHash` + insert new。
8. **RED**：`refresh_expiredToken_throws1401`。
10. **RED**：`logout_unknownToken_isNoop`（幂等）。
11. **REFACTOR**：抽 `private String issueAndPersist(userId, typ)`。

### SecurityConfig 桥
- 无单测；IT 隐式覆盖 context 加载。

### AuthServiceIT
1. 复用 `AbstractIntegrationTest`，`@Autowired AuthService`。
2. `register_login_refresh_logout_happyPath` 端到端；断言 user 行存在 + refresh 行 2 条（旧 revoked、新 active）。
3. `refresh_replayAfterRotate_throws1401`。
4. `login_rateLimit_sixthAttempt_throws1006`（同 ip+email 连发 6 次）。
5. `bcrypt_roundtrip_loginMatchesOriginal`。

### RateLimiterIT
- 已在 RateLimiter TDD 步骤中描述。

---

## 10. Risks & Open Questions

| 风险 / 待确认 | 处置 |
|---|---|
| `GET /users/me` 与 `UserResponse` 范围 | **不在 1.2**，全部推迟到 1.3 |
| `JwtAuthFilter` 1.2 是否落地？ | **否**，仅落 `SecurityConfig` 桥；filter 1.3 与 `users/me` 同批 |
| Redis 不可达时 RateLimiter 行为 | 失败开放（降级放行） + WARN 日志；1.3 接告警 |
| Refresh token raw 长度 32B 是否够 | 够；spec §3.4 明确 32B = 256 bit 安全强度 |
| `lp:rl:login:<ip>:<email>` 中 email 是否需哈希化 | 建议 SHA-256 前 8 字符前缀（避免完整 email 落入 Redis key 与 slowlog）；spec 未明确，1.2 实现哈希前缀，1.3 review 时确认 |
| Flyway V1 是否已含 refresh token 表 | 已确认（Phase 1.1 落 `V1__init_user_and_refresh_token.sql`）；1.2 无需新迁移 |
| `@ConfigurationProperties("lp.jwt")` 是否需要新建 `JwtProperties` 类 | 需要（1.2 落）；`@ConfigurationPropertiesScan` 在 `LifePulseApplication` 已默认启用 |
| 单测中 JwtService 启动校验 secret 长度怎么办 | 用 `@SpringBootTest(properties="lp.jwt.secret=test-only-replace-me-32bytes-min!!")`（已在 `SmokeTest` 使用过 ≥32 字符占位） |
| BCrypt 单测是否需要真 bean | 不需要；`AuthServiceTest` 直接 `new BCryptPasswordEncoder(10)` |

---

## 11. Out-of-Scope Confirmation

- **不写**：AuthController、`@RestControllerAdvice`、`JwtAuthFilter`、`UserContext`、`UserResponse`（全部 1.3）、`/users/me`、前端、Plan/Task、CORS（1.3 与前端联调阶段）。
- **不改**：`User` / `RefreshToken` entity、`UserMapper` / `RefreshTokenMapper`、`AbstractIntegrationTest`、`pom.xml`（已确认 validation starter 在 1.1 pom）。
- **不动**：`application.yml` 中的 `lp.jwt.*` 与 `spring.data.redis.*` 配置项。

---

**Total LOC 估算**：~900 行（生产）+ ~700 行（测试）；约 10 个生产类 + 6 个测试类。

---

*Generated 2026-07-15 · Phase 1.2 planner output, reviewed and corrected by main session.*