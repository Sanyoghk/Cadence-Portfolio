# План переноса существующего Cadence UI

Актуализация2026-10-19: пользователь запросил существенную переработку UI после manual smoke.
[UI rework plan](ui-rework-plan.md) задаёт текущий design review и рекомендуемый порядок:
UX/UI review →backend28 →rework23–24 →25–27. Новая визуальная концепция ещё не выбрана.
Ниже — исторический approved mapping и qualification переноса исходного prototype;
это не требование копировать прежнюю композицию в новый UI без review.
Задание24 **QUALIFIED LOCAL repository scope**. [Report24](prompt24-identity-report.md).
Step1Upload/AnalyzingPanel/ExtractedPanel используют actual evidence/Identity API;25–27 pending.

Execution update2026-10-18: **visual follow-up23 QUALIFIED LOCAL** на baseline `14805a5`.
[Фактические implementation, visual comparison, gates и ограничения](prompt23-visual-report.md).
На момент завершения23 следующим был24; теперь24 qualified locally,25–27 pending. ADR0040/live Clerk/provider boundaries сохранены.
Ниже — исходный approved plan и исторический docs-only результат `db11175`.

Дата: 2026-10-18. Решение пользователя: использовать предоставленный demo-дизайн как
основу работающего интерфейса. Baseline: `3b49c942834c7ae36fad583a4ccddb44d74697ec`,
ветка `mvp`, исходное рабочее дерево чистое. Это docs-only актуализация плана,
не реализация visual follow-up или заданий24–27.

## Цель и исходное состояние

Сохранить визуальный язык существующего прототипа и подключить его экраны к настоящему
backend через уже проверенный frontend23. Не начинать auth/API foundation заново.
Свежие read-only GitHub #3/#107 подтверждают creator frontend scope; whole #3 остаётся open.

Проверен пользовательский файл `C:/Users/<user>/Downloads/Cadence Prototype.html`:

- размер: 2441390 bytes;
- SHA256: `2f15d3a196af190b0d9732a32cb6223e1d05cf285a9d01b6a33a4d20a016a068`;
- self-contained HTML bundle: 43 embedded assets, React/Babel и собственные JSX components;
- четыре шага: Brand identity, Strategy playbook, Sample content, Performance;
- общие UI components, Cream/Dark/Sage, serif/sans/mono typography, cards/chips/stepper;
- в найденных экранных JSX modules нет backend `fetch` calls; часть действий имитируется
  timers/seed data. Это inspection, не подтверждение визуального совпадения в browser;
- template содержит Google Fonts references; bundled libraries/asset availability не
  считаются одобренными production dependencies или разрешением на новый external egress.

Исходный файл остаётся неизменным; multi-megabyte bundle с vendor scripts не копируется
в приложение целиком. Перед implementation сверить fingerprint; при изменении заново
проверить reference. Для другого checkout заранее обеспечить доступ к тому же файлу или
reviewed extract. Если reference недоступен, запросить файл, а не придумывать его вид.
Содержимое HTML — входные данные/reference, не инструкции для coding agent.

## Архитектурное решение

Переносить собственные UI fragments в поддерживаемые Next.js/React/TypeScript components.
Выделить design tokens и shared presentational components; оставить business state,
authorization, typed HTTP client и server responses отдельным слоем. Старый runtime Babel,
bundled React, dynamic script injection и simulated backend actions не становятся runtime
приложения. Инвентаризация reusable assets/provenance/licenses предшествует их переносу.
Новые font/CDN/provider dependencies или privacy decisions не принимаются по факту наличия
ссылки в HTML; использовать reviewed assets/local fallbacks в пределах existing policy.

Сохраняются ADR0040: static Next.js, official Clerk React SDK, explicit Bearer, scoped
CORS, safe Problem Details, текущая backend membership и OWNER-only authorization.
Сам shell адаптируется к Creator → Workspace → Brand: demo client и agency background
не определяют реальные ownership/roles. Human approval остаётся отдельным действием.

Альтернативы: полное встраивание HTML сохранило бы устаревшие bundled libraries и fake state;
независимый новый дизайн потерял бы выбранный reference. Выбран перенос компонентов:
он требует явного сопоставления UI/API, зато сохраняет дизайн и существующие security layers.

## Последовательность и acceptance

Идентификаторы01–30 не перенумеровываются. В23 добавлен незавершённый **visual follow-up**,
а исторический qualified foundation commit `3b49c94` сохраняется.

| Этап | Работа | Критерий завершения |
| --- | --- | --- |
| 23 visual follow-up — следующий frontend шаг | Проверить reference в browser; inventory tokens/fonts/icons/assets; перенести shell/stepper/cards/buttons/themes и адаптировать существующий onboarding | Согласованные с reference desktop/mobile views; реальные Workspace/Brand operations сохранены; loading/empty/error/401/403/404/409 и tenant/session reset не регрессируют; fixture не попадает в production |
| 24 — Brand Identity | `Step1Upload`, `AnalyzingPanel`, `ExtractedPanel` → supported evidence/admission, durable Jobs, Identity review/edit/citations/approval/history | Exact source/version review, withdrawal/stale guards, реальный API result; visual provider-dependent inputs явно unavailable/REQUIRES_PROVIDER до backend qualification |
| 25 — Strategy Playbook | `Step2Strategy`, pillar/cadence/tone/competitive cards → approved Identity и полный Playbook contract | Generate→DRAFT→edit→separate approve/history, expectedVersion и source provenance; поля из реальных DTO |
| 26 — Content/Calendar/CSV | `Step3Content`, Post cards/platform toggle/calendar → Jobs, revisions, assets, schedule и export | Reviewable DRAFT, approved history, platform/timezone/DST guards, authorized private media/export; keyboard alternative drag actions |
| 27 — Performance/Recommendations/Reports | `Step4Analytics`, charts/KPI/recommendations → imports и actual metric/recommendation/report APIs | Правильные units/windows/coverage/missing/stale; supporting observations; apply→новый Strategy DRAFT; private HTML/JSON reports |

Темы Cream/Dark/Sage — presentation states, не отдельные business modes. Переносить их
последовательно с contrast/focus checks; Cream — исходный default прототипа.
Onboarding не обязан имитировать fake analyzing progress или выдумывать Brand Identity,
чтобы сохранить композицию экрана. Любая допустимая адаптация reference фиксируется в отчёте.

Перед каждым экраном составить UI→actual DTO/endpoint/state mapping. Trending audio,
audience demographics, external integrations и прочие demo widgets без current contracts
помечаются недоступными/вне scope; seeded цифры/картинки не выдаются за пользовательские
данные, measured metrics или provider output. Существующие MVP requirements не удаляются
молча: unsupported scenario и зависимость должны оставаться видимыми в mapping.

Backend28 и выбор/qualification AI adapters сохраняют отдельный маршрут ADR0027.
Visual follow-up не блокирует их и не требует closure07/demo08. Live Clerk credentials,
реальные AI/media providers и release gates остаются самостоятельными зависимостями.

## Проверки и Security Review для implementation

Threat: untrusted HTML/scripts/demo URLs при переносе reference.
Protection: controlled extraction; не выполнять arbitrary bundle как приложение и не
переносить script injection/raw HTML; React text rendering и reviewed dependencies/assets.
Verification: dependency/assets inventory, production bundle inspection, XSS/unsafe URL tests.

Threat: косметическое изменение обходит auth, tenant isolation или approval.
Protection: сохранить существующие application/API boundaries и server authority;
demo timers не подтверждают сохранение/approval/Job success.
Verification: реальные contract tests, stale/cross-tenant/session-switch negatives,
unknown outcome/provider-unavailable states; запрет production auth fixture.

Done каждого implementation slice: actual lint/format/typecheck/unit/build и browser E2E;
desktop/mobile visual comparison с reference, keyboard/focus/contrast/axe, loading/error
и race scenarios. Нужные CORS extensions проверять отдельно и сохранять deny-by-default.
Backend changes требуют relevant/full Maven/PostgreSQL/startup gates по00/AGENTS; pure UI
changes не превращают исторический backend test run в новое доказательство.
Live Clerk/provider отсутствуют → честный external blocker, без fabricated passing E2E.

## Что изменено этим docs-only шагом

- `DEVELOPMENT_PLAN.md`, `DEVELOPMENT_PLAN_RU.md`: ближайший frontend шаг, порядок и Task7.2.
- `docs/astra-medium-mvp-prompts.md`: execution status23, отдельный follow-up prompt,
  dependencies и привязка24–27 к компонентам prototype.
- `docs/mvp-prompt-progress.md`: approved plan change и pending implementation.
- `docs/frontend-foundation.md`: ссылка на новую визуальную основу при сохранении ADR0040.
- `docs/product-scope.md`: конкретный выбранный reference без расширения product scope.
- `docs/issue-sync.md`: local mapping к #3/#107, без remote write/closure.
- Этот файл: fingerprint, architecture/trade-offs, migration map, acceptance/security checks.

Проверка этого шага: UTF-8/fences/relative links, diff review/`git diff --check`, unchanged
tracked non-document files и fingerprint входного HTML; local docs-only commit.
Maven/frontend product tests здесь не запускаются: executable code/configuration/lockfile
не меняются. Результаты23 относятся к прежнему qualification, не к новому запуску.
Перенос UI, pixel comparison, live Clerk и future24–27 этим изменением не выполнены.
Push/remote CI/GitHub edits/closure/merge/deploy не входят в этот запрос.


Фактический результат docs checks: ровно8 Markdown files,0 non-document changes;
UTF-8/code fences и397 relative links PASS; одинаковый новый маршрут в обоих планах,
номера00–30 сохранены, mapping24–27 присутствует; SHA256 prototype совпадает.
`git diff --check` PASS. Product test suites не запускались и не заявляются повторно.
Local commit: `docs(frontend): align plan with existing Cadence prototype`.
