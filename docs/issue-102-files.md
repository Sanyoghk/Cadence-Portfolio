# Изменённые файлы — issue #102

Baseline: `9f6f530`; оба feature slices (включая3879f23). Перечислен каждый файл.

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальный delivered REST lifecycle и оставшаяся feature очередь. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Русский план с теми же delivered/remaining границами. |
| [README.md](../README.md) | Доступный Strategy lifecycle и ссылки на contract/qualification. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Минимальный UPDATE approved_by/approved_at; lineage INSERT-only и запрет root DELETE сохраняются. |
| [docs/adr/0011-strategy-rest-lifecycle.md](adr/0011-strategy-rest-lifecycle.md) | Решение о committed approval, revision lineage/audit, locks и trade-offs. |
| [docs/adr/README.md](adr/README.md) | Индекс ADR-0011. |
| [docs/api-contract.md](api-contract.md) | Create/read/list Strategy routes расширяют existing API contract. |
| [docs/database-migrations.md](database-migrations.md) | V27, legacy NULL policy, latest grants и staging/recovery limits. |
| [docs/issue-102-api-slice.md](issue-102-api-slice.md) | Фактическая qualification первого create/read/list slice3879f23. |
| [docs/issue-102-completion.md](issue-102-completion.md) | Полный итог, threat model, tests, limits и source-specific CI. |
| [docs/issue-102-files.md](issue-102-files.md) | Этот per-file manifest обоих issue slices. |
| [docs/issue-sync.md](issue-sync.md) | GitHub completion/next issue121 и distinction local/remote facts. |
| [docs/review-remediation-90686e5.md](review-remediation-90686e5.md) | Remote CI review9f6f530 записан отдельно от feature qualification. |
| [docs/strategy-api.md](strategy-api.md) | Все endpoints, writable DTO, errors, token/lineage/audit/retry/legacy policy. |
| [docs/verification.md](verification.md) | Текущие обязательные lifecycle/concurrency/runtime report gates. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Missing/skipped PostgreSqlStrategyApiTest report запрещает ложный pass. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Mandatory real PostgreSQL Strategy HTTP workflow report. |
| [src/main/java/com/cadence/application/repositories/StrategyRepository.java](../src/main/java/com/cadence/application/repositories/StrategyRepository.java) | Scoped root/Brand projections, read graph/page и pessimistic root lock. |
| [src/main/java/com/cadence/application/results/StrategyDetails.java](../src/main/java/com/cadence/application/results/StrategyDetails.java) | Immutable transaction snapshot, defensive collections, nullable lineage/audit. |
| [src/main/java/com/cadence/application/usecases/ManageStrategyUseCase.java](../src/main/java/com/cadence/application/usecases/ManageStrategyUseCase.java) | update/approve commands: fresh transaction, ordered locks, expected token, current actor. |
| [src/main/java/com/cadence/application/usecases/ReadStrategyUseCase.java](../src/main/java/com/cadence/application/usecases/ReadStrategyUseCase.java) | OWNER/scoped bounded reads и mapping lazy values внутри read transaction. |
| [src/main/java/com/cadence/domain/Strategy.java](../src/main/java/com/cadence/domain/Strategy.java) | Validated audited approval, protected source, independent draft allocation/lineage и content replacement. |
| [src/main/java/com/cadence/domain/StrategyContent.java](../src/main/java/com/cadence/domain/StrategyContent.java) | Complete immutable editable-content invariants и wall-time precision bounds. |
| [src/main/java/com/cadence/presentation/ApiExceptionHandler.java](../src/main/java/com/cadence/presentation/ApiExceptionHandler.java) | Безопасный503 для disabled/unavailable Strategy generation. |
| [src/main/java/com/cadence/presentation/StrategyController.java](../src/main/java/com/cadence/presentation/StrategyController.java) | Create/read/list/PATCH/approval, OWNER, validated DTO, relative Location. |
| [src/main/java/com/cadence/presentation/dto/StrategyResponse.java](../src/main/java/com/cadence/presentation/dto/StrategyResponse.java) | Allowlisted HTTP snapshot без Entity с nullable approval/lineage. |
| [src/main/java/com/cadence/presentation/dto/UpdateStrategyRequest.java](../src/main/java/com/cadence/presentation/dto/UpdateStrategyRequest.java) | Editable fields/expectedVersion allowlist и Jakarta Validation. |
| [src/main/resources/db/migration/V27__strategy_lifecycle.sql](../src/main/resources/db/migration/V27__strategy_lifecycle.sql) | FK/audit pairing, immutable lineage/audit, active parents и deferred modern content checks. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | Reviewed5 Strategy operations, response/request schemas, nullable audit и error contract. |
| [src/test/java/com/cadence/domain/RecommendationTest.java](../src/test/java/com/cadence/domain/RecommendationTest.java) | Fixture использует audit-required Strategy.approve без ослабления recommendation assertions. |
| [src/test/java/com/cadence/domain/StrategyContentTest.java](../src/test/java/com/cadence/domain/StrategyContentTest.java) | Bounds/duplicate/control/precision/defensive-copy/audit/overflow domain negatives. |
| [src/test/java/com/cadence/domain/StrategyTest.java](../src/test/java/com/cadence/domain/StrategyTest.java) | Lifecycle fixtures передают actor/time; существующие immutable snapshot assertions сохраняются. |
| [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java) | Actual restricted runtime create/edit/approval/branch success плюс tampering negatives. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Exact27 applied migrations и latest JPA/Flyway schema validation. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyApiTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyApiTest.java) | Shared HTTP + direct SQL audit/lineage/content negatives, allocation/race/archive lock tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java) | Valid persisted approval actor fixture сохраняет original immutability checks. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyMigrationTest.java) | Populated V23→latest сохраняет original content и NULL lineage/audit. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | Writable DTO fields согласованы с reviewed schema; all route reflection gate. |
| [src/test/java/com/cadence/security/SecurityIntegrationTest.java](../src/test/java/com/cadence/security/SecurityIntegrationTest.java) | All-route unauthenticated fixture корректно подставляет новые path variables. |
| [src/test/java/com/cadence/security/StrategyApiFailClosedTest.java](../src/test/java/com/cadence/security/StrategyApiFailClosedTest.java) | Actual disabled provider даёт safe503 без fallback или persisted draft. |
| [src/test/java/com/cadence/security/StrategyApiIntegrationTest.java](../src/test/java/com/cadence/security/StrategyApiIntegrationTest.java) | Shared Strategy HTTP/application/security contract на H2. |
| [src/test/java/com/cadence/support/StrategyApiContract.java](../src/test/java/com/cadence/support/StrategyApiContract.java) | Общий19-case real HTTP contract: OWNER, scope, content, token, audit, branches и forgery. |
