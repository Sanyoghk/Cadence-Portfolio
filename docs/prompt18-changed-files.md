# Задание18 — изменённые файлы

Всего48 файлов; список получен из actual Git diff + non-ignored untracked files.

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Текущий scope18, prerequisite/qualification и следующий19 без запуска. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронная русская точка продолжения18 и границы provider scope. |
| [README.md](../README.md) | Навигация к текущему private durable CSV API/runbook. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Least privilege: immutable CSV links/history, только fenced artifact publication UPDATE columns. |
| [docs/adr/0035-durable-csv-export.md](adr/0035-durable-csv-export.md) | ADR контракт/lifetime/limits/recovery/formula/security/alternatives до qualification. |
| [docs/adr/README.md](adr/README.md) | Индекс actual Content/calendar/media/CSV ADR0032–0035. |
| [docs/api-contract.md](api-contract.md) | Текущий additive CSV contract отделён от исторического #111 baseline. |
| [docs/astra-medium-mvp-prompts.md](astra-medium-mvp-prompts.md) | Execution note18 и актуальные prerequisites/continuation для следующего номера. |
| [docs/csv-export-api.md](csv-export-api.md) | API,41columns,encoding/timezone/checksums/idempotency/download/retention/recovery runbook. |
| [docs/issue-sync.md](issue-sync.md) | Actual #103/#107 acceptance mapping, local scope и honest remote/provider remaining work. |
| [docs/job-status-api.md](job-status-api.md) | Typed ingest/AI/CSV Job mapping, current CSV authorization/download policy и граница исторических results. |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Execution ledger18 без повторения14–17/запуска19. |
| [docs/prompt18-changed-files.md](prompt18-changed-files.md) | Этот полный перечень каждого изменённого файла и назначения. |
| [docs/prompt18-csv-export-report.md](prompt18-csv-export-report.md) | Подробный итог, security review, acceptance, exact gates, facts/assumptions и границы readiness. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Fixtures обязательного report gate включают CSV SQL/runtime и V50→V51 migration suites. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | CSV PostgreSQL/migration reports обязательны; missing/skipped не считаются pass. |
| [src/main/java/com/cadence/application/repositories/CsvExportRepository.java](../src/main/java/com/cadence/application/repositories/CsvExportRepository.java) | Scoped Job IDs, scalar target projection, immutable lookup, bounded candidates/locked publication. |
| [src/main/java/com/cadence/application/repositories/DurableJobRepository.java](../src/main/java/com/cadence/application/repositories/DurableJobRepository.java) | Generic queue/recovery исключает CSV_EXPORT; экспорт получает отдельный worker contract. |
| [src/main/java/com/cadence/application/results/CsvExportDetails.java](../src/main/java/com/cadence/application/results/CsvExportDetails.java) | Typed safe status/provenance/download path; controlled error code allowlist без worker/key/secret. |
| [src/main/java/com/cadence/application/services/CsvExportRenderer.java](../src/main/java/com/cadence/application/services/CsvExportRenderer.java) | Pure deterministic bounded rendering вне DB transaction и testable crash boundary. |
| [src/main/java/com/cadence/application/services/CsvExportService.java](../src/main/java/com/cadence/application/services/CsvExportService.java) | Submission/idempotency/capture, atomic lease/recovery, publication/failure/status/private download. |
| [src/main/java/com/cadence/application/services/CsvExportSources.java](../src/main/java/com/cadence/application/services/CsvExportSources.java) | Actual actor/parent locks, exact approved revision/schedule/source/media graph и canonical CSV rows. |
| [src/main/java/com/cadence/application/usecases/CsvExportUseCase.java](../src/main/java/com/cadence/application/usecases/CsvExportUseCase.java) | OWNER/scope method authorization и actual principal→application boundary. |
| [src/main/java/com/cadence/domain/CsvDocument.java](../src/main/java/com/cadence/domain/CsvDocument.java) | 41 fixed columns, UTF8 BOM/CRLF/quoting/Unicode formula neutralization и SHA256/caps. |
| [src/main/java/com/cadence/domain/CsvExportBatch.java](../src/main/java/com/cadence/domain/CsvExportBatch.java) | Durable immutable capture/provenance, private bytea artifact, first publication/expiry domain guards. |
| [src/main/java/com/cadence/domain/CsvExportItem.java](../src/main/java/com/cadence/domain/CsvExportItem.java) | Immutable approved revision/schedule FKs, exact version и original content checksum. |
| [src/main/java/com/cadence/domain/CsvExportRequest.java](../src/main/java/com/cadence/domain/CsvExportRequest.java) | Bounded normalized selections/period/timezone и canonical scoped request digest. |
| [src/main/java/com/cadence/domain/DurableJob.java](../src/main/java/com/cadence/domain/DurableJob.java) | Server-owned pending→exact export binding; существующие AI/ingest semantics сохранены. |
| [src/main/java/com/cadence/infrastructure/content/CsvExportScheduling.java](../src/main/java/com/cadence/infrastructure/content/CsvExportScheduling.java) | Explicit worker activation, bounded polling, monotonic failure backoff и fixed safe warning. |
| [src/main/java/com/cadence/presentation/CsvExportController.java](../src/main/java/com/cadence/presentation/CsvExportController.java) | 202 Job Location, typed status, authenticated no-store CSV attachment без arbitrary path/URL. |
| [src/main/java/com/cadence/presentation/JobController.java](../src/main/java/com/cadence/presentation/JobController.java) | Существующий /jobs/{id} дополнен scoped typed CSV_EXPORT status без изменения old views. |
| [src/main/java/com/cadence/presentation/dto/CsvExportRequestDto.java](../src/main/java/com/cadence/presentation/dto/CsvExportRequestDto.java) | Dedicated strict request allowlist + nested selection validation; нет writable ownership/approval. |
| [src/main/resources/application.properties](../src/main/resources/application.properties) | CSV worker explicit opt-in defaultfalse; production activation не выбирает AI adapter. |
| [src/main/resources/db/migration/V51__durable_csv_export.sql](../src/main/resources/db/migration/V51__durable_csv_export.sql) | Private export/items tables, FKs/bounds/quota/actor/source/history/artifact/lease/atomic Job SQL seals. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | 3 CSV operations/DTOs и Job union; old paths/schemas semantic compatibility checked. |
| [src/test/java/com/cadence/application/CsvExportApiIntegrationTest.java](../src/test/java/com/cadence/application/CsvExportApiIntegrationTest.java) | H2 HTTP instance общего CSV contract. |
| [src/test/java/com/cadence/domain/CsvDocumentTest.java](../src/test/java/com/cadence/domain/CsvDocumentTest.java) | Formula whitespace/control/Unicode, CSV quotes/newlines, deterministic bytes, limits/request digest. |
| [src/test/java/com/cadence/persistence/PostgreSqlCsvExportMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlCsvExportMigrationTest.java) | Populated V50→V51 с exact retained STAGING audit и отсутствием fabricated exports. |
| [src/test/java/com/cadence/persistence/PostgreSqlCsvExportTest.java](../src/test/java/com/cadence/persistence/PostgreSqlCsvExportTest.java) | Real PostgreSQL/runtime/replay/lease/crash/media/download/access/archive/edit races и SQL bypass. |
| [src/test/java/com/cadence/persistence/PostgreSqlPdfAnalysisMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPdfAnalysisMigrationTest.java) | Regression fixture: один statement_timestamp для exact60s deadline; production CHECK/assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlPdfIdentityMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPdfIdentityMigrationTest.java) | Такой же стабильный DB timestamp для legacy Identity analysis fixture без fabricated history. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Точный inventory migrations1–51, old versions не пропущены. |
| [src/test/java/com/cadence/persistence/PostgreSqlPostMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPostMigrationTest.java) | Legacy upgrade не придумывает CSV exports/source bindings. |
| [src/test/java/com/cadence/persistence/PostgreSqlUrlAnalysisMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUrlAnalysisMigrationTest.java) | Стабильный timestamp source/deadline fixture; воспроизведённый +1µs failure устранён без ослабления constraint. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | UTC created_at/updated_at inventory56 после новой timestamped export table. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | DTO schema/enum alignment для новых strict CSV request и typed export status. |
| [src/test/java/com/cadence/support/CsvExportApiContract.java](../src/test/java/com/cadence/support/CsvExportApiContract.java) | Submit→worker→status→download, 4 weeks/mixed platforms, BOLA/role/strict/stale/idempotency cases. |
| [src/test/java/com/cadence/support/PostApiContract.java](../src/test/java/com/cadence/support/PostApiContract.java) | Regression fixture: human edit uses authoritative DB Clock вместо host Instant.now; guard не ослаблен. |
