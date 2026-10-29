# ADR0042 — один Workspace на аккаунт, несколько Brands

Дата: 2026-10-20. **Product requirement принято прямым запросом пользователя.**
Статус: изменение плана; implementation/migration **NOT_STARTED**.
Основание: «один workspace на аккаунт, да можно несколько брендов но workspace один».
[Product scope](../product-scope.md) · [V2-01/03/29/30/34](../astra-medium-mvp-prompts-v2.md).

## Контекст: что есть в коде

- [CadenceUser](../../src/main/java/com/cadence/domain/CadenceUser.java) хранит `Set<Workspace>`
  через `@ManyToMany`/`workspace_memberships`; `grantAccess` добавляет membership.
- [CadenceIdentityService](../../src/main/java/com/cadence/security/CadenceIdentityService.java)
  при первом provisioning принимает `cadence_workspace_ids`, до 10 IDs; затем авторитетна БД.
- [WorkspaceManagementUseCase](../../src/main/java/com/cadence/application/usecases/WorkspaceManagementUseCase.java)
  создаёт Workspace под account lock; default `max-per-account` в constructor — 3,
  допустимый диапазон 1–10, archived memberships также учитываются.
- [V26](../../src/main/resources/db/migration/V26__account_workspace_budget.sql) ограничивает
  число memberships на уровне PostgreSQL потолком 10 и защищает counter.
- [WorkspaceController](../../src/main/java/com/cadence/presentation/WorkspaceController.java)
  содержит plural list/create/update/archive API. [Brand](../../src/main/java/com/cadence/domain/Brand.java)
  принадлежит Workspace; `setWorkspace` запрещает перенос в другой Workspace.

Это inspection исходников, не проверка конкретной deployed DB/configuration. Состав и
количество legacy Workspace не установлены. Нельзя считать все данные disposable fixtures.

## Решение и invariants

Сохраняем `CadenceUser → Workspace → Brand` как границы authorization и storage.
Меняем количество связей (cardinality): аккаунт имеет не более одного Workspace во всех
состояниях, после успешного onboarding — ровно один. До завершения допускается ноль,
внутри Workspace — ноль или несколько Brands. Новый аккаунт получает собственный Workspace;
присоединение к чужому Workspace, workspace invitations и переключение tenants не входят в MVP.
Зарезервированные EDITOR/VIEWER не получают новых прав.

Workspace создаётся автоматически server-side при onboarding. Повторный или параллельный
запрос возвращает тот же Workspace; ошибка не оставляет частично привязанные данные.
Authentication определяет аккаунт, актуальная БД определяет его единственный Workspace.
Клиент/JWT bootstrap не выбирает произвольный tenant и не назначает его владельца.

V2-01 выбирает минимальную подходящую схему authoritative binding после inventory:
явный FK либо отдельная связь с `UNIQUE(account_id)` (для нынешнего membership — `user_id`).
Обязательны FK, запрет второго binding, согласованность membership/authorization и отсутствие
параллельного устаревшего источника прав. Один `CHECK` на старом счётчике или только property=1
не доказывает invariant для всех путей записи. Существующие guards заменяются только новой
Flyway migration; runtime role и прямой SQL проверяются на PostgreSQL.

Обычный UI больше не содержит Workspace list/switcher/manager/create-extra/archive-and-recreate.
Настройки единственного Workspace остаются рядом с настройками аккаунта; Brand picker и
управление Brands сохраняются. Архивирование Workspace не разрешает создание второго;
archived/inactive account получает явное состояние, восстановление/удаление — отдельный
account lifecycle без скрытой смены tenant. Архивирование Brand не затрагивает остальные Brands.

Новый current-workspace API выводит tenant из authenticated account. Точный путь фиксируется
в OpenAPI в V2-01; временные plural endpoints обязаны соблюдать тот же invariant и иметь
описанное прекращение поддержки (deprecation). Старый API не может обходить новое правило.

Brand хранит собственные источники, отраслевой контекст, Identity/Playbook, Content/calendar,
social connections, reports и personalization. Workspace industry нельзя автоматически считать
отраслью всех Brands. При переходе сохраняем происхождение legacy metadata; не создаём новые
«подтверждённые» выводы и не меняем approved snapshots. Account budgets общие, usage привязан
к Brand; создание Brand не сбрасывает лимит расходов. Resource limits Brands остаются bounded.

## Миграция существующих данных

1. Read-only preflight: аккаунты с 0/1/>1 memberships, shared Workspace, inactive/archived
   связи и Workspace без memberships; inventory Brands и зависимых данных без вывода secrets.
2. Для однозначных связей подготовить deterministic mapping с сохранением IDs. Для неоднозначных
   — отчёт с вариантами и явное решение владельца данных; не выбирать первый tenant и не
   удалять/объединять остальные автоматически. Если реальных данных нет, доказать это проверкой.
3. Любой перенос Brands между Workspace требует отдельного reviewed migration plan: проверить
   Java/SQL immutability guards, foreign keys, storage paths, snapshots/approvals, jobs/leases,
   reports, audit, budgets и social consent. Перенос не делает consent действующим для нового
   destination. Не обходить tenant guards обычным runtime UPDATE.
4. Перед cutover подготовить backup/restore rehearsal и оградить создание/переназначение
   Workspace от concurrent writes; активные jobs/publishing при затронутом mapping должны
   завершиться либо быть явно приостановлены. Ввести binding/constraints и обновлённые writers
   согласованно, затем проверить counts/references/access. Existing Flyway checksums неизменны.
5. Проверить empty/legacy PostgreSQL fixtures и rollback/recovery, затем qualification на
   целевом окружении. При unresolved conflicts остановить migration без потери данных;
   независимые V2 design задачи можно продолжать, single-workspace не объявлять DONE.

## Threat model и verification plan

Защищаем account access, assets/history, social tokens и budgets. Callers — новый/существующий
OWNER, недоверенный клиент, worker и migration identity. Границы доверия (trust boundaries):
JWT→local identity, HTTP→use case, application→DB, worker→provider, browser cache→Brand view.
Следующие проверки **запланированы, в этом docs-only этапе не выполнялись**.

| Threat | Protection | Verification |
| --- | --- | --- |
| Два Workspace через гонку, legacy API или прямую запись | Account lock, atomic transaction, authoritative binding, DB uniqueness/FK, повторяемое onboarding | Concurrent requests, direct SQL under runtime role, interrupted/retried provisioning |
| Чужой tenant через JWT claims/path/body | Account из verified identity, актуальная DB binding, scoped query и use-case authorization | Forged assignment, foreign ID, inactive user, stale membership; unauthorized requests |
| Смешивание Brands при generation/publish/cache | Проверка Brand→Workspace и принадлежности всех source IDs/connection/revision; cache keys и cancellation | Brands A/B одного аккаунта, чужие accounts, delayed response после переключения Brand |
| Обход расходов через archive/new Workspace/new Brand | Binding сохраняется, общие account reservations/counters, bounded Brand creation | Archive/recreate rejection, concurrent budget reservation для двух Brands |
| Потеря данных/чужой доступ при migration | Preflight mapping, reviewed cutover, backup/restore, immutable IDs/history где возможно | Legacy conflict fixtures, counts/FK checks, restored DB и negative authorization |

Если application check обойдён, ограничение БД должно отклонить вторую связь; если клиент
скрыл/подменил Brand, backend повторно проверяет scope. Новый UI не является защитой backend.

## Alternatives, порядок и последствия

Только скрыть selector — оставляет создание через API. Только `max-per-account=1` — оставляет
many-to-many provisioning и потолок 10 в SQL. Полностью удалить Workspace и привязать все данные
к account — слишком широкий перенос storage/tenant keys и history. Выбран путь сохранения
Workspace как контейнера при замене cardinality и UX; это меньше меняет существующие contracts.

V2-01: migration/enforcement/onboarding и новые contracts; V2-03: live auth/Brand social links;
V2-29: backend acceptance; V2-30: новый Brand UI; V2-34: browser/live regression. Номера старых
промптов не меняются. [ADR0002](0002-clerk-identity-and-memberships.md) уточняется в части
множественных memberships/bootstrap claims; Clerk, локальная authorization и OWNER-only
сохраняются. Multi-workspace UX из [ADR0040](0040-frontend-bearer-onboarding.md) и старых планов
заменён этим решением. Providers, £20 demo budget и backend-first порядок не меняются.
