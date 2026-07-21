# [基础设施] CSP & CORS 生产收紧

**描述**：MVP1 后端 CORS 只白名单 `http://localhost:5173`（dev），前端 Nginx 无 CSP 头。部署到非 localhost 时必须收紧到具体域名并加 CSP，否则 `connect-src`/`frame-ancestors`/`default-src` 都是开放的。

**Acceptance Criteria**：
- [x] `application.yml` 改为 `lp.cors.allowed-origins: ${LP_CORS_ORIGINS:http://localhost}`（prod 必须显式注入，禁 `*`）
- [x] `SecurityConfig` 改为读取属性而非硬编码；空值启动 fail-fast
- [x] `frontend/nginx.conf` 加 `add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self' ${LP_API_ORIGIN}; img-src 'self' data:; frame-ancestors 'none'"`（unsafe-inline 仅 Element Plus 必需，注释标明消除窗口）
- [x] 加 `Strict-Transport-Security`（HTTPS 部署时）/ `X-Content-Type-Options: nosniff` / `Referrer-Policy: strict-origin-when-cross-origin`
- [x] 新增 `WebCorsIT`（MockMvc 验证）：prod 风格的 origin 不回显 allow-origin
- [ ] Playwright `security.spec.ts`（E2E 留待 T18+ backlog）
- [x] docs/QUICKSTART.md 增加「生产环境变量清单」段

**Refs**：RELEASES/v1.0.0-mvp.md §5.1 / R-004

---

## Closeout（v1.2.3 收尾）

| AC | 状态 | 验证 |
|---|---|---|
| `application.yml` 属性化 `lp.cors.allowed-origins` | ✓ | `backend/src/main/resources/application.yml:38` |
| `SecurityConfig` 读 `CorsProperties` + fail-fast | ✓ | `backend/src/main/java/com/lifepulse/security/SecurityConfig.java:60-74` + `CorsProperties.validate()` |
| nginx CSP + HSTS / nosniff / Referrer-Policy | ✓ | `frontend/nginx.conf:7-21`（含 Element Plus `unsafe-inline` 消除窗口注释） |
| `WebCorsIT` 2 用例 | ✓ | `backend/src/test/java/com/lifepulse/security/WebCorsIT.java`（preflight allow + prod-style deny） |
| `CorsPropertiesValidationTest` 5 用例 | ✓ | `backend/src/test/java/com/lifepulse/security/CorsPropertiesValidationTest.java`（empty / wildcard / blank / non-http / valid） |
| `docs/QUICKSTART.md` §2.1 / §2.2 / §2.3 / §5 | ✓ | `LP_CORS_ORIGINS` + `LP_API_ORIGIN` 行已写入 |
| Playwright `security.spec.ts` | — | **T18+ backlog**：v1.2.3 不在范围；待前端 E2E helper 改造（参见 [[2026-07-19-el-select-popper]] 同模式 helper）落地后再补 |

**测试统计**：
- `CorsPropertiesValidationTest` 5/5 ✓
- `WebCorsIT` 2/2 ✓（Testcontainers MySQL）

**结论**：6/7 AC 在主分支已闭合（pre-staged），Playwright E2E 显式列入 backlog。下游消费方：HIGH-2/3 鉴权强化、日报模块（v1.2.3+）部署到非 localhost 时直接受益于 R-004 收紧。
