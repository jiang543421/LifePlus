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
