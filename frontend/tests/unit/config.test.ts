import { describe, expect, it } from 'vitest';
import { parsePublicConfig } from '../../src/lib/config';
const good = {
  NEXT_PUBLIC_API_BASE_URL: 'https://api.example.com/api/v1',
  NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY: `pk_test_${btoa('existing.clerk.accounts.dev$')}`,
  NEXT_PUBLIC_CLERK_JWT_TEMPLATE: 'cadence-api',
};
describe('public configuration boundary', () => {
  it('accepts HTTPS and explicit loopback HTTP, strips a trailing slash', () => {
    for (const base of [
      'https://api.example.com/api/v1',
      'http://localhost:8080/api/v1/',
      'http://127.0.0.1:8080/api/v1',
      'http://[::1]:8080/api/v1',
    ]) {
      const result = parsePublicConfig({ ...good, NEXT_PUBLIC_API_BASE_URL: base });
      expect(result.ok).toBe(true);
      if (result.ok) expect(result.value.apiBaseUrl.endsWith('/')).toBe(false);
    }
  });
  it.each([
    'http://api.example.com/api/v1',
    'https://u:secret@api.example.com/api/v1',
    'https://api.example.com/api/v1?token=x',
    'https://api.example.com/api/v1#x',
    'javascript:alert(1)',
    'https://api.example.com/private',
    '//api.example.com/api/v1',
  ])('rejects unsafe API URL %s', (value) => {
    expect(parsePublicConfig({ ...good, NEXT_PUBLIC_API_BASE_URL: value })).toEqual({ ok: false });
  });
  it('fails closed for missing/placeholder/secret key, bad host/template', () => {
    expect(parsePublicConfig({})).toEqual({ ok: false });
    for (const key of [
      'sk_test_never_public',
      'pk_test_REPLACE_FROM_EXISTING_CLERK_INSTANCE',
      `pk_test_${btoa('evil.test/path$')}`,
    ]) {
      expect(parsePublicConfig({ ...good, NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY: key })).toEqual({
        ok: false,
      });
    }
    expect(parsePublicConfig({ ...good, NEXT_PUBLIC_CLERK_JWT_TEMPLATE: '../../token' })).toEqual({
      ok: false,
    });
  });
});
