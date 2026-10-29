export type PublicConfig = Readonly<{
  apiBaseUrl: string;
  publishableKey: string;
  jwtTemplate: string;
}>;
export type ConfigResult = { ok: true; value: PublicConfig } | { ok: false };

// Only explicit public values cross the static-build/browser trust boundary.
export function parsePublicConfig(input: Record<string, string | undefined>): ConfigResult {
  try {
    const raw = input.NEXT_PUBLIC_API_BASE_URL ?? '';
    const url = new URL(raw);
    const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
    if (
      raw.length > 2048 ||
      url.username ||
      url.password ||
      url.search ||
      url.hash ||
      !['/api/v1', '/api/v1/'].includes(url.pathname) ||
      !(url.protocol === 'https:' || (url.protocol === 'http:' && local))
    )
      return { ok: false };
    const publishableKey = input.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY ?? '';
    if (!/^pk_(test|live)_[A-Za-z0-9+/=]{8,512}$/.test(publishableKey)) return { ok: false };
    const host = atob(publishableKey.replace(/^pk_(test|live)_/, ''));
    if (!/^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,}\$$/i.test(host)) return { ok: false };
    const jwtTemplate = input.NEXT_PUBLIC_CLERK_JWT_TEMPLATE ?? '';
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(jwtTemplate)) return { ok: false };
    return {
      ok: true,
      value: Object.freeze({
        apiBaseUrl: url.href.replace(/\/$/, ''),
        publishableKey,
        jwtTemplate,
      }),
    };
  } catch {
    return { ok: false };
  }
}
