# UI rework23–24 — Cream Orbit

Дата:2026-10-20. Baseline `e35d4e1e6a73246fdb390af48b56437c80431517`, `mvp`.
**QUALIFIED LOCAL — frontend scope23–24.** Один агент. User diff design README и `.agents/` сохранены.
Push/remote writes/CI/merge/deployment не выполнялись. Backend28 и delivered09–24 не реализованы заново.

## Что было сделано

Cream Orbit перенесён в рабочий Next.js/React/TypeScript `frontend/`: public landing/common shell,
responsive navigation, actual Clerk SDK entry, existing Workspace/Brand create/list/read и evidence
→ exact review/consent → Identity DRAFT → edit → separate approval; current/withdrawal/history/citations.
Manager и Identity — разные views. Strategy/Sample content/Performance явно недоступны до25–27.
Не добавлены anonymous analysis, billing, synthetic domain/timers/results или новые backend workflows.

[Каждый изменённый файл](ui-rework23-24-changed-files.md).
[Screenshots, fingerprints и raw design results](design/ui-rework/implementation/README.md).
Actual #3/#107 read-only: OPEN, remote last updated2026-10-18; старые pending23/24/backend28
сопоставлены с local code/reports. Backend28 local qualification подтверждена отдельным report28.

## Как это работает

Server `page.tsx` статически компонует client presentation. `CreatorShell` владеет только appearance
и native menus; `PublicLanding` не принимает source input. Real SignIn/SignUp/UserButton в AuthRoot
сохранены. User/session React key из Clerk unmounts private state при смене session.

Onboarding выбирает view через SSR-safe useSyncExternalStore/hash: только `#manager`/`#identity`,
никаких private IDs/tokens в URL. Workspace меняет Brand на null; BrandDetails/IdentityWorkbench
keyed по scope. View/Brand/session switch unmounts review/editor, aborts I/O; Back заново читает
сервер и не восстанавливает незавершённый consent из cache. Appearance не remounts business forms.

IdentityWorkbench сохраняет existing useAction/useResource, scoped clients, selectors/checksum,
server Job reads/result validation, current read/withdrawal и expectedVersion/separate approval.
Desktop sources/review и draft/history — две равные колонки с24px gap; ниже1100px одна колонка.
Registration можно свернуть native details. Raw source/model text остаётся escaped data.

Fixed header измеряется scoped ResizeObserver: CSS `--header-height` меняется при resize/font/auth
control changes; observer/listeners disconnects на unmount. Long Workspace name визуально bounded,
полный accessible name сохраняется. Heading/controls не закрываются fixed nav. Base CSS видима;
формы не ждут GSAP/таймер. Shared SkipLink переводит focus/scroll, не меняя hash view. Reduced motion, keyboard и footer pause имеют отдельные состояния.

## Архитектурное решение

ADR0040 сохранён: static Next.js + official Clerk React SDK + explicit browser Bearer. No BFF,
Server Actions, custom auth или cookie API authority. Backend OWNER/current membership/resource
checks независимы от UI. Existing API clients,44-schema generated DTO, OpenAPI и lockfile не изменены.

| Вариант | Security/correctness | Trade-off |
| --- | --- | --- |
| Перенести prototype целиком | Потеря actual auth/DTO/version/revocation guards; synthetic results | Меньше JSX work, но неверная authority/data модель |
| Новый cookie/BFF ради styling | Новая CSRF/secret/deployment boundary | Дополнительная сложность без требуемого выигрыша |
| Presentation-only на existing clients — выбран | Сохраняет existing contracts и regression evidence | Нужна аккуратная адаптация layout/navigation |

Assets скопированы локально из approved prototype, SHA/size inventory в frontend/public/assets/README.md;
fonts сохраняют OFL. No new dependencies/CDN/external media.21 reference src/public/package files
unchanged от accepted `aa240ae`; fingerprint compact JSON SHA256
`f426739cfad635f104caff5b2aee2705043a7ef6c4f416d42727585adff56bac`.

Применены design-taste-frontend (whole app-screens reference/inspector), emil-design-eng,
mobile-native, animate, animation-performance. Новый random direction/photo/video floor не применялся:
пользователь уже выбрал reference и отложил motion polishing. Motion сейчас CSS pointer press/chevron/
footer. Prototype GSAP scenes не инстанцируются; global killAll/timelines/ScrollTriggers отсутствуют.
Cinematic intro/text/scroll polishing остаётся поздней ручной работой; settled composition сохранена.

| Before | After | Why |
| --- | --- | --- |
| Card-with-shadow baseline | Open Cream Orbit plane, fixed rails/topbar, serif logo/orbit | Выбранная композиция, различие public/app scales |
| Fake public upload/pillars/checkout | Informational panel → actual Clerk/Workspace | Нет anonymous ownership/privacy/billing contract BG01/BG02 |
| Manager forms рядом с Identity | Отдельные views, Workspace menu/Brand selector | Явный scope, не смешивать onboarding и exact review |
| Theme background transition | Atomic foreground/background changes | Исправлен подтверждённый transient contrast defect |
| Предполагаемая fixed header height | ResizeObserver + bounded scope label | Wrap/viewport/account control не перекрывают content |

## Security Review

Защищаем tokens, private tenant data, reviewed snapshots/consent, approved history и provenance.
Caller может быть malicious authenticated user; boundaries browser↔Clerk↔API и source/model data↔DOM.
Если UI check обойдён, backend по-прежнему требует actual authorization/version/source eligibility.

Threat: foreign IDs или late response возвращают previous private data/consent.
Protection: unchanged scoped clients/DTO response binding, session/scope keys, abort + completion guards;
hash выбирает только view. Backend OWNER/membership checks остаются отдельным слоем.
Verification: Workspace/Brand/session switching, delayed preview/list, forged successful foreign DTO,
section Back и Workspace menu regressions; old text/form/checkbox не восстанавливаются.

Threat: stale/withdrawn source превращается в consent или approval без новой проверки.
Protection: exact server selectors/checksum/version,409 invalidates preview/checkbox; generation →
explicit DRAFT copy → expectedVersion edit → committed read → separate reviewed approval.
Verification: stale409/fresh context, current read/withdrawal/history, withdrawal-after-draft approval
denial, forged citations, approved branch/inherited DEMONSTRATION ORIGIN; automatic approvals0.

Threat: XSS, data/credential leakage, mass assignment, SSRF, replay/resource abuse.
Protection: React escaped text/hex-only swatches, fixed local assets, existing DTO whitelist/validated
API origin/omit cookies/no-store/redirect error/deadline/response caps; no public upload/fetch/tools,
no new browser storage, no blind POST retries. No secret keys or roles in new fields/logs.
Verification: malicious names/source text, foreign DTO/fields, bounded input/timeout/uncertain submit,
export fixture/CDN/domain isolation. SQL/SSRF/scanners unchanged; no new backend qualification claim.

Threat: inaccessible controls/themes/motion or stale observers across routes.
Protection: native details/select/button, Escape/outside/focus return, scoped cleanup, >=16px inputs,
safe-area/viewport-fit, capability hover, pointer-only120ms press, pause/reduced motion.
Verification: actual keyboard/axe,3 themes,320–1440 layouts, header/h1 geometry, no horizontal overflow.

## Runtime Flow

```text
Public HTML → actual Clerk SignIn/SignUp → useAuth user/session key
  → explicit Workspace + Brand → fresh scoped Brand read
  → CadenceApi / IdentityApi / ApiTransport (Bearer, omit cookies, bounded/no-store)
  → existing backend OWNER/membership/domain/PostgreSQL guards
  → register source → ingest Job → exact server snapshot → explicit admission
  → current snapshot / withdrawal/history
  → generation Job → validated result/citations → explicit Identity DRAFT copy
  → expectedVersion edit → committed read → separate approval → immutable history
```

## Тесты и воспроизводимость

В root frontend выполнялись `pnpm typecheck`, `pnpm test`58/58 и `pnpm exec prettier --write ...`.
Чтобы не читать `.env` и не трогать user dev server, full gates выполнялись в exact source копии
`var/ui-rework-qualified/frontend/`, исключающей env: [71-input manifest](design/ui-rework/implementation/qualified-source-manifest.json).
OpenAPI copied byte-for-byte. Existing lockfile/toolchain; после sandbox junction restriction pnpm
materialized206 packages из local store,0 downloads. Versions/lockfile unchanged. Builds использовали
`NEXT_TELEMETRY_DISABLED=1`; actual static setup-required test подтверждает fail-closed config.

| Command (cwd exact frontend copy) | Actual result |
| --- | --- |
| `pnpm lint` | PASS: ESLint0 warnings,44 generated schemas in sync, Prettier |
| `pnpm typecheck` | PASS,0 TypeScript diagnostics |
| `pnpm test` |3 files,58 tests PASS,0 failures |
| `pnpm build` | Next16.4.2 static routes `/`, `/sign-in/`, `/sign-up/`, `/workspaces/`, not-found PASS |
| `pnpm verify:export` |23 HTML/JS/CSS,3 WOFF2 +3 bounded PNG; fixture/loader/CDN absent |
| `pnpm test:e2e` |66 PASS,0 failure/skipped/flaky,38.5s; Chromium/axe |

После successful full62-run добавлены4 responsive browser tests и keyboard focus geometry check.
Финальный CSS focus scroll margin связан с measured header; все6 gates повторены на итоговом source. [Actual browser stats](design/ui-rework/implementation/browser-summary.json).
Final logs: `var/ui-rework-qualified/accepted-{lint,typecheck,unit,build,export,browser}.log`.
Предыдущие exploratory и successful logs сохранены отдельно, не заменяют final run.
Warning: FORCE_COLOR/NO_COLOR conflict у Playwright. Functional warnings/failed final gates нет.

Exploratory failures не считаются pass: slice1 contrast4 failures → atomic colors; private60-run2
pointer timeouts → fixture session control перенесён в account slot; следующий62-run5 failures →
fixture label mismatch и неполный second-Brand GET route. Исправлены explicit Brand label binding,
accessible fixture label и actual route; assertions не ослаблялись. Targeted2/2, затем full62/66 PASS. Визуальный review выявил skip/hash regression: old raw anchor
менял Identity на Manager. Новые assertions URL/view/consent дали4/4 red; shared client SkipLink
с preventDefault/focus/scroll сохранил route и consent. Все6 gates повторены; final66/66 PASS.

`git diff --check` и `python var/verify-ui-rework-implementation.py` — PASS:
53 changed files,16 Markdown,572 local links,71 qualified inputs,21 reference checksums, aligned plans.
`.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check` — BUILD SUCCESS (format only).
`python docs/design/ui-rework/verify_review.py` запускался и отказал по своему **docs-only** baseline:
`Unexpected product change: docker/application/Dockerfile` (backend28). Он неприменим к product
implementation/revised HEAD, не ослаблялся и не называется passed gate. Отдельный implementation
link/source audit выполнен. Backend/pom/SQL/OpenAPI/clients/auth config не менялись; full Maven/PG/
startup/security scanners backend28 заново не запускались. Исторические tests находятся в report28.

## Security Tests

401 redirect/no protected calls;403/404 denial;409 stale consent/edit;503 unavailable; unknown Job
outcome;15s timeout; uncertain create без automatic retry и generation retry с тем же key/body.
Foreign IDs/DTO/citations, unsafe text/markup, wrong scope, unchanged readonly audit fields;
exact consent отдельно от approval, withdrawal-before-approval, immutable approved edit lineage.
Production export не содержит fixture/test token/prototype domain/CDN. Real network egress из
UI contract tests отсутствует. Browser mocks проверяют клиента, не Clerk cryptography/server DB.

## Edge Cases

TEXT/PDF/URL/social reviewed projections; retained image REQUIRES_PROVIDER; unsupported legacy
file provenance. Missing/expired scan и unavailable storage/model представлены controlled backend
errors, не synthetic observations. Current history не выдаётся за current eligibility.
Workspace/Brand/session switches + held late replies; Browser Back clears uncommitted consent;
Appearance preserves forms/checkbox. Long unsafe Workspace320px, bounded pagination, native keyboard,
3 themes, header wrap, mobile reflow, reduced motion/pause; no automatic result/approval.

## Visual verification

Prototype inspected в browser до переноса; before actual landing, final1440/768/390 screenshots,
Identity320/768/390/1440 и Manager1280/375 сравнены. [Evidence/review](design/ui-rework/implementation/README.md).
App error-state inspector0 FAIL/1 palette WARN; public5 FAIL/7 WARN; source design lint1 placeholder
FAIL/19 WARN. Эти **generic design heuristics не объявлены pass**: accepted fixed rails/Cream/no
photo/video/motion floor/footer/meta scale конфликтуют с ними. Каждое исключение объяснено в evidence
README. URL example.com — native input placeholder, не invented source/customer proof. Ни security
scanner, ни axe не подавлены; `pnpm lint` — отдельный successful ESLint/format/schema gate.

## Непроверенные аспекты

Physical iPhone/Android/Safari, browser zoom200%/screen-reader hardware smoke и low-end GPU performance
не проверялись. Viewport emulation не доказывает native keyboard/safe-area/rubber-band поведение;
нужен hardware smoke по mobile-native checklist до release.
Live Clerk setup существовал и проходил user manual smoke19 октября. В этом run `.env` не читался,
real credentials/account не использовались; live signup/token/OWNER self-provisioning E2E не запускалась.
Это **не утверждение об отсутствии настроенных keys**. Fixture не доказывает live auth/browser→real API.
Real AI/media/social adapters и production hosting/CI/release остаются separate qualification.

## Что мне важно понять

Красивый экран не создаёт успешную операцию: UI показывает server receipt/result, а не результат
таймера. Consent привязан к показанному snapshot, approval — к committed revision. Потеря
незавершённого checkbox при смене scope безопаснее переноса на другой Brand. Approved history
не зависит от темы и навигации. Shared tokens/layout можно менять без смены authority/DTO.

## Что мне стоит изучить

1. Граница доверия (trust boundary): Clerk удостоверяет user, backend отдельно проверяет ресурс.
2. Изоляция состояния (state isolation): React scope key + abort отвергают stale completion.
3. Оптимистическая версия (optimistic locking): expectedVersion/checksum связывают решение с увиденными данными.
4. Static export/hydration: public HTML не содержит private SSR data; auth начинается в browser SDK.
5. Accessibility: semantic controls/focus/contrast/reduced motion проверяются отдельно от визуального вкуса.

## Production considerations / Git

Нужны25–27, frontend/cross-stack28/creator E2E, live Clerk/OWNER provisioning, provider/privacy/cost
qualification, hosting CSP/TLS/Clerk authorized origins/exact CORS и device tests. No billing,
anonymous upload или direct publishing promises. Local works ≠ production ready.
Slice1 `edf2d76`; final private/docs slice — commit, содержащий этот report/manifest (см. git log).
No push/new CI/issue writes/merge/deployment. User README diff/.agents не входят в commits.

## Факты, выводы и assumptions

**Факты:**44-schema/71-input fingerprints проверены;58 unit/66 browser, lint/typecheck/build/export
прошли; backend/lib clients/lockfile unchanged; prototype fingerprint unchanged, user changes сохранены.
**Инженерный вывод:** presentation-only перенос сохранил проверяемый existing lifecycle и уменьшил
новые authority/data boundaries. **Assumptions/remaining:** real Clerk/provider/hosting/device среда
требует собственной qualification; fixtures этого не доказывают.

## Последующее уточнение

После ручной проверки пользователь запросил упрощение Workspace menu, hero и footer.
Актуальная дельта описана в [отчёте уточнений2026-10-20](ui-rework23-24-refinements.md).
Выше сохранены результаты первоначального переноса; упоминания footer pause относятся
к первоначальной версии, до удаления decorative motion по запросу пользователя.
