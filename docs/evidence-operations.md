# Эксплуатация evidence pipeline — issue #117

## Идея

Пользователь добавляет источник только в свой Brand. Приложение сохраняет его в приватном quarantine,
проверяет неизменность bytes и malware verdict, затем выполняет ограниченное извлечение в отдельном
процессе. Скачивание, CLEAN и извлечение не означают разрешение использовать или публиковать контент:
решение content safety и Brand Identity approval относится к последующим задачам.

## Локальный запуск на Windows

Требуются Java 21, Python и работающий Docker Desktop с Linux containers.

```powershell
.\mvnw.cmd clean spotless:apply verify
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
.\scripts\start_local_evidence.ps1 -UpdateSignatures
```

`start_local_evidence.ps1` создаёт `.env.evidence-local` с независимыми CSPRNG credentials и ограничивает
Windows ACL текущим пользователем. Файл игнорируется Git; значения не выводятся. Сначала запускаются
PostgreSQL, MinIO, clamd и renderer, затем отдельный MinIO bootstrap, необязательный FreshClam update,
application, ограничение runtime DB privileges и loopback gateway. `-InfrastructureOnly` исключает
application и gateway: эти private services доступны только внутри Compose network;
`-ProjectName cadence-issue117-validation` создаёт отдельный Compose project.

Renderer server сам выполняет mandatory input-free `namespace_probe.py` preflight до token/HTTP:
UID10001/PID1, dumpable0, distinct user/net/PID/mount/IPC namespaces, inherited Docker proc masks и
negative parent FD/environment/memory checks. Unsupported namespace/proc protection, init/Tini wrapper
или profile mismatch запрещают startup. Script ждёт renderer и отдельный token-free `renderer-ready`
через Compose `up --wait`, без Docker exec в renderer. На Ubuntu24.04
с restricted userns необходимо отдельно загрузить scoped enforcing AppArmor profile и задать
`CADENCE_RENDERER_APPARMOR_PROFILE=cadence-evidence-renderer` для opt-in override. Script допускает
только это имя; Docker Desktop не использует AppArmor override. Порядок загрузки и ограничения:
[isolated rendering](isolated-evidence-rendering.md#apparmor-на-ubuntu-2404-и-более-новых-hosts).

По умолчанию application доступен на `http://127.0.0.1:18080`, PostgreSQL на `15432`, MinIO на `19000`.
Порты публикует отдельный nonroot Nginx gateway, подключённый к `evidence` и `local-ingress`, и bind
только к loopback. HTTP upstream фиксирован как `app:8080`, TCP upstream — `postgres:5432` и
`minio:9000`; URL/headers клиента не выбирают upstream. App, PostgreSQL и MinIO не публикуют ports
непосредственно из internal network: Docker может не реализовать host port mappings такой сети.
Clamd и renderer не публикуют ports на host. `/api/v1/health` проверяет
liveness; business API требует действующий Clerk Bearer JWT, локальный OWNER и Workspace membership.

Внутренняя сеть Compose имеет CIDR `172.28.117.0/24` и не предоставляет application, clamd или renderer
публичный egress. Только gateway имеет вторую normal bridge network; он не является forward proxy
и не предоставляет приложению механизм произвольного outbound доступа. Gateway запускается после
healthy application и DB privilege restrictions. Script пересоздаёт его для актуальных private DNS
адресов; после отдельного пересоздания upstream container повторите script или пересоздайте gateway.
Nginx работает как UID10001, read-only/cap-drop ALL/no-new-privileges, с 128MiB memory/0.5CPU/16pids,
128 worker connections и 64MiB tmpfs. HTTP body ограничен11MiB, headers и ожидания ограничены;
access logs выключены, error log — только critical. TCP proxy idle timeout60s ограничивает зависшие
local clients. Это local diagnostic ingress, production требует отдельного TLS/rate/egress policy.
Official Nginx1.30.5 image pinned по registry digest; версия включает
[security patch15Sep2026](https://nginx.org/en/CHANGES-1.30), согласно
[vendor advisories](https://nginx.org/en/security_advisories.html). HTTP/2/HTTP/3/TLS не включены.
Поэтому URL worker выключен; live Clerk JWKS и social API через эту сеть не проверяются. Mocked decoder
в tests не заменяет настройку реального Clerk. Для публичного URL/provider доступа нужен отдельный
reviewed egress policy с deny private/metadata targets; не превращайте clamd/renderer в public services.

FreshClam update использует отдельную сеть и общий signature volume, а clamd остаётся во внутренней
сети. После обновления script перезапускает daemon и ждёт PING. PING не подтверждает freshness:
Java adapter отдельно проверяет VERSION до и после каждого scan и отклоняет старую/будущую/изменившуюся
signature database. Без регулярных updates scan станет UNAVAILABLE. Production требует постоянного
updater и alerting, а не только ручного запуска.

MinIO служит только local/integration fixture. Его source и toolchain pinned, включая patch после
старого Docker release; архивированный upstream не является выбором commercial hosting. У него
синтетический ACL без owner ID, поэтому `minio-fixture=true` разрешает только точную fixture форму и
запрещён в `prod`. Production provider обязан пройти строгие private policy/owner ACL/versioning checks.

## Configuration contracts

Все limits проверяются при startup. Secrets поступают из environment или secret manager.
`.env.example` содержит placeholders; его значения не предназначены для запуска.

| Prefix / property | Default | Назначение |
|---|---|---|
| profiles `s3`, `clamd`, `renderer` | выключены | Явный выбор real adapters |
| `cadence.evidence.production-approved` | false | Operational/product approval |
| `egress-policy-acknowledged`, `retention-policy-acknowledged` | false | Явное подтверждение deployment policy |
| `url-worker-enabled`, `file-scan-worker-enabled` | false | Durable fetching/scanning schedulers |
| `artifacts-enabled`, `cleanup-enabled` | false | Извлечение и orphan reconciliation |
| `s3.endpoint`, `s3.bucket`, `s3.region` | endpoint/bucket обязательны | Private provider, production HTTPS |
| `s3.access-key`, `s3.secret-key` | credential chain | Парные credentials или workload identity |
| `s3.operation-timeout`, `s3.connect-timeout` | PT15S / PT3S | Общий operation budget и connect/acquisition |
| `s3.max-concurrent-operations` | 8 | Глобальное ограничение adapter I/O |
| `s3.allow-insecure-local`, `s3.minio-fixture` | false | Оба shortcuts запрещены в prod |
| `clamd.host`, `clamd.allowed-cidrs`, `clamd.port` | endpoint/CIDRs обязательны / 3310 | Только private pinned addresses |
| `clamd.private-channel-acknowledged` | false | Требует защищённый private channel |
| `clamd.deadline`, `clamd.max-concurrent` | PT30S / 2 | INSTREAM и VERSION hard deadline / capacity |
| `clamd.signature-max-age` | PT48H | 1 minute–72 hours; будущие timestamps запрещены |
| `renderer.host`, `renderer.allowed-cidrs`, `renderer.port` | endpoint/CIDRs обязательны / 8081 | Private bytes-only endpoint |
| `renderer.token`, `renderer.private-channel-acknowledged` | обязательны | CSPRNG token 32–128 chars и network acknowledgement |
| `renderer.deadline` | PT20S | Не более30s; service child deadline 10s |
| `artifacts.max-scan-age` | PT24H | Последний CLEAN для текущих bytes/version |
| `registration.max-workspace-evidence` | 1000 | Включает TEXT, pending/failed URL и FILE |
| `max-concurrent-uploads`, `max-non-multipart-bytes` | 2 / 131072 | Admission до multipart parsing и bounded JSON/form body |
| `registration.max-workspace-registrations-per-hour` | 100 | Новые Evidence rows; duplicate не расходует row budget |
| `lifecycle.max-workspace-bytes`, `max-workspace-objects` | 512 MiB / 1000 | Объекты и upper-bound reservations |
| `lifecycle.max-workspace-writes-per-hour` | 100 | Все generations, включая failed/deleted |
| `lifecycle.write-deadline`, `orphan-retention` | PT2M / PT24H | Publication expiry и orphan TTL |
| `lifecycle.deletion-drain`, `tombstone-sweep`, `cleanup-batch` | PT5M / PT1H / 20 | Повторная очистка и ограниченный batch |
| `social.enabled`, `social.deadline` | false / 10s | Approved account imports, общий snapshot deadline |
| `social.max-requests-per-hour`, `social.max-workspace-calls-per-hour` | 100 / 100 | HTTP adapter и durable workspace attempt caps |
| `social.max-provider-calls-per-hour` | 50 | Durable provider-wide snapshot cap across replicas; <=100 HTTP calls/hour |

Environment aliases `CADENCE_URL_FETCH_WORKER_ENABLED` и `CADENCE_FILE_SCAN_WORKER_ENABLED` явно
связаны с соответствующими `cadence.evidence.*` properties в `application.properties`.
Остальные properties поддерживают Spring relaxed environment binding.

В production запрещено сочетать `prod` с `local`, `test` или `postgres-test`. Активные providers/workers
требуют одновременно `s3` и `clamd` и все operational acknowledgements. Artifacts дополнительно требуют
`renderer`. Bare `prod` сохраняет disabled evidence gate. Суммарные I/O deadlines с DB margin должны
умещаться в write reservation и двухминутный Job lease; слишком длинная chain запрещает startup.

PostgreSQL runtime connections получают fixed `statement_timeout=10s` и `lock_timeout=5s`
через `PostgreSqlEvidenceConnectionPolicy`; несовместимый connection-init SQL отклоняется.
Default application transaction timeout —10s. Это ограничивает ожидание DB locks наряду
с deadlines внешнего I/O; quota transactions явно используют READ_COMMITTED, проверенный
по фактическому JDBC connection. Production migrations выполняются отдельным release job.

Social bindings серверные: Brand UUID, provider, account ID, token, expiry, scopes, reviewed API version
и `approved=true`. Клиент передаёт только enum provider. Не храните secrets в evidence JSON, URL,
Git или exception message. Полный binding contract: [approved social evidence](approved-social-evidence.md).

## IAM, database и network

- S3 runtime identity: private bucket inspection и Put/Get/Delete только `quarantine/*`, дополнительно Put/Get без Delete для retained `media/*` по ADR0034; нельзя менять
  bucket policy/ACL/versioning, создавать buckets или получать admin credentials. AWS policy и
  отличие MinIO fixture перечислены в [storage contract](s3-storage.md).
- Bucket versioning запрещён данным contract: Delete должен удалять bytes, а не создавать delete
  marker с сохраняющейся billable version. Encryption at rest/KMS, region, backups и object-level
  operator access проверяются при deployment.
- Runtime PostgreSQL role не владеет schema и не имеет DDL/admin rights. Миграции выполняет отдельный
  release role; runtime использует Hibernate `validate`. Immutable provenance защищена privileges и
  DB triggers. DELETE разрешён для старых unbound provider attempts и трёх Brand collection tables,
  которые Hibernate заменяет при редактировании Brand; Evidence/artifact/scan history удалить нельзя.
- Clamd INSTREAM не использует TLS: защищённая private connection обязательна. Между недоверенными
  hosts нужен mTLS tunnel/sidecar или эквивалентная network isolation. Клиентский request не выбирает
  host/CIDR/port. Renderer дополнительно требует token и stripped child environment.
- Renderer nonroot, read-only root, cap-drop ALL, no-new-privileges, отдельный user/PID/mount/network
  и IPC namespace, inherited readonly/masked Docker `/proc`, Chromium sandbox, JS/service workers
  выключены, external requests запрещены. Sole token-bearing server PID1 non-dumpable; child env/FD
  scrubbed. Sensitive parent proc access проверяется startup negative probe; Linux negative tests
  подтверждают отказ native ptrace/process-VM. Outer CapBnd=0; child CapEff/Prm/Inh/Amb=0,
  а reset bounding set в новой user namespace не даёт privileges в parent namespace. Seccomp
  допускает только необходимые namespace syscalls без host SYS_ADMIN. Нет `--no-sandbox` fallback.

### Renderer operation/recovery contract

В renderer запрещены Docker exec healthchecks и другие token-bearing соседние processes.
Readiness container имеет собственный environment без renderer token и только HTTP polls `/health`.
Нельзя добавлять `init: true`, Tini или другой parent: server обязан быть container PID1.
Trusted Python child supervisor — PID1 собственного namespace; он проверяет UID10001/PID1,
устанавливает и проверяет dumpable0/CORE0 до input/parser. Не делайте fresh `/proc` mount:
inherited Docker masks защищают kernel surfaces, а non-dumpable server и negative access checks
защищают его credentials.

Обязательны read-only root и fresh private tmpfs: `/tmp`≤128MiB, `/dev/shm`≤256MiB с
rw,noexec,nosuid,nodev и mount root `/`. `/dev/mqueue` закрывается отдельным tmpfs
ro,noexec,nosuid,nodev,size=4k,mode=000; startup проверяет mount metadata и actual creation denial.
Нельзя добавлять host binds на эти roots. Mountinfo не различает bind всего host tmpfs с root `/`;
безопасность этой границы требует reviewed launcher configuration. Compose/Java fixtures задают
точные private tmpfs, без host binds. HTTP body watchdog 20s, worker deadline 10s, bounded reap 2s.

При normal worker completion и successful teardown trusted parent очищает `/tmp` и `/dev/shm`;
тот же purge выполняется перед каждой job, при глобальном one-job admission. Combined roots budget
2s для каждого pre/post purge, depth128/100000 entries и descriptor-relative no-follow walk
защищают от cleanup DoS и traversal,
включая mode-000 directories. Любой worker timeout, signalled `unshare` supervisor
(`returncode < 0`), unexpected killpg/reap failure
либо cleanup failure делает server permanently
poisoned: новые jobs и health возвращают503. Требуется recreate renderer с fresh tmpfs, а не restart
process внутри старых mounts; после recreate дождитесь startup preflight/readiness заново.
Не сохраняйте renderer writable roots в persistent/shared volumes между instances или tenants.

`unshare --kill-child` зависит от mutable child PDEATHSIG. Kill/reap outer wrapper после timeout
не является proof завершения всех descendants. Поэтому `POISONED` устанавливается до teardown,
purge полностью пропускается и новые jobs запрещены даже после successful outer reap.
Group kill/reap остаётся best effort до уничтожения всего container. Alert на `/health`503 и
operator recreate обязателен: malicious или slow document может намеренно лишить instance
доступности. Не возвращайте его в pool простым restart Python process или сбросом health flag.

Manual `docker exec ... namespace_probe.py` и Python exec suite допустимы только в quiescent fixture,
без concurrent jobs/caller traffic. Автоматический production/local startup использует in-process
probe и не требует остановки app ради exec. Нельзя запускать shell/debug/health helper с token env
рядом с hostile job. GenericContainer exec probes выполняются только до payloads в последовательных
fixtures. Исправленная revision прошла Linux Python 29/29 без skips, включая timeout/signalled/
killpg/reap negatives; real Chromium screenshot 1280×720 повторно прошёл после child PID1 protection.
Actual preflight и scoped AppArmor compiler также ранее passed. Полный hardened Maven593 tests
прошёл без failures/errors;3 Windows skips закрыты Linux35/35 без skips. Свежий JAR Compose
startup passed: app/gateway/renderer-ready healthy, health200 и unauthenticated workspaces401;
[CI36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248), source SHA
`ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab`, подтвердил enforcing AppArmor load,
preflight/Chromium screenshot, Maven593 без failures/errors/skips и Python29/29.
Исторический Ubuntu24/26 proc-mount config завершался отказом; blocker устранён текущей revision.
CI host qualification не заменяет validation выбранного внешнего production host.

## Retention и recovery

Referenced Evidence/artifacts сохраняются до отдельного authorized deletion policy. Автоматически
удаляются только managed orphans после expiry+TTL. Cleanup сначала commit DELETING, затем удаляет
bytes вне transaction; publication после этой границы запрещают application и PostgreSQL.
TOMBSTONED records не удаляются: повторный delete после drain освобождает quota, дальнейшие sweeps
убирают delayed PUT. Это eventual physical cleanup с синхронным запретом повторной публикации.

Исторические shared keys нельзя удалять по возрасту. Они консервативно учитываются в quota и требуют
audited adoption. Provider-attempt ledger хранится минимум90days; records с trusted social origin
сохраняются с его history. Backup/restore должен включать DB, private object bytes и migration versions.

При crash между PUT и final commit повторите Job только через обычный claim/lease recovery. Не
помечайте object referenced вручную и не удаляйте registry rows. При provider outage jobs bounded
retry; orphan quota остаётся удержанной до подтверждённого cleanup. При signature outage content
остаётся quarantined. Пример runtime permission restrictions находится в
`docker/postgres/runtime-permissions.sql`; local migration superuser не является production template.

## Observability без утечки данных

Durable Jobs содержат status, bounded attempts/lease и safe error; scan/artifact rows содержат immutable
provenance. Cleanup логирует UUID generation и безопасный код ошибки, а не credentials/content/URLs.
Operator alerts нужны на DEAD jobs, old RUNNING leases, stale signatures, provider errors/429,
quota exhaustion, oldest-due cleanup age, tombstone backlog и disk/bucket limits.

```sql
SELECT type, status, count(*) FROM jobs GROUP BY type, status;
SELECT state, quota_released, count(*), sum(retained_bytes)
FROM quarantine_objects GROUP BY state, quota_released;
SELECT min(next_cleanup_at), count(*) FROM quarantine_objects
WHERE state IN ('DELETING','TOMBSTONED') OR (state='RESERVED' AND orphan_after <= clock_timestamp());
```

Это внутренние operator queries, а не public diagnostic endpoints. Ingress должен ограничивать body,
request rate и одновременные uploads до выделенного memory/CPU budget; Tomcat threads/connections
также ограничены. Application quotas не заменяют perimeter protection от unauthenticated floods.

## Deployment validation

До обработки реальных данных проверить selected S3 provider contract, TLS/KMS/IAM, region/privacy
policy, public-access drift, partial writes, lifecycle billing, signatures/updater isolation, private
channel enforcement, deployed seccomp/kernel namespace support, ingress limits, alerts, backup/restore,
live Clerk JWT/JWKS и реальные Instagram/TikTok credentials/scopes/approval/account bindings.
OAuth onboarding/refresh и real-account qualification отслеживаются отдельно в #32/#33.

Локальные tests доказывают application/DB contracts и real local provider interoperability.
Они не подтверждают существование внешней production инфраструктуры или прав на конкретный аккаунт.
