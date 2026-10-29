'use client';
import { useCallback, useMemo, useState } from 'react';
import { ApiError, type CadenceApi } from '../lib/api';
import type { ReviewKind, Scope } from '../lib/identity-api';
import type {
  BrandEvidenceResponse,
  BrandIdentityResponse,
  EvidenceRegistrationResponse,
} from '../lib/identity-contract.generated';
import {
  ActionError,
  Pages,
  ScopeDeniedContext,
  useAction,
  useResource,
  Waiting,
} from './async-state';
import { Failure } from './failure';
import { Step1Upload } from './evidence-registration';
import { SourceReview } from './evidence-review';
import { AnalyzingPanel, IdentityGeneration } from './identity-generation';
import { IdentityContent, IdentityEditor } from './identity-editor';
import { Button } from './ui';

export function IdentityWorkbench({
  api,
  workspaceId,
  brandId,
  disabled,
}: {
  api: CadenceApi;
  workspaceId: string;
  brandId: string;
  disabled: boolean;
}) {
  const scope = useMemo(() => ({ workspaceId, brandId }), [workspaceId, brandId]);
  const [denied, setDenied] = useState<unknown>(null);
  const [epoch, setEpoch] = useState(0);
  const action = useAction();
  const deny = useCallback((error: unknown) => {
    if (error instanceof ApiError && [401, 403, 404].includes(error.problem.status))
      setDenied(error);
  }, []);
  if (denied)
    return (
      <section className="panel" aria-label="Identity access unavailable">
        <Failure error={denied} />
        <p>
          Private evidence and Identity state were removed from this view. Revalidate access before
          continuing.
        </p>
        <Button
          disabled={action.busy}
          onClick={() =>
            void action.run(
              (s) => api.brand(workspaceId, brandId, s),
              () => {
                setEpoch((n) => n + 1);
                setDenied(null);
              },
            )
          }
        >
          Revalidate Brand access
        </Button>
        <ActionError error={action.error} />
      </section>
    );
  return (
    <ScopeDeniedContext.Provider value={deny}>
      <IdentityWorkspace key={epoch} api={api} scope={scope} disabled={disabled} />
    </ScopeDeniedContext.Provider>
  );
}

function IdentityWorkspace({
  api,
  scope,
  disabled,
}: {
  api: CadenceApi;
  scope: Scope;
  disabled: boolean;
}) {
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [selected, setSelected] = useState<{
    source: BrandEvidenceResponse;
    kind: ReviewKind;
  } | null>(null);
  const [ingest, setIngest] = useState<string | null>(null);
  const [identityPage, setIdentityPage] = useState(0);
  const [identityRefresh, setIdentityRefresh] = useState(0);
  const [selectedIdentity, setSelectedIdentity] = useState<string | null>(null);
  const [manual, setManual] = useState(false);
  const sourceRead = useCallback(
    (s: AbortSignal) => api.identity.sources(scope, page, s),
    [api, scope, page],
  );
  const sources = useResource(sourceRead, refresh);
  const identityRead = useCallback(
    (s: AbortSignal) => api.identity.identities(scope, identityPage, s),
    [api, scope, identityPage],
  );
  const identities = useResource(identityRead, identityRefresh);
  function registered(r: EvidenceRegistrationResponse, kind: ReviewKind) {
    setIngest(r.jobId);
    setSelected({ source: r.evidence, kind });
    setPage(0);
    setRefresh((n) => n + 1);
  }
  function created(v: BrandIdentityResponse) {
    setManual(false);
    setSelectedIdentity(v.id);
    setIdentityPage(0);
    setIdentityRefresh((n) => n + 1);
  }
  const unsupportedFile =
    selected?.source.type === 'FILE' && selected.source.mediaType !== 'application/pdf';
  const visual = ['image/png', 'image/jpeg'].includes(selected?.source.mediaType ?? '');
  return (
    <section className="identity-workbench" aria-label="Brand Identity workspace">
      <div className="identity-heading">
        <p className="eyebrow">BRAND IDENTITY · EVIDENCE → REVIEW → DRAFT → APPROVAL</p>
        <h2>Your voice starts with your sources.</h2>
        <p>
          Every snapshot, consent and revision comes from the server. AI output is a draft; human
          approval is always separate.
        </p>
      </div>
      {disabled && (
        <p className="notice">
          Archived Brand: history remains readable. New source, consent, generation and edits are
          disabled.
        </p>
      )}
      <details className="source-entry" open>
        <summary>Add source material</summary>
        <Step1Upload api={api} scope={scope} registered={registered} disabled={disabled} />
      </details>
      {ingest && (
        <AnalyzingPanel
          key={ingest}
          api={api}
          id={ingest}
          refreshSources={() => setRefresh((n) => n + 1)}
        />
      )}
      <div className="identity-columns">
        <div className="evidence-stage">
          <section className="panel" aria-label="Registered sources">
            <div className="section-heading">
              <h3>Registered sources</h3>
              <Button onClick={() => setRefresh((n) => n + 1)}>Refresh source list</Button>
            </div>
            {sources.state === 'loading' ? (
              <Waiting />
            ) : sources.state === 'error' ? (
              <Failure error={sources.error} />
            ) : (
              <>
                <ul className="source-list">
                  {sources.value.content.map((s) => (
                    <li key={s.id}>
                      <div>
                        <strong>
                          {s.type === 'FILE'
                            ? (s.originalFileName ?? 'Uploaded file')
                            : s.type === 'URL'
                              ? 'Website textual source'
                              : 'Text or approved social source'}
                        </strong>
                        <p>
                          {s.type} · {s.status} · captured {s.capturedAt}
                          <br />
                          <code>{s.id}</code>
                        </p>
                        <p>{s.warning}</p>
                      </div>
                      <Button
                        onClick={() =>
                          setSelected({
                            source: s,
                            kind: s.type === 'URL' ? 'URL' : s.type === 'FILE' ? 'PDF' : 'TEXT',
                          })
                        }
                      >
                        Inspect source {s.id.slice(0, 8)}
                      </Button>
                    </li>
                  ))}
                </ul>
                {sources.value.content.length === 0 && (
                  <p className="notice">No sources registered for this Brand.</p>
                )}
                <Pages
                  label="Evidence pagination"
                  page={page}
                  last={sources.value.last}
                  change={(p) => {
                    setSelected(null);
                    setPage(p);
                  }}
                />
              </>
            )}
          </section>
          {selected && (
            <section aria-label="Selected source">
              <h3>Selected source</h3>
              <p>
                <code>{selected.source.id}</code>
              </p>
              {unsupportedFile ? (
                <>
                  <p className="notice">
                    {visual
                      ? 'This retained visual source requires a qualified provider. No image observations, OCR text, consent or Identity result is fabricated here.'
                      : 'UNSUPPORTED: file type or textual review provenance is unavailable. Legacy file metadata is not verified AI input.'}
                  </p>
                  <SourceReview
                    key={`${selected.source.id}:history`}
                    api={api}
                    evidenceId={selected.source.id}
                    kind="PDF"
                    disabled={disabled}
                    reviewAvailable={false}
                    changed={() => setRefresh((n) => n + 1)}
                  />
                </>
              ) : (
                <>
                  {selected.source.type === 'TEXT' && (
                    <label>
                      Review route
                      <select
                        value={selected.kind}
                        onChange={(e) =>
                          setSelected({
                            ...selected,
                            kind: e.target.value === 'SOCIAL' ? 'SOCIAL' : 'TEXT',
                          })
                        }
                      >
                        <option value="TEXT">Ordinary text</option>
                        <option value="SOCIAL">Approved social text snapshot</option>
                      </select>
                      <small>
                        Social origin is validated by the backend. No automatic fallback to ordinary
                        TEXT.
                      </small>
                    </label>
                  )}
                  <SourceReview
                    key={`${selected.source.id}:${selected.kind}`}
                    api={api}
                    evidenceId={selected.source.id}
                    kind={selected.kind}
                    changed={() => setRefresh((n) => n + 1)}
                    disabled={disabled}
                  />
                </>
              )}
            </section>
          )}
        </div>
        <div className="identity-stage">
          <IdentityGeneration api={api} scope={scope} created={created} disabled={disabled} />
          <section className="panel" aria-label="Identity version history">
            <div className="section-heading">
              <h3>Identity revisions</h3>
              <div className="actions compact">
                <Button onClick={() => setIdentityRefresh((n) => n + 1)}>
                  Refresh Identity history
                </Button>
                <Button
                  disabled={disabled}
                  onClick={() => {
                    setSelectedIdentity(null);
                    setManual(true);
                  }}
                >
                  Create manual DRAFT
                </Button>
              </div>
            </div>
            {identities.state === 'loading' ? (
              <Waiting />
            ) : identities.state === 'error' ? (
              <Failure error={identities.error} />
            ) : (
              <>
                <ul className="revision-list">
                  {identities.value.content.map((i) => (
                    <li key={i.id}>
                      <strong>
                        Revision {i.version} · {i.status}
                      </strong>
                      <span>Updated {i.updatedAt}</span>
                      <Button
                        onClick={() => {
                          setManual(false);
                          setSelectedIdentity(i.id);
                        }}
                      >
                        Open Identity revision {i.version}
                      </Button>
                    </li>
                  ))}
                </ul>
                {identities.value.content.length === 0 && (
                  <p className="notice">
                    No Identity revisions. Generate from admitted evidence or create a clearly
                    manual draft.
                  </p>
                )}
                <Pages
                  label="Identity revision pagination"
                  page={identityPage}
                  last={identities.value.last}
                  change={(p) => {
                    setManual(false);
                    setSelectedIdentity(null);
                    setIdentityPage(p);
                  }}
                />
              </>
            )}
          </section>
          {manual && (
            <IdentityEditor
              key="manual"
              api={api}
              scope={scope}
              done={created}
              cancel={() => setManual(false)}
              disabled={disabled}
            />
          )}
          {selectedIdentity && (
            <IdentityDetail
              key={`${selectedIdentity}:${identityRefresh}`}
              api={api}
              scope={scope}
              id={selectedIdentity}
              done={created}
              disabled={disabled}
            />
          )}
        </div>
      </div>
    </section>
  );
}

function IdentityDetail({
  api,
  scope,
  id,
  done,
  disabled,
}: {
  api: CadenceApi;
  scope: Scope;
  id: string;
  done: (v: BrandIdentityResponse) => void;
  disabled: boolean;
}) {
  const read = useCallback(
    async (s: AbortSignal) => {
      const [identity, provenance] = await Promise.all([
        api.identity.readIdentity(scope, id, s),
        api.identity.provenance(scope, id, s),
      ]);
      const origin =
        provenance.originIdentityId && provenance.originIdentityId !== identity.id
          ? await api.identity.readIdentity(scope, provenance.originIdentityId, s)
          : identity;
      const analysis = identity.analysisProvenance ?? origin.analysisProvenance;
      if (provenance.analysisId && analysis?.analysisId !== provenance.analysisId)
        throw new ApiError({ status: 0, code: 'invalid_response', retryable: false, errors: [] });
      return { identity, provenance, analysis };
    },
    [api, scope, id],
  );
  const load = useResource(read);
  const [editing, setEditing] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const action = useAction();
  if (load.state === 'loading') return <Waiting />;
  if (load.state === 'error') return <Failure error={load.error} />;
  const { identity: i, provenance: p, analysis } = load.value;
  const withdrawn = p.citations.some((c) => c.withdrawn);
  const demo =
    analysis?.warnings.includes('DEMONSTRATION_ONLY') ||
    /fake|demo/i.test(analysis?.provider ?? '');
  return (
    <section className="panel extracted-panel" aria-label="Identity revision details">
      <p className="eyebrow">EXTRACTED / REVIEWED IDENTITY</p>
      <h3>
        Revision {i.version} · {i.status}
      </h3>
      <p>
        Identity <code>{i.id}</code> · lockVersion {i.lockVersion}
        <br />
        Created {i.createdAt} · updated {i.updatedAt}
      </p>
      {i.revisedFromId && (
        <p>
          Branched from <code>{i.revisedFromId}</code>. Original approved history remains unchanged.
        </p>
      )}
      {i.approvedAt && (
        <p>
          Approved by {i.approvedBy} at {i.approvedAt}
        </p>
      )}
      {demo && (
        <p className="notice">
          DEMONSTRATION ORIGIN — local/test fake, not live AI inference. Later human edits do not
          become new provider output.
        </p>
      )}
      {analysis && (
        <details>
          <summary>Original analysis receipt</summary>
          <pre
            className="provenance-text"
            tabIndex={0}
            aria-label="Original analysis provenance data"
          >
            {JSON.stringify(analysis, null, 2)}
          </pre>
        </details>
      )}
      <IdentityContent content={i.content} />
      <h4>Evidence citations and inherited lineage</h4>
      <p>
        Origin <code>{p.originIdentityId ?? 'No evidence-generation origin'}</code> · analysis{' '}
        <code>{p.analysisId ?? 'None'}</code>
      </p>
      <ul className="citation-list">
        {p.citations.map((c, index) => (
          <li key={index}>
            <p>
              Source <code>{c.evidenceId}</code> · version {c.sourceVersion} ·{' '}
              {c.withdrawn ? 'WITHDRAWN' : 'historical citation'}
              <br />
              Admission <code>{c.admissionId}</code> · field {c.fieldPath ?? 'text'}
              <br />
              Artifact {c.artifactId ?? 'none'} · original scan {c.originalScanId ?? 'none'} ·
              latest scan {c.latestScanId ?? 'none'}
              <br />
              URL snapshot {c.urlSnapshotId ?? 'none'} · social snapshot{' '}
              {c.socialSnapshotId ?? 'none'}
            </p>
            <blockquote>{c.quote}</blockquote>
          </li>
        ))}
      </ul>
      {p.citations.length === 0 && (
        <p>
          No evidence citations recorded. Manual/legacy history does not gain invented provenance.
        </p>
      )}
      {editing ? (
        <IdentityEditor
          api={api}
          scope={scope}
          identity={i}
          done={done}
          cancel={() => setEditing(false)}
          disabled={disabled}
        />
      ) : (
        <>
          <Button
            disabled={disabled || action.busy}
            onClick={() => {
              setConfirmed(false);
              setEditing(true);
            }}
          >
            {i.status === 'DRAFT' ? 'Edit DRAFT' : 'Create new DRAFT revision'}
          </Button>
          {i.status === 'DRAFT' && (
            <>
              <label className="checkline">
                <input
                  type="checkbox"
                  checked={confirmed}
                  disabled={disabled || action.busy || withdrawn}
                  onChange={(e) => setConfirmed(e.target.checked)}
                />
                <span>
                  I reviewed the committed content above, revision {i.version} / token{' '}
                  {i.lockVersion}, and approve this exact Identity.
                </span>
              </label>
              <Button
                className="primary"
                disabled={disabled || action.busy || !confirmed || withdrawn}
                onClick={() =>
                  void action.run(
                    (s) => api.identity.approve(scope, i, s),
                    done,
                    () => setConfirmed(false),
                  )
                }
              >
                Approve committed Identity
              </Button>
            </>
          )}
          {withdrawn && (
            <p className="notice">
              Supporting source version was withdrawn. Approval/new consumption is unavailable;
              original history is retained.
            </p>
          )}
        </>
      )}
      <ActionError error={action.error} />
    </section>
  );
}
