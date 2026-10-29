'use client';
import { Failure } from './failure';

import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  useSyncExternalStore,
  type FormEvent,
  type ReactNode,
} from 'react';
import { ApiError, CadenceApi, type Brand, type Page, type Workspace } from '../lib/api';
import { CreatorShell } from './creator-shell';
import { Button, Icon, WorkflowSteps } from './ui';
import { IdentityWorkbench } from './identity-workbench';

// Navigation names only. Resource IDs and session data never come from the URL.
const subscribeView = (change: () => void) => {
  window.addEventListener('hashchange', change);
  return () => window.removeEventListener('hashchange', change);
};
const readIdentityView = () => window.location.hash === '#identity';
const managerOnServer = () => false;

type Load<T> =
  { state: 'loading' } | { state: 'error'; error: unknown } | { state: 'ready'; value: T };
function useRead<T>(read: (signal: AbortSignal) => Promise<T>) {
  const [load, setLoad] = useState<Load<T>>({ state: 'loading' });
  useEffect(() => {
    const controller = new AbortController();
    read(controller.signal).then(
      (value) => {
        if (!controller.signal.aborted) setLoad({ state: 'ready', value });
      },
      (error) => {
        if (!controller.signal.aborted) setLoad({ state: 'error', error });
      },
    );
    return () => controller.abort();
  }, [read]);
  return load;
}
function Loading() {
  return (
    <p role="status" className="notice">
      Loading your workspace…
    </p>
  );
}
function Pager({
  value,
  change,
  label,
}: {
  value: Page<unknown>;
  change: (page: number) => void;
  label: string;
}) {
  if (value.first && value.last) return null;
  return (
    <nav className="pager" aria-label={label}>
      <button disabled={value.first} onClick={() => change(value.page - 1)}>
        Previous
      </button>
      <span>
        Page {value.page + 1} of {Math.max(1, value.totalPages)}
      </span>
      <button disabled={value.last || value.page >= 10_000} onClick={() => change(value.page + 1)}>
        Next
      </button>
    </nav>
  );
}

export function Onboarding({
  api,
  accountControl,
}: {
  api: CadenceApi;
  accountControl?: ReactNode;
}) {
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [selected, setSelected] = useState<Workspace | null>(null);
  const [selectedBrand, setSelectedBrand] = useState<string | null>(null);
  const [brandRefresh, setBrandRefresh] = useState(0);
  const identityOpen = useSyncExternalStore(subscribeView, readIdentityView, managerOnServer);
  function setIdentityOpen(open: boolean) {
    window.location.hash = open ? 'identity' : 'manager';
  }
  const workspaceMenu = useRef<HTMLDetailsElement>(null);
  const main = useRef<HTMLElement>(null);
  function navigate(identity: boolean) {
    setIdentityOpen(identity);
    main.current?.focus();
  }
  function choose(workspace: Workspace) {
    setSelected(workspace);
    setSelectedBrand(null);
    setBrandRefresh(0);
    setIdentityOpen(false);
    if (workspaceMenu.current) workspaceMenu.current.open = false;
  }
  return (
    <CreatorShell
      accountControl={accountControl}
      workspaceControl={
        <details ref={workspaceMenu} className="workspace-switch nav-disclosure">
          <summary>
            <span className="scope-name" title={selected?.name}>
              {selected?.name ?? 'Choose workspace'}
            </span>{' '}
            <span aria-hidden="true">›</span>
          </summary>
          <div className="switch-panel">
            {identityOpen && (
              <WorkspaceList
                key={`${page}:${refresh}`}
                api={api}
                page={page}
                selected={selected?.id}
                choose={choose}
                change={(p) => {
                  setPage(p);
                  setSelected(null);
                  setSelectedBrand(null);
                  navigate(false);
                }}
                retry={() => {
                  setSelected(null);
                  setSelectedBrand(null);
                  setRefresh((v) => v + 1);
                  navigate(false);
                }}
              />
            )}
            <Button onClick={() => navigate(false)}>Manage workspaces</Button>
          </div>
        </details>
      }
      navigation={
        <>
          <button aria-current={identityOpen ? undefined : 'page'} onClick={() => navigate(false)}>
            Workspace Manager
          </button>
          <button aria-current={identityOpen ? 'page' : undefined} onClick={() => navigate(true)}>
            Brand Identity
          </button>
          {['Strategy playbook', 'Sample content', 'Performance'].map((name) => (
            <button key={name} disabled title="Not connected yet. A later frontend stage.">
              {name}
            </button>
          ))}
        </>
      }
    >
      <main ref={main} id="main-content" className="main-content" tabIndex={-1}>
        <div className="intro">
          <div>
            <h1>{identityOpen ? 'Brand Identity' : 'Workspace Manager'}</h1>
            <p className="lead">
              {identityOpen
                ? 'Start with your sources. Keep the decisions yours.'
                : 'Make space for your Brand. Choose a Workspace, then give your ideas a home.'}
            </p>
          </div>
        </div>
        <div className={identityOpen ? 'identity-page' : 'workspace-layout'}>
          {!identityOpen && (
            <aside className="sidebar" aria-label="Workspace navigation">
              <div className="section-label">
                <Icon name="workspace" />
                <span className="eyebrow">Your space</span>
              </div>
              <h2>Workspaces</h2>
              <WorkspaceList
                key={`${page}:${refresh}`}
                api={api}
                page={page}
                selected={selected?.id}
                choose={choose}
                change={(p) => {
                  setPage(p);
                  setSelected(null);
                  setSelectedBrand(null);
                }}
                retry={() => {
                  setSelected(null);
                  setSelectedBrand(null);
                  setRefresh((v) => v + 1);
                }}
              />
              <details>
                <summary id="create-workspace">Create a Workspace</summary>
                <CreateForm
                  key={`workspace:${refresh}`}
                  kind="workspace"
                  api={api}
                  done={(value) => {
                    setPage(0);
                    setRefresh((v) => v + 1);
                    choose(value as Workspace);
                  }}
                />
              </details>
              <p className="sidebar-note">
                A Workspace keeps your Brands together. Choose one to see its profiles.
              </p>
            </aside>
          )}
          <div className="brand-content">
            {!identityOpen && (
              <ol className="steps" aria-label="Onboarding progress">
                <li
                  aria-current={!selected ? 'step' : undefined}
                  className={!selected ? 'current' : ''}
                >
                  Workspace
                </li>
                <li
                  aria-current={selected ? 'step' : undefined}
                  className={selected ? 'current' : ''}
                >
                  Brand profile
                </li>
                <li>Identity review</li>
              </ol>
            )}
            {!selected ? (
              <section className="panel welcome-panel">
                <span className="large-mark" aria-hidden="true">
                  <Icon name="spark" />
                </span>
                <h2>Make room for your next idea.</h2>
                <p>
                  Choose a Workspace or create your first one using its name and industry. Your
                  Brand profile comes next.
                </p>
                {identityOpen && (
                  <Button onClick={() => navigate(false)}>Choose a Workspace and Brand</Button>
                )}
                <div className="welcome-path" aria-hidden="true">
                  <span>Workspace</span>
                  <Icon name="arrow" />
                  <span>Brand</span>
                  <Icon name="arrow" />
                  <span>Your next chapter</span>
                </div>
              </section>
            ) : selected.status === 'ARCHIVED' ? (
              <section className="panel">
                <h2>Workspace archived</h2>
                <p>Select an active Workspace to continue onboarding.</p>
              </section>
            ) : (
              <BrandPanel
                key={`${selected.id}:${brandRefresh}`}
                api={api}
                workspace={selected}
                selectedBrand={selectedBrand}
                choose={setSelectedBrand}
                identityOpen={identityOpen}
                openIdentity={navigate}
                refresh={() => {
                  setSelectedBrand(null);
                  setBrandRefresh((v) => v + 1);
                }}
              />
            )}
          </div>
        </div>
        <details className="workflow-map">
          <summary>Workflow availability</summary>
          <WorkflowSteps />
        </details>
      </main>
    </CreatorShell>
  );
}

function WorkspaceList({
  api,
  page,
  selected,
  choose,
  change,
  retry,
}: {
  api: CadenceApi;
  page: number;
  selected?: string;
  choose: (value: Workspace) => void;
  change: (value: number) => void;
  retry: () => void;
}) {
  const read = useCallback((signal: AbortSignal) => api.workspaces(page, signal), [api, page]);
  const load = useRead(read);
  if (load.state === 'loading') return <Loading />;
  if (load.state === 'error') return <Failure error={load.error} retry={retry} />;
  return (
    <>
      <ul className="workspace-list">
        {load.value.content.map((w) => (
          <li key={w.id}>
            <button aria-pressed={w.id === selected} onClick={() => choose(w)}>
              {w.name}
              {w.status === 'ARCHIVED' && <small>Archived</small>}
            </button>
          </li>
        ))}
      </ul>
      {load.value.content.length === 0 && (
        <p className="notice">No Workspaces yet. Create your first Workspace below.</p>
      )}
      <Pager value={load.value} change={change} label="Workspace pagination" />
    </>
  );
}

function BrandPanel({
  api,
  workspace,
  selectedBrand,
  choose,
  refresh,
  identityOpen,
  openIdentity,
}: {
  api: CadenceApi;
  workspace: Workspace;
  selectedBrand: string | null;
  choose: (value: string | null) => void;
  refresh: () => void;
  identityOpen: boolean;
  openIdentity: (open: boolean) => void;
}) {
  const [page, setPage] = useState(0);
  const [formVersion, setFormVersion] = useState(0);
  return (
    <section aria-label={identityOpen ? 'Selected Brand' : 'Brand profiles'}>
      {!identityOpen && (
        <div className="section-heading">
          <div>
            <p className="eyebrow">{workspace.industry}</p>
            <h2 id="brands-heading">Your Brands</h2>
          </div>
          <button onClick={refresh}>Refresh Brands</button>
        </div>
      )}
      <BrandList
        key={page}
        api={api}
        workspaceId={workspace.id}
        page={page}
        choose={choose}
        change={(p) => {
          choose(null);
          setPage(p);
        }}
        retry={refresh}
        selected={selectedBrand}
        selector={identityOpen}
      />
      {selectedBrand && (
        <BrandDetails
          key={selectedBrand}
          api={api}
          workspaceId={workspace.id}
          brandId={selectedBrand}
          retry={refresh}
          identityOpen={identityOpen}
          openIdentity={openIdentity}
        />
      )}
      {!identityOpen && (
        <section className="panel create-panel">
          <h2>Create a Brand</h2>
          <p>
            Start with a name. Optional links are saved as profile information; adding a link does
            not fetch or analyse it.
          </p>
          <CreateForm
            key={formVersion}
            kind="brand"
            api={api}
            workspaceId={workspace.id}
            done={() => {
              setFormVersion((v) => v + 1);
              refresh();
            }}
          />
        </section>
      )}
      {identityOpen && !selectedBrand && (
        <section className="panel">
          <h2>Choose your Brand</h2>
          <p>Select a Brand above to review sources and shape its Identity.</p>
          <Button onClick={() => openIdentity(false)}>Create or manage Brands</Button>
        </section>
      )}
    </section>
  );
}
function BrandList({
  api,
  workspaceId,
  page,
  choose,
  change,
  retry,
  selected,
  selector = false,
}: {
  api: CadenceApi;
  workspaceId: string;
  page: number;
  choose: (id: string) => void;
  change: (page: number) => void;
  retry: () => void;
  selected?: string | null;
  selector?: boolean;
}) {
  const brandSelectId = useId();
  const read = useCallback(
    (signal: AbortSignal) => api.brands(workspaceId, page, signal),
    [api, workspaceId, page],
  );
  const load = useRead(read);
  if (load.state === 'loading') return <Loading />;
  if (load.state === 'error') return <Failure error={load.error} retry={retry} />;
  return (
    <>
      {selector ? (
        <div className="scope-row">
          <span>Workspace-scoped sources and revision history</span>
          <div className="compact-label">
            <label htmlFor={brandSelectId}>Brand</label>
            <select
              id={brandSelectId}
              value={selected ?? ''}
              onChange={(e) => choose(e.target.value)}
            >
              <option value="">Choose a Brand</option>
              {selected && !load.value.content.some((b) => b.id === selected) && (
                <option value={selected}>Selected Brand · another page</option>
              )}
              {load.value.content.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                  {b.status === 'ARCHIVED' ? ' · Archived' : ''}
                </option>
              ))}
            </select>
          </div>
        </div>
      ) : load.value.content.length === 0 ? (
        <p className="notice">No Brands yet. Give your first Brand a name below.</p>
      ) : (
        <ul className="brand-grid">
          {load.value.content.map((b) => (
            <li className="panel" key={b.id}>
              <span className="eyebrow">{b.status}</span>
              <h3>{b.name}</h3>
              <button onClick={() => choose(b.id)}>Open {b.name}</button>
            </li>
          ))}
        </ul>
      )}
      <Pager value={load.value} change={change} label="Brand pagination" />
    </>
  );
}
function BrandDetails({
  api,
  workspaceId,
  brandId,
  retry,
  identityOpen,
  openIdentity,
}: {
  api: CadenceApi;
  workspaceId: string;
  brandId: string;
  retry: () => void;
  identityOpen: boolean;
  openIdentity: (open: boolean) => void;
}) {
  const read = useCallback(
    (signal: AbortSignal) => api.brand(workspaceId, brandId, signal),
    [api, workspaceId, brandId],
  );
  const load = useRead(read);
  if (load.state === 'loading') return <Loading />;
  if (load.state === 'error') return <Failure error={load.error} retry={retry} />;
  const b = load.value;
  return (
    <>
      {!identityOpen && (
        <section className="panel brand-detail" aria-label="Brand details">
          <p className="eyebrow">BRAND PROFILE</p>
          <h2>{b.name}</h2>
          <dl>
            <dt>Website</dt>
            <dd>{b.websiteUrl || 'Not provided'}</dd>
            <dt>Instagram</dt>
            <dd>{b.instagramHandle || 'Not provided'}</dd>
            <dt>TikTok</dt>
            <dd>{b.tiktokHandle || 'Not provided'}</dd>
            <dt>Status</dt>
            <dd>{b.status}</dd>
          </dl>
          <p>
            {b.status === 'ACTIVE'
              ? 'Your Brand profile is ready. Review evidence and create your Identity below.'
              : 'This Brand is archived.'}
          </p>
          <Button onClick={() => openIdentity(true)}>Open Brand Identity</Button>
        </section>
      )}
      {identityOpen && (
        <IdentityWorkbench
          key={brandId}
          api={api}
          workspaceId={workspaceId}
          brandId={brandId}
          disabled={b.status !== 'ACTIVE'}
        />
      )}
    </>
  );
}

function CreateForm({
  kind,
  api,
  workspaceId,
  done,
}: {
  kind: 'workspace' | 'brand';
  api: CadenceApi;
  workspaceId?: string;
  done: (value: Workspace | Brand) => void;
}) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const inFlight = useRef(false);
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current) return;
    const data = new FormData(event.currentTarget);
    const name = String(data.get('name') ?? '').trim();
    const industry = String(data.get('industry') ?? '').trim();
    if (
      !name ||
      name.length > 255 ||
      (kind === 'workspace' && (!industry || industry.length > 255))
    ) {
      setError(
        new ApiError({ status: 400, code: 'validation_failed', retryable: false, errors: [] }),
      );
      return;
    }
    const optional = (key: string) => String(data.get(key) ?? '').trim() || null;
    inFlight.current = true;
    setPending(true);
    setError(null);
    const current = new AbortController();
    controller.current = current;
    try {
      const value =
        kind === 'workspace'
          ? await api.createWorkspace({ name, industry }, current.signal)
          : await api.createBrand(
              workspaceId!,
              {
                name,
                websiteUrl: optional('websiteUrl'),
                instagramHandle: optional('instagramHandle'),
                tiktokHandle: optional('tiktokHandle'),
              },
              current.signal,
            );
      if (!current.signal.aborted) done(value);
    } catch (e) {
      if (!current.signal.aborted) setError(e);
    } finally {
      if (!current.signal.aborted) {
        inFlight.current = false;
        setPending(false);
      }
    }
  }
  return (
    <form onSubmit={submit} aria-label={`Create ${kind}`} aria-busy={pending}>
      <label>
        {kind === 'workspace' ? 'Workspace name' : 'Brand name'}
        <input
          name="name"
          required
          maxLength={255}
          autoComplete="organization"
          disabled={pending}
        />
      </label>
      {kind === 'workspace' ? (
        <label>
          Industry
          <input name="industry" required maxLength={255} disabled={pending} />
        </label>
      ) : (
        <>
          <label>
            Website (optional)
            <input
              name="websiteUrl"
              type="url"
              maxLength={2048}
              placeholder="https://example.com"
              disabled={pending}
            />
          </label>
          <div className="form-columns">
            <label>
              Instagram (optional)
              <input name="instagramHandle" maxLength={255} disabled={pending} />
            </label>
            <label>
              TikTok (optional)
              <input name="tiktokHandle" maxLength={255} disabled={pending} />
            </label>
          </div>
        </>
      )}
      {error !== null && <Failure error={error} />}
      <Button className="primary" disabled={pending} type="submit">
        {pending ? 'Creating…' : `Create ${kind === 'workspace' ? 'Workspace' : 'Brand'}`}
        {!pending && <Icon name="arrow" />}
      </Button>
    </form>
  );
}
