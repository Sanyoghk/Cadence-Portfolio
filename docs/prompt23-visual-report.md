# Visual follow-up23 — перенос Cadence Prototype

## Что было сделано

**QUALIFIED LOCAL, 2026-10-18.** Существующий demo-дизайн перенесён в standalone frontend:
shell, landing/auth presentation и actual Workspace/Brand onboarding. Сохранены узнаваемые
panel/header/stepper/cards, serif/italic typography и Cream/Dark/Sage. Foundation23 не
реализовывался заново. Следующий frontend scope24;24–27 не delivered этим изменением.

Baseline: `mvp`, clean `14805a54514a95c4054dacfc89bb5d7f4b74dc7d`. Actual GitHub #3/#107
перечитаны: оба open. Qualified foundation `3b49c94`, accepted ADR0040 и source mapping проверены.
User reference2441390bytes, SHA256
`2f15d3a196af190b0d9732a32cb6223e1d05cf285a9d01b6a33a4d20a016a068` совпал до/после работы.
HTML не изменён и не помещён в production bundle.

## Inventory и browser inspection

43 embedded assets:3 vendor JS (React/ReactDOM/Babel),7 JSX modules (shared UI,4 screens,
app и host Tweaks helper),33 WOFF2 files. Основные tokens: ink#1A1814, muted#6B665F,
tan#B88C5D/deep#8E6A41, warm paper/cream, translucent rules,20px outer radius,
14px cards, pill buttons/chips, circular step numbers, line icons. Serif — Cormorant
Garamond, sans — DM Sans; monospace reference использует JetBrains Mono/system fallback.
App defaults дополнительно выбирают accent#F2A900, но это не разрешение копировать низкий
контраст для важного текста. Dark меняет surface/inversion; Sage — muted green accents.

Browser URL policy отклонила original `file://` navigation. Этот запрет не обходился
через выполнение raw bundle на другом origin. Подготовлен materially safer static preview:
audited shared/initial-screen/app JSX → existing TypeScript transpilation и React static
render в ограниченном context → inert HTML. Original loader/vendor JS/host Tweaks protocol
не исполняются browser; нет scripts/iframes/links to external resources, strict CSP с
default-src none, только inline CSS и reviewed embedded font data. Effects/event handlers
не активны, navigation href удалены, animation отключена для стабильного capture.
Server отдавал только3 fixed read-only pages на loopback, не произвольные files/paths.
Это browser inspection исходной композиции через безопасную static projection,
не qualification интерактивности старого bundle. Live/static source effects не выдаются за tests.

До implementation осмотрен Cream preview, source JSX/CSS и external-boundary inventory.
После implementation — все3 reference themes и actual screenshots при1280/375px,
дополнительно actual320px stress case и public static shell в in-app browser.
Reference capture: scripts0/external network requests0 для всех6 pages; target screenshots
и axe/overflow checks — отдельные automated browser tests. Pixel identity не заявляется.

## Как это работает / Runtime Flow

```text
Clerk session (existing AuthRoot/SessionGate)
    ↓ existing userId:sessionId key
Onboarding → CreatorShell (only ephemeral appearance state)
    ↓ existing explicit selection / abort / scoped response validation
CadenceApi → explicit Bearer → existing /api/v1 Workspace/Brand API
    ↓ backend current OWNER/membership/resource authorization (unchanged)
Scoped DTO → escaped React text/forms → chosen presentation theme
```

CreatorShell меняет только CSS variables через allowlisted data-theme. Не хранит identity,
tokens или private data и не меняет request policy. Cream default; theme state не persistent
и сбрасывается при remount/session change. Theme switch сохраняет текущую форму и не
повторяет mutation. WorkflowSteps — semantic region/list с current Brand setup; будущие
Strategy/Content/Performance explicitly not connected, без pretend navigation/results.

Actual Workspace context заменяет demo client. Existing create/list/select/Brand read,
expected DTO fields, loading/empty/archived/error handling и uncertain POST behavior сохранены.
Source/API/token/membership/cache semantics не менялись. Привязка API к реальному backend
существует; browser tests используют isolated fixture и не доказывают live Clerk→backend login.

## Архитектурное решение / обоснованные отличия от reference

| Reference | Выбранная адаптация / причина |
| --- | --- |
| Agency host/iframe chrome, EF/client/90-day sprint | Standalone Cadence C mark, creator header, actual Workspace/industry. Ownership задаёт backend, не demo |
| Brand upload/AI progress первым экраном | Workspace/Brand profile до evidence screen24; не обещаем ещё не подключённый analysis |
| Clickable div stepper и fake future panels | Semantic workflow map, явное not connected yet. Keyboard controls только для actual operations |
| Fixed horizontal layout, clipped mobile content | Responsive4→2 step columns и2→1 workspace columns, wrapping,44px controls. Проверено375/320px |
| Low-contrast Dark/raw light input backgrounds | Semantic complete theme tokens, readable error/field/primary/focus colors; axe во всех themes |
| Pale gold/tan text | Более тёмный Cream accent#86623E, Sage#4B6850 и light Dark accent; сохраняем palette, повышаем contrast |
| Inline SVG EF, contenteditable/auto-save/demo controls | Fixed geometric SVG/Cadence mark, native forms/buttons/select, без fabricated autosave/history |
| Google Fonts links/preconnect |3 unmodified local WOFF2 assets + official OFL notices; no CDN/runtime font requests |

Reuse компонентов выбран вместо iframe/raw HTML/runtime Babel и вместо нового независимого
дизайна. Trade-off: часть presentation переписана в typed components, но existing auth/API
boundary и maintainable dependency graph сохраняются. Новых npm dependencies/versions нет;
pnpm-lock.yaml unchanged. Media/images и чужие agency assets не переиспользовались.

Font provenance, sizes/SHA256/manifest IDs и license references:
[inventory](../frontend/public/fonts/README.md). Официальные
[Cormorant OFL](https://github.com/google/fonts/blob/main/ofl/cormorantgaramond/OFL.txt) и
[DM Sans OFL](https://github.com/google/fonts/blob/main/ofl/dmsans/OFL.txt) проверены18 октября;
copyright/OFL1.1 сохранены в export. Только Latin subsets; иные glyphs используют system
fallback. WOFF2 signature/declared length/size bounds и Chromium loading проверены, это не
exhaustive font parser audit. No custom decoder или live font privacy decision.

## Security Review

Threat: untrusted original scripts, host postMessage protocol, external font/media egress.
Protection: original loader/Tweaks/vendor scripts не импортируются; local licensed fonts,
fixed SVG, no raw HTML injection. Export verifier проверяет deployed artifacts и local fonts.
Verification: actual export scan21HTML/JS/CSS, deliberate fixture leak rejected, no external
requests в six theme/viewport tests; reference CSP/scripts0/network0.

Threat: styling скрывает BOLA/tenant/session leak или превращает будущий экран в approval.
Protection: existing OWNER/backend authority, CadenceApi DTO scope, user/session React key,
Workspace keyed state/AbortController/late guards unchanged. Stage map не выдаёт mutation.
Verification: existing foreign response/XSS/role/error/archival cases, Workspace switch/
late reply и added session-key remount test. Последний моделирует component lifecycle в
fixture, не реальную смену Clerk accounts. Live auth qualification остаётся blocked.

Threat: XSS/raw errors/secrets leakage через новые context chips или font paths.
Protection: validated DTO → escaped React text, fixed CSS URLs/SVG paths; no URL rendering,
token storage/logging/raw detail. No writable role/actor/owner fields.
Verification:320px long malicious name stays text;401/403/404/409 private detail hidden;
no API/config/auth-boundary/backend source change, no new credentials read/copied.

Threat: accessibility regressions закрывают keyboard-only пользователю actual operations.
Protection: landmarks, visible focus, native select/buttons/labels, allowlisted theme,
semantic current steps, readable contrast, reduced-motion rule.
Verification: axe including all themes/error states, keyboard skip/theme/Workspace selection,
no horizontal overflow375/320px. Found wordmark/landmark defects исправлены без weakening tests.

Auth architecture остаётся ADR0040 explicit Bearer, no BFF/API cookie authority, current
scoped CORS. Новых CSRF/SSRF/SQL/command surfaces нет: styling не добавляет fetch/proxy/backend
endpoints. Frontend не считается защитой backend; server authorization не заменялась темой.

## Изменённые файлы

- `.github/workflows/verify.yml` — existing frontend job запускает export-isolation gate после build.
- `frontend/package.json` — только script `verify:export`, dependencies unchanged.
- `frontend/scripts/verify-export.mjs` — actual static artifacts, forbidden fixture/loader/CDN markers, bounded WOFF2 checks.
- `frontend/src/components/creator-shell.tsx` — shared panel/header/context/footer и allowlisted appearance state.
- `frontend/src/components/ui.tsx` — fixed SVG, Cadence Wordmark, Chip, Button и semantic WorkflowSteps.
- `frontend/src/app/globals.css` — prototype tokens/local fonts/themes/forms/focus/desktop/mobile presentation.
- `frontend/src/app/page.tsx` — existing public landing в общей композиции, navigation destinations unchanged.
- `frontend/src/components/auth.tsx` — только AuthLayout presentation в CreatorShell; Clerk/Session код unchanged.
- `frontend/src/components/onboarding.tsx` — presentation/workflow/card layout и shared Button; existing business state/API flow сохранены.
- `frontend/tests/browser/visual-shell.spec.ts` —17 new theme/viewport/error/keyboard/session/long-input tests.
- `frontend/tests/fixture/main.tsx` — isolated session-key remount harness; никогда production entry.
- `frontend/tests/fixture/vite.config.ts` — test-only publicDir для same actual local font assets.
- `frontend/public/fonts/cormorant-garamond-latin-normal.woff2` — reviewed37776-byte font asset.
- `frontend/public/fonts/cormorant-garamond-latin-italic.woff2` — reviewed39304-byte font asset.
- `frontend/public/fonts/dm-sans-latin.woff2` — reviewed62556-byte font asset.
- `frontend/public/fonts/Cormorant-Garamond-OFL.txt` — full original copyright/license notice.
- `frontend/public/fonts/DM-Sans-OFL.txt` — full original copyright/license notice.
- `frontend/public/fonts/README.md` — source IDs/checksums/license/security/provenance inventory.
- `DEVELOPMENT_PLAN.md`, `DEVELOPMENT_PLAN_RU.md` — одинаковая qualified-local точка продолжения24.
- `docs/astra-medium-mvp-prompts.md` — visual follow-up выполнен; contract сохранён, номера не меняются.
- `docs/prototype-ui-adaptation-plan.md` — execution update над historical approved plan.
- `docs/frontend-foundation.md` — shared presentation/fonts/verification guide, live blocker сохранён.
- `docs/mvp-prompt-progress.md` — actual gates/status ledger.
- `docs/issue-sync.md` — local #3 mapping, без ложного remote update/closure.
- Этот report — review, exact checks, deviations/evidence/remaining scope.

## Тесты / фактические команды

В `frontend/`:

```text
pnpm exec prettier --write src tests
pnpm exec prettier --write package.json scripts/verify-export.mjs public/fonts/README.md
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm verify:export
pnpm peers check
pnpm audit --audit-level=low
pnpm test:e2e
pnpm exec playwright test tests/browser/visual-shell.spec.ts -g "native Appearance"
pnpm test:live
```

Final lint/Prettier PASS; standalone typecheck exit0; unit2files/35tests PASS; Next16.4.2
static build PASS, all existing routes exported. Export isolation21HTML/JS/CSS +3WOFF2 PASS;
test marker deliberately inserted in a new unique probe file → gate rejected → probe removed
in finally → clean export PASS. Peers PASS; fresh audit «No known vulnerabilities found».
Final Chromium browser **33/33 PASS**,0 failures/skips,21.3s:16 existing +17 new tests.
Targeted keyboard regression **1/1 PASS** before final full browser run.

Первый16-test run:13pass/3fail (wordmark accessible name + missing landmarks); исправлена
реализация. Следующий33-test run:32pass/1fail — driver Enter открывал native select menu;
проверка исправлена на ArrowDown→Tab с дополнительным assertion dark value, без изменения
UI/ослабления focus assertion. Final33 pass подтверждает correction. Один combined sandbox
lint/type runner завис без оставшегося tsc process; остановлен, standalone typecheck прошёл.
Initial reference recapture один раз прерван Page.captureScreenshot protocol error; после
проверки DOM/font dimensions capture6/6 прошёл. Это diagnostic failure, не successful run.

`pnpm test:live` **exit1 / BLOCKED** до Clerk calls: отсутствуют development keys/test user/
running live setup. Не skip/pass, не live signup/login. Browser fixture не production shortcut.
Warnings: inherited NO_COLOR/FORCE_COLOR runner notices и Git CRLF normalization; не test failures.

В корне repository:

```text
node target/render-safe-reference.cjs
node target/capture-safe-reference.cjs
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
git diff --check
```

Static reference6theme/viewport captures PASS,0 scripts/external requests. Spotless BUILD
SUCCESS:631Java+1POM clean by cache,0 changes needed. Full Maven/PostgreSQL/Python/Linux
backend suites не запускались: backend/source/API/auth policy/schema/dependencies unchanged.
Historical full2270 из foundation23 не объявляется новым run этого UI slice.

Docs inspection через Python stdlib:9 Markdown files,410 relative links, strict UTF-8/
closed fences и parity текущего раздела обоих планов PASS. `git diff --check` PASS.
Staged check дополнительно нашёл trailing whitespace в двух upstream OFL lines;
whitespace normalized без изменения copyright/license wording, final staged check PASS.
Backend source/POM, API/config/SessionGate/lockfile byte-equivalent baseline; reference
fingerprint unchanged. Full OFL notices byte-equivalent в static export. Workflow YAML
parsed existing Prettier (`pnpm exec prettier ../.github/workflows/verify.yml --parser yaml > $null`),
frontend build включает export gate. PyYAML unavailable; новые packages не устанавливались.26 changed files
соответствуют manifest выше; diagnostic previews/screenshots не staged.

Screenshots (ignored verification artifacts): `var/prompt23-reference-{cream,dark,sage}-{1280,375}.png`
и `var/prompt23-visual-{cream,dark,sage}-{1280,375}.png`. Все target6 осмотрены; reference
desktop palette/shape/typography и mobile clipping сопоставлены. Не pixel-perfect claim:
onboarding content/ownership/controls намеренно адаптированы к actual contracts.

## Security Tests / Edge Cases

401/403/404/409/503 safe states; loading/empty; negative foreign successful response, malicious
names/raw errors; archived Workspace; pending/double-submit/uncertain mutation; pagination;
tenant switch and old profile removal, delayed old read after scope/session remount;
theme change сохраняет unsaved current form; all themes contrast/axe; keyboard skip/theme/
Workspace;375px/320px wrapping и no horizontal overflow; production fixture route404,
deployed-artifact leak rejection, fail-closed missing Clerk config. Actual browser form
payload allowlist и exactly one Brand create остаются в existing test.

## Непроверенные аспекты / Production considerations

Live Clerk signup/login/JWT issuance/account revocation и browser→real backend session
не qualified без credentials. Real Clerk widget appearance в Dark/Sage тоже требует live
проверки; SDK internals не переписывались ради theme. Manual screen-reader audit, browser
matrix кроме Chromium, deployed CSP/TLS/Clerk domains и production hosting/load не verified.
Production font-src self и existing Clerk/API origins должны войти в deployment CSP review;
CDN/unsafe-eval не добавлять автоматически. Локальный preview server не deployment approval.
Original bundle interactive behavior не исполнялся; safe static projection ограничена
initial state, а не доказательство работоспособности его fake steps.

#120/#121 и live social blockers сохраняются. Existing intermittent backend metrics-import
guard rejection из report23 этим styling не исправлялся и не проверялся повторно.
No push/remote CI/GitHub edit/issue closure/merge/deploy. #3 broader scope остаётся open.
Local slice commit: `feat(frontend): adapt prototype shell and onboarding themes`.

## Что мне важно понять / Что изучить

1. **Presentation state:** тема выбирает colors/layout, а не identity/permissions; CSS change
   не может разрешить backend operation.
2. **Semantic HTML:** native controls/landmarks дают keyboard/screen-reader навигацию;
   красивый clickable div и неправильный aria-label способны сломать accessibility.
3. **Design tokens:** semantic variables переиспользуют palette по всем states; одной смены
   background недостаточно для читабельного Dark error/input.
4. **Fixture isolation:** тестовая session полезна для race tests, но обязана отсутствовать
   в deployed artifacts и не является live JWT/login evidence.
5. **Provenance / self-hosted assets:** файл с известным checksum/license можно осознанно
   включить в build; наличие CDN ссылки в HTML не разрешает новый внешний runtime request.

**Факты:** перечисленные inspection, actual commands/results, unchanged private API/auth
boundary и reference fingerprint; screenshots подтверждают presentation в Chromium.
**Инженерный вывод:** перенос presentation primitives сохраняет выбранный UI и уменьшает
риск смешать demo state с реальной authority, без нового auth/provider слоя.
**Assumptions / requiring verification:** live Clerk/widget/production CSP, font rendering
на других OS и assistive technology; дальнейшая integration24–27 и release qualification.
