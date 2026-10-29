# Spring 4 migration и подготовка задания 11

Дата начала: 2026-10-15. Исходный HEAD `4833e92571886d2f698061b2195e1c89ee4d2800`,
ветка `mvp`, исходное working tree чистое. Отчёт завершён2026-10-16. Пользователь отдельно поручил migration после10.

## Что было сделано

Spring Boot3.5.16/Spring6.2.19 заменены согласованным Boot4.0.8 BOM: Spring7.0.9,
Tomcat11.0.27 (security patch поверх BOM11.0.24), Hibernate7.2.24.Final, Spring Security7.0.7, Flyway11.14.1,
Testcontainers2.0.5, JUnit6.0.3, Mockito5.20.0; Java21 сохранена.
Перенесены modular starters и imports. Schema и source policies не менялись.
Для11 подготовлен [точный handoff с gaps](prompt11-readiness.md), реализация11 не начата.

## Как это работает и архитектурное решение

Зависимости согласует Boot BOM — набор совместимых версий framework/libraries.
Старая принудительная версия Tomcat10 заменена на11.0.27: Boot4 требует Servlet6.1/Tomcat11.
Flyway получил dedicated starter; test starters webmvc/security/data-jpa включают нужные
auto-configurations Boot4. Переименование Testcontainers artifacts следует его BOM2.

Альтернатива «подменить spring-framework.version на7 внутри Boot3» отвергнута: она смешивает
несовместимые поколения. Boot4.1.1 также опубликован; выбрана минимальная stable4.0.8 ветка,
уже содержащая исправления, чтобы не добавлять независимые изменения4.1 в security migration.
Suppression advisories, Enterprise-only6.2.20 и milestone4.2 не использовались.

Jackson2.21.7 сохраняется через официальный `spring-boot-jackson2` compatibility module.
Причина: existing wire DTO, source canonicalization и private persisted snapshot schema1
опираются на Jackson2. Одновременная смена JSON generation увеличила бы риск нарушения
immutable history/digests. `spring.jackson2.time-zone=UTC` и явный preferred converter
сохраняют поведение; `JsonCompatibilityTest` проверяет именно фактический MVC converter.
Это **переходный deprecated module**, не обещание вечной поддержки. До удаления module
нужна отдельная Jackson3 migration с golden wire/snapshot/digest fixtures и explicit defaults;
задание11 не требует такой смены. Jackson3, приходящий со starter, обновлён согласованным Jackson BOM3.1.7.

Spring7 перестал автоматически выдавать `ProblemDetail.type=about:blank`. Targeted suite
воспроизвёл нарушение existing OpenAPI. `ApiProblems.create` теперь явно задаёт тот же URI
для filter/MVC ошибок. Existing assertions не ослаблены. Spring Security7 также добавлял request-derived `resource_metadata` URL в Bearer challenge.
Existing unit tests воспроизвели отличие; `ApiSecurityErrors` теперь выдаёт только фиксированные
RFC6750 значения `Bearer`, `invalid_token`, `invalid_request` и прежние status400/401.
Никакие host/context/exception values не участвуют в header.
Новый negative HTTP test также воспроизвёл неожиданно публичный discovery endpoint (200).
В Security7.0.7 `OAuth2ResourceServerConfigurer` безусловно добавляет
`OAuth2ProtectedResourceMetadataFilter` перед authorization и не имеет disable method.
`SecurityConfiguration` строит тот же `DefaultSecurityFilterChain`, исключая ровно этот
optional discovery filter через public chain constructor. Все остальные filters сохраняют
порядок и request matcher. Отдельная deny-chain/URL blocker потребовала бы дублирования
security config; upgrade всей ветки4.1 только ради нового API расширил бы migration scope.
HTTP regression требует anonymous401 и authenticated404, поэтому будущий framework change
не сможет незаметно включить discovery. Это удаление необязательной публичной возможности,
а не отключение authentication/authorization защиты. Это форматирование ошибки,
а не новый authentication protocol: JWT validation и security guards сохранены.
Добавлены unit/HTTP regression tests подставленного host и закрытого metadata endpoint.
Order admission filter теперь
берётся из `SecurityFilterProperties`, сохраняя authentication перед body admission.

## Runtime Flow

```text
Bearer request → Spring Security7 → bounded MultipartAdmissionFilter
 → MVC + reviewed Jackson2 DTO conversion → secured use case → application/domain
 → Spring Data/Hibernate7 → PostgreSQL (existing Flyway V1–V43 + runtime grants)

AI submission → captured operation/Job → fenced worker
 → physical/model I/O вне DB transaction → fresh access/source/lease checks
 → atomic result/receipt/Job publication → отдельный human review/approval
```

## Security Review

**Threat:** vulnerable Spring MVC dependencies (CVE-2026-47890, CVE-2026-47884).
**Protection:** официально исправленный Spring7.0.9 через Boot BOM; no scanner suppression.
**Verification:** fresh SBOM/OSV evidence и resolved version inventory, результаты ниже.
Прямых `XsltView`/SSE-fragment flows в product source не найдено; это не заменяет обновление.

**Threat:** BOLA/role escalation либо resource exhaustion при изменении security/filter chain.
**Protection:** existing actual OWNER membership/tenant checks, bearer-only stateless auth,
deny-by-default, bounded request admission; перенесён API filter-order constant; исключён только optional public metadata filter.
**Verification:** SecurityIntegrationTest, ApiSecurityErrorsTest (включая forged host/header),
MultipartAdmissionFilterTest и full negative contracts.

**Threat:** JSON default change ломает receipts, citations, UTC или safe errors.
**Protection:** explicit reviewed mapper/timezone, unchanged snapshot version/checksums,
controlled ProblemDetail type; no polymorphic/default typing activation.
**Verification:** JsonCompatibilityTest, OpenApiContractTest, mixed evidence and AI contracts.

**Threat:** Hibernate/driver upgrade меняет locks, transactions, runtime grants или publication.
**Protection:** existing parent-first locks, lease/attempt fences, atomic TX, SQL triggers/FKs,
production `validate`, separate migration/runtime roles; V1–V43 unchanged.
**Verification:** real PostgreSQL concurrency, runtime-role, populated upgrade suites and gates.

Новые model egress, fetch, shell execution, public/debug endpoints, credentials, logging
user content или production activation не добавлены. Unknown cost/provider outcome остаётся unknown.

## Тесты и фактические результаты

Предварительные запуски:

- `mvnw.cmd --batch-mode --no-transfer-progress test-compile`: первый запуск выявил перенос
  `DEFAULT_FILTER_ORDER`; после корректировки повторная compilation прошла.
- `mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SecurityIntegrationTest,OpenApiContractTest,AiGenerationIntegrationTest,PostgreSqlAiGenerationTest,PostgreSqlPersistenceTest,ConfigurationProfilesTest test`:
  93 tests, 15 failures, 0 errors/skips. 14 failures воспроизводили ProblemDetail type regression,
  один — проверку прежнего имени UTC property. PG AI31, PG persistence8 и H2 AI28 прошли.
- После исправления `ApiProblems` и переноса property assertion:
  `mvnw.cmd --batch-mode --no-transfer-progress -Dtest=SecurityIntegrationTest,OpenApiContractTest,JsonCompatibilityTest,ConfigurationProfilesTest test`:
  **28 tests, 0 failures/errors/skips**, BUILD SUCCESS.

Первый full `spotless:apply clean verify`:1383 tests,2 failures,0 errors,3 Windows skips;
обе failures — новый framework `resource_metadata` в challenge. Остальные full scenarios
прошли; этот run **не считается qualified**. Исправление сохраняет прежние assertions,
добавляет два regression tests. Следующий targeted run25/1failure воспроизвёл
public discovery200; после исключения filter повторная qualification обязательна.
Два промежуточных test-compile запуска остановились на недостающих static imports новых
tests (`header`, `jwt`); imports исправлены без изменения assertions.
Финальный targeted security run:25/25 без failures/errors/skips, SBOM194 components.
Первый OSV scan этого BOM:10 unique advisories в Jackson core/databind3.1.5 и Tomcat11.0.24.
Spring7.0.9 findings отсутствуют. Jackson3 BOM поднят до3.1.7, Tomcat до11.0.27;
изменения patch-level, опубликованные artifacts сверены с Maven Central. Apache advisories
также показывают fixes11.0.26 сверх первоначального OSV report; выбрана актуальная11.0.27.
Первоначальные findings сохранены отдельно, suppressions не добавлены.
Финальные full/security/startup результаты ниже.

## Итоговая qualification

Команда финального run (2026-10-15 23:58:57 BST):

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
```

**BUILD SUCCESS**, 135 suites, **1385 tests:0 failures,0 errors,3 Windows filesystem skips**;
1382 выполненных tests прошли. Полный Linux suite **35/35 без skips** покрывает пропущенные
symlink cases. Spotless, compilation, JAR/repackage и JaCoCo gate PASS; LINE7586 covered /
437 missed (94.55%). Все required PostgreSQL/provider integration tests выполнены.

Дополнительные фактически выполненные команды:

```powershell
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-spring4-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-spring4-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\spring4-startup.ps1
```

Python scripts10/10 и renderer29/29 PASS; scripts/renderer исходники не менялись после
этих запусков. Linux gate повторён на final classpath:35/35. Оба report validators PASS:
PostgreSQL/MinIO/ClamAV/renderer/full evidence pipeline без required skips/failures.
Packaged JAR запускался на127.0.0.1:18099, `local`, worker-enabled=true:
health200; anonymous analysis/Strategy Job submit, Strategy read, Job status401;
metadata endpoint401 и ровно `WWW-Authenticate: Bearer`. Spawned process остановлен после
проверки. Startup stdout/stderr не показали application error.
Для воспроизведения startup: `java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --cadence.ai-jobs.worker-enabled=true --server.address=127.0.0.1 --server.port=18099`;
проверить `/api/v1/health`, защищённые routes и `/.well-known/oauth-protected-resource`.

491 source/build SHA-256 совпал до/после final full run. V1–V43 unchanged.
JAR SHA-256:`41d48bffe4772b6cfbbf31aca0f667fd0bfd8bb0f9192635953a56925fbde3a7`.
Evidence archive: ignored `var/spring4-verification/` (Surefire,JaCoCo,SBOM,OSV,manifest,summary).
Final log:`var/spring4-final-full.log`; Linux:`var/spring4-final-linux.log`;
startup:`var/spring4-startup-check.log`. Это local evidence, не committed secrets/artifacts.

## Commit / push / CI / следующий scope

Qualified local slice сохраняется commit `chore(platform): qualify Spring Boot 4 migration`.
Commit этой qualification доступен через `git log -1 -- docs/spring4-migration-report.md`.
Исходный parent4833e92 содержит completed10. Push/remote CI этого migration source не
выполнялись: существующая whole-issue policy требует отдельного разрешения для outgoing
ancestry unfinished scopes. Старый CI38810265479 остаётся исторически failed security,
а не переименовывается в success. Merge/deploy/issue closure не выполнялись.

**Можно начинать11 локально:** prerequisite10 и migration gates выполнены;
[remaining acceptance matrix](prompt11-readiness.md) сверена с кодом/ADR/draftA.
11/12/13, production adapters#120 и visual остаток#121 остаются незавершёнными.
Оба плана и progress ledger синхронизированы. Qualified local не означает production-ready.

## Fresh dependency scan

`mvnw.cmd --batch-mode --no-transfer-progress spotless:apply test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -DincludeTestScope=true -DoutputFormat=json -DoutputName=bom.cdx`
завершился BUILD SUCCESS; SBOM включает **194 runtime/test components**.

Официальный Windows OSV2.5.1 binary загружен из GitHub release, SHA-256 проверен по
published release digest до исполнения:
`25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.

```powershell
.\var\osv-scanner-2.5.1.exe scan source -L var/spring4-bom.cdx.json --all-packages --format=json --output-file=var/spring4-osv-results.json
python -B scripts/verify_dependency_scan.py var/spring4-bom.cdx.json var/spring4-osv-results.json
```

Обе команды exit0: **Verified194 Maven dependencies; no OSV vulnerabilities reported**.
Нет missing packages, severity exclusions или suppressions. Resolved: Spring WebMVC7.0.9,
Security7.0.7, Tomcat11.0.27, Jackson2.21.7 и3.1.7. SBOM/OSV saved в ignored `var`.
Отдельный tooling-only POM `var/spring4-tooling/pom.xml` разрешил Boot packaging plugin
через `mvnw.cmd --batch-mode --no-transfer-progress -f var/spring4-tooling/pom.xml org.springframework.boot:spring-boot-maven-plugin:4.0.8:help` (exit0).
Он не меняет source/target и не запускает второй test suite.

## Warnings

Compilation сообщает deprecated API (в том числе compatibility converter/Testcontainers и
existing storage configuration), existing unchecked operations в DatabaseEvidenceClockTest.
Spring предупреждает о будущем изменении поиска nested test configuration в7.1.
Hikari пишет connection warnings при остановленных disposable PostgreSQL containers и ещё
cached test contexts; такие же warnings присутствуют в qualified10 log. Они не засчитываются
как тестовые успехи: результат определяется Surefire/report gates. CycloneDX schema validator
выдал warnings `meta:enum`/`deprecated`; SBOM194 принят независимым inventory validator.
Эти сообщения не подавлялись. Hosted/production warning behavior требует отдельной проверки.

## Security Tests и Edge Cases

Проверяются anonymous401, foreign404, reserved roles, actual membership revocation,
malformed/unknown fields, pagination/oversized upload, SSRF/physical source corruption,
withdrawal/expiry/version races, mixed supported sources, two-worker claim, redelivery,
lost lease, post-dispatch unknown, immutable history, SQL/runtime bypass и migration upgrades.
Expected behavior и assertions сохранены из qualified10; new tests фиксируют JSON/security compatibility.

## Непроверенные аспекты / Production considerations

Local Docker qualification не доказывает hosted Ubuntu26 AppArmor enforcement или remote CI.
Live Clerk/provider, commercial privacy/retention/pricing, real adapter #120, visual remainder
#121, нагрузка, production migration/backup/restore/TLS/IAM остаются отдельными gates.
Проект не объявляется production-ready. Whole-issue push policy сохраняется; незавершённые
scopes не закрываются. Dependency scan — snapshot advisory database, не доказательство
отсутствия всех vulnerabilities и не замена SAST/container review.

## Что важно понять / Что изучить

1. **BOM (bill of materials):** согласует версии; одного исправленного Spring jar недостаточно.
2. **Wire compatibility:** клиент видит JSON contract, а не Java types; изменение default может
   сломать API даже при успешной компиляции. Поэтому type и timestamps проверяются на HTTP.
3. **Immutable provenance:** сохранённый snapshot/checksum — история решения; framework
   migration не должна переписывать её или выдумывать новые receipts.
4. **Lease fencing:** просроченный worker теряет право publish независимо от результата модели;
   новые persistence libraries проверяются настоящими конкурентными PostgreSQL tests.
5. **Qualification:** compile, local full tests, fresh vulnerability scan и exact-SHA remote CI
   являются разными доказательствами; один зелёный шаг не заменяет остальные.

## Источники

- [Official Boot4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- [Boot4.0.8 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.0.8/spring-boot-dependencies-4.0.8.pom).
- [Apache Tomcat11 security advisories](https://tomcat.apache.org/security-11.html).
- [Spring advisory CVE-2026-47890](https://spring.io/security/cve-2026-47890/).
- [Spring advisory CVE-2026-47884](https://spring.io/security/cve-2026-47884/).
- [Historical baseline09 CI](https://github.com/Sanyoghk/Cadence/actions/runs/38810265479):
  verify passed, dependency-security failed on Spring6.2.19; это не CI нового migration SHA.

## Факты / выводы / assumptions

Факты — версии resolved BOM, diff и результаты фактически завершённых проверок ниже.
Инженерный вывод — coherent Boot4 upgrade с сохранённым JSON contract уменьшает риск
относительно смешивания Boot3/Spring7. Assumptions: live/hosted production boundaries
требуют собственной qualification; local tests их не подтверждают.

## Изменённые файлы

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Квалификация migration и точка продолжения11, без закрытия незавершённых scopes. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Квалификация migration и точка продолжения11, без закрытия незавершённых scopes. |
| [README.md](../README.md) | Актуальная версия stack. |
| [docs/adr/0001-spring-boot-modular-monolith.md](adr/0001-spring-boot-modular-monolith.md) | Дополнение о framework upgrade; решение modular monolith сохранено. |
| [docs/dependency-security.md](dependency-security.md) | Новый stack/security evidence отделён от исторической Boot3 remediation. |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Квалификация migration и точка продолжения11, без закрытия незавершённых scopes. |
| [docs/prompt11-readiness.md](prompt11-readiness.md) | Фактически доставленные contracts10 и acceptance matrix оставшегося11. |
| [docs/spring4-migration-report.md](spring4-migration-report.md) | Решения migration, security review, точные проверки и оставшиеся ограничения. |
| [pom.xml](../pom.xml) | Согласованный Boot4 BOM, modular starters, Testcontainers2, Jackson2 compatibility; старый Tomcat pin заменён11.0.27; Jackson3 BOM3.1.7. |
| [src/main/java/com/cadence/http/ApiProblems.java](../src/main/java/com/cadence/http/ApiProblems.java) | Явный about:blank сохраняет существующий безопасный Problem Details contract. |
| [src/main/java/com/cadence/security/MultipartAdmissionFilter.java](../src/main/java/com/cadence/security/MultipartAdmissionFilter.java) | Boot4 SecurityFilterProperties API; сохранение и проверка порядка filters. |
| [src/main/resources/application.properties](../src/main/resources/application.properties) | Явный выбор Jackson2 converter и UTC configuration. |
| [src/test/java/com/cadence/application/EvidenceRegistrationQuotaTest.java](../src/test/java/com/cadence/application/EvidenceRegistrationQuotaTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/configuration/ConfigurationProfilesTest.java](../src/test/java/com/cadence/configuration/ConfigurationProfilesTest.java) | Перенос DataSource auto-configuration и проверки UTC property для Jackson2. |
| [src/test/java/com/cadence/configuration/JsonCompatibilityTest.java](../src/test/java/com/cadence/configuration/JsonCompatibilityTest.java) | Regression tests фактического MVC mapper, UTC JSON и сохранённой schema1. |
| [src/test/java/com/cadence/persistence/PostgreSqlAiGenerationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiGenerationTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlEvidenceRegistrationQuotaTest.java](../src/test/java/com/cadence/persistence/PostgreSqlEvidenceRegistrationQuotaTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlReviewConcurrencyTest.java](../src/test/java/com/cadence/persistence/PostgreSqlReviewConcurrencyTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/security/AiGenerationIntegrationTest.java](../src/test/java/com/cadence/security/AiGenerationIntegrationTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/security/BrandAnalysisFailClosedTest.java](../src/test/java/com/cadence/security/BrandAnalysisFailClosedTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/security/EvidenceScanIntegrationTest.java](../src/test/java/com/cadence/security/EvidenceScanIntegrationTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/security/JobApiIntegrationTest.java](../src/test/java/com/cadence/security/JobApiIntegrationTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/security/MultipartAdmissionFilterTest.java](../src/test/java/com/cadence/security/MultipartAdmissionFilterTest.java) | Boot4 SecurityFilterProperties API; сохранение и проверка порядка filters. |
| [src/test/java/com/cadence/security/SecurityIntegrationTest.java](../src/test/java/com/cadence/security/SecurityIntegrationTest.java) | Boot4 import и regression tests: discovery anonymous401/authenticated404, fixed challenge и ProblemDetail type. |
| [src/test/java/com/cadence/security/SocialEvidenceSecurityIntegrationTest.java](../src/test/java/com/cadence/security/SocialEvidenceSecurityIntegrationTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/security/StrategyApiFailClosedTest.java](../src/test/java/com/cadence/security/StrategyApiFailClosedTest.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/support/BrandIdentityApiContract.java](../src/test/java/com/cadence/support/BrandIdentityApiContract.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/support/EvidenceAdmissionApiContract.java](../src/test/java/com/cadence/support/EvidenceAdmissionApiContract.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/support/EvidenceAnalysisApiContract.java](../src/test/java/com/cadence/support/EvidenceAnalysisApiContract.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/support/IdentityEvidenceApiContract.java](../src/test/java/com/cadence/support/IdentityEvidenceApiContract.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/support/PdfReviewApiContract.java](../src/test/java/com/cadence/support/PdfReviewApiContract.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/support/SocialReviewApiContract.java](../src/test/java/com/cadence/support/SocialReviewApiContract.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/support/StrategyApiContract.java](../src/test/java/com/cadence/support/StrategyApiContract.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [src/test/java/com/cadence/support/UrlReviewApiContract.java](../src/test/java/com/cadence/support/UrlReviewApiContract.java) | Imports modular test annotations Boot4; прежние behavioral assertions сохранены. |
| [docs/astra-medium-mvp-prompts.md](astra-medium-mvp-prompts.md) | Ссылка задания11 на актуальную readiness matrix. |
| [src/main/java/com/cadence/security/ApiSecurityErrors.java](../src/main/java/com/cadence/security/ApiSecurityErrors.java) | Фиксированный безопасный Bearer challenge без request-derived metadata URL. |
| [src/test/java/com/cadence/security/ApiSecurityErrorsTest.java](../src/test/java/com/cadence/security/ApiSecurityErrorsTest.java) | Negative test подставленного host/context и сохранение прежних challenge assertions. |
| [src/main/java/com/cadence/security/SecurityConfiguration.java](../src/main/java/com/cadence/security/SecurityConfiguration.java) | Исключён только автоматически добавленный public discovery filter; прежние authentication/authorization guards сохранены. |
