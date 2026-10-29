# Отчёт: новый scope и последовательные промпты Cadence V2

Дата:2026-10-20. Исходный HEAD `fe4159a5ca3f8d164f8f1179bdd0d0289cc7e76b`, ветка `mvp`,
ahead31 относительно локального `origin/mvp`. Это **docs-only работа**, не implementation.

## Что было сделано

Новый продуктовый сценарий описывает organic workflow от brand assets до готового
фото/видео или монтажа raw footage, approval, Instagram/TikTok publication, research,
metrics/reports/reminders и дальнейшего Strategy revision.
Подготовлены V2-00 execution contract,35 последовательных заданий и6 отдельных backlog decisions.
В начало плана вынесены готовое, адаптация, новое и переработка.
Все прежние00–30 и безномерный AI stage имеют mapping. Исходный HTML разобран без исполнения
JavaScript;48 функциональных пунктов получили explicit status/disposition.

Уточнения пользователя сохранены: только органическое продвижение; £20 demo AI calls,
hosting/rendering/data/training оценить отдельно. Providers/video/training не утверждены.

## Как это работает

Текущая документация имеет одну точку scope — `product-scope.md`, один последовательный
execution plan V2 и inventory с доказательствами. Старые планы/ledger/reports сохранены
как исторические, с заметным supersession. README и AGENTS указывают актуальный маршрут.
V2 IDs не переиспользуют номера старого плана; qualification прошлого scope не переносится
автоматически на новые workflows.

## Архитектурное решение

Сохранены modular monolith, Workspace boundary, current OWNER checks, immutable approved
revisions, Jobs/recovery и DTO/SQL guards. Предложено расширить domains research/media/
publishing/notifications. Новые классы/fields в плане — design proposals до V2-01.
Общее AI-управление shell/publishing не предлагается: отдельные typed ports ограничивают
authority model output. Media renderer изолирован; external I/O вне длинных transactions.
Fine-tuning — опция после evaluation, а не prerequisite branded editing.

Trade-off: больше явных lifecycle contracts, зато различимы draft/approval/consent/dispatch/
published и сохраняется контроль над cost/tenant access. Backend-first остаётся, но
финальный platform audit с требуемым UX нельзя объявить готовым до нового frontend.

## Security Review

Это review **предлагаемых решений**. Runtime protections новых scopes ещё не реализованы.

| Threat | Protection в плане | Verification |
| --- | --- | --- |
| Document prompt injection | HTML/JSON извлечены как data, JS не исполнялся; требования берутся из user message | Inspection extraction; никаких команд исходного brief не запускалось |
| Unauthorized publication | Exact package approval + separate consent, account/time/platform binding, rechecks | Запланированы V2-20/23/34 negatives; не выполнены сейчас |
| OAuth account takeover/tenant leak | One-time state, redirect allowlist, PKCE where supported, encrypted server tokens | V2-03 tests state replay/foreign binding/revoke |
| Decoder/command injection/DoS | Quarantine, format/decoded-resource caps, isolated renderer, typed EditPlan/no shell | V2-12–14 malicious media/sandbox tests |
| Overspend/duplicates | Reservation before dispatch, measured settlement, unknown outcome reconciliation | V2-04 parallel/crash/unknown billing tests |
| Fake market facts | Captured snapshots/source/window/coverage, hypotheses separate | V2-07–11 false citation/sparse/stale tests |
| Cross-client training leakage | Explicit training consent, tenant datasets/models, holdout/rollback | V2-18/19/X01 leakage/evaluation tests |
| Historical approval corruption | New schemas/migrations, explicit upgrades, immutable old revisions | V2-01/09/17 migration/concurrency tests |

## Изменённые файлы

| Файл | Назначение |
| --- | --- |
| [AGENTS.md](../AGENTS.md) | Актуальный product scope, delivered Jobs/Playbook, backend-first/platform gate |
| [README.md](../README.md) | Новое описание и ссылки, корректировка stale implementation claim |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальный V2 маршрут, старый корпус помечен historical |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Тот же актуальный приоритет и historical boundary |
| [product-scope.md](product-scope.md) | Полное новое описание/user flow/requirements/DoD |
| [astra-medium-mvp-prompts-v2.md](astra-medium-mvp-prompts-v2.md) | Execution contract,35 заданий, milestones/dependencies/security/tests |
| [scope-v2-inventory.md](scope-v2-inventory.md) | Реализация vs новые требования; перенос00–30;48 HTML functions |
| [media-personalization-and-platforms.md](media-personalization-and-platforms.md) | Personalization/training/editing, official platform constraints и cost decisions |
| [ADR0041](adr/0041-expanded-creator-production-scope.md) | Ограниченный supersession прежних scope решений |
| [ADR index](adr/README.md) | Навигация к0041 |
| [старые промпты](astra-medium-mvp-prompts.md) | Archive banner; старые задания/results сохранены |
| [progress ledger](mvp-prompt-progress.md) | V2 NOT_STARTED и сохранённая previous qualification |
| [backend completion snapshot](backend-mvp-completion.md) | Исторический статус прежнего CSV-first remaining scope |
| [UI plan](ui-rework-plan.md) | Новый frontend через V2-30–34 после backend readiness |
| [прежнее AI research](ai-adapters-discussion-2026-10-20.md) | Старый demo basket не смета всего нового scope |
| [этот отчёт](scope-v2-refresh-report.md) | Проверки, решения, ограничения и handoff |

Source HTML в Downloads не изменён и не скопирован в Git. User changes
`docs/design/ui-rework/README.md`, `frontend/next-env.d.ts`, `.agents/`, `frontend/AGENTS.md`
не включаются в наш commit. Local extraction/verification helpers лежат в ignored `var/`.

## Runtime Flow

Runtime сейчас не изменён. Предложенный будущий flow:

```text
Clerk/current tenant → assets/source review → real Identity DRAFT → human approval
  → own/market snapshots → Strategy DRAFT/edit/approval → four-week production briefs
  → generated scenes OR raw footage/edit plan → sandbox render → final package review
  → explicit platform consent → durable publish intent → remote status/reconciliation
  → metrics/market refresh → regular report/reminder → recommendation → Strategy DRAFT
```

## Тесты

Фактически выполнено:

```powershell
python var/scope-refresh-2026-10-20/verify_docs.py
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
git diff --check
git diff --exit-code -- src pom.xml .github .mvn scripts
git diff --cached --check
git diff --cached --exit-code -- src pom.xml .github .mvn scripts frontend
```

- Documentation check: **PASS**,16 Markdown files,592 существующие local links,
  balanced fences/UTF-8;36 prompt blocks V2-00–35, old00–30 + AI stage, H01–48, X01–06.
  Первый запуск остановился из-за несогласованного написания `PublishIntent` в product
  scope; термин уточнён, повторный запуск прошёл. Проверки не ослаблялись.
- Spotless: **BUILD SUCCESS**,645 Java files и1pom по formatter cache,0 изменений.
- Backend source/config/build/scripts diff пуст; whitespace diff check прошёл.
- Проверены объявленные зависимости V2: нет ссылок на будущий шаг/цикла; тело старых
  промптов сохранено. Staged manifest содержит ровно16 перечисленных Markdown files,
  без source/frontend и пользовательских UI-изменений.
- Git предупреждает об LF→CRLF при следующей checkout/index нормализации некоторых
  Markdown files; ошибок содержимого/formatting в этих предупреждениях нет.

Compilation/unit/integration/security/browser/startup tests не требуются для изменения
только Markdown и не выдаются за выполненные. План будущих обязательных suites — V2-00/29/34.

## Security Tests

В этом этапе не запускались: runtime не менялся. В плане явно заданы cross-tenant,
OAuth state/refresh/revoke, media decoder/command/path injection, approval/consent races,
cost/retry/unknown outcomes, webhook replay, source withdrawal и training isolation.
Static documentation checks подтверждают перенос требований, не работу этих защит.

## Edge Cases

При planning рассмотрены: unsupported personal account, unaudited private-only TikTok,
отсутствие market source, sparse/stale observations, no-data vs zero, unknown paid/publish
outcome, revoked consent после dispatch, DST/missed slot, modified final media после
sample approval, corrupt/huge media, silent clips, unavailable training support.
Они включены в acceptance соответствующих задач; runtime evidence pending.

## Непроверенные аспекты

- Runtime полный backend/frontend suite — не запускался в docs-only этапе.
- Новые AI/video/editing outputs, costs/quality и training — отсутствуют live experiments;
  проверить после provider/data решения в согласованном бюджете.
- Meta/TikTok account entitlement, app audit и scheduled publishing policy — no live
  account calls; direct Meta developer pages недоступны web fetch, использованы official
  Meta Postman collections. Exact permissions/insights audit остаётся V2-02/03/21/24.
- Доступный лицензированный market source и coverage — не выбран; риск external blocker.
- Production IAM/TLS/backup restore/codec sandbox/key rotation/retention — последующая
  qualification, не гарантия этой документации.

## Что мне важно понять

Новый scope значительно шире «подключить AI adapters». Backend foundation переиспользуется,
но media production и внешняя публикация имеют свои данные, ошибки, permissions и стоимость.
Post approval не равен OAuth grant или remote published result. Цена и качество оцениваются
по **принятому готовому asset**, включая неудачные/отклонённые попытки.

## Что мне стоит изучить

1. **Прослеживаемость требований (requirements traceability)**: mapping исходного пункта
   к реализации/промпту/acceptance предотвращает потерю работы при изменении ТЗ.
2. **Версионирование контрактов (schema evolution)**: новые fields не переписывают
   approved Playbook/history; upgrade явный.
3. **Надёжная доставка (outbox/reconciliation)**: durable intent переживает restart,
   но remote uncertainty требует проверки, а не слепого повторного publish.
4. **Изоляция выполнения (sandboxing)**: codec/model input не получает права API process.
5. **Персонализация и обучение (conditioning vs fine-tuning)**: references меняют input,
   training меняет weights и требует отдельной оценки/consent.

## Production considerations

Gate до real-user release: permitted sources, provider policies/cost controls, audited platform
UX, token protection, media sandbox, retention/backup recovery, observation/alerts и actual
full-suite/CI evidence. Billing/paid ads/agency/white-label не входят по умолчанию.
Исторические HTML timeline/cost estimates не используются как новые обещания.

## Git, CI и tracking

Read-only GitHub #107/#120/#121 сверены: OPEN, remote scope старый. Local docs commit после
checks; push/new CI/issue updates/closure/merge/deployment не выполняются.
Старые local frontend edits остаются рабочими и не включаются в docs commit.

## Факты, выводы и assumptions

**Факты inspection:** текущие source contracts/guards, старые reports с ограничениями,
полный извлечённый HTML, прямые требования/уточнения пользователя, official research.
**Инженерные выводы:** foundation сохраняется; нужны отдельные production/publish lifecycles;
персонализация без training — разумный первый baseline.
**Pending решения/проверки:** providers, data access, media limits/качество, personal training,
hosting/privacy/retention, новые costs и live/audit qualification.
