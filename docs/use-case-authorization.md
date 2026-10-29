> Current policy: #74 correction requires OWNER for all operations. EDITOR/VIEWER access described in historical validation below is now denied. See [role correction](owner-only-role-fix.md).

# Авторизация application use cases

## Что было сделано

WorkspaceManagementUseCase, BrandManagementUseCase, RegisterBrandEvidenceUseCase и AnalyzeBrandUseCase теперь защищены Spring method security. Вызов через Spring bean проверяется независимо от наличия controller. На классе установлен denyAll; каждый доступный метод содержит явное правило.

Добавленный позднее GetJobUseCase следует тем же правилам; его контракт и проверки описаны в [job-status-api.md](job-status-api.md).

## Архитектурное решение

Сохранён текущий контракт методов с permittedWorkspaceIds. ObjectAuthorization проверяет, что этот набор является подмножеством memberships текущего authenticated CadencePrincipal. Внутренний caller может сузить scope, но не расширить его. Затем существующий scoped repository lookup ограничивает конкретный ресурс.

Создание Workspace требует OWNER и совпадения ownerId с текущим userId. Проверка сохранённой роли владельца в use case остаётся дополнительной защитой. Действующие роли HTTP не расширены:

| Операция | Разрешённые роли |
| --- | --- |
| Чтение Workspace, Brand, evidence | OWNER в собственном scope |
| Создание/изменение/архивирование Workspace и Brand, регистрация evidence | OWNER |
| Существующий placeholder анализа Brand | OWNER |

EDITOR/VIEWER запрещены все пользовательские операции, включая чтение и анализ; роли зарезервированы для будущего согласования.

Method security работает через Spring proxy. Production callers должны получать use cases через dependency injection. Создание через new и self-invocation не проходят повторно через proxy. Текущие внутренние вызовы update/archive → findById сохраняют scope, а входной метод уже выполняет равную или более строгую проверку. Background worker не должен подделывать user authentication: существующий URL worker использует отдельные внутренние сервисы.

## Threat model и Security Review

1. Защищаем Workspace/Brand/evidence, memberships и право изменения данных.
2. User-facing use cases могут вызывать controller или другой компонент приложения от имени authenticated пользователя.
3. Граница доверия — вход в Spring use-case bean: authentication и аргументы ownerId/permittedWorkspaceIds.
4. Возможные атаки — VIEWER вызывает mutation; OWNER передаёт чужой scope; caller подставляет ownerId другого пользователя; отсутствует authentication.
5. Защита — явные role rules, authenticated CadencePrincipal, subset-проверка scope, current-user проверка ownerId, затем scoped lookup.
6. Если controller пропущен, method security остаётся активной. Если передан UUID чужого объекта с собственным scope, repository lookup его не возвращает. Нарушение доверенной среды JVM/создание Authentication произвольным кодом этим механизмом не изолируется.
7. Defense in depth — HTTP authentication/authorization → method security → scoped repository lookup → существующие domain guards и database constraints.

Threat: прямой вызов user-facing service в обход HTTP role rule.
Protection: PreAuthorize на use case, default denyAll.
Verification: прямые вызовы Spring beans без authentication, с VIEWER и EDITOR.

Threat: расширение разрешённых Workspace IDs или подмена владельца.
Protection: permitsWorkspaceScope и isCurrentUser.
Verification: forged/mixed scope и чужой ownerId отклоняются; собственные операции проходят.

SQL, migrations, DTO, JWT-протокол и dependencies не менялись. Новые queries, внешние вызовы и логирование sensitive data не добавлены. API rate limiting, audit trail и production persistence остаются отдельными задачами.

## Runtime Flow

JWT → локальный principal → controller role/ownership → Spring method security → проверка scope или ownerId → существующий use case → scoped query → domain/DB → response DTO.

## Изменённые файлы этого этапа

| Файл | Назначение |
| --- | --- |
| src/main/java/com/cadence/security/ObjectAuthorization.java | Authenticated principal, subset scope и current user |
| src/main/java/com/cadence/application/usecases/WorkspaceManagementUseCase.java | Роли, scope и запрет owner impersonation |
| src/main/java/com/cadence/application/usecases/BrandManagementUseCase.java | Роли и scope |
| src/main/java/com/cadence/application/usecases/RegisterBrandEvidenceUseCase.java | Роли регистрации/чтения и scope |
| src/main/java/com/cadence/application/usecases/AnalyzeBrandUseCase.java | Роль анализа и scope |
| src/test/java/com/cadence/security/UseCaseMethodSecurityTest.java | Direct Spring-bean integration tests |
| docs/verification.md | Актуальные границы authorization |
| docs/clerk-authentication.md | Описание второго слоя проверок |
| docs/maintenance-review-2026-09-11.md | Пометка об историческом ограничении первого этапа |
| docs/use-case-authorization.md | Этот отчёт и правила для новых callers |

## Security Tests и Edge Cases

UseCaseMethodSecurityTest проверяет отсутствие authentication, недостаточную роль, поддельный/смешанный scope, null scope, чужой объект с собственным scope, подмену ownerId, authority без CadencePrincipal и неаутентифицированный principal. Пустой scope возвращает пустой список Workspaces для OWNER; OWNER может выполнять lifecycle и evidence операции; EDITOR/VIEWER не могут читать данные или выполнять анализ.

Предварительный regression run воспроизвёл отсутствие отказа на расширенном scope. Исправления не ослабляют assertions. В новом комплексном lifecycle-тесте добавлен flush после анализа перед чтением текущей версии: тест объединяет операции в одну транзакцию, тогда как отдельные HTTP-запросы завершают свои транзакции между операциями.

## Что мне стоит изучить

- Spring proxy: аннотация выполняет проверку при вызове управляемого bean, а не произвольного Java-объекта.
- Least privilege: scope можно сузить, но нельзя получить больше memberships через аргумент метода.
- Defense in depth: роль разрешает тип операции; scoped query определяет допустимые данные.
- Optimistic locking: version меняется при flush/commit, поэтому следующий запрос использует актуальную версию.

## Тесты

- `.\mvnw.cmd '-Dtest=UseCaseMethodSecurityTest#suppliedScopeCannotExpandAuthenticatedMembership' test` до исправления: один failure, расширенный scope не был отклонён. Это воспроизведение regression.
- `.\mvnw.cmd spotless:apply '-Dtest=UseCaseMethodSecurityTest,SecurityIntegrationTest' test`: HTTP-проверки прошли; новый lifecycle test выявил необходимость flush перед чтением версии внутри общей тестовой транзакции. Ожидания optimistic locking не ослаблялись.
- `.\mvnw.cmd spotless:apply clean verify` после исправлений: BUILD SUCCESS; 187 тестов, 183 прошли, 4 PostgreSQL-теста пропущены. Все 11 новых method-security сценариев прошли. Компиляция, Spotless и JaCoCo threshold прошли; Spring application context поднят в test profile.
- `git diff --check`: без ошибок whitespace.

## Непроверенные аспекты

PostgreSQL/Testcontainers не запускались из-за отсутствующего Docker; migration SQL и production startup этим запуском не подтверждены. Live Clerk, удалённый CI и production integrations не проверялись. Ранее сделанные изменения сохранены; commit, push и deployment не выполнялись.

## Production considerations

Сохранены ранее зафиксированные ограничения: Docker/PostgreSQL, настоящий Clerk tenant, remote CI и production adapters не подтверждены этим этапом. Проверки выполняются на H2 для application behavior; они не доказывают PostgreSQL constraints или готовность к deployment.
