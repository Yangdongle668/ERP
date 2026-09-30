import { defineConfig, devices } from '@playwright/test'

/**
 * 前端端到端测试（Playwright）。
 * 先启动后端（H2 内存库，无需 MySQL）：ERP_PROFILE=h2 java -jar ../erp-server/target/erp-server.jar
 * 再执行：npm run e2e（前端由 vite 自动启动，/api 代理到 8080）。
 * 全新的 H2 库初始管理员密码 admin123 需要先改密，global-setup 会完成；重复运行使用同一个改后的密码。
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  globalSetup: './e2e/global-setup.ts',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'zh-CN'
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], launchOptions: { executablePath: process.env.E2E_CHROMIUM } } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: 'npm run dev -- --host 127.0.0.1', url: 'http://localhost:5173', reuseExistingServer: true, timeout: 60_000 }
})
