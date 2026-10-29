# Issue #111 — API contracts, errors и controller tests

## Что было сделано

Существующие workflows получили один проверяемый HTTP contract: reviewed OpenAPI,
safe errors, pagination и UTC timestamps. Убрана утечка private FILE storage
reference. Новые feature APIs должны расширять этот contract в своих issues.
Это завершение repository scope #111, не всего MVP и не production deployment.

## Как это работает

`OpenApiController` читает packaged `openapi/cadence.json` при startup и публикует
его OWNER-only `/api/v1/openapi`. Swagger UI/runtime generator не добавлены.
`OpenApiContractTest` сравнивает controller route inventory, DTO fields и enum
values со схемой; existing workflow tests проверяют actual responses через
`OpenApiContractAssertions`. Проверяется используемый schema subset, не весь
произвольный JSON Schema standard.

`ApiProblems` задаёт status/title/detail/code/retryable/UUID instance. Filters
пишут тот же плоский JSON, который MVC отдаёт через ProblemDetail. Advice сохраняет
framework protocol headers, превращает malformed UUID в 400 и пишет безопасный
500 event без exception message/cause. DTO validation возвращает sorted field/code
entries без rejected values. Typed `EvidenceMalwareRejectedException` сохраняет
полезное фиксированное описание INFECTED verdict и code `evidence_malware_rejected`.

`ApiPagination` ограничивает page 0–10000, size 1–100, primary sort field и direction;
unique `id` делает порядок равных значений определённым. `BaseEntity` и audit DTO
хранят `Instant`; V22 переводит 22 audit columns на TIMESTAMPTZ(6). Legacy demo
wall times явно считаются UTC независимо от PostgreSQL session time zone.

## Архитектурное решение

HTTP concerns находятся в `http`, MVC representation в `presentation`, Bearer
handlers в `security`, business rejection в `application.exceptions`, persistence
type в domain/Flyway. Controllers не возвращают Entity и не передают diagnostics
adapter напрямую. Shared factory устраняет расхождение между filter и MVC paths.
Infrastructure adapter contracts #117 не меняются.

Alternatives и trade-offs описаны в [ADR-0008](adr/0008-reviewed-api-contract-and-utc.md).
Checked-in schema требует explicit review, зато не добавляет dependencies и не
выводит public contract из внутреннего Entity. Offset pagination сохраняет API,
но не является snapshot under concurrent writes и требует performance qualification
для больших tenants. `Instant` исправляет будущие моменты времени; timezone старых
значений нельзя восстановить. User подтвердил отсутствие production данных.

## Security Review

### Authentication / Authorization

Threat: missing/invalid token, forged role, foreign Workspace/Brand/Job ID.

Protection: прежние decoder issuer/audience/azp/expiration, stored server role,
deny-by-default filters, OWNER method security и scoped repository. OpenAPI не public.

Verification: все 16 защищённых operations проверены без Bearer; existing reserved
role/BOLA/direct-use-case tests проходят. Job foreign/missing/orphan/unsupported
остаётся одинаковым 404; Workspace/Brand scope violation остаётся 403.

### Data exposure / secrets / logging

Threat: exception, validation, token challenge либо FILE response раскрывает
token, input, SQL, provider endpoint или private key.

Protection: controlled messages/codes, sanitized Bearer protocol headers,
field/code-only violations, UUID instance, no-store; FILE sourceReference null;
unexpected log содержит только exception class и instance.

Verification: ApiProblemContractTest, ApiSecurityErrorsTest, provider outage tests,
recursive private FILE reference assertion. Scan integration отдельно доказывает,
что internal persisted key сохраняется для worker, хотя HTTP его скрывает.

### Validation / injection / abuse / resource exhaustion

Threat: malformed UUID/JSON, arbitrary sort path, huge offset/body, mass assignment.

Protection: DTO/domain validation, fixed sort allowlist, page/size bounds, existing
body/upload/storage quotas и deadlines; explicit DTO arguments. V22 SQL names
берутся из static table allowlist и quoted `%I`, не из external input.

Verification: deep offset/int overflow/size/direction negatives, malformed input,
internal status/role/version assignment ignored, equal-name adjacent pages unique,
existing resource/admission/security tests. Новых dependencies нет; injection
surfaces и public security matchers не расширены.

### Data integrity / concurrent updates

Threat: lost mutation, ambiguous timezone conversion, bypass DB provenance.

Protection: expectedVersion + @Version; Instant; explicit UTC conversion; прежние
DB constraints/runtime grants остаются действовать.

Verification: stale updates, fresh JPA mapping и populated V10/V21 upgrades,
session time zone invariance, all 22 audit columns, existing append-only/fencing
and runtime-role tests на PostgreSQL.

## Изменённые файлы

Полный список каждого из 44 файлов и назначения изменения:
[manifest](issue-111-files.md). Ключевые classes/methods:
`ApiProblems.create/write`, `ApiExceptionHandler.handleOther/handleValidation`,
`ApiSecurityErrors.commence/handle`, `ApiPagination.request`,
`BaseEntity.onCreate/onUpdate`, `BrandEvidenceResponse.from`, `OpenApiController.contract`.

## Runtime Flow

```text
HTTP Bearer request
  → Spring Security authentication + stored role/membership
  → bounded body/upload admission
  → DTO/parameter validation + controller method authorization
  → scoped application use case + domain invariants
  → repository + @Version + PostgreSQL constraints
  → DTO → JSON (Instant timestamps with Z)

Failure
  → ApiSecurityErrors / MultipartAdmissionFilter / ApiExceptionHandler
  → ApiProblems → application/problem+json + no-store

FILE
  → authorized active Brand → signature + private ClamAV CLEAN
  → private generation / fenced durable registration
  → persisted internal reference + Evidence/Job
  → public DTO (FILE reference null), 202 + Job Location

OpenAPI
  → authenticated OWNER → reviewed packaged JSON → no-store
```

## Тесты

Product source: `7ee9efa`. Final local full gate реально выполнен 2026-10-01,
Java 21.0.11, Docker Desktop Linux engine. Source после gate не менялся; следующие
documentation changes фиксируют результаты.

```powershell
$env:Path = 'C:\Users\<user>\AppData\Local\Programs\DockerDesktop\resources\bin;' + $env:Path
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& 'python' -m unittest discover -s scripts -p 'test_*.py'
& 'python' scripts/verify_postgres_report.py
& 'python' scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-renderer-preflight docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-preflight -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

| Gate | Фактический результат |
|---|---|
| Full Maven | BUILD SUCCESS, 4m47s; 615 tests, 0 failures/errors, 3 Windows filesystem skips; 612 executed passed |
| Linux filesystem | 35/35 passed, 0 skips, включая все 3 Windows skips |
| Required PostgreSQL/MinIO/ClamAV/renderer/full pipeline reports | Passed, no required missing/failed/skipped suites |
| Python scripts / Linux renderer | 10/10 и 29/29 passed, 0 failures/skips |
| Spotless / compile / JAR / JaCoCo | Passed; line coverage 3800 covered / 262 missed (~93.55%) |

Focused commands также реально выполнены:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply test '-Dtest=BaseEntityTest,PostgreSqlUtcMigrationTest,PostgreSqlPersistenceTest,SecurityIntegrationTest,JobApiIntegrationTest,ApiPaginationTest'
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply test '-Dtest=OpenApiContractTest,ApiPaginationTest,SecurityIntegrationTest,JobApiIntegrationTest,SocialEvidenceSecurityIntegrationTest,ApiProblemContractTest,ApiSecurityErrorsTest'
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply test '-Dtest=EvidenceScanIntegrationTest,OpenApiContractTest,ApiProblemContractTest'
```

Результаты: 46/46, 58/58 и 20/20 без skips. Ранние failed runs выявили malformed
UUID 500, outdated migration inventory, test schema pattern semantics, draft nullability,
private key expectation и потерю controlled malware detail; они исправлены и не
считаются successful gates. Constraints/authorization не ослаблены.

Warnings: negative persistence tests намеренно вызывают constraint violations;
poisoned/failed cleanup fixtures пишут controlled warnings; Hikari cached test
contexts видят закрытые connections после остановки Testcontainers. JVM class
sharing warning относится к test instrumentation. Это не failures
и не доказательство production health. Unexpected 500 event намеренно проверяется
с malformed secret-bearing exception без раскрытия её message.

Ignored logs: `var/issue111-final-verify.log`, `var/issue111-linux-filesystem.log`,
`var/issue111-python-scripts.log`, `var/issue111-renderer-python.log`.

### Compose startup

```powershell
.\scripts\start_local_evidence.ps1 -ProjectName cadence-issue117-validation -UpdateSignatures
```

Сохранённая demo БД upgraded V21 → V22, Hibernate validate/runtime startup прошли,
app/gateway/renderer-ready healthy. Реальный HTTP: health 200, Workspace и OpenAPI
без Bearer 401; problem media/no-store/code/opaque instance проверены через gateway.
Секреты не выводились. При smoke PowerShell вернул Content как byte[]; explicit
UTF-8 decoding подтвердил полный JSON contract. Это особенность client-side smoke,
не потеря полей gateway. Log: `var/issue111-compose-startup.log`.

## Remote CI qualification

[CI 36893518886](https://github.com/Sanyoghk/Cadence/actions/runs/36893518886)
для pushed source/doc baseline `1e5716d82f7cfca13289fa30ef6c98e157b0d572`
завершился success: `verify` и `dependency-security` passed.

- Ubuntu full `sh ./mvnw --batch-mode --no-transfer-progress clean verify`:
  **615 tests, 0 failures/errors/skips**, BUILD SUCCESS, 11m10s, JaCoCo passed.
- Scripts Python **10/10**, renderer Python **29/29**; required PostgreSQL/evidence
  report gates passed, включая populated UTC migration suite.
- Enforcing scoped AppArmor preflight: `namespace-probe ok`, real isolated
  screenshot1280×720. Renderer negative startup fixture намеренно сообщает
  invalid sandbox boundary; все6 RendererContainerIntegrationTest cases passed.
- FullEvidenceProviderIntegrationTest **7/7**, 0 skips.
- OSV: verified **165 Maven components**, no findings на момент scan.

Source после CI не менялся; последующий docs-only commit записывает это qualification
и не является новым выполненным CI запуском его собственного SHA. Это CI fixture,
не selected-provider или live account/production deployment qualification.

## Security Tests

Отрицательные сценарии: unauthenticated all operations, reserved roles, cross-tenant
UUID, invalid/expired token, malformed Bearer challenge, firewall rejection,
malformed JSON/UUID, validation missing fields, wrong media/method, unexpected
internal failure, decoder/provider diagnostic privacy, private FILE key exclusion,
mass assignment, unbounded/nested pagination, stale mutation и malware verdict.
Existing #117 storage/SSRF/scan/provenance/quotas/lease/security tests входят в full gate.

## Edge Cases

Равные имена сортируются unique ID; max page/size принимаются, вне bounds — 400.
Unknown input fields не назначают внутреннее состояние. Draft signature nullable
до анализа. Null FILE reference не удаляет internal object key. Duplicate evidence
сохраняет stable Job Location. Legacy UTC migration не зависит от display session
zone и сохраняет строки. Variable fractional seconds поддерживаются API schema.

## Непроверенные аспекты

Production cloud/TLS/IAM/Clerk/social credentials и actual deployment отсутствуют:
qualify separately по #32/#33/#87/#118/#116 и existing #117 runbooks.
Backup/restore на реальных данных, DDL duration/locks под нагрузкой, внешний client
generator и reverse proxy/parser errors до Spring не квалифицированы. Local Docker
не подтверждает AppArmor enforcement выбранного production host.

## Что мне важно понять

Ошибка может возникнуть до controller, поэтому одного MVC advice недостаточно.
Security filters и upload admission используют тот же explicit contract.
Неизвестный текст exception нельзя делать public detail: provider может положить
туда token или hostname. Typed domain rejection позволяет вернуть полезную причину
без reflection произвольного message.

UTC formatting не превращает LocalDateTime в момент времени. Instant задаёт момент,
TIMESTAMPTZ сохраняет его независимо от session display timezone; legacy timezone
остаётся отдельным data decision. Tie-breaker убирает неоднозначность сортировки,
но concurrent changes всё ещё сдвигают offset pages.

## Что мне стоит изучить

1. Граница доверия (trust boundary): external input/diagnostics проверяются перед
   использованием и не пересекают public DTO boundary автоматически.
2. HTTP problem details (RFC 9457): один machine-readable error representation;
   status/code для логики, title/detail для пользователя.
3. Момент времени (Instant): UTC point вместо wall-clock LocalDateTime без offset.
4. Определённый порядок (deterministic ordering): unique secondary key нужен для
   стабильных equal-sort pages; это не snapshot isolation.
5. Проверка contract drift: routes, DTO и реальный HTTP response должны меняться
   вместе с версионируемой схемой, иначе tests отклоняют изменение.

## Production considerations

Перед rollout: backup и stopped writes для V22, establish historical timezone для
любого иного populated environment, qualify provider/TLS/runtime roles, edge rate
limits/CORS и operational tracing. Не смешивать old LocalDateTime binary и new
Instant binary на этих columns. Full commercial release остаётся отдельным gate.

**Подтверждённые факты:** commands и results таблицы, route/DTO inspection, security
negatives и PostgreSQL fixtures. **Инженерные выводы:** shared factory уменьшает
representation drift, DTO privacy и Instant исправляют конкретные boundaries.
**Assumptions:** demo legacy wall times интерпретируются как UTC с согласованным
отсутствием production history; внешняя инфраструктура требует отдельной проверки.
