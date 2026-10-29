# #121 — reviewed TEXT provenance для Identity

Дата: 2026-10-13. Начало: `mvp`, HEAD `eee0059908adab4356c5b096b820ffaf9751dd02`, clean tree.
Это законченный TEXT Identity slice. Whole #121 остаётся IN PROGRESS.

## Что было сделано

Добавлен explicit copy из evidence analysis receipt в Identity DRAFT. Original input digest,
admission links и exact quotations сохраняются через immutable analysis receipt; copier
записывается отдельным immutable audit field. Созданы scoped API для copy и provenance read.
Approval проверяет source eligibility снова, включая inherited origin у approved-edit branches.
Legacy declared-only copy и manual workflow сохраняют свои contracts.

## Как это работает

POST `from-evidence-analysis` принимает только analysisId/expectedVersion. Actor — из principal,
actual active OWNER/membership — из БД. Account → Workspace → Brand locks сериализуют операцию
с revocation/withdrawal. Source graph проверяется относительно original admissions и publication
lineage: Brand version = receipt.brandVersion+1, Workspace version совпадает. TEXT source
version/checksum/text/policy должны совпадать; withdrawal sourceVersion запрещает copy/approval.
Input digest пересчитывается из immutable declared snapshot и selected admissions, exact quotes
проверяются исходным port contract. Content копируется из current signature и сверяется с
original output checksum. Identity создаётся DRAFT; approval выполняется отдельным запросом.

Completed receipt не получает искусственный TTL: generation deadline уже проверен при
SUCCEEDED. При copy и approval защищает current version/access/source fence.
GET provenance возвращает до 10 original quotes, source checksum, origin Identity/analysis ID,
copiedBy и withdrawal flags. Manual/declared origins дают empty evidence metadata. Archive/
withdrawal сохраняют readable history для current active OWNER/member, но блокируют новые writes.

Manual DRAFT edits сохраняют original receipt, не утверждая, что edited content создан моделью.
После APPROVED edit новая manual DRAFT связана через revisedFromId. Original source ищется
по same-Brand цепочке строго меньших revision versions (до 1000 hops). Поэтому branching
не позволяет обойти withdrawal. Старое APPROVED состояние после withdrawal не переписывается.
Future Strategy consumer обязан проверять eligibility заново; эта integration ещё не выполнена.

## Архитектурное решение

`IdentityEvidenceUseCase` — authorize/lock/copy/read orchestration. Внутренняя policy
`EvidenceIdentitySourcePolicy` — bounded origin resolution и source/citation validation.
Existing `BrandIdentityUseCase.approve` использует ту же policy после actual actor/parent/revision
locks. Domain factory различает evidence/declared modes и требует actor для evidence origin.
Domain не импортирует application DTO. Quotations не дублируются: они уже immutable в receipt.

V35 добавляет nullable `analysis_copied_by`, не выдумывает legacy audit и не меняет V1–V34.
Invoker triggers дублируют copy/approval/lineage/source checks; новый trigger выполняется до
старого parent guard. Runtime UPDATE copiedBy не выдаётся. Existing content checksums,
approved immutability и collection guards остаются активными.

Alternative: расширить legacy copy молча — меньше API, но меняет source contract.
Дублирование quotes в Identity усложнило бы immutable history. Выбраны explicit endpoint и
receipt links. Trade-off: отдельный bounded lineage lookup; длинную lineage нужно измерить
под production нагрузкой. Строгая current parent fence может потребовать нового analysis
после parent changes; старый receipt сохраняется как history.

## Security Review

Защищаем tenant content/quotes, approval/audit и provenance. Caller — current OWNER/member.
Trust boundaries: principal → actual DB rights; stored receipt graph → Identity;
manual revision lineage → approval; historical quotes → JSON DTO.

**Threat: IDOR/BOLA, stale principal, forged actor/role.**
Protection: scoped Brand/Identity/receipt queries, principal actor binding и actual account/
membership rechecks. HTTP DTO не содержит copiedBy/role. Verification: unauthorized/VIEWER,
foreign Identity/attempt/scope, inactive/revoked account, forged actor и mass assignment tests.
Concurrent copy ждёт Account revocation, не удерживая Workspace lock, затем получает отказ.

**Threat: withdrawn/stale source copy или approval.**
Protection: reviewed locks, source/version/checksum/digest/policy checks; PostgreSQL guards
дублируют lifecycle. Verification: source/parent changes, withdrawn copy/approval, SQL bypass
denials и обе очередности approval/withdrawal race. Если approval фиксируется первым,
history остаётся APPROVED, но subsequent source read отмечает withdrawal.

**Threat: laundering через approved-edit branch.**
Protection: inherited same-Brand origin, decreasing revision versions и bounded traversal
в application/SQL. Verification: branch approval после withdrawal rejected; eligible branch
approved; original citations сохраняются, manual edits не получают выдуманного receipt.

**Threat: forged audit/history, provenance lost при migration.**
Protection: immutable copiedBy/analysis FK, mode-aware factories/triggers, append-only original
source/citation ledger и restricted runtime grants. Verification: SQL rewrite denied;
runtime может approve valid source, но не UPDATE copier; populated V34→V35 upgrade сохраняет
content, approval/token и legacy null audit без fabricated evidence rows.

**Threat: unsafe rendering, injection, excessive exposure/resource exhaustion.**
Protection: plain JSON DTO/no-store, parameterized queries/static SQL, max 5 sources/20000
UTF-8 bytes/10 bounded quotations и max1000 lineage hops. Нет новых URL/file/shell/provider
calls, dependencies, secret values или token logging. Verification: OpenAPI/null/empty schemas,
exact source/citation contract regression suite, immutable list/digest bounds из previous slice.
Exact quote не доказывает truth/relevance; frontend должен отображать quotes как untrusted text.
Global rate/cost controls и live provider defenses не квалифицированы этим slice.

SecurityFilterChain/CSRF/CORS не менялись: explicit Bearer header, без cookie sessions.
Если application layer обходится, SQL guards всё ещё блокируют указанные invariants.
Runtime DB credentials предполагаются доступными только trusted backend, не клиенту.

## Изменённые файлы

| File | Назначение |
|---|---|
| `src/main/java/com/cadence/application/repositories/BrandAnalysisCitationRepository.java` | Ordered bounded quotation reads из immutable receipt. |
| `src/main/java/com/cadence/application/results/IdentityEvidenceProvenance.java` | DTO origin/audit/input digest/quotes/withdrawal flags. |
| `src/main/java/com/cadence/application/services/EvidenceIdentitySourcePolicy.java` | Source validation, same-Brand origin resolution и provenance mapping. |
| `src/main/java/com/cadence/application/usecases/IdentityEvidenceUseCase.java` | Actual actor authorization, Account-first copy и scoped historical read. |
| `src/main/java/com/cadence/application/usecases/BrandIdentityUseCase.java` | Approval проверяет direct/inherited evidence origin. |
| `src/main/java/com/cadence/domain/BrandAnalysisAttempt.java` | Read-only immutable declared snapshot getters для digest recheck. |
| `src/main/java/com/cadence/domain/BrandAnalysisCitation.java` | Read-only quotation getters. |
| `src/main/java/com/cadence/domain/BrandIdentity.java` | Immutable copier и separate evidence/declared factories. |
| `src/main/java/com/cadence/presentation/IdentityEvidenceController.java` | Explicit copy/read routes, principal actor, no-store. |
| `src/main/java/com/cadence/presentation/dto/EvidenceIdentityFromAnalysisRequest.java` | Required analysisId/expectedVersion, без writable actor/content. |
| `src/main/resources/db/migration/V35__evidence_identity_provenance.sql` | Nullable audit, mode/source/copy/approval/lineage invoker guards. |
| `src/main/resources/openapi/cadence.json` | New routes, request/provenance schemas, nullable/empty response contract. |
| `docker/postgres/runtime-permissions.sql` | Документирован immutable V35 field, без дополнительного UPDATE grant. |
| `src/test/java/com/cadence/support/IdentityEvidenceApiContract.java` | Shared HTTP/use-case/security/manual/lineage/withdrawal scenarios. |
| `src/test/java/com/cadence/security/IdentityEvidenceApiIntegrationTest.java` | H2 execution shared contract. |
| `src/test/java/com/cadence/persistence/PostgreSqlIdentityEvidenceTest.java` | PostgreSQL contract, SQL/runtime guards и actual races. |
| `src/test/java/com/cadence/persistence/PostgreSqlIdentityEvidenceMigrationTest.java` | Populated V34→V35 no-backfill/content/audit preservation. |
| `src/test/java/com/cadence/domain/EvidenceIdentityModeTest.java` | Mode/actor/checksum factory negative tests. |
| `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java` | Exact migration inventory35. |
| `src/test/java/com/cadence/presentation/OpenApiContractTest.java` | New DTO fields/schema alignment. |
| `scripts/verify_postgres_report.py` | Mandatory new PostgreSQL reports, no skips/missing suites. |
| `scripts/test_verify_postgres_report.py` | False-green regression gates для новых suites. |
| `docs/adr/0016-evidence-identity-provenance.md` | Architecture/threat/lineage decision. |
| `docs/adr/README.md` | ADR discovery. |
| `docs/brand-identity-api.md` | Copy/read/approval/source/lineage semantics. |
| `docs/evidence-analysis-api.md` | Updated Identity continuation contract. |
| `docs/issue-121-identity-evidence-slice.md` | Этот report. |
| `docs/verification.md` | Latest qualification и production boundaries. |
| `docs/issue-sync.md` | Current issue status/continuation. |
| `DEVELOPMENT_PLAN.md` | TEXT stage progression, remaining non-TEXT/Strategy. |
| `DEVELOPMENT_PLAN_RU.md` | Согласованная русская версия plan. |

Ignored var logs/launcher и generated target reports/JAR не входят в committed source.

## Runtime Flow

```text
POST from-evidence-analysis → Bearer/OWNER → validated request + principal actor
    → IdentityEvidenceUseCase (REQUIRES_NEW/READ_COMMITTED)
    → actual Account → Workspace → Brand locks
    → scoped receipt → source policy → locked Evidence / withdrawals / digests / exact quotes
    → domain evidence factory → DRAFT + copiedBy → SQL guards/content seal → commit
    → existing Identity DTO + Location + no-store

POST approval → actual Account → Workspace → Brand → Identity locks
    → same-Brand inherited origin → source fence → committed content approval
    → PostgreSQL source/approval guards → commit

GET evidence-provenance → scoped Identity + actual OWNER/membership
    → bounded origin → immutable receipt links/quotes + withdrawal flags → DTO + no-store
```

## Тесты

Final BUILD SUCCESS: 2026-10-13T22:49:34+01:00, 4:47.
109 suites /938 tests,0 failures,0 errors,3 Windows skips;935 executed pass.
H2 Identity API17/17,PostgreSQL24/24,migration1/1,domain mode2/2.
PostgreSQL/MinIO/ClamAV/renderer/full pipeline mandatory gates PASS без skips.
Spotless/compilation/JAR packaging/JaCoCo PASS;LINE5498 covered/317 missed =94.55%.
Packaged local JAR: health200,anonymous copy POST401 и provenance GET401; процесс остановлен
в finally. Это startup/authentication smoke, не live Clerk/production qualification.

Фактически выполненные команды:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=IdentityEvidenceApiIntegrationTest,PostgreSqlIdentityEvidenceTest,PostgreSqlEvidenceAnalysisTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\identity-evidence-startup.ps1
git diff --check
git diff --cached --check
```

Первый targeted run: 57 tests,3 fixture/schema failures,39 Docker skips. Docker daemon был
выключен; запущен Docker Desktop Hidden, готовность29.8.1 подтверждена. Nullable output schemas
приведены к existing anyOf format; BOLA fixture теперь использует реальный foreign Identity
при legitimate scope. Повторный targeted run63/63, без skips. Assertions не ослаблялись.
Первый full run938 tests:0 failures,2 errors в mass assignment fixture,3 Windows skips.
После HTTP filter SecurityContext очищается: subsequent assertion перенесён в отдельный
authenticated GET вместо unauthenticated direct use-case call. Выполнен повторный full run.

Python10/10,renderer29/29,Linux35/35 без skips. Linux включает 3 Windows symlink scenarios;
наборы отдельно не суммируются как уникальные независимые cases. Existing CDS/deprecated S3/
unchecked test-code/Hikari disposed-container warnings не подавлялись. Negative configuration
test warnings не являются failure startup собранного application.

## Security Tests

Authorized copy/read/edit/approval; anonymous/VIEWER/foreign IDs; stale membership/inactive
account; forged actor and role; SQL copier rewrite/source withdrawal/lineage bypass;
restricted runtime grants; account revocation and обе approval/withdrawal race orderings.
Исторический legacy-copy evidence denial test сохранён и продолжает проходить.

## Edge Cases

- Empty manual evidence provenance — null metadata, zero quotes, без fabricated citations.
- Другой current OWNER может copy старый успешный receipt; original producer не переписывается.
  Inactive original producer не отзывает сам review автоматически; withdrawal — явный lifecycle.
- DRAFT manual edit сохраняет original checksum/provenance; approval подтверждает edited content.
- New approved-edit DRAFT наследует source через lineage; withdrawal блокирует его approval.
- Archived history readable; archived/stale source writes rejected.
- Repeated copy — новая revision, не idempotent; cap1000 и reviewed version allocator сохраняются.

## Непроверенные аспекты

Нет live AI/Clerk qualification, свежего OSV/SAST/container scan или hosted TLS/IAM/AppArmor/
commercial deployment проверки. Non-TEXT source policy, approved Identity→Strategy, AI Jobs,
budgets/rate/idempotency/full Playbook — отдельные scopes. 1000-hop lineage performance под
реальной нагрузкой отдельно не измерена; traversal bounded, но может иметь N+1 read cost.
Exact citations — provenance исходной generation, не семантическое доказательство edited content.
Privacy/retention/actor anonymization и DB/app clock synchronization требуют production review.

## Что мне важно понять

Сохранять ссылку на receipt недостаточно: между анализом и approval источник может быть отозван.
Проверка проводится на каждом моменте, где система создаёт новый trusted state.
Original model provenance и human-edited content различаются: old quote/history сохраняется,
но edited caption не объявляется автоматически поддержанным этой citation.
Lineage нужен не только для UI history: он также не позволяет забыть security boundary.

## Что мне стоит изучить

1. Происхождение данных (provenance): immutable input/quote/receipt links показывают original generation.
2. Ограждение публикации (publication fencing): source/access/version повторно проверяются при copy/approval.
3. Порядок блокировок (lock ordering): Account-first исключает типичный deadlock с revocation writers.
4. История происхождения revisions (lineage): same-Brand decreasing versions позволяют наследовать source safely.
5. Минимальные привилегии (least privilege): runtime может approve, но не переписывать copier/history.

## Production considerations и Git

#121/#107 остаются open; next — non-TEXT eligibility и APPROVED Identity→Strategy source binding.
Real adapters#120, AI Jobs/budgets/Playbook отдельно. Push и exact-source-SHA remote CI — после
whole issue; merge/deployment согласовываются отдельно. Slice сохраняется local commit
`feat: preserve evidence provenance in identity drafts (#121)`; actual SHA — в final/GitHub sync.

Подтверждённые факты: фактические inspection/tests и команды/results выше.
Инженерные выводы: independent application/SQL guards закрывают перечисленные attack vectors.
Assumptions: runtime DB identity trusted backend only; original review отзывается явно;
semantic correctness, hosted security и commercial operational limits ещё не квалифицированы.

## GitHub synchronization — approved и выполнена

Feature сохранена local commit `ea2117f40e3d90b7423dfa5f6ecb08d16ab66fcb`. Первоначально GitHub
descriptions #121/#107 не были синхронизированы: automatic approval review отклонила отправку internal
verification/architecture payload без отдельного подтверждения конкретных payload/destinations.
Повторный ответ reviewer явно запретил обход другим способом. External writes остановлены.
Implementation/test qualification завершена независимо от external записи.
Пользователь явно ответил «Разрешаю оба updates» для [точного draft](issue-121-identity-evidence-sync-draft.md)
и обоих destinations. Approved block добавлен в descriptions #121/#107. Readback подтвердил
exact body = previous body + approved block, неизменные title/state и open status.
Blocker снят; push/merge/deployment не выполнялись.

Docs-only follow-up изменяет этот report, `docs/issue-sync.md` и добавляет
`docs/issue-121-identity-evidence-sync-draft.md`. Product code/SQL не меняются; для follow-up
проверяются diff и document links, прежний full run не выдаётся за новый запуск.
