import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './tests/browser',
  fullyParallel: true,
  workers: 2,
  retries: 0,
  reporter: [['list'], ['json', { outputFile: 'test-results/browser.json' }]],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:4174',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: [
    {
      command: 'pnpm fixture',
      url: 'http://127.0.0.1:4174',
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: 'node scripts/serve-export.mjs',
      url: 'http://127.0.0.1:4173',
      reuseExistingServer: false,
      timeout: 30_000,
    },
  ],
});
