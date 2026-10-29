# ADR0040 — Static creator frontend, Clerk Bearer and onboarding

Status: Accepted engineering decision within approved Next.js/Clerk scope; 2026-10-18.
Task23 / [#3](https://github.com/Sanyoghk/Cadence/issues/3). Baseline `15ad3ec`, `mvp`.

## Context and decision

Stable `/api/v1` Workspace/Brand DTOs, OWNER-only local authorization and Clerk JWT decoder
already exist. No frontend or prototype assets exist in this checkout. #3 includes later
Identity/Strategy/Content/Analytics screens; task23 does not claim that whole issue complete.

Use standalone `frontend/` Next.js App Router with static export, React/TypeScript and the
official browser `@clerk/react` SDK. Public pages contain no private server-rendered data.
ClerkProvider/SignIn/SignUp/UserButton manage authentication; hash routing works on static
hosting. Signed-out Workspace screen redirects to `/sign-in/`. No Clerk CLI provisioning,
keyless account creation, secret key, BFF, API proxy, server action or frontend token bypass.

Get a short-lived token using the explicit configured Clerk JWT template; send it only to
the validated configured API origin/path via `Authorization: Bearer`. Fetch uses
`credentials: omit`, `cache: no-store`, `redirect: error`, 15s deadline, 512KiB response cap.
No JWT in browser application storage, URL, logs or diagnostics. Clerk controls its own
session storage; browser SDK session cookies are never accepted as API authentication.

Backend CORS defaults to no origins, accepts at most ten exact HTTPS origins (HTTP only
explicit loopback), no wildcard/credentials. GET/POST and Authorization/Content-Type/Accept
only, on `/api/v1/workspaces/**` and `/api/v1/brands/*`; other cross-origin requests denied.
Expand deliberately with future screens. Preflight grants no principal or resource access.
Backend CSRF remains disabled because API authority comes exclusively from an explicit
Bearer header, never ambient cookies. Clerk session mutations remain the SDK's concern.
Backend signature/issuer/audience/expiry/azp and current local OWNER/membership are unchanged.
Configure CORS and Clerk authorized parties to the same deployed frontend origins; they
serve different purposes and neither replaces resource authorization.

## Alternatives and trade-offs

| Choice | Security/correctness | Complexity/maintainability | Performance/value |
| --- | --- | --- | --- |
| Browser Bearer + static React SDK (chosen) | Existing JWT boundary, no extra server secret; XSS can access in-memory token, so no raw HTML/unsafe links | Small typed client; exact CORS setup required | Static hosting; private data loads after session |
| Cookie/BFF + Next server SDK | HttpOnly backend cookie possible, but requires new CSRF/session/proxy/secret architecture | Extra authority and operational boundary without onboarding need | SSR private views possible later |
| Browser Bearer + Next server integration | Retains API contract but adds server Clerk configuration/middleware for SSR | Additional runtime/secret with little value for this public shell | Useful when authenticated SSR is required |

No changes to tenant ownership, backend quota, archive or approval semantics. Creating a
Workspace and creating a Brand are separate committed operations, not an atomic wizard.
No auto retry of ambiguous POST: refresh list before resubmitting to avoid duplicate creates;
onboarding API has no agreed idempotency key contract. Existing backend quotas stay authoritative.

## State and DTO contracts

Workspace list/create → explicit Workspace selection → bounded Brand list/create → fresh
Brand profile read. List pages20, max page10000; no persistent/global data cache. Session
user+session key unmounts private state; Workspace changes unmount Brand state/forms and
abort in-flight reads/writes. Late replies cannot update new scope. Brand response must
match requested Workspace/id. Archived Workspace cannot show creation; archived Brand
remains readable as history. API independently enforces archive/access races.

Request types whitelist only name/industry and Brand profile fields. Responses rehydrate
minimal bounded DTOs, discard `signature` and unrelated/private fields. Shared safe Problem
Details retains controlled status/code/retryable/field codes; UI never prints raw detail,
exception, URL or token. React text escaping and plain text profile links prevent XSS.
Explicit loading/empty/error/401/403/404/409 and uncertain-network states; semantic labels,
skip link, visible focus, native keyboard controls and responsive 375px layout.

## Threat model and verification

Protect tokens, membership and private Workspace/Brand data. Authenticated malicious users,
untrusted websites and stale requests cross browser/API/Clerk/configuration boundaries.

| Threat | Protection | Verification |
| --- | --- | --- |
| BOLA/role escalation | Existing use-case/membership + DTO whitelist; response scope binding | Existing Security suite + CORS OWNER/EDITOR + client foreign response tests |
| CSRF/foreign origins | Explicit Bearer, omitted cookies, exact CORS; no new cookie API | Cookie-only401, untrusted/null/suffix origin403, exact preflight has no credentials |
| XSS/error leak | Escaped text, no HTML renderer/external profile links, controlled errors | Malicious names/raw error fixture, bounded JSON/UTF8 tests, axe |
| Tenant/session races | Session-scoped UI, request cancellation and response guards, no shared cache | Delayed old Workspace response, visible profile/form reset tests |
| Token leak/SSRF/redirect | Public env validation, fixed API builders, redirect error; browser only | Unsafe base/path/token absence tests; no server fetch capability |
| Resource abuse | Page/response/deadline/input bounds, no automatic mutation retry | Bounds/malformed/oversize/uncertain POST tests; backend quotas unchanged |
| Test bypass reaching production | Vite fixture outside src/app; distinct server; no production flag | Export route404, build inspection; live test has separate config/keys |

Failure of frontend defenses does not grant backend authorization. Valid foreign token/ID
still faces actual membership checks. Compromised browser/XSS is a residual risk: apply
TLS, scoped Clerk deployment/security settings and hosting CSP/headers before release.

## Dependency and qualification policy

Exact compatible versions + pnpm integrity lock; no framework scaffold/Clerk CLI side effects.
Next16.4.2 includes October14 security fixes; React19.2.9 matches Clerk6.17.8 peer range
(latest React19.3.1 does not). Node24.19.0, pnpm11.25.0, TypeScript5.9.3.
ESLint10 supported with compatible typescript-eslint/react-hooks; current eslint-config-next
pulled unsupported ESLint10 peers and was removed, not overridden. Browser axe verifies a11y.
No CSS framework, HTTP/state/schema/form package needed for this foundation.

Local contract/auth-gate browser tests are not live Clerk authentication. Missing existing
Clerk keys/test user is an external blocker; do not manufacture passing signup/login or
bootstrap user metadata manually in the UI. Live script fails explicitly before network
without development keys. Real public signup and server-controlled OWNER claim provisioning
must be qualified with the existing Clerk instance; no production auth shortcut is introduced.

Official sources checked before dependency installation:
[Next security October14](https://nextjs.org/blog/nextjs-security-update-october-14-2026),
[Next16.4.2](https://github.com/vercel/next.js/releases/tag/v16.4.2),
[Next installation](https://nextjs.org/docs/app/getting-started/installation),
[Clerk React](https://clerk.com/docs/react/getting-started/quickstart),
[Clerk Bearer requests](https://clerk.com/docs/guides/development/making-requests),
[Clerk Playwright](https://clerk.com/docs/guides/development/testing/playwright/overview),
[Spring CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html).
Exact qualification results belong to [task23 report](../prompt23-frontend-report.md).

## Frontend24 contract extension (2026-10-18)

Static Bearer architecture unchanged. Frontend evidence/Identity routes are explicitly
allowlisted in CORS with exact configured origins and method-specific rules: source JSON/
multipart/social registration, review/admission/current read/withdrawal/history, analysis-jobs,
Job/result reads, Identity collection/read/PATCH/copy/approval/provenance. GET-only reads and
POST-only commands cannot acquire PATCH/DELETE through the allowlist. Only analysis-jobs accepts
Idempotency-Key; Location is exposed for these scoped operations; credentials remain disabled.
CORS never grants authorization. Unknown routes retain the default-deny policy.

Evidence list has a dedicated4MiB response cap; default512KiB and15s deadline stay intact.
The larger cap covers20 existing20000-character source references including UTF8 Unicode;
no unlimited buffer was introduced. Source bytes, review text, provider metadata and generated
Identity remain distinct. Explicit confirmation is tied to exact server selectors and locally
verified SHA256 UTF8;409 resets consent. Images/screenshots remain REQUIRES_PROVIDER.

New GET `/api/v1/evidence/{evidenceId}/ai-admission/history?page=0&size=20` returns immutable
metadata only, fixed reviewedAt DESC/id DESC, page0..10000/size1..20, Cache-Control no-store.
Requires current active OWNER/membership under existing parent lock order. Includes source/
context versions, checksums, review actor/time/policy, artifact/scan/snapshot/expiry selectors
and whole-sourceVersion withdrawal audit. It never claims current scan/storage eligibility;
existing current snapshot reads retain all guards. Archive history/withdrawal remains readable
under current authorization. No schema/grant/retention policy or source admission semantics changed.

[Implementation, alternatives, threat model, gates and limitations](../prompt24-identity-report.md).

### Concurrent initial account bootstrap

Task24 actual HTTP qualification exposed a race between two first requests for one verified
JWT subject. Preserve DB uniqueness and current-account authority: CadenceIdentityService
uses a TransactionTemplate boundary so commit failures are caught after rollback, then at
most one fresh lookup reads the committed active winner. Do not retry INSERT, combine JWT
roles/workspace claims, reuse a failed persistence context or recover inside an ambient
rollback-only transaction. Existing signature/issuer/audience/expiry/azp and business resource
checks remain unchanged. Subject is bounded to the existing255-column contract, without silent
trimming/control characters. Deterministic PostgreSQL competing-insert and unit negative tests
qualify this technical correction; no new authentication protocol/product policy is introduced.
