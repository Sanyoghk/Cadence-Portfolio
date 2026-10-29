# Задание19 — изменённые файлы

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Align Task6.1 manual/CSV actual scope and broader #99 remaining. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Align Task6.1 manual/CSV actual scope and broader #99 remaining. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Least-privilege immutable metrics receipts/observations/results; only publication columns writable. |
| [docs/adr/0036-manual-csv-metric-import.md](../docs/adr/0036-manual-csv-metric-import.md) | ADR semantics/alternatives/threat model/bounds/recovery/provider boundary. |
| [docs/astra-medium-mvp-prompts.md](../docs/astra-medium-mvp-prompts.md) | Execution19 links и remaining scope without starting20. |
| [docs/issue-sync.md](../docs/issue-sync.md) | Honest local #99/#107 mapping, no external closure claim. |
| [docs/metrics-import-api.md](../docs/metrics-import-api.md) | API examples, correction/read semantics и operational recovery. |
| [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) | Current19 qualification ledger. |
| [docs/prompt19-changed-files.md](../docs/prompt19-changed-files.md) | Полный перечень файлов этого slice и их назначений. |
| [docs/prompt19-metrics-import-report.md](../docs/prompt19-metrics-import-report.md) | Detailed qualification/security/student-oriented report. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Guard tests require new reports and reject false green. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Require successful new metrics PostgreSQL/migration reports, no skips. |
| [src/main/java/com/cadence/application/exceptions/MetricImportUnavailableException.java](../src/main/java/com/cadence/application/exceptions/MetricImportUnavailableException.java) | Controlled parser-capacity failure. |
| [src/main/java/com/cadence/application/repositories/DurableJobRepository.java](../src/main/java/com/cadence/application/repositories/DurableJobRepository.java) | Exclude dedicated metrics type из generic queue. |
| [src/main/java/com/cadence/application/repositories/MetricImportRepository.java](../src/main/java/com/cadence/application/repositories/MetricImportRepository.java) | Raw receipt idempotency/scope/locks/candidate projection queries. |
| [src/main/java/com/cadence/application/repositories/MetricObservationRepository.java](../src/main/java/com/cadence/application/repositories/MetricObservationRepository.java) | Natural-key latest revision и bounded history queries. |
| [src/main/java/com/cadence/application/results/MetricImportDetails.java](../src/main/java/com/cadence/application/results/MetricImportDetails.java) | Safe typed Job/provenance/status DTO, без raw bytes. |
| [src/main/java/com/cadence/application/results/MetricObservationDetails.java](../src/main/java/com/cadence/application/results/MetricObservationDetails.java) | Explicit normalized observation/audit/source/window DTO. |
| [src/main/java/com/cadence/application/services/MetricImportParser.java](../src/main/java/com/cadence/application/services/MetricImportParser.java) | Strict bounded UTF-8 JSON/CSV parser вне transactions, capacity guard. |
| [src/main/java/com/cadence/application/services/MetricImportService.java](../src/main/java/com/cadence/application/services/MetricImportService.java) | Admission/replay/scoped query, claim/recovery/atomic fenced batch publication. |
| [src/main/java/com/cadence/application/usecases/ImportMetricsUseCase.java](../src/main/java/com/cadence/application/usecases/ImportMetricsUseCase.java) | OWNER/scoped method-security boundary и actual principal actor. |
| [src/main/java/com/cadence/domain/DurableJob.java](../src/main/java/com/cadence/domain/DurableJob.java) | Guarded binding нового METRICS_IMPORT payload. |
| [src/main/java/com/cadence/domain/MetricImport.java](../src/main/java/com/cadence/domain/MetricImport.java) | Raw human receipt, bounded private bytes/provenance и first publication. |
| [src/main/java/com/cadence/domain/MetricObservation.java](../src/main/java/com/cadence/domain/MetricObservation.java) | Normalized nullable counters и immutable correction lineage. |
| [src/main/java/com/cadence/domain/MetricRow.java](../src/main/java/com/cadence/domain/MetricRow.java) | Immutable interval counts/value checksum и domain bounds. |
| [src/main/java/com/cadence/infrastructure/content/MetricImportScheduling.java](../src/main/java/com/cadence/infrastructure/content/MetricImportScheduling.java) | Explicit worker activation, bounded poll/backoff, safe logging. |
| [src/main/java/com/cadence/presentation/ApiExceptionHandler.java](../src/main/java/com/cadence/presentation/ApiExceptionHandler.java) | Safe503 parser-capacity Problem Details. |
| [src/main/java/com/cadence/presentation/JobController.java](../src/main/java/com/cadence/presentation/JobController.java) | Scoped typed METRICS_IMPORT Job status dispatch. |
| [src/main/java/com/cadence/presentation/MetricImportController.java](../src/main/java/com/cadence/presentation/MetricImportController.java) | Dedicated manual/CSV202, import status и bounded Post metrics API. |
| [src/main/resources/application.properties](../src/main/resources/application.properties) | Defaultfalse explicit metrics worker activation. |
| [src/main/resources/db/migration/V52__metric_imports.sql](../src/main/resources/db/migration/V52__metric_imports.sql) | Additive PostgreSQL tables/FKs/bounds/checksums/lineage/history/quota/publication guards. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | Metrics routes/request/result schemas; existing schemas preserved, typed Job union extended. |
| [src/test/java/com/cadence/application/MetricImportApiIntegrationTest.java](../src/test/java/com/cadence/application/MetricImportApiIntegrationTest.java) | H2 execution shared API/domain contract. |
| [src/test/java/com/cadence/application/MetricImportParserTest.java](../src/test/java/com/cadence/application/MetricImportParserTest.java) | Malformed/formula/URL/header/encoding/coercion/capacity/200-row parser tests. |
| [src/test/java/com/cadence/domain/MetricRowTest.java](../src/test/java/com/cadence/domain/MetricRowTest.java) | Counter/interval/raw bounds, defensive bytes copy, exclusive deadline domain tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlCsvExportMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlCsvExportMigrationTest.java) | Freeze previous V50→V51 migration scenario to its exact target. |
| [src/test/java/com/cadence/persistence/PostgreSqlMetricImportMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlMetricImportMigrationTest.java) | Populated V51→V52 retains legacy metrics exactly, no fabricated observations. |
| [src/test/java/com/cadence/persistence/PostgreSqlMetricImportTest.java](../src/test/java/com/cadence/persistence/PostgreSqlMetricImportTest.java) | Real DB worker/replay/correction/revocation/archive/lease/SQL/runtime races. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Actual migrations inventory52. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Actual UTC created/updated audit inventory60. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | Metrics DTO fields/enums/time wire alignment. |
| [src/test/java/com/cadence/support/MetricImportApiContract.java](../src/test/java/com/cadence/support/MetricImportApiContract.java) | Shared H2/PostgreSQL HTTP/security/workflow/dedup/correction/rollback boundary tests. |
