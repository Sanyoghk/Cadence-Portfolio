import { test, expect, type Page, type Route } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
const a = '00000000-0000-0000-0000-000000000001';
const b = '00000000-0000-0000-0000-000000000002';
const c = '00000000-0000-0000-0000-000000000003';
const ws = (id = a, name = 'Studio') => ({
  id,
  name,
  industry: 'Design',
  status: 'ACTIVE',
  lockVersion: 0,
});
const brand = (workspaceId = a, name = 'North') => ({
  id: c,
  workspaceId,
  name,
  status: 'ACTIVE',
  lockVersion: 0,
  websiteUrl: null,
  instagramHandle: null,
  tiktokHandle: null,
});
const paged = (content: unknown[]) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
  first: true,
  last: true,
});
const json = (route: Route, value: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
async function setup(page: Page, workspaces = [ws()]) {
  await page.route('**/api/v1/**', (route) => {
    if (route.request().url().includes('/brands')) return json(route, paged([]));
    return json(route, paged(workspaces));
  });
}
test('onboarding create Workspace → Brand → versioned profile, no writable audit fields', async ({
  page,
}) => {
  const spaces: ReturnType<typeof ws>[] = [];
  const brands: ReturnType<typeof brand>[] = [];
  let creates = 0;
  await page.route('**/api/v1/**', async (route) => {
    expect(route.request().headers().authorization).toBe('Bearer isolated-ui-fixture-NOT-A-JWT');
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      if (route.request().url().endsWith('/workspaces')) {
        expect(Object.keys(body).sort()).toEqual(['industry', 'name']);
        spaces.push(ws(a, body.name));
        return json(route, spaces[0], 201);
      }
      creates++;
      expect(Object.keys(body).sort()).toEqual([
        'instagramHandle',
        'name',
        'tiktokHandle',
        'websiteUrl',
      ]);
      brands.push(brand(a, body.name));
      return json(route, brands[0], 201);
    }
    if (route.request().url().includes(`/brands/${c}`)) return json(route, brands[0]);
    return json(route, paged(route.request().url().includes('/brands') ? brands : spaces));
  });
  await page.goto('/');
  await expect(page.getByText('No Workspaces yet.', { exact: false })).toBeVisible();
  await page.getByText('Create a Workspace', { exact: true }).click();
  await page.getByLabel('Workspace name').fill('Studio');
  await page.getByLabel('Industry', { exact: true }).fill('Design');
  await page.getByRole('button', { name: 'Create Workspace', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your Brands' })).toBeVisible();
  await page.getByLabel('Brand name').fill('North');
  await page.getByRole('button', { name: 'Create Brand', exact: true }).dblclick();
  await page.getByRole('button', { name: 'Open North' }).click();
  await expect(page.getByRole('region', { name: 'Brand details' })).toContainText(
    'Your Brand profile is ready',
  );
  expect(creates).toBe(1);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({ path: '../var/prompt23-desktop.png', fullPage: true });
});
test('Workspace switch clears previous data and discards a late old response', async ({ page }) => {
  await setup(page, [ws(a, 'Studio A'), ws(b, 'Studio B')]);
  let release!: () => void;
  const delayed = new Promise<void>((resolve) => {
    release = resolve;
  });
  let entered!: () => void;
  const requested = new Promise<void>((resolve) => {
    entered = resolve;
  });
  await page.route(`**/workspaces/${a}/brands?**`, async (route) => {
    entered();
    await delayed;
    await json(route, paged([brand(a, 'PRIVATE A')]));
  });
  await page.route(`**/workspaces/${b}/brands?**`, (route) =>
    json(route, paged([brand(b, 'Brand B')])),
  );
  await page.goto('/');
  await page.getByRole('button', { name: 'Studio A', exact: true }).click();
  await requested;
  await page.getByLabel('Brand name').fill('unsaved A');
  await page.getByRole('button', { name: 'Studio B', exact: true }).click();
  release();
  await expect(page.getByRole('button', { name: 'Open Brand B' })).toBeVisible();
  await expect(page.getByText('PRIVATE A')).toHaveCount(0);
  await expect(page.getByLabel('Brand name')).toHaveValue('');
});
test('prior selected Brand profile disappears immediately when switching Workspace', async ({
  page,
}) => {
  await setup(page, [ws(a, 'Studio A'), ws(b, 'Studio B')]);
  await page.route(`**/workspaces/${a}/brands?**`, (route) =>
    json(route, paged([brand(a, 'Private A')])),
  );
  await page.route(`**/brands/${c}`, (route) => json(route, brand(a, 'Secret profile A')));
  await page.goto('/');
  await page.getByRole('button', { name: 'Studio A', exact: true }).click();
  await page.getByRole('button', { name: 'Open Private A' }).click();
  await expect(page.getByRole('heading', { name: 'Secret profile A' })).toBeVisible();
  await page.getByRole('button', { name: 'Studio B', exact: true }).click();
  await expect(page.getByText('Secret profile A')).toHaveCount(0);
});
for (const status of [401, 403, 404, 409, 503])
  test(`controlled ${status} state without raw error disclosure`, async ({ page }) => {
    await page.route('**/api/v1/**', (route) =>
      json(
        route,
        { code: 'request_failed', detail: '<script>private-token-and-SQL</script>' },
        status,
      ),
    );
    await page.goto('/');
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText('private-token-and-SQL', { exact: false })).toHaveCount(0);
    if (status === 401)
      await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toHaveAttribute(
        'href',
        '/sign-in/',
      );
    if (status === 403) await expect(page.getByRole('alert')).toContainText('Access denied');
    if (status === 404)
      await expect(page.getByRole('alert')).toContainText('resource is unavailable');
  });
test('foreign successful response is rejected and user text remains text, never markup', async ({
  page,
}) => {
  await setup(page, [ws(a, '<img src=x onerror=alert(1)>')]);
  await page.route(`**/workspaces/${a}/brands?**`, (route) =>
    json(route, paged([brand(b, 'Foreign secret')])),
  );
  await page.goto('/');
  await page.getByRole('button', { name: '<img src=x onerror=alert(1)>', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('unexpected response');
  await expect(page.getByText('Foreign secret')).toHaveCount(0);
  await expect(page.locator('img[src="x"]')).toHaveCount(0);
});
test('loading state, keyboard skip link and mobile layout pass accessibility', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/?session=loading');
  await expect(page.getByRole('status')).toContainText('Loading secure');
  await expect(page.getByLabel('Create workspace')).toHaveCount(0);
  await setup(page);
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('#main-content')).toBeFocused();
  await page.getByRole('button', { name: 'Studio', exact: true }).click();
  await expect(page.getByLabel('Brand name')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({ path: '../var/prompt23-mobile.png', fullPage: true });
});
test('signed-out gate redirects to sign-in and performs no protected API request', async ({
  page,
}) => {
  let calls = 0;
  await page.route('**/api/v1/**', (route) => {
    calls++;
    return json(route, paged([]));
  });
  await page.goto('/?session=signed-out');
  await expect(page).toHaveURL(/\/sign-in\//);
  await expect(
    page.getByRole('heading', { name: 'Sign-in redirect target · isolated fixture' }),
  ).toBeVisible();
  expect(calls).toBe(0);
});
test('archived Workspace cannot expose a creation form', async ({ page }) => {
  await setup(page, [{ ...ws(), status: 'ARCHIVED' }]);
  await page.goto('/');
  await page.getByRole('button', { name: 'Studio', exact: false }).click();
  await expect(page.getByRole('heading', { name: 'Workspace archived' })).toBeVisible();
  await expect(page.getByLabel('Brand name')).toHaveCount(0);
});
test('uncertain create is not retried automatically', async ({ page }) => {
  await setup(page);
  let calls = 0;
  await page.route('**/api/v1/workspaces/*/brands', (route) => {
    calls++;
    return route.abort('failed');
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Studio', exact: true }).click();
  await page.getByLabel('Brand name').fill('North');
  await page.getByRole('button', { name: 'Create Brand', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('avoid duplicates');
  expect(calls).toBe(1);
});
test('production static shell starts, is accessible and fails closed without Clerk config', async ({
  page,
}) => {
  await page.goto('http://127.0.0.1:4173/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('CADENCE');
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.goto('http://127.0.0.1:4173/workspaces/');
  await expect(page.getByRole('heading', { name: 'Setup required' })).toBeVisible();
  await expect(page.getByLabel('Brand name')).toHaveCount(0);
  const response = await page.request.get('http://127.0.0.1:4173/tests/fixture/main.tsx');
  expect(response.status()).toBe(404);
});

test('real browser timeout is bounded and exposes no private transport error', async ({ page }) => {
  let release!: () => void;
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/v1/**', async (route) => {
    await held;
    await route.abort().catch(() => undefined);
  });
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('could not confirm', { timeout: 20_000 });
  release();
});

test('pagination revalidates the bounded next page without carrying old selection', async ({
  page,
}) => {
  await page.route('**/api/v1/**', (route) => {
    if (route.request().url().includes('/brands')) return json(route, paged([]));
    const next = new URL(route.request().url()).searchParams.get('page') === '1';
    return json(route, {
      content: [ws(next ? b : a, next ? 'Second page' : 'First page')],
      page: next ? 1 : 0,
      size: 20,
      totalPages: 2,
      totalElements: 2,
      first: !next,
      last: next,
    });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'First page', exact: true }).click();
  await page.getByLabel('Brand name').fill('unsaved first');
  await page
    .getByRole('navigation', { name: 'Workspace pagination' })
    .getByRole('button', { name: 'Next' })
    .click();
  await expect(page.getByRole('button', { name: 'Second page', exact: true })).toBeVisible();
  await expect(page.getByLabel('Brand name')).toHaveCount(0);
  await page.getByRole('button', { name: 'Second page', exact: true }).click();
  await expect(page.getByLabel('Brand name')).toHaveValue('');
});
