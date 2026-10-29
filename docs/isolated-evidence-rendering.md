# Isolated website screenshot and PDF text extraction

Исправленный Linux renderer gate: **29/29 Python tests, 0 skipped** (server 12, probe 8, ephemeral 9).
Trusted child PID1 protection и timeout/signalled-supervisor/killpg/reap poisoning regressions
прошли; real Chromium screenshot 1280×720 после supervisor protection passed. Namespace preflight
и scoped AppArmor compilation ранее также прошли. Полный hardened Maven gate прошёл:
593 tests, 0 failures/errors; 590 passed и3 Windows filesystem skips, выполненные отдельно
в Linux35/35 без skips. [CI36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248)
source SHA `ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab` подтвердил enforcing AppArmor,
preflight/screenshot, полный Maven593 с0 skips и Python29/29. Внешний production host требует
отдельной validation; full592 относится к исторической revision.

## Поведение и граница доверия

`EvidenceRenderer` принимает bytes из authorized quarantine source после проверки SHA-256 и
положительного malware scan. Он создаёт screenshot статического HTML и bounded PDF text.
Screenshot показывает downloaded HTML с inline CSS; JavaScript и все внешние resources отключены.
Это воспроизводимый snapshot source evidence, а не полностью интерактивная копия live website.
PDF extraction читает максимум первые 20 pages. Image OCR в этот контракт не входит.

`RendererEvidenceAdapter` вызывает две фиксированные операции private sidecar:
`POST /html/screenshot` и `POST /pdf/text`. Запрос не содержит remote URL, выбранный пользователем
путь, filename или executable command. Внешний caller не может выбрать renderer endpoint.
Endpoint и CIDR allowlist задаёт оператор, случайный API token передаётся в header, никогда в URL.

## Configuration и Compose

```properties
cadence.evidence.renderer.host=evidence-renderer
cadence.evidence.renderer.port=8081
cadence.evidence.renderer.allowed-cidrs=172.28.117.0/24
cadence.evidence.renderer.token=${CADENCE_RENDERER_TOKEN}
cadence.evidence.renderer.deadline=PT20S
cadence.evidence.renderer.private-channel-acknowledged=true
```

Bean включается Spring profile `renderer`. Configuration требует private CIDRs, deadline от
100 ms до 30 s, token из 32–128 base64url-compatible characters и явное подтверждение private
channel. Реальный token должен иметь минимум 256 bits случайности и храниться в deployment
secret. Token не попадает в `RendererProperties.toString()`. Production deployment использует
private isolated transport; при multi-host связи требуется защищённый transport tunnel.

Service `renderer` строится из `docker/evidence-renderer/Dockerfile`; полная configuration
находится в [Compose](../compose.yaml). Обязательные process/storage boundaries:

| Boundary | Configuration |
|---|---|
| Trusted server | UID10001, непосредственно container PID1; без Tini/`init: true` |
| Container | Read-only image, cap-drop ALL, no-new-privileges, reviewed seccomp |
| Writable storage | `/tmp` private tmpfs ≤128 MiB; `/dev/shm` private tmpfs ≤256 MiB; rw,noexec,nosuid,nodev |
| POSIX message queues | `/dev/mqueue` masked by tmpfs ro,noexec,nosuid,nodev,size=4k,mode=000 |
| Resources | 2 GiB memory, 1 CPU, 256 pids; one global renderer job |
| Readiness | Отдельный `renderer-ready` container без token, HTTP polling `/health` |

Server запускает обязательный namespace preflight до чтения token и открытия HTTP socket.
Он не стартует при неверном UID/PID1, Tini/init wrapper, отсутствующей namespace/proc/parent
защите или запрошенном, но не enforcing AppArmor profile. Перед чтением secret вызывается
`PR_SET_DUMPABLE=0`; перед каждой job `PR_GET_DUMPABLE` повторно проверяет этот invariant.
В самом renderer нет Docker exec healthcheck: такой соседний process наследовал бы container
environment с token. Readiness polling выполняется из отдельного token-free container.

Startup guard проверяет read-only image root и filesystem mount metadata: `/tmp` и `/dev/shm`
должны быть отдельными bounded tmpfs с `rw,noexec,nosuid,nodev`, mount root `/`; `/dev/mqueue`
должен быть tmpfs `ro,noexec,nosuid,nodev`, размером ≤4 KiB и mode 000. Создание message queue
через этот mount должно фактически отказать. Это закрывает ещё одно место хранения bytes
между jobs, которое не входит в purge двух writable roots.

Trusted launcher обязан создавать fresh private tmpfs и запрещать host binds на `/tmp`,
`/dev/shm` и `/dev/mqueue`. Mountinfo отвергает обычный host filesystem и subdirectory bind,
но не отличает bind целого host tmpfs с root `/` от private tmpfs. Это явная launch assumption:
reviewed Compose и Java fixtures используют точные tmpfs mounts без host binds; сам HTTP input
не может изменять container launch configuration. Нельзя расширять mounts при deployment.

Renderer port не публикуется на host. Private network изолирует HTTP service;
обработка самого untrusted content дополнительно выполняется без сетевых interfaces/routes.

## Runtime flow

```text
Authorised source + existing CLEAN scan
    → checksum-verified bounded quarantine bytes
    → pinned private HTTP request + unpredictable token
    → authentication, operation/content/body validation
    → one admitted operation; bounded purge of fixed /tmp + /dev/shm
    → server-generated temporary directory; scrubbed environment/FDs
    → new user + network + PID + mount + IPC namespaces; inherited protected /proc
    → trusted child Python PID1 sets dumpable0 before input/parser
    → fixed Chromium/Poppler operation with OS resource limits
    → pinned directory descriptor; regular, singly linked, bounded output file
    → normal child PID1 completion + reap; bounded purge of both writable roots
      or timeout/signalled-supervisor/killpg/reap failure → poison, no purge, recreate required
    → bounded PNG/UTF-8 response, or permanent fail-closed server state
    → Java validates media/version/framing/PNG pixels/CRC/dimensions or text length
    → fenced persistence and private quarantine artifact storage
```

Java имеет один operation slot, bounded DNS executor без очереди и monotonic deadline на DNS,
connect, write и read. Все DNS answers должны принадлежать разрешённым private CIDRs; соединение
закреплено за конкретным IP. Ответ ограничен 8 KiB headers, 3 MiB PNG или 64 KiB UTF-8 bytes.
Redirects, compression, chunked/ambiguous framing, duplicate headers, лишние bytes, отсутствие
EOF и неверная renderer version отклоняются. PDF result ограничен 16000 UTF-16 units и не может
содержать управляющие characters. PNG должен содержать корректные CRC, IHDR 1280×720 с RGB/RGBA
8-bit format, IDAT и завершающий IEND; arbitrary PNG metadata chunks не допускаются.
IDAT проверяется bounded `Inflater`: ровно 720 rows фиксированной ширины, допустимый filter byte,
полный zlib stream без лишних/truncated pixels и trailing compressed bytes. Память для одной
decoded row не превышает 5121 bytes; размер растра заранее фиксирован и не может стать bomb.

Python HTTP server обрабатывает один request последовательно, ограничивает listen backlog 8
и не создаёт unbounded request threads. Socket read timeout 3 s и absolute connection watchdog
20 s защищают header/body от slowloris, включая постоянно поступающие маленькие fragments.
Content-Length обязателен и не может повторяться; Transfer-Encoding запрещён.
Body ограничен 2 MiB HTML или 10 MiB PDF. Worker deadline — 10 s; каждый purge имеет отдельный
combined-root budget 2 s до job и 2 s после job. Kill/reap дополнительно ограничен 2 s.
Raw content, token, parser diagnostics и user filenames не логируются.

Каждая операция запускает фиксированный `unshare` argv, без shell. Content сохраняется под
именем `input` в server-generated private temporary directory. Child получает минимальный
environment без API token. User mapping сохраняет non-root UID 10001. Новый network namespace
содержит только выключенный loopback; child проверяет отличие namespace от server и interfaces.
Каждая job имеет новые user/network/PID/mount/IPC namespaces. `/proc` наследуется от Docker:
`--mount-proc` не используется, readonly submounts и masked paths сохраняются. Этот proc mount
показывает container PIDs, включая trusted parent, но не host PIDs. PID syscalls child работают
в его новом namespace; trusted Python child обязан быть PID1. До чтения input и запуска parser
он проверяет UID10001/PID1, устанавливает и проверяет `PR_SET_DUMPABLE=0` и CORE0,
защищая namespace supervisor от native inspection/изменения
со стороны parser того же UID. Фактический exit namespace PID1 заставляет kernel завершить
descendants, включая detached Chromium. Однако kill/reap внешнего `unshare` после timeout
не доказывает этот exit: `--kill-child` использует изменяемый child `PR_SET_PDEATHSIG`.

Поэтому любой worker timeout, signalled `unshare` supervisor (`returncode < 0`) либо unexpected
killpg/reap failure переводит
server в `POISONED` **до cleanup**. Process-group kill и outer reap выполняются только best effort;
purge и последующая job запрещены даже если внешний wrapper уже reaped. Это защищает следующий
tenant от потенциально живого parser. Восстановление — recreate всего container с fresh tmpfs,
а не сброс флага/restart Python process. Медленный или hostile документ может требовать operator
recovery; availability здесь ограничена ради tenant isolation.

Inherited proc не считается защитой token сам по себе. Startup negative preflight проверяет
фактический отказ доступа к parent `/proc/<pid>/environ`, `mem`, `fd`, `fdinfo`, `map_files`,
root/cwd/exe и namespace links, включая task aliases. Linux negative tests дополнительно
подтверждают отказ native `ptrace` и `process_vm_readv` к non-dumpable parent.
Probe проверяет сохранённые Docker masks,
readonly paths, отсутствие token/canary в child environment, distinct namespaces и privileges.
Других token-bearing processes в renderer быть не должно.

Outer container требует нулевых capabilities, включая bounding set `CapBnd=0`. После exec child
проверяет `CapEff`, `CapPrm`, `CapInh`, `CapAmb=0`. При создании новой user namespace Linux
может восстановить полный child bounding set: это legitimate namespace-scoped состояние,
не host privilege и не capabilities в parent namespace. Поэтому нулевой `CapBnd` проверяется
у outer server, а child проверяется по четырём активным/наследуемым sets и actual denied parent
operations. См. [Linux user namespaces](https://man7.org/linux/man-pages/man7/user_namespaces.7.html).

Server закрепляет descriptor исходной temporary directory до запуска child и не передаёт его
subprocess (`O_CLOEXEC`, `close_fds=True`). Output открывается относительно этого descriptor
с `O_NOFOLLOW|O_NONBLOCK`; `fstat` требует regular file, один hard link и bounded length.
Подмена output symlink, FIFO или ancestor UUID directory не заставляет server читать свой
`/proc/self/environ` либо ждать pipe. PDF cutoff считает UTF-16 units, одинаково с Java;
supplementary Unicode characters сохраняются целиком.

Перед и после каждой job trusted parent очищает **оба фиксированных roots** `/tmp` и `/dev/shm`,
удерживая глобальный filesystem/job slot. Очистка после job допускается при normal worker
completion и successful teardown; после timeout/signalled-supervisor/killpg/reap failure она запрещена независимо от
outer-wrapper reap. Обычный положительный nonzero worker exit (например, отказ PDF parser)
возвращает safe unavailable, не считается signalled-supervisor failure и допускает normal cleanup.
Parser не выбирает cleanup path. Descriptor-relative no-follow traversal не открывает untrusted
files, удаляет symlinks/FIFOs/sockets как directory entries и pin-ит directories через `O_PATH`.
Owned mode-000 directories восстанавливаются через проверенный pinned inode. Общий budget двух
roots — 2 s, depth — 128, entries — 100000; foreign owner/device, mount boundary, отказ операции
или исчерпание budget завершают cleanup ошибкой. Это исключает чтение data предыдущего tenant
через persistent temporary file или shared-memory segment. Если cleanup не подтверждён либо
worker timeout, signalled supervisor или killpg/reap failure оставили termination неподтверждённой,
`POISONED` запрещает последующие jobs и `/health` возвращает503. Восстановление требует recreate
container с fresh tmpfs; сбросить флаг или продолжить с сохранёнными writable mounts нельзя.

Chromium запускается с `chromium_sandbox=True`, JavaScript disabled, service workers blocked,
downloads disabled, route abort для всех requests и CSP `default-src 'none'`. Включено только
inline CSS. Screenshot фиксированного viewport исключает unbounded full-page raster. Poppler
`pdftotext` получает fixed UTF-8/page arguments, CPU/virtual-memory/file/core/open-file limits.
Container cgroup дополнительно ограничивает общий memory/CPU/pids/temp объём, включая browser.

## Security review

| Threat | Protection | Verification |
|---|---|---|
| SSRF/remote resources/metadata через HTML | Новый network namespace, CSP, route abort, JS/service-worker disabled | Real screenshot с embedded HTTP URLs не вызывает spy server |
| Враждебный JavaScript меняет screenshot/крадёт данные | JS disabled, sandbox enabled, source bytes never navigated as URL | Real screenshot одинаков для static HTML и script-augmented HTML |
| Parser exploit читает token или внутреннюю infrastructure | Sole token-bearing server PID1, dumpable0, scrubbed child environment/FDs, inherited Docker proc protections и distinct namespaces | Actual startup preflight denies parent proc access; native ptrace/process-VM negatives и enforcing CI36833733248 passed; external production host validation separate |
| Следующий tenant читает остатки или встречает живой parser после timeout | Protected trusted child PID1, normal-completion purge fixed roots, readonly mode 000 mqueue mask; timeout/signalled-supervisor/killpg/reap failure poisons before cleanup, no future job until recreate | Linux 29 passed, including new uncertain-teardown negatives; real Chromium smoke 1280×720 passed |
| Parser подменяет output link и заставляет server читать secret | Pinned directory FD, no inherited descriptors, O_NOFOLLOW/O_NONBLOCK, regular/single-link/size checks | Реальные Linux symlink/ancestor/FIFO/hardlink/oversize regression tests внутри renderer container |
| Path/command injection | Fixed commands/operations; server temporary paths | Input/path/auth negative tests; no shell invocation |
| PDF decompression/resource bomb | Input bytes, 20 pages, CPU/memory/file limits, hard deadline and cgroup | Input/output bounds; real malformed PDF rejected |
| Daemon outage или недостоверный output | Exact length/media/version, strict UTF-8, bounded PNG pixel stream/CRC/dimensions, fail-closed errors | Fake HTTP malformed/redirect/oversize/EOF/IDAT and real wrong token/namespace failure |
| Unauthorized sidecar abuse/slowloris | Secret token, fixed paths, serial admission/backlog, absolute watchdog | Python auth/body/operation/failure tests |

Defense in depth сохраняет source quarantine и existing scan checks даже при отказе renderer.
Generated PNG остаётся private quarantine artifact и должен проходить собственную scan policy;
extracted text остаётся untrusted input для frontend/AI, requiring output encoding/instruction separation.

## Dependencies и seccomp provenance

Base image: официальный Microsoft Playwright Python `v1.63.0-noble`, digest
`sha256:72bd171a9ffc2b4b59532aaa6210e21014d07093120dc25528870c0b840da1f0`.
Playwright 1.63.0 и его Python dependencies закреплены в `requirements.txt`; Chromium нужен
для реального rendering, JDK/jsoup не реализуют browser layout. Poppler — отдельный поддерживаемый
native PDF parser из Ubuntu security repository. Installed image packages требуют периодического
vulnerability scan/update. При распространении container соблюдайте лицензии bundled browser,
Python libraries и GPL-licensed Poppler; приложение вызывает CLI, а не связывает GPL library в Java.

`seccomp.json` взят из Apache-2.0 проекта Microsoft Playwright, pinned tag
[`v1.63.0/utils/docker/seccomp_profile.json`](https://github.com/microsoft/playwright/blob/v1.63.0/utils/docker/seccomp_profile.json).
Это Docker default-deny profile с разрешёнными `clone`, `setns`, `unshare` для browser sandbox.
Проект добавляет `pidfd_open`/`pidfd_send_signal` для `unshare --kill-child`, `chroot` для Chromium
sandbox и необходимые mount syscalls для его внутренней sandbox. Renderer сам не перемонтирует
`/proc` и сохраняет Docker masks. Kernel permissions и отсутствие outer
capabilities продолжают запрещать privileged host operations; SYS_ADMIN не добавляется.
Production runtime должен поддерживать non-root user/PID/network/mount namespaces. Никакого
`--no-sandbox` fallback при incompatibility нет: parser возвращает safe unavailable.

### AppArmor на Ubuntu 24.04 и более новых hosts

Ubuntu ограничивает создание unprivileged user namespaces через AppArmor. Разрешение syscall в
seccomp само по себе не разрешает его во втором security layer. Для renderer предусмотрен отдельный
enforcing profile `docker/evidence-renderer/cadence-evidence-renderer.apparmor`, основанный на
[Moby v28.0.4 docker-default](https://github.com/moby/moby/blob/v28.0.4/profiles/apparmor/template.go).
Он сохраняет proc/sys restrictions, ограничение ptrace/signal одним profile и добавляет `userns create`.
В ABI4 явно разрешён UNIX socket IPC; AF_ALG и AF_VSOCK запрещены.

Вместо общего `deny mount` разрешена только recursive-private propagation `/`;
fresh `proc` mount и его `umount` не разрешены. Остальные mounts default-denied. Это fixed syscall
текущего
[util-linux2.39 unshare](https://github.com/util-linux/util-linux/blob/v2.39.3/sys-utils/unshare.c).
Docker cap-drop ALL продолжает запрещать mount в исходном container/host namespace: необходимые
capabilities возникают только в новой user namespace, владеющей новой mount namespace.
Chromium и Poppler наследуют enforcing profile; переход в unconfined profile не разрешён.

На Linux host после review профиля оператор выполняет:

```sh
sudo apparmor_parser --replace docker/evidence-renderer/cadence-evidence-renderer.apparmor
sudo aa-status
docker compose --env-file .env.evidence-local --profile app \
  -f compose.yaml -f docker/evidence-renderer/compose.apparmor.yaml up --build -d
```

Автоматический startup использует in-process preflight sole server PID1, без Docker exec.
Manual `docker exec ... namespace_probe.py` допускается только для **quiescent diagnostic fixture**:
никаких concurrent jobs и caller traffic. Его process наследует container environment и сам может
стать доступным hostile child. Не запускайте exec healthchecks, shell/debug agents или другие
processes с token environment внутри работающего renderer. GenericContainer preflight/Python exec
suite допустимы только в quiescent fixtures с последовательными tests, до hostile payloads.

Override подключает только фиксированный named profile к renderer. Он не изменяет `docker-default`
и остальные containers. Docker Desktop не предоставляет AppArmor LSM и использует base Compose
без этого opt-in override. На AppArmor host с restrictive userns policy profile обязателен;
глобальный `kernel.apparmor_restrict_unprivileged_userns` не выключается, SYS_ADMIN не добавляется,
`apparmor=unconfined` и `--no-sandbox` не используются. Основания:
[Ubuntu user namespace restrictions](https://documentation.ubuntu.com/release-notes/24.04/) и
[Docker custom container profiles](https://docs.docker.com/engine/security/apparmor/).

`scripts/start_local_evidence.ps1` ждёт `renderer`/`renderer-ready` через Compose `up --wait`.
Проверка namespaces всегда выполняется самим server до token/HTTP; script не запускает Docker exec
в renderer. Для PowerShell7 на AppArmor Linux host оператор
после загрузки profile задаёт `$env:CADENCE_RENDERER_APPARMOR_PROFILE='cadence-evidence-renderer'`:
script сам выбирает фиксированный override и проверяет enforcing profile; другие имена запрещены.
Без переменной Windows/Docker Desktop path не меняет AppArmor configuration, но native
namespace/proc/PID/parent-secret preflight остаётся обязательным. `/health` не запускает parser;
200 возможен после successful startup preflight, 503 указывает на poisoned server. Это не заменяет
real parser smoke, load tests и qualification deployed-host sandbox.

CI загружает только этот profile, проверяет enforce и сохранение исходного userns sysctl,
затем до полного Maven запускает fixed `namespace_probe.py` и real Chromium smoke с production
caps/seccomp/resource bounds. Probe проверяет UID10001, отсутствие outer capabilities,
no-new-privileges, distinct user/net/PID/mount/IPC namespaces, isolated loopback, inherited proc masks,
denied sensitive parent proc access и AppArmor inheritance.
Он не принимает evidence bytes/token и выводит только fixed error category/exit code.
Integration fixtures повторяют probe до provider payloads; env
`CADENCE_RENDERER_APPARMOR_PROFILE=cadence-evidence-renderer` разрешает только это exact имя.
Raw parser stderr/content/credentials остаются скрытыми. Local Docker Desktop proof не подтверждает
AppArmor enforcement: необходим отдельный успешный CI/deployed-host gate.

### Почему сохраняется inherited `/proc`

Linux связывает proc mount с PID namespace его mounting process; поэтому унаследованный Docker
proc показывает container processes. Fresh proc поверх него скрыл бы установленные runtime
submount protections. OCI определяет masked paths как недоступные для чтения и readonly paths
как недоступные для записи. Решение проекта — сохранить их и независимо запретить sensitive
parent access, проверив это перед admission. Это инженерный вывод из [Linux PID namespaces](https://man7.org/linux/man-pages/man7/pid_namespaces.7.html)
и [OCI masked/readonly paths](https://github.com/opencontainers/runtime-spec/blob/main/config-linux.md#masked-paths).

`PR_SET_DUMPABLE=0` ограничивает tracing и меняет доступ к sensitive proc entries; фактические
denials проверяет probe, а не assumption об одинаковом UID. См. [Linux PR_SET_DUMPABLE](https://man7.org/linux/man-pages/man2/PR_SET_DUMPABLE.2const.html)
и [Linux proc filesystem](https://docs.kernel.org/filesystems/proc.html). Chromium также использует
несколько sandbox layers и отдельно рассматривает proc access как путь обхода seccomp. Поэтому
namespace/seccomp/parent protections проверяются совместно: [Chromium Linux sandbox](https://chromium.googlesource.com/chromium/src/%2B/refs/heads/main/sandbox/linux/README.md).

`unshare --kill-child` в [util-linux2.39 source](https://raw.githubusercontent.com/util-linux/util-linux/v2.39.3/sys-utils/unshare.c)
устанавливает child parent-death signal. Linux позволяет calling process сбросить этот signal;
поэтому успешный reap wrapper после timeout не является доказательством уничтожения namespace.
Инженерное решение проекта — protected trusted PID1 и irreversible failure state, не reuse
неопределённого sandbox. См. [Linux PR_SET_PDEATHSIG](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html).

Официальные references: [Playwright Docker isolation guidance](https://playwright.dev/python/docs/docker),
[Chromium sandbox launch option](https://playwright.dev/python/docs/api/class-browsertype#browser-type-launch-option-chromium-sandbox).

## Verification и deployment validation

```powershell
python -m unittest discover -s docker/evidence-renderer -p 'test_*.py'
.\mvnw.cmd '-Dtest=RendererEvidenceAdapterTest,RendererContainerIntegrationTest' test
.\mvnw.cmd '-Dtest=FullEvidenceProviderIntegrationTest' test
```

Real tests запускают Chromium sandbox/Poppler с теми же capabilities/seccomp/cgroup/tmpfs bounds.
Они проверяют screenshot, отсутствие JavaScript/network effects, PDF text, malformed PDF,
token denial и fail-closed работу без namespace permissions. Container suite запускает также
весь Python boundary suite на Linux; Windows invocation пропускает только две Linux descriptor
проверки, которые фактически выполняются в container. Fake HTTP tests проверяют output contracts,
malformed zlib pixel streams и deadlines. Full provider suite соединяет authorized Spring use case,
реальные PostgreSQL/Flyway, least-privilege MinIO, ClamAV и renderer: PDF text, quarantined website
PNG, EICAR attachment и encrypted-PDF rejection. Только public website download подменяется
offline byte fixture; generated bytes, scanners, storage и persistence реальные.
Exact results фиксируются общим gate отчётом #117.

Исправленная revision после timeout/PDEATHSIG finding проверена реальным Linux container:
**29 Python tests, 0 failures/errors/skips** — `test_server.py`:12, `test_probe.py`:8,
`test_ephemeral.py`:9. Server negatives проверяют poison before cleanup/reuse после timeout,
signalled supervisor и killpg/reap failure.
Probe tests проверяют fail-closed parent-access/proc/namespace и filesystem/mqueue boundaries;
ephemeral tests — fixed-root cleanup, no-follow/mode 000, combined budget и poisoning.
Actual startup preflight и scoped AppArmor compiler ранее прошли. Real Chromium screenshot 1280×720
повторно прошёл после trusted child PID1 protection. Local logs:
`var/issue117-final-renderer-python.log`, `var/issue117-final-renderer-smoke.log`.
Полный hardened Maven gate: BUILD SUCCESS,593 tests,0 failures/errors,590passed+3Windows skips;
Linux filesystem35/35 без skips. Full provider7/7 и renderer integration6/6 passed,
mandatory PostgreSQL/evidence report gates passed без required skips. Log:
`var/issue117-final-hardened-verify.log`. Свежий JAR Compose startup passed: app/gateway/
renderer-ready healthy, health200 и unauthenticated workspaces401. Enforcing CI36833733248
для source SHA `ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab` прошёл: AppArmor load,
namespace preflight, real Chromium screenshot, Maven593 без failures/errors/skips и Python29/29.
Исторические Ubuntu24/26 runs отказали прежней fresh-proc configuration; её blocker устранён.
CI fixture не квалифицирует внешний production host. Полные факты: [verification](verification.md).

Tests не доказывают отсутствие новых kernel/browser/native parser vulnerabilities и не заменяют
production runtime validation. Перед user data deployment проверяются patched host kernel,
изоляция private HTTP transport, token rotation, resource sizing/load, image scan, сеть только
app→renderer, orphan cleanup/retention для generated artifacts и восстановление worker leases.
