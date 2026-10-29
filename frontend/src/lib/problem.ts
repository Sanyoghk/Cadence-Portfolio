export type ProblemDetails = Readonly<{
  status: number;
  code: string;
  retryable: boolean;
  errors: ReadonlyArray<{ field: string; code: string }>;
}>;
type Json = Record<string, unknown>;
export class ApiError extends Error {
  constructor(readonly problem: ProblemDetails) {
    super(problem.code);
    this.name = 'ApiError';
  }
}
export function parseProblem(v: unknown, status: number): ProblemDetails {
  const o = v && typeof v === 'object' ? (v as Json) : {};
  const code =
    typeof o.code === 'string' && /^[a-z][a-z0-9_]{0,79}$/.test(o.code) ? o.code : 'request_failed';
  const errors = Array.isArray(o.errors)
    ? o.errors.slice(0, 20).flatMap((e) => {
        if (!e || typeof e !== 'object') return [];
        const { field, code } = e;
        return typeof field === 'string' &&
          /^[a-zA-Z]{1,40}$/.test(field) &&
          typeof code === 'string' &&
          /^[a-zA-Z_]{1,40}$/.test(code)
          ? [{ field, code }]
          : [];
      })
    : [];
  return { status, code, retryable: o.retryable === true, errors };
}
