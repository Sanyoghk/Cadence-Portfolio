# Исправление OWNER-only политики (#74)

## Что изменилось и почему

Все пользовательские операции Workspace, Brand, evidence, Job и анализа требуют OWNER. Ранее EDITOR/VIEWER могли читать данные, а EDITOR — запускать анализ, вопреки согласованному MVP. Ограничения применены в контроллерах и application use cases. Роли сохранены как зарезервированные; миграции, данные и зависимости не менялись.

## Security Review и Runtime Flow

JWT → локальная роль и memberships → controller OWNER + ownership → Spring method security OWNER + scope → scoped repository → DTO.

- Threat: зарезервированная роль читает данные или запускает анализ. Protection: OWNER на обоих входных слоях. Verification: HTTP 403 и AccessDeniedException при прямом вызове Spring bean, даже внутри назначенного Workspace.
- Threat: JWT claim OWNER повышает локальные права. Protection: существующая локальная identity остаётся источником роли. Verification: EDITOR/VIEWER в БД с OWNER claim получают 403.
- Threat: OWNER подставляет чужой UUID или расширяет scope. Protection: сохранены membership и scoped queries. Verification: существующие cross-Workspace, forged-scope и impersonation tests.

Новые SQL, внешние вызовы, логирование или секреты не добавлены. Validation, constraints, pagination и safe DTO сохранены. Неаутентифицированные запросы продолжают получать 401. Method security требует вызова через Spring proxy.

## Проверки

- `.\mvnw.cmd '-Dtest=UseCaseMethodSecurityTest#reservedRolesCannotReadOrAnalyzeEvenWithinTheirScope' test` до исправления: 2 теста, 2 failures — ожидаемый AccessDeniedException не возникал.
- `.\mvnw.cmd spotless:apply clean verify` после исправления: BUILD SUCCESS, 202 теста, 198 прошли, 4 PostgreSQL/Testcontainers пропущены без Docker; failures/errors 0. Компиляция, test-profile startup, Spotless и JaCoCo прошли.
- `git diff --check`: успешно.

## Непроверенные аспекты и production

PostgreSQL, live Clerk и production startup для этого изменения локально не проверены. Предыдущий зелёный CI не доказывает новое изменение. Схема БД не затронута. Перед выпуском остаются общие gates проекта: production adapters, security scanning, audit/rate limiting и проверка эксплуатации.

## Что стоит изучить

- Authentication подтверждает личность; authorization отдельно разрешает операцию.
- Membership не заменяет разрешённую роль: для доступа нужны обе проверки.
- Defense in depth сохраняет запрет при обходе HTTP-контроллера через другой Spring-компонент.

## Changed files

- `docs/clerk-authentication.md`: Current role policy and verification boundaries.
- `docs/issue-sync.md`: Current role policy and verification boundaries.
- `docs/job-status-api.md`: Current role policy and verification boundaries.
- `docs/use-case-authorization.md`: Current role policy and verification boundaries.
- `docs/verification.md`: Current role policy and verification boundaries.
- `src/main/java/com/cadence/application/usecases/AnalyzeBrandUseCase.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/application/usecases/BrandManagementUseCase.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/application/usecases/GetJobUseCase.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/application/usecases/RegisterBrandEvidenceUseCase.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/application/usecases/WorkspaceManagementUseCase.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/presentation/BrandController.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/presentation/BrandEvidenceController.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/presentation/CadencePanelController.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/presentation/JobController.java`: OWNER-only operation guards.
- `src/main/java/com/cadence/presentation/WorkspaceController.java`: OWNER-only operation guards.
- `src/test/java/com/cadence/security/JobApiIntegrationTest.java`: Regression and authorization coverage.
- `src/test/java/com/cadence/security/SecurityIntegrationTest.java`: Regression and authorization coverage.
- `src/test/java/com/cadence/security/UseCaseMethodSecurityTest.java`: Regression and authorization coverage.
- `docs/owner-only-role-fix.md`: change report, threat model and actual test results.
