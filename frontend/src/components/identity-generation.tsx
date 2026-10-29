'use client';
import { useCallback, useEffect, useState } from 'react';
import { ApiError, type CadenceApi } from '../lib/api';
import type { Scope } from '../lib/identity-api';
import type {
  AiGenerationJob,
  BrandIdentityResponse,
  JobResponse,
} from '../lib/identity-contract.generated';
import { ActionError, useAction, useResource, useScopeDenied, Waiting } from './async-state';
import { Failure } from './failure';
import { Button } from './ui';

export function AnalyzingPanel({
  api,
  id,
  generation = false,
  refreshSources,
  created,
  scope,
}: {
  api: CadenceApi;
  id: string;
  generation?: boolean;
  refreshSources?: () => void;
  created?: (v: BrandIdentityResponse) => void;
  scope?: Scope;
}) {
  const deny = useScopeDenied();
  const [job, setJob] = useState<AiGenerationJob | JobResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [paused, setPaused] = useState(false);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let count = 0;
    async function poll() {
      try {
        const value = generation
          ? await api.identity.generationJob(id, controller.signal)
          : await api.identity.ingestJob(id, controller.signal);
        if (controller.signal.aborted) return;
        setJob(value);
        count++;
        const active = generation
          ? ['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(value.status)
          : ['PENDING', 'RUNNING', 'FAILED'].includes(value.status);
        if (active && count < 30) timer = setTimeout(() => void poll(), 2000);
        else if (active) setPaused(true);
      } catch (e) {
        if (!controller.signal.aborted) {
          setError(e);
          setPaused(true);
          deny?.(e);
        }
      }
    }
    void poll();
    return () => {
      controller.abort();
      if (timer) clearTimeout(timer);
    };
  }, [api, id, generation, refresh, deny]);
  return (
    <section
      className="panel"
      aria-label={generation ? 'Identity generation Job' : 'Source acquisition Job'}
    >
      <p className="eyebrow">{generation ? 'ANALYSIS STATUS' : 'ACQUISITION'}</p>
      <h3>{generation ? 'Identity analysis' : 'Source acquisition status'}</h3>
      <p>
        Job <code>{id}</code>
      </p>
      {job ? (
        <>
          <p role="status">
            Status: <strong>{job.status}</strong> · attempt {job.attempts}
          </p>
          {'errorCode' in job && job.errorCode && <p>Controlled outcome: {job.errorCode}</p>}
          {job.status === 'OUTCOME_UNKNOWN' && (
            <p className="notice">
              The provider outcome is unknown. No successful result or zero cost is assumed. Do not
              blindly resubmit.
            </p>
          )}
          {!generation && job.status === 'SUCCEEDED' && (
            <p>Acquisition succeeded. This does not admit the source to AI.</p>
          )}
          {job.status === 'SUPERSEDED' && (
            <p>Source or access changed. This operation did not publish a current result.</p>
          )}
        </>
      ) : (
        <Waiting />
      )}
      {paused && (
        <p>
          Automatic polling paused after an error or bounded polling window. No outcome was
          inferred.
        </p>
      )}
      {error !== null && <Failure error={error} />}
      <div className="actions compact">
        <Button
          onClick={() => {
            setError(null);
            setJob(null);
            setPaused(false);
            setRefresh((n) => n + 1);
          }}
        >
          Refresh Job status
        </Button>
        {refreshSources && <Button onClick={refreshSources}>Refresh registered sources</Button>}
      </div>
      {generation &&
        !error &&
        scope &&
        created &&
        job &&
        'resultId' in job &&
        job.status === 'SUCCEEDED' &&
        job.resultId && (
          <ExtractedPanel
            key={job.resultId}
            api={api}
            scope={scope}
            resultId={job.resultId}
            created={created}
          />
        )}
    </section>
  );
}

function ExtractedPanel({
  api,
  scope,
  resultId,
  created,
}: {
  api: CadenceApi;
  scope: Scope;
  resultId: string;
  created: (v: BrandIdentityResponse) => void;
}) {
  const read = useCallback(
    (s: AbortSignal) => api.identity.analysisResult(resultId, s),
    [api, resultId],
  );
  const load = useResource(read);
  const action = useAction();
  const [uncertain, setUncertain] = useState(false);
  const [copied, setCopied] = useState(false);
  if (load.state === 'loading') return <Waiting />;
  if (load.state === 'error') return <Failure error={load.error} />;
  const r = load.value;
  const demo = r.warnings.includes('DEMONSTRATION_ONLY') || /fake|demo/i.test(r.provider);
  return (
    <section aria-label="Extracted Identity result">
      <h4>Review the generated result</h4>
      <p className="notice">
        {demo
          ? 'DEMONSTRATION OUTPUT — deterministic local/test fake, not live AI inference.'
          : 'Provider-reported output — not approved Identity content.'}{' '}
        Human review is required.
      </p>
      <dl>
        <dt>Provider</dt>
        <dd>{r.provider}</dd>
        <dt>Model</dt>
        <dd>{r.modelVersion}</dd>
        <dt>Prompt</dt>
        <dd>{r.promptVersion}</dd>
      </dl>
      <pre className="provenance-text" tabIndex={0} aria-label="Generated result data">
        {JSON.stringify(
          {
            palette: r.palette,
            fonts: r.fonts,
            voiceUp: r.voiceUp,
            voiceDown: r.voiceDown,
            sampleCaption: r.sampleCaption,
            warnings: r.warnings,
          },
          null,
          2,
        )}
      </pre>
      <h4>Exact citations</h4>
      <ul className="citation-list">
        {r.citations.map((c, i) => (
          <li key={i}>
            <p>
              Admission <code>{c.admissionId}</code> · {c.fieldPath ?? 'exact text substring'}
            </p>
            <blockquote>{c.quote}</blockquote>
          </li>
        ))}
      </ul>
      <Button
        className="primary"
        disabled={action.busy || uncertain || copied}
        onClick={() =>
          void action.run(
            async (s) => {
              const brand = await api.brand(scope.workspaceId, scope.brandId, s);
              return api.identity.copyAnalysis(scope, resultId, brand.lockVersion, s);
            },
            (identity) => {
              setCopied(true);
              created(identity);
            },
            (e) => {
              if (e instanceof ApiError && e.problem.status === 0) setUncertain(true);
            },
          )
        }
      >
        Create Identity DRAFT from this result
      </Button>
      <p>Create a reviewable draft from this completed analysis. Approval is a separate action.</p>
      {uncertain && (
        <p role="status">
          Copy outcome is uncertain. Refresh Identity history before considering another copy; it
          may already exist.
        </p>
      )}
      <ActionError error={action.error} />
    </section>
  );
}

export function IdentityGeneration({
  api,
  scope,
  created,
  disabled,
}: {
  api: CadenceApi;
  scope: Scope;
  created: (v: BrandIdentityResponse) => void;
  disabled: boolean;
}) {
  const action = useAction();
  const [capture, setCapture] = useState<{ version: number; key: string } | null>(null);
  const [job, setJob] = useState<AiGenerationJob | null>(null);
  return (
    <section className="panel" aria-label="Generate Identity">
      <h3>From reviewed sources to a draft.</h3>
      <p>
        Use up to five currently eligible sources that you have reviewed and admitted. Changed,
        withdrawn, expired or unavailable material cannot support a new result.
      </p>
      <p>
        Model availability is checked when you request generation. An unavailable model produces no
        draft. Demo results are clearly labeled.
      </p>
      {!job && (
        <Button
          className="primary"
          disabled={disabled || action.busy}
          onClick={() =>
            void action.run(async (s) => {
              let request = capture;
              if (!request) {
                const brand = await api.brand(scope.workspaceId, scope.brandId, s);
                request = { version: brand.lockVersion, key: crypto.randomUUID() };
                setCapture(request);
              }
              return api.identity.submit(scope, request.version, request.key, s);
            }, setJob)
          }
        >
          {capture ? 'Retry same generation submission' : 'Generate from admitted evidence'}
        </Button>
      )}
      {action.busy && <p role="status">Submitting durable generation…</p>}
      <ActionError error={action.error} />
      {capture && !job && (
        <p>
          Same request/key is retained for a coherent retry. No automatic retry or successful
          operation is assumed.
        </p>
      )}
      {job && (
        <AnalyzingPanel
          key={job.jobId}
          api={api}
          id={job.jobId}
          generation
          scope={scope}
          created={created}
        />
      )}
    </section>
  );
}
