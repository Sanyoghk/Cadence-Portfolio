import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

for (const width of [1440, 768, 390, 320]) {
  test(`Cream Orbit public ${width}: genuine entry, bounded local assets and accessible layout`, async ({
    page,
  }) => {
    const outbound: string[] = [];
    await page.route('**/*', async (r) => {
      if (!r.request().url().startsWith('http://127.0.0.1:4173/')) {
        outbound.push(r.request().url());
        return r.abort();
      }
      return r.continue();
    });
    await page.setViewportSize({ width, height: 900 });
    await page.goto('http://127.0.0.1:4173/');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('CADENCE');
    await expect(page.getByText('Create YOUR brand.', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Explore your brand' })).toHaveCount(0);
    await expect(page.locator('.curtain-star-one, .curtain-star-two')).toHaveCount(0);
    await expect(page.locator('.footer-strip, .public-footer')).toHaveCount(0);
    await expect(page.getByRole('button', { name: /decorative motion/i })).toHaveCount(0);
    const footer = page.getByRole('contentinfo');
    await expect(footer.getByText('Cadence', { exact: true })).toBeVisible();
    await expect(footer.getByText('hello@cadence.example')).toBeVisible();
    await expect(footer.getByText('Demo contact details — not monitored.')).toBeVisible();
    await expect(page.locator('input[type=file],textarea,input[type=url]')).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Open your Workspace' })).toHaveAttribute(
      'href',
      '/workspaces/',
    );
    await expect(page.getByText('Concept plans.', { exact: false })).toBeVisible();
    for (const theme of ['cream', 'dark', 'sage']) {
      await page.getByLabel('Appearance').selectOption(theme);
      expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
    }
    await page.getByLabel('Appearance').selectOption('cream');
    await page.screenshot({ path: `../var/ui-rework-public-${width}.png`, fullPage: true });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect(footer).toBeVisible();
    if (width < 761) {
      const menu = page.locator('.mobile-nav summary');
      await menu.press('Enter');
      await expect(
        page.getByRole('navigation', { name: 'Mobile public navigation' }),
      ).toBeVisible();
      await menu.press('Escape');
      await expect(menu).toBeFocused();
      await expect(page.locator('.mobile-nav')).not.toHaveAttribute('open');
    }
    expect(outbound).toEqual([]);
  });
}
