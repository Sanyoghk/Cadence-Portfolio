# Creator frontend foundation

UI rework23–24 (2026-10-20) **QUALIFIED LOCAL**: Cream Orbit в рабочем `frontend/`,
public landing/common shell, responsive Manager/Identity navigation, actual Clerk SDK entry,
existing Workspace/Brand/evidence/Identity workflows. [Report](ui-rework23-24-report.md).
58 unit/66 browser+axe, lint/typecheck/build/export PASS. Старые design notes ниже исторические;
ADR0040 Bearer/DTO/tenant/session contract не меняется.25–27 и live/device/release gates отдельно.

Task23, [ADR0040](adr/0040-frontend-bearer-onboarding.md), [issue #3](https://github.com/Sanyoghk/Cadence/issues/3).
Standalone Next.js/React/TypeScript static application in `frontend/`. No agency/iframe scope.
Foundation23 covers Clerk and Workspace/Brand onboarding. Task24 now adds qualified local evidence/Identity screens;25–27 remain pending. [Report24](prompt24-identity-report.md).

Исторический запрос2026-10-19: существенная переработка web UI после manual smoke.
[UI rework plan](ui-rework-plan.md): design review сейчас →backend28 →rework23–24 →25–27.
Конкретный Cream Orbit package выбран19 октября и перенесён20 октября;
auth/API clients и qualified business workflows не реализовывались заново.

Историческое решение пользователя2026-10-18: [существующий demo UI → план переноса](prototype-ui-adaptation-plan.md).
Visual follow-up23 qualified locally: [report](prompt23-visual-report.md).
Shell/onboarding и Cream/Dark/Sage перенесены;24 qualified locally,25–27 pending после rework.
Foundation23/ADR0040 и его историческая qualification сохраняются. Local Clerk development
setup выполнен19 октября; user-reported manual smoke не равен полной live auth E2E/
self-service provisioning/production qualification. Новый UI не меняет Bearer architecture.

## Prototype presentation layer

`CreatorShell` owns only ephemeral appearance state (Cream default, Dark/Sage); no identity,
token or business data is persisted. `ui.tsx` supplies reviewed fixed SVG/wordmark, Chip,
Button and a semantic workflow map. Future stages are explicitly not connected, not links
to fake results. Workspace/Brand forms still use the existing CadenceApi and scoped state.
Self-hosted prototype Cormorant Garamond/DM Sans assets, checksums and OFL notices:
[font inventory](../frontend/public/fonts/README.md). No font CDN/preconnect/runtime download.
`pnpm verify:export` checks production artifacts for fixture/old loader/CDN markers and
bounded local WOFF2 assets. CI runs it after build; it does not replace live auth qualification.
Historical foundation checks below remain distinct from the actual visual follow-up report.


## Local setup

Use Node24.x (qualified24.19.0), pnpm11.25.0. In `frontend/`:

```powershell
pnpm install --frozen-lockfile
Copy-Item .env.example .env.local
pnpm dev
```

Replace public placeholders in the ignored `.env.local` from the **existing** Clerk instance:
`NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, `NEXT_PUBLIC_CLERK_JWT_TEMPLATE=cadence-api`,
`NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1`. No secret key belongs in any
`NEXT_PUBLIC_*` value. These are build-time settings; rebuild when changed. Missing/invalid
configuration shows Setup required and makes no private API requests. No account is
automatically provisioned through CLI/keyless mode. Open `http://localhost:3000` explicitly
(not127.0.0.1 unless that exact origin is configured everywhere).

Backend requires `CADENCE_CORS_ALLOWED_ORIGINS=http://localhost:3000` and the matching
`CLERK_AUTHORIZED_PARTIES`, plus issuer/JWKS/audience from [Clerk backend setup](clerk-authentication.md).
All three must refer to the same existing Clerk instance. API allows explicit Bearer only;
frontend fetch omits cookies. CORS defaults empty/denied, credentials false, only onboarding
GET/POST paths+headers. No CORS wildcard or public OPTIONS authority bypass. Future pages
must deliberately extend the reviewed CORS contract if they need other paths/methods.

Configure the Clerk JWT template `cadence-api`: actual audience, issuer/expiry/azp and
server-controlled `cadence_role=OWNER`, initially empty `cadence_workspace_ids` for a new
creator. Do not use unsafe user-editable metadata. Existing local users/memberships remain
authoritative; later claims cannot grant foreign Workspace access. Public signup without
administrator intervention depends on that server-controlled default/template and must
be verified live; UI does not invent an OWNER role or synchronize arbitrary claims.

Clerk [JWT templates](https://clerk.com/docs/guides/sessions/jwt-templates) automatically
include issuer/subject/expiry/azp claims; do not manufacture them in the client. Verify the
actual template audience, short TTL and origin against the backend validators. Sign-out
removes the browser session; it does not magically invalidate every already issued
self-contained Bearer token before expiry. Local account/membership revocation remains
authoritative on backend requests. Immediate live Clerk revocation semantics require
separate qualification; the fixture does not prove them.

## API and UI

- `/` public landing; `/sign-in/`, `/sign-up/` SDK components with hash routing.
- `/workspaces/`: actual Clerk session required. Signed-out redirects to sign-in.
- GET/POST `/api/v1/workspaces`; GET/POST `/workspaces/{id}/brands`; GET `/brands/{id}`.
- Create payloads whitelist existing DTO fields; role/actor/ownership/version internals are
  not writable. Brand profile does not render legacy signature/private provider data.
- List size20, pages0..10000. Explicit selection, per-page nav, archived/missing/forbidden/
  loading/empty/error states. Brand selection performs a fresh authorized read.
- Workspace and Brand creation are separate server operations. On uncertain mutation,
  refresh list before resubmitting; no automatic POST retry/idempotency claim.
- No persistent data/token cache; Workspace/session switches unmount and cancel prior state.
- Website/social fields are plain text. Saving profile URL does not ingest/fetch/analyse it.
- Human decisions about Identity/Strategy/Content remain later workflows, never autoapproved.

## Verification commands

```powershell
pnpm peers check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm exec playwright install chromium
pnpm test:e2e
pnpm audit --audit-level=low
```

The mandatory browser suite starts an isolated Vite test entry on4174 and a public exported
shell on4173. Test-only session and fake API routes are outside `src/app`; they cannot be
activated through production environment/request flags, and the fixture is absent in `out`.
Tests cover actual shipped UI/auth gate/client code, but **not** Clerk cryptography, signup,
live token issuance or browser→real-backend integration. Backend tests separately exercise
real security chain with deterministic JWT/JWKS fixtures and PostgreSQL rules.

## Live qualification (external blocker until credentials available)

Use a dedicated **development** Clerk instance/test user, server-controlled OWNER claims and
running backend/frontend with matching origins. Test runner only: `CLERK_PUBLISHABLE_KEY`,
`CLERK_SECRET_KEY`, `CADENCE_E2E_EMAIL`, `CADENCE_E2E_PASSWORD`, `CADENCE_LIVE_WEB_URL`.
Keep them in process environment/secret manager, never frontend public env or Git. Runner
has trace/video/screenshots off. Avoid sharing raw failure artifacts containing user data.

```powershell
pnpm test:live
```

Missing inputs throw an explicit blocker before Clerk calls (not skip/pass). Official
`@clerk/testing` gets a bot-testing token and authenticates a real development user; this
does not bypass API JWT validation. Test checks signed-out redirect, actual sign-in, scoped
API success and sign-out. Separately qualify fresh public signup/email verification,
server-controlled OWNER defaults, Workspace+Brand creation with live account, account
switch/revocation and actual deployed CORS/TLS. That external gate is not replaced by fixtures.

## Hosting/security and remaining release gates

Deploy only static `frontend/out/`, never repository root, `.env`, tests, node_modules or
dev/Vite server. Use real404 for unknown paths; do not rewrite missing private/test files
to authenticated content. Local `scripts/serve-export.mjs` is verification-only, not a
production server. Rebuild with deployment public values. No server secrets required.

TLS, hosting CSP (exact Clerk script/frame/connect domains and API connect origin, static
Next inline-script hashes), HSTS, nosniff, frame denial, no-referrer, permissions policy
and access/log controls need deployment-specific review. Do not guess a broad `*.clerk.*`
policy or add `unsafe-eval` to make an integration work. SDK uses its existing external
Clerk service; no new provider/data policy beyond approved Clerk auth. Avoid personal
data in build/logs. Backend quotas remain; distributed abuse/rate-limit and production load
qualification belong to later security scope. Package audit is time-specific, not proof
that all browser/provider/configuration threats are resolved.

CI now defines frozen install/peer check/audit/lint/type/unit/build/browser+axe gates without
live credentials. No remote run is claimed before authorized push and exact-SHA verification.

## Frontend24: evidence и Identity

Open Workspace → Brand → Open Brand Identity. Supported workflow: register TEXT/URL/PDF or
validated social text → exact server snapshot → explicit human consent → current read /
withdrawal history → durable EVIDENCE generation → DRAFT copy → edit → separate approval.
Images/screenshots remain REQUIRES_PROVIDER; live model/social configuration may return
controlled503. No browser fake result, automatic consent or approval is used. Current tokens,
checksums/selector contracts, revision lineage and demo attribution remain visible.
[Detailed flow/security/qualification](prompt24-identity-report.md).

Frontend contract generation: `pnpm contracts:generate`; drift check: `pnpm contracts:check`
(included in lint). Default512KiB/15s bounded Bearer transport is retained; source list alone
is bounded to4MiB. Frontend memory is scoped to active session/Workspace/Brand and is aborted
on switch; revoked access removes the private Identity subtree. No JWT/prompt/content cache.

Optional real HTTP integration harness, from `frontend/` after qualified Maven packaging:

```powershell
node scripts/qualify-identity-stack.mjs --isolated-local
```

It owns three loopback ports4174/18124/18125, fresh local H2, backend local fake and ephemeral
RSA/JWKS test issuer. It runs production UI components via the separate Vite test entry,
bridges requests to actual HTTP API with a signed test JWT and passes actual backend responses
through unchanged. Real JwtDecoder/roles/current memberships/DTO/Jobs/Identity guards execute.
No production auth switch, deployed service, live Clerk keys, external provider or persistent
signing key is involved. Cleanup stops owned processes/listeners. This complements PostgreSQL
runtime/migration tests and separately configured live Clerk E2E; it replaces neither gate.
Deploy only `out/`: tests/scripts/JWKS issuer and fixture markers must never enter that artifact.
