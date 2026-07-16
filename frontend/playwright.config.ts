import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E 配置（CLAUDE.md §6.1）。
 *
 * <p>Phase 1（Auth）覆盖：登录流、跨用户越权 1003、refresh 重放 1401、登录限流 1006。
 * Phase 2/3 的任务流/日历流留到对应阶段补 spec。
 *
 * <p>后端策略：`page.route()` 全量 mock `/api/v1/*`（api-mock.ts），
 * 真实后端契约由 `backend/src/test/.../*IT.java`（Testcontainers）覆盖。
 * `webServer` 仅启动 vite dev（5173），不需要 MySQL/Redis。
 */
export default defineConfig({
    testDir: './src/e2e',
    timeout: 30_000,
    expect: { timeout: 5_000 },
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: 0, // MVP1：不重试，失败即暴露
    workers: process.env.CI ? 1 : undefined,
    reporter: process.env.CI ? 'list' : 'list',
    use: {
        baseURL: 'http://localhost:5173',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
        locale: 'zh-CN',
    },
    projects: [
        { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    ],
    webServer: {
        command: 'pnpm dev',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
    },
});