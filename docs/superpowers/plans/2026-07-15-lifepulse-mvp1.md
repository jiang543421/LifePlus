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

---

## 瀛愭枃浠剁储寮昤n
| 瀛愭枃浠?| 绔犺妭鏍囩 | 琛屽彿 | 璇存槑 |
|---|---|---|---|
| `2026-07-15-lifepulse-mvp1-01-phase-0-infra.md` | 01-phase-0-infra.md | 43..324 (282 琛? | Phase 0 Infrastructure: T-001..T-005（git + docker-compose + env + Spring Boot + Vue init） |
| `2026-07-15-lifepulse-mvp1-02-phase-1-auth.md` | 02-phase-1-auth.md | 325..562 (238 琛? | Phase 1 Authentication: A-001..A-011 backend + F-A01..A05 frontend |
| `2026-07-15-lifepulse-mvp1-03-phase-2-task.md` | 03-phase-2-task.md | 563..662 (100 琛? | Phase 2 Task: T-T01..T05 + F-T01..T05 |
| `2026-07-15-lifepulse-mvp1-04-phase-3-plan.md` | 04-phase-3-plan.md | 663..746 (84 琛? | Phase 3 Plan: T-P01..P05 + F-P01..P05 |
| `2026-07-15-lifepulse-mvp1-05-phase-3.1-linkage.md` | 05-phase-3.1-linkage.md | 747..795 (49 琛? | Phase 3.1 Plan x Task linkage（F-H03 补完） |
| `2026-07-15-lifepulse-mvp1-06-phase-4-home.md` | 06-phase-4-home.md | 796..831 (36 琛? | Phase 4 Home dashboard: F-H01..H04 |
| `2026-07-15-lifepulse-mvp1-07-phase-5-polish.md` | 07-phase-5-polish.md | 832..899 (68 琛? | Phase 5 Polish & Release: R-001..R-006 + Self-Review + Handoff |

> 鍏?7 涓瓙鏂囦欢 + 鏈?INDEX锛?preambleEnd 琛?preamble锛?= 婧愭枃浠?899 琛?1:1 瀹屾暣瑕嗙洊銆俙n
## 鎷嗗垎瑙勫垯锛圕LAUDE.md 搂3 + 鐢ㄦ埛纭害鏉燂級

- 姣忓瓙鏂囦欢 <=300 琛宍n- 鎸夊師绔犺妭椤哄簭锛屽崟鏂囦欢鍐呬繚鎸佸師椤哄簭
- INDEX 淇濈暀鍘?preamble + 瀛愭枃浠?TOC
- 涓嶄慨鏀逛换浣曟鏂囷紱鍙寜琛屽垏鐗嘸n
