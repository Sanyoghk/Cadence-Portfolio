import { ApiError, parseProblem } from './problem';

export type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH';
  body?: object;
  key?: string;
  maxBytes?: 524288 | 4194304;
};
async function boundedJson(response: Response, maximum: number): Promise<unknown> {
  if (!response.body) throw Error('Missing body');
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let bytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      bytes += value.length;
      if (bytes > maximum) throw Error('Response limit');
      chunks.push(value);
    }
    const all = new Uint8Array(bytes);
    let offset = 0;
    for (const chunk of chunks) {
      all.set(chunk, offset);
      offset += chunk.length;
    }
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(all));
  } finally {
    await reader.cancel().catch(() => undefined);
    reader.releaseLock();
  }
}

/** Shared explicit Bearer boundary. Callers build fixed paths; no response URL is followed. */
export class ApiTransport {
  constructor(
    private readonly base: string,
    private readonly getToken: () => Promise<string | null>,
    private readonly transport: typeof fetch = (input, init) => fetch(input, init),
  ) {}
  async json(path: string, signal: AbortSignal, options: RequestOptions = {}): Promise<unknown> {
    if (options.key && !/^[A-Za-z0-9._:-]{1,128}$/.test(options.key))
      throw new ApiError({ status: 400, code: 'invalid_request', retryable: false, errors: [] });
    const combined = AbortSignal.any([signal, AbortSignal.timeout(15000)]);
    try {
      combined.throwIfAborted();
      const token = await new Promise<string | null>((resolve, reject) => {
        const abort = () => reject(combined.reason);
        combined.addEventListener('abort', abort, { once: true });
        this.getToken()
          .then(resolve, reject)
          .finally(() => combined.removeEventListener('abort', abort));
      });
      combined.throwIfAborted();
      if (!token)
        throw new ApiError({
          status: 401,
          code: 'authentication_required',
          retryable: false,
          errors: [],
        });
      const multipart = options.body instanceof FormData;
      const response = await this.transport(this.base + path, {
        method: options.method ?? (options.body ? 'POST' : 'GET'),
        credentials: 'omit',
        cache: 'no-store',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
        signal: combined,
        headers: {
          Accept: 'application/json, application/problem+json',
          Authorization: `Bearer ${token}`,
          ...(options.body && !multipart ? { 'Content-Type': 'application/json' } : {}),
          ...(options.key ? { 'Idempotency-Key': options.key } : {}),
        },
        ...(options.body
          ? { body: multipart ? (options.body as FormData) : JSON.stringify(options.body) }
          : {}),
      });
      const media = response.headers.get('content-type')?.split(';')[0];
      if (!['application/json', 'application/problem+json'].includes(media ?? '')) {
        if (!response.ok) throw new ApiError(parseProblem(null, response.status));
        throw new ApiError({ status: 0, code: 'invalid_response', retryable: false, errors: [] });
      }
      let value: unknown;
      try {
        value = await boundedJson(response, options.maxBytes ?? 524288);
      } catch {
        if (!response.ok) throw new ApiError(parseProblem(null, response.status));
        throw new ApiError({ status: 0, code: 'invalid_response', retryable: false, errors: [] });
      }
      if (!response.ok) throw new ApiError(parseProblem(value, response.status));
      return value;
    } catch (e) {
      if (signal.aborted) throw signal.reason;
      if (e instanceof ApiError) throw e;
      throw new ApiError({ status: 0, code: 'connection_uncertain', retryable: false, errors: [] });
    }
  }
}
