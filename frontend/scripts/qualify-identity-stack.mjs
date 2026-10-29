// Test harness only: ephemeral loopback issuer, fresh H2/local fake backend, real HTTP responses.
// Never authenticates a deployed service or prints/persists a signing key/JWT.
import { generateKeyPairSync, randomUUID, sign } from 'node:crypto';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { createWriteStream } from 'node:fs';
import { access } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { chromium, expect } from '@playwright/test';
const root = fileURLToPath(new URL('../..', import.meta.url));
const jar = fileURLToPath(new URL('../../target/cadence-0.0.1-SNAPSHOT.jar', import.meta.url));
const frontend = fileURLToPath(new URL('..', import.meta.url));
const app = 'http://127.0.0.1:18124',
  issuer = 'http://127.0.0.1:18125',
  fixture = 'http://127.0.0.1:4174';
if (process.argv[2] !== '--isolated-local')
  throw Error('Requires --isolated-local; only a fresh owned local test process is supported');
await access(jar);
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const kid = randomUUID(),
  subject = `isolated-stack-${randomUUID()}`;
function jwt(sub = subject, role = 'OWNER') {
  const now = Math.floor(Date.now() / 1000);
  const encode = (v) => Buffer.from(JSON.stringify(v)).toString('base64url');
  const body = `${encode({ alg: 'RS256', kid, typ: 'JWT' })}.${encode({ iss: issuer, aud: 'cadence-stack-test', azp: fixture, sub, iat: now, exp: now + 300, cadence_role: role, cadence_workspace_ids: [] })}`;
  return `${body}.${sign('RSA-SHA256', Buffer.from(body), privateKey).toString('base64url')}`;
}
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function listen(server, port) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', resolve);
  });
}
async function free(port) {
  const s = createServer();
  await listen(s, port);
  await new Promise((r) => s.close(r));
}
for (const port of [18124, 18125, 4174]) await free(port);
const keys = createServer((req, res) => {
  if (req.method !== 'GET' || req.url !== '/.well-known/jwks.json') {
    res.writeHead(404).end();
    return;
  }
  res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }).end(
    JSON.stringify({
      keys: [{ ...publicKey.export({ format: 'jwk' }), kid, alg: 'RS256', use: 'sig' }],
    }),
  );
});
let java, vite, browser;
const logs = [];
function launch(command, args, cwd, label) {
  const log = createWriteStream(`${root}/var/prompt24-stack-${label}.log`);
  logs.push(log);
  const child = spawn(command, args, { cwd, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(log);
  child.stderr.pipe(log);
  child.once('error', () => {
    log.write('Local child process failed to launch\n');
  });
  return child;
}
async function ready(url, child) {
  const end = Date.now() + 45000;
  while (Date.now() < end) {
    if (child.exitCode !== null) throw Error('Owned local process exited before readiness');
    try {
      if ((await fetch(url, { signal: AbortSignal.timeout(1500) })).ok) return;
    } catch {
      // A bounded startup connection failure is expected until the owned process listens.
    }
    await delay(250);
  }
  throw Error('Owned local readiness deadline exceeded');
}
async function stop(child) {
  if (!child || child.exitCode !== null) return;
  const ended = new Promise((r) => child.once('exit', r));
  child.kill();
  await Promise.race([ended, delay(5000)]);
  if (child.exitCode === null) {
    child.kill('SIGKILL');
    await Promise.race([ended, delay(5000)]);
  }
}
let owner = jwt(),
  active = owner,
  sourceId,
  identityId,
  admissionId,
  brandId,
  workspaceId;
const statusCounts = {};
async function api(path, method = 'GET', body, token = owner) {
  const res = await fetch(`${app}/api/v1${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000),
  });
  return res;
}
try {
  await listen(keys, 18125);
  java = launch(
    'java',
    [
      '-jar',
      jar,
      '--spring.profiles.active=local',
      '--server.address=127.0.0.1',
      '--server.port=18124',
      '--spring.datasource.url=jdbc:h2:mem:cadence-stack;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
      '--spring.datasource.username=sa',
      '--spring.datasource.password=',
      '--spring.datasource.driver-class-name=org.h2.Driver',
      '--spring.flyway.enabled=false',
      '--spring.jpa.hibernate.ddl-auto=create-drop',
      `--cadence.security.clerk.issuer=${issuer}`,
      `--cadence.security.clerk.jwks-uri=${issuer}/.well-known/jwks.json`,
      '--cadence.security.clerk.audience=cadence-stack-test',
      `--cadence.security.clerk.authorized-parties=${fixture}`,
      `--cadence.security.cors.allowed-origins=${fixture}`,
      '--cadence.brand-analysis.adapter=fake',
      '--cadence.strategy-generation.adapter=fake',
      '--cadence.content-generation.adapter=fake',
      '--cadence.ai-jobs.worker-enabled=true',
      '--cadence.evidence.cleanup-enabled=false',
      '--cadence.evidence.url-worker-enabled=false',
      '--cadence.evidence.file-scan-worker-enabled=false',
      '--cadence.evidence.artifacts-enabled=false',
      '--cadence.evidence.storage-root=var/prompt24-stack-evidence',
    ],
    root,
    'backend',
  );
  await ready(`${app}/api/v1/health`, java);
  vite = launch(
    process.execPath,
    [
      `${frontend}/node_modules/vite/bin/vite.js`,
      '--config',
      'tests/fixture/vite.config.ts',
      '--host',
      '127.0.0.1',
    ],
    frontend,
    'fixture',
  );
  await ready(fixture, vite);
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  // The isolated UI session is bridged to REAL HTTP/JWT validation, never a generated response.
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      url = new URL(request.url());
    const response = await route.fetch({
      url: app + url.pathname + url.search,
      headers: { ...request.headers(), authorization: `Bearer ${active}` },
      timeout: 20000,
    });
    statusCounts[response.status()] = (statusCounts[response.status()] ?? 0) + 1;
    if (response.ok() && request.method() === 'POST') {
      const v = await response.json();
      if (url.pathname === '/api/v1/workspaces') workspaceId = v.id;
      if (url.pathname.endsWith('/brands')) brandId = v.id;
      if (url.pathname.endsWith('/evidence')) sourceId = v.evidence.id;
      if (url.pathname.endsWith('/ai-admission')) admissionId = v.id;
      if (url.pathname.endsWith('/identities/from-evidence-analysis')) identityId = v.id;
    }
    await route.fulfill({ response });
  });
  await page.goto(fixture);
  await page.getByText('Create a Workspace', { exact: true }).click();
  await page.getByLabel('Workspace name').fill('Stack studio');
  await page.getByLabel('Industry', { exact: true }).fill('Creator');
  await page.getByRole('button', { name: 'Create Workspace', exact: true }).click();
  await page.getByLabel('Brand name').fill('Stack North');
  await page.getByRole('button', { name: 'Create Brand', exact: true }).click();
  await page.getByRole('button', { name: 'Open Stack North', exact: true }).click();
  await page.getByRole('button', { name: 'Open Brand Identity', exact: true }).click();
  await page
    .getByLabel('Source text', { exact: true })
    .fill('Independent creator. Calm practical voice. <script>never execute</script>');
  await page.getByRole('button', { name: 'Register text', exact: true }).click();
  await page.getByRole('button', { name: 'Load exact snapshot' }).click();
  await expect(page.getByLabel('Exact review text', { exact: true })).toContainText(
    'Independent creator',
  );
  await page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ }).check();
  const brand = await (await api(`/brands/${brandId}`)).json();
  const changed = await api(`/brands/${brandId}`, 'PATCH', {
    expectedVersion: brand.lockVersion,
    name: 'Stack North revised',
    websiteUrl: null,
    instagramHandle: null,
    tiktokHandle: null,
  });
  expect(changed.status).toBe(200);
  await page.getByRole('button', { name: 'Confirm this exact snapshot' }).click();
  await expect(
    page.getByRole('region', { name: 'Source review and history' }).getByRole('alert'),
  ).toContainText('changed');
  await expect(page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ })).toHaveCount(
    0,
  );
  await page.getByRole('button', { name: 'Load exact snapshot' }).click();
  await page.getByRole('checkbox', { name: /I reviewed this exact snapshot/ }).check();
  await page.getByRole('button', { name: 'Confirm this exact snapshot' }).click();
  await page.getByRole('button', { name: 'Check current snapshot' }).click();
  await expect(page.getByRole('region', { name: 'Current admitted snapshot' })).toContainText(
    'Independent creator',
  );
  await page.getByRole('button', { name: 'Generate from admitted evidence' }).click();
  await expect(page.getByRole('region', { name: 'Extracted Identity result' })).toContainText(
    'DEMONSTRATION',
    { timeout: 20000 },
  );
  await page.getByRole('button', { name: 'Create Identity DRAFT from this result' }).click();
  const detail = page.getByRole('region', { name: 'Identity revision details' });
  await expect(detail).toContainText('DRAFT');
  await page.getByRole('button', { name: 'Edit DRAFT', exact: true }).click();
  await page
    .getByRole('textbox', { name: 'Sample caption', exact: true })
    .fill('Human reviewed caption');
  await page.getByRole('button', { name: 'Save DRAFT', exact: true }).click();
  await expect(detail).toContainText('Human reviewed caption');
  await page.getByRole('checkbox', { name: /I reviewed the committed content/ }).check();
  await page.getByRole('button', { name: 'Approve committed Identity' }).click();
  await expect(detail).toContainText('APPROVED');
  const approved = await (await api(`/identities/${identityId}`)).json();
  expect(approved.status).toBe('APPROVED');
  expect(approved.approvedBy).toBeTruthy();
  expect(approved.content.sampleCaption).toBe('Human reviewed caption');
  await detail.screenshot({ path: `${root}/var/prompt24-identity-approved-http.png` });
  await page.getByRole('button', { name: 'Create new DRAFT revision' }).click();
  await page
    .getByRole('textbox', { name: 'Sample caption', exact: true })
    .fill('New branch content');
  await page.getByRole('button', { name: 'Save as new DRAFT' }).click();
  await expect(detail).toContainText('Branched from');
  await expect(detail).toContainText('DEMONSTRATION ORIGIN');
  await expect(detail).toContainText('DRAFT');
  const history = await (await api(`/brands/${brandId}/identities?size=20`)).json();
  expect(history.content).toHaveLength(2);
  const branch = history.content.find((v) => v.id !== identityId);
  expect(branch.revisedFromId).toBe(identityId);
  expect((await (await api(`/identities/${identityId}`)).json()).content.sampleCaption).toBe(
    'Human reviewed caption',
  );
  await page.getByRole('button', { name: 'Withdraw source version', exact: true }).click();
  await page.getByRole('button', { name: /Confirm withdrawal of version/ }).click();
  await expect(page.getByRole('region', { name: 'Source review and history' })).toContainText(
    'Withdrawn',
  );
  expect(
    (
      await api(`/identities/${branch.id}/approval`, 'POST', {
        expectedVersion: branch.lockVersion,
      })
    ).status,
  ).toBe(409);
  expect((await api(`/evidence/${sourceId}/ai-admission/${admissionId}/snapshot`)).status).toBe(
    409,
  );
  await page.getByRole('button', { name: 'Refresh Identity history' }).click();
  await expect(
    page.getByRole('checkbox', { name: /I reviewed the committed content/ }),
  ).toBeDisabled();
  const foreign = jwt(`isolated-foreign-${randomUUID()}`);
  for (const path of [`/identities/${identityId}`, `/evidence/${sourceId}/ai-admission/history`])
    expect([403, 404]).toContain((await api(path, 'GET', undefined, foreign)).status);
  active = foreign;
  await page.getByRole('button', { name: 'Refresh source list' }).click();
  await expect(page.getByRole('region', { name: 'Identity access unavailable' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Identity revision details' })).toHaveCount(0);
  expect(workspaceId).toBeTruthy();
  expect(statusCounts[401] ?? 0).toBe(0);
  expect(Object.keys(statusCounts).some((status) => Number(status) >= 500)).toBe(false);
  console.log(
    `PASS real HTTP stack: onboarding/evidence/stale409/review/current/generation/edit/approval/branch/history/withdrawal/foreign/revocation; statuses ${JSON.stringify(statusCounts)}. Ephemeral issuer, H2 and backend local fake; not live Clerk/AI.`,
  );
} finally {
  if (browser) await browser.close();
  await stop(vite);
  await stop(java);
  await new Promise((r) => keys.close(r));
  for (const log of logs) log.end();
}
for (const port of [18124, 18125, 4174]) await free(port);
console.log('Owned backend/fixture/JWKS stopped; loopback ports released.');
