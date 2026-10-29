# Задание03 — scoped URL textual admission, #121

Дата: 2026-10-14. Baseline `54a0c3fe1f54f705eeebd65408f93e89426d4a3c`, `mvp`, исходный
working tree clean. ADR0021 P1–P3 действительно ACCEPTED; prerequisite02 не переизобретался.
Актуальные GitHub #107/#121 прочитаны; remote status отстаёт от local slices, whole issue open.
Статус: **03 QUALIFIED**. Implementation, full verification, security review и documentation завершены.

## Что было сделано

URL evidence получает собственный immutable acquisition snapshot, затем отдельный explicit
OWNER review/consent. Можно получить current verified snapshot и отозвать разрешение. Новый
review после изменения Brand/Workspace context или scan не переписывает старую историю.
URL consumption/model/citations/Identity — scope04 и не включены этим изменением.

## Как это работает

`UrlFetchWorker` сохраняет `UrlTextSnapshot` в transaction успешного fetch. Receipt связывает
registration checksum, original retained generation/bytes hash, captured/fetched/extracted time,
website-v2 metadata/warnings, compact `url-text-v1` projection/hash и original Job/attempt/lease.
Snapshot создаётся при fetch, а не задним числом при review. V39 не backfill legacy metadata.

`ReviewUrlEvidenceUseCase` вызывает capture в `UrlReviewTransactions`, читает bytes и повторяет
локальное извлечение вне DB transaction, затем выполняет fresh validation. Actual OWNER/member,
context/source versions, exact snapshot, current CLEAN/expiry, retained ledger и withdrawal
проверяются до и после I/O. На source максимум2MiB; review projection максимум20000 UTF-8 bytes.
Нет нового fetch по URL и нет model calls. Original screenshot не нужен для textual review.

Human actor/time берутся server-side. Receipt содержит originalScanId/latestScanId, которые
совпадают на момент нового consent: это capture-time scan, а не scan до оригинальной extraction.
Ранее сохранённые scan rows сохраняют engine/signatures/Job/attempt provenance. Current read
не выдаёт stale consent как актуальный. Withdrawal старого receipt блокирует всю sourceVersion.

## Архитектурное решение

[ADR0022](adr/0022-url-text-admission.md) реализует [принятую policy](adr/0021-source-kind-ai-eligibility.md).
Разделены acquisition snapshot и admission: факт ingestion не равен разрешению использовать.
URL контроллер и DTO отделены от существующих PDF/TEXT selectors; общий withdrawal переиспользован.
Raw HTML не выдаётся. Projection deterministic, с сохранением Unicode и escaping quote/backslash.

Альтернативы: inline-only copy не доказывает original acquisition; refetch меняет источник;
DB locks во время I/O увеличивают contention. Выбран capture→physical verification→fresh locked
finish. Trade-offs: дополнительные private reads/local parsing; old URL без receipt недопустим.
Повтор registration дедуплицирует old URL, не создаёт silent refresh. Re-acquisition lifecycle
отдельный, существующий workflow не фальсифицирует provenance ради legacy compatibility.

## Security Review

Threat: BOLA/forged actor/stale principal. Protection: scoped queries, method security, active
OWNER и actual membership под Account→Workspace→Brand→Evidence→Quarantine locks.
Verification: HTTP/direct-bean foreign/scope/role/actor, revoked membership/role/inactive account.

Threat: stale bytes/provenance/consent и TOCTOU. Protection: immutable snapshot/version/hash,
size/SHA-256/private ledger, exact re-extraction, latest CLEAN/expiry, deadline и fresh guards.
Verification: corrupt/missing bytes, changed metadata/capture time/source/parent/scan, expiry,
withdrawal во время I/O, original metadata cannot be assigned to new fetch attempt.

Threat: SQL обход/история consent/неограниченный рост. Protection: V39 source/Job lease/context/
actor/latest scan guards, FK/partial unique index, append-only receipts и cap1000/evidence.
Verification: forged SQL receipts, overwrite/delete, scoped runtime permissions,1000/1001 boundary,
обе реальные PostgreSQL очередности re-review/withdrawal race и populated upgrade.

Threat: SSRF/prompt injection/data exposure. Protection: review зависит от private storage и
локального parser, не UrlEvidenceFetcher; metadata остаются untrusted text, generation disabled.
Verification: no fetcher/analyzer calls после acquisition, strict unknown request fields,
response без quarantine reference/leaseOwner; existing SSRF suite включён в full gate.

Threat: resource exhaustion. Protection:2MiB source,20k UTF-8 projection, bounded parser/storage,
two-slot bulkhead,30s operation lifetime,1000 receipts. Verification: Unicode oversize/empty
inputs, deadline, cap; физические storage timeouts остаются существующими provider settings.
Новые dependencies/secrets/security bypass отсутствуют. SQL не может проверить external bytes;
rogue deletion после physical witness остаётся operational risk, защищаемый IAM/immutability.

## Изменённые файлы

Полный перечень с назначением — [manifest](issue-121-url-admission-files.md).

## Runtime Flow

```text
Acquisition: pinned URL fetch → bounded website-v2 extraction → private retained generation
    → atomic UrlTextSnapshot + successful original Job

Review/consent/current read:
OWNER request → strict DTO/scoped ownership → locked current metadata capture
    → bounded private read + hash + exact local re-extraction (no transaction)
    → fresh locked actor/context/source/scan/withdrawal check
    → preview / immutable EvidenceAdmission / current reviewed projection

Withdrawal: actual OWNER → parent/source locks → append-only withdrawal for sourceVersion
```

## Тесты

Реально выполненные targeted commands:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply -DskipTests compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=UrlReviewApiIntegrationTest,PostgreSqlUrlReviewTest,UrlFetchWorkerTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=UrlReviewApiIntegrationTest,PostgreSqlUrlReviewTest,UrlFetchWorkerTest,PostgreSqlUrlReviewMigrationTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=UrlReviewApiIntegrationTest,PostgreSqlUrlReviewTest,UrlFetchWorkerTest,PostgreSqlUrlReviewMigrationTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=UrlReviewDomainTest,UrlReviewApiIntegrationTest,PostgreSqlUrlReviewTest,UrlFetchWorkerTest,PostgreSqlUrlReviewMigrationTest,OpenApiContractTest'
```

Compilation PASS. Ранние focused runs:55 tests/2failures/25errors (fixture checksum/auth и missing
test scanner bean);56/0failures/1error (SQL запрет подмены storage reference сработал раньше
application check). Исправлены fixtures/разделение layer assertions; затем69/0/0 и72/0/0 PASS.
После security hardening выявлен JDBC Instant fixture error, исправлен на Timestamp.
Ни одна security assertion не отключалась. Предварительные full runs остановлены после source
changes; они не квалифицируют final source и не выдаются за completed pass.

Окончательный targeted run: **76 tests,0 failures/errors/skips**. Финальный full gate запущен
в ignored isolated copy `var/prompt03-final-verification`:481 source/build SHA-256 совпали с root,
315 compiled class hashes не изменились. Это исключает обнаруженную в01 внешнюю перезапись
root `target/classes`; никакого изменения tests или ослабления coverage gate ради результата.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
& 'C:\Users\<user>\eclipse-workspace\cadence\var\prompt03-startup.ps1'
```

Full **BUILD SUCCESS 2026-10-14 05:09:22 Europe/London**:122 suites,1137 tests,0 failures/errors,
3 Windows filesystem skips;1134 Maven tests исполнены успешно. Все3 skipped symlink cases
покрыты отдельным Linux suite **35/35**,0skips/failures. Required PostgreSQL/MinIO/ClamAV/renderer/
full-provider reports PASS. URL H2API25/25, PostgreSQL29/29, populated migration1/1, domain3/3.
Compilation/Spotless/package прошли. JaCoCo LINE6375/6716 =94.92%. Packaged local JAR health200;
anonymous URL preview/admit/current snapshot/shared withdrawal401. PostgreSQL Spring contexts
с Flyway/JPA validation также реально стартовали в tests.

На source-identical scripts/renderer в `var/prompt03-verification` выполнены:

```powershell
python -B -m unittest discover -s scripts -p 'test_*.py'
docker build --tag cadence-prompt03-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt03-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

Python **10/10**, renderer **29/29** PASS. Первый sandboxed Python run не смог писать Windows TEMP
fixtures (PermissionError); повтор той же команды с разрешённым доступом прошёл, tests не менялись.
Warnings: existing S3 deprecated API/unchecked test code/commons-logging discovery; Hikari warnings
при завершении disposable PostgreSQL containers. Negative sandbox tests печатают ожидаемые
namespace rejection logs; mandatory renderer reports при этом без skips/failures.

Docs/content/link/diff review:161 local links, old OpenAPI paths/schemas semantic equality,
V1–V38 unchanged, синхронные оба плана. `git diff --check` и staged diff проверены перед commit.

## Security Tests

Непрошедшая authentication, EDITOR/VIEWER, foreign scope/receipt, widened scope, forged actor,
unknown writable fields, actual revoked OWNER/member, latest ERROR после CLEAN, scan expiry,
corrupt/unavailable bytes, changed extraction/context/capture, withdrawal/re-review races,
SQL forgery/history rewrite и runtime UPDATE/DELETE запрет. URL admissions не попадают в
TEXT/PDF analysis selection: проверен fail-closed scope boundary03/04.

## Edge Cases

Empty body допускается только с содержательным title/description; полностью empty projection
отклоняется. UTF-8 bytes ограничены отдельно от UTF-16 chars. Supplementary Unicode/quotes/
backslash сохраняются, unpaired surrogates отвергаются. New CLEAN требует explicit re-review;
старый withdrawal действует на новые parent contexts. Legacy v2 inline metadata без receipt
не превращаются в verified source. Duplicate consent409,1001-й receipt409/SQL rejection.

## Непроверенные аспекты

Нет live AI/provider activation, frontend, actual Clerk tenant или commercial storage/IAM/TLS
qualification. Snapshot после ответа может устареть; future04 consumer должен повторять guards
перед egress/publication/copy/approval. Images/social scope05 и Strategy source вне задания03.
Fresh vulnerability/SAST scans не выполнялись, dependencies не добавлены. Исторический report01
не используется как доказательство прохождения новых tests.

## Что мне важно понять

Consent относится к конкретному тексту и его происхождению. Если сайт изменился, это другой
input, поэтому review не должен заново загружать URL. Проверки перед чтением недостаточно:
за время I/O могли отозвать доступ или consent, поэтому нужен второй locked check. При этом
locks на всё время I/O сделали бы storage failure причиной длительной блокировки БД.

## Что мне стоит изучить

1. Неизменяемое происхождение (immutable provenance): source bytes, extraction и fetch attempt
   связываются один раз, вместо восстановления истории из похожего текущего состояния.
2. Гонка проверки и использования (TOCTOU): capture/verify/final check защищает commit после I/O.
3. Частичный уникальный индекс (partial unique index): URL consent имеет свой exact tuple,
   без изменения старой PDF/TEXT history.
4. Отзыв на уровне версии (source-version withdrawal): смена parent context не возвращает разрешение.
5. Проверка реальной БД (PostgreSQL qualification): H2 не доказывает trigger/locking/runtime grants.

## Production considerations

Migration V39 additive, V1–V38 immutable. Обновить runtime grants после migration; для recovery
использовать backup/forward migration, не удаление consent history. Подтвердить private storage
immutability/retention и operational bounds. Legacy re-acquisition должен иметь отдельное явное
решение; new Job с old bytes не является original fetch provenance. Provider/privacy/retention
#120 не утверждаются этим slice. Whole #121 остаётся open; push/CI/merge/deployment не выполнены.

Local commit subject: `feat(evidence): qualify URL textual review and admission`.
Его SHA определяется через `git log -1 -- docs/issue-121-url-admission-slice.md`; exact tested source
зафиксирован manifest выше. Финальный пользовательский ответ содержит SHA после создания commit.

Факты: prerequisite, код и выполненные checks фиксируются выше. Инженерные выводы: отдельные
acquisition/consent receipts и final guards предотвращают рассмотренные stale/forgery сценарии.
Assumptions: storage/IAM за пределами локальной qualification и eventual source re-acquisition
не доказаны; новая model integration здесь отсутствует.
