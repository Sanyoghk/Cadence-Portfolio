# Отчёт: корректировка плана на один Workspace на аккаунт

Дата: 2026-10-20. Исходное состояние: `mvp`, HEAD `6f2306b`.
Это **docs-only** задача. Backend/frontend implementation и существующие migrations не менялись.

## Что было сделано

В [плане V2](astra-medium-mvp-prompts-v2.md) и [описании продукта](product-scope.md) закреплено:
один Workspace на аккаунт, несколько Brands внутри него. Убраны multi-workspace сценарии
из целевого scope, а их замена включена в начало V2-01. Статус implementation — NOT_STARTED.
V2-03/29/30/34 дополнены onboarding, migration, API/browser и Brand isolation acceptance.

## Как это работает — целевой сценарий

При первом onboarding сервис создаёт единственный Workspace аккаунта. При следующих входах
использует ту же запись. Пользователь создаёт и выбирает Brands; у каждого свои Identity,
Strategy, материалы, контент и соцсети. Расходы всех Brands ограничены общим бюджетом аккаунта.
В текущем приложении это ещё не реализовано: изменены требования и последовательность работ.

## Архитектурное решение

Сохранить Workspace как контейнер и границу изоляции данных, но ограничить число связей
аккаунта с Workspace на уровне use cases и PostgreSQL. Только скрытие selector не блокирует
старые API; property=1 не заменяет изменение bootstrap membership и SQL invariants.
Полное удаление Workspace потребовало бы неоправданно широкого переноса tenant references.
Детали, alternatives, trade-offs, migration gates и affected components — в [ADR0042](adr/0042-single-workspace-per-account.md).

## Security Review

Threat: второй Workspace через параллельные запросы, JWT assignments или legacy API.
Protection: план требует atomic onboarding, authoritative account binding и DB uniqueness/FK.
Verification: inspection обнаружил текущие many-to-many memberships/лимит10; runtime negatives
запланированы в V2-01/03/29, в этой задаче не запускались.

Threat: смешивание данных Brands или публикация в соцсеть другого Brand.
Protection: account→Workspace→Brand checks для sources/Jobs/connections и Brand-aware cache.
Verification: negative acceptance добавлена в V2-01/29/30/34; publishing tests после V2-20–23.

Threat: потеря данных либо нарушение доступа при слиянии legacy Workspace.
Protection: read-only inventory, явный mapping, immutable history и backup/restore до cutover.
Verification: проверен запрет переноса в `Brand.setWorkspace`; SQL/storage/legacy DB проверки
остаются обязательным implementation gate. Автоматическая очистка/слияние не разрешены планом.

## Изменённые файлы

| Файл | Назначение |
| --- | --- |
| [AGENTS.md](../AGENTS.md) | Правило для следующих implementation задач; pending migration |
| [README.md](../README.md) | Новая модель аккаунта и честная граница текущей реализации |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальный маршрут single-workspace/Brand UI |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Та же актуальная scope section |
| [product-scope.md](product-scope.md) | User flow, invariants, Brand context, общие budgets |
| [astra-medium-mvp-prompts-v2.md](astra-medium-mvp-prompts-v2.md) | Начальные статусы, V2-00/01/03/29/30/34, migration и tests |
| [scope-v2-inventory.md](scope-v2-inventory.md) | Готовая прежняя модель отделена от необходимой переработки |
| [mvp-prompt-progress.md](mvp-prompt-progress.md) | Новый остаток со статусом NOT_STARTED |
| [ui-rework-plan.md](ui-rework-plan.md) | Старый Workspace manager помечен как заменённый Brand UI |
| [ADR index](adr/README.md) | Ссылка на решение0042 |
| [ADR0042](adr/0042-single-workspace-per-account.md) | Source evidence, архитектура, migration/threat model/acceptance |
| [Этот отчёт](single-workspace-plan-report.md) | Границы изменений, проверки и непроверенные аспекты |

## Runtime Flow — планируемый

```text
Clerk JWT → verified local account → idempotent onboarding use case
  → account lock + transaction → единственный Workspace binding → PostgreSQL constraints
  → create/select Brand → server-side account/Workspace/Brand checks
  → sources → Identity → Strategy → Content → approval → Brand social connection
```

## Проверки документации

Выполнены команды:

```powershell
python var/scope-refresh-2026-10-20/verify_docs.py
.\mvnw.cmd spotless:check
git diff --check
git diff --exit-code -- src pom.xml .github .mvn scripts
```

- Docs verifier: PASS, 18 Markdown files, 627 существующих локальных ссылок, UTF-8/fences;
  V2-00–35 и 36 prompt blocks, старые00–30 + AI stage, H01–48 и X01–06 сохранены.
  Нет forward/cyclic declared dependencies; обе актуальные DEVELOPMENT_PLAN sections совпадают,
  body архивного плана сохранён. Helper лежит в ignored `var/`, не входит в commit.
- Spotless: BUILD SUCCESS; 645 Java files и 1 POM проверены через cache, изменений не требуют.
- Git whitespace и отсутствие backend/build/config/scripts diff: PASS.
- Git сообщает обычное предупреждение LF→CRLF для части Markdown при следующей записи;
  ошибок проверки whitespace нет.

Application unit/integration/security tests: **не запускались**. Число passed/failed для них
не заявляется: docs-only проверки не доказывают новую runtime cardinality.

## Security Tests и Edge Cases — запланированы, не выполнены

- Repeat/concurrent onboarding и interrupted provisioning: тот же Workspace без дубликатов.
- Forged assignments, чужие IDs, inactive user, direct SQL: доступ/вторая связь запрещены.
- Archived Workspace: создание второго запрещено; явное account lifecycle состояние.
- 0/1/>1 legacy memberships, shared/orphan data: deterministic mapping или migration blocker.
- Несколько Brands: разные industry/context/social destination, общий account budget.
- Brand switch во время запроса и logout/login: stale response не появляется в другом context.
- Existing approved history, Jobs и storage references: сохранность после migration/restore.

## Непроверенные аспекты

Не подключались к БД и не выясняли фактическое число legacy Workspace. Не выполняли migration,
live Clerk/provider/OAuth, новые HTTP/SQL constraints или browser flows. Для их проверки нужны
implementation V2-01/03 и PostgreSQL fixtures, затем target-data preflight и полный acceptance.
Исторические qualified suites не являются доказательством нового single-workspace invariant.

## Что мне важно понять

Один Workspace упрощает продуктовый сценарий, но не отменяет authorization. Разделение по
аккаунтам защищает от чужого пользователя; разделение по Brand предотвращает неверный AI
контекст и публикацию в неправильную соцсеть даже у одного владельца. БД должна запрещать
вторую связь независимо от числа одновременных запросов и поведения frontend.

## Что мне стоит изучить

- **Cardinality** — допустимое число связей: здесь account→один Workspace→несколько Brands.
- **Idempotency** — повтор запроса не создаёт второй ресурс; применяется к onboarding.
- **Database constraint** — правило, которое БД проверяет при записи, даже в обход HTTP.
- **Data migration** — контролируемый переход существующих данных к новой модели с сохранением ссылок.

## Production considerations и Git

Перед включением нужны target-data inventory, проверенная migration/restore, согласованный
cutover старых writers, runtime-role tests, live auth и Brand cache/publishing qualification.
Providers и £20 demo AI budget не изменены. Push, remote issue edits/CI, merge и deployment
не выполнялись. Изменения сохраняются отдельным local docs commit.
Пользовательские изменения `docs/design/ui-rework/README.md`, `frontend/next-env.d.ts`,
untracked `.agents/` и `frontend/AGENTS.md` оставлены вне этого commit.

## Факты, выводы и assumptions

**Факты inspection:** ManyToMany memberships, bootstrap claims до10 IDs, create/list/archive
API, default constructor limit3 и SQL ceiling10; Brand.setWorkspace запрещает перенос.
**Инженерные выводы:** одного UI/config изменения недостаточно; требуется DB/domain/auth
эволюция и проверка legacy данных. **Assumptions:** состав реальной БД неизвестен; автоматический
onboarding — предложенное поведение нового single-workspace UX, схема binding уточняется V2-01.
