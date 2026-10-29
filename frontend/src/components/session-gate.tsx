'use client';
import { useEffect, type ReactNode } from 'react';
export function SessionGate({
  loaded,
  signedIn,
  children,
}: {
  loaded: boolean;
  signedIn: boolean;
  children: ReactNode;
}) {
  useEffect(() => {
    if (loaded && !signedIn) window.location.replace('/sign-in/');
  }, [loaded, signedIn]);
  if (!loaded || !signedIn)
    return (
      <main id="main-content" className="auth-layout">
        <p role="status">{loaded ? 'Redirecting to sign in…' : 'Loading secure sign-in…'}</p>
      </main>
    );
  return children;
}
