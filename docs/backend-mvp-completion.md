# Backend MVP до нового frontend

> **Исторический snapshot до расширения ТЗ20 октября.** Актуальны
> [план V2](astra-medium-mvp-prompts-v2.md), [product scope](product-scope.md) и
> [inventory](scope-v2-inventory.md). Direct publishing теперь required; added ready video,
> raw editing, research/reminders. Evidence ниже сохраняется, прежний remaining scope заменён.

Дата: 2026-10-20. Статус: цель backend-first подтверждена; этапы и AI integration — предложение
для обсуждения, **не утверждённый план реализации**.
Исходный HEAD `2f85d43d59090e768f0bf53726af2ea8a609e5ad`, ветка `mvp`.

Уточнение пользователя: provider пока не выбран, бюджет демо £20; сначала анализ всех AI
adapters и обсуждение. Claude рассматривается для Brand/Strategy; упоминание NanoBanana
для видео требует уточнения конкретного продукта. [Исследование и рекомендации](ai-adapters-discussion-2026-10-20.md)
не активируют providers, не разрешают расходы/передачу данных и не принимают policy за пользователя.

## Что было сделано

Пользователь решил полностью переработать frontend после завершения backend MVP и явно
выбрал **работающий MVP с реальным AI**: Identity → Strategy → Content → CSV export →
manual/CSV metrics → recommendations. Billing и direct publishing остаются вне MVP.
Новая UI implementation и экраны25–27 отложены до backend acceptance.
Существующий frontend сохраняется как возможный клиент для smoke; его дизайн не определяет
backend scope и не считается новым решением о дизайне второго frontend.

В этом этапе выполнена сверка кода, contracts, отчётов и actual GitHub #107/#120/#121.
Создан план завершения с проверяемыми gates; изменены только документы. Реальные adapters,
model selection, privacy policy и новая qualification всего backend этим этапом не выполнены.

## Подтверждённое исходное состояние

| Область | Доказательство в repository | Что ещё требуется |
| --- | --- | --- |
| Stack | [pom.xml](../pom.xml): Java21, Spring Boot4.0.8 | Сохранить stack; recovery2026-10-08 описывает старый baseline |
| Workspace/Brand, tenancy | [WorkspaceController](../src/main/java/com/cadence/presentation/WorkspaceController.java), [BrandController](../src/main/java/com/cadence/presentation/BrandController.java) | Приёмка с новым live Clerk пользователем и без ручной выдачи чужих memberships |
| Identity и approved source | [supported-source acceptance](issue-121-whole-review-2026-10-15.md), [source06](issue-121-approved-strategy-slice.md) | Visual images/screenshots и real provider; #121 OPEN |
| AI Jobs/recovery | [report10](prompt10-durable-generation-report.md), [report11](prompt11-recovery-report.md) | Квалифицировать provider-specific dispatch, timeout, usage и recovery |
| Budgets/idempotency | [GenerationBudgets](../src/main/java/com/cadence/application/services/GenerationBudgets.java), [report12](prompt12-budget-report.md) | Сейчас `available()` допускает только local/test + fake + synthetic, запрещает prod. Реальные деньги/usage/price policy ещё не интегрированы |
| Brand/Strategy/Content | [Brand config](../src/main/java/com/cadence/infrastructure/analysis/BrandAnalysisConfiguration.java), [Strategy config](../src/main/java/com/cadence/infrastructure/strategy/StrategyGenerationConfiguration.java), [Content config](../src/main/java/com/cadence/infrastructure/content/ContentGenerationConfiguration.java) | Каждый switch поддерживает `disabled` и `fake`; production adapter отсутствует |
| Playbook, revisions, Calendar, CSV | [report13](prompt13-playbook-report.md), [report14](prompt14-post-revisions-report.md), [report15](prompt15-content-generation-report.md), [report16](prompt16-calendar-report.md), [report18](prompt18-csv-export-report.md) | Сохранить workflow; проверить единый сценарий с реальными generated drafts |
| Media | [DemoMediaConfiguration](../src/main/java/com/cadence/infrastructure/content/DemoMediaConfiguration.java), [ADR0034](adr/0034-private-demo-media.md) | Только фиксированные demo PNG/MP4; production отключён. Generated assets не готовы |
| Metrics/Performance | [report19](prompt19-metrics-import-report.md), [report20](prompt20-performance-report.md) | Manual/CSV входит в выбранный MVP; live social sync остаётся отдельным scope |
| Recommendations | [RuleRecommendationEngine](../src/main/java/com/cadence/infrastructure/recommendations/RuleRecommendationEngine.java), [report21](prompt21-recommendations-report.md) | `HEURISTIC`, не live AI. Product scope обещает AI recommendations; требуется adapter/qualification, не переименование эвристики |
| Reports | [report22](prompt22-reports-report.md) | Проверить новые provider provenance и доступ к сохранённому результату |
| Security/operations | [report28](prompt28-backend-report.md), [runbook](backend-demo-runbook.md) | Backend28 qualified locally19 октября; изменение adapters требует новых применимых gates |
| Authentication | [CadenceIdentityService](../src/main/java/com/cadence/security/CadenceIdentityService.java), [ADR0040](adr/0040-frontend-bearer-onboarding.md) | Первый resolve требует подписанный `cadence_role`; автоматическая выдача корректного claim новому пользователю в live Clerk не доказана |

Отчёты в таблице — **историческое evidence**, а не новый запуск tests. Наличие класса
подтверждает implementation boundary, но само по себе не доказывает корректность runtime.
Backend28 уже выполнен в своём scope: его не нужно реализовывать заново.

Git при начале:30 commits ahead локального `origin/mvp`; это не новый fetch remote.
Существующие изменения `docs/design/ui-rework/README.md`, `frontend/next-env.d.ts`,
untracked `.agents/` и `frontend/AGENTS.md` принадлежат прежней работе и сохраняются.
Read-only GitHub20 октября: #107/#120/#121 OPEN; current sections обновлены18 октября,
поэтому remote master не отражает весь позднейший local scope.

## Как это работает / целевая приёмка

Backend считается завершённым для этого milestone, когда новый creator может через API:

1. Войти через Clerk, получить свой account и создать Workspace/Brand.
2. Добавить поддерживаемые источники, явно подтвердить конкретный snapshot для AI.
3. Получить Identity от реального AI, отредактировать и отдельно утвердить её.
4. На основании точной APPROVED Identity получить и утвердить полный Strategy Playbook.
5. Сгенерировать четырёхнедельный Content, редактировать/regenerate отдельные drafts,
   утвердить revisions, назначить расписание и скачать CSV.
6. Импортировать реальные manual/CSV metrics, привязать их к нужным публикациям,
   получить Performance и evidence-linked recommendations.
7. Применить выбранную рекомендацию в новый Strategy DRAFT и получить private report.
8. Повторить безопасный read/retry после timeout/restart без двойной публикации и обхода бюджета.

Images/screenshots остаются обязательным незавершённым scope #121. Generated media также
нельзя объявить готовыми через demo fixtures. Минимальные требования к реальным media
(image generation, video, пользовательские assets либо только editorial prompts) нужно
явно зафиксировать; этот документ не исключает прежние обещания молча.

## Архитектурное решение

Сохранить modular monolith и существующие presentation → application → domain →
repositories/infrastructure. Внешний AI подключается к validated ports; он не получает
доступ к repositories, arbitrary URLs/tools, чужим источникам или approval authority.
Общий transport допустим, но Brand, evidence, Strategy, Content и Recommendation
сохраняют отдельные contracts.

Не переписывать backend ради нового UI: основная business orchestration уже существует.
Не размазывать provider HTTP/JSON по controllers: это связывает внешний API с authorization
и усложняет проверку отказов. Trade-off выбранного подхода — adapters и несколько явных
contracts, зато provider-specific изменения не переписывают весь пользовательский цикл.

Live adapter требует интеграции существующих budgets: фиксации денежной единицы, версии
цены, верхней оценки, реального usage и неоднозначного результата вызова. `SYNTHETIC`
не переименовывается в реальные деньги. Новые SQL invariants оформляются новой Flyway
migration; существующая история и checksums не меняются.

## Предлагаемый порядок этапов — ожидает обсуждения

Эти обозначения — milestones этого плана, не новые GitHub issues или перенумерация01–30.

| Этап | Результат | Условие приёмки |
| --- | --- | --- |
| B0 — текущий этап | Сверка готового backend и фиксация remaining scope | Документы согласованы; code evidence и непроверенное явно разделены |
| B1 — решения по AI | Provider/API/model для Identity/vision, Strategy, Content, recommendations; media scope; privacy/region/retention/deletion и cost ceilings | Подтверждённая матрица data egress/usage/limits, проверенная по актуальным official docs; secrets вне Git/chat |
| B2 — первый live generation workflow | Bounded transport, реальные budgets/usage, Brand/evidence и Strategy adapters поверх существующих Jobs | Validated DRAFT, exact approved sources/provenance; malformed/oversized/refusal/429/timeout/retry/cost tests; controlled live smoke отдельно |
| B3 — visual Identity | Direct multimodal path по ADR0027; exact bytes/admission/checksum/current scans/withdrawal | PNG/JPEG/screenshots реально consumed; positive и forbidden tests; whole #121 acceptance по evidence |
| B4 — завершение AI product loop | Content adapter; AI recommendations с deterministic calculation/evidence; согласованный real media scope | Content→approval→Calendar→CSV→manual metrics→recommendation→new Strategy DRAFT; отсутствие скрытых demo результатов |
| B5 — frontend integration contract | Self-service auth qualification, согласованный API inventory/OpenAPI, safe errors, versions, Jobs/idempotency и targeted CORS | Новый account проходит API journey; чужой tenant/role/malformed token отклонены; future UI не требует изменения domain ради существующих use cases |
| B6 — backend release candidate | Regression/security/recovery/load/backup-restore checks в выбранном окружении; runbook и actual CI | Все обязательные gates конкретного source SHA; known limitations оформлены; отдельный deployment approval |

B5 contract inspection и подготовку B6 можно делать последовательно в этом же чате,
пока B1 ожидает решения. Subagents не используются. После B6 начинается новый frontend;
frontend/cross-stack E2E выполняется после его реализации и не выдаётся за backend test.

Перед первым изменением B2 нужно изучить exact evidence/usage ports и SQL guards, определить
изменяемый вертикальный сценарий и его миграцию; таблица не разрешает включить production
одной заменой `fake` на имя provider. Missing config/credential/unsupported model = отказ.

## Security Review

Защищаем private source bytes/text, tenant data, tokens, immutable approvals и деньги на AI.
Callers: authenticated creator, атакующий authenticated пользователь, anonymous client.
Границы доверия (trust boundaries): HTTP/JWT → backend, storage → worker,
backend → AI provider, model output → domain/DB. При обходе проверки UI остаются серверные
checks; при обходе одного application path critical invariants сохраняются в PostgreSQL.

| Threat | Protection | Verification |
| --- | --- | --- |
| IDOR/BOLA, stale membership | Current OWNER/member + scoped queries + recheck перед egress/publication | Два tenants, reserved roles, revoke/archive во время вызова |
| Prompt injection/data exfiltration | Sources только data; bounded server selection, no tools/arbitrary fetch; отдельный consent | Враждебный source text/image, fabricated source IDs/citations; никакого расширения входов моделью |
| Malformed/untrusted model output | Strict DTO/schema/domain validation, response caps, DRAFT-only, human approval | Неполный JSON, лишние/чужие IDs, invalid enums/oversized text/refusal/truncation |
| Overspend/duplicate calls | Durable reservation до egress, per-account/workspace/global limits, exact attempt/lease, bounded retries | Parallel/replay, timeout после отправки, unknown usage held, restart/stale worker |
| SSRF/credential leak | Approved fixed HTTPS endpoints, TLS verification, no client URL/provider credentials in output | Redirect/host policy, safe errors, captured logs и negative transport fixtures |
| Visual bytes change/withdrawal | Exact admission/checksum/scans/lifecycle/current access; повторная проверка перед publication | Corrupted/changed image, expired scan, withdrawal/access revoke during I/O |
| Privilege escalation during signup | Signed issuer/audience/azp claims + local account/membership; no client-assigned role | Новый пользователь, missing/forged role, foreign workspace IDs, inactive existing account |
| CSV/report injection | Existing bounded rendering, escaping/formula protection, private downloads | Реальные generated strings и malicious imported values через полный pipeline |

Это threat model и стратегия проверки будущей integration, не заявление о прохождении
этих tests в текущем docs-only этапе. Historical security tests см. отчёты соответствующих scopes.

## Runtime Flow

```text
Clerk Bearer → current account/OWNER/membership → validated request
  → immutable source capture + consent/eligibility + budget reservation
  → durable Job / exact attempt + lease
  → fresh source/access check → bounded provider I/O вне DB transaction
  → schema/domain/provenance validation + actual usage accounting
  → fresh access/source/withdrawal/deadline/lease checks
  → atomic DRAFT publication + audit / PostgreSQL constraints
  → отдельная human approval → downstream approved-source workflow
```

## Тесты и Security Tests

Для каждого implementation slice: compilation, Spotless, targeted domain/unit/HTTP tests,
PostgreSQL migrations/runtime grants/concurrency, applicable security negative tests,
`.\mvnw.cmd clean verify`, mandatory reports/Python/Linux/renderer gates. Configuration/JPA/DI
требуют application startup. Dependencies/scans выполняются на фактических versions/images.
Controlled live provider checks используют заранее согласованные synthetic inputs и cost cap;
offline fixtures не доказывают live API, output quality, retention или provider billing.

Обязательные Edge Cases: missing credentials, provider outage/429/refusal/timeout/partial JSON,
unknown charge, повторный idempotency key с другим payload, concurrent generation,
withdrawal/revoke/archive между capture и completion, stale approved source,
empty/sparse metrics, timezone/DST, CSV formula payload и restart после отправки request.

Точные команды и результаты текущего planning этапа фиксируются в разделе verification ниже.
Новые runtime/security tests не требуются для неизменённого product code; они не заявляются
выполненными только потому, что перечислены как acceptance будущих этапов.

## Непроверенные аспекты и требуемые решения

- Пользователь указал £20 для демо, provider/API account пока не выбран. Период бюджета
  не уточнён: исследование консервативно считает £20 общим лимитом, не автоматическим месячным
  пополнением. Model, privacy, retention, region и разрешение на передачу данных не утверждены.
- Live Clerk self-service требует проверки нового account и server-controlled claim provisioning;
  текущий manual smoke этого не доказывает. Auth semantics этим планом не меняются.
- AI recommendation и real media requirements необходимо конкретизировать; existing heuristic
  и demo assets не подменяют эти обещания.
- Public social OAuth/live metrics sync остаются отдельными scopes #32/#33/#99; выбранный
  manual/CSV metrics путь позволяет приёмку без автоматического sync, но не закрывает эти issues.
- Полный текущий Maven suite, PostgreSQL/renderer suite, provider/live tests и remote CI
  не запускались этим docs-only этапом. Historical pass не квалифицирует будущие adapters.
- Commercial deployment дополнительно требует operational privacy/retention, IAM/TLS,
  distributed rate/cost limits, alerts, capacity, restore/rotation drills и hosting qualification.

## Что мне важно понять / что стоит изучить

1. **Порт и адаптер (ports and adapters):** use case определяет нужный результат; конкретный
   provider реализует внешнее взаимодействие. Смена API не должна менять ownership/approval.
2. **Идемпотентность (idempotency):** повтор одной операции не создаёт второй результат.
   Она не доказывает, что provider не списал деньги после network timeout.
3. **Резервирование расходов (cost reservation):** деньги ограничиваются до вызова, а не только
   подсчитываются после. Неизвестный outcome удерживает reservation до reconciliation.
4. **Проверка права публикации (publication fencing):** поздний worker не имеет права
   сохранить результат после потери lease, отзыва источника или доступа.
5. **Контракт API (API contract):** новый frontend зависит от DTO, ошибок, versions и Jobs,
   а не от внутреннего устройства JPA и provider JSON.

## Изменённые файлы этого этапа

- `docs/backend-mvp-completion.md`: текущая сверка, target, remaining scopes, acceptance и отчёт.
- `docs/ai-adapters-discussion-2026-10-20.md`: official-source research всех AI областей,
  кандидатные модели, cost calculations и ограничения; решения не утверждены.
- `DEVELOPMENT_PLAN.md` и `DEVELOPMENT_PLAN_RU.md`: единый текущий приоритет backend до нового UI.
- `docs/product-scope.md`: подтверждённая пользователем последовательность и real-AI цель.
- `docs/mvp-prompt-progress.md`: новая запись planning, без fabricated qualification.
- `docs/ui-rework-plan.md`: UI отложен; старые execution records сохранены.
- `docs/astra-medium-mvp-prompts.md`: текущий маршрут выполнения; номера заданий сохранены.

## Verification текущего этапа

Фактически выполнено:

```powershell
python var/backend-mvp-planning/verify_plan.py
python var/backend-mvp-planning/check_costs.py
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
git diff --check
git diff --exit-code -- src pom.xml .github .mvn scripts
```

- Documentation check: PASS,8 Markdown files/434 существующие local links, balanced code fences,
  совпадающий current priority в обоих DEVELOPMENT_PLAN, сохранённые historical sections.
  Скрипт сохранён в ignored `var/backend-mvp-planning/` как локальное verification evidence.
- Spotless: BUILD SUCCESS;645 Java files и pom чистые по проверке formatter/cache.
- Cost arithmetic: PASS,4 Decimal assertions; illustrative usage, без вызова provider.
- Diff whitespace check: PASS; backend source/configuration/migrations/build/scripts unchanged.
- Unit/integration/security/runtime tests: **не запускались**. Это docs-only change;
  новых заявлений об их прохождении нет. Compilation/startup/full-suite counts не установлены.

## Факты, выводы, assumptions

**Факты:** решение пользователя о real-AI backend-first; inspected config/ports/source,
read-only открытые issues и исторические reports с указанными границами.
**Инженерный вывод:** главный remaining scope — real adapters/usage/visuals/product integration
и квалификация полного сценария, а не повторная реализация Jobs/Playbook/Calendar.
**Assumptions/pending:** provider/cost/privacy/media decisions, live infrastructure и качество
AI output. Процент готовности и обещание фиксированного числа turns не устанавливаются.
Local documentation commit после docs checks сохраняет исследование, не утверждает integration.
Push, issue updates/closure, merge и deployment не выполняются.
