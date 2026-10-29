# #121 manual Identity draft slice — manifest

Каждый изменённый файл этого slice; V1–V27 не изменены.

- [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) — Текущий slice и remaining roadmap.
- [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) — Текущий slice и remaining roadmap.
- [README.md](../README.md) — Ссылка на новый Identity API/ADR.
- [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) — Минимальные editable column grants; root deletion/metadata update запрещены.
- [docs/adr/0012-brand-identity-drafts.md](../docs/adr/0012-brand-identity-drafts.md) — ADR реализованного slice и отдельных последующих approval/citation/source contracts.
- [docs/adr/README.md](../docs/adr/README.md) — ADR index: новая запись0012.
- [docs/brand-identity-api.md](../docs/brand-identity-api.md) — Endpoint/content/errors/pagination/expectedVersion contract.
- [docs/database-migrations.md](../docs/database-migrations.md) — V28 rollout/grants/data preservation/recovery constraints.
- [docs/issue-121-draft-files.md](../docs/issue-121-draft-files.md) — Этот полный manifest.
- [docs/issue-121-draft-slice.md](../docs/issue-121-draft-slice.md) — Полный технический отчёт, threat review, executed results и ограничения.
- [docs/issue-sync.md](../docs/issue-sync.md) — Текущий #121 IN PROGRESS и следующий scope без premature completion.
- [docs/verification.md](../docs/verification.md) — Новые mandatory gates и граница local/remote qualification.
- [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) — Gate regression tests включают обе новые обязательные suites.
- [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) — Mandatory new PostgreSQL suites без skips/missing/failed reports.
- [src/main/java/com/cadence/application/repositories/BrandIdentityRepository.java](../src/main/java/com/cadence/application/repositories/BrandIdentityRepository.java) — Scoped projections/read queries и root pessimistic lock.
- [src/main/java/com/cadence/application/results/BrandIdentityDetails.java](../src/main/java/com/cadence/application/results/BrandIdentityDetails.java) — Immutable application snapshot inside transaction.
- [src/main/java/com/cadence/application/usecases/BrandIdentityUseCase.java](../src/main/java/com/cadence/application/usecases/BrandIdentityUseCase.java) — OWNER/scope guards, parent locks, allocation/cap, expectedVersion, pagination.
- [src/main/java/com/cadence/domain/BrandIdentity.java](../src/main/java/com/cadence/domain/BrandIdentity.java) — JPA revision root, immutable metadata, owned ordered collections, draft replacement.
- [src/main/java/com/cadence/domain/BrandIdentityContent.java](../src/main/java/com/cadence/domain/BrandIdentityContent.java) — Validated immutable content, defensive copies, RGB/text/list bounds.
- [src/main/java/com/cadence/presentation/BrandIdentityController.java](../src/main/java/com/cadence/presentation/BrandIdentityController.java) — Четыре scoped REST operations, dedicated DTO/Location/PageResponse.
- [src/main/java/com/cadence/presentation/dto/BrandIdentityResponse.java](../src/main/java/com/cadence/presentation/dto/BrandIdentityResponse.java) — Публичный immutable response без Entity/private state.
- [src/main/java/com/cadence/presentation/dto/CreateBrandIdentityRequest.java](../src/main/java/com/cadence/presentation/dto/CreateBrandIdentityRequest.java) — Allowlisted manual content body с Bean Validation.
- [src/main/java/com/cadence/presentation/dto/IdentityContentRequest.java](../src/main/java/com/cadence/presentation/dto/IdentityContentRequest.java) — Nested palette/lists/caption request validation и domain mapping.
- [src/main/java/com/cadence/presentation/dto/UpdateBrandIdentityRequest.java](../src/main/java/com/cadence/presentation/dto/UpdateBrandIdentityRequest.java) — Required expectedVersion и full replacement content.
- [src/main/resources/db/migration/V28__brand_identity_drafts.sql](../src/main/resources/db/migration/V28__brand_identity_drafts.sql) — Новая schema, FK/checks, metadata/parent/collection guards, deferred validation.
- [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) — Новые Identity routes и request/response schemas; старый contract семантически сохранён.
- [src/test/java/com/cadence/domain/BrandIdentityTest.java](../src/test/java/com/cadence/domain/BrandIdentityTest.java) — Domain normalization/immutability/invalid content и version boundaries.
- [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java) — Actual restricted runtime role: editable collections разрешены, metadata/delete запрещены.
- [src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityApiTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityApiTest.java) — PostgreSQL HTTP contract, raw SQL/cap/allocation/concurrency/archive negatives.
- [src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityMigrationTest.java) — Populated V27→latest сохраняет legacy signature без fake Identity backfill.
- [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) — Точный migration1–28 и mapped roots18 inventory.
- [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) — Точный UTC column inventory26 и явные два timestamps Identity; legacy conversion сохраняется.
- [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) — DTO field/schema contract для новых operations.
- [src/test/java/com/cadence/security/BrandIdentityApiIntegrationTest.java](../src/test/java/com/cadence/security/BrandIdentityApiIntegrationTest.java) — Запуск общего HTTP/security contract на H2.
- [src/test/java/com/cadence/support/BrandIdentityApiContract.java](../src/test/java/com/cadence/support/BrandIdentityApiContract.java) — Общий HTTP success/denial/validation/mass assignment/stale/archive contract.
