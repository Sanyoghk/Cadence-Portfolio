# Задание23 — изменённые файлы

Каждый tracked/new product/documentation файл этой задачи. Ignored `var/`, `node_modules`,
`.next`, `out`, logs/screenshots не входят в commit.

| Файл | Назначение |
| --- | --- |
| [.env.example](../.env.example) | Явный opt-in CORS origin для локального frontend; реальные secrets не добавлены. |
| [.github/workflows/verify.yml](../.github/workflows/verify.yml) | Pinned Node/pnpm frontend CI: frozen dependencies, peers/audit/lint/type/unit/build/browser/axe. |
| [.gitignore](../.gitignore) | Исключает dependency/build/browser artifacts frontend, не скрывает исходники/lock. |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальная точка23 и сохранение remaining #3/live scopes. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронная русская точка23 и remaining scopes. |
| [README.md](../README.md) | Ссылка на standalone frontend и live setup; граница delivered scope. |
| [docs/adr/0040-frontend-bearer-onboarding.md](adr/0040-frontend-bearer-onboarding.md) | Auth/static/CORS architecture, alternatives, threat model, dependency decisions. |
| [docs/adr/README.md](adr/README.md) | Индекс ADR0040. |
| [docs/astra-medium-mvp-prompts.md](astra-medium-mvp-prompts.md) | Ссылка на actual23 execution и live qualification boundary. |
| [docs/clerk-authentication.md](clerk-authentication.md) | Связывает existing JWT contract с frontend/CORS, не меняет server-controlled claims. |
| [docs/frontend-foundation.md](frontend-foundation.md) | Setup, API/Clerk/CORS configuration, verification/live gate и hosting constraints. |
| [docs/issue-sync.md](issue-sync.md) | Local mapping к actual #3/#107 без remote write/closure. |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Progress23 и честные blockers. |
| [docs/prompt23-changed-files.md](prompt23-changed-files.md) | Этот полный per-file manifest. |
| [docs/prompt23-frontend-report.md](prompt23-frontend-report.md) | Подробный отчёт, exact commands/results, risks и учебное объяснение. |
| [frontend/.env.example](../frontend/.env.example) | Только3 public placeholders; no server secret. |
| [frontend/.npmrc](../frontend/.npmrc) | Exact save versions, strict peers, official registry. |
| [frontend/.prettierignore](../frontend/.prettierignore) | Generated/build/lock files исключены из formatter; source/tests проверяются. |
| [frontend/.prettierrc.json](../frontend/.prettierrc.json) | Единый читаемый TS/TSX/CSS formatting contract. |
| [frontend/eslint.config.mjs](../frontend/eslint.config.mjs) | Поддерживаемые ESLint10/TypeScript/React Hooks recommended checks. |
| [frontend/next-env.d.ts](../frontend/next-env.d.ts) | Official generated Next type references; no runtime code. |
| [frontend/next.config.ts](../frontend/next.config.ts) | Static export, no server/optimizer, trailing slash route contract. |
| [frontend/package.json](../frontend/package.json) | Exact runtime/dev dependencies, Node/pnpm policy, reproducible check/build scripts. |
| [frontend/playwright.config.ts](../frontend/playwright.config.ts) | Mandatory Chromium fixture/static-shell browser gates, no trace/PII artifacts. |
| [frontend/playwright.live.config.ts](../frontend/playwright.live.config.ts) | Отдельный live Clerk gate; не подменяется fixture/skipped green result. |
| [frontend/pnpm-lock.yaml](../frontend/pnpm-lock.yaml) | Integrity hashes/transitive versions/peer graph. |
| [frontend/pnpm-workspace.yaml](../frontend/pnpm-workspace.yaml) | Explicit workspace и restricted dependency build scripts (sharp disabled). |
| [frontend/scripts/serve-export.mjs](../frontend/scripts/serve-export.mjs) | Loopback verification-only static server, bounded route-root/path/method/headers; не production server. |
| [frontend/src/app/error.tsx](../frontend/src/app/error.tsx) | Safe render-error boundary без exception/token disclosure. |
| [frontend/src/app/globals.css](../frontend/src/app/globals.css) | Responsive creator layout, visible focus, semantic contrast, mobile form controls. |
| [frontend/src/app/layout.tsx](../frontend/src/app/layout.tsx) | Public root metadata, English UI language, skip link; no private SSR. |
| [frontend/src/app/not-found.tsx](../frontend/src/app/not-found.tsx) | Controlled not-found UI. |
| [frontend/src/app/page.tsx](../frontend/src/app/page.tsx) | Public creator landing/navigation. |
| [frontend/src/app/sign-in/page.tsx](../frontend/src/app/sign-in/page.tsx) | Static official Clerk SignIn entry. |
| [frontend/src/app/sign-up/page.tsx](../frontend/src/app/sign-up/page.tsx) | Static official Clerk SignUp entry. |
| [frontend/src/app/workspaces/page.tsx](../frontend/src/app/workspaces/page.tsx) | Authenticated onboarding entry через AuthRoot. |
| [frontend/src/components/auth.tsx](../frontend/src/components/auth.tsx) | Official Clerk wiring, explicit template token, session-keyed private subtree. |
| [frontend/src/components/onboarding.tsx](../frontend/src/components/onboarding.tsx) | Workspace/Brand list/create/read, pagination, cancellation, scoped keyed state, accessible forms/errors. |
| [frontend/src/components/session-gate.tsx](../frontend/src/components/session-gate.tsx) | Signed-out redirect/loading и отсутствие protected children до session. |
| [frontend/src/lib/api.ts](../frontend/src/lib/api.ts) | Typed whitelist client, runtime DTO/scope/byte/deadline validation, safe Problem Details, no cookies/retries. |
| [frontend/src/lib/config.ts](../frontend/src/lib/config.ts) | Public configuration trust boundary; fails closed без ключей/при unsafe URL. |
| [frontend/tests/browser/onboarding.spec.ts](../frontend/tests/browser/onboarding.spec.ts) | 16 actual Chromium UI/auth-gate/axe/race/timeout/production isolation scenarios. |
| [frontend/tests/fixture/index.html](../frontend/tests/fixture/index.html) | Test-only Vite HTML outside production App Router. |
| [frontend/tests/fixture/main.tsx](../frontend/tests/fixture/main.tsx) | Явный isolated UI contract session, не JWT/production bypass. |
| [frontend/tests/fixture/vite.config.ts](../frontend/tests/fixture/vite.config.ts) | Separate test port/root и bounded filesystem access. |
| [frontend/tests/live/auth.spec.ts](../frontend/tests/live/auth.spec.ts) | Real Clerk sign-in/API/sign-out qualification template; до keys не pass. |
| [frontend/tests/live/setup.ts](../frontend/tests/live/setup.ts) | Required development credentials validation, explicit blocker, official Clerk setup. |
| [frontend/tests/unit/api.test.ts](../frontend/tests/unit/api.test.ts) | HTTP/scope/headers/bounds/malformed/uncertain/replay-negative client tests. |
| [frontend/tests/unit/config.test.ts](../frontend/tests/unit/config.test.ts) | Missing/secret/unsafe URL/key/template validation tests. |
| [frontend/tsconfig.json](../frontend/tsconfig.json) | Strict TypeScript/browser/Next types; no implicit any. |
| [frontend/vitest.config.ts](../frontend/vitest.config.ts) | Изолированные Node unit tests; browser/live suites отдельно. |
| [src/main/java/com/cadence/security/ApiCorsProperties.java](../src/main/java/com/cadence/security/ApiCorsProperties.java) | Immutable validated bounded exact origins; empty defaults deny. |
| [src/main/java/com/cadence/security/SecurityConfiguration.java](../src/main/java/com/cadence/security/SecurityConfiguration.java) | CORS перед JWT, narrow onboarding paths/methods/headers, deny fallback; Bearer/CSRF policy сохранена. |
| [src/main/resources/application.properties](../src/main/resources/application.properties) | Empty-by-default external CORS allowlist property. |
| [src/test/java/com/cadence/security/ApiCorsPropertiesTest.java](../src/test/java/com/cadence/security/ApiCorsPropertiesTest.java) | Unsafe/wildcard/config bounds negatives и allowed HTTPS/loopback. |
| [src/test/java/com/cadence/security/OnboardingCorsIntegrationTest.java](../src/test/java/com/cadence/security/OnboardingCorsIntegrationTest.java) | Exact preflight, actual OWNER/EDITOR onboarding, cookie/origin/path/header/method negatives. |
| [src/test/java/com/cadence/security/SecurityIntegrationTest.java](../src/test/java/com/cadence/security/SecurityIntegrationTest.java) | Regression default cross-origin deny и cookie-only401; прежние tests сохранены. |
