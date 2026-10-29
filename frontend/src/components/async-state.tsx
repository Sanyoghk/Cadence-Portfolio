'use client';
import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Failure } from './failure';
export const ScopeDeniedContext = createContext<((error: unknown) => void) | null>(null);
export const useScopeDenied = () => useContext(ScopeDeniedContext);

export function useAction() {
  const deny = useScopeDenied();
  const current = useRef<AbortController | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  useEffect(() => () => current.current?.abort(), []);
  async function run<T>(
    call: (signal: AbortSignal) => Promise<T>,
    success: (value: T) => void,
    failed?: (error: unknown) => void,
  ) {
    if (current.current) return;
    const controller = new AbortController();
    current.current = controller;
    setBusy(true);
    setError(null);
    try {
      const result = await call(controller.signal);
      if (!controller.signal.aborted) success(result);
    } catch (e) {
      if (!controller.signal.aborted) {
        setError(e);
        deny?.(e);
        failed?.(e);
      }
    } finally {
      if (!controller.signal.aborted) {
        current.current = null;
        setBusy(false);
      }
    }
  }
  return { run, busy, error, clear: () => setError(null) };
}
type Load<T> =
  { state: 'loading' } | { state: 'error'; error: unknown } | { state: 'ready'; value: T };
export function useResource<T>(read: (signal: AbortSignal) => Promise<T>, revision = 0): Load<T> {
  const deny = useScopeDenied();
  const [state, setState] = useState<{ read: typeof read; revision: number; load: Load<T> }>({
    read,
    revision,
    load: { state: 'loading' },
  });
  useEffect(() => {
    const controller = new AbortController();
    read(controller.signal).then(
      (value) => {
        if (!controller.signal.aborted)
          setState({ read, revision, load: { state: 'ready', value } });
      },
      (error) => {
        if (!controller.signal.aborted) {
          setState({ read, revision, load: { state: 'error', error } });
          deny?.(error);
        }
      },
    );
    return () => controller.abort();
  }, [read, deny, revision]);
  return state.read === read && state.revision === revision ? state.load : { state: 'loading' };
}
export function ActionError({ error }: { error: unknown }) {
  return error ? <Failure error={error} /> : null;
}
export function Waiting() {
  return (
    <p role="status" className="notice">
      Reading current server state…
    </p>
  );
}
export function Pages({
  page,
  last,
  change,
  label,
}: {
  page: number;
  last: boolean;
  change: (n: number) => void;
  label: string;
}) {
  return (
    <nav className="pager" aria-label={label}>
      <button type="button" disabled={page === 0} onClick={() => change(page - 1)}>
        Previous
      </button>
      <span>Page {page + 1}</span>
      <button type="button" disabled={last || page >= 10000} onClick={() => change(page + 1)}>
        Next
      </button>
    </nav>
  );
}
