import { ApiError } from '../lib/problem';
export function Failure({ error, retry }: { error: unknown; retry?: () => void }) {
  const p = error instanceof ApiError ? error.problem : undefined;
  const message =
    p?.status === 401
      ? 'Your session has expired. Sign in again.'
      : p?.status === 403
        ? 'Access denied. An active Workspace OWNER membership is required.'
        : p?.status === 404
          ? 'This resource is unavailable. It may have been removed or access changed.'
          : p?.status === 409
            ? 'The snapshot, version, source or context changed. Load fresh server state and review again; consent was not repeated.'
            : p?.status === 400
              ? 'Check the form fields and try again.'
              : p?.status === 429
                ? 'A history, capacity or budget limit was reached. No additional operation was confirmed.'
                : p?.status === 503
                  ? 'Storage, scan, account binding or model capability is unavailable. No successful result was inferred.'
                  : p?.code === 'invalid_response'
                    ? 'The API returned an unexpected response. No data was accepted.'
                    : 'We could not confirm the request. Refresh the list before submitting again to avoid duplicates.';
  return (
    <div className="notice error" role="alert">
      <p>{message}</p>
      {p?.status === 401 ? (
        <a href="/sign-in/">Sign in</a>
      ) : (
        retry && (
          <button type="button" onClick={retry}>
            Refresh
          </button>
        )
      )}
    </div>
  );
}
