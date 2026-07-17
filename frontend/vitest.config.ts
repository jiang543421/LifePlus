import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    test: {
        environment: 'jsdom',
        globals: true,
        include: ['src/**/*.{test,spec}.ts'],
        // E2E（Playwright）文件用 @playwright/test，不走 vitest；CLAUDE.md §3 把
        // E2E 目录放在 src/e2e/，这里显式排除避免 vitest 误扫。
        exclude: ['src/e2e/**', 'node_modules/**'],
        passWithNoTests: true,
    },
});
