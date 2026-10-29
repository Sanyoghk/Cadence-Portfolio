import { describe, expect, it, vi } from 'vitest';
import { ApiError, CadenceApi, parseProblem } from '../../src/lib/api';
const a = '00000000-0000-0000-0000-000000000001';
const b = '00000000-0000-0000-0000-000000000002';
const item = { id: a, name: 'Studio', industry: 'Design', status: 'ACTIVE', lockVersion: 0 };
const brand = {
  id: b,
  workspaceId: a,
  name: 'Brand',
  status: 'ACTIVE',
  lockVersion: 0,
  websiteUrl: null,
  instagramHandle: null,
  tiktokHandle: null,
};
const paged = (content: unknown[]) => ({
  content,
  page: 0,
  size: 20,
  totalPages: 1,
  totalElements: content.length,
  first: true,
  last: true,
});
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } });
const signal = () => new AbortController().signal;
describe('typed API trust boundary', () => {
  it('uses explicit Bearer, no cookies/cache/redirects and bounded pagination', async () => {
    const transport = vi.fn<typeof fetch>().mockResolvedValue(json(paged([item])));
    const api = new CadenceApi(
      'https://api.test/api/v1',
      async () => 'fixture-not-a-real-jwt',
      transport,
    );
    expect((await api.workspaces(0, signal())).content).toEqual([item]);
    expect(transport).toHaveBeenCalledWith(
      'https://api.test/api/v1/workspaces?page=0&size=20',
      expect.objectContaining({
        credentials: 'omit',
        redirect: 'error',
        cache: 'no-store',
        headers: expect.objectContaining({ Authorization: 'Bearer fixture-not-a-real-jwt' }),
      }),
    );
  });
  it('does not send client role/owner fields or preserve extra response secrets', async () => {
    const transport = vi
      .fn<typeof fetch>()
      .mockResolvedValue(json({ ...item, secret: 'internal' }, 201));
    const api = new CadenceApi('https://api.test/api/v1', async () => 'fixture', transport);
    const result = await api.createWorkspace(
      { name: 'Studio', industry: 'Design', role: 'ADMIN' } as Parameters<
        CadenceApi['createWorkspace']
      >[0],
      signal(),
    );
    expect(result).not.toHaveProperty('secret');
    expect(JSON.parse(transport.mock.calls[0][1]!.body as string)).toEqual({
      name: 'Studio',
      industry: 'Design',
    });
  });
  it.each([400, 401, 403, 404, 409, 503])(
    'preserves controlled status %s, drops private detail/instance',
    async (status) => {
      const api = new CadenceApi(
        'https://api.test/api/v1',
        async () => 'fixture',
        vi.fn<typeof fetch>().mockResolvedValue(
          json(
            {
              status: 200,
              code: 'access_denied',
              detail: 'private SQL',
              instance: 'https://secret',
            },
            status,
          ),
        ),
      );
      await expect(api.workspaces(0, signal())).rejects.toMatchObject({
        problem: { status, code: 'access_denied' },
      });
      expect(parseProblem({ code: '<script>', detail: 'secret' }, status)).toEqual({
        status,
        code: 'request_failed',
        retryable: false,
        errors: [],
      });
    },
  );
  it('missing token fails before network', async () => {
    const transport = vi.fn<typeof fetch>();
    await expect(
      new CadenceApi('https://api.test/api/v1', async () => null, transport).workspaces(
        0,
        signal(),
      ),
    ).rejects.toMatchObject({ problem: { status: 401 } });
    expect(transport).not.toHaveBeenCalled();
  });
  it('aborted token retrieval cannot subsequently send a request', async () => {
    let resolve!: (token: string) => void;
    const transport = vi.fn<typeof fetch>();
    const controller = new AbortController();
    const request = new CadenceApi(
      'https://api.test/api/v1',
      () =>
        new Promise((r) => {
          resolve = r;
        }),
      transport,
    ).workspaces(0, controller.signal);
    controller.abort();
    resolve('fixture');
    await expect(request).rejects.toBeDefined();
    expect(transport).not.toHaveBeenCalled();
  });
  it.each(['../../secret', a + '?role=OWNER', '<script>'])(
    'rejects path injection %s',
    async (value) => {
      const transport = vi.fn<typeof fetch>();
      await expect(
        new CadenceApi('https://api.test/api/v1', async () => 'fixture', transport).brands(
          value,
          0,
          signal(),
        ),
      ).rejects.toBeInstanceOf(ApiError);
      expect(transport).not.toHaveBeenCalled();
    },
  );
  it.each([-1, 10001, 1.5, Number.MAX_SAFE_INTEGER])('rejects page %s', async (value) => {
    const transport = vi.fn<typeof fetch>();
    await expect(
      new CadenceApi('https://api.test/api/v1', async () => 'fixture', transport).workspaces(
        value,
        signal(),
      ),
    ).rejects.toBeInstanceOf(ApiError);
    expect(transport).not.toHaveBeenCalled();
  });
  it('rejects foreign list/detail and wrong resource ID even in a successful response', async () => {
    for (const body of [
      paged([{ ...brand, workspaceId: b }]),
      { ...brand, workspaceId: b },
      { ...brand, id: a },
    ]) {
      const api = new CadenceApi(
        'https://api.test/api/v1',
        async () => 'fixture',
        vi.fn<typeof fetch>().mockResolvedValue(json(body)),
      );
      await expect(
        'content' in body ? api.brands(a, 0, signal()) : api.brand(a, b, signal()),
      ).rejects.toMatchObject({ problem: { code: 'invalid_response' } });
    }
  });
  it.each([
    paged(new Array(21).fill(item)),
    { ...paged([item]), page: 1 },
    { ...paged([item]), size: 100 },
    paged([{ ...item, lockVersion: -1 }]),
    paged([{ ...item, status: 'ADMIN' }]),
  ])('rejects malformed/beyond-bound data', async (body) => {
    await expect(
      new CadenceApi(
        'https://api.test/api/v1',
        async () => 'fixture',
        vi.fn<typeof fetch>().mockResolvedValue(json(body)),
      ).workspaces(0, signal()),
    ).rejects.toBeInstanceOf(ApiError);
  });
  it('bounds response bytes and rejects invalid encoding/HTML', async () => {
    for (const response of [
      json('a'.repeat(512 * 1024)),
      new Response('<html>secret', { headers: { 'content-type': 'text/html' } }),
      new Response(new Uint8Array([0xff]), { headers: { 'content-type': 'application/json' } }),
    ]) {
      await expect(
        new CadenceApi(
          'https://api.test/api/v1',
          async () => 'fixture',
          vi.fn<typeof fetch>().mockResolvedValue(response),
        ).workspaces(0, signal()),
      ).rejects.toBeInstanceOf(ApiError);
    }
  });
  it('never automatically retries uncertain POST', async () => {
    const transport = vi.fn<typeof fetch>().mockRejectedValue(new Error('secret token upstream'));
    await expect(
      new CadenceApi('https://api.test/api/v1', async () => 'fixture', transport).createBrand(
        a,
        brand,
        signal(),
      ),
    ).rejects.toMatchObject({ message: 'connection_uncertain' });
    expect(transport).toHaveBeenCalledTimes(1);
  });
  it('malformed JSON preserves an error status but cannot become successful typed data', async () => {
    for (const status of [200, 401, 403, 404]) {
      const response = new Response('{broken', {
        status,
        headers: { 'content-type': 'application/json' },
      });
      await expect(
        new CadenceApi(
          'https://api.test/api/v1',
          async () => 'fixture',
          vi.fn<typeof fetch>().mockResolvedValue(response),
        ).workspaces(0, signal()),
      ).rejects.toMatchObject({
        problem: status === 200 ? { code: 'invalid_response' } : { status },
      });
    }
  });
});
