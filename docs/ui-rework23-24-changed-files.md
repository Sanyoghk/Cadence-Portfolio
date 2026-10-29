# UI rework23–24 — изменённые файлы

Baseline `e35d4e1`; оба slices. User design README diff и `.agents/` исключены.
Backend, migrations, API clients, OpenAPI и dependencies/lockfile не изменены.

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Current execution/qualification и следующий25–27 scope. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Та же continuation/qualification на русском; markers aligned. |
| [docs/astra-medium-mvp-prompts.md](../docs/astra-medium-mvp-prompts.md) | Current execution marker без перенумерации. |
| [docs/design/ui-rework/backend-backlog.md](../docs/design/ui-rework/backend-backlog.md) | BG09 backend28 local pass vs remaining live/cross-stack gates. |
| [docs/design/ui-rework/design-system.md](../docs/design/ui-rework/design-system.md) | Working tokens/layout/motion handoff; prototype history preserved. |
| [docs/design/ui-rework/implementation/README.md](../docs/design/ui-rework/implementation/README.md) | Screenshot comparison/critique и explanation всех design flags. |
| [docs/design/ui-rework/implementation/before-landing-desktop.jpg](../docs/design/ui-rework/implementation/before-landing-desktop.jpg) | Actual local browser screenshot, synthetic test data only. |
| [docs/design/ui-rework/implementation/browser-summary.json](../docs/design/ui-rework/implementation/browser-summary.json) | Raw qualification/fingerprint/design-check evidence; no credentials. |
| [docs/design/ui-rework/implementation/error-desktop.jpg](../docs/design/ui-rework/implementation/error-desktop.jpg) | Actual local browser screenshot, synthetic test data only. |
| [docs/design/ui-rework/implementation/error-inspector.json](../docs/design/ui-rework/implementation/error-inspector.json) | Raw qualification/fingerprint/design-check evidence; no credentials. |
| [docs/design/ui-rework/implementation/error-mobile.jpg](../docs/design/ui-rework/implementation/error-mobile.jpg) | Actual local browser screenshot, synthetic test data only. |
| [docs/design/ui-rework/implementation/identity-desktop.png](../docs/design/ui-rework/implementation/identity-desktop.png) | Actual local browser screenshot, synthetic test data only. |
| [docs/design/ui-rework/implementation/identity-mobile.png](../docs/design/ui-rework/implementation/identity-mobile.png) | Actual local browser screenshot, synthetic test data only. |
| [docs/design/ui-rework/implementation/landing-desktop.jpg](../docs/design/ui-rework/implementation/landing-desktop.jpg) | Actual local browser screenshot, synthetic test data only. |
| [docs/design/ui-rework/implementation/landing-inspector.json](../docs/design/ui-rework/implementation/landing-inspector.json) | Raw qualification/fingerprint/design-check evidence; no credentials. |
| [docs/design/ui-rework/implementation/landing-mobile.jpg](../docs/design/ui-rework/implementation/landing-mobile.jpg) | Actual local browser screenshot, synthetic test data only. |
| [docs/design/ui-rework/implementation/landing-tablet.jpg](../docs/design/ui-rework/implementation/landing-tablet.jpg) | Actual local browser screenshot, synthetic test data only. |
| [docs/design/ui-rework/implementation/qualified-source-manifest.json](../docs/design/ui-rework/implementation/qualified-source-manifest.json) | Raw qualification/fingerprint/design-check evidence; no credentials. |
| [docs/design/ui-rework/implementation/reference-fingerprint.json](../docs/design/ui-rework/implementation/reference-fingerprint.json) | Raw qualification/fingerprint/design-check evidence; no credentials. |
| [docs/design/ui-rework/implementation/source-design-lint.txt](../docs/design/ui-rework/implementation/source-design-lint.txt) | Raw qualification/fingerprint/design-check evidence; no credentials. |
| [docs/design/ui-rework/navigation-and-system.md](../docs/design/ui-rework/navigation-and-system.md) | Actual Manager/Identity/Clerk routes и unavailable later views. |
| [docs/design/ui-rework/ui-api-mapping.md](../docs/design/ui-rework/ui-api-mapping.md) | Actual working clients/API mapping23–24. |
| [docs/frontend-foundation.md](../docs/frontend-foundation.md) | Working Cream Orbit foundation/actual guards и remaining gates. |
| [docs/issue-sync.md](../docs/issue-sync.md) | Read-only #3/#107 и local→remote delta; no writes. |
| [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) | Dependencies/slices/checks/remaining ledger. |
| [docs/product-scope.md](../docs/product-scope.md) | Принятый и перенесённый UI; unchanged product boundaries. |
| [docs/ui-rework-plan.md](../docs/ui-rework-plan.md) | Approved design → local implementation статус. |
| [docs/ui-rework23-24-changed-files.md](../docs/ui-rework23-24-changed-files.md) | Каждый изменённый файл и назначение. |
| [docs/ui-rework23-24-report.md](../docs/ui-rework23-24-report.md) | Полный implementation/security/tests/limitations report. |
| [frontend/public/assets/README.md](../frontend/public/assets/README.md) | Approved generated artwork provenance/size/SHA inventory. |
| [frontend/public/assets/orbit.png](../frontend/public/assets/orbit.png) | Local exact copy принятого decorative asset; не private evidence/media. |
| [frontend/public/assets/paper.png](../frontend/public/assets/paper.png) | Local exact copy принятого decorative asset; не private evidence/media. |
| [frontend/public/assets/scroll-line.png](../frontend/public/assets/scroll-line.png) | Local exact copy принятого decorative asset; не private evidence/media. |
| [frontend/scripts/serve-export.mjs](../frontend/scripts/serve-export.mjs) | Correct PNG MIME for nosniff static verification server. |
| [frontend/scripts/verify-export.mjs](../frontend/scripts/verify-export.mjs) | Prototype/test domain marker denial and bounded local PNG validation. |
| [frontend/src/app/globals.css](../frontend/src/app/globals.css) | Cream Orbit themes/app layout/forms/review/focus/responsive/reduced motion. |
| [frontend/src/app/layout.tsx](../frontend/src/app/layout.tsx) | Static metadata/viewport-fit/theme-color/CSS imports. |
| [frontend/src/app/page.tsx](../frontend/src/app/page.tsx) | Static composition of actual client landing. |
| [frontend/src/app/public-landing.css](../frontend/src/app/public-landing.css) | Reference public hero/curtain/plans/about/footer composition. |
| [frontend/src/components/auth.tsx](../frontend/src/components/auth.tsx) | Cream Orbit auth layout; unchanged Clerk/Session authority. |
| [frontend/src/components/creator-shell.tsx](../frontend/src/components/creator-shell.tsx) | Shared fixed shell/menus/appearance/observer/listener cleanup. |
| [frontend/src/components/evidence-registration.tsx](../frontend/src/components/evidence-registration.tsx) | Reference star artwork; unchanged upload/registration contracts. |
| [frontend/src/components/identity-workbench.tsx](../frontend/src/components/identity-workbench.tsx) | Collapsible registration + source/draft columns; unchanged exact lifecycle handlers. |
| [frontend/src/components/onboarding.tsx](../frontend/src/components/onboarding.tsx) | Manager/Identity/hash Back/Workspace menu/Brand selector; scope guards preserved. |
| [frontend/src/components/public-landing.tsx](../frontend/src/components/public-landing.tsx) | Public information/actual entry links; no demo domain/results. |
| [frontend/src/components/ui.tsx](../frontend/src/components/ui.tsx) | Approved local star wordmark; existing button/workflow primitives. |
| [frontend/tests/browser/cream-orbit.spec.ts](../frontend/tests/browser/cream-orbit.spec.ts) | 4 public viewport/theme/axe/local-egress/pause/menu tests. |
| [frontend/tests/browser/identity.spec.ts](../frontend/tests/browser/identity.spec.ts) | Brand late response/Back/Workspace switch +4 responsive exact-consent regressions. |
| [frontend/tests/browser/onboarding.spec.ts](../frontend/tests/browser/onboarding.spec.ts) | New real landing expectation; onboarding/security assertions preserved. |
| [frontend/tests/browser/visual-shell.spec.ts](../frontend/tests/browser/visual-shell.spec.ts) | Keyboard/menu/Appearance and revised semantic structure/session assertions. |
| [frontend/src/components/skip-link.tsx](../frontend/src/components/skip-link.tsx) | Shared keyboard skip без смены hash/view; static fallback preserved. |
| [frontend/tests/fixture/index.html](../frontend/tests/fixture/index.html) | Удалён отдельный raw skip; fixture использует shipped component. |
| [frontend/tests/fixture/main.tsx](../frontend/tests/fixture/main.tsx) | Test session switch in account slot, accessible compact label; production isolation unchanged. |

Всего: 53 файла. Коммиты: public/shared slice edf2d76, final private/docs slice содержит этот manifest.
