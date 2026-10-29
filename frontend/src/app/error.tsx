'use client';
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main id="main-content" className="auth-layout">
      <h1>Something went wrong</h1>
      <p role="alert">
        The page could not be loaded. Your session details have not been displayed.
      </p>
      <button onClick={reset}>Try again</button>
    </main>
  );
}
