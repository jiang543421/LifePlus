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
