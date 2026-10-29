# Изменённые файлы — issue #114

Baseline: `bedbf56289acfe6921eea075b703101f03656169`. Каждый файл и назначение изменения:

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Разделение delivered port от отложенного real AI workflow. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | То же уточнение scope в русском плане. |
| [README.md](../README.md) | Актуальное описание implementation и ссылка на analysis contract. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Запрет runtime DELETE ledger и UPDATE immutable source fields. |
| [docs/adr/0009-brand-analysis-port-and-fenced-publication.md](adr/0009-brand-analysis-port-and-fenced-publication.md) | ADR: transaction proxy, alternatives, version fencing и ограничения. |
| [docs/adr/README.md](adr/README.md) | ADR index с новым архитектурным решением. |
| [docs/brand-analysis.md](brand-analysis.md) | Contract/configuration/runtime lifecycle/deployment limits. |
| [docs/issue-114-completion.md](issue-114-completion.md) | Полный отчёт, реальные команды/tests и production separation. |
| [docs/issue-114-files.md](issue-114-files.md) | Этот полный manifest каждого изменённого файла. |
| [docs/issue-sync.md](issue-sync.md) | Синхронизация согласованного scope с GitHub/master plan. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Missing/skipped analysis report запрещает ложный зелёный gate. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Обязательная успешная PostgreSqlBrandAnalysisTest qualification без skips. |
| [src/main/java/com/cadence/application/exceptions/BrandAnalysisUnavailableException.java](../src/main/java/com/cadence/application/exceptions/BrandAnalysisUnavailableException.java) | Контролируемая ошибка без раскрытия provider diagnostics. |
| [src/main/java/com/cadence/application/ports/BrandAnalysisCommand.java](../src/main/java/com/cadence/application/ports/BrandAnalysisCommand.java) | Immutable нормализованные declared fields, UUID попытки и версии источника. |
| [src/main/java/com/cadence/application/ports/BrandAnalysisPort.java](../src/main/java/com/cadence/application/ports/BrandAnalysisPort.java) | Provider-neutral interface без Entity и raw provider payload. |
| [src/main/java/com/cadence/application/ports/BrandAnalysisResult.java](../src/main/java/com/cadence/application/ports/BrandAnalysisResult.java) | Bounded validated output, immutable copies, fresh signature и однозначный SHA-256 checksum. |
| [src/main/java/com/cadence/application/repositories/BrandAnalysisAttemptRepository.java](../src/main/java/com/cadence/application/repositories/BrandAnalysisAttemptRepository.java) | Persistence попыток и lookup активной попытки бренда. |
| [src/main/java/com/cadence/application/services/BrandAnalysisTransactions.java](../src/main/java/com/cadence/application/services/BrandAnalysisTransactions.java) | Короткие отдельные start/complete/fail transactions, scoped locks и version/deadline fencing. |
| [src/main/java/com/cadence/application/services/BrandIngestService.java](../src/main/java/com/cadence/application/services/BrandIngestService.java) | Вызов immutable port и безопасное преобразование ошибок/null результата. |
| [src/main/java/com/cadence/application/usecases/AnalyzeBrandUseCase.java](../src/main/java/com/cadence/application/usecases/AnalyzeBrandUseCase.java) | Orchestration без caller transaction; failure recording и atomic completion. |
| [src/main/java/com/cadence/domain/BrandAnalysisAttempt.java](../src/main/java/com/cadence/domain/BrandAnalysisAttempt.java) | Lifecycle RUNNING/SUCCEEDED/FAILED/SUPERSEDED, deadline и provenance. |
| [src/main/java/com/cadence/infrastructure/analysis/BrandAnalysisConfiguration.java](../src/main/java/com/cadence/infrastructure/analysis/BrandAnalysisConfiguration.java) | Fake isolation в local/test; prod/mixed profiles и unknown adapter startup rejection. |
| [src/main/java/com/cadence/infrastructure/analysis/DeterministicBrandAnalysisAdapter.java](../src/main/java/com/cadence/infrastructure/analysis/DeterministicBrandAnalysisAdapter.java) | Package-private deterministic fixture с demonstration warnings/provenance. |
| [src/main/java/com/cadence/presentation/ApiExceptionHandler.java](../src/main/java/com/cadence/presentation/ApiExceptionHandler.java) | HTTP 503 brand_analysis_unavailable через существующий error contract. |
| [src/main/resources/application-local.properties](../src/main/resources/application-local.properties) | Local-only fake adapter selection. |
| [src/main/resources/db/migration/V23__brand_analysis_attempts.sql](../src/main/resources/db/migration/V23__brand_analysis_attempts.sql) | PostgreSQL ledger, constraints, unique RUNNING и immutable/deadline/source guards. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI analyze description и документированный response 503. |
| [src/test/java/com/cadence/application/AnalyzeBrandUseCaseTest.java](../src/test/java/com/cadence/application/AnalyzeBrandUseCaseTest.java) | Orchestration ordering, unknown brand, failure и superseded completion. |
| [src/test/java/com/cadence/application/BrandAnalysisContractTest.java](../src/test/java/com/cadence/application/BrandAnalysisContractTest.java) | Normalization, output bounds, aliasing, safe failures и checksum ambiguity regression. |
| [src/test/java/com/cadence/application/UseCaseAuthorizationTest.java](../src/test/java/com/cadence/application/UseCaseAuthorizationTest.java) | Сохранение прежних denial assertions при новом constructor dependency. |
| [src/test/java/com/cadence/infrastructure/analysis/BrandAnalysisConfigurationTest.java](../src/test/java/com/cadence/infrastructure/analysis/BrandAnalysisConfigurationTest.java) | Prod/mixed/unknown rejection; local/test и disabled behavior. |
| [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java) | Изолированный analysis fixture при настоящих providers; runtime ledger privilege negative tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlBrandAnalysisTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandAnalysisTest.java) | Real transaction/concurrency/deadline/recovery/atomic rollback и SQL negative tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Exact inventory V1–V23 и новая mapped table. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | V21→latest populated UTC upgrade, включая audit timestamps нового ledger. |
| [src/test/java/com/cadence/security/BrandAnalysisFailClosedTest.java](../src/test/java/com/cadence/security/BrandAnalysisFailClosedTest.java) | Реальный HTTP 503/OpenAPI и сохранность signature при disabled adapter. |
| [src/test/java/com/cadence/security/UseCaseMethodSecurityTest.java](../src/test/java/com/cadence/security/UseCaseMethodSecurityTest.java) | Owner success fixture использует committed requests и свежие версии после анализа. |
| [src/test/resources/application-test.properties](../src/test/resources/application-test.properties) | Явный fake для test profile. |
