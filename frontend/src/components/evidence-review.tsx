'use client';
import { useCallback, useState } from 'react';
import type { CadenceApi } from '../lib/api';
import type { Review, ReviewKind } from '../lib/identity-api';
import { reviewKind } from '../lib/identity-api';
import type { EvidenceAdmissionHistory } from '../lib/identity-contract.generated';
import { ActionError, Pages, useAction, useResource, Waiting } from './async-state';
import { Failure } from './failure';
import { Button } from './ui';

export function SourceReview({
  api,
  evidenceId,
  kind,
  changed,
  disabled,
  reviewAvailable = true,
}: {
  api: CadenceApi;
  evidenceId: string;
  kind: ReviewKind;
  changed: () => void;
  disabled: boolean;
  reviewAvailable?: boolean;
}) {
  const action = useAction();
  const [preview, setPreview] = useState<Review | null>(null);
  const [consent, setConsent] = useState(false);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [snapshot, setSnapshot] = useState<{ text: string; id: string } | null>(null);
  const [withdraw, setWithdraw] = useState<EvidenceAdmissionHistory | null>(null);
  const read = useCallback(
    (s: AbortSignal) => api.identity.history(evidenceId, page, s),
    [api, evidenceId, page],
  );
  const history = useResource(read, refresh);
  const invalidate = () => {
    setConsent(false);
    setPreview(null);
    setSnapshot(null);
  };
  function loadPreview() {
    invalidate();
    void action.run((s) => api.identity.preview(kind, evidenceId, s), setPreview);
  }
  return (
    <section className="panel review-panel" aria-label="Source review and history">
      <h3>Exact human review</h3>
      <p>
        Source <code>{evidenceId}</code> ·{' '}
        {reviewAvailable
          ? `${kind} review. No automatic approval or consent.`
          : 'History only: no supported textual preview for this file.'}
      </p>
      {reviewAvailable && (
        <Button disabled={disabled || action.busy} onClick={loadPreview}>
          Load exact snapshot
        </Button>
      )}
      {reviewAvailable && preview && (
        <section aria-label="Exact source snapshot">
          <p>
            Policy <code>{preview.value.policyVersion}</code> · source version{' '}
            {preview.value.sourceVersion}
          </p>
          <pre className="review-text" tabIndex={0} aria-label="Exact review text">
            {preview.value.text}
          </pre>
          <details>
            <summary>Snapshot checksum and provenance</summary>
            <pre className="provenance-text" tabIndex={0} aria-label="Snapshot provenance data">
              {JSON.stringify({ ...preview.value, text: undefined }, null, 2)}
            </pre>
          </details>
          <label className="checkline">
            <input
              type="checkbox"
              checked={consent}
              disabled={action.busy || disabled}
              onChange={(e) => setConsent(e.target.checked)}
            />
            <span>
              I reviewed this exact snapshot and allow its use as AI input under the current
              supported policy. This does not approve an unknown live provider or an Identity.
            </span>
          </label>
          <Button
            className="primary"
            disabled={!consent || disabled || action.busy}
            onClick={() =>
              void action.run(
                (s) => api.identity.admit(preview, s),
                () => {
                  invalidate();
                  setRefresh((n) => n + 1);
                  changed();
                },
                invalidate,
              )
            }
          >
            Confirm this exact snapshot
          </Button>
        </section>
      )}
      {action.busy && <p role="status">Checking current source state…</p>}
      <ActionError error={action.error} />
      <h4>Admission and withdrawal history</h4>
      <p>
        Immutable metadata is historical. Use “Check current snapshot” for fresh
        scan/storage/access/expiry checks. A withdrawal blocks the entire source version and cannot
        be undone here.
      </p>
      {history.state === 'loading' ? (
        <Waiting />
      ) : history.state === 'error' ? (
        <Failure error={history.error} retry={() => setRefresh((n) => n + 1)} />
      ) : (
        <>
          <ul className="receipt-list">
            {history.value.content.map((a) => (
              <li key={a.id}>
                <strong>
                  Version {a.sourceVersion} ·{' '}
                  {a.withdrawn ? 'Withdrawn' : 'Reviewed (current eligibility not assumed)'}
                </strong>
                <p>
                  <code>{a.id}</code>
                  <br />
                  Reviewed by {a.reviewedBy} at <time dateTime={a.reviewedAt}>{a.reviewedAt}</time>
                </p>
                <p>
                  Policy {a.policyVersion} · Brand token {a.brandVersion} · Workspace token{' '}
                  {a.workspaceVersion}
                  <br />
                  Content checksum <code>{a.contentChecksum}</code>
                </p>
                {a.scanExpiresAt && <p>Scan expiry: {a.scanExpiresAt}</p>}
                {a.bindingExpiresAt && <p>Binding expiry: {a.bindingExpiresAt}</p>}
                {a.withdrawnAt && (
                  <p>
                    Withdrawn by {a.withdrawnBy} at {a.withdrawnAt}
                  </p>
                )}
                <div className="actions compact">
                  <Button
                    disabled={a.withdrawn || action.busy || disabled}
                    onClick={() => {
                      invalidate();
                      void action.run(
                        (s) =>
                          api.identity.snapshot(reviewKind(a.policyVersion), evidenceId, a.id, s),
                        (v) => setSnapshot({ id: a.id, text: v.text }),
                      );
                    }}
                  >
                    Check current snapshot
                  </Button>
                  <Button
                    disabled={a.withdrawn || action.busy}
                    onClick={() => {
                      invalidate();
                      setWithdraw(a);
                    }}
                  >
                    Withdraw source version
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          {history.value.content.length === 0 && (
            <p className="notice">No reviewed admissions for this source.</p>
          )}
          <Pages
            label="Admission history pagination"
            page={page}
            last={history.value.last}
            change={(p) => {
              invalidate();
              setWithdraw(null);
              setPage(p);
            }}
          />
        </>
      )}
      {snapshot && (
        <section aria-label="Current admitted snapshot">
          <h4>Current snapshot · {snapshot.id}</h4>
          <pre className="review-text" tabIndex={0}>
            {snapshot.text}
          </pre>
        </section>
      )}
      {withdraw && (
        <section className="notice" aria-label="Confirm source withdrawal">
          <p>
            Withdraw source <code>{evidenceId}</code>, version {withdraw.sourceVersion}, reviewed in
            receipt <code>{withdraw.id}</code>? All admissions of this source version become
            unusable. Original history stays retained.
          </p>
          <Button
            disabled={action.busy}
            onClick={() =>
              void action.run(
                (s) => api.identity.withdraw(evidenceId, withdraw.id, s),
                () => {
                  setWithdraw(null);
                  invalidate();
                  setRefresh((n) => n + 1);
                  changed();
                },
              )
            }
          >
            Confirm withdrawal of version {withdraw.sourceVersion}
          </Button>
          <Button disabled={action.busy} onClick={() => setWithdraw(null)}>
            Keep admission
          </Button>
        </section>
      )}
    </section>
  );
}
