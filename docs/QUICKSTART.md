# LifePulse 快速上手

> 本文档面向**自部署**用户：clone → 配环境变量 → 启动。开发流程见
> [CONTRIBUTING.md](../CONTRIBUTING.md)；架构与设计见 [docs/specs/01-architecture.md](specs/01-architecture.md)。

---

## 1. 三种启动模式

| 模式 | 入口 | 适用场景 |
|---|---|---|
| Docker Compose（推荐） | `docker compose up -d --build` | 自部署、CI 烟测 |
| 后端 + 前端分跑（dev） | `mvn spring-boot:run` + `pnpm dev` | 日常开发 |
| 单测/集成测试 | `mvn verify` / `pnpm test` | 回归 |

---

## 2. 环境变量清单（生产部署必看）

所有变量通过 `.env` 或部署平台的 secret 注入；默认值仅适用于 **dev 单机**。
**生产环境**以下变量**必须显式覆盖**：

### 2.1 后端（`backend`）

| 变量 | 默认值（dev） | 生产要求 | 用途 |
|---|---|---|---|
| `LP_DB_URL` | `jdbc:mysql://localhost:3306/lifepulse?...` | 指向托管 MySQL 8.0+（utf8mb4） | JDBC URL |
| `LP_DB_USER` | `lp` | 业务专用账号（非 root） | DB 用户名 |
| `LP_DB_PASS` | `lp_dev_only` | **必改**；≥ 16 位随机串 | DB 密码（CLAUDE.md §7.1） |
| `LP_REDIS_URL` | `redis://localhost:6379` | `redis://:密码@host:port` | Redis（限流 + refresh 哈希） |
| `LP_JWT_SECRET` | `dev-only-secret-replace-me-32bytes-xxx` | **必改**；≥ 32 字节随机串，禁含 `replace-me` | HS256 签名密钥；启动 fail-fast 校验占位符 |
| `LP_CORS_ORIGINS` | `http://localhost` | **必改**；逗号分隔的具体 origin（如 `https://app.lifepulse.com`） | CORS 白名单（issue R-004）；禁 `*` |

> 启动 fail-fast：未注入 `LP_JWT_SECRET` 真实值 / `LP_CORS_ORIGINS` 留空 / 含通配符 `*` → 进程退出码非 0。

### 2.2 前端（`frontend`，Dockerfile nginx 阶段）

| 变量 | 默认值 | 生产要求 | 用途 |
|---|---|---|---|
| `LP_API_ORIGIN` | （空） | **必填**；后端 API 的具体 origin（含 scheme） | nginx.conf `connect-src` CSP 白名单（issue R-004） |

> 例：`LP_API_ORIGIN=https://api.lifepulse.com` → CSP 允许前端 XHR 打到该 origin。

### 2.3 完整 `.env` 模板

```env
# === backend ===
LP_DB_URL=jdbc:mysql://db:3306/lifepulse?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Shanghai
LP_DB_USER=lifepulse_app
LP_DB_PASS=<生成：openssl rand -hex 24>
LP_REDIS_URL=redis://:redis_pass@redis:6379
LP_JWT_SECRET=<生成：openssl rand -hex 32>
LP_CORS_ORIGINS=https://app.lifepulse.com

# === frontend ===
LP_API_ORIGIN=https://api.lifepulse.com
```

---

## 3. Docker Compose 部署

```bash
# 1. 准备 .env（参考 §2.3）
cp .env.example .env
# 编辑 .env，填入生产密钥

# 2. 启动（构建镜像 + 后台运行）
docker compose up -d --build

# 3. 验证
curl http://localhost/actuator/health    # 期望 {"status":"UP"}
curl -I http://localhost/                # 期望 CSP / X-Content-Type-Options / Referrer-Policy 头
```

> 容器启动顺序：mysql → redis（健康检查通过）→ backend（Flyway 迁移）→ frontend（nginx）。

---

## 4. 开发模式

```bash
# 后端（dev profile：标记文件；行为与默认 profile 一致，仅用于本地覆盖属性）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 前端
cd frontend
pnpm install
pnpm dev   # http://localhost:5173
```

dev 不再自动注入 seed 账号；如需 demo 账号走 [README.md §4](../README.md#4-seed-账号-r006-v122-重构) 的 UserIT.@BeforeEach 路径或显式 `LP_FLYWAY_LOCATIONS`。

---

## 5. 安全 checklist（生产前必过）

- [ ] `LP_JWT_SECRET` ≥ 32 字节随机串，未含 `replace-me`
- [ ] `LP_CORS_ORIGINS` 列出全部前端域名，无 `*`
- [ ] `LP_DB_PASS` ≥ 16 位，未复用 dev 占位符
- [ ] MySQL / Redis 已设强密码
- [ ] HTTPS 已配置（Let's Encrypt / 反代）
- [ ] nginx `Strict-Transport-Security` 头已生效（curl -I 验证）
- [ ] 后端日志不打印 password / token / 完整 email（CLAUDE.md §7.3）