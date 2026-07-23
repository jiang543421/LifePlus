# Phase 1 — Authentication

> **Sub-specs to load at start of Phase 1:** `02-database.md §2.1 & 2.4`, `03-api-auth.md §2-§7`.

## Task A-001: Flyway V1 — `t_user` + `t_refresh_token`

**Files:** `backend/src/main/resources/db/migration/V1__init_user_and_refresh_token.sql`, `backend/src/test/java/com/lifepulse/it/AbstractIntegrationTest.java`

- [ ] **Step 1: Write `AbstractIntegrationTest` (Testcontainers base)**
```java
@Testcontainers @SpringBootTest
public abstract class AbstractIntegrationTest {
  @Container public static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("lifepulse").withUsername("lp").withPassword("lp_dev_only");
  @Container public static RedisContainer<?> REDIS = new RedisContainer<>("redis:7-alpine");
  @DynamicPropertySource static void props(DynamicPropertyRegistry r){
    r.add("spring.datasource.url", MYSQL::getJdbcUrl);
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
    r.add("spring.data.redis.url", REDIS::getRedisURI);
  }
}
```

- [ ] **Step 2: Write `V1__init_user_and_refresh_token.sql`** using columns + 3 indexes (`uq_email`, `uq_token_hash`, `idx_expires_at`) per `02-database §2.1, §2.4, §3`. Add `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`.

- [ ] **Step 3: Verify Flyway** — `cd backend && mvn -q -B -DskipTests flyway:info` (use running local MySQL or accept offline output).

- [ ] **Step 4: Commit** `feat(db): init user and refresh token tables`

---

## Task A-002: `User` entity + `UserMapper`

**Files:** `backend/src/main/java/com/lifepulse/auth/entity/User.java`, `.../repository/UserMapper.java`, `.../common/mybatis/MetaObjectHandlerConfig.java`

- [ ] **Step 1: Failing IT**
```java
class UserMapperIT extends AbstractIntegrationTest {
  @Autowired UserMapper userMapper;
  @Test void insert_and_findByEmail() {
    User u = new User(); u.setEmail("a@b.com"); u.setPasswordHash("$2a$10$xxx"); u.setNickname("nick");
    userMapper.insert(u);
    assertThat(userMapper.findByEmail("a@b.com").getId()).isNotNull();
  }
}
```
Run: `mvn -q -B -Dtest=UserMapperIT test` — fails (no class).

- [ ] **Step 2: Write `User`**
```java
@Data @TableName("t_user")
public class User {
  @TableId(type=IdType.AUTO) private Long id;
  private String email; private String passwordHash; private String nickname;
  @TableField(fill=FieldFill.INSERT) private OffsetDateTime createdAt;
  @TableField(fill=FieldFill.INSERT_UPDATE) private OffsetDateTime updatedAt;
  @TableLogic private Integer deleted;
}
```

- [ ] **Step 3: Implement `MetaObjectHandler`** setting `OffsetDateTime.now()` on insert/update.

- [ ] **Step 4: Write `UserMapper`**
```java
@Mapper public interface UserMapper extends BaseMapper<User> {
  @Select("SELECT * FROM t_user WHERE email=#{email} AND deleted=0 LIMIT 1") User findByEmail(String email);
}
```

- [ ] **Step 5: Run IT (PASS). Commit** `feat(auth): user entity and mapper`

---

## Task A-003: `RefreshToken` entity + mapper

Same pattern as A-002 with columns + `findByHash` + `revokeByHash` SQL methods. Commit `feat(auth): refresh token entity and mapper`.

---

## Task A-004: BCrypt + DTOs

**Files:** `backend/src/main/java/com/lifepulse/auth/dto/{RegisterReq,LoginReq,TokenPair,RefreshReq,LogoutReq}.java`, `.../security/PasswordEncoderConfig.java`

DTO shapes (verbatim):
```java
public record RegisterReq(@Email @NotBlank String email, @NotBlank @Size(min=8,max=64) String password, @Size(max=64) String nickname) {}
public record LoginReq(@Email @NotBlank String email, @NotBlank String password) {}
public record RefreshReq(@NotBlank String refreshToken) {}
public record LogoutReq(@NotBlank String refreshToken) {}
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}
```

- [ ] **Step 1: Unit test BCrypt round-trip, implement config = `new BCryptPasswordEncoder(10)`. Commit** `feat(auth): password encoder and DTO records`

---

## Task A-005: `AuthService.register` + `login` (with rate limit)

**Files:** `backend/src/main/java/com/lifepulse/auth/service/AuthService.java`, `.../exception/BusinessException.java`, `.../security/RateLimiter.java`

- [ ] **Step 1: Unit tests** for register (new + existing email) and login (wrong password + rate-limited).
- [ ] **Step 2: Implement**
```java
@Service @RequiredArgsConstructor
public class AuthService {
  private final UserMapper users; private final RefreshTokenMapper refreshTokens;
  private final PasswordEncoder encoder; private final JwtService jwt; private final RateLimiter limiter;
  @Transactional
  public Long register(RegisterReq req){
    if (users.findByEmail(req.email())!=null) throw new BusinessException(1005,"邮箱已注册");
    User u = new User(); u.setEmail(req.email());
    u.setPasswordHash(encoder.encode(req.password())); u.setNickname(req.nickname());
    users.insert(u); return u.getId();
  }
  public TokenPair login(LoginReq req, String ip){
    limiter.checkOrThrow("login", ip+":"+req.email(), 5, Duration.ofMinutes(1));
    User u = users.findByEmail(req.email());
    if (u==null || !encoder.matches(req.password(), u.getPasswordHash())) throw new BusinessException(1002,"邮箱或密码错误");
    return mintTokens(u.getId());
  }
  /* mintTokens implemented in A-007 */
}
```

- [ ] **Step 3: `RateLimiter`** via `StringRedisTemplate` `INCR + EXPIRE 60`, throws `BusinessException(1006)`.

- [ ] **Step 4: Run unit tests (PASS), commit** `feat(auth): service register/login + rate limit`

---

## Task A-006: `JwtService`

**Files:** `backend/src/main/java/com/lifepulse/auth/security/JwtService.java`

- [ ] **Step 1: Tests** mint+parse, signature invalid, expired → null.
- [ ] **Step 2: Implement** using `Jwts.builder()` / `Jwts.parser().verifyWith(...)` with HS256 key from `lp.jwt.secret`.
- [ ] **Step 3: Run, commit** `feat(auth): jwt service`

---

## Task A-007: `AuthService.refresh` + `logout` + integrate into `mintTokens`

- [ ] **Step 1: Tests**: refresh rotates, replay returns 1401; logout revokes.
- [ ] **Step 2: Implement** `mintTokens` (insert hashed refresh), `refresh` (revoke old + mint new), `logout` (revoke by hash). Use `MessageDigest.getInstance("SHA-256")` for token hash.
- [ ] **Step 3: Run, commit** `feat(auth): refresh and logout`

---

## Task A-008: `AuthController` + envelope + global handler

**Files:** `backend/src/main/java/com/lifepulse/auth/web/AuthController.java`, `.../web/GlobalExceptionHandler.java`, `.../common/web/{ApiResponse,TraceIdFilter}.java`

- [ ] **Step 1: Slice test** for register (201 envelope `{code:0,data:{userId:N}}`).
- [ ] **Step 2: Implement controller** for 4 endpoints (each returning `ApiResponse.ok(payload)`; register uses `@ResponseStatus(HttpStatus.CREATED)`).
- [ ] **Step 3: Implement `ApiResponse<T>(int code,String message,T data,String traceId)` with static `ok(T)`.
- [ ] **Step 4: Implement `GlobalExceptionHandler`** with handlers for `BusinessException` (mapped HTTP status), `MethodArgumentNotValidException` → 1001, fallback → 1500.
- [ ] **Step 5: `TraceIdFilter`** — generate UUID, put into MDC + response header `X-Trace-Id`.
- [ ] **Step 6: Run, commit** `feat(auth): controller and envelope`

---

## Task A-009: Spring Security wiring + `JwtAuthFilter`

**Files:** `backend/src/main/java/com/lifepulse/security/{SecurityConfig,JwtAuthFilter,UserContext}.java`

- [ ] **Step 1: Test** unauthenticated GET `/api/v1/users/me` returns 401 envelope `{code:1002}`.
- [ ] **Step 2: Implement `SecurityConfig`** (CSRF off, stateless, permits `/api/v1/auth/**` and `/actuator/health`, all else authenticated, custom auth entry point returning JSON).
- [ ] **Step 3: Implement `JwtAuthFilter`** — extract `Authorization: Bearer ...`, parse with `JwtService`, set `UserContext` in `SecurityContextHolder`.
- [ ] **Step 4: Run, commit** `feat(security): jwt filter and stateless config`

---

## Task A-010: Auth slice tests

`@WebMvcTest` parameterized tests for each endpoint: missing fields → 1001; unknown email → 1002; 6th 60s login → 1006.

- [ ] **Step 1: Write tests, run, commit** `test(auth): slice tests`

---

## Task A-011: Auth flow integration test

`backend/src/test/java/com/lifepulse/auth/it/AuthFlowIT.java` (Testcontainers): register → login → refresh → replay returns 1401.

- [ ] **Step 1: Write, run `mvn -q -B -Dtest=AuthFlowIT test`, commit** `test(auth): e2e flow`

---

## Task F-A01: `stores/auth.ts`

**Files:** `frontend/src/stores/auth.ts`, `frontend/src/utils/storage.ts`

- [ ] **Step 1: Test** hydration from `localStorage` (`lp_access`, `lp_refresh`, `lp_user`).
- [ ] **Step 2: Implement** matching `04-frontend §5` schema; `setTokens` / `setUser` / `clear` write to localStorage.
- [ ] **Step 3: Run, commit** `feat(frontend): auth store`

---

## Task F-A02: `api/http.ts` interceptor + refresh queue

**Files:** `frontend/src/api/http.ts`

- [ ] **Step 1: Vitest** with `axios-mock-adapter`: 1002 → calls `/auth/refresh` once → retries original.
- [ ] **Step 2: Implement** request interceptor sets `Authorization`; response interceptor on `code:1002` does the refresh-queue (single-flight via Promise), on success retry with new token, on failure `auth.clear()` + `router.push('/login')`.
- [ ] **Step 3: Run, commit** `feat(frontend): http interceptor refresh queue`

---

## Task F-A03: `LoginView` + `RegisterView`

**Files:** `frontend/src/views/{LoginView,RegisterView}.vue`, `frontend/src/api/auth.ts`

- [ ] **Step 1: Component test** LoginView validates required fields and calls `auth.login`.
- [ ] **Step 2: Implement** Element Plus forms; on success `router.push(query.return || '/')`.
- [ ] **Step 3: Run, commit** `feat(frontend): auth views`

---

## Task F-A04: Wire `users/me` + router guard

**Files:** `frontend/src/api/user.ts`, `frontend/src/router/index.ts`

- [ ] **Step 1: Test** router guard: visit `/` without token → redirect to `/login?return=/`.
- [ ] **Step 2: Implement** `beforeEach` reading `useAuthStore().accessToken`.
- [ ] **Step 3: Run, commit** `feat(frontend): router guard`

---

## Task F-A05: Playwright e2e for login

**Files:** `frontend/e2e/login.spec.ts`, `frontend/playwright.config.ts`

- [ ] **Step 1: Test** registers via API, visits `/login`, fills form, asserts redirect home.
- [ ] **Step 2: Run** `pnpm exec playwright test`.
- [ ] **Step 3: Commit** `test(frontend): login e2e`

---
