# LifePulse MVP1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Spec reference (read once per Phase only):** [`docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md`](../../specs/2026-07-15-lifepulse-mvp1-design.md) (索引) + `docs/specs/01-architecture..05-nfr-testing.md`.
>
> **Encoding-time spec loader rule (strict):** during execution, load only the sub-spec for the current task family (e.g. Phase 2 → 02-database + 03-api-auth); never load 02/03/04 all together.

**Goal:** Ship LifePulse MVP1 — a multi-user Web app: email+password auth, task TODO list with optional plan association, calendar plan events, plus a Home dashboard with 4 placeholder cards.

**Architecture:** Single monorepo. Spring Boot 3 (Java 21) backend with MyBatis-Plus + Flyway; Vue 3 + TS frontend bundled by Vite, served by Nginx. JWT HS256 with refresh-token rotation. MySQL 8 + Redis. Docker Compose one-shot up.

**Tech Stack:**
- Backend: Spring Boot 3.3+, Java 21, Maven, MyBatis-Plus 3.5+, Spring Security 6, JJWT 0.12+, BCrypt (Spring built-in), Flyway 10+, MySQL 8.0, Spring Data Redis, Lombok, Jakarta Validation
- Frontend: Vue 3.4+, TypeScript 5+ (strict), Vite 5+, Pinia 2+, Vue Router 4, Element Plus, Axios 1.7+, dayjs, @vueuse/core
- Test: JUnit 5, Mockito, @WebMvcTest, @SpringBootTest + Testcontainers (mysql:8 / redis:7), Vitest, @vue/test-utils, Playwright
- Tooling: Maven 3.9+, pnpm 9+, Docker 24+, docker compose v2

---

## Global Constraints

These constraints apply to every task. Don't restate them per step.

1. **Java**: OpenJDK 21, UTF-8 source. Backend root: `backend/`. Package root: `com.lifepulse`.
2. **Spring Boot**: 3.3.x with `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-data-redis`, `spring-boot-starter-actuator`. MyBatis-Plus 3.5.6.
3. **DB**: MySQL 8.0.21+ on port 3306 inside docker-compose (`db`). Schema `lifepulse`. Username `lp` / password `lp_dev_only` (DO NOT reuse in prod).
4. **Migrations**: Flyway, file pattern `backend/src/main/resources/db/migration/V<NUM>__<NAME>.sql`, table names prefixed `t_`. Each migration MUST include every index listed in sub-spec `02-database.md §3`.
5. **Logical delete**: every entity has `deleted TINYINT NOT NULL DEFAULT 0`, MyBatis-Plus `@TableLogic` annotation; never hard-delete.
6. **Time**: DTO `OffsetDateTime` (Asia/Shanghai); DB column `DATETIME`. Frontend formats with dayjs TZ `Asia/Shanghai`.
7. **Code style** (project `~/.claude/rules/ecc/common/coding-style.md`): immutable patterns, no mutation; names camelCase (vars/funs) / PascalCase (types/components); functions < 50 lines; files < 800 lines; magic numbers → named constants.
8. **JWT**: HS256, secret pulled from `application.yml` (`lp.jwt.secret`, 32+ bytes base64-encoded; sample `dev-only-secret-replace-me-32+bytes-xxx`); payloads `sub=userId, iat, exp, typ`.
9. **Refresh tokens**: stored as `SHA-256(token)` hex digest in `t_refresh_token.token_hash`. Rotation on each refresh: old `revoked_at = NOW()`, new token issued.
10. **Login rate limit**: 5 attempts per minute per (ip, email). Implemented via Redis `INCR` with 60s TTL on key `lp:rl:login:<ip>:<email>`. Returns code 1006 on overflow.
11. **Response envelope** (per `coding-style.md` patterns): `{code, message, data, traceId}`. Codes listed in `03-api-auth.md §3`.
12. **Frontend base URL**: dev → `http://localhost:8080/api/v1`; prod via `import.meta.env.VITE_API_BASE`.
13. **Tests**: backend `mvn verify` must pass with JaCoCo line coverage ≥ 80% in `com.lifepulse.**`. Frontend `pnpm run lint && pnpm run test` must pass.
14. **Git**: messages follow `<type>: <subject>` (feat/fix/refactor/docs/test/chore). Don't auto-commit unless asked. CI is not in MVP1 scope.
15. **Files referencing auth contracts**: any task touching Task/Plan on the backend must read sub-spec `docs/specs/03-api-auth.md`; frontend Task/Plan tasks read `docs/specs/04-frontend.md` once at start.

---

# Phase 0 — Infrastructure

## Task T-001: Initialize git + Monorepo skeleton

**Files:**
- Create: `backend/pom.xml`, `frontend/package.json`, `.gitignore`, `README.md`, empty `docs/`

- [ ] **Step 1: `git init` in repo root**
```bash
cd C:/Users/jxw/Desktop/ai-coding-projects/LifePulse
git init -b main
git config user.email "lp@local" && git config user.name "lp"
```

- [ ] **Step 2: Create the Monorepo skeleton**
```bash
mkdir -p backend/src/main/java/com/lifepulse backend/src/main/resources/db/migration backend/src/test/java/com/lifepulse frontend/src
```

- [ ] **Step 3: Create `.gitignore`**
Write `.gitignore` with:
```
target/
build/
node_modules/
dist/
.idea/
.vscode/
*.iml
.env
.env.local
*.log
.DS_Store
```

- [ ] **Step 4: Initial commit**
```bash
git add .gitignore backend/ frontend/
git commit -m "chore: scaffold monorepo skeleton"
```
Expected: `main` branch with seed commit.

---

## Task T-002: docker-compose for MySQL/Redis/backend/nginx

**Files:**
- Create: `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`

- [ ] **Step 1: `backend/Dockerfile` (multi-stage)**
```dockerfile
# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/lifepulse-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

- [ ] **Step 2: `backend/pom.xml`** (final in T-004; here write minimal stub with `<groupId>com.lifepulse</groupId>`)

- [ ] **Step 3: `frontend/Dockerfile`**
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

- [ ] **Step 4: `frontend/nginx.conf`**
```nginx
server {
  listen 80;
  server_name _;
  root /usr/share/nginx/html;
  index index.html;
  location / {
    try_files $uri $uri/ /index.html;
  }
  location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Trace-Id $request_id;
  }
}
```

- [ ] **Step 5: `docker-compose.yml`**
```yaml
services:
  db:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: lifepulse
      MYSQL_USER: lp
      MYSQL_PASSWORD: lp_dev_only
      MYSQL_ROOT_PASSWORD: lp_root_only
    ports: ["3306:3306"]
    volumes: ["dbdata:/var/lib/mysql"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-uroot", "-plp_root_only"]
      interval: 5s
      timeout: 3s
      retries: 20
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20
  backend:
    build: ./backend
    depends_on:
      db:    { condition: service_healthy }
      redis: { condition: service_healthy }
    environment:
      LP_DB_URL:    jdbc:mysql://db:3306/lifepulse?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
      LP_DB_USER:   lp
      LP_DB_PASS:   lp_dev_only
      LP_REDIS_URL: redis://redis:6379
      LP_JWT_SECRET: dev-only-secret-replace-me-32bytes-xxx
    ports: ["8080:8080"]
  frontend:
    build: ./frontend
    depends_on: [backend]
    ports: ["80:80"]
volumes:
  dbdata: {}
```

- [ ] **Step 6: Commit**
```bash
git add docker-compose.yml backend/Dockerfile frontend/Dockerfile frontend/nginx.conf
git commit -m "chore: add docker compose stack"
```

---

## Task T-003: `.env.example`, `README.md`, contribution notes

**Files:** `.env.example`, `README.md`, `CONTRIBUTING.md`

- [ ] **Step 1: `.env.example`** — capture every secret key listed in `01-architecture §1.4`.
- [ ] **Step 2: `README.md`** — project intro, prerequisites (JDK 21, Node 20+, Docker), quick start (`docker compose up -d`), URLs, default seed user.
- [ ] **Step 3: `CONTRIBUTING.md`** — branch naming, commit message format, TDD mandate.
- [ ] **Step 4: Commit** `git commit -m "docs: project hygiene files"`

---

## Task T-004: Spring Boot init (`backend/`)

**Files:** `backend/pom.xml`, `backend/src/main/java/com/lifepulse/LifePulseApplication.java`, `backend/src/main/resources/application.yml`, `backend/src/test/java/com/lifepulse/SmokeTest.java`

> **Sub-spec to load:** `docs/specs/01-architecture.md` and only the §7 stack section of `docs/specs/03-api-auth.md`.

- [ ] **Step 1: Write failing smoke test**
```java
package com.lifepulse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class SmokeTest {
  @Test void contextLoads() {}
}
```

- [ ] **Step 2: Run it (should FAIL)**
```bash
cd backend && mvn -q -B test 2>&1 | tail -5
```
Expected: compile error.

- [ ] **Step 3: Write `pom.xml`** with Spring Boot 3.3.5 parent, dependencies `web`, `validation`, `security`, `data-redis`, `actuator`, `mysql-connector-j` runtime, `mybatis-plus-spring-boot3-starter 3.5.6`, `flyway-mysql 10`, `jjwt-api/impl/jackson 0.12.6`, `lombok` provided; test scope `spring-boot-starter-test`, `spring-security-test`, `testcontainers-{junit-jupiter,mysql,redis}`.

- [ ] **Step 4: Write `LifePulseApplication.java`**
```java
@SpringBootApplication
public class LifePulseApplication { public static void main(String[] a){ SpringApplication.run(LifePulseApplication.class,a);} }
```

- [ ] **Step 5: Write `application.yml`** (paste this exactly):
```yaml
server:
  port: 8080
  forward-headers-strategy: native
spring:
  application: { name: lifepulse }
  datasource:
    url: ${LP_DB_URL:jdbc:mysql://localhost:3306/lifepulse?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai}
    username: ${LP_DB_USER:lp}
    password: ${LP_DB_PASS:lp_dev_only}
  flyway: { enabled: true, baseline-on-migrate: true, locations: classpath:db/migration }
  data:
    redis:
      url: ${LP_REDIS_URL:redis://localhost:6379}
      timeout: 2s
mybatis-plus:
  global-config.db-config.logic-delete-field: deleted
  global-config.db-config.logic-delete-value: 1
  global-config.db-config.logic-not-delete-value: 0
lp:
  jwt:
    secret: ${LP_JWT_SECRET:dev-only-secret-replace-me-32bytes-xxx}
    access-ttl: PT1H
    refresh-ttl: P7D
logging:
  pattern.console: "%d{HH:mm:ss.SSS} [%thread] %-5level %X{traceId:-} %logger{36} - %msg%n"
```

- [ ] **Step 6: Run smoke test (PASS)**
```bash
cd backend && mvn -q -B test
```

- [ ] **Step 7: Commit** `git commit -m "feat(backend): spring boot 3 boot"`

---

## Task T-005: Vue 3 + Vite + TS init (`frontend/`)

**Files:** `frontend/package.json`, `frontend/tsconfig.json`, `frontend/vite.config.ts`, `frontend/index.html`, `frontend/src/main.ts`, `frontend/src/App.vue`, `frontend/src/router/index.ts`, `frontend/src/views/HelloView.vue`, `frontend/src/__tests__/HelloView.spec.ts`, `frontend/vitest.config.ts`

> **Sub-spec to load:** `docs/specs/04-frontend.md` (only §1, §2, §5 store schema; not §3 interceptors yet).

- [ ] **Step 1: Write failing component test**
```ts
import { mount } from '@vue/test-utils'
import HelloView from '@/views/HelloView.vue'
import { describe, it, expect } from 'vitest'
describe('HelloView', () => {
  it('shows hello text', () => {
    const wrapper = mount(HelloView)
    expect(wrapper.text()).toContain('Hello')
  })
})
```

- [ ] **Step 2: Run it (FAIL)** `cd frontend && pnpm vitest run`

- [ ] **Step 3: Write `package.json`** with deps `vue@^3.4`, `vue-router@^4.4`, `pinia@^2.2`, `axios@^1.7`, `element-plus@^2.8`, `dayjs@^1.11`, `@vueuse/core@^11`; devDeps `vite@^5.4`, `@vitejs/plugin-vue@^5.1`, `typescript@^5.5`, `vue-tsc@^2.1`, `vitest@^2.0`, `@vue/test-utils@^2.4`, `@types/node@^22`.

- [ ] **Step 4: Configs**: `tsconfig.json` (strict, paths `@/*` → `src/*`, types `["vitest/globals"]`); `vite.config.ts` (resolve `@`, port 5173, proxy `/api` → `http://localhost:8080`); `vitest.config.ts` (jsdom env, alias `@`).

- [ ] **Step 5: Scaffolds**
```ts
// main.ts
import { createApp } from 'vue'; import { createPinia } from 'pinia'; import App from './App.vue'; import router from './router'
createApp(App).use(createPinia()).use(router).mount('#app')
```
```ts
// router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
export default createRouter({ history: createWebHistory(), routes:[{path:'/',name:'home',component:()=>import('@/views/HelloView.vue')}] })
```
`App.vue`: `<router-view/>`. `HelloView.vue`: `<h1>Hello LifePulse</h1>`.

- [ ] **Step 6: Install + run test (PASS)**
```bash
cd frontend && pnpm install && pnpm test
```

- [ ] **Step 7: Commit** `git commit -m "feat(frontend): vue 3 + vite + ts bootstrap"`

---

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

# Phase 2 — Task module

> **Sub-specs to load at start of Phase 2:** `02-database §2.2`, `03-api-auth §5.3`, `04-frontend` § for TaskListView / stores/task / api/task.

## Task T-T01: Flyway V2 — `t_task`

- [ ] **Step 1: Write `V2__init_task.sql`** with columns + `idx_user_status_due` + `idx_user_plan` per `02-database §3`.
- [ ] **Step 2: `mvn -q -B -DskipTests flyway:info`; commit** `feat(db): t_task`

---

## Task T-T02: `Task` entity + `TaskMapper`

- [ ] **Step 1: IT** insert + find by id + page by user + soft delete.
- [ ] **Step 2: Implement** `Task` with `TaskStatus` / `TaskPriority` enums; mapper methods `pageByUser`, `findByUserAndId`, `updateStatus`, `pageByPlan`.
- [ ] **Step 3: Run, commit** `feat(task): task entity and mapper`

---

## Task T-T03: `TaskService` CRUD + status + ownership check

- [ ] **Step 1: Unit tests** create sets userId, update rejects cross-user (1003), byPlan rejects other user, softDelete sets flag.
- [ ] **Step 2: Implement** reading userId from `UserContext.current()`; throw `BusinessException(1003)` on cross-user.
- [ ] **Step 3: Run, commit** `feat(task): task service`

---

## Task T-T04: `TaskController` 7 endpoints

**Files:** `backend/src/main/java/com/lifepulse/task/web/TaskController.java`, `.../dto/{TaskCreateReq,TaskUpdateReq,TaskStatusReq,TaskView}.java`, `.../common/web/PageResponse.java`

- [ ] **Step 1: Slice tests** for each endpoint (validation + auth + cross-user).
- [ ] **Step 2: Implement**
```java
@RestController @RequestMapping("/api/v1/tasks") @RequiredArgsConstructor
public class TaskController {
  private final TaskService svc;
  @GetMapping public ApiResponse<PageResponse<TaskView>> list(@RequestParam Map<String,String> q){...}
  @GetMapping("/{id}") public ApiResponse<TaskView> get(@PathVariable long id){...}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<Map<String,Long>> create(@Valid @RequestBody TaskCreateReq r){...}
  @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody TaskUpdateReq r){...}
  @PatchMapping("/{id}/status") public ApiResponse<Void> patchStatus(@PathVariable long id, @Valid @RequestBody TaskStatusReq r){...}
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id){...}
  @GetMapping("/by-plan/{planId}") public ApiResponse<List<TaskView>> byPlan(@PathVariable long planId){...}
}
```
- [ ] **Step 3: Run, commit** `feat(task): task controller`

---

## Task T-T05: Task integration test

`backend/src/test/java/com/lifepulse/task/it/TaskIT.java` (Testcontainers): register/login → create/list → patch status → soft delete → cross-user 1003.

- [ ] **Step 1: Test, run, commit** `test(task): e2e`

---

## Task F-T01: `stores/task.ts`

- [ ] **Step 1: Test** filter state syncs, paginated list cached.
- [ ] **Step 2: Implement** matching `04-frontend §5` schema.
- [ ] **Step 3: Run, commit** `feat(frontend): task store`

---

## Task F-T02: `api/task.ts`

- [ ] **Step 1: Test** with `axios-mock-adapter` for CRUD + status patch + by-plan.
- [ ] **Step 2: Implement** thin wrappers returning `response.data`.
- [ ] **Step 3: Run, commit** `feat(frontend): task api`

---

## Task F-T03: `TaskListView`

**Files:** `frontend/src/views/TaskListView.vue`, `frontend/src/components/{TaskItem,TaskFilters}.vue`

- [ ] **Step 1: Component tests** for TaskFilters (`update:filters`), TaskItem (renders title/status).
- [ ] **Step 2: Implement** TaskListView (filters + el-pagination).
- [ ] **Step 3: Run, commit** `feat(frontend): task list view`

---

## Task F-T04: `TaskDetailView`

- [ ] **Step 1: Test** edit form pre-fills, saves via PATCH.
- [ ] **Step 2: Implement** view/edit/toggle status/delete.
- [ ] **Step 3: Run, commit** `feat(frontend): task detail view`

---

## Task F-T05: Playwright e2e for task

`frontend/e2e/task.spec.ts`: register → create → list shows → toggle done → list removes from TODO.

- [ ] **Step 1: Write, run, commit** `test(frontend): task e2e`

---

# Phase 3 — Plan module

> **Sub-specs to load at start of Phase 3:** `02-database §2.3`, `03-api-auth §5.4`, `04-frontend` § for plan views.
>
> **状态：✅ 已完成（2026-07-17，branch `feat/phase3-plan`，7 个 commit）。**
> 实际落地范围覆盖 T-P01..T-P05 + F-P01..F-P05；**F-H03 链接（本计划下的任务）** 已在 Phase 3.1 跟进（见下方 # Phase 3.1 — Plan × Task linkage）。

## Task T-P01: Flyway V3 — `t_plan`

- [x] **Step 1: V3** with columns + `idx_user_start` per `02-database §3`.
- [x] **Step 2: `flyway:info` confirm, commit** `feat(db): t_plan`（实现见 commit `d440490`）

---

## Task T-P02: `Plan` entity + `PlanMapper`

- [x] **Step 1: IT** insert, find by id, page by user+range (uses `idx_user_start`).
- [x] **Step 2: Implement** entity + mapper.
- [x] **Step 3: Run, commit** `feat(plan): plan entity and mapper`（实现见 commit `d440490`）

---

## Task T-P03: `PlanService` + cross-user check + range query

- [x] **Step 1: Unit tests** for range query, cross-user (1003), `endTime > startTime` (1001).
- [x] **Step 2: Implement**, commit `feat(plan): plan service`（实现见 commit `44cf8b1`）

---

## Task T-P04: `PlanController` 5 endpoints

- [x] **Step 1: Slice tests**, implement DTOs and controller matching `03-api-auth §5.4`.
- [x] **Step 2: Run, commit** `feat(plan): plan controller`（实现见 commit `573ab66`）

---

## Task T-P05: Plan integration test (incl. task link)

`PlanIT`: register → create plan → create task with `planId` → `GET /by-plan/{planId}` returns 1 task.

- [x] **Step 1: Test, run, commit** `test(plan): e2e with task link`（实现见 commit `573ab66` 内 `PlanFlowIT`）

---

## Task F-P01: `stores/plan.ts`

Same pattern as F-T01. Commit `feat(frontend): plan store`。
- [x] 实现见 commit `44733d7`。

---

## Task F-P02: `api/plan.ts`

- [x] **Step 1: Test, implement** wrapping endpoints, commit `feat(frontend): plan api`（实现见 commit `44733d7`）

---

## Task F-P03: `PlanCalendarView` (month view + event markers)

**Files:** `frontend/src/views/PlanCalendarView.vue`, `frontend/src/components/CalendarMonth.vue`, `frontend/src/utils/calendar.ts`

- [x] **Step 1: Test** `CalendarMonth` renders day grid + event dots.
- [x] **Step 2: Implement**: month grid via dayjs; click day → side panel list.
- [x] **Step 3: Run, commit** `feat(frontend): calendar month view`（实现见 commit `0f30b15`）

---

## Task F-P04: `PlanDetailView` + `EventDialog`

- [x] **Step 1: Test** dialog open/close + save.
- [x] **Step 2: Implement** event dialog (create/edit), detail read-only.
- [x] **Step 3: Run, commit** `feat(frontend): plan detail and dialog`（实现见 commit `0f30b15`）

---

## Task F-P05: Calendar e2e

`frontend/e2e/plan/plan-flow.spec.ts`：guard / empty / create / edit / delete / month switch / cross-user 1003 共 7 用例。`api-mock.ts` 增加 `setupPlanDefaults`（CRUD + from/to 范围查询）+ `mockPlanCrossUser`。

- [x] **Step 1: Write, run, commit** `test(frontend): plan e2e`（实现见 commit `5a2a86a`）
- 注：单测落地 50 个 Vitest 用例见 `b045d56`（calendar util + CalendarMonth + EventDialog + PlanCalendarView + PlanDetailView + plan store）。

---

# Phase 3.1 — Plan × Task linkage（F-H03 补完）

> **Sub-specs to load:** `03-api-auth §5.3`（Task API 复用，by-plan 端点）+ `04-frontend`（PlanDetailView 视图段）。
>
> **状态：✅ 已完成（2026-07-17，branch `feat/phase3-1-plan-tasks` 基于 `main` @ `d420c72`，8 个 commit）。**
> 后端端点 `GET /tasks/by-plan/{planId}` 已随 Phase 2 就绪（TaskMapper + TaskService + TaskController + IT 全覆盖），本次仅做前端集成 + 状态扩展 + E2E 补完。

## 目标

`PlanDetailView` 加载并展示本日程下的关联任务（PRD F-H03）。复用后端 `/tasks/by-plan/{planId}` 端点（user_id+plan_id 联合过滤 → 隐式跨用户隔离，返回空列表而非 1003）。

## Task T-31-A: `task` store 扩展 `fetchByPlan` action

- [x] **Step 1: Spec** `stores/task.spec.ts` 新增 `fetchByPlan` describe（4 case：成功 / ApiError / 通用错 / 成功重置错），commit `test(task): add fetchByPlan store spec`（commit `9caead6`）
- [x] **Step 2: Implement** state 加 `byPlanTasks / byPlanLoading / byPlanError`，action 复用 taskApi.byPlan；commit `feat(task): add fetchByPlan action and by-plan state`（commit `8452c75`）

## Task F-31-B: 新组件 `PlanTaskList.vue`

- [x] **Step 1: Spec** `components/__tests__/PlanTaskList.spec.ts` 覆盖 4 态 + 交互（点击 emit open / planId 变化重新拉取）；commit `test(plan): add PlanTaskList component spec`（commit `2928749`）
- [x] **Step 2: Implement** 纯展示组件，props `planId: number`，emit `open(id)`；commit `feat(plan): add PlanTaskList component for plan detail related tasks`（commit `abffd53`）

## Task F-31-C: `PlanDetailView` 嵌入 `PlanTaskList`

- [x] **Step 1: Spec** `views/__tests__/PlanDetailView.spec.ts` 新增 3 case（渲染区块 / 点击跳转 / 加载失败时不渲染）；commit `test(plan): add F-H03 related-tasks assertions to PlanDetailView spec`（commit `43da48e`）
- [x] **Step 2: Implement** article 内 `<PlanTaskList :plan-id="plan.id" @open="onTaskOpen" />`，`onTaskOpen` 路由到 `/tasks/${id}`；commit `feat(plan): embed PlanTaskList in PlanDetailView`（commit `16ceb8f`）

## Task F-31-D: E2E 补完

- [x] **Step 1: Spec** `e2e/plan/plan-flow.spec.ts` 新增 case（创建 plan → 创建 2 task planId 指向 → 详情页看 2 行），`Promise.all` 同时订阅 `waitForResponse` 与 `goto`；commit `test(e2e): add F-H03 related-tasks assertion to plan flow`（commit `20e14a9`）
- [x] **Step 2: Mock** `e2e/helpers/api-mock.ts` 加 `GET /tasks/by-plan/{planId}` 分支（用 `state.detail.values()` 反查，因为 `TaskListItem` 无 planId 字段）；commit `test(e2e): mock /tasks/by-plan endpoint in api-mock helper`（commit `1130e1c`）
- [x] **Step 3: Run all** `npx playwright test plan-flow.spec.ts` → 8/8 passed

## 测试覆盖

| 层 | 用例 | 备注 |
|---|---|---|
| store 单测 | `stores/task.spec.ts` 5 case | fetchByPlan 4 态 + 重置 |
| 组件单测 | `PlanTaskList.spec.ts` 6 case | 4 态 + 2 交互 |
| 视图单测 | `PlanDetailView.spec.ts` 3 case | 渲染区块 / 跳转 / 失败不渲染 |
| E2E | `plan-flow.spec.ts` 新增 1 case（总 8） | 详情页 2 行关联任务 |

## 影响面

- 后端零改动（`/tasks/by-plan/{planId}` 已在 Phase 2 完成）
- store 状态加 3 字段（`byPlanTasks / byPlanLoading / byPlanError`），与现有 `list / loading / error` 平行，互不污染
- 跨用户越权由后端 `user_id + plan_id` 联合过滤兜底，返回空列表而非 1003

---

# Phase 4 — Home dashboard

> **Sub-spec:** `04-frontend §4` only.

## Task F-H01: `HomeView` with 4 cards + 2 placeholders

- [ ] **Step 1: Test** ModuleCard emits click; PlaceholderCard toast on click.
- [ ] **Step 2: Implement** `grid-template-columns: repeat(3,1fr)`, 6 cards.
- [ ] **Step 3: Run, commit** `feat(frontend): home dashboard`

---

## Task F-H02: `TopBar` + `UserMenu`

- [ ] **Step 1: Test** UserMenu triggers `auth.clear()` + push `/login`.
- [ ] **Step 2: Implement** menu 我的资料 / 设置 / 退出登录.
- [ ] **Step 3: Run, commit** `feat(frontend): top bar and user menu`

---

## Task F-H03: Link task ↔ plan in PlanDetailView

Add "本计划下的任务" section using `api.task.byPlan(planId)`; create-task button pre-fills `planId`.

- [ ] **Step 1: Test** empty state + populated state; commit `feat(frontend): task list under plan`

---

## Task F-H04: Responsive breakpoints

Media queries `@media (max-width:1023px)` → 2 cols; `<767px` → 1 col + collapsed top bar.

- [ ] **Step 1: Lint passes; manual verify; commit** `feat(frontend): responsive layout`

---

# Phase 5 — Polish & Release

> **Sub-specs:** `01-architecture §1.4`, `05-nfr-testing §1-§5`.

## Task R-001: Performance pass

- [ ] **Step 1: Confirm** each backend list endpoint uses documented index via `EXPLAIN`.
- [ ] **Step 2: Frontend** `pnpm build && pnpm preview`; throttled devtools profile reads ≤ 2 s.
- [ ] **Step 3: Document** any findings in `docs/perf-notes.md`; commit `docs: perf notes`

---

## Task R-002: Security checklist re-run

`frontend/e2e/security.spec.ts`: 6th 60s attempt → 1006; refresh replay → 1401; cross-user → 1003.

- [ ] **Step 1: Add, run, commit** `test(frontend): security flows`

---

## Task R-003: Logging + traceId

`logback-spring.xml` JSON encoder with MDC.

- [ ] **Step 1: Verify** traceId flows request → response envelope → backend log; commit `feat(observability): traceId and json logging`

---

## Task R-004: Actuator + Compose healthcheck

- [ ] **Step 1: `GET /actuator/health` returns 200 `{status:UP}`**.
- [ ] **Step 2: Update `docker-compose.yml`** with `backend.healthcheck = curl http://localhost:8080/actuator/health`.
- [ ] **Step 3: Commit** `chore: actuator health and compose probe`

---

## Task R-005: Full Compose smoke

- [ ] **Step 1:** `docker compose down -v && docker compose up -d --build`
- [ ] **Step 2:** curl `/api/v1/auth/login` with seed account; capture results.
- [ ] **Step 3:** Write `docs/smoke-<date>.md`; commit `docs: smoke run record`

---

## Task R-006: README + seed

- [ ] **Step 1: README** quickstart + URLs + env vars + seed account.
- [ ] **Step 2: V4** Flyway `V4__seed_user.sql` (BCrypt-hashed; document hash source).
- [ ] **Step 3: Commit** `docs+chore: seed user and readme`

---

## Self-Review (delete before merging)

- [ ] Every spec requirement traceable: see spec index §3-§7 ↔ each task.
- [ ] No "TBD"/"TODO"/"implement later" outside this review section.
- [ ] DTO names in T-T04/T-P04 match `03-api-auth §5.3 & §5.4` exactly.
- [ ] Task entity singular `Task` + `task.status` enum used consistently.
- [ ] All commits conventional and concrete.

## Execution Handoff

After implementation: a reviewer must ensure
1. `docker compose up -d --build` succeeds clean.
2. `mvn verify` passes with JaCoCo ≥ 80%.
3. `pnpm run lint && pnpm run test` passes.
4. All Playwright e2e green.
5. README quickstart reproducible by a stranger.
