// TEST ENTRY ONLY. Never imported by src/app; Vite serves it on a distinct test port.
// This is a UI contract session, not a Clerk JWT and not accepted by the Java backend.
import { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Onboarding } from '../../src/components/onboarding';
import { SessionGate } from '../../src/components/session-gate';
import { CadenceApi } from '../../src/lib/api';
import { SkipLink } from '../../src/components/skip-link';
import '../../src/app/globals.css';
const state = new URLSearchParams(location.search).get('session');
const api = new CadenceApi(
  `${location.origin}/api/v1`,
  async () => 'isolated-ui-fixture-NOT-A-JWT',
);
function Fixture() {
  const [session, setSession] = useState(0);
  return (
    <>
      <SkipLink />
      {location.pathname === '/sign-in/' ? (
        <main id="main-content">
          <h1>Sign-in redirect target · isolated fixture</h1>
        </main>
      ) : (
        <SessionGate loaded={state !== 'loading'} signedIn={state !== 'signed-out'}>
          <Onboarding
            key={session}
            api={api}
            accountControl={
              state === 'switchable' ? (
                <button
                  aria-label="Switch isolated fixture session"
                  onClick={() => setSession((s) => s + 1)}
                >
                  Session
                </button>
              ) : (
                <span>Test session</span>
              )
            }
          />
        </SessionGate>
      )}
    </>
  );
}
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Fixture />
  </StrictMode>,
);
