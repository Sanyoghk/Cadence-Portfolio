# Cream Orbit — отчёт о прототипе,2026-10-19

## Что было сделано

Выбранное пользователем направление **Cream Orbit** превращено в отдельный интерактивный
React/Vite prototype и desktop/mobile screenshots. Публичная страница содержит intro≥3s,
CADENCE, Brand Identity→pillars, Standard/Plus concept, About и footer. Кабинет содержит
Manager, Identity, Strategy, Sample content/Calendar/Post list и Performance.

[Открыть preview](http://127.0.0.1:4177/) · [макеты и visual QA](design-qa.md) ·
[компоненты/tokens](design-system.md) · [navigation](navigation-and-system.md) ·
[UI→API mapping](ui-api-mapping.md) · [backend backlog](backend-backlog.md).

Actual branch `mvp`. Перед продолжением HEAD уже `ba39c6a8081b7ecfd180e36ca28591aabbc8bb65`:
внешний commit `chore(frontend): add pinned GSAP dependency` сохранён. Предыдущий design slice
`594d7ec` также сохранён. Независимые untracked `.agents/skills/` не включаются в commit.
Рабочие frontend/backend, auth, database и migrations этим slice не изменены.

## Как это работает

`App` выбирает одну секцию по hash route. Workspace и Brand выбраны отдельно; `selectBrand`
ищет Brand только внутри выбранного Workspace. Смена scope перемонтирует рабочую область,
очищает её forms/selection/pending timers. State находится только в React memory этой вкладки.
Reload возвращает synthetic fixtures. Никакой email/password/token, файла или source URL
не передаётся на сервер. Account — прозрачный Clerk entry preview без формы credentials.

`demo.js` хранит чистые функции переходов состояния: exact version consent, withdrawal,
Identity DRAFT/APPROVED, Strategy DRAFT/APPROVED, Content revisions/scheduling и approved-only CSV.
`currentIdentity` проверяет одновременно consent, approved status и актуальность source version.
`currentStrategy` проверяет актуальную approved Identity и её version в Strategy.
Рекомендация создаёт новый Strategy DRAFT; approved snapshots не переписываются.

В public форме inputs не анализируются и не сохраняются: кнопка использует только named
Sunday Clay fixture. Browse demo files выбирает synthetic PDF, а не настоящий файл.
Блок появляется при scroll; после preparation форма увеличивается/исчезает и раскрывает pillars.
Подписки — concept с TBD, без оплаты и entitlement. В кабинете существующие posts явно demo;
новые synthetic samples требуют approved Strategy. Реальный backend не эмулируется fetch-заглушками.

## Архитектурное решение

Изолированный Sites template внутри `docs/design/ui-rework/prototype` выбран, чтобы UX можно
было проверить без регрессии квалифицированных frontend23–24/backend09–22. Альтернативы:
изменить рабочий Next.js UI сейчас или оставить только картинки. Первая нарушает scope
design review, вторая не позволяет проверить consent/approval/navigation/mobile state.

Использованы React19.2.9, react-dom19.2.9, Vite8.3.4 — закреплённые версии, уже используемые
рабочим frontend. Native dialog/details/select/meter решают взаимодействия без новой UI library.
pnpm lockfile один; template npm lock удалён до handoff, scripts сохраняют build/test contracts.
Шрифты локальные, OFL notices сохранены. Artwork собственный ImageGen, не scraped assets референса.

Trade-off: prototype memory model показывает жизненный цикл, но не реализует серверную
authorization, transactions, durable Jobs, optimistic locking, private file quarantine,
exact byte receipts и recovery. UI→API mapping фиксирует, что требуется для следующего переноса.
Подключение demo functions к реальным данным вместо существующих contracts недопустимо.

Sites project `appgprj_6ad5f400cb908191bf93dccbcca4d1ce` создан private/unpublished.
Project ID сохранён официальным helper; Worker и hosting template сохранены. Build готовит
`dist/client/index.html`, `dist/server/index.js`, `dist/.openai/hosting.json`. Deploy не выполнен,
live URL отсутствует. Доступен локальный preview текущей сессии, не публичный hosting.

## Security Review

Защищаемые данные здесь — только synthetic state и текст, введённый локально. Caller — любой
посетитель demo; граница доверия (trust boundary) находится у формы/route. Реальных accounts,
платежей, backend tokens, uploads и внешнего I/O нет. Dev server привязан к loopback.

```text
Threat: Смешение Workspace/Brand context и показ предыдущих данных.
Protection: Scoped selectBrand, key Workspace/Brand, раздельные fixtures, cleanup таймеров.
Verification: Domain foreign/missing Brand negatives; browser switch очищает forms/metrics/actions.
Limit: Это не server-side tenant isolation test; production OWNER/membership checks не изменялись.

Threat: Stale source или withdrawal разрешает генерацию/approval старого артефакта.
Protection: currentConsent/currentIdentity/currentStrategy, version guards и отдельные approvals.
Verification: Domain stale/no/withdrawn consent tests и browser replacement/withdrawal scenarios.
Limit: Demo version не подменяет exact bytes/checksum/review receipt существующего backend.

Threat: XSS/SSRF/утечка через source URL, файл, auth form или внешний font provider.
Protection: React text rendering, нет raw HTML, fetch/source navigation/upload/auth credentials;
            assets/fonts локальные; .env не читались.
Verification: Source inspection; browser HTTP origins только loopback, console errors0,
              cookies/localStorage/sessionStorage0. Реальные malicious uploads не отправлялись.

Threat: Spreadsheet formula injection и экспорт неутверждённых revisions.
Protection: csvFor фильтрует APPROVED, quotes/escaping и formula-prefix neutralization.
Verification: Domain test с =HYPERLINK и исключением DRAFT;2 approved fixture rows.
Limit: Реальный экспорт использует existing durable snapshot/private download contract.

Threat: Произвольный API/write path получает misleading200 app shell.
Protection: Неизменённый Sites Worker не делает SPA fallback для API/write requests.
Verification:4 Worker tests, включая missing API/write rejection и packaging contract.

Threat: Небounded demo generation расходует память, callback переживает смену scope.
Protection: До5 Workspaces, до12 synthetic content samples, maxLength и timer cleanup.
Verification: Domain content bound; inspection cleanup; browser scoped interactions.
Limit: Не production rate limiting; серверные quotas/cost/deadline gates остаются обязательными.

Threat: Supply-chain vulnerability через новые UI/animation dependencies.
Protection: Native primitives, exact versions, pnpm lock, ignore-scripts install.
Verification: pnpm audit --prod:0 known advisories на момент запуска.
Limit: Audit не доказывает отсутствие неизвестных уязвимостей.
```

SQL/JPA/CSRF/auth/session implementation здесь не изменяются и не исполняются; нет нового
server endpoint или client privilege. Frontend checks демонстрируют UX и не являются защитой API.

## Изменённые файлы

Все новые исполняемые файлы находятся под `docs/design/ui-rework/prototype/`:

| Файл | Назначение |
| --- | --- |
| `src/App.jsx` | Shared shell, routes, Workspace/Brand context, Account/plan/history dialogs |
| `src/Landing.jsx` | Public sections, demo Identity teaser, curtain, pricing scroll line/footer |
| `src/Workspace.jsx` | Пять рабочих секций, forms, approvals, Calendar, Performance/report preview |
| `src/demo.js` | Synthetic fixtures и чистые lifecycle/CSV/scheduling функции |
| `src/ui.jsx` | Button/Field/Star/Pillars, native Modal и bounded demo preparation timer |
| `src/main.jsx` | React entry из template |
| `src/styles.css` | Tokens, responsive composition, controls, themes, motion/reduced motion |
| `index.html` | Title/meta, safe-area viewport, theme-color, entry |
| `package.json` | Exact React/Vite, scripts и packageManager |
| `pnpm-lock.yaml` | Воспроизводимые dependencies; npm lock не оставлен |
| `vite.config.mjs` | Loopback preview и dist/client build |
| `.openai/hosting.json` | Официальный template+project ID; d1/r2 null |
| `worker/index.js` | Неизменённый Sites Worker template |
| `scripts/prepare-sites-build.mjs` | Неизменённый Sites packaging helper |
| `tests/sites-worker.test.mjs` |4 неизменённых template tests |
| `tests/demo-domain.test.mjs` |11 meaningful state/security-negative tests |
| `scripts/browser-qa.mjs` |26 local browser scenario/layout assertions и screenshots |
| `scripts/inspect-preview.mjs` | Settled captures после обязательного intro без bypass |
| `README.md`, `AGENTS.md` | Usage/scope и сохранённые template instructions |
| `.gitignore`, `.npmrc` | Исключение dependencies/build/cache и template package settings |
| `public/assets/orbit.png` | Собственный прозрачный orbital emblem |
| `public/assets/paper.png` | Собственная негромкая paper texture |
| `public/assets/scroll-line.png` | Собственная линия со звёздами для pricing |
| `public/fonts/cormorant-garamond-latin-normal.woff2` | Existing self-hosted Display |
| `public/fonts/cormorant-garamond-latin-italic.woff2` | Existing italic Display |
| `public/fonts/dm-sans-latin.woff2` | Existing Text |
| `public/fonts/Cormorant-Garamond-OFL.txt`, `DM-Sans-OFL.txt`, `README.md` | Лицензии/происхождение шрифтов |

Документация и evidence в `docs/design/ui-rework/`:

| Файл / набор | Назначение |
| --- | --- |
| `README.md` | Выбор Cream Orbit, preview и текущий статус |
| `design-system.md` | Реальные tokens/components/motion/mobile contracts |
| `design-qa.md` | Макеты, reference comparison, Before/After/Why, skills и critique |
| `prototype-report.md` | Этот отчёт, security/verification/limits |
| `navigation-and-system.md` | Отличие prototype routes от будущего authenticated flow |
| `ui-api-mapping.md` | Existing contracts отдельно от synthetic demo controls |
| `backend-backlog.md` | Обновление gaps и frontend work по ходу прототипа |
| `verify_review.py` | Bounded artifact/link/hash/API checks; отдельный baseline slice |
| `artifact-manifest.json` | SHA256/размеры актуальных изображений |
| `selected/cream-refined.png` | Уточнённый выбранный visual target |
| `craft/page/direction.json`, `craft/app/direction.json` | Reproducible draws seed191026 |
| `craft/screens/*.jpg`, `browser-qa.json` | Desktop/tablet/mobile snapshots и26 assertions |
| `craft/inspect-landing/*`, `craft/inspect-app/*` | Реальные inspector metrics/contact sheets |
| `craft/*-inspector-output.json`, `source-lint.json`, `dependency-audit.json` | Raw QA/audit evidence |
| `craft/.gitignore` | Не включать ephemeral browser profiles/downloads |
| `docs/ui-rework-plan.md` | Результат выбранного design slice и backend tracking section |

Старые12 references,3 directions, prompts и API inventory сохранены. Images manifest намеренно
не обходит node_modules/dist/browser profiles. Built dist и dependencies не коммитятся.

## Runtime Flow

```text
Hash route / native control
    ↓
App: Workspace + Brand scope
    ↓
Landing или одна из пяти Workspace sections
    ↓
demo.js: input bounds + current source/consent/lifecycle guards
    ↓
immutable React memory update
    ↓
render + separate status/history
    ↓
опционально local Blob CSV (только approved synthetic revisions)
```

Сервер Cadence/Controller/Service/Repository/Database в этом runtime flow отсутствует.
Будущий flow с Clerk Bearer и server-side authorization описан в ADR0040 и UI→API mapping.

## Тесты

Фактически выполнены команды ниже. `pnpm` доступен в
`C:/Users/<user>/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/fallback/pnpm.cmd`;
в командах запуска использовался этот полный путь. Рабочая директория первых четырёх — prototype.

```text
pnpm test
pnpm run test:sites
pnpm run build
pnpm audit --prod --json
node docs/design/ui-rework/prototype/scripts/browser-qa.mjs
node docs/design/ui-rework/prototype/scripts/inspect-preview.mjs http://127.0.0.1:4177/ --out docs/design/ui-rework/craft/inspect-landing --widths 1440,768,390 --allow hairline,palette-cream,palette-terracotta,few-pictures,no-footage,name-only-title,late-title --json
node docs/design/ui-rework/prototype/scripts/inspect-preview.mjs http://127.0.0.1:4177/#workspace/content --out docs/design/ui-rework/craft/inspect-app --app --widths 1440,768,390 --allow palette-cream --json
node .agents/skills/design-taste-frontend/scripts/lint.mjs docs/design/ui-rework/prototype/src --json
node frontend/node_modules/prettier/bin/prettier.cjs --check docs/design/ui-rework/prototype/src docs/design/ui-rework/prototype/scripts/browser-qa.mjs docs/design/ui-rework/prototype/scripts/inspect-preview.mjs docs/design/ui-rework/prototype/tests/demo-domain.test.mjs docs/design/ui-rework/prototype/package.json
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
python docs/design/ui-rework/verify_review.py
git diff --check
```

Результаты:11 domain tests PASS,4 Worker tests PASS,0 failed/skipped;26 browser checks PASS;
Vite build PASS,20 modules; Sites outputs присутствуют. Dependency audit:0 info/low/moderate/
high/critical известных vulnerabilities. Public inspector0 FAIL/2 WARN, app0 FAIL/4 WARN;
lint0 FAIL/7 WARN с указанными в visual QA причинами. Prettier PASS. Spotless BUILD SUCCESS,
633 Java +1 POM clean по cache, не новый Java test run. Artifact verifier PASS:13 Markdown files,404 local links,52 images,23 controllers/85 route
annotations;0 working product changes. Diff whitespace check PASS.

Первый browser run выявил испорченную кодировку punctuation в двух demo files; исправлено
UTF-8 чтение/запись, последующие runs PASS. Отдельный network assertion сначала посчитал
inline icons native date/time внешним запросом; исправлен predicate только на HTTP(S)/WS(S),
внешние HTTP-запросы по-прежнему запрещены. Результаты не скрыты изменением ожиданий продукта.
Inspector сначала захватывал3s intro; final wrapper ждёт settled UI и сохраняет настоящий intro.

Maven/frontend product tests не запускались: рабочий код не изменён. Docker/Testcontainers,
Clerk live и server authorization не заявлены как проверенные этим design slice.

## Security Tests

Проверены: foreign/missing Brand lookup; no/stale/withdrawn consent; approved Identity старой
source version; Strategy approval с чужой source version; content до approval и сверх лимита;
повторное approval approved post; immutable approved history; draft/out-of-range scheduling;
formula-bearing CSV и исключение draft; отсутствие переноса метрик между Brands; Worker API/write
fallback rejection. Browser проверяет withdrawal/source replacement, scope reset и external I/O.

## Edge Cases

- Empty Brand/Performance: неизвестные данные показаны как no observations, rate `—`.
- Несколько posts в одном дне на375/390px: agenda сохраняет ширину каждого post≥150px.
- Смена source kind/version после approval: fresh consent не актуализирует старую Identity.
- Editing approved artifact: новый DRAFT, historical APPROVED snapshot сохранён.
- Intro на быстрой загрузке: controls inert, экран остаётся минимум3s.
- Reduced motion: статичные logo/footer/line, содержание доступно; intro duration сохранён.
- Escape native dialog/menu: overlay закрывается, focus возвращается предусмотренным механизмом.
- Date/time demo ограничены19–25Oct2026 и10:00–18:59 London. Это не полная DST simulation.

## Непроверенные аспекты

Нет физического iPhone/Android: touch keyboard, browser chrome/safe area и real-device GPU
проверяются позднее на устройстве. Headless mobile viewport не доказывает native feel на hardware.
Не запускались axe/screen-reader audit, полный Dark/Sage contrast suite и E2E real auth/API.
Не проверены реальные files, SSRF,401/403/409, network uncertainty/retries и expiry downloads:
они отсутствуют в prototype и остаются обязательными при frontend переносе.

## Что мне важно понять

Кнопка disabled объясняет UX, но не защищает API. Поэтому чистая demo state machine проверяет
инварианты повторно, а коммерческий backend обязан проверять их на сервере независимо от UI.
Consent разрешает конкретный просмотренный source; approval утверждает конкретный artifact.
Это разные решения. Изменение источника нельзя «починить» одним повторным consent для старой
Identity. Memory prototype подтверждает композицию/сценарий, а не production data integrity.

## Что мне стоит изучить

1. **Конечный автомат (state machine)** — допустимые переходы DRAFT→APPROVED и guards в demo.js.
2. **Неизменяемый снимок (immutable snapshot)** — history сохраняет утверждённую версию при edit.
3. **Граница доверия (trust boundary)** — input/route не дают прав на server resource.
4. **Прогрессивная адаптация (responsive adaptation)** — Calendar меняет представление на agenda,
   сохраняя тот же смысл и действия.
5. **Доступность движения (reduced motion)** — animation поддерживает изменение состояния,
   но содержание и действия не должны зависеть от движения.

## Production considerations

Следующий шаг — согласованный перенос через existing typed transport, Clerk SDK/Bearer и
актуальный server OWNER/membership. Использовать exact review receipts/checksums, durable Jobs,
idempotency/version guards, cancellation/scope guards, bounded pagination и safe Problem Details.
Private media/export/reports требуют authenticated bytes/download. BG01/BG02 и provider decisions
не активируются дизайном. Real subscriptions/anonymous analysis/AI не объявлены готовыми.
Sites deploy/merge/push не выполнялись; local commit сохраняет только проверенный design slice.

**Подтверждённые факты:** build,15 Node tests,26 browser checks, screenshots/inspection,
advisory audit и отсутствие app diff этого slice.

**Инженерные выводы:** выбранная композиция переносится в более плотную рабочую оболочку;
native controls и изолированный state model достаточны для текущего design review.

**Assumptions/дальнейшая проверка:** real-device feel, полная accessibility/themes, live Clerk/API,
provider qualification, backend28 и коммерческий deployment.
