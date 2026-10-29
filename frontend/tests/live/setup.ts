import { clerkSetup } from '@clerk/testing/playwright';
export default async function setup() {
  const required = [
    'CLERK_PUBLISHABLE_KEY',
    'CLERK_SECRET_KEY',
    'CADENCE_E2E_EMAIL',
    'CADENCE_E2E_PASSWORD',
    'CADENCE_LIVE_WEB_URL',
  ];
  if (required.some((name) => !process.env[name]))
    throw new Error(
      'Live Clerk qualification blocked: configure development Clerk keys, dedicated test user and running frontend/backend. See docs/frontend-foundation.md.',
    );
  if (
    !process.env.CLERK_PUBLISHABLE_KEY!.startsWith('pk_test_') ||
    !process.env.CLERK_SECRET_KEY!.startsWith('sk_test_')
  )
    throw new Error('Live test permits only a dedicated development Clerk instance.');
  const url = new URL(process.env.CADENCE_LIVE_WEB_URL!);
  if (
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    url.pathname !== '/' ||
    !(
      url.protocol === 'https:' ||
      (url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname))
    )
  )
    throw new Error('Invalid live test origin');
  await clerkSetup();
}
