'use client';

import { ClerkProvider, SignIn, SignUp, UserButton, useAuth } from '@clerk/react';
import { useMemo, type ReactNode } from 'react';
import { CadenceApi } from '../lib/api';
import { parsePublicConfig, type PublicConfig } from '../lib/config';
import { Onboarding } from './onboarding';
import { SessionGate } from './session-gate';
import { CreatorShell } from './creator-shell';

export function AuthRoot({ screen }: { screen: 'workspaces' | 'sign-in' | 'sign-up' }) {
  const config = parsePublicConfig({
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL,
    NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY: process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY,
    NEXT_PUBLIC_CLERK_JWT_TEMPLATE: process.env.NEXT_PUBLIC_CLERK_JWT_TEMPLATE,
  });
  if (!config.ok)
    return (
      <AuthLayout>
        <h1>Setup required</h1>
        <p>
          Clerk and the Cadence API must be configured before sign-in is available. Contact the
          application owner.
        </p>
        <p>No account or API access is available in this state.</p>
      </AuthLayout>
    );
  return (
    <ClerkProvider
      publishableKey={config.value.publishableKey}
      signInUrl="/sign-in/"
      signUpUrl="/sign-up/"
      signInFallbackRedirectUrl="/workspaces/"
      signUpFallbackRedirectUrl="/workspaces/"
      afterSignOutUrl="/sign-in/"
    >
      {screen === 'workspaces' ? (
        <Session config={config.value} />
      ) : (
        <AuthLayout>
          <h1>{screen === 'sign-in' ? 'Welcome back.' : 'Make space for your ideas.'}</h1>
          <p>Your Brand starts with a clear direction.</p>
          {screen === 'sign-in' ? (
            <SignIn routing="hash" forceRedirectUrl="/workspaces/" />
          ) : (
            <SignUp routing="hash" forceRedirectUrl="/workspaces/" />
          )}
        </AuthLayout>
      )}
    </ClerkProvider>
  );
}
function Session({ config }: { config: PublicConfig }) {
  const { isLoaded, isSignedIn, userId, sessionId, getToken } = useAuth();
  // Session identity is a React key: account/session changes unmount all private state.
  const api = useMemo(
    () => new CadenceApi(config.apiBaseUrl, () => getToken({ template: config.jwtTemplate })),
    [config.apiBaseUrl, config.jwtTemplate, getToken],
  );
  return (
    <SessionGate loaded={isLoaded} signedIn={isSignedIn === true}>
      {isLoaded && isSignedIn && (
        <Onboarding key={`${userId}:${sessionId}`} api={api} accountControl={<UserButton />} />
      )}
    </SessionGate>
  );
}
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <CreatorShell publicPage>
      <main id="main-content" className="auth-layout">
        <section className="panel">{children}</section>
        <p>From your first idea to your next chapter.</p>
      </main>
    </CreatorShell>
  );
}
