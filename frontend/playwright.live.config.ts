import { defineConfig, devices } from '@playwright/test';
// Separate live qualification. Never silently skip required credentials or use the UI fixture.
export default defineConfig({
  testDir: './tests/live',
  globalSetup: './tests/live/setup.ts',
  workers: 1,
  retries: 0,
  reporter: 'list',
  timeout: 60_000,
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.CADENCE_LIVE_WEB_URL ?? 'http://localhost:3000',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
});
