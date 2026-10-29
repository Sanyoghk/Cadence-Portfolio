import { test, expect } from '@playwright/test';
import { clerk, setupClerkTestingToken } from '@clerk/testing/playwright';
test('real Clerk sign-in, scoped API and sign-out', async ({ page }) => {
  await setupClerkTestingToken({ page });
  await page.goto('/workspaces/');
  await expect(page).toHaveURL(/\/sign-in\//);
  await clerk.loaded({ page });
  await clerk.signIn({
    page,
    signInParams: {
      strategy: 'password',
      identifier: process.env.CADENCE_E2E_EMAIL!,
      password: process.env.CADENCE_E2E_PASSWORD!,
    },
  });
  await page.goto('/workspaces/');
  await expect(page.getByRole('heading', { name: 'Workspaces', exact: true })).toBeVisible();
  // Require the actual backend response, rather than accepting a rendered shell as authentication.
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: 'Workspace pagination' })).toBeVisible();
  await clerk.signOut({ page });
  await page.goto('/workspaces/');
  await expect(page).toHaveURL(/\/sign-in\//);
});
