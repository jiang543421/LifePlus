/**
 * nginx 配置文件静态断言（R-004 / CLAUDE.md §7.5）。
 *
 * <p>背景：dev E2E（vite only，无 nginx、无 backend）无法真正测 CSP / HSTS /
 * nosniff / Referrer-Policy 响应头。生产端 4 项头由 frontend/nginx.conf 的
 * `add_header ... always` 指令声明，由 docker compose nginx 在 prod 容器
 * 注入。本测试静态解析该文件，确保 4 项头不被未来手改移除。
 *
 * <p>对端到端可执行层（HTTP 实际响应）覆盖：见 `docs/QUICKSTART.md §3`
 * 生产烟测 `curl -I https://<host>`；本测试是配置层兜底，避免「代码改了
 * 但运维忘 reload」silently regress。
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
// src/__tests__/ → repo root → frontend/nginx.conf
const NGINX_CONF = resolve(__dirname, '..', '..', 'nginx.conf');

/** 从 nginx.conf 抠出 add_header Content-Security-Policy 那行的值。 */
function readNginxConf(): string {
  return readFileSync(NGINX_CONF, 'utf8');
}

describe('frontend/nginx.conf（R-004 CSP/CORS 生产收紧）', () => {
  const conf = readNginxConf();

  it('存在 nginx.conf 文件', () => {
    expect(conf.length).toBeGreaterThan(0);
  });

  it('Content-Security-Policy 含 default-src / script-src / style-src / connect-src / frame-ancestors', () => {
    // §7.5：style-src 必须含 'unsafe-inline'（EP 2.x 运行时插 <style>）；注释里要标消除窗口
    const cspLine = conf
      .split('\n')
      .find((line) => line.includes('Content-Security-Policy'));
    expect(cspLine, 'nginx.conf 必须配置 Content-Security-Policy add_header').toBeDefined();
    const directives = [
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self' 'unsafe-inline'",
      'connect-src',
      "frame-ancestors 'none'",
      'base-uri',
      'form-action',
    ];
    for (const directive of directives) {
      expect(
        cspLine!.includes(directive),
        `CSP 缺少关键指令 "${directive}"：${cspLine}`,
      ).toBe(true);
    }
    // unsafe-inline 的消除窗口必须留注释（CLAUDE.md §4.4 why-not-what）
    expect(
      conf.includes('unsafe-inline'),
      'CSP 注释必须明确说明 unsafe-inline 仅 EP 必需（v1.x 消除窗口）',
    ).toBe(true);
  });

  it('X-Content-Type-Options: nosniff 加 always', () => {
    const line = conf
      .split('\n')
      .find((l) => l.includes('X-Content-Type-Options'));
    expect(line, 'nginx.conf 必须加 X-Content-Type-Options').toBeDefined();
    expect(line).toContain('nosniff');
    expect(line, '必须带 always（4xx/5xx 也下发）').toContain('always');
  });

  it('Referrer-Policy: strict-origin-when-cross-origin 加 always', () => {
    const line = conf
      .split('\n')
      .find((l) => l.includes('Referrer-Policy'));
    expect(line, 'nginx.conf 必须加 Referrer-Policy').toBeDefined();
    expect(line).toContain('strict-origin-when-cross-origin');
    expect(line).toContain('always');
  });

  it('Strict-Transport-Security（HSTS）含 max-age 与 includeSubDomains', () => {
    const line = conf
      .split('\n')
      .find((l) => l.includes('Strict-Transport-Security'));
    expect(line, 'nginx.conf 必须加 HSTS').toBeDefined();
    // max-age ≥ 1 年（CLAUDE.md §7.5）
    expect(line).toMatch(/max-age=(31536000|63072000)/);
    expect(line).toContain('includeSubDomains');
    expect(line).toContain('always');
  });

  it('所有安全 add_header 都用 always 关键字（即便 4xx/5xx 也下发）', () => {
    // 防御未来误删 always 导致错误页裸奔
    const securityHeaders = [
      'Content-Security-Policy',
      'X-Content-Type-Options',
      'Referrer-Policy',
      'Strict-Transport-Security',
    ];
    for (const header of securityHeaders) {
      const line = conf
        .split('\n')
        .find((l) => l.includes(header) && l.includes('add_header'));
      expect(line, `${header} 行必须包含 add_header`).toBeDefined();
      expect(
        line!.endsWith('always;') || line!.endsWith('always'),
        `${header} 必须以 always 结尾：${line}`,
      ).toBe(true);
    }
  });

  it('/api/ 反代保留（确保前端可走 nginx 调后端，不绕过 R-004 收紧）', () => {
    expect(conf).toContain('location /api/');
    expect(conf).toContain('proxy_pass http://backend:8080');
  });
});
