# Задание22 — изменённые файлы

| Файл | Назначение |
| --- | --- |
| [.env.example](../.env.example) | Явный disabled-by-default report worker flag без credentials. |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальные Task6.3/report22 acceptance и qualification boundary. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронный статус report22 и remaining scopes на русском. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Report snapshot/history immutable; runtime может только first fenced artifact publication. |
| [docs/adr/0039-creator-performance-reports.md](../docs/adr/0039-creator-performance-reports.md) | Approved package preserved; engineering capture/retention/source/history rules. |
| [docs/astra-medium-mvp-prompts.md](../docs/astra-medium-mvp-prompts.md) | Execution22 actual implementation/qualification и следующий номер отдельно. |
| [docs/creator-reports-api.md](../docs/creator-reports-api.md) | API, stable snapshot semantics, retention/expiry, worker/recovery/limits/runbook. |
| [docs/issue-sync.md](../docs/issue-sync.md) | #105/#107 actual mapping и approved private-delivery refinement без remote closure. |
| [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) | Ledger report22, без повторной реализации19–21. |
| [docs/prompt22-changed-files.md](../docs/prompt22-changed-files.md) | Полный per-file manifest данного slice. |
| [docs/prompt22-report-contract.md](../docs/prompt22-report-contract.md) | Initial design и текущие implementation/API/qualification references. |
| [docs/prompt22-reports-report.md](../docs/prompt22-reports-report.md) | Подробный qualification/architecture/security/mentoring report. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Report gate regression: required missing/skipped suites fail qualification. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Mandatory actual successful report PostgreSQL/runtime/migration suites. |
| [src/main/java/com/cadence/application/exceptions/CreatorReportUnavailableException.java](../src/main/java/com/cadence/application/exceptions/CreatorReportUnavailableException.java) | Controlled admission/infrastructure503, internal cause не раскрывается в API. |
| [src/main/java/com/cadence/application/repositories/CreatorReportRepository.java](../src/main/java/com/cadence/application/repositories/CreatorReportRepository.java) | Scoped target/Job/key lookups, retained quota и bounded worker candidates. |
| [src/main/java/com/cadence/application/repositories/DurableJobRepository.java](../src/main/java/com/cadence/application/repositories/DurableJobRepository.java) | Исключить CREATOR_REPORT из generic ingest claim/recovery для reviewed lock order. |
| [src/main/java/com/cadence/application/results/CreatorReportDetails.java](../src/main/java/com/cadence/application/results/CreatorReportDetails.java) | Safe status/actor/time/checksums/controlled errors/lifetime/relative paths DTO. |
| [src/main/java/com/cadence/application/results/CreatorReportSnapshot.java](../src/main/java/com/cadence/application/results/CreatorReportSnapshot.java) | Dedicated immutable export model; privacy projection, exact counters/formulas/source validation. |
| [src/main/java/com/cadence/application/services/CreatorReportRenderer.java](../src/main/java/com/cadence/application/services/CreatorReportRenderer.java) | Deterministic escaped static HTML, human-readable metrics/coverage/history без scripts/resources. |
| [src/main/java/com/cadence/application/services/CreatorReportService.java](../src/main/java/com/cadence/application/services/CreatorReportService.java) | Atomic capture/Job, scoped idempotency/result/download, claim/retry/recovery/publication fencing. |
| [src/main/java/com/cadence/application/services/CreatorReportSources.java](../src/main/java/com/cadence/application/services/CreatorReportSources.java) | Actual parent/source access, same captured metric rows, original historical advice evidence. |
| [src/main/java/com/cadence/application/services/RecommendationService.java](../src/main/java/com/cadence/application/services/RecommendationService.java) | Package-private historical projection reuse внутри уже authorized parent-locked transaction. |
| [src/main/java/com/cadence/application/usecases/CreatorReportUseCase.java](../src/main/java/com/cadence/application/usecases/CreatorReportUseCase.java) | Deny-by-default OWNER/scope boundary и actual actor из principal. |
| [src/main/java/com/cadence/domain/CreatorReport.java](../src/main/java/com/cadence/domain/CreatorReport.java) | Bounded immutable snapshot и single artifact publication/expiry/lease invariants. |
| [src/main/java/com/cadence/domain/CreatorReportOutputException.java](../src/main/java/com/cadence/domain/CreatorReportOutputException.java) | Permanent controlled invalid/oversized output outcome вместо бесконечных retries. |
| [src/main/java/com/cadence/domain/CreatorReportRequest.java](../src/main/java/com/cadence/domain/CreatorReportRequest.java) | Validated exact date/source/platform/bucket/selection и stable request digest. |
| [src/main/java/com/cadence/domain/DurableJob.java](../src/main/java/com/cadence/domain/DurableJob.java) | Typed report Job payload binding, separate from ingest/AI/CSV. |
| [src/main/java/com/cadence/infrastructure/analytics/CreatorReportScheduling.java](../src/main/java/com/cadence/infrastructure/analytics/CreatorReportScheduling.java) | Explicit worker activation, bounded poll и safe infrastructure backoff/log. |
| [src/main/java/com/cadence/presentation/ApiExceptionHandler.java](../src/main/java/com/cadence/presentation/ApiExceptionHandler.java) | Safe report_unavailable Problem Details. |
| [src/main/java/com/cadence/presentation/CreatorReportController.java](../src/main/java/com/cadence/presentation/CreatorReportController.java) | Submit202/Location/status/JSON/HTML API, private/no-store/CSP/nosniff/Range refusal. |
| [src/main/java/com/cadence/presentation/JobController.java](../src/main/java/com/cadence/presentation/JobController.java) | Scoped CREATOR_REPORT status dispatch, foreign/missing remains404. |
| [src/main/java/com/cadence/presentation/dto/CreatorReportRequestDto.java](../src/main/java/com/cadence/presentation/dto/CreatorReportRequestDto.java) | Strict allowlisted request DTO; audit/tenant/approval not writable. |
| [src/main/resources/application.properties](../src/main/resources/application.properties) | Report worker default false, explicit environment activation. |
| [src/main/resources/db/migration/V55__creator_performance_reports.sql](../src/main/resources/db/migration/V55__creator_performance_reports.sql) | Additive table/index/FK/checks/source/evidence/immutability and deferred Job/result guards. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | Report4 endpoints/8 typed schemas и Job variant; prior contracts preserved. |
| [src/test/java/com/cadence/application/CreatorReportApiIntegrationTest.java](../src/test/java/com/cadence/application/CreatorReportApiIntegrationTest.java) | H2 execution shared report/API/security/history contract. |
| [src/test/java/com/cadence/application/CreatorReportRendererTest.java](../src/test/java/com/cadence/application/CreatorReportRendererTest.java) | Independent deterministic escaping/Unicode/output cap/request-bound unit tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlCreatorReportMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlCreatorReportMigrationTest.java) | Populated V54→V55, legacy data unchanged/new report history empty. |
| [src/test/java/com/cadence/persistence/PostgreSqlCreatorReportTest.java](../src/test/java/com/cadence/persistence/PostgreSqlCreatorReportTest.java) | Actual PostgreSQL/runtime/SQL forged evidence/quota/replay/lease/revocation/deadline tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Actual migration inventory55. |
| [src/test/java/com/cadence/persistence/PostgreSqlRecommendationMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlRecommendationMigrationTest.java) | Historical V53→V54 test explicitly targets54; assertions retained. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Actual created/updated UTC audit inventory70. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | Report request/result/nested record DTO fields and enums bound to reviewed schema. |
| [src/test/java/com/cadence/support/CreatorReportApiContract.java](../src/test/java/com/cadence/support/CreatorReportApiContract.java) | Shared capture/JSON/HTML/coverage/correction/expiry/security/oversize/archive tests. |
