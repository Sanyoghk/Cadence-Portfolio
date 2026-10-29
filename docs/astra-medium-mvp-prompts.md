# Cadence — последовательные промпты для Astra / Medium

> **АРХИВ / SUPERSEDED расширенным ТЗ2026-10-20.**
> Выполнять [новый план V2](astra-medium-mvp-prompts-v2.md), а не прежние next-task инструкции.
> Все00–30 перенесены в [матрицу](scope-v2-inventory.md); выполненные scopes не повторяются.
> Direct publishing теперь required; ready video/raw editing/market intelligence/reminders
> добавлены. Номера ниже и результаты tests сохранены как историческое evidence.

Создан: 2026-10-14. ADR0027 route: 2026-10-15; UI rework planning: 2026-10-19.
Назначение: сначала MVP backend без AI adapters, затем подключение AI и полный creator demo.
Модель в интерфейсе: **GPT-6 Astra**, reasoning effort: **Medium**. Выбор делает пользователь;
текст промпта сам по себе не переключает модель. Название «Astro» в запросе трактуется как Astra.

## Как пользоваться

**Актуальный приоритет2026-10-20:** по подтверждённому решению пользователя сначала закончить
backend MVP с реальным AI, затем полностью переделывать frontend.
[План завершения](backend-mvp-completion.md) заменяет прежний next25/design-first порядок.
09–22/28 backend не повторяются; next — provider/data/cost decisions и оставшаяся integration,
включая visual Identity, реальные budgets, Content/recommendations/media и API acceptance.
25–27 отложены;01–30 остаются прежними идентификаторами. Записи ниже исторические, если
они противоречат этому приоритету. Planning не является qualification новой integration.
Перед implementation пользователь запросил AI research под £20 и обсуждение; provider/model
и integration plan пока не утверждены. Не запускать adapters по одному наличию proposal.

Execution2026-10-20: **Cream Orbit UI rework23–24 QUALIFIED LOCAL**, после backend28.
[Report](ui-rework23-24-report.md):58 unit/66 browser+axe, lint/typecheck/build/export PASS.
Следующий frontend scope25→26→27; existing backend scopes не повторять. Tasks25–27,
frontend/cross-stack28, live/provider/device/release gates остаются отдельно. Номера стабильны.

1. Продолжай в текущем проекте/workspace. В новом чате отправь общий промпт **00**.
2. Отправляй задания по одному по актуальному маршруту ниже, после проверки prerequisites;
   номера **01–30** — стабильные идентификаторы, а не обязательный порядок исполнения.
3. Можно отправлять короткую команду из раздела «Запуск задания», вместо копирования блока.
4. При незавершённом задании используй «Продолжение задания». Provider-dependent остаток05/07
   явно отложен ADR0027 и не блокирует независимые backend задачи; остальные prerequisites сохраняются.
5. Один номер — результат с критериями готовности. Он может занять несколько рабочих turns.
   **30 промптов не означают 30 гарантированных итераций** и не заменяют текущую оценку30–48.
6. После нового human product decision/current plan change переоцени зависимые задания.
   Уже выполненную функцию не реализуй заново; сначала проверь фактическую готовность.

Это набор задач, не утверждение их готовности и не команда выполнить все задачи сейчас.
Создание этого документа не разрешает новые external writes, push, merge или deployment.
При последующем запуске задания применяются явные инструкции пользователя и текущие permissions.

Основные источники: [AGENTS.md](../AGENTS.md), [product scope](product-scope.md),
[план](../DEVELOPMENT_PLAN_RU.md), [issue sync](issue-sync.md), применимые ADR и GitHub #107.
Исторический baseline при написании: `bbf4516`, ветка `mvp`; это не требование откатить HEAD.
На историческом baseline PDF analysis был реализован в `d43af8c`, V37; последующая
реализация01–06 отражена в [progress ledger](mvp-prompt-progress.md), не повторять её.
На момент первоначального написания implementation run:1053 tests/0 failures/errors/3 Windows skips,
покрытых Linux35/35. Это история, а не результат будущих заданий.

## Milestones и зависимости

Текущий planning update2026-10-19: после manual smoke пользователь запросил существенную
переработку web UI. [UI rework plan](ui-rework-plan.md) задаёт рекомендуемый маршрут
**UX/UI design review сейчас →28 backend →UI rework23–24 →25–27**. Конкретный дизайн
ещё не выбран; существующие backend/auth/use cases не реализуются заново.
23–24 qualified locally;09–22 delivered в своих scopes. Исторический baseline ниже
не является указанием повторить эти задания. Local Clerk development setup выполнен;
user-reported smoke не заменяет полную live auth E2E/production qualification.

Актуальное решение: [ADR0027](adr/0027-backend-first-ai-adapters-last.md).
Baseline этой актуализации: `3389ba7`, чистая `mvp`. 01–04 и06 qualified locally;
05 social textual slice qualified, visual scope REQUIRES_PROVIDER;07 review выполнен,
closure blocked. Это historical qualification, не новый запуск tests и не remote CI.

| Задания / этап | Приоритет и результат |
| --- | --- |
| 01–06 | Сохранить qualified textual evidence/Identity/Strategy; visual остаток05 отложен |
| 09–13 | Delivered local contracts/Jobs/recovery/budgets/Playbook; не повторять |
| 14–18 | QUALIFIED LOCAL Posts/content/calendar/media/CSV; не повторять |
| 19–22 | QUALIFIED LOCAL metrics/analytics/recommendations/reports; не повторять |
| UX/UI design review | Сейчас: новый пакет navigation/desktop/mobile/state mapping до новых экранов; конкретный дизайн pending |
| 28, backend scope | До выбора AI: cross-module security, audit, observability, reliability и full backend gates |
| AI integration stage | После backend: выбрать API/model/privacy, реализовать и квалифицировать adapters |
| Остаток05 →07 | Завершить visual lifecycle/consumer и whole #121 acceptance; затем closure по actual gates |
| 08 | Необязательная промежуточная API demo; не блокирует09 и не означает полный backend |
| UI rework23–24 →25–27 | Foundation23/visual follow-up23/24 qualified; после design review и backend28 перенести существующие workflows на новый UI, затем новые экраны25–27 по готовым API |
| 28, frontend/cross-stack scope →29–30 | Завершить security review, creator E2E и documented release |

Следующий backend milestone: **28 (backend) →AI integration**, независимо от полного UI.
Frontend route: **design review сейчас →после backend28 UI rework23–24 →25–27 →frontend28→29–30**.
Provider-independent admission/lifecycle/contracts не нужно откладывать только из-за
отсутствия provider. Visual-specific детали и реальные observations не выдумывать.
Frontend можно развивать по готовым contracts; полный frontend/security/E2E scope остаётся
обязательным для UI release, но не задерживает backend milestone и выбор AI API.

### AI integration stage — после backend qualification

Это отдельный этап, а не разрешение выбрать provider прямо сейчас. Перед началом составить
acceptance matrix backend09–22/28 по actual code/tests и проверить remaining gaps.
Затем выбрать API/model и processor/region/retention/deletion/cost policy. Реализовать
Brand/Strategy adapters #120 и явно выделенные Content/Recommendation/media adapter scopes;
#120 не считается автоматически покрывающим все генерации продукта.

Для visual Identity утверждён прямой путь: проверенные admitted image bytes → AI adapter
→ validated structured Identity → current source/access/withdrawal rechecks → Identity DRAFT.
Отдельный OCR/visual observations pass не обязателен. Human approval остаётся отдельным.
Обязательны bounded I/O, secrets isolation, retry/deadline/budget controls, provenance,
malformed output/timeout/revocation tests и controlled live qualification до activation.
При необходимости расширить current text-only ports versioned contracts, сохраняя историю.

Завершить remaining05 и07; #121 остаётся OPEN до actual acceptance, full gates и exact-SHA CI.
Production fail-closed и local/test-only fakes сохраняются до qualification; backend без
adapters не является работающим production AI MVP. Live Clerk, IAM/TLS, backups/restore,
load/pilot и deployment остаются отдельными gates. Direct publishing/billing не включены.

Политика push не изменена. Новые independent commits в той же `mvp` включают ancestry
незавершённого #121: нельзя считать завершение другого issue разрешением отправить всю
ветку. Перед push проверить весь outgoing range; пока #121 unfinished, не push этот range
без отдельного явного изменения пользователем whole-issue policy. Local работа продолжается.

## 00 — общий стартовый промпт: персональный алгоритм

```text
Продолжай Cadence в текущем project/workspace. Работай как Senior Software Engineer,
Software Architect, Application Security Engineer и Technical Mentor. Мой уровень —
второй курс Software Engineering. Все планы, updates и отчёты пиши на русском;
classes/methods/files/commands и English technical terms сохраняй. Сложный важный термин
при первом упоминании объясни коротко. Модель для этого чата выбираю Astra / Medium;
это не выбор AI provider Cadence и не команда менять настройки приложения.

Следуй AGENTS.md и моим персональным инструкциям. Этот промпт их сохраняет и не ослабляет.
Порядок приоритетов: correctness, security, architecture, data integrity, maintainability,
testability, reliability, performance, simplicity. Работай одним агентом; subagents и
другие implementation чаты — только по моей отдельной явной просьбе.

1. Начни с actual branch/HEAD/git status. Сохрани пользовательские изменения и не сбрасывай
   рабочую ветку mvp. В новом контексте восстанови product scope, актуальные планы,
   issue #107/текущей задачи и нужные ADR. Исторический recovery не определяет следующий issue.
   Изучи связанные code/configuration/security/pom/tests/usages перед существенным изменением.
   Повторно читай уже изученное только при изменении состояния или необходимости задачи.

2. Перед нетривиальным implementation дай короткий план: Goal, Current architecture,
   Proposed architecture, Security considerations, Components affected, Testing strategy,
   Security testing strategy. Для auth/files/URL/personal data/public API/integrations сделай
   threat model: что защищаем, callers, trust boundaries, attacks, protections, что при
   обходе одной защиты и defense in depth. Объясняй проблему и механизм, не «best practice».

3. Реализуй законченный вертикальный сценарий через нужные layers, API/contracts/migrations
   и tests. Обычные engineering choices решай самостоятельно. Не заканчивай одним планом,
   одним произвольным файлом или предложением продолжить. Большой scope дели на связные
   проверенные slices, сохраняй оставшиеся acceptance criteria и продолжай до границы задачи.
   Существенную product/provider/privacy ambiguity уточняй; выполняй независимую работу.

4. Сохраняй modular monolith Java21/Spring Boot, presentation→application→domain→infrastructure,
   PostgreSQL/Flyway, Clerk authentication и server-side authorization. Tenant boundary:
   CadenceUser→Workspace→Brand. OWNER-only, deny-by-default, actual account/membership/resource
   checks. Frontend не security boundary. REST /api/v1, отдельные DTO, bounded pagination,
   optimistic locking, UTC и safe Problem Details. APPROVED/LOCKED immutable; edit→new DRAFT.

5. Input, DB user content, storage bytes, URLs/configuration и model output недоверенные.
   Проверяй BOLA/IDOR, mass assignment, injection, SSRF, traversal/file formats, CSRF/CORS
   по фактической auth architecture, concurrency, quotas/deadlines/resource exhaustion,
   data exposure/logging. Не храни secrets в Git и не ослабляй TLS/auth/CSRF для demo.
   Нужные invariants защищай validation/domain и PostgreSQL constraints/runtime privileges.
   Новая dependency требует необходимости, maintenance/security/supply-chain review.
   External I/O вне долгих DB transactions; перед egress/publication повторяй соответствующие
   permissions/source/lifecycle/version/deadline/lease checks. Сохраняй reviewed lock order.
   Existing Flyway checksums immutable; изменения схемы — новой migration.
   Не выдумывай legacy approvals, citations, cost/latency/moderation или generation provenance.

6. Fake generation разрешена только в изолированных local/test, с явной demo attribution.
   Production fail-closed сохраняется до отдельного решения #120 и qualification providers.
   Fixture не доказывает качество AI. Реализованная ingest queue не означает готовность AI Jobs.
   Direct publishing/billing не входят в текущий MVP. Product gaps не скрывай под demo labels.
   По ADR0027 сначала provider-independent backend, затем AI API/adapters; #121 open
   не блокирует независимые задачи. Visual bytes могут идти прямо в AI для Identity;
   обязательного отдельного OCR/interpretation pass нет. Live egress пока не разрешён.

7. Проверяй compilation, formatting, unit/domain, integration и security negative scenarios.
   Persistence/migration/concurrency — PostgreSQL/Testcontainers. При config/JPA/DI проверь
   startup. Для законченного backend implementation slice выполни обязательный full gate:
   .\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply clean verify
   (добавляй -o только если необходимые artifacts действительно доступны локально).
   Выполни Python report gates/tests и применимые Linux filesystem/renderer suites согласно
   текущему AGENTS.md и verification docs. Docker обязателен для full integration suite.
   Frontend: фактические lint/typecheck/unit/build/E2E commands из package scripts.
   Required skipped integration tests не pass. Исправляй причину failure, не ослабляй test.
   После source changes, затрагивающих уже проверенное, повтори необходимые gates.
   Без новых changes/failures/concerns не повторяй успешные runs ради дополнительных «зелёных».
   Docs-only: content/links/diff checks, без выдачи старых tests за новый запуск.

8. Сделай final architecture/security review: auth, ownership, unexpected input, injection,
   sensitive exposure/secrets, abuse/exhaustion, logging, dependencies и DB integrity.
   Устрани найденные реалистичные проблемы до завершения. Не называй local feature production-ready.

9. Обнови docs/contracts/оба плана и сохрани каждый законченный qualified slice local commit.
   Push по текущему правилу — после полного issue, затем exact-source-SHA CI и acceptance review.
   Закрывай issue только при реально выполненных criteria; синхронизируй #107.
   Не публикуй неподтверждённые достижения. Merge/deployment согласуются отдельно.
   Existing GitHub approval покрывает только указанный payload/destinations. Если automatic
   approval review требует отдельного разрешения, сначала подготовь точный reviewable draft,
   выполни независимую local работу и объясни конкретную причину запроса; не обходи отказ.
   Самостоятельно не создавай дополнительные approval flows без применимого требования.

10. Сохраняй короткие содержательные updates во время работы. В финале — результат,
    фактические checks, commits/push/CI, remaining scope и ссылка на подробный docs report.
    Report содержит: Что было сделано; Как это работает; Архитектурное решение и trade-offs;
    Security Review с Threat/Protection/Verification; каждый изменённый файл; Runtime Flow;
    точные commands и counts/errors/skips; Security Tests; Edge Cases; Непроверенные аспекты;
    Что мне важно понять;1–5 концепций для изучения; Production considerations.
    Явно разделяй доказанные facts, engineering conclusions и assumptions. Не раскрывай
    скрытые внутренние рассуждения; давай проверяемые решения и их технические основания.

Веди краткий progress ledger в docs/mvp-prompt-progress.md: номер, dependencies, actual
completed scope, commit/report/checks и remaining/blockers. Не объявляй номер done по факту
создания файла или частичного slice. Старые проверенные задачи не реализуй повторно.
Сейчас выполняй только задание, указанное моим следующим сообщением.
```

## Запуск задания из файла

```text
Выполни задание 09 из docs/astra-medium-mvp-prompts.md, применяя общий промпт00,
AGENTS.md и мои персональные инструкции. Сверь фактические prerequisites и current HEAD;
дойди до qualified результата и local commit. Другие номера пока не начинай.
```

Для следующих заданий замени09 на нужный номер по актуальному маршруту. В этом же чате не нужно снова копировать00.
В новом чате можно использовать ту же короткую команду: она явно требует прочитать00.

## Продолжение незавершённого задания

```text
Продолжай текущее задание из docs/astra-medium-mvp-prompts.md по progress ledger.
Сначала сверяй actual HEAD/status и сохраняй уже выполненную работу. Устрани доступные
blockers, закончи remaining acceptance criteria, verification, security review, docs и commit.
Не подменяй завершение доступного scope переходом дальше. Исключение ADR0027: отложенные
provider-dependent05/07 не блокируют явно выбранную независимую backend задачу.
Не спрашивай повторно то,
что я уже явно разрешил для конкретного scope/payload/destinations.
```

## 01 — PDF Identity copy, provenance и approval

Prerequisite: current qualified PDF analysis, ADR0019/V37. Issue: #121.

```text
Применяй общий алгоритм00. Заверши PDF evidence analysis→Identity copy/read и direct/inherited
approval. Изучи текущие TEXT Identity/V35 guards и PDF receipt workflow; сначала ADR.
Учти, что successful analysis увеличивает Brand version: receipt currentness при copy
нужно определить по publication lineage, а не ослабить version check.

Нужны exact immutable admission/artifact/original/latest scan links и quotes, actual active
OWNER/member, свежие source/withdrawal/expiry/ledger/physical bytes checks на consumer boundary.
Private I/O вне DB transactions, затем locked fresh commit. APPROVED edit создаёт новый DRAFT;
branch не обходит inherited provenance/withdrawal. Legacy/manual/TEXT contracts сохраняются.
Не делай новый analysis ради молчаливого изменения origin existing Identity.

Done: PDF copy/read/approval работают по явному contract; stale/missing/corrupt bytes,
expired/new ERROR scan, foreign source, revoked membership, withdrawal during I/O/approval
и inherited-branch bypass отклоняются. PostgreSQL/runtime/concurrency/migration tests и
полный gate проходят. Docs, current plans, report и local commit обязательны.
```

## 02 — Согласованная eligibility policy для остальных sources

Prerequisite:01. Issue: #121. Architecture/product decision task.

```text
Применяй00. Разбери URL metadata/text/screenshots, uploaded images и social snapshots,
которые уже ingestятся, и подготовь ADR eligibility для каждого source kind.
Раздели исходные bytes, extracted text, screenshot и validated provider metadata;
FETCHED/CLEAN не означают permission для AI. Укажи exact snapshot/checksum/version/provenance,
latest scan/expiry/retention rules, explicit review/withdrawal и поддерживаемый model input.

Сравни варианты по security, correctness, complexity и user value. Не выбирай молча vision
provider/OCR, external data transmission/privacy policy или отказ от утверждённых MVP inputs.
Если product decision необходим, сформулируй конкретный выбор и рекомендацию; пока ждёшь,
подготовь contracts/threat model и независимые docs. Обоснованный технический выбор внутри
ранее утверждённого scope делай самостоятельно.

Done: approved decisions явно определяют supported/unsupported/requires-provider states
и зависимости03–05; source policy не скрывает MVP gaps. Синхронизируй планы; docs-only
checks и commit. Не запускай full Maven только ради ADR без product changes.
```

## 03 — URL human review и immutable admission

Prerequisite: утверждённая URL policy02. Issue: #121.

```text
Применяй00. Реализуй scoped human review/admission/current snapshot/withdrawal для
разрешённого в ADR URL textual snapshot. Выбирай exact bounded extracted text/metadata;
сохраняй original fetch/extraction/scan provenance и human actor/time.
Legacy metadata без нужного provenance не backfill как verified input.

Review не должен делать новый сетевой fetch по client URL. Сохрани existing SSRF/public-IP
pinned fetch boundaries; private retained bytes/artifacts перепроверяй по принятой policy.
Изменённые source/context/scan/expiry и withdrawn sourceVersion дают безопасный отказ.

Done: explicit review→consent→current read→withdrawal, versioned re-review/history bounds;
foreign/role/actor/unknown fields, stale/corrupt/unavailable input и concurrent revocation
проверены. PostgreSQL/runtime/migration и full gates, API/docs/report/local commit.
Если URL policy не утверждена, dependency остаётся pending; не изобретай формат.
```

## 04 — URL analysis → Identity consumption и publication

Prerequisite:03. Issue: #121.

```text
Применяй00. Подключи admitted URL textual snapshots к bounded server-selected generation
и Identity provenance/approval по ADR02. Сохрани total input budget и exact selected-source
citations; kind-specific eligibility проверяй перед model egress и final publication.
Модель не выбирает foreign sources и не получает arbitrary fetch/tools/private references.

Переиспользуй проверенные orchestration/ports; source kind не должен позволять пропустить
physical/access/withdrawal guards. Сохрани old TEXT/PDF receipts и digest compatibility
либо обоснуй новый versioned format без rewriting history.

Done: mixed supported sources публикуются атомарно; source/access/expiry/scan/withdrawal
races и malicious webpage instructions не превращаются в backend commands/egress.
Identity copy/approval/inherited lineage проверены вместе с SQL/runtime guards.
Проведи full qualification, docs и local commit.
```

## 05 — Image/social eligibility по принятому contract

Prerequisite:02, ready consumers01/04. Issue: #121.

```text
Применяй00. Сверь ledger и ADR0027: social text уже qualified; не реализуй повторно.
Visual provider-dependent остаток выполняется на AI integration stage после backend.
Реализуй оставшиеся разрешённые ADR02 image/social evidence scenarios.
Для visual Identity используй прямой multimodal путь; отдельный OCR/observations pass не обязателен.
Сначала проверь actual extraction/provider contracts. Не утверждай, что screenshot/bytes
уже стали текстом, и не выдумывай OCR/vision observations. Если нужны отсутствующие provider
или privacy decisions, подготовь exact port/contracts и зафиксируй blocker.

Для разрешённых inputs нужны bounded immutable review snapshot, consent/withdrawal,
tenant/provider-origin/source/checksum/time binding, current lifecycle и validated output.
Generation/copy/approval должны повторять required kind-specific checks. Live OAuth/account
qualification #32/#33 не обходить fake credentials. Unsupported kinds отклоняются явно.

Done: все согласованные здесь source scenarios имеют positive и forbidden tests,
original provenance/history preserved, concurrency/SQL/runtime/full gates passed.
Если stage требует несколько independent kind slices, сохрани отдельные commits и
продолжай этот номер до его actual acceptance boundary; не закрой #121 по одному виду.
```

## 06 — APPROVED Identity → Strategy source binding

Prerequisite:01–05 либо явно согласованный supported source scope. Issue: #121.

```text
Применяй00. Переведи новый Strategy generation на конкретную immutable APPROVED Identity
revision вместо legacy current Brand.signature. Сначала ADR правила выбора approved revision,
source eligibility, когда повторно проверяется evidence и как source сохраняется в истории.

Capture actual actor/scope/source ID/version и relevant immutable provenance; external model
I/O вне locks; completion rechecks permissions, parents, Identity/source eligibility и deadline.
Новая approval/edit не должна молча заменить captured source. Отсутствие acceptable source
даёт safe отказ. Legacy Strategy history не получает выдуманный Identity approval.

Done: approved Identity→new Strategy DRAFT с source link; missing/DRAFT/foreign/withdrawn/
expired source, approval/edit/archive/access races denied по принятому contract.
Existing Strategy editing/approval/history и TEXT/PDF guards не регрессируют.
Migration/runtime/PostgreSQL/full/startup gates, API/docs/report/local commit.
```

## 07 — Закрыть весь #121 по acceptance criteria

Prerequisite:06, AI integration stage и все required #121 scopes фактически delivered.
До этого07 остаётся REVIEWED / CLOSURE BLOCKED; не prerequisite независимых09–22/28.

```text
Применяй00. Проведи whole-issue review #121 по актуальным GitHub acceptance criteria и
плану #107. Сопоставь каждое требование с code/API/ADR/tests, не считай unchecked box
единственным источником статуса. Исправь gaps/regressions; недоставленные scopes назови точно.

Когда issue действительно завершён, выполни required full Docker/Python/Linux/startup gates,
security review и completion report. Commit finished slices; push mvp по существующей
whole-issue policy, проверь verify/security CI именно отправленного source SHA.
Если remote CI fails, исправь и снова qualify новый SHA. После actual gates синхронизируй
#121/#107 и закрой только fulfilled issue. Для external payload obey current exact approvals;
при required review rejection сначала concrete draft, затем user approval. Merge/deploy отдельно.

Done: acceptance matrix, actual pushed SHA/CI URLs/results, docs и honest remaining scopes.
При unfinished required scope issue остаётся open; reported blocker не считается closure.
```

## 08 — Backend portfolio milestone

Prerequisite: qualified supported textual scope01–06, actual API/startup availability.
Закрытие07 и visual/provider implementation не нужны для этой ограниченной demo.
Optional documentation/demo task; не блокирует09.

```text
Применяй00. Подготовь воспроизводимую backend demo для резюме: понятный README, architecture
diagram, API walkthrough и sanitized examples для onboarding→evidence review→Identity approval
→Strategy. Используй текущий Docker/Clerk setup и clearly labeled isolated generation fixtures.
Документируй supported sources и pending content/frontend/real-provider functionality.

Проверь запуск по инструкции из чистого disposable local окружения, actual runtime DB role,
health/auth rejection и оба tenants. Не удаляй существующие volumes/data ради clean run.
Не вводи auth bypass или real tokens в examples. Offline fixtures не claim live AI quality.

Done: newcomer может пройти backend walkthrough; prerequisites/commands/result expectations
точные, секретов нет, runnable examples проверены. Docs-only commit либо relevant qualification
для найденных setup fixes. Это промежуточный milestone, full demo-MVP пока не завершён.
```

## 09 — Follow-up contracts для Jobs, budgets и Playbook

Статус15 октября: design qualified — [report09](prompt09-contracts-report.md).
Контракты10–12: [ADR0028](adr/0028-durable-ai-workflow-contracts.md);13:
[ADR0029](adr/0029-full-playbook-contract.md). Mapping и exact issue drafts:
[A/B/P](ai-workflow-issue-drafts.md). Не повторять design без новых findings; дальше10.

Prerequisite: qualified supported source scope01–06 и ADR0027; сверить актуальные contracts.
#107 Task2.4/2.5/3.3. Закрытие07, demo08 и выбор AI provider не требуются.

```text
Применяй00. Найди существующие issues для AI Jobs, budgets/idempotency и full Playbook.
Не создавай duplicate и не выдумывай issue numbers. Подготовь отдельные cohesive scopes,
dependencies, acceptance criteria, API/provenance/recovery semantics и security tests.
Если issues ещё отсутствуют, сохрани exact issue drafts и подготовь создание по текущим
user authorizations/approval requirements; отсутствие внешней записи не блокирует local design.

Нужен ADR async source capture/worker publication/lease fencing, idempotency scope и
cost reservation/settlement при uncertain provider outcome; reused ingest queue не ready AI Jobs.
Cost/latency/moderation unknown не backfill. Принятые product limits фиксируй явно.

Done: reviewed contracts и mapping future tasks10–13→actual issues/local drafts;
оба плана aligned, docs-only checks/commit. Не реализуй весь механизм в этом design task.
```

## 10 — Durable AI Jobs: submission, worker и fenced completion

Prerequisite:09. #107 Task2.4.

```text
Применяй00. Реализуй durable Brand/Identity и Strategy generation по approved async contract:
submission202/Job Location, scoped status/result, persisted captured input/source links,
atomic claims, bounded retries/deadlines и explicit terminal outcomes.
Используй существующую queue где корректно, различай ingest и generation jobs.

Worker выполняет model I/O вне DB transactions. Перед egress/publication actual access,
source eligibility/withdrawal/version/deadline и lease/attempt fencing; lost lease никогда
не публикует результат. Human approval остаётся отдельной операцией.
Сохрани backward compatibility либо явно version/deprecate synchronous contract.

Done: submit→worker→one result→status с API/OpenAPI, foreign Job404, retries/re-delivery
и two workers не дают duplicate publication. PostgreSQL claims/concurrency, crash/restart
recovery и full gates обязательны. Production adapter disabled до qualification.
```

## 11 — AI Jobs recovery и operational visibility

Подготовка после Spring migration: [actual contracts и remaining acceptance](prompt11-readiness.md).
Перед стартом проверить migration qualification и actual HEAD; не повторять delivered10.

Prerequisite:10; если весь scope уже qualified10, не делай повторную реализацию.

```text
Применяй00. Закончи recovery/reliability gaps AI Jobs: expired lease, crash до/после provider,
restart, unavailable/timeouts, cancellation semantics по contract, source/access revocation.
Определи ambiguous provider outcome без ложного retry-safe обещания и duplicate results.
Idempotency/cost integration оставь совместимой с12, не освобождай uncertain spend молча.

Добавь bounded concurrency, controlled error codes, Job/attempt correlation и metrics без
prompts/tokens/user content/secrets в logs. Не добавляй публичные debug/admin bypasses.

Done: deterministic failure-injection/restart tests и PostgreSQL lease/publication races
проверены, terminal status/provenance coherent, runbook воспроизводит recovery.
Required gates/docs/commit; late stale worker rejected независимо от счастливого frontend.
```

## 12 — Idempotency, tenant budgets и accounting

Prerequisite:09–11. #107 Task2.5.

```text
Применяй00. Реализуй agreed generation idempotency и durable budgets. Key связывается с
actor/tenant/operation/request digest; replay same request возвращает coherent existing
operation, другой payload отвергается. Согласуй approved-edit branching semantics, не
приписывай unrelated операции idempotency без contract.

Cost reservation/settlement и concurrency limits работают при retries/crash/multiple workers;
uncertain provider cost явно uncertain, не ноль. Fake usage отличается от измеренного live
usage. До выбора provider используй validated neutral usage records и fail-closed limits.

Done: concurrent replay exactly one operation, key/payload/scope collisions denied,
retry не обходит budget, crash/recovery не освобождает расход ошибочно; thresholds/overflow/
deadline boundaries и SQL/runtime invariants проверены. Full qualification/docs/commit.
```

## 13 — Полный Strategy Playbook

Prerequisite:06,09–12. #107 Task3.3.

```text
Применяй00. Закончи approved Playbook contract: audience assumptions, channel goals/mix,
tone spectrum, competitive whitespace и four-week outline вместе с existing pillars/cadence.
Определи field semantics по product scope, single coherent interpretation Identity/Strategy,
weights/channel/timezone bounds и ready source для Content generation.

Реализуй domain/DTO/migration/provider contract, review/edit/approval/history и async jobs.
Model output validated, approved content immutable. Legacy missing fields сохраняются как
legacy, без fabricated audience/goals/AI output. Human-edit attribution не provider output.

Done: complete Playbook generate→edit→approve→versioned read; malformed output, foreign
source, wrong weights/timezone, stale versions и late completion denied. PostgreSQL/migration/
runtime/full gates, API/docs/commit. Согласованная отсутствующая product semantics уточняется.
```

## 14 — Post revisions и human approval

Prerequisite:13. Issues: #115/#103; actual issue scope сверить перед implementation.

```text
Применяй00. Реализуй platform-specific Post identity/revision lifecycle из конкретного
approved Strategy: Instagram/TikTok content fields, actor/time/provenance, DRAFT edit с
expectedVersion, separate approval и approved edit→new DRAFT с immutable lineage.
Post identity сохраняется между revisions; approved history не rewrite/delete.

Согласуй invariants и scheduling/export eligibility до migration. Dedicated DTO не
позволяет client выставить approvedBy/provider/source ownership. Authorization на resource
и DB constraints independent. Legacy SocialPost data migrate без fabricated audit.

Done: coherent create/read/list/edit/approve/revision API, bounds/ownership/role/stale/replay
negatives, SQL/runtime history guards и concurrent edit/approve/archive tests. Full gates/docs/commit.
```

## 15 — Content generation и regeneration Jobs

Prerequisite:10–14. Issues: #98/#115 по actual acceptance.

```text
Применяй00. Реализуй validated Content generation port и durable Jobs для captions,
hashtags, platform format, media prompts и requested four-week slots из approved Strategy.
Все результаты — reviewable DRAFT, с original source/model/prompt/attempt provenance.
Regeneration field/all сохраняет Post identity, создаёт правильную revision и не меняет approval.

Используй existing lease/idempotency/budget guards, recheck Strategy/Identity/evidence
eligibility по agreed lifetime policy. Local/test deterministic fake marked demo;
live content/media provider ещё не выбран. Не изобретай успешные assets/moderation/cost.

Done: generation→editable draft→regeneration, bounded validated output; late/lost-lease,
source/access change, malformed output и duplicate delivery не публикуют stale/duplicate data.
API/worker/PostgreSQL/full gates, docs и commit.
```

## 16 — Calendar, scheduling и timezone/DST

Prerequisite:14–15. Issues: #115/#103.

```text
Применяй00. Реализуй bounded calendar API и schedule/reschedule semantics: timezone,
UTC instants, platform slots, draft/approved eligibility и version checks. Отдельно определи
ambiguous/nonexistent local time при DST, duplicate slot policy и bulk operation atomicity.
Пользовательский timezone не является произвольным system setting.

Scheduling не вызывает platform publishing. Изменение approved content создаёт revision;
определи отличие scheduling metadata от immutable approved content. Backend validates drag
reschedule и batch input независимо от frontend. Query intervals/pagination bounded.

Done: four-week calendar, schedule/reschedule/history и UTC/local round-trip; DST gap/fold,
foreign IDs, invalid ranges, conflicting edits/slots/archive/access races tested.
PostgreSQL/index review/full gates/API/docs/commit.
```

## 17 — Private media/assets и platform validation

Prerequisite:14–15. Issue: #98.

```text
Применяй00. Заверши media references/asset lifecycle для demo Content workflow: tenant-bound
private assets, explicit origin/checksum/format/size, platform validation и moderation state.
Reuse reviewed storage/scan boundaries, не путай quarantine object и publishable approved asset.
Demo media fixtures явно labeled и не выдают себя за actual generated images/videos.

Нужны scoped bounded read/delivery contract, no client server filename, no arbitrary URL
fetch, safe caching и deletion/retention behavior для referenced history. Live media adapter
оставь отдельным scope; новый adapter не выбирай без product/privacy/cost decision.

Done: valid Post asset references readable authorised owner, foreign/unverified/oversized/
wrong-format/missing assets rejected; replacement/archive/retention races и provenance
checked. Applicable storage/renderer/SQL/runtime/full gates, docs и commit.
```

## 18 — Durable safe CSV export

Prerequisite:14–17. #103 export acceptance; actual issue mapping сверить.

```text
Применяй00. Реализуй deterministic bounded CSV export только eligible approved Post revisions.
Зафиксируй export columns/platform/timezone/encoding contract, source versions/checksums,
creator/time provenance, idempotency и durable Job/status/private download lifetime.
Capture stable batch; fresh access/archive/source checks до generation/publication/download
по принятому contract. Partial/stale export не masquerades current approved content.

Защити spreadsheet formula injection включая leading whitespace/control characters,
CSV quoting/newlines/Unicode, filenames/path traversal, BOLA и resource exhaustion.
Download не должен раскрывать private storage keys или бессрочный bearer URL.

Done: approved four-week content export→authorized download→correct CSV; DRAFT/foreign/
withdrawn/stale content и concurrent edits denied или deterministic snapshot по contract.
Repeat export coherent, caps/escaping/formula negatives tested; full gates/docs/commit.
```

## 19 — Manual/CSV metrics ingestion

Execution19: [ADR0036](adr/0036-manual-csv-metric-import.md), [API](metrics-import-api.md),
[report](prompt19-metrics-import-report.md). Manual/CSV scope отделён от broader live
provider synchronization #99; закрытие #99 им не подменяется. Следующие номера не запущены.

Prerequisite:14–18. Issue: #99.

```text
Применяй00. Реализуй bounded manual/CSV metrics import для scoped platform/Post identity.
Определи raw origin отдельно от normalized observation, units/counters/time window,
deduplication/idempotency, correction semantics и validated foreign-resource mapping.
Existing ingest Job infrastructure reuse для больших imports по approved contract.

Не превращай missing values в zero; invalid/negative/overflow/out-of-order data обрабатывай
явно. Парсер ограничивает bytes/rows/columns/field lengths и не выполняет formulas/URLs.
Client role/tenant/audit fields не writable. Source time и ingestion time разделены.

Done: manual/CSV→validated observations, duplicate/concurrent imports coherent;
foreign Post, malformed headers/encoding/numbers/timestamps, oversize payload и rollback
tested on PostgreSQL. API/docs/migration/runtime/full gates и commit.
```

## 20 — Performance calculations и analytics API

Execution20 — QUALIFIED LOCAL (2026-10-17): [ADR0037](adr/0037-performance-analytics.md), [API](performance-api.md),
[report20](prompt20-performance-report.md). Actual #104 acceptance сопоставлен с code/tests;
historical attribution explicit/versioned, missing context не подменяется current content.

Prerequisite:19. Issue: #104.

```text
Применяй00. Реализуй actual performance metrics/formulas и bounded API по platform/pillar/
format/time. Сначала определи numerator/denominator, time windows, normalized units,
minimum sample и missing/stale state. Не сравнивай несопоставимые observation windows.

Use scoped queries, appropriate indexes и bounded aggregates; division by zero/overflow
не скрывай invented values. Historical Strategy/Post associations должны позволять объяснить
результат. UI future clients получают DTO с достаточными provenance/coverage caveats.

Done: manual independently calculated fixtures совпадают с formulas; missing/zero/sparse/
duplicate/corrected data, timezone boundaries и cross-tenant denial tested. Relevant query
plan/performance checks, PostgreSQL/full gates, docs и commit.
```

## 21 — Evidence-linked recommendations и apply→Strategy DRAFT

Execution21: [ADR0038](adr/0038-evidence-linked-recommendations.md), [API](recommendations-api.md),
[report21](prompt21-recommendations-report.md). **21 QUALIFIED LOCAL**.
Full2104/0 failures/errors,4 Windows filesystem skips covered Linux37/37; PG61/61,migration1/1;Python10/10,renderer29/29,reports/Spotless/JaCoCo/startup PASS;freshSBOM194/OSV0;722 stable source/build hashes.
Live inference не заявляется; report22 не включён в этот execution.


Prerequisite:13,19–20. Issue: #100.

```text
Применяй00. Реализуй recommendation contract с supporting metric observations, confidence/
uncertainty, origin/rule/model attribution и bounded explanatory output. Не выдавай heuristic
или demo fake за live AI inference; minimum sample enforced. Model citations проверяй по
captured scoped metrics, не принимай invented evidence IDs или causal claims как факт.

Apply recommendation создаёт новый editable Strategy DRAFT с source recommendation/metrics
и lineage; approved source не меняется автоматически. Fresh access/metric/source lifecycle
и concurrency guards защищают generation/apply. Idempotency по approved contract.

Done: metrics→reviewable recommendation→new DRAFT, stale/foreign/insufficient data и
forged supporting IDs denied; repeated apply/concurrent changes coherent. SQL/runtime/
PostgreSQL/full gates, docs/report/commit.
```

## 22 — Creator reports и reviewable provenance

Execution22: [approved product package ADR0039](adr/0039-creator-performance-reports.md),
[contracts/test matrix](prompt22-report-contract.md). **22 QUALIFIED LOCAL**.
[report22](prompt22-reports-report.md), [API](creator-reports-api.md). Approved package preserved;
Full2250/0 failures/errors,4 Windows filesystem skips covered Linux37/37; report H266/66,PG76/76,migration1/1;Python10/10,renderer29/29,reports/Spotless/JaCoCo/startup PASS;freshSBOM194/OSV0;743 stable source/build/config hashes.
Исторический execution22 завершён; текущий статус23 указан ниже и в progress ledger.

Prerequisite:19–21. Issue: #105.

```text
Применяй00. Реализуй creator-facing performance/recommendation report по текущему issue
contract. Сначала согласуй required format и retention, если они ещё не определены;
не добавляй PDF/mail delivery только из предположения. Нужны bounded date range,
coverage/missing/stale disclosure, supporting observations и stable source versions.

Если report generation expensive, use durable Job/idempotency и private scoped result.
Не включай лишние personal data/prompts/private keys. История объясняет, какие данные
поддерживали recommendation; дальнейшие corrections не переписывают original provenance.

Done: authorised report readable/exportable в согласованном формате, foreign/download/
oversize/missing-data negatives и changed-source behavior tested. Docs/API/full relevant
gates/commit; post-MVP delivery/integrations не заявляй delivered.
```

## 23 — Frontend foundation, Clerk и onboarding

Foundation **QUALIFIED LOCAL** в `3b49c94`: [ADR0040](adr/0040-frontend-bearer-onboarding.md),
[report23](prompt23-frontend-report.md). Live Clerk signup/login остаётся external blocker.
Решение пользователя2026-10-18: развивать существующий demo-дизайн; [план/mapping](prototype-ui-adaptation-plan.md).
**Visual follow-up23 QUALIFIED LOCAL**, [report](prompt23-visual-report.md).
Foundation не реализуется повторно. Исторический следующий scope24 уже qualified locally;
теперь действует [UI rework plan](ui-rework-plan.md),25–27 и полные live/release gates open.

### Foundation contract — исторически выполнен

Prerequisite: stable qualified onboarding API;08 optional,07 closure не требуется.
Можно чередовать с backend по готовым contracts; frontend не блокирует AI integration. Issue: #3.

```text
Применяй00. Создай или продолжи standalone Next.js/React/TypeScript frontend в принятой
repository структуре. До dependency additions проверь current official docs/security и
зафиксируй compatible versions/lockfile. Prototype — UX reference, не agency CRM/iframe scope.

Нужны typed API client/shared Problem Details, validated env config без server secrets,
Clerk integration и Workspace/Brand onboarding. Auth architecture (Bearer либо cookie/BFF)
выбери явно: cookie требует CSRF review, CORS/origins scoped. Не используй token bypass.
Local auth fixture — только технически изолированный test setup, без production shortcut.

Done: signup/login по available Clerk setup→Workspace/Brand screens, loading/empty/error/
401/403/404 states, keyboard accessibility и responsive layout; tenant switch не даёт stale
cross-tenant cache. Actual lint/typecheck/unit/build и auth E2E, docs/commit; live credentials
missing сохраняются как external blocker, не выдуманное прохождение live login.
```

### Visual follow-up23 — qualified locally, contract сохранён для истории

Prerequisite: qualified foundation23/ADR0040, доступный проверенный `Cadence Prototype.html`;
source fingerprint и mapping в [плане переноса](prototype-ui-adaptation-plan.md). Issue: #3.
Это продолжение23, стабильные номера01–30 не меняются.

```text
Применяй00. Выполни visual follow-up23 по docs/prototype-ui-adaptation-plan.md.
Сначала actual HEAD/status и reference fingerprint. Foundation23 уже delivered: не повторяй
Clerk/API/onboarding implementation и не меняй ADR0040 auth architecture ради styling.

Используй существующий Cadence Prototype.html как основной visual/interaction reference.
Сначала browser inspection и inventory tokens/typography/themes/icons/assets, затем перенос
собственных UI components в текущий Next.js/React/TypeScript shell и Workspace/Brand screens.
Сохрани узнаваемую композицию и Cream/Dark/Sage states с проверками contrast/focus;
адаптируй demo client к actual Creator/Workspace/Brand. Изменения reference обоснуй в report.
Bundled old React/Babel/scripts, fake progress/results и agency/iframe ownership не переносить.
Перед fonts/media/dependency reuse проверь provenance/license/security/external requests;
не добавляй CDN/provider/privacy policy молча. Backend contracts и private auth boundary сохранены.

Done: actual Workspace/Brand onboarding в дизайне прототипа; desktop/mobile visual comparison,
keyboard/axe/loading/empty/error/401/403/404/409, tenant/session switch и late response guards.
Actual lint/format/typecheck/unit/build/browser gates, production fixture isolation, docs/commit.
Live credentials missing — external blocker.24–27 этим шагом не объявляй delivered.
```

## 24 — Frontend evidence и Brand Identity

Execution2026-10-18: **QUALIFIED LOCAL repository scope**. Frontend58unit/56Chromium+axe; full2286/0failures/errors,4Windows skips covered Linux37; actual HTTP/startup/reports/security gates PASS.
[Report24](prompt24-identity-report.md). Live Clerk/AI/social-provider gates separate;25–27 not started.

Prerequisite: qualified supported source scope01–06, foundation23 и visual follow-up23. Issue: #3.
Visual UI после соответствующего backend/adapters; до этого явно unavailable, не fake success.
07 closure не prerequisite UI для уже supported workflows.

```text
Применяй00. Развивай Step1Upload/AnalyzingPanel/ExtractedPanel выбранного prototype через
shared components visual follow-up23, сохраняя actual contract. Реализуй UI source
registration/Job status, exact human review/consent,
withdrawal/history, Identity generation/edit/citations/approval/version history.
Используй actual OpenAPI contracts, current version tokens и scoped API; raw server/model
text отображай safely как data, не executable HTML. Confirmation относится к показанному
точному snapshot; stale409 предлагает fresh review, не silent consent.

Fake/demo attribution visible; no automatic approval. Обработай expired/missing scan,
unavailable storage/model, revoked access, unknown Job outcomes и optimistic edit conflicts.
Do not hide backend error by fabricating success/Identity/source content in browser state.

Done: creator проходит evidence→review→Identity draft→edit→approve; forbidden cross-tenant,
withdrawal/stale variants проверены backend + frontend E2E. Accessibility/responsive/error
states, lint/typecheck/unit/build, docs и commit.
```

### UI design review и rework23–24 — перед25, без нового номера

Запрос2026-10-19 и acceptance: [UI rework plan](ui-rework-plan.md).
Следующий UI этап — проектирование; конкретный дизайн должен быть рассмотрен
пользователем на макетах до массовой замены UI. Рекомендуемый backend приоритет28 сохранён.

```text
Применяй00. Подготовь UX/UI design review по docs/ui-rework-plan.md после actual HEAD/status.
Существующие09–24 не реализуй заново. Сначала inventory текущих workflows и проблем,
navigation map и UI→actual API/state mapping, затем конкретные desktop/mobile wireframes
onboarding/Identity и representative Strategy/Calendar/Performance views для общей основы.
Покажи варианты и рекомендацию: степень сохранения prototype/themes, navigation, density,
typography и shared components ещё не выбраны. Не объявляй design artifacts настоящими
данными или passing workflow. Auth/tenant/review/approval/history/provider boundaries сохранены.
Done: reviewable design package, API/unsupported-state mapping, согласованный implementation
scope, планы/docs-only checks и commit. Большую реализацию UI этим design шагом не начинай.
```

После review конкретного design package и qualified backend28:

```text
Применяй00. Выполни UI rework23–24 по выбранному design package и docs/ui-rework-plan.md.
Перенеси shared foundation/navigation, затем существующие Workspace/Brand/evidence/Identity
workflows вертикальными slices. Переиспользуй typed API/Clerk Bearer и current DTO/contracts;
не заменяй backend/security/consent/version/history semantics ради оформления.
Сначала проверь selected design/current prerequisites. Assets/fonts/dependencies/egress
требуют provenance/license/security review; unknown gaps честно unavailable.
Done: actual onboarding→evidence→review→Identity draft→edit→approve в новом UI;
desktop/mobile, keyboard/axe, error/401/403/404/409, tenant/session/late response negatives,
lint/format/typecheck/unit/build/export/browser gates, docs/report и local commits.
25–27 этим rework не объявляй delivered. При backend changes примени full relevant00 gates.
```

## 25 — Frontend Strategy Playbook

Prerequisite:13,23–24; выбранный design package и UI rework23–24 по [плану](ui-rework-plan.md). Issue: #3.

```text
Применяй00. Используй согласованный новый design package и shared UI rework23–24.
Прежний Step2Strategy/cards — historical reference, не обязательная новая композиция.
Реализуй Playbook UI: source approved Identity, async generation/status,
audience/goals/tone/competitive whitespace/pillars/cadence/four-week outline, editing,
separate approval и version/provenance history. Используй реальные backend DTO/bounds.

Не допускай silent lost updates или изменения approved version. Job retry/error/pending
states показывай честно; stale source/access conflicts требуют actual backend result.
Client validation улучшает UX, сервер остаётся authoritative. Private model metadata
показывай только в нужном для review объёме.

Done: approved Identity→Playbook generate→edit→approve через UI; invalid weights/timezone,
stale version/foreign source/archive и provider failure scenarios tested. Actual frontend
gates/E2E, docs и commit.
```

## 26 — Frontend Content Calendar, review и CSV

Prerequisite:14–18,23–25; согласованный новый shared UI по [плану](ui-rework-plan.md). Issue: #3.

```text
Применяй00. Используй согласованный новый design package/shared UI; прежний Step3Content — reference;
trending audio без approved provider/contract явно unavailable. Реализуй Content
generation/status, platform-specific editors/regeneration,
revision history/approval, media preview, four-week calendar и schedule/reschedule, CSV
export/download. Backend contracts authoritative; direct publishing buttons/actions не добавляй.

Drag/reschedule имеет keyboard alternative; timezone/DST и pending server confirmation
явно отображаются. Не выдавай optimistic move за committed schedule при409/403.
Media/private downloads безопасно scoped; export only eligible approved data.
Обработай empty calendar, missing asset, Job timeout/retry, stale source/version и quota errors.

Done: creator генерирует/редактирует/утверждает/расписывает четырёхнедельный content и
скачивает правильный safe CSV. Actual lint/typecheck/unit/build/E2E, responsive/accessibility
checks, docs и commit; tests используют scoped fixtures без auth bypass production.
```

## 27 — Frontend Analytics, recommendations и reports

Prerequisite:19–22,23–26; согласованный новый shared UI по [плану](ui-rework-plan.md). Issue: #3.

```text
Применяй00. Используй согласованный новый design package/shared UI для KPI/charts/recommendations;
прежний Step4Analytics — reference. Audience demographics и прочие widgets без current API явно unavailable,
не выдумывай показатели или новые integrations. Реализуй manual/CSV metrics import,
validation feedback, Performance views,
supporting observations/coverage, recommendations/reports и apply→новый Strategy DRAFT.
Graphs получают actual backend formulas/data; missing/stale/insufficient sample видны,
не replaced synthetic browser numbers. Demo datasets явно labeled.

Обеспечь bounded filtering/pagination, safe file/error rendering, accessible charts с text
alternative и separation caches по tenant. Apply не меняет approved Strategy автоматически.
Failure/retry/duplicate import/stale recommendation обрабатываются по backend contract.

Done: content metrics→analytics→review recommendation→new DRAFT через UI; foreign scopes,
zero/missing/sparse data и errors tested. Actual frontend gates/E2E, docs и commit.
```

## 28 — Security, audit, observability и demo reliability gate

Execution28 backend: QUALIFIED LOCAL. [Actual report](prompt28-backend-report.md). Frontend/cross-stack28 PENDING; whole28 incomplete.


Prerequisite для backend scope:09–22; frontend23–27 и adapters не требуются.
Prerequisite для frontend/cross-stack scope:23–27 и qualified backend.
Issues: #87/#83/#116/#118 по actual scopes. Ledger отдельно фиксирует обе части28.

```text
Применяй00. Сначала выполни backend часть28 перед выбором AI API по ADR0027.
После frontend/adapters повторно проверь изменённые boundaries и заверши cross-stack часть;
backend pass не объявляет весь28 completed. Проведи cross-module threat/architecture review: auth/account/membership,
tenant caches/resource scoping, approval/export lineage, async fencing/idempotency/budgets,
SSRF/uploads/CSV/XSS/CSRF/CORS и logging. Исправь реалистичные gaps, не подавляй scanners.

Закончи обязательные для безопасной demo audit/security events, redaction, correlation/Job
metrics/health/readiness и bounded rate/concurrency limits по threat model. Выполни fresh
dependency/SAST/container/secret checks там, где инфраструктура позволяет; недоступное
отметь точно. Check rulesets/admin permissions без обещания включить unavailable enforcement.

Done: findings→fixes→regression evidence, actual permissions/scan results/runbooks,
backend required gates и startup passed для backend части; frontend/cross-stack gates
обязательны для завершения всего28. Production-specific hosting/TLS/IAM/
backup/load/pilot requirements остаются отдельно, не claim production-ready по local tests.
```

## 29 — Critical creator journey E2E и clean demo stack

Prerequisite: обе части28 и23–27. Issues: #118/#8/#116.
Fixture E2E допустим отдельно; live adapter qualification не выводится из его результата.

```text
Применяй00. Проверь весь journey на reproducible isolated demo stack:
onboarding→evidence review→Identity approval→Playbook approval→four-week content→edit/
approval/schedule→CSV export→manual/CSV metrics→analytics→recommendation→new Strategy DRAFT.

Используй реальные backend API/PostgreSQL/runtime role и применимые storage/scanner/renderer
fixtures, clearly labeled local/test generation adapters. Добавь second-tenant negative
journey, expired/revoked auth/source, optimistic conflict, worker restart/provider outage,
duplicate request/import и download access. E2E не должен проходить только против browser mocks.
Проверь clean setup по README в disposable resources без удаления пользовательских volumes.

Done: actual commands/results/artifacts, deterministic synthetic dataset, screenshots где
полезно, accessibility/responsive checks и CI E2E gate. Исправь failures без weakened asserts.
Missing live Clerk setup квалифицируй separately; test-auth isolation сама требует проверки.
Report явно разделяет fixture journey и live integration. Docs и qualified commits.
```

## 30 — Финальный GitHub/CV demo-MVP release

Prerequisite: required backend09–22, обе части28, frontend23–27,29;
01–06 по approved scope,07 closure после AI integration. Optional08 не обязателен.
При pending adapters/#121 допустим только явно ограниченный fixture demo report,
не full MVP acceptance и не разрешение push unfinished ancestry.

```text
Применяй00. Проведи final acceptance audit по product scope, #107 и текущим issue criteria.
Для каждого MVP outcome укажи implementation/API/UI, tests/E2E, limitations и actual status.
Закрой оставшиеся required gaps; fake labels не заменяют workflow implementation.

Обнови README, architecture/security decisions, quickstart, demo walkthrough, API examples,
known limitations и portfolio description. Текст честно разделяет implemented workflow,
isolated fake generation и pending real AI/commercial readiness. Не заявляй live AI quality.
Укажи repository source SHA, actual backend/frontend/full Docker/Python/Linux/E2E/scan gates.

После whole-issue completion push по текущей policy и проверь CI exact source SHA; синхронизируй
fulfilled GitHub issues/#107 согласно exact approvals и actual evidence. Merge/release tag/
deployment выполняй только при соответствующем отдельном разрешении, после reviewable result.

Done: reproducible documented UI demo полного creator cycle, current green exact-SHA CI,
immutable provenance/approval/security intact, remaining production checklist explicit.
Создай final docs report и local commit. Отдельно назови fulfilled demo-MVP facts,
engineering conclusions и unverified production/provider assumptions.
```

## Общий критерий перехода к следующему номеру

Implementation outcome и applicable acceptance выполнены, layers consistent, security review
проведён, tests фактически passed, required suites не skipped, docs/contracts актуальны,
qualified source сохранён. Наличие external approval blocker отражается отдельно от готовности
local implementation. Нельзя заявлять fulfilled remote CI/issue closure, пока их нет.
ADR0027 разрешает переход к независимому backend scope при отложенных05/07 без их
ложного закрытия. Backend часть28 даёт gate для AI integration, но не заменяет frontend review.
Architecture-only02/09 считаются завершёнными после actual decision/contracts, docs checks и commit;
они не являются implementation pass. Уже fulfilled task записывается verified, без duplicate code.

## Команды project gates для implementation

Ниже команды для будущих implementation runs, не результаты создания этого документа.
В PowerShell backend gate и обязательные report checks:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
```

Renderer suite выполняется по актуальной CI/verification инструкции с квалифицированным
image текущего renderer source. Старый image tag не доказывает проверку изменённого renderer.
Frontend commands берутся из фактического package manager/lockfile/package scripts.
Если менялся only frontend, объём backend rerun определяется impact и project requirements;
full cross-module journey проверяется29/30. Config/JPA/DI changes требуют startup.
После docs-only изменений full implementation suite повторно не заявляется.

## Принципы формулировки для Astra

Общие требования вынесены в00, чтобы не дублировать большой персональный алгоритм30 раз.
Каждый task задаёт конкретный результат, зависимости, границы решений и proof of completion.
Чтение контекста и повтор testing привязаны к необходимости; обязательные project gates сохранены.
Autonomy явная, существенные product/privacy решения не превращены в скрытые assumptions.
Это рекомендации организации работы; экономия tokens или ускорение не измерены на Cadence.

Источники OpenAI Docs:
[Astra prompting guidance](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-6-astra),
[instructions/context guidance](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra),
[reasoning effort](https://developers.openai.com/api/docs/guides/reasoning).
Готовность задач определяется текущим repository и выполненными checks, а не свойствами модели.

## Проверка актуализации 2026-10-15

Изменён только этот Markdown: маршрут/prerequisites, direct visual Identity contract,
backend/frontend части28 и отдельный AI integration stage. Проверены номера00–30, fences,
локальные ссылки, согласованность dependencies с ADR0027 и `git diff --check`; Spotless.
Full runtime tests не запускались: product code/configuration/migrations не менялись.
GitHub issues, push, merge и deployment этой актуализацией не изменены.

## Историческая проверка создания 2026-10-14

Docs-only задача: добавлены этот файл и ссылка в README, product code/configuration,
AGENTS.md и активные DEVELOPMENT_PLAN не менялись. Проверены numbering00–30 без gaps/duplicates,
33 fenced text blocks (общий алгоритм, запуск, продолжение,30 задач), баланс code fences,
43 relative file links в книге/README и `git diff --check`. Условные product decisions
отмечены02–05; qualified local work отделена от external approvals, exact-SHA CI и deployment.
Full Maven/Docker/frontend tests при создании промптов не запускались. Упомянутые ранее
1053 tests — исторический implementation run, не proof выполнения этой sequence.


### Execution note10 (2026-10-15)

Actual implementation/contracts: [durable generation](durable-ai-generation.md),
[report10](prompt10-durable-generation-report.md). Проверять final gates/commit по отчёту,
не выполнять10 заново из-за исторического текста prompt09. Remaining11/12/13 отдельны.
Baseline09 pushed по явному запросу; security CI Spring failure вынесен пользователем в
отдельную stack migration после10. Это не approval production/live AI activation.

### Execution note15 (2026-10-16)

Actual baseline2c8a2b6:10–14 delivered, не повторять. Scope15 qualified locally:
[report15](prompt15-content-generation-report.md), [ADR0032](adr/0032-content-generation-jobs.md),
[API](content-generation-api.md). #98/#115 broader acceptance остаётся open;16–18 не начаты.
Scope15 не выбирает live provider/media/privacy/cost policy и не объявляет assets готовыми.
Final full1554/0failures/errors,3Windows skips covered Linux35/35;ContentPG25/25,crash8/8,
Python10/10,renderer29/29,reports/coverage/startup PASS;SBOM194/OSV0. Следующий16 не начат.


### Execution note16 (2026-10-16)

Actual baselinec0e5473:14–15 delivered, не повторять. Task16 qualified locally:
[report16](prompt16-calendar-report.md), [ADR0033](adr/0033-calendar-scheduling.md), [API](calendar-api.md).
Full1638/0failures/errors,3Windows skips covered Linux35/35;CalendarPG45/45;Python10/10,
renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;605 stable source/build hashes.
Следующий17 не начат. #115 assets17/#103 durable CSV18 остаются open; no push/closure.
READY calendar entry не означает media/export/publishing permission; provider/privacy scope unchanged.


### Execution note17 (2026-10-16)

Actual baselinef029531:14–16 delivered не повторялись.17 qualified locally:
[report17](prompt17-media-report.md), [ADR0034](adr/0034-private-demo-media.md), [Media API](media-assets-api.md).
Full1731/0failures/errors,4Windows skips covered Linux37/37;MediaPG49/49;Python10/10,
renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;632 stable source/build hashes.
Следующий18 не начат. Exact authored DEMO PNG/MP4 refs не являются live generated assets;
live producer/general decoder/moderation/privacy/cost/deletion decisions не выбраны и не скрыты.
18 должен capture/check exact revision и current media; public/presigned URLs не согласованы.
No push/remote CI/issue closure; production demo creation/delivery/attachment disabled.


### Execution note18 (2026-10-16)

Prerequisite14–17 actual clean abaeeb1; #103/#107 checked.18 QUALIFIED LOCAL.
[report18](prompt18-csv-export-report.md), [ADR0035](adr/0035-durable-csv-export.md), [CSV API](csv-export-api.md).
Durable approved immutable CSV batch, current private download, no provider/publishing changes.
Full1810/0failures/errors,4Windows skips covered Linux37/37;CSV PG43/43,HTTP24/24,domain11/11,migration1/1;AI child-JVM8/8. Python10/10,renderer29/29,mandatory reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;651 stable source/build hashes.
19 не начато. No push/external issue edits/closure/merge/deployment.
