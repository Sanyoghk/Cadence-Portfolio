# #121 — explicit PDF admission history

Дата: 2026-10-14. Branch `mvp`; исходный HEAD `a972291bc093b0fbe079629b76269ccc0f88120c`, clean tree.
Это законченный consent/snapshot/withdrawal slice; whole #121 остаётся IN PROGRESS.
Local qualification завершена; фактические результаты ниже.

## Что было сделано

OWNER может явно подтвердить exact reviewed PDF, получить current physical snapshot и отозвать
consent. Сохраняются immutable text/audit и original artifact/latest scan provenance.
PDF source остаётся PENDING; model consumption этим slice не включается.

## Как это работает

`PdfEvidenceReviewController` принимает `AdmitPdfEvidenceRequest`: exact UUIDs, versions,
checksums, scanExpiresAt/policyVersion. Неизвестные поля отвергаются; actor/time/text выбирает
server. `ReviewPdfEvidenceUseCase` объединяет preview/admission/snapshot в одном двухслотовом
bulkhead. Private generic verified helper выполняет capture → bounded physical read → finalize.
`PdfReviewTransactions` повторяет actual active OWNER/membership и source/artifact/scans/ledger
checks под Account → Workspace → Brand → Evidence → Quarantine locks в новых transactions.
External I/O без transaction; deadline30s, size/header/SHA-256 и snapshot equality обязательны.

`EvidenceAdmission` имеет separate TEXT/PDF policy shape. PDF stores artifact/original/latest
scan IDs, renderer и original scan expiry; GET snapshot проверяет именно эту receipt lineage,
text/checksums/current policy и expiry. Fresh CLEAN rescan требует нового consent; earlier
receipt immutable. Expiry сохраняется в PostgreSQL microseconds, без продления current policy.
`ReviewEvidenceUseCase.withdraw` используется также для PDF; stale/archive не препятствуют
withdrawal, но actual access обязателен. Любой withdrawal блокирует sourceVersion целиком.

## Архитектурное решение

Shared immutable history позволяет сохранить единый withdrawal invariant и cap1000/evidence.
Alternative отдельной PDF table дублировала бы эти правила. V36 вводит nullable PDF fields и
строгий policy discriminator; TEXT rows получают NULL, а прежние content/audit/UUID не меняются.
Отдельные partial UNIQUE indexes защищают TEXT context и PDF context/artifact/latest scan/expiry.
Общий source history index сохраняет эффективный cap/withdrawal lookup без policy predicate.
JPA/H2 nullable UNIQUE не заменяет эти production constraints; domain locks и PostgreSQL
tests обязательны. Старый PreviewPdfEvidenceUseCase переименован по расширившейся ответственности.

SQL invoker guard сохраняет common account/tenant/lifecycle/version/checksum/time/withdrawal/
cap checks и добавляет PDF artifact/scan/Job/ledger/expiry checks. UTF-16 bound16000 проверяется
и в domain, и SQL (supplementary characters считаются двумя code units). SQL не может проверить
private bytes: этот слой доверяет backend orchestration; application witness проверяет storage.
No dependencies/roles/grants/SecurityFilterChain changes. V1–V35 checksums untouched.
Model selection и V34/V35 generation/Identity guards остаются TEXT-only; DTO явно возвращает
`aiConsumptionEnabled=false`. Future integration обязана выполнить отдельные PDF consumer fences.

## Security Review

Threat: forged tenant/actor/selectors, неожиданное client text/role/reviewer поле.
Protection: principal actor, actual DB OWNER/member, scoped queries, exact selector match,
separate validated DTO/unknown-field rejection; server-owned audit/content.
Verification: shared H2/PG authorized/anonymous/VIEWER/forged actor/foreign receipt and selectors
tests, 400 unknown/missing fields, immutable original receipt audit.

Threat: stale or substituted scan/artifact, physical byte loss, changed access/source during I/O.
Protection: original + latest CLEAN binding, expiry exclusive, referenced ledger/server key,
physical size/header/SHA-256, fresh finalize/deadline, exact request expiry/policy.
Verification: source/archive/membership/latest scan mutation callbacks, corrupt storage no
receipt, expired snapshot, domain exact expiry/no extension; existing preview graph negatives.

Threat: replay, SQL history rewrite/forged content/scan/policy/expiry, consent race/abuse.
Protection: SQL policy/FK/UNIQUE/invoker guard, immutable history/runtime no UPDATE/DELETE,
parent-first locks, withdrawal sourceVersion fence, cap1000 и shared two-slot bulkhead.
Verification: real restricted runtime INSERT/withdrawal success + SQLState42501 rewrite/delete,
SQL forged metadata rejection, cap1000/1001 application/SQL, concurrent admission exactly1,
held admission/withdrawal transactions в обеих очередностях.

Threat: PDF prompt instructions или data accidentally entering legacy model consumer.
Protection: no model call; existing query/type/policy publication guards remain TEXT-only.
Verification: PDF absent from findCurrentText, analyzer not invoked, old TEXT snapshot denies PDF;
V34/V35 guards inspected, full old analysis/Identity regression suite выполняется.
Text остаётся untrusted plain text; frontend обязан исключать raw HTML rendering.

## Изменённые файлы

- `src/main/java/com/cadence/domain/EvidenceAdmission.java`: nullable immutable PDF fields/constructor/currentness.
- `src/main/java/com/cadence/domain/PdfReviewToken.java`: exact bounded review selectors, expiry/policy binding.
- `src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java`: PDF replay lookup.
- `src/main/java/com/cadence/application/services/PdfReviewTransactions.java`: admission/snapshot capture/finalize/withdrawal checks.
- `src/main/java/com/cadence/application/usecases/PreviewPdfEvidenceUseCase.java`: переименован в `ReviewPdfEvidenceUseCase.java`, shared read/bulkhead orchestration.
- `src/main/java/com/cadence/application/results/PdfAdmissionDetails.java`: private receipt metadata, consumption disabled flag.
- `src/main/java/com/cadence/application/results/PdfAdmissionSnapshot.java`: current receipt + original text.
- `src/main/java/com/cadence/presentation/dto/AdmitPdfEvidenceRequest.java`: validated selectors, unknown fields rejected.
- `src/main/java/com/cadence/presentation/PdfEvidenceReviewController.java`: new POST/GET no-store operations.
- `src/main/resources/db/migration/V36__pdf_evidence_admission.sql`: policy shape, FK/indexes/invoker guard, no backfill.
- `src/main/resources/openapi/cadence.json`: new operations/DTOs; old TEXT schemas preserved.
- `src/test/java/com/cadence/support/PdfReviewApiContract.java`: shared real register/extract/consent/lifecycle/API/race scenarios.
- `src/test/java/com/cadence/persistence/PostgreSqlPdfReviewTest.java`: SQL/runtime/cap/concurrency/withdrawal ordering tests.
- `src/test/java/com/cadence/persistence/PostgreSqlPdfAdmissionMigrationTest.java`: populated V35 upgrade keeps TEXT/withdrawal audit and NULL PDF fields.
- `src/test/java/com/cadence/domain/PdfAdmissionDomainTest.java`: exact expiry/no extension/invalid reviewer/domain invariants.
- `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java`: strict36 migrations/version inventory.
- `src/test/java/com/cadence/persistence/PostgreSqlEvidenceAdmissionMigrationTest.java`: every old field unchanged plus all new fields NULL.
- `src/test/java/com/cadence/presentation/OpenApiContractTest.java`: new DTO schema alignment.
- `scripts/verify_postgres_report.py`, `scripts/test_verify_postgres_report.py`: PDF migration mandatory/no false green.
- `docs/adr/0018-pdf-admission-history.md`: architecture/threat decisions.
- `docs/pdf-review-api.md`: explicit consent/snapshot/withdrawal contracts.
- `DEVELOPMENT_PLAN.md`, `DEVELOPMENT_PLAN_RU.md`: completed PDF history scope, remaining consumers.
- `docs/verification.md`, `docs/issue-sync.md`: qualification/current external sync status after verification.
- `docs/issue-121-pdf-admission-slice.md`: этот отчёт.

## Runtime Flow

```text
POST exact selectors / GET scoped receipt
    ↓ Controller validation / principal actor
    ↓ ReviewPdfEvidenceUseCase / nonblocking shared slot
    ↓ capture transaction / actual account + parents + source + scans + ledger
    ↓ consent/replay/withdrawal/selectors checks
    ↓ commit / private bytes read / size + PDF header + SHA-256
    ↓ fresh finalize transaction / same graph + access + deadline
    ↓ append immutable EvidenceAdmission OR return verified current snapshot
    ↓ no-store DTO / finally releases slot
```

Withdrawal: principal → actual account/parents/Evidence locks → scoped admission → append
EvidenceAdmissionWithdrawal; storage read не нужен, approved/history никогда не переписываются.

## Тесты

Фактически выполненные команды:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply -DskipTests compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PdfReviewApiIntegrationTest,PostgreSqlPdfReviewTest,OpenApiContractTest,PostgreSqlEvidenceAdmissionMigrationTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PdfAdmissionDomainTest,PdfReviewApiIntegrationTest,PostgreSqlPdfReviewTest,PostgreSqlPdfAdmissionMigrationTest,OpenApiContractTest,PostgreSqlEvidenceAdmissionMigrationTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\pdf-admission-startup.ps1
git diff --check
```

Final clean verify BUILD SUCCESS `2026-10-14T01:24:19+01:00`,7:47min:
114 suites/1024 tests,0 failures/errors,3 Windows filesystem skips;1021 executed pass.
H2PDF34/34,PGPDF42/42,PDF domain2/2,populated V35 migration1/1,OpenAPI3/3.
JaCoCo LINE5841covered/322missed =94.78%;all coverage gates,Spotless/compilation/JAR PASS.
Mandatory PostgreSQL (включая новый migration suite), MinIO/ClamAV/renderer/full pipeline
reports PASS без skipped required tests. Python10/10,renderer29/29,Linux35/35 без skips.
Linux filesystem run закрывает3 Windows platform cases; storage source не менялся после него.
Packaged local health200,anonymous previewGET/admissionPOST/snapshotGET401; helper stopped own
process. Startup helper/raw logs в ignored `var/`, local H2/fake не является live qualification.
Final run включает archive/source/expiry workflow cases, renamed use-case, expiry-context
regression и общий history lookup index; qualification относится к этому source.

Первый full run остановлен после review: replay key должен включать expiry для explicit
re-review после изменения configured max-scan-age. Index/repository исправлены; новый H2/PG
regression test меняет policy age и проверяет новый receipt без изменения original expiry.
Повторный clean verify квалифицирует именно окончательный source; interrupted run не pass.
Следующий exploratory full run также остановлен до конца для сохранения unrestricted history
lookup index после замены старого UNIQUE на partial indexes. Финальный run включает этот index,
его migration assertion и indexed ledger lookup; оба interrupted runs не qualification pass.
Первый targeted68 имел4 assertion failures: globally ignored unknown field и lost test
SecurityContext после MockMvc. DTO strengthened, fixture principal restored; assertions не
ослаблялись. Повтор73/73; следующий78/78 включает expiry/policy selectors и runtime/races/cap.
Исторический PDF preview run не выдаётся за этот новый запуск.
Warnings: existing S3 deprecated API, JVM bootstrap classpath/CDS, Commons Logging discovery,
Hikari validation/connection warnings после stopped test containers; reports/gates PASS.
Document links/diff проверены; tracked V1–V35 migration diff пуст. Единственная новая migration V36.

## Security Tests / Edge Cases

Explicit receipt201/current snapshot200; anonymous401/VIEWER403/forged actor403; forged UUIDs,
checksum/version mismatch409, unknown/missing fields400; no client text/audit; replay409.
Latest fresh CLEAN rescan preserves original scan/history, requires new consent; withdrawal
blocks all contexts/scans. Archived source still withdrawable; expired/exact expiry not usable.
Corrupted/missing physical bytes never create receipt. Revocation/source/archive/scan changes
during I/O reject commit/return. SQL/runtime rewrite, invalid policy/expiry/scans denied;
cap1000 accepted/1001 denied; concurrent same consent exactly1; both lock orderings exercised.

## Непроверенные аспекты

Live provider/Clerk/hosted storage/renderer qualification, privacy/retention, fresh OSV/SAST/image
scans и distributed/user quotas не выполнялись. Process slots не являются cluster-wide budget;
deadline не отменяет произвольный hung adapter, transport timeouts обязательны в production.
DB guard не видит private bytes. Backend/runtime identity trusted; external storage deletion
после physical witness не контролируется DB locks. Future consumers обязаны повторить проверки.
Нет PDF model/publication/Identity integration, URL/image/social policy или new READY state.

## Что мне важно понять

Consent фиксирует конкретный проверенный текст и provenance, а не разрешает всё содержимое
Brand навсегда. Новый scan/version context требует нового confirmation. Withdrawal нельзя
обойти новым scan/parent context; original audit остаётся readable immutable history.
Транзакционные locks сериализуют конкурирующие commits, но private I/O выполняется отдельно:
поэтому после него backend повторяет актуальность всех prerequisites.

## Что мне стоит изучить

1. Разделение вариантов данных (discriminated union): policy отделяет TEXT/PDF shapes без backfill.
2. Частичный unique index (partial unique index): разные immutable review contexts в PostgreSQL.
3. Происхождение данных (provenance): original artifact/scan и latest scan — разные immutable links.
4. Гонка проверки/использования (TOCTOU): fresh finalize после external I/O защищает commit.
5. Минимальные привилегии (least privilege): runtime может append consent/withdrawal, не rewrite audit.

## Production considerations / Git

Следующий slice: PDF-aware server input selection и generation/publication/Identity approval
fences, затем URL/images/social eligibility и APPROVED Identity→Strategy. Real adapters#120,
AI Jobs/budgets/full Playbook — отдельные scopes. Whole #121/#107 open; local commit после
qualification, push/exact-source-SHA CI после whole issue; merge/deployment отдельно.
V36 выполняет transactional ALTER/CHECK/index creation; на production-size table требуется
проверить lock duration, backup и rollout migration-before-app. Populated fixtures не являются
load benchmark. Recovery — reviewed forward fix, без удаления immutable consent history.
Slice сохраняется local commit `feat: persist reviewed PDF admission history (#121)`.
Новый progress payload не покрывался старым PDF preview approval; пользователь отдельно
подтвердил «Разрешаю оба admission updates» согласно прежнему требованию automatic review.
Feature commit: `730a491c7cb0dcb03cff4cf9efbf42af9166a2ce`.
[Точный progress draft](issue-121-pdf-admission-sync-draft.md) добавлен в descriptions #121/#107.
Readback: exact old body + approved block; title/state/labels сохранены, оба issues open.
External sync blocker снят. Docs-only follow-up добавляет draft и уточняет report/issue-sync; product
source/tests не меняются, прежний full run не выдаётся за новый запуск.

Подтверждённые факты: inspection и actual commands/results qualification.
Инженерные выводы: layers/locks/constraints закрывают локально проверенные attack vectors.
Assumptions: trusted backend/DB identity и configured production transport/storage policy;
semantic truth/consent UI behavior/live commercial readiness требуют отдельной qualification.
