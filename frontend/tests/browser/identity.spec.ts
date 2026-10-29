import { test, expect, type Page, type Route } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import * as f from '../support/identity';
import type * as C from '../../src/lib/identity-contract.generated';
const json = (route: Route, value: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
async function setup(
  page: Page,
  options: {
    kind?: 'TEXT' | 'PDF' | 'URL' | 'SOCIAL' | 'IMAGE';
    status?: number;
    previewStatus?: number;
    stale?: boolean;
    unknown?: boolean;
    secondBrand?: boolean;
  } = {},
) {
  const kind = options.kind ?? 'TEXT';
  const evidence: C.BrandEvidenceResponse = {
    ...f.evidence,
    type: kind === 'URL' ? 'URL' : kind === 'PDF' || kind === 'IMAGE' ? 'FILE' : 'TEXT',
    mediaType: kind === 'PDF' ? 'application/pdf' : kind === 'IMAGE' ? 'image/png' : null,
    originalFileName: kind === 'PDF' ? 'source.pdf' : kind === 'IMAGE' ? 'retained.png' : null,
  };
  const state = {
    sources: [] as C.BrandEvidenceResponse[],
    identities: [] as C.BrandIdentityResponse[],
    history: [] as C.EvidenceAdmissionHistory[],
    current: f.identity,
    consents: 0,
    approvals: 0,
    copies: 0,
    submits: [] as { key: string; body: unknown }[],
    stale: !!options.stale,
    denied: 0,
    previewStatus: options.previewStatus ?? 0,
    revision: 0,
  };
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname.replace('/api/v1', ''),
      method = request.method();
    expect(request.headers().authorization).toBe('Bearer isolated-ui-fixture-NOT-A-JWT');
    if (path === '/workspaces') return json(route, f.pageOf([f.workspace]));
    if (path === `/workspaces/${f.w}/brands`)
      return json(
        route,
        f.pageOf(
          options.secondBrand ? [f.brand, { ...f.brand, id: f.id(93), name: 'South' }] : [f.brand],
        ),
      );
    if (path === `/workspaces/${f.w}/brands/${f.id(93)}` || path === `/brands/${f.id(93)}`)
      return json(route, { ...f.brand, id: f.id(93), name: 'South' });
    if (path === `/brands/${f.id(93)}/evidence` || path === `/brands/${f.id(93)}/identities`)
      return json(route, f.pageOf([]));
    if (path === `/workspaces/${f.w}/brands/${f.b}` || path === `/brands/${f.b}`)
      return json(route, f.brand);
    if (state.denied)
      return json(route, { code: 'access_denied', detail: 'private-secret' }, state.denied);
    if (path === `/brands/${f.b}/evidence` || path === `/brands/${f.b}/evidence/social`) {
      if (method === 'GET') return json(route, f.pageOf(state.sources));
      state.sources = [evidence];
      return json(route, { evidence, jobId: f.id(30), jobStatus: 'PENDING' }, 202);
    }
    if (path === `/jobs/${f.id(30)}`)
      return json(route, {
        id: f.id(30),
        status: 'SUCCEEDED',
        attempts: 1,
        maxAttempts: 3,
        nextAttemptAt: null,
        completedAt: f.time,
      });
    if (path.endsWith('/ai-admission/history')) return json(route, f.pageOf(state.history));
    if (path.endsWith('/withdrawals')) {
      expect(request.postData()).toBeNull();
      state.history = state.history.map((v) => ({
        ...v,
        withdrawn: true,
        withdrawnBy: f.id(8),
        withdrawnAt: f.time,
      }));
      return json(
        route,
        { id: f.id(31), admissionId: f.a, withdrawnBy: f.id(8), withdrawnAt: f.time },
        201,
      );
    }
    const preview =
      kind === 'PDF'
        ? f.pdf
        : kind === 'URL'
          ? f.url
          : kind === 'SOCIAL'
            ? f.social
            : { ...f.review, brandVersion: state.revision };
    const admission =
      kind === 'PDF'
        ? { ...f.admission, ...f.pdf, id: f.a, aiConsumptionEnabled: true }
        : kind === 'URL'
          ? {
              ...f.admission,
              ...f.url,
              id: f.a,
              originalScanId: f.id(11),
              aiConsumptionEnabled: true,
            }
          : kind === 'SOCIAL'
            ? {
                review: { ...f.admission, policyVersion: 'social-text-review-v1' },
                snapshotId: f.id(10),
                capturedAt: f.time,
                bindingExpiresAt: f.expiry,
              }
            : { ...f.admission, brandVersion: state.revision };
    if (path.endsWith('/snapshot')) {
      if (state.history.some((v) => v.withdrawn)) return json(route, { code: 'withdrawn' }, 409);
      return json(route, { admission, text: f.text });
    }
    if (path.startsWith(`/evidence/${f.e}/`)) {
      if (method === 'GET')
        return state.previewStatus
          ? json(route, { code: 'unavailable', detail: 'private-secret' }, state.previewStatus)
          : json(route, preview);
      state.consents++;
      const body = request.postDataJSON();
      expect(body).not.toHaveProperty('text');
      expect(body).not.toHaveProperty('reviewedBy');
      expect(body.contentChecksum).toBe(f.hash(f.text));
      if (state.stale) {
        state.stale = false;
        state.revision++;
        return json(route, { code: 'stale_source', detail: 'private-secret' }, 409);
      }
      state.history = [
        { ...f.receipt, policyVersion: preview.policyVersion, brandVersion: state.revision },
      ];
      return json(route, admission, 201);
    }
    if (path === `/brands/${f.b}/analysis-jobs`) {
      state.submits.push({
        key: request.headers()['idempotency-key'],
        body: request.postDataJSON(),
      });
      if (options.status)
        return json(route, { code: 'unavailable', detail: 'private-secret' }, options.status);
      return json(
        route,
        options.unknown
          ? {
              ...f.job,
              status: 'OUTCOME_UNKNOWN',
              resultId: null,
              resultUrl: null,
              errorCode: 'PROVIDER_OUTCOME_UNKNOWN',
            }
          : f.job,
        202,
      );
    }
    if (path === `/jobs/${f.j}`)
      return json(
        route,
        options.unknown
          ? {
              ...f.job,
              status: 'OUTCOME_UNKNOWN',
              resultId: null,
              resultUrl: null,
              errorCode: 'PROVIDER_OUTCOME_UNKNOWN',
            }
          : f.job,
      );
    if (path === `/analysis-results/${f.r}`) return json(route, f.result);
    if (path === `/brands/${f.b}/identities/from-evidence-analysis`) {
      state.copies++;
      expect(request.postDataJSON()).toEqual({ analysisId: f.r, expectedVersion: 0 });
      state.identities = [state.current];
      return json(route, state.current, 201);
    }
    if (path === `/brands/${f.b}/identities`) return json(route, f.pageOf(state.identities));
    if (path === `/identities/${f.i}/evidence-provenance`)
      return json(route, {
        ...f.provenance,
        citations: f.provenance.citations.map((c) => ({
          ...c,
          withdrawn: state.history.some((v) => v.withdrawn),
        })),
      });
    if (path === `/identities/${f.i}/approval`) {
      state.approvals++;
      expect(request.postDataJSON()).toEqual({ expectedVersion: state.current.lockVersion });
      if (state.history.some((v) => v.withdrawn)) return json(route, { code: 'withdrawn' }, 409);
      state.current = {
        ...state.current,
        status: 'APPROVED',
        lockVersion: state.current.lockVersion + 1,
        approvedBy: f.id(8),
        approvedAt: f.time,
      };
      state.identities = [state.current];
      return json(route, state.current);
    }
    if (path === `/identities/${f.i}`) {
      if (method === 'PATCH') {
        const body = request.postDataJSON();
        expect(Object.keys(body).sort()).toEqual(['content', 'expectedVersion']);
        expect(body.expectedVersion).toBe(state.current.lockVersion);
        state.current = {
          ...state.current,
          content: body.content,
          lockVersion: state.current.lockVersion + 1,
        };
        state.identities = [state.current];
      }
      return json(route, state.current);
    }
    throw new Error(`Unexpected fixture request ${method} ${path}`);
  });
  await page.goto('/?session=switchable');
  await page.getByRole('button', { name: 'Studio', exact: true }).click();
  await page.getByRole('button', { name: 'Open North', exact: true }).click();
  await page.getByRole('button', { name: 'Open Brand Identity', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Brand Identity workspace' })).toBeVisible();
  return state;
}
async function register(page: Page, kind = 'TEXT') {
  if (kind === 'TEXT') {
    await page.getByLabel('Source text', { exact: true }).fill(f.text);
    await page.getByRole('button', { name: 'Register text', exact: true }).click();
  } else if (kind === 'URL') {
    await page.getByText('Add a website URL', { exact: true }).click();
    await page.getByLabel('Public website URL').fill('https://example.org');
    await page.getByRole('button', { name: 'Register URL', exact: true }).click();
  } else if (kind === 'SOCIAL') {
    await page.getByText('Capture approved social text', { exact: true }).click();
    await page.getByRole('button', { name: 'Capture social text', exact: true }).click();
  } else {
    await page.getByLabel('Evidence file', { exact: true }).setInputFiles({
      name: kind === 'PDF' ? 'source.pdf' : 'retained.png',
      mimeType: kind === 'PDF' ? 'application/pdf' : 'image/png',
      buffer: Buffer.from('test fixture bytes'),
    });
    await page.getByRole('button', { name: 'Upload source', exact: true }).click();
  }
}

test('Cream Orbit Brand selector cancels late old review and never carries consent to another Brand', async ({
  page,
}) => {
  const state = await setup(page, { secondBrand: true });
  await register(page);
  let entered!: () => void;
  let release!: () => void;
  const requested = new Promise<void>((r) => (entered = r));
  const pending = new Promise<void>((r) => (release = r));
  await page.route(`**/evidence/${f.e}/ai-admission`, async (route) => {
    entered();
    await pending;
    await json(route, f.review);
  });
  await page.getByRole('button', { name: 'Load exact snapshot' }).click();
  await requested;
  await page.getByLabel('Brand', { exact: true }).selectOption(f.id(93));
  release();
  await expect(page.getByText('No sources registered for this Brand.')).toBeVisible();
  await expect(page.getByLabel('Exact review text', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ })).toHaveCount(
    0,
  );
  expect(state.consents).toBe(0);
  expect(state.approvals).toBe(0);
});

test('Cream Orbit section Back and Workspace switch remove uncommitted consent and private forms', async ({
  page,
}) => {
  const state = await setup(page);
  await register(page);
  await page.getByRole('button', { name: 'Load exact snapshot' }).click();
  await page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ }).check();
  const manager = page
    .getByRole('navigation', { name: 'Workspace sections', exact: true })
    .getByRole('button', { name: 'Workspace Manager' });
  await manager.click();
  await expect(page.getByRole('region', { name: 'Brand Identity workspace' })).toHaveCount(0);
  await page.goBack();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Brand Identity');
  await expect(page.getByRole('region', { name: 'Brand Identity workspace' })).toBeVisible();
  await expect(page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ })).toHaveCount(
    0,
  );
  await page.locator('.workspace-switch > summary').click();
  await page.getByRole('button', { name: 'Studio', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Workspace Manager');
  await expect(page.getByRole('region', { name: 'Brand Identity workspace' })).toHaveCount(0);
  expect(state.consents).toBe(0);
  expect(state.approvals).toBe(0);
});

for (const width of [1440, 768, 390, 320])
  test(`Cream Orbit Identity ${width}: header, exact consent and theme remain coherent`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    const state = await setup(page);
    await register(page);
    await page.getByRole('button', { name: 'Load exact snapshot' }).click();
    const consent = page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ });
    await consent.check();
    await page.getByLabel('Appearance').selectOption('dark');
    await expect(consent).toBeChecked();
    await page.getByLabel('Appearance').selectOption('cream');
    await page.getByText('Add source material', { exact: true }).click();
    await page.getByRole('link', { name: 'Skip to content' }).focus();
    await page.getByRole('link', { name: 'Skip to content' }).press('Enter');
    await expect(page).toHaveURL(/#identity$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Brand Identity');
    await expect(consent).toBeChecked();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      await page.evaluate(
        () =>
          document.querySelector('h1')!.getBoundingClientRect().top >=
          document.querySelector('header')!.getBoundingClientRect().bottom,
      ),
    ).toBe(true);
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: `../var/ui-rework-identity-${width}.png`, fullPage: true });
    expect(state.consents).toBe(0);
    expect(state.approvals).toBe(0);
  });
async function consent(page: Page) {
  await page.getByRole('button', { name: 'Load exact snapshot' }).click();
  await expect(page.getByLabel('Exact review text', { exact: true })).toHaveText(f.text);
  await page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ }).check();
  await page.getByRole('button', { name: 'Confirm this exact snapshot' }).click();
}
async function generate(page: Page) {
  await page.getByRole('button', { name: 'Generate from admitted evidence' }).click();
  await expect(page.getByRole('region', { name: 'Extracted Identity result' })).toContainText(
    'DEMONSTRATION OUTPUT',
  );
  await page.getByRole('button', { name: 'Create Identity DRAFT from this result' }).click();
  await expect(page.getByRole('region', { name: 'Identity revision details' })).toContainText(
    'Revision 1 · DRAFT',
  );
}

test('evidence → exact consent → current read → generated DRAFT → human edit → separate approval', async ({
  page,
}) => {
  const s = await setup(page);
  await register(page);
  await consent(page);
  expect(s.consents).toBe(1);
  await page.getByRole('button', { name: 'Check current snapshot' }).click();
  await expect(page.getByRole('region', { name: 'Current admitted snapshot' })).toContainText(
    f.text,
  );
  expect(await page.locator('.review-text img').count()).toBe(0);
  await generate(page);
  expect(s.approvals).toBe(0);
  await page.getByRole('button', { name: 'Edit DRAFT', exact: true }).click();
  await page.getByRole('textbox', { name: 'Sample caption', exact: true }).fill('My human edit.');
  await page.getByRole('button', { name: 'Save DRAFT', exact: true }).click();
  const detail = page.getByRole('region', { name: 'Identity revision details' });
  await expect(detail).toContainText('My human edit.');
  await expect(detail).toContainText('DEMONSTRATION ORIGIN');
  expect(s.approvals).toBe(0);
  await page.getByRole('checkbox', { name: /I reviewed the committed content/ }).check();
  await page.getByRole('button', { name: 'Approve committed Identity' }).click();
  await expect(detail).toContainText('Revision 1 · APPROVED');
  expect(s.approvals).toBe(1);
  expect(s.copies).toBe(1);
  await expect(page.getByRole('button', { name: 'Create new DRAFT revision' })).toBeVisible();
});

test('409 invalidates exact consent; fresh review has a new context token; withdrawal remains historical', async ({
  page,
}) => {
  const s = await setup(page, { stale: true });
  await register(page);
  await consent(page);
  await expect(page.getByRole('alert')).toContainText('changed');
  await expect(page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ })).toHaveCount(
    0,
  );
  expect(s.consents).toBe(1);
  await consent(page);
  expect(s.consents).toBe(2);
  expect(s.history[0].brandVersion).toBe(1);
  await page.getByRole('button', { name: 'Withdraw source version', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm withdrawal of version 0' }).click();
  await expect(page.getByRole('region', { name: 'Source review and history' })).toContainText(
    'Withdrawn',
  );
  await expect(page.getByRole('button', { name: 'Check current snapshot' })).toBeDisabled();
});
for (const kind of ['PDF', 'URL', 'SOCIAL'] as const)
  test(`${kind}: typed source registration, review and current snapshot`, async ({ page }) => {
    const s = await setup(page, { kind });
    await register(page, kind);
    await consent(page);
    await page.getByRole('button', { name: 'Check current snapshot' }).click();
    await expect(page.getByRole('region', { name: 'Current admitted snapshot' })).toContainText(
      f.text,
    );
    expect(s.consents).toBe(1);
  });
test('images remain explicit provider gap; retained bytes never gain observations', async ({
  page,
}) => {
  await setup(page, { kind: 'IMAGE' });
  await register(page, 'IMAGE');
  await expect(page.getByRole('region', { name: 'Selected source' })).toContainText(
    'requires a qualified provider',
  );
  await expect(page.getByRole('button', { name: 'Load exact snapshot' })).toHaveCount(0);
});
for (const status of [409, 503])
  test(`expired/scan/storage/model ${status}: no invented preview or result`, async ({ page }) => {
    const s = await setup(page, { previewStatus: status, status: 503 });
    await register(page);
    await page.getByRole('button', { name: 'Load exact snapshot' }).click();
    await expect(
      page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ }),
    ).toHaveCount(0);
    await page.getByRole('button', { name: 'Generate from admitted evidence' }).click();
    await expect(
      page.getByRole('region', { name: 'Generate Identity' }).getByRole('alert'),
    ).toBeVisible();
    await expect(page.getByRole('region', { name: 'Extracted Identity result' })).toHaveCount(0);
    expect(s.approvals).toBe(0);
  });
test('unknown provider outcome is explicit, with no draft or automatic resubmission', async ({
  page,
}) => {
  const s = await setup(page, { unknown: true });
  await page.getByRole('button', { name: 'Generate from admitted evidence' }).click();
  await expect(page.getByRole('region', { name: 'Identity generation Job' })).toContainText(
    'OUTCOME_UNKNOWN',
  );
  await expect(page.getByRole('region', { name: 'Extracted Identity result' })).toHaveCount(0);
  expect(s.submits).toHaveLength(1);
});
for (const status of [401, 403, 404])
  test(`revoked/foreign access ${status} removes private workbench`, async ({ page }) => {
    const s = await setup(page);
    await register(page);
    await page.getByRole('button', { name: 'Load exact snapshot' }).click();
    await expect(page.getByLabel('Exact review text', { exact: true })).toBeVisible();
    s.denied = status;
    await page.getByRole('button', { name: 'Refresh source list' }).click();
    await expect(page.getByRole('region', { name: 'Identity access unavailable' })).toBeVisible();
    await expect(page.getByLabel('Exact review text', { exact: true })).toHaveCount(0);
    await expect(page.getByText('private-secret', { exact: false })).toHaveCount(0);
  });
test('withdrawal after draft read is rejected at approval; refreshing history exposes inherited withdrawal', async ({
  page,
}) => {
  const s = await setup(page);
  await register(page);
  await consent(page);
  await generate(page);
  await page.getByRole('button', { name: 'Withdraw source version', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm withdrawal of version 0' }).click();
  await page.getByRole('checkbox', { name: /I reviewed the committed content/ }).check();
  await page.getByRole('button', { name: 'Approve committed Identity' }).click();
  await expect(
    page.getByRole('region', { name: 'Identity revision details' }).getByRole('alert'),
  ).toBeVisible();
  expect(s.current.status).toBe('DRAFT');
  await page.getByRole('button', { name: 'Refresh Identity history' }).click();
  await expect(
    page.getByRole('checkbox', { name: /I reviewed the committed content/ }),
  ).toBeDisabled();
});
for (const theme of ['cream', 'dark', 'sage'])
  test(`${theme}: responsive evidence/review keyboard focus and axe`, async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 900 });
    await setup(page);
    await page.getByLabel('Appearance').selectOption(theme);
    await register(page);
    await page.getByRole('button', { name: 'Load exact snapshot' }).click();
    await page.getByLabel('Exact review text', { exact: true }).focus();
    await expect(page.getByLabel('Exact review text', { exact: true })).toBeFocused();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    await page.screenshot({ path: `../var/prompt24-${theme}-mobile.png`, fullPage: true });
  });
test('session switch removes evidence state and late preview cannot restore it', async ({
  page,
}) => {
  await setup(page);
  await register(page);
  let release!: () => void;
  let entered!: () => void;
  const waiting = new Promise<void>((r) => (entered = r));
  const pending = new Promise<void>((r) => (release = r));
  await page.route(`**/evidence/${f.e}/ai-admission`, async (route) => {
    entered();
    await pending;
    await json(route, f.review);
  });
  await page.getByRole('button', { name: 'Load exact snapshot' }).click();
  await waiting;
  await page.getByRole('button', { name: 'Switch isolated fixture session' }).click();
  release();
  await expect(page.getByRole('region', { name: 'Brand Identity workspace' })).toHaveCount(0);
  await expect(page.getByLabel('Exact review text', { exact: true })).toHaveCount(0);
});

test('uncertain submission reuses exact key/body; malformed successful result never becomes a draft', async ({
  page,
}) => {
  const s = await setup(page, { status: 503 });
  await page.getByRole('button', { name: 'Generate from admitted evidence' }).click();
  await expect(
    page.getByRole('button', { name: 'Retry same generation submission' }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Retry same generation submission' }).click();
  await expect.poll(() => s.submits.length).toBe(2);
  expect(s.submits[1]).toEqual(s.submits[0]);
  expect(s.copies).toBe(0);
});
test('malformed result blocks copy; stale edit remains unapproved and requires explicit reload', async ({
  page,
}) => {
  const s = await setup(page);
  await page.route(`**/analysis-results/${f.r}`, (route) =>
    json(route, {
      ...f.result,
      citations: [{ admissionId: 'foreign-not-uuid', quote: 'invented' }],
    }),
  );
  await page.getByRole('button', { name: 'Generate from admitted evidence' }).click();
  await expect(
    page.getByRole('region', { name: 'Identity generation Job' }).getByRole('alert'),
  ).toContainText('unexpected response');
  await expect(
    page.getByRole('button', { name: 'Create Identity DRAFT from this result' }),
  ).toHaveCount(0);
  expect(s.copies).toBe(0);
  s.identities = [f.identity];
  await page.getByRole('button', { name: 'Refresh Identity history' }).click();
  await page.getByRole('button', { name: 'Open Identity revision 1' }).click();
  await page.route(`**/identities/${f.i}`, (route) =>
    route.request().method() === 'PATCH'
      ? json(route, { code: 'stale_version' }, 409)
      : json(route, f.identity),
  );
  await page.getByRole('button', { name: 'Edit DRAFT', exact: true }).click();
  await page.getByRole('textbox', { name: 'Sample caption', exact: true }).fill('Unsaved edit');
  await page.getByRole('button', { name: 'Save DRAFT', exact: true }).click();
  await expect(
    page.getByRole('region', { name: 'Identity editor' }).getByRole('alert'),
  ).toContainText('changed');
  await expect(
    page.getByRole('checkbox', { name: /I reviewed the committed content/ }),
  ).toHaveCount(0);
  expect(s.approvals).toBe(0);
  expect(s.current.lockVersion).toBe(0);
});
test('foreign successful Identity response is refused rather than displayed', async ({ page }) => {
  const s = await setup(page);
  s.identities = [f.identity];
  await page.getByRole('button', { name: 'Refresh Identity history' }).click();
  await page.route(`**/identities/${f.i}`, (route) =>
    json(route, {
      ...f.identity,
      brandId: f.id(91),
      content: { ...f.content, sampleCaption: 'FOREIGN PRIVATE CAPTION' },
    }),
  );
  await page.getByRole('button', { name: 'Open Identity revision 1' }).click();
  await expect(
    page.getByRole('region', { name: 'Brand Identity workspace' }).getByRole('alert'),
  ).toContainText('unexpected response');
  await expect(page.getByText('FOREIGN PRIVATE CAPTION')).toHaveCount(0);
});
test('desktop review and Identity editor/approval remain accessible and no external requests occur', async ({
  page,
}) => {
  const external: string[] = [];
  page.on('request', (r) => {
    if (!r.url().startsWith('http://127.0.0.1:4174/')) external.push(r.url());
  });
  await page.setViewportSize({ width: 1280, height: 900 });
  await setup(page);
  await register(page);
  await consent(page);
  await generate(page);
  await page.getByRole('button', { name: 'Edit DRAFT', exact: true }).click();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole('button', { name: 'Close editor' }).click();
  await page.getByRole('checkbox', { name: /I reviewed the committed content/ }).focus();
  await page.keyboard.press('Space');
  await expect(page.getByRole('button', { name: 'Approve committed Identity' })).toBeEnabled();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(external).toEqual([]);
  await page.screenshot({ path: '../var/prompt24-identity-desktop.png', fullPage: true });
});

test('approved edit lineage keeps the original demo attribution without inventing a new provider receipt', async ({
  page,
}) => {
  const s = await setup(page);
  const child = {
    ...f.identity,
    id: f.id(55),
    version: 2,
    revisedFromId: f.i,
    analysisProvenance: null,
  };
  s.identities = [child];
  await page.route(`**/identities/${child.id}`, (route) => json(route, child));
  await page.route(`**/identities/${child.id}/evidence-provenance`, (route) =>
    json(route, { ...f.provenance, identityId: child.id }),
  );
  await page.getByRole('button', { name: 'Refresh Identity history' }).click();
  await page.getByRole('button', { name: 'Open Identity revision 2' }).click();
  const detail = page.getByRole('region', { name: 'Identity revision details' });
  await expect(detail).toContainText('DEMONSTRATION ORIGIN');
  await expect(detail).toContainText(`Branched from ${f.i}`);
  await detail.getByText('Original analysis receipt', { exact: true }).click();
  await expect(detail.getByLabel('Original analysis provenance data')).toContainText('fake-local');
  expect(child.analysisProvenance).toBeNull();
});

test('legacy unknown file metadata is unsupported, without invented PDF provenance or visual classification', async ({
  page,
}) => {
  const s = await setup(page);
  s.sources = [
    {
      ...f.evidence,
      type: 'FILE',
      mediaType: null,
      originalFileName: 'legacy.pdf',
      sourceReference: null,
    },
  ];
  s.history = [
    {
      ...f.receipt,
      policyVersion: 'pdf-text-review-v1',
      artifactId: f.id(10),
      originalScanId: f.id(11),
      latestScanId: f.id(12),
      scanExpiresAt: f.expiry,
    },
  ];
  await page.getByRole('button', { name: 'Refresh source list' }).click();
  await page.getByRole('button', { name: `Inspect source ${f.e.slice(0, 8)}` }).click();
  await expect(page.getByRole('region', { name: 'Selected source' })).toContainText('UNSUPPORTED');
  await expect(page.getByRole('button', { name: 'Load exact snapshot' })).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Selected source' })).not.toContainText(
    'retained visual',
  );
  await page.getByRole('button', { name: 'Withdraw source version', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm withdrawal of version 0' }).click();
  await expect(page.getByRole('region', { name: 'Source review and history' })).toContainText(
    'Withdrawn',
  );
});
