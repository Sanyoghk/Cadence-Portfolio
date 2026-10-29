# Cadence: исправления после восстановления состояния

Это отчёт о первом этапе исправлений. Последующее усиление ролей на уровне Spring use cases описано в [use-case-authorization.md](use-case-authorization.md); указанное ниже ограничение проверок ролей контроллерами более не описывает текущее состояние.

## Что было сделано

Устранены обнаруженные пробелы Workspace-scoping в application layer, ошибка JWT validation и устаревшие утверждения документации. Добавлена конфигурация CI с обязательным контролем PostgreSQL-тестов. Изменения локальные, без commit, push и deployment.

## Архитектурное решение

Сохранён существующий pattern: контроллер получает CadencePrincipal после authentication, проверяет роль и передаёт разрешённые Workspace IDs в use case. Use case выполняет scoped lookup до чтения или изменения ресурса. Внешний клиент не передаёт этот набор через DTO. Анализ бренда выполняется в транзакции и возвращает через контроллер существующий BrandSignatureResponse DTO. HTTP path и поля ответа сохранены.

Ролевые проверки HTTP остаются в контроллерах; эти изменения не превращают внутренние методы в отдельный authentication boundary. Внутренние callers обязаны получать разрешения из доверенного серверного контекста. Это ограничение явно описано в verification.md.

Миграции, структура БД и runtime dependencies не менялись. Используются существующие scoped queries и constraints. Архивация Workspace теперь запрещает обновление Brand, анализ и добавление evidence в его Brands.

## Security Review

Threat: вызов use case с UUID чужого Brand/Workspace, минуя controller.
Protection: обязательный permittedWorkspaceIds и scoped lookup до побочных эффектов.
Verification: UseCaseAuthorizationTest проверяет отказ и отсутствие вызовов ingest/storage/scanner/очереди; MVC проверяет HTTP 401/403.

Threat: JWT без aud/azp вызывает NullPointerException; JWT без exp не имеет обязательного срока действия.
Protection: null-safe validators и обязательный exp; сохранена проверка подписи, issuer, audience, authorized party и времени.
Verification: JwtDecoderTest использует настоящий configured decoder, локальный JWKS и генерируемые RSA-подписи. До исправления новый тест воспроизвёл NullPointerException. После исправления он проходит без ослабления ожидаемого отказа.

Threat: обход архивирования через дочерний Brand.
Protection: проверяется статус Brand и Workspace до анализа, обновления и регистрации evidence.
Verification: direct-call и HTTP negative tests; внешние побочные эффекты не вызываются.

Threat: ложная зелёная CI-сборка при пропущенных PostgreSQL-тестах.
Protection: docker info перед сборкой, затем проверка наличия и результата XML-отчёта; пропуск означает отказ CI.
Verification: Python unit test покрывает успешный, пустой, отсутствующий, несогласованный, skipped, failed и error report; настоящий локальный skipped report отклонён.

Injection: новые SQL/shell операции с пользовательским вводом не добавлены. Secrets: тестовые RSA-ключи генерируются в памяти, реальные credentials не используются. Abuse/resource limits ingest сохранены; rate limits остаются release work. Logging чувствительных данных не добавлен. CI имеет contents: read, checkout не сохраняет credentials; actions закреплены SHA. Dependabot не заменяет vulnerability scan.

## Изменённые файлы

Пути относительно корня Cadence:

| Файл | Назначение |
| --- | --- |
| src/main/java/com/cadence/application/usecases/AnalyzeBrandUseCase.java | Scoped lookup, archive guard, транзакция |
| src/main/java/com/cadence/application/usecases/BrandManagementUseCase.java | Scoping create/list; guard архивированного Workspace |
| src/main/java/com/cadence/application/usecases/RegisterBrandEvidenceUseCase.java | Scoping списка; archive guard |
| src/main/java/com/cadence/presentation/CadencePanelController.java | Передача разрешений и response DTO |
| src/main/java/com/cadence/presentation/WorkspaceController.java | Передача разрешений create/list Brand |
| src/main/java/com/cadence/presentation/BrandEvidenceController.java | Передача разрешений списка evidence |
| src/main/java/com/cadence/security/SecurityConfiguration.java | Null-safe JWT claims, обязательный exp, пояснение CSRF |
| src/test/java/com/cadence/application/AnalyzeBrandUseCaseTest.java | Проверка scoped контракта анализа |
| src/test/java/com/cadence/application/BrandManagementUseCaseTest.java | Проверка scoped контракта создания |
| src/test/java/com/cadence/application/UseCaseAuthorizationTest.java | Direct-call negative и positive tests |
| src/test/java/com/cadence/security/JwtDecoderTest.java | Настоящие подписи/JWKS/validators |
| src/test/java/com/cadence/security/SecurityIntegrationTest.java | HTTP анализ, роли, чужой Workspace, archive, response contract |
| .github/workflows/verify.yml | CI verify и PostgreSQL gate |
| .github/dependabot.yml | Предложения обновлений Maven/Actions |
| scripts/verify_postgres_report.py | Проверка фактического выполнения PostgreSQL suite |
| scripts/test_verify_postgres_report.py | Проверка CI gate на разных отчётах |
| .gitignore | Исключение Python bytecode cache |
| README.md | Точный статус заглушки и CI |
| DEVELOPMENT_PLAN.md | Статус CI без утверждения о remote success |
| DEVELOPMENT_PLAN_RU.md | То же в русском плане |
| docs/adr/0001-spring-boot-modular-monolith.md | Убраны устаревшие pilot/iframe требования |
| docs/clerk-authentication.md | Границы и способы проверки authentication |
| docs/verification.md | Проверенное поведение, assumptions и production gates |
| docs/maintenance-review-2026-09-11.md | Этот отчёт |

## Runtime Flow

Bearer JWT → настоящий decoder и validators → локальный CadencePrincipal → роль/ownership в controller → permittedWorkspaceIds → scoped lookup в use case → проверка архивирования → операция в БД → DTO.

## Тесты

- `.\mvnw.cmd spotless:apply test`: первоначально 175 тестов, один failure в новом JWT negative test; выявлен NullPointerException.
- `.\mvnw.cmd spotless:apply clean verify`: BUILD SUCCESS, 176 тестов, 0 failures/errors, 4 skipped.
- `.\mvnw.cmd spotless:apply verify`: финальная Java-проверка после review, BUILD SUCCESS; 172 прошли, 4 skipped; Spotless и JaCoCo threshold прошли.
- `python -m unittest discover -s scripts -p 'test_*.py'`: один test method со сценариями, OK.
- `python scripts/verify_postgres_report.py`: ожидаемый exit 1 — локальные PostgreSQL-тесты были пропущены; ложный success заблокирован.
- `git diff --check`: ошибок whitespace нет.

## Security Tests

Проверены unauthenticated, чужой Workspace, VIEWER denial, разрешённый OWNER/EDITOR analysis, прямой вызов use cases вне membership, неправильная подпись JWT, issuer/audience/azp, отсутствие aud/azp/exp, expired и not-before в будущем. Существующие SSRF/worker tests также прошли в полном наборе.

## Edge Cases

Архивирован Brand; архивирован родительский Workspace; отсутствует ресурс; отсутствует обязательный JWT claim; PostgreSQL suite пропущен или отсутствует его отчёт. Чтение архивированных данных для участников сохранено.

## Непроверенные аспекты

Docker отсутствует: четыре PostgreSQL/Testcontainers-теста не выполнялись, production startup не проверен. Spring startup проверен в test profile на H2. GitHub Actions удалённо не запускался; YAML просмотрен, но GitHub validation/run ещё не состоялся. Реальный Clerk tenant/JWKS не использовался. Populated migration upgrade, vulnerability scan и SAST не выполнены.

## Что мне стоит изучить

1. Defense in depth: проверка controller не заменяет ограниченный запрос в application layer.
2. Authentication и authorization: подпись JWT подтверждает источник identity, но доступ к ресурсу решает Cadence.
3. Negative tests: должны доказывать отказ и отсутствие побочных эффектов.
4. Проверяемость CI: skipped test не даёт доказательства работоспособности PostgreSQL.

## Production considerations

До deployment нужны реальные storage/scanner, operational gates ADR-0005, PostgreSQL/upgrade verification, live Clerk configuration, rate limits и security scans. Эти внешние проверки и незавершённые продуктовые функции не объявлены выполненными. Production fail-closed gate сохранён.

SHA actions сверены с официальными commits: [checkout](https://github.com/actions/checkout/commit/11bd71901bbe5b1630ceea73d27597364c9af683), [setup-java](https://github.com/actions/setup-java/commit/c5195efecf7bdfc987ee8bae7a71cb8b11521c00).
