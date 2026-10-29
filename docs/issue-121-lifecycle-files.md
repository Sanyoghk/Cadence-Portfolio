# #121 Identity lifecycle slice — полный manifest

Baseline e514ad7. V1–V28 не изменены.

- [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) — Roadmap: второй slice completed locally, remaining #121 explicitly open.
- [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) — Roadmap: второй slice completed locally, remaining #121 explicitly open.
- [README.md](../README.md) — Актуальная доступность Identity lifecycle и report links.
- [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) — Additional status/audit column UPDATE; immutable lineage/root history preserved.
- [docs/adr/0012-brand-identity-drafts.md](../docs/adr/0012-brand-identity-drafts.md) — Accepted V29 lifecycle, actor/lock/source contracts и remaining citations/Strategy source.
- [docs/brand-identity-api.md](../docs/brand-identity-api.md) — Approval request,200/201 PATCH semantics, audit/lineage/errors/replay contracts.
- [docs/database-migrations.md](../docs/database-migrations.md) — V29 constraints/preservation/grants/rollout/recovery considerations.
- [docs/issue-121-lifecycle-files.md](../docs/issue-121-lifecycle-files.md) — Этот manifest всех изменённых файлов.
- [docs/issue-121-lifecycle-slice.md](../docs/issue-121-lifecycle-slice.md) — Полный технический отчёт, threat model, tests, limitations и mentoring.
- [docs/issue-sync.md](../docs/issue-sync.md) — Current partial #121 status и local results.
- [docs/verification.md](../docs/verification.md) — Exact new-source local gates; remote qualification explicitly pending.
- [src/main/java/com/cadence/application/results/BrandIdentityDetails.java](../src/main/java/com/cadence/application/results/BrandIdentityDetails.java) — Immutable application snapshot включает lineage и actor/time.
- [src/main/java/com/cadence/application/usecases/BrandIdentityUseCase.java](../src/main/java/com/cadence/application/usecases/BrandIdentityUseCase.java) — Separate approval current DB authorization; approved edit allocation/branch result.
- [src/main/java/com/cadence/domain/BrandIdentity.java](../src/main/java/com/cadence/domain/BrandIdentity.java) — Approval state transition, immutable source и independent draft factory.
- [src/main/java/com/cadence/presentation/BrandIdentityController.java](../src/main/java/com/cadence/presentation/BrandIdentityController.java) — New approval route/VersionRequest; approved PATCH201/Location.
- [src/main/java/com/cadence/presentation/dto/BrandIdentityResponse.java](../src/main/java/com/cadence/presentation/dto/BrandIdentityResponse.java) — Публичные lineage/audit fields без Entity/private references.
- [src/main/resources/db/migration/V29__brand_identity_lifecycle.sql](../src/main/resources/db/migration/V29__brand_identity_lifecycle.sql) — Status/audit/lineage constraints/FK/indexes и root lifecycle guard; V28 unchanged.
- [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) — Identity approval route и extended Identity response/PATCH201 contract.
- [src/test/java/com/cadence/domain/BrandIdentityTest.java](../src/test/java/com/cadence/domain/BrandIdentityTest.java) — Approval/invalid actor/time/edit/replay/branch invariants и copy isolation.
- [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java) — Actual non-owner runtime approve/branch и approved source/audit/lineage negatives.
- [src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityApiTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityApiTest.java) — SQL tampering, edit/approve/branches/archive/account revocation и cap branch tests.
- [src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityMigrationTest.java) — Populated V28→latest preserves draft content/version/token/NULL audit.
- [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) — Exact migration1–29 inventory; mapped roots unchanged18.
- [src/test/java/com/cadence/support/BrandIdentityApiContract.java](../src/test/java/com/cadence/support/BrandIdentityApiContract.java) — Persisted actor fixture, HTTP/direct bean denied/approval/lineage/fresh-content tests.
