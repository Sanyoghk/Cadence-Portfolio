import { test, expect, type Page, type Route } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const id = '00000000-0000-0000-0000-000000000001';
const workspace = {
  id,
  name: 'Northstar studio',
  industry: 'Independent creator',
  status: 'ACTIVE',
  lockVersion: 0,
};
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
async function setup(page: Page) {
  await page.route('**/api/v1/**', (route) =>
    json(route, paged(route.request().url().includes('/brands') ? [] : [workspace])),
  );
}

for (const theme of ['Cream', 'Dark', 'Sage'])
  for (const width of [1280, 375]) {
    test(`${theme} ${width}: actual profile form, responsive composition, fonts and axe`, async ({
      page,
    }) => {
      const external: string[] = [];
      page.on('request', (r) => {
        if (!r.url().startsWith('http://127.0.0.1:4174/')) external.push(r.url());
      });
      await page.setViewportSize({ width, height: 900 });
      await setup(page);
      await page.goto('/');
      await page.getByLabel('Appearance').selectOption(theme.toLowerCase());
      await expect(page.locator('.theme-surface')).toHaveAttribute(
        'data-theme',
        theme.toLowerCase(),
      );
      await page.getByRole('button', { name: workspace.name, exact: true }).click();
      await expect(page.getByLabel('Brand name')).toBeVisible();
      await page.getByLabel('Brand name').fill('A real draft name');
      // Appearance changes cannot remount/reset business forms or issue mutations.
      await page.getByLabel('Appearance').selectOption('cream');
      await page.getByLabel('Appearance').selectOption(theme.toLowerCase());
      await expect(page.getByLabel('Brand name')).toHaveValue('A real draft name');
      await page.getByText('Workflow availability', { exact: true }).click();
      await expect(page.getByRole('region', { name: 'Your creative workflow' })).toContainText(
        'Not connected yet',
      );
      await expect(
        page.getByRole('region', { name: 'Your creative workflow' }).getByRole('button'),
      ).toHaveCount(0);
      await page.evaluate(() => document.fonts.ready);
      expect(
        await page.evaluate(
          () =>
            document.fonts.check('500 32px "Cormorant Garamond"') &&
            document.fonts.check('400 14px "DM Sans"'),
        ),
      ).toBe(true);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
      expect(external).toEqual([]);
      await page.screenshot({
        path: `../var/prompt23-visual-${theme.toLowerCase()}-${width}.png`,
        fullPage: true,
      });
    });
  }

for (const theme of ['dark', 'sage'])
  for (const status of [401, 403, 404, 409]) {
    test(`${theme}: controlled ${status} is readable without private detail`, async ({ page }) => {
      await page.route('**/api/v1/**', (r) =>
        json(r, { detail: '<script>private-SQL-secret</script>' }, status),
      );
      await page.goto('/');
      await page.getByLabel('Appearance').selectOption(theme);
      await expect(page.getByRole('alert')).toBeVisible();
      await expect(page.getByText('private-SQL-secret', { exact: false })).toHaveCount(0);
      expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    });
  }

test('native Appearance control and Workspace navigation work by keyboard with visible focus', async ({
  page,
}) => {
  await setup(page);
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.locator('.wordmark')).toBeFocused();
  await page.keyboard.press('Tab');
  const workspaceMenu = page.locator('.workspace-switch > summary');
  await expect(workspaceMenu).toBeFocused();
  await workspaceMenu.press('Enter');
  await expect(page.getByRole('button', { name: 'Manage workspaces', exact: true })).toBeVisible();
  await expect(
    page
      .locator('.workspace-switch')
      .getByRole('button', { name: 'Create workspace', exact: true }),
  ).toHaveCount(0);
  await expect(page.locator('#create-workspace')).toBeVisible();
  await workspaceMenu.press('Escape');
  await expect(workspaceMenu).toBeFocused();
  for (
    let i = 0;
    i < 30 && !(await page.getByLabel('Appearance').evaluate((e) => e === document.activeElement));
    i++
  )
    await page.keyboard.press('Tab');
  await expect(page.getByLabel('Appearance')).toBeFocused();
  expect(
    await page.getByLabel('Appearance').evaluate((e) => getComputedStyle(e).outlineStyle),
  ).toBe('solid');
  await page.getByLabel('Appearance').press('ArrowDown');
  await page.keyboard.press('Tab');
  await expect(page.getByLabel('Appearance')).toHaveValue('dark');
  const choice = page.getByRole('button', { name: workspace.name, exact: true });
  for (let i = 0; i < 30 && !(await choice.evaluate((e) => e === document.activeElement)); i++)
    await page.keyboard.press('Tab');
  await expect(choice).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByLabel('Brand name')).toBeVisible();
});

test('session-key remount removes private selection and rejects late old scope completion', async ({
  page,
}) => {
  await setup(page);
  let release!: () => void;
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  let entered!: () => void;
  const requested = new Promise<void>((resolve) => {
    entered = resolve;
  });
  await page.route(`**/workspaces/${id}/brands?**`, async (r) => {
    entered();
    await held;
    await json(
      r,
      paged([
        {
          id: '00000000-0000-0000-0000-000000000002',
          workspaceId: id,
          name: 'PRIVATE OLD SESSION',
          status: 'ACTIVE',
          lockVersion: 0,
          websiteUrl: null,
          instagramHandle: null,
          tiktokHandle: null,
        },
      ]),
    );
  });
  await page.goto('/?session=switchable');
  await page.getByRole('button', { name: workspace.name, exact: true }).click();
  await requested;
  await page.getByLabel('Brand name').fill('Private unsaved name');
  await page.getByRole('button', { name: 'Switch isolated fixture session' }).click();
  release();
  await expect(page.getByLabel('Brand name')).toHaveCount(0);
  await expect(page.getByText('PRIVATE OLD SESSION')).toHaveCount(0);
  await expect(page.getByText('Private unsaved name')).toHaveCount(0);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Workspace Manager');
});

test('320px long untrusted Workspace text wraps without adding markup or hiding controls', async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 812 });
  const name = '<img src=x onerror=alert(1)>' + 'W'.repeat(200);
  await page.route('**/api/v1/**', (r) =>
    json(r, paged(r.request().url().includes('/brands') ? [] : [{ ...workspace, name }])),
  );
  await page.goto('/');
  await page.getByRole('button', { name, exact: true }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect(page.locator('img[src="x"]')).toHaveCount(0);
  await expect(page.getByLabel('Appearance')).toBeVisible();
});
