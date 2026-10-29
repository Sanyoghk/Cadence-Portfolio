# ADR-0007: private evidence pipeline, generations and quotas

Status: accepted for implementation, 2026-10-01. Production activation requires separate deployment validation.

## Цель и границы

Issue #117 сохраняет недоверенные материалы Brand приватно, проверяет точные bytes,
получает bounded metadata/PDF text/screenshot и сохраняет проверяемое происхождение.
`CLEAN` означает результат антивируса; материал остаётся недоверенным и не становится
автоматически `READY` или одобренным для AI.

## Решение

- Application зависит от `EvidenceObjectStorage`, `EvidenceObjectLifecycleStorage`,
  `EvidenceMalwareScanner`, `EvidenceRenderer`, `ApprovedSocialEvidenceSource`.
  S3 SDK, clamd protocol, HTTP и Poppler/Chromium остаются в infrastructure.
- Каждый PUT получает новую UUID generation, даже при одинаковом checksum.
  Workspace lock сериализует quota admission; короткие транзакции используют
  `READ_COMMITTED`, чтобы ожидавший count видел последний commit.
  PostgreSQL `clock_timestamp()` задаёт общий момент времени для leases и deadlines.
- Публикация и cleanup блокируют одну lifecycle row. После `DELETING` публикация
  запрещена навсегда. Два этапа удаления удерживают quota до drain; постоянные
  tombstones повторно удаляют поздний PUT. Cleanup не перечисляет весь bucket и
  не удаляет legacy keys или объекты с действующими references/jobs.
- S3 adapter требует private bucket, conditional immutable writes, checksum,
  bounded reads, TLS в production, deadlines и bounded concurrency. Versioned buckets
  запрещены: удаление latest version не гарантирует уничтожение старых bytes.
- ClamAV принимает bounded bytes через private INSTREAM. Версия сигнатур проверяется
  до и после scan; stale/future/changing/неполные результаты не дают `CLEAN`.
- Renderer получает только уже полученные bytes, проверенные свежим `CLEAN` receipt.
  Trusted server — единственный token-bearing process, UID10001/container PID1 без Tini;
  до token/HTTP обязательны dumpable0 и actual namespace/proc/parent-access preflight.
  Jobs получают отдельные user/net/PID/mount/IPC namespaces и наследуют Docker readonly/masked
  `/proc`, не делают fresh proc mount. Sensitive parent proc FD/environment/memory доступа нет;
  это проверяется negative probe, а Linux tests дополнительно проверяют отказ `ptrace` и
  `process_vm_readv`. Outer bounding capabilities=0; child после exec имеет
  CapEff/Prm/Inh/Amb=0, legitimate новый user-namespace bounding set не даёт parent privileges.
  Readiness HTTP polling вынесен в token-free container.
  Job работает без внешнего network, с отключёнными JS, CSP, Chromium sandbox и
  CPU/memory/process/output limits. Parent читает результат через
  заранее открытый directory descriptor и `O_NOFOLLOW`, проверяет regular file и
  единственную hard link. Trusted child Python PID1 устанавливает dumpable0 до input/parser.
  Kernel завершает descendants при exit namespace PID1, но timeout kill/reap outer `unshare`
  не доказывает этот exit: mutable child PDEATHSIG может отключить relay. Любой worker timeout,
  signalled wrapper (`returncode < 0`) или unexpected killpg/reap failure poisons server до cleanup; ни purge,
  ни следующий tenant не допускаются до recreate с fresh tmpfs. Normal completion допускает
  bounded no-follow purge обоих `/tmp` и `/dev/shm` roots. Startup guard требует read-only
  root, `/tmp`≤128MiB и `/dev/shm`≤256MiB как rw,noexec,nosuid,nodev tmpfs с mount root `/`;
  `/dev/mqueue` закрыт ro,noexec,nosuid,nodev,size 4k,mode 000 tmpfs. Launcher запрещает host binds:
  mountinfo не отличает bind целого host tmpfs от private root mount. Body watchdog 20s, worker 10s,
  каждый pre/post purge 2s. PDF text и screenshot имеют отдельный immutable provenance.
  Исправленная revision: Linux Python 29/29 без skips, включая timeout/PID1 regressions,
  real Chromium screenshot 1280×720 passed; preflight и AppArmor compiler также ранее passed.
  Полный hardened Maven593 tests,0 failures/errors passed;3 Windows skips выполнены в Linux35/35.
  [CI36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248), source SHA
  `ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab`: enforcing AppArmor/preflight/screenshot,
  Maven593 без failures/errors/skips и Python29/29 passed. Внешний production host validation
  остаётся отдельным deployment gate.
- Social endpoints используют server-side account binding и фиксированные approved
  API hosts. Token передаётся только в Authorization header. Долговечный receipt
  расходует quota до HTTP; фиксированная provider row сериализует global admission
  между Workspace и replicas. Клиент не выбирает account, API version или endpoint.

## Альтернативы и trade-offs

| Вариант | Проблема | Выбор |
|---|---|---|
| Shared checksum key и удаление после rollback | Cleanup может удалить bytes победителя concurrent registration | Уникальные generations, больше объектов до retention |
| Счётчик quota только в процессе | Несколько replicas независимо превышают общий лимит | PostgreSQL locks и append-only receipts; короткое ожидание lock |
| Renderer открывает URL | SSRF, redirects, scripts и внешние ресурсы обходят URL fetch checks | Только bytes, screenshot без JS/remote images; ограниченная визуальная точность |
| PDF parser в JVM | Malformed документ получает память/CPU application process | Отдельный process/container; требуется поддерживаемый Linux sandbox |
| Fresh proc mount в child | Скроет Docker readonly/masked submounts; Ubuntu отвергает mount | Inherited protected proc и проверяемые parent access denials; process metadata container может быть видна |
| Exec healthcheck внутри renderer | Новый process наследует token environment и становится соседом hostile parser | Sole server PID1 и token-free readiness container; manual exec только quiescent fixture |
| Только UUID job directory cleanup | Parser оставляет bytes/shared memory вне выбранной directory для следующего tenant | One global job, kill/reap и purge обоих fixed tmpfs roots; failure требует fresh container |
| Reuse после timeout и reap только wrapper | Mutable PDEATHSIG не позволяет доказать остановку всех descendants | Protected trusted PID1, poison before cleanup и recreate; slow/hostile document может потребовать operator recovery |
| Удалить lifecycle row после DELETE | Поздний PUT воскресит незарегистрированные bytes без учёта | Постоянный tombstone; требуется мониторить рост registry и backlog |
| Versioned bucket | DELETE оставляет предыдущие версии и стоимость | Reject startup и operations; production bucket выделяется отдельно |

## Threat model

Защищаем tenant files, metadata, API tokens, capacity и scan provenance.
Внешний OWNER может прислать hostile input, чужой ID, повторы и concurrent requests;
provider responses также недоверенные. Trust boundaries: HTTP body, storage bytes,
scanner verdict, renderer output, social JSON и финальная DB transaction.

Defense in depth: authorization/scoped queries → DTO/domain bounds → adapter bounds
→ checksum/receipt/lease → transactional ownership recheck → DB constraints/triggers.
При сбое scanner/renderer/storage операция не публикует непроверенный artifact;
уже записанные bytes остаются в quota и удаляются lifecycle worker.

## Проверки и эксплуатация

Negative contracts, concurrent quota/publication/cleanup tests и SQL bypass tests
выполняются на PostgreSQL. MinIO/ClamAV/renderer tests обязательны в CI;
missing/skipped report считается ошибкой. Полный список и фактические результаты:
[отчёт #117](../issue-117-completion.md).

Внешние TLS/IAM, region, backups/restore, signature updates, network policy,
OAuth approval и live provider entitlement проверяются перед deployment:
[operations](../evidence-operations.md). Аcknowledgement property фиксирует решение
оператора, но не доказывает наличие firewall или mTLS.
