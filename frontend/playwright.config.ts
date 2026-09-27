import { defineConfig, devices } from '@playwright/test';

// Runs against the real Docker Compose stack (frontend + backend + Postgres + Redis).
// Not started automatically here: CI brings the stack up itself
// (see .github/workflows/ci.yml's e2e job) so these tests can also run against a stack a developer
// already has open locally, rather than each spec run managing its own compose lifecycle.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // shares real booking capacity against the same seeded slots across spec files
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
