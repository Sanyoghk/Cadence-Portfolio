import { describe, it, expect, vi } from 'vitest';
import { CadenceApi } from '../../src/lib/api';
import { contract } from '../../src/lib/contract';
import { reviewKind } from '../../src/lib/identity-api';
import * as f from '../support/identity';
const signal = () => new AbortController().signal;
const scope = { workspaceId: f.w, brandId: f.b };
const json = (v: unknown, status = 200) =>
  new Response(JSON.stringify(v), { status, headers: { 'content-type': 'application/json' } });
function setup(v: unknown) {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValue(json(v));
  return {
    fetcher,
    api: new CadenceApi('https://api.test/api/v1', async () => 'fixture', fetcher).identity,
  };
}
describe('actual evidence/Identity trust contracts', () => {
  it('verifies exact UTF8 SHA256, freezes preview and sends only review selectors', async () => {
    const { api, fetcher } = setup(f.review);
    const p = await api.preview('TEXT', f.e, signal());
    expect(Object.isFrozen(p.value)).toBe(true);
    fetcher.mockResolvedValue(json(f.admission));
    await api.admit(p, signal());
    expect(JSON.parse(fetcher.mock.calls[1][1]!.body as string)).toEqual({
      sourceVersion: 0,
      brandVersion: 0,
      workspaceVersion: 0,
      contentChecksum: f.hash(f.text),
    });
    expect(fetcher.mock.calls[1][1]).toMatchObject({
      credentials: 'omit',
      cache: 'no-store',
      redirect: 'error',
    });
  });
  it.each([
    { ...f.review, text: 'changed' },
    { ...f.review, evidenceId: f.id(90) },
    { ...f.review, text: '\ud800' },
  ])('rejects corrupt, foreign or invalid Unicode preview %#', async (value) => {
    const { api } = setup(value);
    await expect(api.preview('TEXT', f.e, signal())).rejects.toMatchObject({
      problem: { code: 'invalid_response' },
    });
  });
  for (const [kind, preview, route, receipt] of [
    [
      'PDF',
      f.pdf,
      'document-review',
      {
        ...f.admission,
        policyVersion: 'pdf-text-review-v1',
        artifactId: f.id(10),
        originalScanId: f.id(11),
        latestScanId: f.id(12),
        rendererVersion: 'renderer-v1',
        scanExpiresAt: f.expiry,
        aiConsumptionEnabled: true,
      },
    ],
    [
      'URL',
      f.url,
      'url-review',
      {
        ...f.admission,
        policyVersion: 'url-text-review-v1',
        snapshotId: f.id(10),
        originalScanId: f.id(11),
        latestScanId: f.id(12),
        scanExpiresAt: f.expiry,
        aiConsumptionEnabled: true,
      },
    ],
    [
      'SOCIAL',
      f.social,
      'social-review',
      {
        review: { ...f.admission, policyVersion: 'social-text-review-v1' },
        snapshotId: f.id(10),
        capturedAt: f.time,
        bindingExpiresAt: f.expiry,
      },
    ],
  ] as const)
    it(`${kind} uses dedicated preview/consent contract, never generic fallback`, async () => {
      const { api, fetcher } = setup(preview);
      const p = await api.preview(kind, f.e, signal());
      fetcher.mockResolvedValue(json(receipt));
      await api.admit(p, signal());
      expect(fetcher.mock.calls[0][0]).toBe(`https://api.test/api/v1/evidence/${f.e}/${route}`);
      expect(fetcher.mock.calls[1][0]).toBe(
        `https://api.test/api/v1/evidence/${f.e}/${route}/admissions`,
      );
      const body = JSON.parse(fetcher.mock.calls[1][1]!.body as string);
      expect(body).not.toHaveProperty('text');
      expect(body).not.toHaveProperty('reviewedBy');
      expect(body.contentChecksum).toBe(f.review.contentChecksum);
    });
  it('checks fresh snapshot checksum and receipt binding', async () => {
    const { api, fetcher } = setup({ admission: f.admission, text: f.text });
    expect((await api.snapshot('TEXT', f.e, f.a, signal())).text).toBe(f.text);
    fetcher.mockResolvedValue(json({ admission: { ...f.admission, id: f.id(90) }, text: f.text }));
    await expect(api.snapshot('TEXT', f.e, f.a, signal())).rejects.toThrow();
  });
  it('withdrawal has no actor, tenant or consent body', async () => {
    const { api, fetcher } = setup({
      id: f.id(20),
      admissionId: f.a,
      withdrawnBy: f.id(8),
      withdrawnAt: f.time,
    });
    await api.withdraw(f.e, f.a, signal());
    expect(fetcher.mock.calls[0][1]!.body).toBeUndefined();
  });
  it('multipart uses generated boundary and rejects unqualified type/size before fetch', async () => {
    const { api, fetcher } = setup({ evidence: f.evidence, jobId: f.j, jobStatus: 'PENDING' });
    await api.upload(scope, new File(['%PDF-'], 'test.pdf', { type: 'application/pdf' }), signal());
    expect(fetcher.mock.calls[0][1]!.body).toBeInstanceOf(FormData);
    expect(fetcher.mock.calls[0][1]!.headers).not.toHaveProperty('Content-Type');
    await expect(
      api.upload(scope, new File(['x'], 'evil.svg', { type: 'image/svg+xml' }), signal()),
    ).rejects.toThrow();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('Identity edit filters audit fields and preserves version/branch semantics', async () => {
    const { api, fetcher } = setup({ ...f.identity, lockVersion: 1 });
    await api.editIdentity(
      scope,
      f.identity,
      { ...f.content, approvedBy: f.id(90) } as typeof f.content,
      signal(),
    );
    expect(JSON.parse(fetcher.mock.calls[0][1]!.body as string)).toEqual({
      content: f.content,
      expectedVersion: 0,
    });
    expect(fetcher.mock.calls[0][1]!.method).toBe('PATCH');
    fetcher.mockResolvedValue(json({ ...f.identity, brandId: f.id(90) }));
    await expect(api.readIdentity(scope, f.i, signal())).rejects.toThrow();
    fetcher.mockResolvedValue(json(f.identity));
    await expect(
      api.editIdentity(scope, { ...f.identity, status: 'APPROVED' }, f.content, signal()),
    ).rejects.toThrow();
    await expect(api.approve(scope, f.identity, signal())).rejects.toThrow();
  });
  it('generation uses same caller key, fixed result route and no arbitrary result URL', async () => {
    const { api, fetcher } = setup({ ...f.job, resultUrl: 'https://private.invalid/secret' });
    await expect(api.submit(scope, 0, 'same-key', signal())).rejects.toThrow();
    fetcher.mockResolvedValue(json(f.job));
    await api.submit(scope, 0, 'same-key', signal());
    expect(fetcher.mock.calls[0][1]!.headers).toHaveProperty('Idempotency-Key', 'same-key');
    fetcher.mockResolvedValue(json(f.result));
    await api.analysisResult(f.r, signal());
    expect(fetcher.mock.calls[2][0]).toBe(`https://api.test/api/v1/analysis-results/${f.r}`);
  });
  it('rejects foreign history, unknown policy/status and unbounded pages', async () => {
    const { api, fetcher } = setup(f.pageOf([{ ...f.receipt, evidenceId: f.id(90) }]));
    await expect(api.history(f.e, 0, signal())).rejects.toThrow();
    expect(() => reviewKind('vision-invented')).toThrow();
    expect(() => contract('AiGenerationJob', { ...f.job, status: 'MAGIC_SUCCESS' })).toThrow();
    await expect(api.sources(scope, 10001, signal())).rejects.toThrow();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it('invalid human content is a controlled request failure without egress', async () => {
    const { api, fetcher } = setup(f.identity);
    await expect(
      api.createIdentity(scope, { ...f.content, voiceUp: [] }, signal()),
    ).rejects.toMatchObject({ problem: { status: 400 } });
    expect(fetcher).not.toHaveBeenCalled();
  });
});

it('approved edit returns a new DRAFT with original lineage; request never writes approval', async () => {
  const { api, fetcher } = setup({ ...f.identity, id: f.id(50), version: 2, revisedFromId: f.i });
  const r = await api.editIdentity(
    scope,
    { ...f.identity, status: 'APPROVED' },
    f.content,
    signal(),
  );
  expect(r.id).toBe(f.id(50));
  expect(r.revisedFromId).toBe(f.i);
  expect(JSON.parse(fetcher.mock.calls[0][1]!.body as string)).not.toHaveProperty('approvedBy');
});
it.each([
  { ...f.job, resultId: null },
  { ...f.job, completedAt: null },
  { ...f.job, resultUrl: `/api/v1/strategies/${f.r}` },
  { ...f.job, status: 'RUNNING', completedAt: null },
])('rejects incoherent terminal/result receipt %#', async (value) => {
  const { api } = setup(value);
  await expect(api.generationJob(f.j, signal())).rejects.toThrow();
});
it('rejects missing mandatory model result and selector changes in admission receipt', async () => {
  expect(() => contract('AnalysisJobResult', { provider: 'fake-local' })).toThrow();
  const { api, fetcher } = setup(f.pdf);
  const p = await api.preview('PDF', f.e, signal());
  fetcher.mockResolvedValue(
    json({ ...f.admission, ...f.pdf, id: f.a, aiConsumptionEnabled: true, latestScanId: f.id(99) }),
  );
  await expect(api.admit(p, signal())).rejects.toThrow();
});
it('copy and create never accept automatic approval or unrelated provider provenance', async () => {
  const { api, fetcher } = setup({
    ...f.identity,
    status: 'APPROVED',
    approvedBy: f.id(8),
    approvedAt: f.time,
  });
  await expect(api.createIdentity(scope, f.content, signal())).rejects.toThrow();
  await expect(api.copyAnalysis(scope, f.r, 0, signal())).rejects.toThrow();
  fetcher.mockResolvedValue(
    json({
      ...f.identity,
      analysisProvenance: { ...f.identity.analysisProvenance, analysisId: f.id(95) },
    }),
  );
  await expect(api.copyAnalysis(scope, f.r, 0, signal())).rejects.toThrow();
});

it('reads a complete worst-case allowed source page including JSON-escaped control characters', async () => {
  const value = '\u0000'.repeat(20000);
  const { api } = setup(
    f.pageOf(
      Array.from({ length: 20 }, (_, n) => ({
        ...f.evidence,
        id: f.id(100 + n),
        status: 'PENDING',
        sourceReference: value,
        checksum: f.hash('TEXT\n' + value),
      })),
    ),
  );
  expect((await api.sources(scope, 0, signal())).content).toHaveLength(20);
});
it('source page response still has a hard byte cap', async () => {
  const { api } = setup('x'.repeat(4194304));
  await expect(api.sources(scope, 0, signal())).rejects.toThrow();
});
