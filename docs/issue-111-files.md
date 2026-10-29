# Изменённые файлы — issue #111

Baseline: `bc4411d`. Product source qualification и commands приведены в [completion report](issue-111-completion.md).

| Файл | Назначение |
|---|---|
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Текущий API baseline и переход к #114; future routes остаются целевой архитектурой. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Русская версия baseline, scope #111 и следующий шаг #114. |
| [README.md](../README.md) | Ссылка на API contract и описание опубликованного OWNER-only OpenAPI. |
| [docs/adr/0008-reviewed-api-contract-and-utc.md](adr/0008-reviewed-api-contract-and-utc.md) | Решение, alternatives, trade-offs, legacy timezone и security boundaries. |
| [docs/adr/README.md](adr/README.md) | Индекс нового ADR-0008. |
| [docs/api-contract.md](api-contract.md) | Публичные conventions, errors, pagination, UTC, async, privacy, threat model. |
| [docs/database-migrations.md](database-migrations.md) | V22, demo-only legacy interpretation, locks, stopped-writes и recovery. |
| [docs/issue-111-completion.md](issue-111-completion.md) | Итог, runtime flow, review, tests, ограничения и учебные пояснения. |
| [docs/issue-111-files.md](issue-111-files.md) | Этот полный manifest изменений относительно baseline bc4411d. |
| [docs/issue-sync.md](issue-sync.md) | Новый implementation baseline #111; история #117 сохраняется. |
| [docs/verification.md](verification.md) | Фактические commands/results #111 отдельно от прежних revisions. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Проверяет missing/skipped UTC report и successful complete inventory. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | UTC populated migration suite теперь обязательна; skips/missing reports запрещены. |
| [src/main/java/com/cadence/application/exceptions/EvidenceMalwareRejectedException.java](../src/main/java/com/cadence/application/exceptions/EvidenceMalwareRejectedException.java) | Typed malware rejection с контролируемым message, без provider diagnostics. |
| [src/main/java/com/cadence/application/usecases/RegisterBrandEvidenceUseCase.java](../src/main/java/com/cadence/application/usecases/RegisterBrandEvidenceUseCase.java) | INFECTED verdict выбрасывает typed rejection; existing closed-failure/storage flow сохранён. |
| [src/main/java/com/cadence/domain/BaseEntity.java](../src/main/java/com/cadence/domain/BaseEntity.java) | createdAt/updatedAt и lifecycle callbacks используют Instant. |
| [src/main/java/com/cadence/http/ApiPagination.java](../src/main/java/com/cadence/http/ApiPagination.java) | Page/size/field bounds, allowlist direction и unique id ordering. |
| [src/main/java/com/cadence/http/ApiProblems.java](../src/main/java/com/cadence/http/ApiProblems.java) | Один safe ProblemDetail factory/servlet writer, code/retryable/instance/violations. |
| [src/main/java/com/cadence/presentation/ApiExceptionHandler.java](../src/main/java/com/cadence/presentation/ApiExceptionHandler.java) | Единые safe domain/validation/framework errors, malformed UUID 400, безопасный 500 log. |
| [src/main/java/com/cadence/presentation/ApiProblemHeaders.java](../src/main/java/com/cadence/presentation/ApiProblemHeaders.java) | No-store для MVC ProblemDetail responses. |
| [src/main/java/com/cadence/presentation/BrandEvidenceController.java](../src/main/java/com/cadence/presentation/BrandEvidenceController.java) | Shared pagination и explicit JSON/multipart consumes. |
| [src/main/java/com/cadence/presentation/OpenApiController.java](../src/main/java/com/cadence/presentation/OpenApiController.java) | Публикует reviewed packaged JSON только OWNER, no-store. |
| [src/main/java/com/cadence/presentation/SocialBrandEvidenceController.java](../src/main/java/com/cadence/presentation/SocialBrandEvidenceController.java) | Убрана отдельная error representation; shared advice обрабатывает outage. |
| [src/main/java/com/cadence/presentation/WorkspaceController.java](../src/main/java/com/cadence/presentation/WorkspaceController.java) | Shared pagination для Workspaces/Brands. |
| [src/main/java/com/cadence/presentation/dto/BrandEvidenceResponse.java](../src/main/java/com/cadence/presentation/dto/BrandEvidenceResponse.java) | Audit Instant; private FILE sourceReference скрыт как null. |
| [src/main/java/com/cadence/presentation/dto/BrandResponse.java](../src/main/java/com/cadence/presentation/dto/BrandResponse.java) | UTC audit Instant в DTO. |
| [src/main/java/com/cadence/presentation/dto/WorkspaceResponse.java](../src/main/java/com/cadence/presentation/dto/WorkspaceResponse.java) | UTC audit Instant в DTO. |
| [src/main/java/com/cadence/security/ApiSecurityErrors.java](../src/main/java/com/cadence/security/ApiSecurityErrors.java) | Bearer/denial/firewall errors без token/decoder diagnostics. |
| [src/main/java/com/cadence/security/MultipartAdmissionFilter.java](../src/main/java/com/cadence/security/MultipartAdmissionFilter.java) | 413/503 через shared factory; прежние budgets/Retry-After сохранены. |
| [src/main/java/com/cadence/security/SecurityConfiguration.java](../src/main/java/com/cadence/security/SecurityConfiguration.java) | Shared handlers подключены к security/resource-server/firewall; deny-by-default сохранён. |
| [src/main/resources/db/migration/V22__utc_audit_timestamps.sql](../src/main/resources/db/migration/V22__utc_audit_timestamps.sql) | 22 audit columns конвертированы explicit UTC на 11 static allowlisted tables. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI 3.1.1: 17 operations, 12 paths, 20 schemas, Bearer/errors/conventions. |
| [src/test/java/com/cadence/domain/BaseEntityTest.java](../src/test/java/com/cadence/domain/BaseEntityTest.java) | Проверяет настоящий Instant, а не local wall time. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Fresh/populated upgrade теперь обязан включать точную V22; security constraints остаются. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Populated V21 → V22, независимость от session zone, 22 audit column types. |
| [src/test/java/com/cadence/presentation/ApiPaginationTest.java](../src/test/java/com/cadence/presentation/ApiPaginationTest.java) | Отрицательные bounds/sort/direction и deterministic tie-breaker. |
| [src/test/java/com/cadence/presentation/ApiProblemContractTest.java](../src/test/java/com/cadence/presentation/ApiProblemContractTest.java) | Malformed input/validation/framework/500 contract и отсутствие reflected diagnostics. |
| [src/test/java/com/cadence/presentation/OpenApiContractAssertions.java](../src/test/java/com/cadence/presentation/OpenApiContractAssertions.java) | Проверяет использованный schema subset на реальных responses, private FILE reference. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | Exact route/DTO/enum inventory, references и OWNER-only publication. |
| [src/test/java/com/cadence/security/ApiSecurityErrorsTest.java](../src/test/java/com/cadence/security/ApiSecurityErrorsTest.java) | 401/403/firewall/Bearer protocol и challenge redaction. |
| [src/test/java/com/cadence/security/EvidenceScanIntegrationTest.java](../src/test/java/com/cadence/security/EvidenceScanIntegrationTest.java) | Shared response validation, private-key exclusion с сохранённым internal reference, typed malware error. |
| [src/test/java/com/cadence/security/JobApiIntegrationTest.java](../src/test/java/com/cadence/security/JobApiIntegrationTest.java) | Existing success/failure/Location/privacy flows теперь проверяются против contract. |
| [src/test/java/com/cadence/security/SecurityIntegrationTest.java](../src/test/java/com/cadence/security/SecurityIntegrationTest.java) | Workflow schemas, все protected operations 401, equal-name pagination, deep offsets, mass assignment. |
| [src/test/java/com/cadence/security/SocialEvidenceSecurityIntegrationTest.java](../src/test/java/com/cadence/security/SocialEvidenceSecurityIntegrationTest.java) | Existing provider success/denial/outage/idempotency/quota responses проходят contract checks. |
