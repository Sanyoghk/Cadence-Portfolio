# Задание28 — backend security / audit / observability

Дата: 2026-10-19. Исходный HEAD `2f00d58fc3c7e8d869bc9b2587c107dfefe99803`, `mvp`,
26 commits ahead origin. Existing design README change и untracked `.agents/` сохранены.
Prerequisites09–22 сверены по code/ADR/ledger, не реализуются повторно. Read-only issues
[#87](https://github.com/Sanyoghk/Cadence/issues/87), [#83](https://github.com/Sanyoghk/Cadence/issues/83),
[#116](https://github.com/Sanyoghk/Cadence/issues/116), [#118](https://github.com/Sanyoghk/Cadence/issues/118)
и [#107](https://github.com/Sanyoghk/Cadence/issues/107) прочитаны до implementation.

**Execution status: BACKEND28 QUALIFIED LOCAL.** Frontend/cross-stack28 остаётся PENDING;
whole28/whole issues/production readiness не объявляются выполненными.

## Что было сделано

Добавлены safe HTTP correlation/headers, bounded global concurrency/admission и account rate
windows, fixed-cardinality internal metrics, отдельный core DB readiness. V56 даёт same-transaction
append-only audit для approvals/scheduling/exports/reports/evidence/permissions без user content.
Container HIGH/CRITICAL findings исправлены security updates, удалением unused runtime
dependencies/installer bytes и locked Go dependency updates существующего fixture. Metrics/CSV/
report publication использует coherent completion timestamp с отдельным fresh expiry fence.
Scanners сохраняют все raw findings. [Contracts/threat matrix](backend-security-gate.md),
[операционный runbook](backend-demo-runbook.md), [изменённые файлы](prompt28-changed-files.md).

## Как это работает / Runtime Flow

```text
HTTP
  → server UUID + safe headers + global bounded admission
  → existing Clerk Bearer validation + persisted account/membership
  → existing bounded body/upload admission + new account rate window
  → dedicated DTO / OWNER use case / scoped current-resource checks
  → domain invariants + existing ordered transactions / version / lease guards
  → PostgreSQL constraints + fixed audit trigger + atomic commit
  → controlled response with X-Request-ID / safe status counters
```

Model/storage external I/O, durable reservations и publication fencing остаются в existing
reviewed orchestration. Approval не становится автоматическим, async result не считается human
approval. Readiness делает только bounded DB query; liveness не зависит от providers.

## Архитектурное решение

Новые servlet filters окружают прежнюю authorization, не заменяют её. Process-local rates
ограничивают demo request work; DB budgets остаются источником distributed cost/queue integrity.
Альтернативный distributed limiter потребовал бы новый shared service/operational policy до
выбора hosting. Он оставлен production prerequisite, не выдан за текущую гарантию нескольких JVM.

Audit триггерами выбран вместо копирования logging во все use cases: rollback/deferred SQL
failure атомарны с event, runtime не может обойти запись direct repository write. Защита на
application/domain/SQL сохраняется. Цена — PostgreSQL-specific retained audit; H2 остаётся
disposable development без audit qualification. Operational permission changes честно имеют
DATABASE_PRINCIPAL, а не выдуманный human actor. Historical events не backfill.

Remote JMX/Actuator/server secrets/debug API не добавлены. Оригинальные source/model/prompt/
attempt receipts и Identity/Strategy/Post/export histories не переписаны. API изменения additive;
frontend generated file меняет только actual OpenAPI fingerprint,44 DTO schemas сохраняются.

## Findings → fixes / disposition

| Finding | Fix / disposition | Verification |
| --- | --- | --- |
| No request correlation / pre-auth work bound | server UUID/MDC/finally;16 concurrent,6000 admissions/min; safe sampled failures | hostile header/body, capacity/liveness/exception recovery, HTTP tests |
| Repeated requests обходят только queue quotas |1200 read/120 write account windows, bounded2048 map; scopes/sessions share account;429/Retry-After | concurrent100 calls exactly10 admissions, expiry/full-map/other account tests |
| Health only liveness | public exact GET readiness with connection/query bounds, safe503 | real H2 HTTP readiness + SQL timeout/unavailable unit tests; startup below |
| Distributed domain audit only | V56 fixed append-only trigger, no runtime direct privileges, no content; fail-closed sequence cap | PostgreSQL approval/replay/scheduling/export, permissions, rollback, upgrade/no-backfill/cap tests |
| PostgreSQL diagnostics may leak row data | prod SQL/bind/row diagnostic loggers OFF; safe application error codes/correlation retained | configuration inspection; sampled request/worker logs; full error tests |
| Renderer11 HIGH matches | OS security updates; remove unused GStreamer bad codecs/build tools and cached installer wheel | final full Trivy report:0HIGH/CRITICAL; retained564MEDIUM/83LOW, no current fixed versions |
| Infrastructure images: PostgreSQL1CRITICAL/24HIGH, MinIO5CRITICAL/71HIGH, ClamAV1HIGH, gateway5HIGH | Alpine security updates; PostgreSQL native su-exec preserves UID/GID/groups/exec contract; MinIO/mc unchanged source with Go1.27.2 and pinned patched module graphs | PG/Clam/gateway candidates0findings; MinIO0HIGH/CRITICAL +2UNKNOWN reviewed below; real provider/runtime qualification24/24PASS |
| Semgrep p/java socket audit finding | Reviewed intentional public HTTP + pinned raw socket layered with SSLSocket for HTTPS; endpoint identification/SNI/handshake enforced; no auth headers/cookies |60 rules/429files,1 audit finding,0parse errors; original result preserved; existing TLS/SSRF tests |
| Gitleaks historical generic-api-key | False positive: ordinary ADR0040 description of a private session subtree; no credential. Current wording clearer; history not rewritten | full156commits scan:1 redacted finding/triaged; final current snapshot including new docs checked separately; no ignore/baseline |
| Missing source/container CI | pinned checksum-verified tools, local engine/no telemetry, full artifact retention and scan-completeness tests | new workflow prepared; local commands below; no remote run claimed |
| Ruleset / required-check enforcement | User has admin, private rulesets403 plan limitation; branch protection403 integration permission | actual read responses, no setting/visibility/subscription change |
| Intermittent normalized metrics ingestion timestamp rejection | One SQL transaction timestamp per atomic batch, projected as Instant; fresh expiry/lease checks retained | independent DB timestamp assertion RED on old worker / GREEN on fixed worker; raw checksum and reported times unchanged |
| Separate wall-clock samples can make Job completion earlier than result completion | One completion stamp for each atomic metrics/CSV/report publication, plus separate fresh lease/deadline check after SQL write | deterministic PostgreSQL RED with DATABASE_METRICS_SEAL;7/7 positive backward-clock/negative post-write expiry tests on fixed workers |

Container counts отражают raw matches, включая повторения advisory в разных бинарниках/packages.
SAST audit findings остаются видимыми warnings в CI и требуют triage. Завершённый scan
не означает отсутствие findings. Нет nosemgrep, severity-filtered output, ignore files,
allow-empty или переписывания Git history. Container gate отвергает HIGH/CRITICAL, включая
unfixed, и каждый unreviewed UNKNOWN; MEDIUM/LOW сохраняются в risk register.

В renderer остаются vendor-unfixed OS matches, включая multimedia libraries. Его bounded,
network-restricted sandbox уменьшает воздействие. Это не доказывает недостижимость каждой
upstream vulnerability и не разрешает включать production providers.

MinIO raw report сохраняет2UNKNOWN `GO-2026-5932`, по одному на `minio`/`mc`. Согласно
[официальному Go advisory](https://vuln.go.dev/ID/GO-2026-5932.json), affected code — семь
package paths `golang.org/x/crypto/openpgp`. Module-level scan отмечает весь x/crypto module.
Compiler dependency inventories точных бинарников содержат1359server/605client packages;
ни одного affected OpenPGP path нет. Проверка `minio` читает bounded inventories из того же
immutable `Metadata.ImageID`, используя network-none/read-only/cap-drop/non-root containers.
Только этот advisory/module/known binary с отсутствующими affected packages получает
NOT_APPLICABLE. Missing/malformed/mismatched proof, affected package, иной UNKNOWN или
HIGH/CRITICAL блокируют gate. Raw finding и доказательство сохраняются; уязвимый reachable
runtime не разрешается. Counterexample fixtures проверяют эту границу.

MinIO остаётся прежним archived local/integration fixture: source hashes и S3/least-privilege
contract сохранены. Новый storage/AI provider, processor или privacy policy не выбирались.
Readonly Go builds используют committed graphs/checksums и
[официальный security-patched Go1.27.2](https://go.dev/doc/devel/release).
Upstream transitive test-only `go mod tidy` содержит unavailable test package. Runtime-complete
sums подготовлены compilation, затем проверены readonly builds и реальными Cadence S3/provider
tests. Upstream MinIO full-test или production-store qualification не заявляются.

## Security Review

Threat: BOLA, stale authorization, approved/source lineage rewrite, duplicate worker publication.
Protection: existing current scoped account/parent/source/withdrawal/deadline/version/lease
checks and SQL/runtime guards unchanged; filters do not grant scope or accept writable actor.
Verification: full current backend/PostgreSQL/recovery/API suites below.

Threat: credential/content disclosure, log injection and high-cardinality telemetry.
Protection: no raw URL/query/body/token/exception in request events; generated UUID; fixed enum/
status counters; bounded event sampling; prod ORM diagnostic suppression; safe Problem Details.
Verification: malicious request fixtures + sampled actual startup/worker events; redacted scanners.

Threat: audit forgery/deletion, privilege escalation via SECURITY DEFINER, falsely attributed actor.
Protection: trusted trigger schema identifiers, parameterized values, fixed pg_catalog search_path,
no direct runtime/HTTP audit writes/reads; honest domain/database-principal origin, no backfill.
Verification: migration/rollback/cap and runtime INSERT/SELECT/UPDATE/DELETE/TRUNCATE/execute
negative tests; approval/export events exactly bound to original actor/resource/workspace.

Threat: SSRF/file/CSV/XSS/CSRF/CORS/supply-chain abuse.
Protection: preserved pinned public-IP/TLS fetch, quarantine/scan/sandbox and bounded CSV/HTML
encoding; Bearer-only API/exact origins; immutable dependency/tool/action pins. No new API adapter.
Verification: existing relevant negative suites + final renderer/scans/report-gate results below.

## Тесты и actual execution

- Initial sandbox targeted:9unit passed;27 context errors because host loopback prohibited.
  Repeated with host permissions, no test weakening.
- Targeted host:111tests/0failures/errors/skips, including PostgreSQL audit/post/export.
- Final HTTP/headers/rates/readiness/OpenAPI target:40/40PASS.
- Первый isolated full:2302tests/3failures/0errors/4Windows filesystem skips,25:48min — **FAIL**.
  Два failures: строгий existing migration inventory ещё ожидал55 вместо новой V56; updated
  exact inventory до56, не relaxed. Один failure: performance cap fixture metrics Job DEAD
  вместо SUCCEEDED. Original bulk fixture повторён20раз:1failure; safe diagnostics подтвердили
  SQL guard rejection, но не конкретный predicate. Диагностические trigger variants меняли
  timing и не считаются qualification. Worker теперь использует coherent DB ingestion timestamp.
  Промежуточная OffsetDateTime projection дала34failures/3errors в118tests и была исправлена
  на Instant; этот run — FAIL. Fixed targeted H2/PostgreSQL/diagnostics:34/34PASS.
  Новый DB assertion подтвердил RED/GREEN timestamp contract; точная причина каждого
  исторического intermittent отказа не выдаётся за доказанную. После fixes нужен fresh full.
- Migration privilege-window + original9persistence + diagnostics + isolated performance
  fixture:12/12PASS, включая default grants до применения V56.
- Второй full:2304tests/1failure/0errors/4Windows skips,22:28 — **FAIL**. Metrics seed
  в PostgreSqlCreatorReportTest получил DEAD. Original scenario50повторов/51total tests PASS
  с расширенными safe categories не воспроизвёл редкий отказ; этот повтор не заменяет full.
  Независимый controlled backward-clock fixture дал RED на SQL publication seal. Metrics/CSV/
  report workers теперь используют общий completion stamp с fresh post-write expiry check;
 7/7regressions PASS. Какой именно predicate вызвал исторический редкий отказ, не доказано.
- Infrastructure target на patched images:24/24PASS (ClamAV5,full provider10,S3/private IAM8,
  safe diagnostics1), без skips. Temporary anonymous-generic helper compilation failure fixed
  typed PostgreSQL subclass; failed compilation не считается выполненным test run.
- Actual Spring/JMX/HTTP/readiness/rate target:15/15PASS, including local opt-in export.
- Python report gates:13/13PASS (initial sandbox temp ACL failure not pass).
- Linux filesystem/storage:37/37PASS,0skips, на compiled classes текущего source manifest.
- Renderer Python:29/29PASS on final cached-wheel-free image.
- Maven194 resolved runtime/test components, fresh OSV0findings, independent completeness verifier PASS.
- Frontend44 schemas/fingerprint in sync; direct Node/TypeScript typecheck PASS.
- Финальный full/startup/final image checks: PASS, exact evidence ниже; предыдущие FAIL сохранены как FAIL.

### Команды qualification

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply
.\var\prompt28-qualified\mvnw.cmd -o --batch-mode --no-transfer-progress -f var/prompt28-qualified/pom.xml clean verify
python -m unittest discover -s scripts -p 'test_*.py'
# Следующие команды выполнены из var/prompt28-qualified:
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
# Из repository root; после actual compilation/resolution:
.\mvnw.cmd --batch-mode --no-transfer-progress org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\osv-scanner-2.5.1.exe scan source --lockfile target/bom.cdx.json --all-packages --format=json --output-file=var/prompt28-osv-results.json
python scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt28-osv-results.json
python scripts/verify_security_reports.py sast var/prompt28-semgrep-final.json
python scripts/verify_security_reports.py container var/prompt28-renderer-qualified-trivy.json
python scripts/verify_security_reports.py container var/prompt28-app-base-trivy.json
python scripts/verify_security_reports.py minio var/prompt28-minio-fixed-trivy.json
.\var\prompt28-startup.ps1
.\var\prompt28-tools\trivy\trivy.exe image --scanners vuln --list-all-pkgs --format json --timeout 5m --output var/prompt28-application-actual-trivy.json cadence-app-prompt28
python scripts/verify_security_reports.py container var/prompt28-application-actual-trivy.json
python scripts/verify_security_reports.py minio var/prompt28-minio-actual-trivy.json
$rendererSeccomp=(Resolve-Path docker/evidence-renderer/seccomp.json).Path
docker run --rm --network none --read-only --memory 256m --cpus 1 --pids-limit 64 --shm-size 256m --cap-drop ALL --security-opt no-new-privileges --security-opt "seccomp=$rendererSeccomp" --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-prompt28-final -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

Full run/source manifest and ignored local logs are under `var/prompt28-*`. Maven inventory
regeneration without `--all-packages` initially produced incomplete OSV output: independent
verifier rejected it, so it was repeated with complete inventory; only194matched components
count as PASS. CycloneDX reports a schema keyword warning (`deprecated`), not unresolved artifacts.
Повторный renderer command сначала был запущен без обязательного mqueue tmpfs и получил
2preflight errors/29tests. Guards и tests сохранены; с полным reviewed runtime contract выше
повтор29/29PASS. Небезопасная команда не считается successful qualification. Full JVM renderer
suite также прошёл с правильным contract. Compilation содержит прежний S3 SDK deprecation notice;
OpenJDK class-sharing и Hikari closed test-container connections дают diagnostic warnings.

## Security Tests / Edge Cases

- Foreign/missing/inactive account, changed membership/source/scan/expiry/withdrawal и stale
  versions: existing HTTP/use-case/SQL negatives повторяются full gate, не доверяют frontend.
- Audit: no legacy backfill; exact actor/workspace/resource; replay produces one approval;
  rollback removes event; default grants do not expose a newly migrated table; direct runtime
  read/forgery/update/delete/truncate/execute denied; capacity exhaustion rolls business change back.
- HTTP: forged client request ID, log-newline/body/token payload, exception cleanup, semaphore
  exhaustion, account/session/tenant switching, full map and exact minute boundary.
- Readiness: connection/query failure gives safe503; result/statement/connection are closed;
  exact GET probes public, other methods/subpaths/audit/actuator remain private.
- Metrics: one transaction ingestion instant for a batch; reported times and original bytes
  remain distinct/preserved. Separate wall-clock samples need not be ordered: one completion
  timestamp now binds result and Job; fresh post-write expiry check remains independent for
  metrics/CSV/report. Expiry fixture proves transaction/audit rollback with no published result.

Первый root `clean` завершился ошибкой: существующий backend PID35924 держит JAR.
Этот процесс и данные сохранены. Full run использует точную source copy под
`var/prompt28-qualified`, SHA256 manifest, без .env/ignored credentials/untracked skills.
Старый работающий JAR не является qualification artifact. После container/code fixes повторены
соответствующие targets; затем выполняется новый full run. Параллельных Maven writes в один
qualification target нет. Точное состояние source подтверждает SHA256 manifest.

## Финальные artifacts / source binding

Full gate:187suites/2311tests,2307executed PASS,0failures/errors/flakes,4Windows link skips.
Эти четыре cases: linked root/parent/file/media-parent в FileSystemEvidenceObjectStorageTest;
Linux37/37PASS покрывает их. Mandatory PostgreSQL/provider report validators PASS без skips.
JaCoCo lines12995covered/808missed,94.15%; Spotless645Java files и POM PASS; JAR собран.

Frozen manifest775code/build/contract files: `01abee44dc62b0691c424c380c80ec5848cb5d0d138fb0949d3df4dc67f966ae`.
Qualified JAR: `b474ae2c03c6c37baa0229b122aae0206719b5a17ad3f49632209905563604b1`, `var/prompt28-qualified/target/cadence-0.0.1-SNAPSHOT.jar`.
Все775hashes сверены с working source; временные diagnostic tests в final snapshot отсутствуют.

| Component | Immutable image | Raw findings | Full report SHA256 |
| --- | --- | --- | --- |
| Application/JAR | `sha256:a74277a412443260f354b04623444fe02e2cd717677ad8db6e9b17c8aa177f8c` | {'LOW': 32, 'MEDIUM': 27} | `28a05f4b75a1572f025c6d7858f2700820d2be206f32769b65c15e072c497bba` |
| PostgreSQL | `sha256:37bd91065123eaa647fde64af2f212d96c38e98041a22232177920eb7abb7d07` | 0 findings | `d1a5f33890b9e47ef1fa673512f17a6d54320866aee7c0e3b542c2952aac33aa` |
| ClamAV | `sha256:0b3dc181646c4056f6cf9fd0e4e0b86304ea959208fc2913c3c3ecfdd8938199` | 0 findings | `4e4c703c28124ac10e6924fa138f5a0c1142544285414a7155e6edba542d6e6d` |
| MinIO fixture | `sha256:2f3b85859d07c45e1b356734df2d04263dffc40e8c91074b6781f279ad8f7391` | {'UNKNOWN': 2} | `94e5e26a7effb83791220968dfc44a6e5cbafcc229e7c39baac8f23922aadf3e` |
| Renderer | `sha256:666782a93cdc8b0895f06ae37186897167082fa3b65d9bdaa4a38d0844c94eb8` | {'LOW': 83, 'MEDIUM': 564} | `f05e2f89e526d8f4bfcc74d328bae567597b6db9ac2791384034f119c6bc9815` |
| Gateway | `sha256:d518236478ccf5bf46c04317497d379fd1c6e2477a4bf304a99f60339628fcf6` | 0 findings | `c8251b67e92f4d52f64d3cdaa06f1a13462afedccd87c5c6e04dbfbc9fc7e6aa` |

Startup actual JAR image: non-root10001/read-only/cap-drop/tmpfs, own loopback18092.
GET health/readiness200; server UUID/no-store; private Workspace/Job/health subpath/actuator/audit
401; POST readiness401; packaged healthcheck PASS. Только свой smoke container удалён.
Существующий backend PID35924 подтверждён host inspection и не перезапускался; текущая
пользовательская H2 session не заменена новой qualification session.

Gateway `nginx -t` PASS под non-root/read-only/network-none/cap-drop; alias hosts использованы
для syntax validation. Это не claim нового gateway end-to-end release или hosting qualification.
Renderer Python29/29PASS на final image, full Java renderer6/provider10 и S3/scanner suites PASS.
Source security workflow/compose YAML syntax PASS через existing Prettier parser.
Frontend44schema source fingerprint нормализован согласно existing Git LF policy; generated
DTO contents прежние, direct TypeScript check PASS. Frontend build/E2E этого backend scope не заявлены.

Secret scan: current1158source/deliverable files/~10.82MB,0findings. Сначала получены57matches:
56public SHA256 в generated qualification manifest, случайно добавленном в source export,
и ordinary description из ADR, повторённая в docs. Source-only export исправлен: generated
ignored metadata не является source checkout; формулировка уточнена. Raw redacted57-match
report сохранён для triage; scanner rules/ignore/baseline не менялись. Git history156commits:
1ordinary-text false positive retained/triaged, без переписывания истории.

## Acceptance / actual issue mapping

| Scope | Backend boundary | Remaining outside backend28 |
| --- | --- | --- |
| #87 security/audit | safe headers/CORS, redacted bounded events/correlation, immutable SQL audit, quotas/rates, uploads/SSRF negatives, scan workflow | real secret manager/TLS/IAM/hosting; new frontend/adapters review; remote enforcement/CI |
| #83 health/readiness | provider-independent liveness; required core DB readiness; safe output/probe runbook | selected hosting probe/edge/optional provider activation qualification |
| #116 operations | local prerequisites/runbooks, Job recovery, audit/correlation/incident and fail-closed diagnosis | production backup/PITR/restore, credentials rotation/deletion/rollback rehearsals in selected hosting |
| #118 tests | current deterministic domain/HTTP/security/PostgreSQL/migration/crash/provider fixtures | future frontend25–27/cross-stack creator E2E and real provider qualification; exact pushed-SHA CI |
| 28 backend | QUALIFIED LOCAL: fresh full/runtime/security evidence выше | no automatic whole28/#87/#83/#116/#118 closure |
| 28 frontend/cross-stack | PENDING |23–27 + qualified backend, repeat changed boundaries after adapters/UI |

## Runbook tabletop

1. DB unavailable: readiness503/liveness200 policy; inspect DB/pool/grants without logging
   credentials; restore dependency, no fake success or bypass. SQL timeout/error tests support flow.
2. Worker crash after dispatch: persisted witness → OUTCOME_UNKNOWN, reservation held; no manual
   lease extension/retry/cost zero. Real child-JVM recovery fixtures in full run qualify mechanics.
3. Stale source/access:409/403/404 or superseded outcome according to existing contract;
   fresh review/version required, no silent consent. PostgreSQL race suites support denial.
4. Audit capacity / leak incident: stop new affected admissions, preserve history, identify actual
   credential principal; planned rotation/forward repair. Production rotation/restore not rehearsed.

Это documented operator walkthrough по actual tests/contracts, не утверждение о проведённой
аварии, restore реальных backup или live provider outage drill.

## Непроверенные аспекты / Production considerations

Frontend/cross-stack28,25–27, real AI/media adapters, live OAuth #32/#33 и whole#121 remain open.
Provider/model/processor/region/retention/deletion/cost policy не выбраны. Deployment edge/TLS/IAM,
secret manager, distributed request admission, remote JMX monitoring, backup/load/pilot и remote
CI нового source отдельно. Public HTTP source policy остаётся принятой, но transport не даёт
cryptographic authenticity для HTTP content; human review/provenance не являются TLS.

## Что мне важно понять / Что изучить

- Same-transaction audit: событие и business change commit/rollback вместе; обычный log не даёт
  такой гарантии и не заменяет immutable domain receipts.
- Lease fencing: поздний worker теряет право publication, даже если получил успешный ответ.
  Rate limiting не заменяет эту DB гарантию.
- Liveness versus readiness: живой процесс и готовность обслуживать requests — разные сигналы.
- Cardinality / redaction: метрика с arbitrary user/URL labels растёт без bounds и раскрывает
  данные; фиксированные enums/status и server correlation дают контролируемую диагностику.
- Scanner evidence: execution, completeness, findings и triage различаются. Missing/error report
  не clean result; zeroHIGH не означает отсутствие MEDIUM/LOW или production readiness.

## Git / facts, conclusions, assumptions

Qualified source сохраняется local commit вместе с этим report; final source manifest и artifacts выше. Push/remote issue
edits/closure/new CI/merge/deployment не выполняются; #121 remains open по ADR0027.

Факты: только actual commands/results и inspected code/permissions, с указанными snapshot/image.
Инженерные выводы: documented controls и конкретная triage findings поддерживают bounded demo.
Assumptions: production operational controls будут выбраны и независимо qualified позднее;
local evidence не доказывает live provider behavior или качество generation.
