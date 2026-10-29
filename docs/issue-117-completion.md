# Issue #117 — итог реализации

Дата: 2026-10-01. Ветка: `mvp`. Deployment validation внешней инфраструктуры отделена от локального Definition of Done.

## Что было сделано

Реализованы реальные S3-compatible storage и private clamd adapters, isolated website
screenshots/PDF text extraction, approved Instagram/TikTok adapters, bounded quotas,
deadlines, signature freshness и race-safe orphan cleanup. Добавлены V16–V21,
Docker Compose с отдельными runtime identities, configuration guards, negative tests
и обязательные CI report gates.

Поддерживаются TEXT, URL (`text/html`, `text/plain`, строгий UTF-8/явный UTF-16 BOM),
PDF, PNG и JPEG. Новые archive formats не введены: ZIP/office/прочие архивы не
принимаются; лимиты вложений ClamAV и PDF renderer заданы независимо.
Heuristic charset guessing намеренно отсутствует: неоднозначные bytes отклоняются.

## Как это работает

`RegisterBrandEvidenceUseCase` проверяет OWNER, scoped Workspace и active Brand.
FILE сначала резервирует новую generation в `QuarantineLifecycle`; scanning и PUT
выполняются вне DB transaction. Финальная короткая transaction повторно проверяет
ownership/archive, quota, deadline и atomically сохраняет Evidence, Job и reference.
Duplicate возвращает прежнее Evidence; незадействованная generation становится orphan.

`UrlFetchWorker` использует существующий pinned public-IP fetch contract, записывает
bytes в отдельную generation и создаёт malware Job. `EvidenceMalwareScanWorker`
сверяет checksum, получает clamd verdict и сохраняет immutable scan receipt с attempt,
lease, source version и signature identity. Свежий CLEAN разрешает отдельный artifact Job.

`EvidenceArtifactWorker` повторно проверяет последний receipt, отправляет bytes
renderer, ограничивает output и сохраняет provenance. Screenshot получает собственную
private generation. PDF text ограничен 16,000 UTF-16 units; HTML input — 2 MiB,
PNG output — 3 MiB и 1280×720. CLEAN/FETCHED/artifact не переводят Evidence в READY.

Social registration резервирует долговечный provider attempt до HTTP. Server binding
определяет approved account/token/scopes/version; DTO содержит только provider enum.
Normalized JSON сохраняется как недоверенный TEXT; отдельный `EvidenceSocialOrigin`
подтверждает server-side retrieval, а не доверие к пользовательскому содержимому.

## Архитектурное решение

[ADR-0007](adr/0007-private-evidence-pipeline.md) сравнивает alternatives и trade-offs.
Ports отделяют application от SDK/protocol/processes. PostgreSQL locks сериализуют
tenant/global budgets; actual `READ_COMMITTED` проверяется перед quota admission,
чтобы ожидание lock не сохраняло stale snapshot. DB clock задаёт leases/deadlines.

Выбраны unique generations вместо shared checksum keys: rollback одного uploader не
может удалить bytes concurrent победителя. Tombstone сохраняется постоянно; cleanup
сначала фиксирует DELETING, затем удаляет bytes, ждёт drain и повторяет DELETE.
Quota освобождается после подтверждённых этапов; поздний PUT удаляется следующими
sweeps. Physical deletion eventual; публикация после fence запрещена синхронно.

## Security Review

### Чужие ресурсы / BOLA

Threat: OWNER подставляет чужой Brand/Workspace ID или forged scope.
Protection: principal-derived memberships, method security deny-by-default, scoped
repositories, повторные locked checks и composite PostgreSQL ownership constraints.
Verification: HTTP/direct Spring-bean unauthorized, non-OWNER, cross-Workspace и
archive-race tests; raw-SQL provenance/ownership rejection.

### Malware / forged CLEAN / stale scan

Threat: EICAR, encrypted/непроверяемый PDF, scanner outage, старые сигнатуры,
подмена bytes или завершение устаревшего worker.
Protection: private INSTREAM bounded exact bytes, VERSION до/после, freshness/future
checks, AlertEncrypted, fail-closed verdicts, checksum, latest receipt, lease fencing
и independent DB triggers.
Verification: real ClamAV clean/EICAR/embedded EICAR/encrypted PDF/stale/future cases;
protocol mutation, timeout, malformed response и stale attempt tests.

### SSRF / hostile rendering / path attacks

Threat: private/metadata URL, DNS rebinding, HTML script/subresource, PDF process
escape или renderer output через symlink/FIFO/hardlink/замену parent directory.
Protection: public fetch IP pinning/revalidation; renderer получает bytes, отдельный
user/net/PID/mount/IPC namespace, inherited Docker proc masks, sole non-dumpable server PID1,
startup negative parent-access probe, token-free readiness, JS off, CSP, routes denied,
Chromium sandbox, process limits; trusted child Python PID1 dumpable0 до input/parser,
normal-completion purge обоих tmpfs roots. Worker timeout, signalled supervisor, killpg/reap
failure или cleanup failure permanently poisons server до cleanup/reuse;
parent держит directory FD, использует NOFOLLOW/NONBLOCK и regular-file checks.
Verification: URL negative fixtures; real restricted Chromium/Poppler integration;
Linux Python output-fencing tests и bounded PNG/UTF-16 Java contracts.

### Cleanup races / resource exhaustion / cost

Threat: cleanup и publication одновременно; PUT завершается после DELETE; storage,
JSON, multipart или social calls расходуют неограниченные ресурсы.
Protection: одно lifecycle lock, irreversible generations/tombstones, drain+sweep,
workspace/global provider rows, append-only attempt budgets, bounded batch, bytes,
concurrency и deadlines. JDK HTTP retries отключены проверяемыми startup flags.
Verification: delayed PUT/re-reference/rollback/active Job/ref cleanup cases,
real PostgreSQL concurrent admission (включая datasource default REPEATABLE_READ),
failed-call budget retention, body/capacity rejection и configuration negatives.

### Exposure / secrets / storage drift / injection

Threat: public bucket, versioned retained bytes, credential-bearing error/paging URL,
подмена provider endpoint, mass assignment или shell/SQL injection.
Protection: private policy/owner ACL/versioning checks до операций, TLS prod,
least-privilege role/key, fixed hosts/paths, Authorization-only tokens, selected DTO
fields, strict bounded JSON, parameterized SQL и фиксированные subprocess arguments.
Verification: MinIO runtime key contract, public/versioning/collision negatives,
credential-redaction/no-follow-paging tests и actual runtime DB restrictions.
Новые dependencies: pinned AWS SDK 2.55.7; существующий JDK HTTP используется для
social. SDK нужен для verified S3 signing/protocol, собственного signing нет.

## Изменённые файлы

[Полный пофайловый manifest](issue-117-files.md) перечисляет каждый файл текущего turn
и его назначение. Основные contracts: [S3](s3-storage.md), [clamd](clamd-scanning.md),
[lifecycle](quarantine-lifecycle.md), [artifacts](evidence-artifacts.md),
[isolated renderer](isolated-evidence-rendering.md), [social](approved-social-evidence.md),
[operations](evidence-operations.md), [migrations](database-migrations.md).

## Runtime Flow

```text
Bearer JWT → local OWNER/membership → DTO/body admission → scoped active Brand
  → quota reservation (DB clock, READ_COMMITTED, workspace lock)
  → private ClamAV/immutable S3 I/O outside transaction
  → locked atomic Evidence + Job + generation reference
  → claimed scan Job → checksum + fresh ClamAV receipt → fenced commit
  → claimed artifact Job → last CLEAN/source/lease check
  → isolated renderer → bounded output → atomic artifact/provenance commit

Orphan: expiry + retention → locked DELETING commit → DELETE outside transaction
  → TOMBSTONED → drain + DELETE → quota release → permanent periodic sweeps

Social: scoped OWNER → approved binding preflight → durable workspace/global provider quota receipt
  → approved fixed-host HTTP → bounded canonical snapshot
  → atomic TEXT Evidence + Job + trusted retrieval origin
```

## Тесты

Исторический завершённый local full gate выполнен 2026-10-01 на Java 21.0.11 с работающим Docker.
Результаты этой таблицы относятся к revision до исправленного inherited-proc/parent-secret/
readiness/ephemeral-cleanup renderer. Текущие Linux Python 29 и actual sandbox smoke приведены
отдельно ниже; hardened full Maven593 также прошёл и приведён отдельным current gate:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean spotless:apply verify
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence/evidence-renderer:local docker/evidence-renderer
```

| Проверка | Фактический результат |
| --- | --- |
| Полный Maven `clean ... verify` | BUILD SUCCESS; 592 tests в 72 Surefire reports, 0 failures, 0 errors; 589 passed, 3 Windows filesystem skips |
| Linux filesystem suite | 35/35 passed, 0 skipped; включает все три пропущенных на Windows symlink/root/parent cases |
| PostgreSQL / evidence report gates | Passed; required PostgreSQL/MinIO/ClamAV/renderer suites присутствуют и не пропущены |
| Python CI/report/diagnostics scripts | 10/10 passed; 7 report-gate tests и 3 kernel-diagnostics tests |
| Python renderer в прежней Linux revision | 7/7 passed, 0 skipped; historical output-fencing result, заменён текущим Python 29 gate ниже |
| Spotless / compilation / JAR | Passed; executable Spring Boot JAR собран |
| JaCoCo | 93.26% line coverage: 3736 covered, 270 missed; required checks passed |
| Maven runtime/test SBOM + OSV | 165 components, complete clean report, 0 findings |
| Renderer requirements + OSV | 4 уникальных PyPI packages, 0 findings |

`FullEvidenceProviderIntegrationTest`: **7 tests, 0 failures/errors/skips**. Он использует
реальные PostgreSQL/Flyway V1–V21, restricted runtime database role, restricted MinIO key,
ClamAV и isolated Chromium/Poppler. Проверены scan/extraction pipeline, EICAR attachment,
encrypted PDF, private PNG, immutable provenance и runtime role ограничения.
Website retrieval задаётся offline byte fixture; последующие provider calls и DB persistence
реальны. Integration scanner Clock привязан к signature VERSION для воспроизводимости.
Отдельный current-clock smoke ниже проверяет фактическую freshness без такой привязки.

Local startup выполнен командами:

```powershell
.\scripts\start_local_evidence.ps1 -UpdateSignatures -ProjectName cadence-issue117-validation
.\scripts\start_local_evidence.ps1 -ProjectName cadence-issue117-validation
```

Compose app healthy. Через loopback gateway подтверждены `/api/v1/health` → 200
(`Cadence backend is running`), unauthenticated `/api/v1/brands` → 401,
MinIO port 19000 health → 200 и PostgreSQL port 15432 TCP connection.
У app/clamd/renderer/PostgreSQL/MinIO отсутствуют собственные host port bindings;
только gateway публикует loopback ports. Это local startup/authentication boundary,
а не qualification внешней production infrastructure.

Scratch `LiveClamdSmoke` повторно выполнен в restricted Java Linux container внутри private
Compose evidence network: actual current Clock, CLEAN и EICAR INFECTED, signature freshness
≤48h — passed. Локальное доказательство: `var/issue117-live-clamd.log`; scratch harness
не является product source. Полный Maven log — `var/issue117-corrected-final-verify.log`, XML reports —
`target/surefire-reports`, Linux filesystem log — `var/issue117-corrected-linux-filesystem.log`.
Dependency scan commands и границы проверки перечислены в [dependency security](dependency-security.md).

Несущественные warnings: Hikari проверял connections уже остановленных предыдущих
Testcontainers contexts; CycloneDX сообщал unknown schema metadata keywords. Build, report
completeness и coverage gates при этом прошли. Production connection handling этими warning
messages отдельно не квалифицируется.

### Исправления после первого remote Linux gate

[CI 36821456124](https://github.com/Sanyoghk/Cadence/actions/runs/36821456124) выполнил
590 tests без skips, но выявил 3 failures и 2 errors: ложный DNS admission отказ при
быстрых redirects и renderer sandbox incompatibility на Ubuntu/AppArmor.
DNS исправление сохраняет deadlines/threads и ограничивает очередь двумя tasks;
deterministic handoff и saturation regressions добавили два tests. Targeted suite:
81 tests, 0 failures/errors/skips. После исправлений полный local gate повторён:
592 tests с результатами выше; startup script повторно проверил namespace boundary.

Предыдущая renderer profile revision с двумя private mount operations скомпилирована
AppArmor4.0.1 и прошла local namespace/Chromium smoke на Docker Desktop. Этот результат
не подтверждает latest corrected profile или AppArmor enforcement. Docker Desktop
не имеет AppArmor; actual enforcement требует successful Ubuntu/deployed-host gate.
Исторические commands/logs:
`var/renderer-preflight-commands.txt`, `var/renderer-apparmor-parser.log`,
`var/renderer-namespace-preflight.log`, `var/renderer-sandbox-smoke.log`.
Deployment host обязан повторить этот input-free preflight перед user data.

Последующая Ubuntu24/26 CI загрузила enforcing `cadence-evidence-renderer` profile, но
fresh-proc namespace preflight завершился отказом `proc-mount`. Это не successful remote gate.
Commit `b4708c5` добавил bounded kernel diagnostics: только allowlisted renderer
denial categories, fixed paths и redacted неизвестные значения, без произвольного
host audit output. Три local negative tests диагностики проходят; Python scripts
suite содержит10 tests. Результат diagnostics не заменяет sandbox qualification.

Исправление сохраняет inherited Docker readonly/masked `/proc`, без `--mount-proc`;
job получает отдельные user/net/PID/mount/IPC namespaces. Trusted server — UID10001/container
PID1 без Tini, `PR_SET_DUMPABLE=0` до token/HTTP, mandatory negative namespace/parent proc
FD/environment/memory preflight и `PR_GET_DUMPABLE` перед job. Linux negative tests дополнительно
подтверждают отказ `ptrace`/`process_vm_readv` к non-dumpable parent. Outer CapBnd=0; child
CapEff/Prm/Inh/Amb=0, новый namespace bounding set не означает privileges в parent namespace.
HTTP readiness вынесен в
отдельный `renderer-ready` container без token; startup script больше не использует Docker exec.
Trusted child Python PID1 проверяет UID10001/PID1, устанавливает и проверяет dumpable0 и CORE0
до input/parser. Normal worker completion допускает purge `/tmp` и `/dev/shm` до/после job,
с combined2s/depth128/100000-entry budget и no-follow descriptor walk для mode-000 directories.
Startup требует read-only root, `/tmp`≤128MiB и `/dev/shm`≤256MiB как rw,noexec,nosuid,nodev
tmpfs с mount root `/`; `/dev/mqueue` закрыт ro,noexec,nosuid,nodev,size=4k,mode=000 tmpfs.
Trusted launcher запрещает host binds: mountinfo не различает bind целого host tmpfs с root `/`.
Reviewed Compose и Java fixtures используют private tmpfs без host binds. HTTP watchdog 20s,
worker 10s, cleanup 2s перед job и 2s после job; kill/reap 2s.
Security review: `unshare --kill-child` использует mutable child PDEATHSIG. Поэтому timeout и reap
outer wrapper не доказывают остановку всех descendants. Worker timeout, signalled supervisor
(`returncode < 0`) или killpg/reap failure устанавливают POISONED до teardown/cleanup; postpurge
полностью пропускается, следующая job запрещена. Group kill/reap лишь best effort.
Jobs/health503 до recreate целого container с fresh tmpfs; slow/hostile document может требовать
operator recovery, поэтому нужен alert на 503. Manual Docker exec
разрешён только в quiescent fixture без concurrent jobs и token-bearing соседних processes.

### Linux renderer gate после timeout/PDEATHSIG исправления

Фактически повторно выполнены image build и полный Python suite исправленной revision:

```powershell
docker build --tag cadence-renderer-preflight docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-preflight -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

**29 tests, 29 passed, 0 failures/errors/skips**: server 12 + probe 8 + ephemeral 9. Проверены
output fencing, parent proc/native inspection denial, invalid filesystem/mqueue mount configuration,
mode 000/no-follow cleanup, depth/budget/failure poisoning. Actual input-free startup preflight и
real Chromium screenshot 1280×720 повторно passed после protected child PID1. Scoped AppArmor
profile compiler ранее passed;
Docker Desktop не проверяет enforcement Linux LSM.

Protected child PID1 и irreversible poisoning при uncertain teardown проверены новым Linux 29 gate,
включая timeout/signalled-supervisor/killpg/reap regressions. Локальные logs:
`var/issue117-final-renderer-build.log`, `var/issue117-final-renderer-python.log`,
`var/issue117-final-renderer-smoke.log`.

### Итоговый hardened full gate

Фактически выполнены:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean spotless:apply verify
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
```

| Проверка | Фактический результат |
|---|---|
| Полный Maven | BUILD SUCCESS,2m43s;593 tests,0 failures/errors,590passed,3 Windows filesystem skips;72 XML |
| Linux filesystem | 35/35 passed,0 skips; выполняет все3 пропущенных Windows scenarios |
| PostgreSQL/evidence report gates | Passed; required integration suites присутствуют,0 required skips |
| Real full provider | 7/7 passed; PostgreSQL/MinIO/ClamAV/renderer/runtime-role contracts |
| Renderer container integration | 6/6 passed; explicit init/namespace startup rejection, negative fixtures≈6.7s |
| Linux renderer Python / scripts Python | 29/29 и10/10 passed,0 failures/errors/skips |
| Spotless/compilation/JAR/JaCoCo | Passed;93.26% line coverage,3736 covered/270 missed |

Logs: `var/issue117-final-hardened-verify.log`, `var/issue117-final-linux-filesystem.log`,
renderer logs выше. Fresh JAR Compose startup также passed:

```powershell
.\scripts\start_local_evidence.ps1 -ProjectName cadence-issue117-validation
```

App/gateway/renderer-ready healthy; `/api/v1/health`200 и unauthenticated `/api/v1/workspaces`401.
Renderer без init/exec healthcheck; readiness environment names не содержат token.
Log: `var/issue117-final-compose-application.log`.
Historical full592 выше остаётся доказательством прежней revision;
hardened593 — текущий executed local result. Enforcing remote CI прошёл отдельно ниже.

### Итоговый remote Linux/AppArmor gate

[CI36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248) — overall success
для source SHA [ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab](https://github.com/Sanyoghk/Cadence/commit/ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab).
[Verify job110276029645](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248/job/110276029645)
и [dependency-security job110276029478](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248/job/110276029478)
success. Actual enforcing AppArmor load, `namespace-probe ok` и real Chromium screenshot1280×720
passed; прежний `proc-mount` blocker устранён. Исторические failed runs выше сохранены как история
исправлений и не описывают текущий результат.

Remote Maven command: `sh ./mvnw --batch-mode --no-transfer-progress clean verify`.
Фактический результат: **593 tests,0 failures/errors/skips, BUILD SUCCESS, JaCoCo checks PASS**.
Renderer integration **6/6**, full provider **7/7**, Python scripts **10/10** и renderer Python
**29/29 без skips** passed. PostgreSQL/evidence report gates прошли без missing/skipped required suites.
Dependency job подтвердил165 Maven components и OSV0 findings. Точные Python/Docker commands,
включая CI tag `cadence-renderer-verification`, записаны в [verification](verification.md).

Этот CI квалифицирует product source указанного SHA. Последующий docs-only commit записывает
результат при source-identical коде, без заявления об отдельном CI запуске его нового SHA.
Проверена Linux/AppArmor CI fixture; внешний production deployment, выбранные IAM/TLS/region/
network policies и live provider entitlement этими tests не квалифицированы.

## Security Tests и Edge Cases

Проверены: unauthorized/non-OWNER/foreign tenant; archived resource; duplicate and
cross-brand races; rollback after PUT; cleanup/publication race; late PUT; tombstone
resurrection; quota exhaustion; inherited stale isolation; oversized/chunked body;
MIME spoofing; invalid/changed checksum; stale/future/changing signatures; encrypted
PDF; scanner failure; invalid DNS/CIDR/configuration; malicious renderer output;
expired Job lease; immutable provenance; latest non-CLEAN scan; output bomb/framing;
provider 429/outage/oversized/malformed/duplicate-field JSON; expired/unapproved
account binding; secret-bearing unused paging URLs; budgets after failed HTTP.

Empty PDF text допустим. UTF-16 supplementary characters учитываются как две units;
truncate не создаёт разорванную surrogate pair. Legacy quarantine keys не удаляются
автоматически и консервативно занимают quota.

## Непроверенные аспекты / Production considerations

Внешняя infrastructure не предоставлена. Перед real-user deployment обязательны:
selected-provider TLS/private ACL/policy/versioning/KMS/IAM/conditional PUT checks,
region/privacy/retention approval, deployed private channel/mTLS/network policy,
FreshClam update cadence/alerts, Linux sandbox support, ingress body/rate/deadline
limits, load/soak tests, backup/restore и live Clerk JWT/JWKS. Live Meta/TikTok access,
account approval и OAuth onboarding/refresh остаются #32/#33; local fixtures не
подтверждают права реального account. Production activation выключена по умолчанию.

MinIO является local fixture, не выбранным commercial storage. Malware scanning
не доказывает отсутствие всех угроз. CLEAN input остаётся недоверенным для AI.
Append-only registry/retained references требуют capacity monitoring и отдельной
authorized data deletion policy. SAST/container-image CVE scanning и branch protection
относятся к release hardening; clean Maven OSV не заменяет их.

## Что мне важно понять

Object store не участвует в PostgreSQL rollback. Поэтому безопасность требует
явного lifecycle, а не попытки удалить key при любом exception. Lock защищает
критический участок, а isolation определяет, насколько свежие данные внутри него.
Антивирус и sandbox решают разные задачи; один слой не заменяет другой.

## Что мне стоит изучить

1. **Граница доверия (trust boundary)** — внешние bytes/verdict/JSON проверяются
   независимо от auth пользователя и поведения frontend.
2. **Согласованность после задержки (eventual consistency)** — private orphan может
   физически существовать до sweep, но уже не может быть опубликован.
3. **Ограждение попытки (lease fencing)** — completion разрешён только текущему
   owner/attempt до deadline; задержавшийся worker не переписывает новые данные.
4. **Уровень изоляции (transaction isolation)** — READ_COMMITTED обновляет snapshot
   каждого statement после ожидания lock; REPEATABLE_READ сохраняет старый snapshot.
5. **Минимальные привилегии (least privilege)** — runtime role/key может выполнить
   необходимые операции, но не управляет schema, bucket policy или историей provenance.

## Факты, выводы и assumptions

Факты: implementation inspected, executed test/scan/startup results перечислены
выше по точным commands; deployment не выполнен.
Инженерный вывод: независимые application/adapter/DB checks защищают описанные
invariants при hostile clients и конкурентном выполнении.
Assumptions: approved operators обеспечивают реальные TLS/IAM/network/retention
policies; локальные flags не являются доказательством этих внешних условий.
