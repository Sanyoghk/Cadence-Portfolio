'use client';
import { useState, type FormEvent } from 'react';
import type { CadenceApi } from '../lib/api';
import type { EvidenceRegistrationResponse } from '../lib/identity-contract.generated';
import type { ReviewKind, Scope } from '../lib/identity-api';
import { useAction, ActionError } from './async-state';
import { Button, OrbitStar } from './ui';

export function Step1Upload({
  api,
  scope,
  registered,
  disabled,
}: {
  api: CadenceApi;
  scope: Scope;
  registered: (v: EvidenceRegistrationResponse, kind: ReviewKind) => void;
  disabled: boolean;
}) {
  const action = useAction();
  const [file, setFile] = useState<File | null>(null);
  function register(e: FormEvent<HTMLFormElement>, type: 'TEXT' | 'URL') {
    e.preventDefault();
    const target = e.currentTarget;
    const value = String(new FormData(target).get('value') ?? '');
    void action.run(
      (s) => api.identity.register(scope, type, value, s),
      (r) => {
        target.reset();
        registered(r, type);
      },
    );
  }
  return (
    <section aria-label="Register evidence">
      <div className="identity-section-title">
        <div>
          <p className="eyebrow">01 — SOURCES</p>
          <h3>
            Let’s read <em>your</em> Brand.
          </h3>
          <p>
            Add original material, then review the exact server snapshot. Registration and a CLEAN
            scan are not permission for AI.
          </p>
        </div>
      </div>
      <div className="evidence-input-grid">
        <div className="upload-box">
          <OrbitStar />
          <h4>Bring your source material.</h4>
          <p>
            PDF, PNG or JPEG · up to 10 MiB. The server validates and privately scans bytes. SVG/ZIP
            are unsupported.
          </p>
          <label>
            Evidence file
            <input
              type="file"
              accept="application/pdf,image/png,image/jpeg"
              disabled={disabled || action.busy}
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
          <Button
            disabled={disabled || action.busy || !file}
            onClick={() => {
              if (file)
                void action.run(
                  (s) => api.identity.upload(scope, file, s),
                  (r) => {
                    setFile(null);
                    registered(r, 'PDF');
                  },
                );
            }}
          >
            Upload source
          </Button>
          <p className="notice">
            Images and screenshots: <strong>REQUIRES_PROVIDER</strong>. Retained bytes are not
            extracted text or a generated Identity. PDF supports reviewed extracted text.
          </p>
        </div>
        <div>
          <form aria-label="Register text source" onSubmit={(e) => register(e, 'TEXT')}>
            <label>
              Source text
              <textarea
                name="value"
                required
                maxLength={20000}
                rows={5}
                disabled={disabled || action.busy}
              />
            </label>
            <Button type="submit" className="primary" disabled={disabled || action.busy}>
              Register text
            </Button>
          </form>
          <details>
            <summary>Add a website URL</summary>
            <form aria-label="Register URL source" onSubmit={(e) => register(e, 'URL')}>
              <label>
                Public website URL
                <input
                  name="value"
                  type="url"
                  required
                  maxLength={2048}
                  disabled={disabled || action.busy}
                />
              </label>
              <p>
                Only public website URLs are eligible. You review the captured snapshot without
                loading the website again.
              </p>
              <Button type="submit" disabled={disabled || action.busy}>
                Register URL
              </Button>
            </form>
          </details>
          <details>
            <summary>Capture approved social text</summary>
            <form
              aria-label="Register social source"
              onSubmit={(e) => {
                e.preventDefault();
                const p = String(new FormData(e.currentTarget).get('provider'));
                if (p === 'INSTAGRAM' || p === 'TIKTOK')
                  void action.run(
                    (s) => api.identity.social(scope, p, s),
                    (r) => registered(r, 'SOCIAL'),
                  );
              }}
            >
              <label>
                Connected platform
                <select name="provider" disabled={disabled || action.busy}>
                  <option value="INSTAGRAM">Instagram</option>
                  <option value="TIKTOK">TikTok</option>
                </select>
              </label>
              <p>
                The backend chooses the approved account binding. Missing live OAuth/account
                qualification remains unavailable; no fake credentials or client account IDs.
              </p>
              <Button type="submit" disabled={disabled || action.busy}>
                Capture social text
              </Button>
            </form>
          </details>
        </div>
      </div>
      {action.busy && <p role="status">Registering source…</p>}
      <ActionError error={action.error} />
    </section>
  );
}
