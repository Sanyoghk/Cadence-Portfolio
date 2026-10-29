# Database migrations and recovery

Flyway owns the production PostgreSQL schema. Hibernate validates mappings and must never create or update production tables.

## V56 — security audit

Новая append-only `security_audit_events`: fixed same-transaction triggers, actor/resource/
workspace/DB-principal/time/result без content или ретроспективного backfill. В миграции
сразу отзываются унаследованные default privileges runtime role, если она уже существует;
отдельный `runtime-permissions.sql` также запрещает direct table/sequence/function access.
Тем самым нет окна между Flyway и grants job. Immutable UPDATE/DELETE/TRUNCATE guard,
NO CYCLE sequence cap1000000 и controlled operator access описаны в
[contract](backend-security-gate.md#append-only-audit). V1–V55 не изменялись.
H2 create-drop не квалифицирует retained audit. Pending/actual qualification — [report28](prompt28-backend-report.md).

## Local PostgreSQL

Run `scripts/start_local_evidence.ps1 -InfrastructureOnly` to generate ignored credentials and initialize the isolated local services. Build the application and run the script without that switch for Flyway migration with `cadence_migration`, followed by Hibernate validation and normal runtime access as `cadence_runtime`. See [evidence operations](evidence-operations.md). Production uses its own PostgreSQL connection and approved provider/network configuration.

V16–V21 add quarantine generations, immutable artifacts, provider attempt/origin provenance, an admission-count index and shared provider capacity slots. V22 converts BaseEntity audit timestamps to UTC instants. V1–V21 remain unchanged. The test suite covers a populated V10 upgrade through V22, an explicit populated V21 → V22 UTC fixture, and isolated older upgrades.

The local migration role is the Docker bootstrap identity; it is not a production privilege template. In production use a separate migration role with schema DDL rights, apply migrations as a release job, then run the application with `SPRING_FLYWAY_ENABLED=false`, Hibernate `validate` and a non-owner runtime role. Apply equivalent narrow grants from `docker/postgres/runtime-permissions.sql`: immutable scan/artifact/origin rows cannot be updated; only expired unbound provider receipts need DELETE. Never provide runtime application credentials with SUPERUSER/CREATEDB/CREATEROLE or schema ownership.

## Migration rules

- Treat an applied migration as immutable; add a new versioned migration for every schema change.
- Test forward migration from an empty database and from the latest production backup before promotion.
- Keep DDL and application changes backward-compatible during rolling deployment whenever possible.
- Take and verify a PostgreSQL backup before a destructive migration.

## V25–V26 — исправления architecture/security review 90686e5

V25 сохраняет extraction metadata при переходе URL в FAILED и расширяет только TEXT
content до 20 000 символов; FILE/URL references остаются ограничены 4096. Зависящий
от source_reference trigger пересоздаётся с тем же определением внутри transaction.
V26 ограничивает membership growth на account: атомарный счётчик и trigger разрешают
не более 10 memberships, включая архивные Workspace. Существующие accounts сверх
лимита сохраняют доступ, но не могут добавлять memberships. Их нужно отдельно
рассмотреть перед production. API имеет более низкий configurable limit: 3 по умолчанию,
`CADENCE_MAX_WORKSPACES_PER_ACCOUNT` допускает 1–10. Архивация quota не освобождает.

Checksums V1–V24 не изменены. Для populated V11–V13 upgrade необходим
`LegacyScanReceiptMigrationCallback`: Spring Boot автоматически регистрирует его.
Он отключает только `evidence_scan_result_guard` непосредственно перед V14 и включает
его обратно сразу после V14 в той же PostgreSQL transaction. Exclusive table lock
не позволяет конкурентной записи обойти guard; rollback восстанавливает trigger.
Работа вне transaction отвергается. Java Flyway consumers вне Spring должны явно
добавить `.callbacks(new LegacyScanReceiptMigrationCallback())`; один каталог SQL
без callback не является поддерживаемым способом upgrade populated V11–V13.

Используйте release job с этой версией приложения и migration identity, остановленными
старыми writers и проверенным backup. Затем примените runtime grants и запустите
runtime с Flyway disabled. Schema downgrade и rollback старого binary отдельно
не квалифицированы. `PostgreSqlReviewMigrationTest` проверяет populated V13 upgrade,
откат прерванного V14 и сохранение legacy memberships сверх нового лимита.

## V22 — UTC audit timestamps

`BaseEntity.createdAt/updatedAt` и API DTO теперь используют `Instant`; 22 audit
columns на 11 entity tables — `TIMESTAMP(6) WITH TIME ZONE`. V22 интерпретирует
legacy `TIMESTAMP WITHOUT TIME ZONE` явно через `AT TIME ZONE 'UTC'`, независимо
от timezone PostgreSQL session. Domain timestamps других types не изменяются.

Владелец проекта подтвердил: существующие данные только local test/demo, production
данных нет. Это позволяет принять явную UTC интерпретацию legacy wall times.
Это не восстановление их исторической timezone: если demo запись создавалась через
`LocalDateTime.now()` в London летом, UTC интерпретация может отличаться от её
реального момента на час. Записи сохраняются; исправлять их эвристически нельзя.

Перед применением к любому иному populated environment выясните timezone
источника и согласуйте data conversion. Не запускайте этот assumed-UTC upgrade
над неизвестной реальной историей. Applied migrations не редактируются; отличная
историческая интерпретация требует отдельной reviewed migration/recovery процедуры.

`ALTER COLUMN TYPE` может переписать tables/indexes и берёт сильные locks.
Для production upgrade нужны проверенный backup, maintenance window и измеренная
duration на репрезентативном объёме. На время upgrade остановите старую application:
`LocalDateTime` runtime и `Instant` runtime нельзя смешивать при этих новых columns.
Rollback старого binary с новой timestamp mapping не квалифицирован; используйте
проверенный forward repair либо backup restore в stopped-writes режиме.

`PostgreSqlUtcMigrationTest` создаёт V21, вставляет demo wall times, применяет V22
с London session и читает результат в Los Angeles session: exact UTC instants
сохраняются; все 22 audit columns проверены. Этот suite включён в обязательный
Python CI report gate, поэтому skip не принимается как successful migration test.

## Downgrade and restore

Flyway Community applies forward migrations; it does not provide automatic undo migrations. Application rollback is therefore one of:

1. Roll back the application binary while leaving a backward-compatible schema in place.
2. Apply a reviewed forward repair migration when data remains valid.
3. For an incompatible or destructive migration, stop writes, restore the pre-migration PostgreSQL backup into a clean database, verify it, and switch the application connection to the restored database.

Never edit `flyway_schema_history` manually. Record the application version, migration version, backup identifier, restore verification, and operator for every production recovery.


## V24 — Strategy generation

Nullable provenance сохраняет legacy history без backfill. Generated drafts имеют
complete source/version/deadline provenance и bounded pillars/cadence. Deferred
constraints проверяют final collections при commit. Identity/provenance и approved
content защищены triggers. `PostgreSqlStrategyMigrationTest` проверяет populated
V23 → V24, а fresh schema — PostgreSqlPersistenceTest.

После Flyway примените `docker/postgres/runtime-permissions.sql`: runtime UPDATE
ограничен editable/status/audit columns; generation metadata INSERT-only. Перед
production upgrade нужен backup и stopped old writes: новая immutability policy
может отвергнуть операции старого runtime. Rollback binary без проверки несовместим.
В V24 historical publishing times не корректируются: production данных сейчас нет;
неизвестный ранее JVM timezone не позволяет безопасно угадать shift legacy demo data.
## V27 — Strategy REST lifecycle (#102), 2026-10-05

Добавлены nullable revised_from_id/self FK и approved_by/user FK + approved_at/paired
constraint. Новые approvals требуют active tenant OWNER и server actor/time; новые roots
начинаются DRAFT. Lineage/approved audit immutable; version allocation revised roots
проверяется под Workspace→Brand locks. Modern generated/revised/newly-approved content
проверяется deferred trigger после полного JPA flush. V24 guards approved collections остаются.
Старые approved rows сохраняют NULL audit: никаких выдуманных actor/time при backfill.
V1–V26 checksums не меняются; populated V23→V27 сохранение проверяется integration test.
Runtime UPDATE дополнен **только** approved_by/approved_at; revised_from_id INSERT-only,
DELETE strategies запрещён runtime grant. Применить latest migrations и затем обновлённый
docker/postgres/runtime-permissions.sql до runtime activation. Hibernate ddl-auto=validate.
Ранее запущенный runtime со старым permissions script не сможет approval до обновления grants.
Миграция требует ALTER TABLE lock; длительность и rollout на production-size dataset,
backup/PITR restore и recovery требуют отдельной staging qualification. Down-migration
удалила бы audit/lineage; использовать controlled roll-forward, не удаление истории.

## V28 — Manual Brand Identity drafts (#121 slice 1)

Создаёт brand_identities и три owned collections; legacy Brand.signature/history не меняет.
V1–V27 checksums сохранены. Identity metadata/status immutable, только DRAFT допустим.
Allocation1–1000 под parent locks, RGB/text bounds, ownership и deferred final collection
validation защищены БД. Populated V27→V28 проверяется PostgreSqlBrandIdentityMigrationTest.
После Flyway применить latest runtime-permissions.sql до активации runtime. Hibernate validate.
Root DELETE/status/version UPDATE не разрешены runtime. Rollback, удаляющий новые drafts,
теряет данные; использовать reviewed roll-forward. Backup/PITR, lock duration и upgrade
production-size dataset требуют staging qualification. Approval требует новой migration,
а не ручного отключения constraints V28.

## V29 — Brand Identity approval и revision lineage (#121 slice2)

V28 drafts сохраняют content/version/lockVersion и NULL audit/lineage, без backfill.
Status допускает DRAFT/APPROVED/LOCKED; новые approved roots запрещены. Approved audit
обязателен, связан с actual active tenant OWNER в момент approval; lineage связывает same-Brand
approved/locked source. Approved content/audit, revision metadata и lineage immutable.
V28 collection guards/deferred constraints сохраняются. Parent/account-first locking обеспечивается
application; DB checks не заменяют HTTP authentication или server actor selection.

V1–V28 checksums сохранены. После Flyway применить latest runtime-permissions.sql: UPDATE
status/approved_by/approved_at добавлен к editable columns; revised_from_id INSERT-only.
Runtime/root DELETE запрещён. Staging backup/PITR и recovery/rollout qualification обязательны;
controlled roll-forward предпочтителен downgrade с потерей audit/lineage. Старый V28 runtime
может редактировать DRAFT, но попытка изменить approved content будет отвергнута; rolling
upgrade/feature activation необходимо согласовать. Migration ALTER TABLE locks/load duration
и production clock synchronization не квалифицированы local tests.
