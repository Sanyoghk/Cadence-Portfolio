'use client';
import { useState, type FormEvent } from 'react';
import { ApiError, type CadenceApi } from '../lib/api';
import type { Scope } from '../lib/identity-api';
import type {
  BrandIdentityResponse,
  CreateBrandIdentityRequest,
  IdentityContentResponse,
} from '../lib/identity-contract.generated';
import { ActionError, useAction } from './async-state';
import { Button } from './ui';

const colors = ['primary', 'secondary', 'accent', 'neutral', 'background'] as const;
export function IdentityContent({ content }: { content: IdentityContentResponse }) {
  return (
    <div className="identity-content">
      <ul className="palette-list">
        {colors.map((name) => (
          <li key={name}>
            <span
              className="color-swatch"
              aria-hidden="true"
              style={{
                backgroundColor: /^#[0-9A-Fa-f]{6}$/.test(content.palette[name] ?? '')
                  ? content.palette[name]!
                  : undefined,
              }}
            />
            <span>
              {name}: {content.palette[name]}
            </span>
          </li>
        ))}
      </ul>
      <dl>
        <dt>Fonts</dt>
        <dd>{content.fonts.join(' · ')}</dd>
        <dt>Voice up</dt>
        <dd>{content.voiceUp.join(' · ')}</dd>
        <dt>Voice down</dt>
        <dd>{content.voiceDown.join(' · ')}</dd>
      </dl>
      <p className="eyebrow">SAMPLE CAPTION</p>
      <blockquote>{content.sampleCaption}</blockquote>
    </div>
  );
}

export function IdentityEditor({
  api,
  scope,
  identity,
  done,
  cancel,
  disabled,
}: {
  api: CadenceApi;
  scope: Scope;
  identity?: BrandIdentityResponse;
  done: (v: BrandIdentityResponse) => void;
  cancel: () => void;
  disabled: boolean;
}) {
  const action = useAction();
  const [uncertain, setUncertain] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const lines = (name: string) =>
      String(data.get(name) ?? '')
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean);
    const palette = Object.fromEntries(
      colors.map((k) => [k, String(data.get(k) ?? '')]),
    ) as CreateBrandIdentityRequest['content']['palette'];
    const content: CreateBrandIdentityRequest['content'] = {
      palette,
      fonts: lines('fonts'),
      voiceUp: lines('voiceUp'),
      voiceDown: lines('voiceDown'),
      sampleCaption: String(data.get('sampleCaption') ?? ''),
    };
    await action.run(
      (s) =>
        identity
          ? api.identity.editIdentity(scope, identity, content, s)
          : api.identity.createIdentity(scope, content, s),
      done,
      (e) => {
        if (e instanceof ApiError && e.problem.status === 0) setUncertain(true);
      },
    );
  }
  return (
    <section className="panel" aria-label="Identity editor">
      <h4>{identity ? 'Edit reviewed content' : 'Create a manual Identity DRAFT'}</h4>
      <p>
        {identity?.status === 'APPROVED' || identity?.status === 'LOCKED'
          ? 'Saving creates a new DRAFT revision; approved history stays unchanged.'
          : 'Saving does not approve content. Human edits are not new provider output.'}
      </p>
      {identity && (
        <p>
          Editing revision {identity.version}, expected lockVersion {identity.lockVersion}. On 409
          load fresh history; your changes are not silently retried.
        </p>
      )}
      <form aria-label="Save Identity content" onSubmit={submit}>
        <fieldset disabled={disabled || action.busy || uncertain}>
          <legend>Palette · six-digit RGB</legend>
          <div className="palette-fields">
            {colors.map((k) => (
              <label key={k}>
                {k[0].toUpperCase() + k.slice(1)} color
                <input
                  name={k}
                  required
                  pattern="#[0-9a-fA-F]{6}"
                  maxLength={7}
                  defaultValue={identity?.content.palette[k] ?? ''}
                  placeholder="#112233"
                />
              </label>
            ))}
          </div>
          <label>
            Fonts · one per line (1–5)
            <textarea
              name="fonts"
              required
              rows={3}
              maxLength={504}
              defaultValue={identity?.content.fonts.join('\n') ?? ''}
            />
          </label>
          <div className="form-columns">
            <label>
              Voice up · one per line (1–10)
              <textarea
                name="voiceUp"
                required
                rows={3}
                maxLength={1009}
                defaultValue={identity?.content.voiceUp.join('\n') ?? ''}
              />
            </label>
            <label>
              Voice down · one per line (1–10)
              <textarea
                name="voiceDown"
                required
                rows={3}
                maxLength={1009}
                defaultValue={identity?.content.voiceDown.join('\n') ?? ''}
              />
            </label>
          </div>
          <label>
            Sample caption
            <textarea
              name="sampleCaption"
              required
              rows={4}
              maxLength={1000}
              defaultValue={identity?.content.sampleCaption ?? ''}
            />
          </label>
          <Button className="primary" type="submit" disabled={disabled || action.busy || uncertain}>
            {identity?.status === 'APPROVED' || identity?.status === 'LOCKED'
              ? 'Save as new DRAFT'
              : 'Save DRAFT'}
          </Button>
        </fieldset>
        <Button disabled={action.busy} onClick={cancel}>
          Close editor
        </Button>
      </form>
      <ActionError error={action.error} />
      {uncertain && (
        <p role="status">
          The write outcome is uncertain. Refresh version history before a new write; no automatic
          retry is performed.
        </p>
      )}
    </section>
  );
}
