# Задание20 — изменённые файлы

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Align Task6.2 actual performance scope/remaining external gates. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Align Task6.2 actual performance scope/remaining external gates. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Revoke context UPDATE/DELETE, сохранить least privilege history. |
| [docs/adr/0037-performance-analytics.md](../docs/adr/0037-performance-analytics.md) | ADR formulas/window/attribution/alternatives/bounds/threat model. |
| [docs/astra-medium-mvp-prompts.md](../docs/astra-medium-mvp-prompts.md) | Execution20 contracts/report references;21 не начинается. |
| [docs/issue-sync.md](../docs/issue-sync.md) | Actual #104/#107 acceptance/local boundary, no external closure claim. |
| [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) | Current20 qualification ledger. |
| [docs/performance-api.md](../docs/performance-api.md) | API examples/formulas/coverage/correction/window/operational contract. |
| [docs/prompt20-changed-files.md](../docs/prompt20-changed-files.md) | Полный перечень файлов и назначений этого slice. |
| [docs/prompt20-performance-report.md](../docs/prompt20-performance-report.md) | Detailed qualification/security/mentoring report. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Reject missing/skipped performance reports в gate tests. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Require real successful performance PostgreSQL/migration reports, no skipped gates. |
| [src/main/java/com/cadence/application/exceptions/PerformanceUnavailableException.java](../src/main/java/com/cadence/application/exceptions/PerformanceUnavailableException.java) | Safe bounded query/capacity503 outcome. |
| [src/main/java/com/cadence/application/ports/PerformanceReader.java](../src/main/java/com/cadence/application/ports/PerformanceReader.java) | Application port для bounded normalized source projection. |
| [src/main/java/com/cadence/application/repositories/MetricAttributionRepository.java](../src/main/java/com/cadence/application/repositories/MetricAttributionRepository.java) | Latest/exact context history и retained quota queries. |
| [src/main/java/com/cadence/application/repositories/MetricObservationRepository.java](../src/main/java/com/cadence/application/repositories/MetricObservationRepository.java) | Scalar scoped target projection перед parent/entity locks. |
| [src/main/java/com/cadence/application/results/MetricAttributionDetails.java](../src/main/java/com/cadence/application/results/MetricAttributionDetails.java) | Explicit immutable human context/lineage/source DTO. |
| [src/main/java/com/cadence/application/results/PerformanceReport.java](../src/main/java/com/cadence/application/results/PerformanceReport.java) | Bounded groups/rates/coverage/source DTO без private/provider payloads. |
| [src/main/java/com/cadence/application/services/PerformanceCalculator.java](../src/main/java/com/cadence/application/services/PerformanceCalculator.java) | Exact cohorts, latest source dedup, coverage/pagination/provenance и comparison keys. |
| [src/main/java/com/cadence/application/services/PerformanceService.java](../src/main/java/com/cadence/application/services/PerformanceService.java) | Actual authorization/parent locks/capacity, bounded read orchestration и attribution history. |
| [src/main/java/com/cadence/application/usecases/PerformanceUseCase.java](../src/main/java/com/cadence/application/usecases/PerformanceUseCase.java) | OWNER/scope method-security boundary и actual caller actor. |
| [src/main/java/com/cadence/domain/MetricAttribution.java](../src/main/java/com/cadence/domain/MetricAttribution.java) | Immutable versioned human context к historical Post revision/Strategy pillar. |
| [src/main/java/com/cadence/domain/PerformanceMetrics.java](../src/main/java/com/cadence/domain/PerformanceMetrics.java) | Pure checked weighted complete-pair formulas, minimum sample и explicit issues. |
| [src/main/java/com/cadence/domain/PerformanceQuery.java](../src/main/java/com/cadence/domain/PerformanceQuery.java) | Bounded DAY/PERIOD dates/zone/dimension/page и exact UTC window policy. |
| [src/main/java/com/cadence/domain/PerformanceSample.java](../src/main/java/com/cadence/domain/PerformanceSample.java) | Bounded normalized scalar read model, без raw receipts/provider payloads. |
| [src/main/java/com/cadence/infrastructure/analytics/PerformanceReadRepository.java](../src/main/java/com/cadence/infrastructure/analytics/PerformanceReadRepository.java) | Fixed parameterized JDBC query/latest corrections/context, timeout и1001limit. |
| [src/main/java/com/cadence/presentation/ApiExceptionHandler.java](../src/main/java/com/cadence/presentation/ApiExceptionHandler.java) | Controlled performance_unavailable Problem Details. |
| [src/main/java/com/cadence/presentation/PerformanceController.java](../src/main/java/com/cadence/presentation/PerformanceController.java) | GET performance и PUT/GET attribution/history API. |
| [src/main/java/com/cadence/presentation/dto/MetricAttributionRequest.java](../src/main/java/com/cadence/presentation/dto/MetricAttributionRequest.java) | Strict request DTO с exact content/context versions, без writable audit. |
| [src/main/resources/db/migration/V53__performance_attribution.sql](../src/main/resources/db/migration/V53__performance_attribution.sql) | Additive context history, independent source/actor/lineage/quota guards и window index. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | Actual performance/attribution routes, strict request/response/time/formula contracts. |
| [src/test/java/com/cadence/application/PerformanceApiIntegrationTest.java](../src/test/java/com/cadence/application/PerformanceApiIntegrationTest.java) | H2 execution actual API/contract regression. |
| [src/test/java/com/cadence/application/PerformanceCalculatorTest.java](../src/test/java/com/cadence/application/PerformanceCalculatorTest.java) | Freshness exact boundary, Strategy pillar namespaces и over-cap fail-closed tests. |
| [src/test/java/com/cadence/domain/PerformanceMetricsTest.java](../src/test/java/com/cadence/domain/PerformanceMetricsTest.java) | Independent weighted arithmetic/null/zero/overflow/sample/timezone domain tests. |
| [src/test/java/com/cadence/infrastructure/analytics/PerformanceReadRepositoryTest.java](../src/test/java/com/cadence/infrastructure/analytics/PerformanceReadRepositoryTest.java) | Regression: фактический timeout при executeQuery после настроек Spring JDBC и короткий transaction deadline. |
| [src/test/java/com/cadence/persistence/PostgreSqlMetricImportMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlMetricImportMigrationTest.java) | Freeze previous V51→V52 scenario to exact52 target. |
| [src/test/java/com/cadence/persistence/PostgreSqlPerformanceMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPerformanceMigrationTest.java) | Populated V52→V53 legacy preservation/no fabricated context. |
| [src/test/java/com/cadence/persistence/PostgreSqlPerformanceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPerformanceTest.java) | PostgreSQL/runtime/history/concurrent replay/cap1001/actual EXPLAIN checks. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Actual migration inventory53. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Actual created/updated UTC audit inventory62. |
| [src/test/java/com/cadence/presentation/OpenApiContractAssertions.java](../src/test/java/com/cadence/presentation/OpenApiContractAssertions.java) | Validate decimal number responses for actual BigDecimal rates. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | New DTO fields/enums/date contracts aligned with published API. |
| [src/test/java/com/cadence/support/PerformanceApiContract.java](../src/test/java/com/cadence/support/PerformanceApiContract.java) | Shared H2/PG workflows, contexts/history, platform/window/security/coverage tests. |
