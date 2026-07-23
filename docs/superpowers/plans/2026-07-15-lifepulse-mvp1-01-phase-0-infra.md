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
