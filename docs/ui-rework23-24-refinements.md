# Уточнения frontend после ручной проверки

Дата:2026-10-20. Baseline `9f506176ff4e64675e30b0e2901f5f48c8ead157`, `mvp`.
Один агент; scope — четыре запрошенных изменения оформления23–24.

## Что было сделано

| Before | After | Why |
| --- | --- | --- |
| Manage workspaces и Create workspace в topbar menu | Только Manage workspaces; форма создания остаётся в Manager | Пользователь попросил убрать дублирующее действие |
| Explore your brand в hero | Кнопка удалена | Упрощение первого экрана |
| Две звезды на краях Let’s read your brand | Оба элемента и их CSS удалены | Упрощение рамки; внутренняя звезда upload-панели сохранена |
| Большой нижний wordmark, движущаяся лента и pause button | Один статичный footer: Cadence, вымышленные email/адрес и пометка demo | Запрошенное завершение страницы без декоративного движения |

Appearance и Cream/Dark/Sage сохранены. Контакты `hello@cadence.example` и
`24 Orbit Lane, Example City` вымышлены, отображаются обычным текстом без внешних ссылок
и явно помечены `Demo contact details — not monitored.`

## Как это работает и архитектурное решение

`Onboarding` показывает единственное управляющее действие в Workspace menu; обработчик
Manage workspaces по-прежнему открывает Manager. Его существующая форма создания не менялась.
`PublicLanding` больше не владеет pause state или декоративной лентой. Единственный semantic
footer принадлежит `CreatorShell`: publicPage показывает контакты, private shell — прежний текст.
Это изменение существующих компонентов; отдельный footer service/store и новые dependencies
не нужны. CSS удалён вместе с элементами, вместо их скрытия через display:none.

## Security Review

Threat: чужие данные или late response попадают в другой Workspace после navigation.
Protection: presentation-only diff сохраняет прежние scoped clients, state keys и abort guards.
Verification: полный existing E2E suite проверяет tenant/session switching и late responses.

Threat: вымышленные контакты приводят к непреднамеренной внешней передаче данных.
Protection: зарезервированный `.example`, plain text без mailto/URL, явная demo-пометка.
Verification: inspection JSX и browser public tests без внешних requests.

Threat: неактивные элементы остаются keyboard targets либо контраст ломается в другой теме.
Protection: элементы физически удалены; сохранены semantic footer, theme tokens и native controls.
Verification: keyboard/Escape/focus, axe в трёх темах, overflow checks320–1440px.

Authentication/authorization, DTO, API, backend, storage и dependencies не изменены.
Новые inputs, cookies, HTML injection, logging или дополнительные external requests не добавлены.

## Изменённые файлы

- `frontend/src/components/onboarding.tsx`: удалено Create workspace из topbar menu.
- `frontend/src/components/public-landing.tsx`: удалены hero CTA, крайние звёзды, footer animation/state.
- `frontend/src/components/creator-shell.tsx`: public footer с демонстрационными контактами.
- `frontend/src/app/public-landing.css`: стили статичного footer; удалён неиспользуемый marquee CSS.
- `frontend/tests/browser/cream-orbit.spec.ts`: четыре viewport проверяют новую структуру и footer.
- `frontend/tests/browser/visual-shell.spec.ts`: keyboard test проверяет отсутствие Create workspace
  в menu и доступность существующей формы в Manager.
- `docs/ui-rework-plan.md`: зафиксирован уточнённый scope, а не отложенный backlog.
- `docs/ui-rework23-24-report.md`: первоначальный отчёт связан с актуальной дельтой.
- `docs/ui-rework23-24-refinements.md`: текущий отчёт.

## Runtime Flow

Public route → CreatorShell → PublicLanding → один статичный footer.
Workspace topbar → Manage workspaces → существующий Manager → прежний scoped API client.
Изменение frontend не заменяет server-side authorization.

## Тесты

Выполнены в `var/ui-refinements-20261020/frontend/`, точной копии tracked frontend inputs
с текущими правками и OpenAPI, без `.env`/credentials; работающий root dev server не останавливался.

| Команда | Фактический результат |
| --- | --- |
| `pnpm lint` | PASS: ESLint,44 OpenAPI schemas in sync, Prettier |
| `pnpm typecheck` | PASS,0 diagnostics |
| `pnpm test` |3 files,58 tests passed,0 failures |
| `pnpm build` | PASS, статические `/`, `/sign-in`, `/sign-up`, `/workspaces`, not-found |
| `pnpm verify:export` | PASS:23 HTML/JS/CSS,3 WOFF2,3 PNG; fixture/CDN/original loader absent |
| `pnpm test:e2e` |66 passed,0 failed/skipped/flaky,38.9s; Chromium+axe |
| `.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check` (repository root) | BUILD SUCCESS,exit0 |
| `git diff --check`, локальный docs/link + source SHA256 audit | PASS:32 links,69 frontend files match qualified copy |

Первые setup attempts не прошли: отсутствовал OpenAPI в копии, Turbopack не принимал
внешнюю node_modules junction, а копирование packages без pnpm layout нарушало разрешение
transitive dependencies. OpenAPI скопирован, `pnpm install --offline --frozen-lockfile --ignore-scripts`
восстановил layout из local store без downloads. Lockfile и product dependencies не изменены.
Sandbox spawn restrictions для pnpm/typecheck/export устранены запуском тех же checks на host.
Эти неуспешные attempts не выдаются за pass; финальные команды выше завершились exit0.
Локальные logs: `var/ui-refinements-20261020/{lint,unit,build,typecheck,export,browser}.log`.

## Security Tests и Edge Cases

Existing suite сохраняет проверки controlled401/403/404/409, raw text/XSS, foreign scope,
session/tenant switching, late responses, stale review/consent и separate approval.
Новые assertions проверяют отсутствие удалённых controls, доступность Manager creation,
ровно один footer, видимую demo attribution, reduced-motion readability и отсутствие overflow.
Screenshot1440/390 визуально просмотрены; actual dev page/footer дополнительно проверены в browser.

## Непроверенные аспекты

Live Clerk authentication и реальные providers повторно не тестировались: credential/config
changes отсутствуют; реальные `.env` не читались и не копировались. Physical iOS/Android/Safari
не проверены; эмуляция ширины не доказывает поведение реального устройства.
Full Maven/PostgreSQL не запускались для presentation-only diff без backend changes.

## Что мне важно понять и изучить

Удаление JSX удаляет и keyboard target, и обработчик; скрытие через CSS сохраняло бы лишний
код. Semantic landmark — обозначенная область страницы (`footer`/contentinfo) — помогает
screen readers. Regression test теперь проверяет новый согласованный UI contract; прежние
security assertions остаются. Изолированная сборка (isolated build) позволяет проверять
production export без остановки dev server и без включения настоящих local keys.

## Production considerations и статус

До публичного запуска заменить demo contacts на подтверждённые данные. Эта UI дельта не
квалифицирует hosting, live auth/providers или задачи25–27. Push/merge/deployment не входят
в работу. Пользовательские README changes, `.agents/` и автоматически созданные Next dev
`frontend/AGENTS.md`/`next-env.d.ts` сохранены и не включены в этот commit.

**Факты:** четыре изменения внесены; перечисленные frontend gates реально прошли.
**Инженерный вывод:** ограниченная presentation-only дельта сохраняет прежние trust boundaries.
**Assumptions/remaining:** hardware/live/production проверки требуют отдельной среды;
демонстрационные контакты не являются действующими контактами продукта.
