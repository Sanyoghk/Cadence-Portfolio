# Frontend24 — изменённые файлы

Каждый product/test/doc файл завершённого slice; generated logs/artifacts var/target не включены.

| Файл | Назначение |
| --- | --- |
| [docs/adr/0040-frontend-bearer-onboarding.md](adr/0040-frontend-bearer-onboarding.md) | Accepted engineering extension: scoped CORS/history/response cap and unchanged Bearer boundary. |
| [docs/evidence-admission-api.md](evidence-admission-api.md) | Bounded metadata history API and historical/current eligibility distinction. |
| [docs/prompt24-identity-report.md](prompt24-identity-report.md) | Detailed architecture, flow, threat model, tests, limitations and local commit report. |
| [frontend/package.json](../frontend/package.json) | Generator/check commands; OpenAPI drift включён в lint. |
| [frontend/scripts/generate-identity-contract.mjs](../frontend/scripts/generate-identity-contract.mjs) | Генерация44 reachable schemas/types из actual OpenAPI с fingerprint и --check. |
| [frontend/src/app/globals.css](../frontend/src/app/globals.css) | Themed responsive upload/review/Identity components; keyboard focus и bounded text wrapping. |
| [frontend/src/components/async-state.tsx](../frontend/src/components/async-state.tsx) | Abortable reads/actions, refresh generation, scope denial, bounded paging. |
| [frontend/src/components/evidence-registration.tsx](../frontend/src/components/evidence-registration.tsx) | Step1Upload: actual TEXT/URL/multipart/social registration, explicit visual gap. |
| [frontend/src/components/evidence-review.tsx](../frontend/src/components/evidence-review.tsx) | Exact checksum preview, explicit consent, current snapshot, metadata history/withdrawal. |
| [frontend/src/components/failure.tsx](../frontend/src/components/failure.tsx) | Контролируемые shared Problem Details states без raw server/provider errors. |
| [frontend/src/components/identity-editor.tsx](../frontend/src/components/identity-editor.tsx) | Content display/whitelist editor, expectedVersion, approved branching и uncertain write handling. |
| [frontend/src/components/identity-generation.tsx](../frontend/src/components/identity-generation.tsx) | Bounded real Job polling, same-key submit, validated result/citations, guarded DRAFT copy. |
| [frontend/src/components/identity-workbench.tsx](../frontend/src/components/identity-workbench.tsx) | Scoped orchestration, list/history/detail/provenance, separate exact approval and access revocation. |
| [frontend/src/components/onboarding.tsx](../frontend/src/components/onboarding.tsx) | Точка входа actual Brand Identity; общий Failure component. |
| [frontend/src/components/ui.tsx](../frontend/src/components/ui.tsx) | Первый шаг shell отражает доставленный Brand Identity workflow. |
| [frontend/src/lib/api.ts](../frontend/src/lib/api.ts) | Shared transport delegation и typed evidence/Identity client; onboarding contract сохранён. |
| [frontend/src/lib/contract.ts](../frontend/src/lib/contract.ts) | Bounded runtime schema subset validation, safe IDs/integers/UTF8/UTC and request whitelist. |
| [frontend/src/lib/identity-api.ts](../frontend/src/lib/identity-api.ts) | Fixed typed routes, scoped binding, exact preview/checksums/selectors, coherent Jobs/DRAFT receipts. |
| [frontend/src/lib/identity-contract.generated.ts](../frontend/src/lib/identity-contract.generated.ts) | 44 generated actual OpenAPI types/schemas; edit only through generator/schema. |
| [frontend/src/lib/problem.ts](../frontend/src/lib/problem.ts) | Вынесенный existing controlled ApiError/Problem Details parser. |
| [frontend/src/lib/transport.ts](../frontend/src/lib/transport.ts) | Shared bounded Bearer transport, JSON/multipart/PATCH/key, omitted cookies/cache and deadlines. |
| [frontend/tests/browser/identity.spec.ts](../frontend/tests/browser/identity.spec.ts) | 23 Chromium lifecycle/security/conflict/withdrawal/theme/accessibility scenarios. |
| [frontend/tests/support/identity.ts](../frontend/tests/support/identity.ts) | Typed deterministic fixtures, SHA256 and scoped audit samples; only test tree. |
| [frontend/tests/unit/identity-api.test.ts](../frontend/tests/unit/identity-api.test.ts) | Trust-boundary/API/selector/checksum/unknown/foreign/branching/auto-approval negatives. |
| [src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java](../src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java) | Bounded paged receipt query. |
| [src/main/java/com/cadence/application/repositories/EvidenceAdmissionWithdrawalRepository.java](../src/main/java/com/cadence/application/repositories/EvidenceAdmissionWithdrawalRepository.java) | Bounded scalar withdrawal metadata projection. |
| [src/main/java/com/cadence/application/results/EvidenceAdmissionHistory.java](../src/main/java/com/cadence/application/results/EvidenceAdmissionHistory.java) | Metadata-only immutable history DTO с whole-sourceVersion withdrawal audit. |
| [src/main/java/com/cadence/application/usecases/ReviewEvidenceUseCase.java](../src/main/java/com/cadence/application/usecases/ReviewEvidenceUseCase.java) | Scoped OWNER history use case под existing parent locks; metadata paging/cap. |
| [src/main/java/com/cadence/presentation/EvidenceAdmissionController.java](../src/main/java/com/cadence/presentation/EvidenceAdmissionController.java) | GET history DTO/validation/no-store endpoint. |
| [src/main/java/com/cadence/security/SecurityConfiguration.java](../src/main/java/com/cadence/security/SecurityConfiguration.java) | Explicit route/method/header CORS extension; no authentication architecture change. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | History schema/route и исправленные stale Identity descriptions; generated-client source. |
| [src/test/java/com/cadence/security/OnboardingCorsIntegrationTest.java](../src/test/java/com/cadence/security/OnboardingCorsIntegrationTest.java) | Exact allowed methods/headers, denied origins/methods/cookie authority. |
| [src/test/java/com/cadence/support/EvidenceAdmissionApiContract.java](../src/test/java/com/cadence/support/EvidenceAdmissionApiContract.java) | H2/PostgreSQL history, archive, context withdrawal, actual membership, role/scope/paging tests. |
| [frontend/scripts/qualify-identity-stack.mjs](../frontend/scripts/qualify-identity-stack.mjs) | Test-only browser→real local HTTP backend с ephemeral signed JWT/JWKS, fresh H2/local fake и owned cleanup. |
| [frontend/scripts/verify-export.mjs](../frontend/scripts/verify-export.mjs) | Дополнительные production artifact forbidden markers для stack harness. |
| [docs/prompt24-changed-files.md](prompt24-changed-files.md) | Полный manifest каждого изменённого файла. |
| [docs/frontend-foundation.md](frontend-foundation.md) | Setup/навигация24 и воспроизводимый isolated real HTTP harness с явными live blockers. |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальный результат24 и следующий scope25 после qualification, сохранены provider/live boundaries. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронный русский план24/25 и подтверждённые результаты. |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Execution ledger24, фактические gates и remaining scopes. |
| [docs/issue-sync.md](issue-sync.md) | Локальный статус #3/#107; без выдуманных remote edits/CI/closure. |
| [docs/astra-medium-mvp-prompts.md](astra-medium-mvp-prompts.md) | Статус и evidence24, стабильная нумерация и prerequisites сохранены. |
| [docs/prototype-ui-adaptation-plan.md](prototype-ui-adaptation-plan.md) | Actual Step1 mapping/result24 и исторический23 без rewriting. |
| [src/main/java/com/cadence/security/CadenceIdentityService.java](../src/main/java/com/cadence/security/CadenceIdentityService.java) | Bounded post-rollback bootstrap recovery; current DB role/membership/active state, canonical subject validation. |
| [src/test/java/com/cadence/security/CadenceIdentityServiceTest.java](../src/test/java/com/cadence/security/CadenceIdentityServiceTest.java) | 10 recovery/deny-by-default/subject boundary unit cases. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Deterministic two INSERT PostgreSQL race, equal committed identity and no union of losing role/workspace claims. |
| [.gitattributes](../.gitattributes) | LF для frontend/OpenAPI при core.autocrlf=true; WOFF2 явно binary, стабильный generated-contract fingerprint на Windows/Linux. |
