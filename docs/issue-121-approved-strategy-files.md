# Задание06 — manifest изменений

Полный список файлов local commit; runtime grants не расширены, V1–V41 не менялись.

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальный baseline06 и supported scope вместо устаревшей точки03. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронная русская точка продолжения06 и remaining blockers. |
| [docs/adr/0026-approved-identity-strategy-source.md](adr/0026-approved-identity-strategy-source.md) | Selection/evidence/lineage/race/history policy и alternatives до implementation. |
| [docs/issue-121-approved-strategy-files.md](issue-121-approved-strategy-files.md) | Полный manifest изменённых файлов. |
| [docs/issue-121-approved-strategy-slice.md](issue-121-approved-strategy-slice.md) | Подробный отчёт, security review и qualification evidence. |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Actual acceptance boundary06, results и remaining scopes. |
| [docs/strategy-api.md](strategy-api.md) | Latest APPROVED contract, failure semantics и source receipt response. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Обязательность нового populated Strategy migration report в gate tests. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Новый обязательный PostgreSQL migration suite без skips. |
| [src/main/java/com/cadence/application/results/StrategyDetails.java](../src/main/java/com/cadence/application/results/StrategyDetails.java) | Immutable read DTO generation.identitySource; nullable legacy provenance. |
| [src/main/java/com/cadence/application/services/StrategyBuildTransactions.java](../src/main/java/com/cadence/application/services/StrategyBuildTransactions.java) | Capture latest approved source, actual actor/membership, preflight/completion physical checks и atomic publication. |
| [src/main/java/com/cadence/application/usecases/BuildStrategyUseCase.java](../src/main/java/com/cadence/application/usecases/BuildStrategyUseCase.java) | Pre-egress validation и capacity2 с освобождением slots при любом исходе. |
| [src/main/java/com/cadence/domain/ApprovedIdentitySource.java](../src/main/java/com/cadence/domain/ApprovedIdentitySource.java) | Validated immutable typed source receipt. |
| [src/main/java/com/cadence/domain/StrategyGenerationProvenance.java](../src/main/java/com/cadence/domain/StrategyGenerationProvenance.java) | Additive immutable Identity/approval/analysis/requestedBy fields. |
| [src/main/resources/db/migration/V42__approved_identity_strategy_source.sql](../src/main/resources/db/migration/V42__approved_identity_strategy_source.sql) | FK/all-or-none audit, immutable source guard, SQL currentness/actor/checksum/evidence checks; no backfill. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | Nullable typed source receipt и documented create semantics. |
| [src/test/java/com/cadence/application/BuildStrategyUseCaseTest.java](../src/test/java/com/cadence/application/BuildStrategyUseCaseTest.java) | New captured source fixture и enforced preflight-before-model order. |
| [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java) | Actual restricted runtime generation from approved Identity и source-field UPDATE denial. |
| [src/test/java/com/cadence/persistence/PostgreSqlApprovedStrategyMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlApprovedStrategyMigrationTest.java) | Populated V41 generated history preserved; no new legacy generation after V42. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Strict migration inventory V42. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java) | Real approved fixtures; exact source/audit, source replacement/revocation/deadline/SQL forgery и concurrent Account lock order. |
| [src/test/java/com/cadence/security/StrategyApiFailClosedTest.java](../src/test/java/com/cadence/security/StrategyApiFailClosedTest.java) | Actual approved fixture reaches disabled provider and preserves expected503. |
| [src/test/java/com/cadence/support/IdentityEvidenceApiContract.java](../src/test/java/com/cadence/support/IdentityEvidenceApiContract.java) | Inherited approved TEXT generation и withdrawal before/during model на H2/PostgreSQL. |
| [src/test/java/com/cadence/support/StrategyApiContract.java](../src/test/java/com/cadence/support/StrategyApiContract.java) | Shared HTTP lifecycle fixtures получают настоящий Identity approval. |
| [src/test/java/com/cadence/support/StrategyFixtures.java](../src/test/java/com/cadence/support/StrategyFixtures.java) | Bounded Identity content fixture. |
| [src/test/java/com/cadence/support/UrlAnalysisApiContract.java](../src/test/java/com/cadence/support/UrlAnalysisApiContract.java) | Mixed URL/PDF/social Strategy positive и scan/expiry/binding/physical/withdrawal negatives на H2/PostgreSQL. |
