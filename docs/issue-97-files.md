# Изменённые файлы — issue #97

Baseline: `e92f1ef`. Каждый изменённый файл и назначение.

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальный delivered scope и remaining API/provider work. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Те же границы в русском плане. |
| [README.md](../README.md) | Описание generation application flow и ссылки на contracts. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | INSERT-only provenance, column-level UPDATE, guarded draft collection DELETE. |
| [docs/adr/0010-strategy-generation-and-immutable-history.md](adr/0010-strategy-generation-and-immutable-history.md) | Архитектурное решение, alternatives и последствия. |
| [docs/adr/README.md](adr/README.md) | Индекс ADR-0010. |
| [docs/brand-analysis.md](brand-analysis.md) | Отдельная GitHub задача #120 для production adapter. |
| [docs/database-migrations.md](database-migrations.md) | V24, populated upgrade, privileges и recovery limits. |
| [docs/issue-97-completion.md](issue-97-completion.md) | Полный итоговый отчёт и реальные qualification facts. |
| [docs/issue-97-files.md](issue-97-files.md) | Полный manifest всех изменённых файлов. |
| [docs/issue-sync.md](issue-sync.md) | Актуальная синхронизация GitHub/master scope. |
| [docs/strategy-generation.md](strategy-generation.md) | Contracts, lifecycle, security threat model, configuration и remaining scope. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Missing/skipped Strategy suites не допускают ложного successful gate. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Обязательные Strategy build/migration PostgreSQL reports. |
| [src/main/java/com/cadence/application/exceptions/StrategyGenerationUnavailableException.java](../src/main/java/com/cadence/application/exceptions/StrategyGenerationUnavailableException.java) | Фиксированная безопасная ошибка без raw provider cause. |
| [src/main/java/com/cadence/application/ports/StrategyGenerationCommand.java](../src/main/java/com/cadence/application/ports/StrategyGenerationCommand.java) | Immutable source snapshot, version/deadline и bounded signature. |
| [src/main/java/com/cadence/application/ports/StrategyGenerationResult.java](../src/main/java/com/cadence/application/ports/StrategyGenerationResult.java) | Bounded validated pillars/cadence/provenance без approval status. |
| [src/main/java/com/cadence/application/ports/StrategyGenerationService.java](../src/main/java/com/cadence/application/ports/StrategyGenerationService.java) | Provider-neutral port без JPA Entity. |
| [src/main/java/com/cadence/application/results/BuiltStrategy.java](../src/main/java/com/cadence/application/results/BuiltStrategy.java) | Immutable application result DTO. |
| [src/main/java/com/cadence/application/services/StrategyBuildTransactions.java](../src/main/java/com/cadence/application/services/StrategyBuildTransactions.java) | Scoped Workspace→Brand locks, source capture и fenced next-version publication. |
| [src/main/java/com/cadence/application/usecases/BuildStrategyUseCase.java](../src/main/java/com/cadence/application/usecases/BuildStrategyUseCase.java) | Method authorization, separate transactions и safe provider failure. |
| [src/main/java/com/cadence/domain/PublishingCadence.java](../src/main/java/com/cadence/domain/PublishingCadence.java) | LOCAL_TIME JDBC mapping без timezone shift publishing wall times. |
| [src/main/java/com/cadence/domain/Strategy.java](../src/main/java/com/cadence/domain/Strategy.java) | Defensive nested copies, immutable identity и attach-once provenance. |
| [src/main/java/com/cadence/domain/StrategyGenerationProvenance.java](../src/main/java/com/cadence/domain/StrategyGenerationProvenance.java) | Embedded immutable source/provider/deadline metadata. |
| [src/main/java/com/cadence/infrastructure/strategy/DeterministicStrategyGenerationAdapter.java](../src/main/java/com/cadence/infrastructure/strategy/DeterministicStrategyGenerationAdapter.java) | Local/test deterministic sample draft, demo/review warnings. |
| [src/main/java/com/cadence/infrastructure/strategy/StrategyGenerationConfiguration.java](../src/main/java/com/cadence/infrastructure/strategy/StrategyGenerationConfiguration.java) | Production fail-closed и strict fake profile isolation. |
| [src/main/resources/application-local.properties](../src/main/resources/application-local.properties) | Локальный explicit fake selector. |
| [src/main/resources/db/migration/V24__strategy_generation_provenance.sql](../src/main/resources/db/migration/V24__strategy_generation_provenance.sql) | Complete metadata constraints, immutable history/state guards и deferred collection checks. |
| [src/test/java/com/cadence/application/BuildStrategyUseCaseTest.java](../src/test/java/com/cadence/application/BuildStrategyUseCaseTest.java) | Provider ordering/failure/null/invalid-source tests. |
| [src/test/java/com/cadence/application/StrategyGenerationContractTest.java](../src/test/java/com/cadence/application/StrategyGenerationContractTest.java) | Malformed/oversized/mutable output и source contract negative tests. |
| [src/test/java/com/cadence/domain/StrategyTest.java](../src/test/java/com/cadence/domain/StrategyTest.java) | State transitions и approved nested alias regression. |
| [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java) | Actual runtime role: generate two drafts, approve, reject forbidden metadata/content changes. |
| [src/test/java/com/cadence/infrastructure/strategy/StrategyGenerationConfigurationTest.java](../src/test/java/com/cadence/infrastructure/strategy/StrategyGenerationConfigurationTest.java) | Local/test/prod/mixed/unknown/disabled configuration tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | V24 exact migration inventory и schema validation. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java) | Authorization, archive/source races, deadlines, allocation, immutability и DB content constraints. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyMigrationTest.java) | Populated V23→V24 preserving drafts/approved rows без fabricated provenance. |
| [src/test/java/com/cadence/support/StrategyFixtures.java](../src/test/java/com/cadence/support/StrategyFixtures.java) | Bounded deterministic shared fixtures, no external provider credentials. |
| [src/test/resources/application-test.properties](../src/test/resources/application-test.properties) | Explicit fake test selector. |
