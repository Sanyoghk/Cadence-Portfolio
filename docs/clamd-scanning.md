# Private ClamAV malware scanning

## Назначение и контракт

`ClamdEvidenceMalwareScanner` реализует `EvidenceMalwareScanner` и передаёт только bytes из
проверенного quarantine object. Приложение не отправляет filename, URL, shell command или путь
на сервере ClamAV. `CLEAN` означает только отсутствие обнаруженной угрозы при этих сигнатурах;
это не одобрение evidence и не разрешение публиковать или исполнять содержимое.

Adapter включается профилем `clamd`, который можно сочетать с `local,s3` или `prod,s3`.
Development fixture scanner должен быть отключён при выборе `clamd`.

## Configuration

```properties
cadence.evidence.clamd.host=clamav
cadence.evidence.clamd.port=3310
cadence.evidence.clamd.allowed-cidrs=172.28.0.0/24
cadence.evidence.clamd.deadline=PT30S
cadence.evidence.clamd.signature-max-age=PT48H
cadence.evidence.clamd.max-concurrent=2
cadence.evidence.clamd.private-channel-acknowledged=true
```

`allowed-cidrs` задаёт оператор. Адрес должен принадлежать перечисленной сети; публичные,
multicast, unspecified и link-local сети, включая cloud metadata, запрещены. CIDRs допускаются
только внутри RFC1918, IPv6 ULA или явно выбранного loopback диапазона. Для приложения,
запущенного на host, local Compose port следует привязывать только к `127.0.0.1` и выбирать
`127.0.0.1/32`. В production используйте отдельную private network и максимально узкий CIDR.

Configuration отклоняется при старте без host/CIDR/явного подтверждения private channel, при
deadline вне 100 ms–60 s, signature maximum age вне 1 min–72 h, concurrency вне 1–8 либо
невалидном port. Подтверждение isolation — договор deployment, а не доказательство firewall.

## Runtime flow и ограничения

```text
QuarantinedEvidenceScanner: authorized object + expected SHA-256
    → bounded private storage read
    → checksum verification
    → ClamdEvidenceMalwareScanner: capacity slot
    → bounded DNS; проверить все ответы; закрепить один IP
    → VERSION на закреплённом IP; проверить age и provenance
    → INSTREAM: chunks ≤16 KiB; total ≤10 MiB
    → одна ASCII NUL-terminated response и EOF
    → VERSION на том же IP; исключить reload race
    → immutable MalwareScanReport
    → worker fencing и сохранение verdict/provenance
```

Один monotonic deadline распространяется на DNS, connect, write, read и обе VERSION проверки.
Nonblocking `SocketChannel`/`Selector` ограничивает backpressure на write, чего обычный
`SO_TIMEOUT` для read не обеспечивает. Максимум concurrent requests задаётся semaphore;
очереди ожидания нет. DNS имеет fixed daemon pool и `SynchronousQueue`: зависший DNS не
создаёт неограниченное количество потоков или задач. По истечении deadline socket закрывается.

Результат `CLEAN` допустим только при точном `stream: OK`, полном response и свежей одинаковой
VERSION до/после scan. `FOUND` возвращает `INFECTED`; signature name наружу не передаётся.
Ошибки, отсутствие EOF/NUL, лишние records, response более 512 bytes, provider outage,
неподдерживаемая VERSION, будущие/старые сигнатуры и reload uncertainty не дают `CLEAN`.
Retry policy остаётся в durable worker, adapter retries не выполняет.

Provenance ограничена `clamav-<semver>` и `daily-<number>@<ISO UTC timestamp>`. VERSION не
указывает timezone: daemon обязан работать с `TZ=UTC`; timestamps в будущем более чем на
5 минут отклоняются. Clock skew и FreshClam failures должны отслеживаться оператором.

## Threat model

Защищаем user evidence, quarantine isolation и достоверность scan verdict. Scanner вызывается
только внутренним worker после authorization/ownership и checksum checks; trust boundary —
private TCP response daemon и DNS/configuration оператора.

| Threat | Protection | Verification |
|---|---|---|
| Пользователь выбирает путь/command/remote scanner | Port принимает только byte array; endpoint из configuration; фиксированные VERSION/INSTREAM | Fake protocol server проверяет точные commands и bytes |
| DNS rebinding или публичный scanner | Все DNS ответы проверены against private CIDRs; subsequent connections используют один `InetAddress` | Mixed-public, metadata, multicast, outside-allowlist tests |
| Timeout, truncated reply или error ошибочно считаются чистым scan | Exact bounded response framing, EOF и fail-closed results | Malformed/truncated/oversized/multiple records; real outage |
| Устаревшие сигнатуры/reload нарушают provenance | VERSION до/после, freshness/future bound и equality | Stale/future/reload negative tests; real loaded DB stale clock |
| DoS через огромный upload или медленный daemon | 10 MiB bytes, 16 KiB chunks, hard deadline, bounded concurrency/DNS | Oversized bytes, slow response, blocked write и capacity tests |
| Утечка content или signature name через logs/errors | Adapter не логирует bytes/response; только фиксированные failure identifiers | Inspection, assertion отсутствия EICAR в provenance |

Обход одного слоя не делает bytes доступными пользователю: checksum, private storage,
durable scan worker fencing, evidence status и ownership checks остаются независимыми защитами.

## Docker и production deployment

Используется официальный `clamav/clamav:1.4.6` с закреплённым digest:
`sha256:57deb108fc4c72778aa83eafbca7bb7153e28c3f57c005afd38d31f16da86f23`.
1.4.6 включает опубликованные security fixes для parser memory errors;
[release notes](https://blog.clamav.net/2026/08/clamav-154-and-146-security-patch.html).
Регулярный image update/security review всё равно обязателен.

`clamd` TCP не имеет TLS и authentication. Этот deployment предполагает same-host/isolated
private network; чувствительные bytes нельзя передавать по общедоступной или недоверенной сети.
Для связи через несколько hosts требуется защищённый network transport (например private
authenticated encrypted overlay) и firewall только application→clamd. Свободный private IP сам
по себе не доказывает encryption/isolation. Public port publishing в production запрещён.

Оператор должен проверить: `TZ=UTC`, NTP, private network/firewall, отсутствие untrusted
clients, FreshClam updates, `StreamMaxLength`≥10 MiB, разумные `MaxScanSize`, `MaxFileSize`,
`MaxRecursion`, `MaxFiles`, `MaxThreads`, `MaxQueue`, daemon `ReadTimeout`/`CommandReadTimeout`,
`AlertExceedsMax=yes` (достижение internal scan limits не должно выглядеть как clean scan),
`AlertEncrypted=yes`, storage/temp capacity, memory/reload capacity, signature-age monitoring.
Антивирус не гарантирует обнаружение всех угроз; rendering/execution remains a separate trust boundary.

## Проверки и границы доказательств

```powershell
.\mvnw.cmd '-Dtest=ClamdEvidenceMalwareScannerTest,ClamdScannerConfigurationTest,ClamdContainerIntegrationTest' test
.\mvnw.cmd '-Dtest=FullEvidenceProviderIntegrationTest' test
```

Fake-server tests доказывают framing, DNS policy, timeout/backpressure и negative contracts.
Integration tests используют настоящий официальный ClamAV engine и real preloaded database,
проверяют clean bytes, стандартную безопасную EICAR test string, stale/future DB age, outage
и encrypted AES-256 PDF, который daemon с `AlertEncrypted=yes` не может объявить CLEAN.
Full provider tests дополнительно проверяют валидный PDF с EmbeddedFile stream, содержащим
канонический 68-byte EICAR attachment, до store/BrandEvidence/artifact publication.
Integration Clock задан относительно VERSION timestamp из image: это воспроизводимо проверяет
freshness policy и не ослабляет production `Clock.systemUTC()`. FreshClam для этих tests отключён,
чтобы не зависеть от стороннего CDN или даты сборки image.
В full provider tests только scanner freshness Clock anchored к loaded database VERSION;
leases, capturedAt, scan receipts и artifact timestamps используют настоящий PostgreSQL clock.

Эти tests не подтверждают, что production network защищена, текущая DB достаточно свежая,
FreshClam CDN доступен либо production deployment прошёл нагрузку/восстановление. Такие проверки
выполняются в конкретном deployment перед разрешением реальных user uploads.

Источники протокола: [официальный ClamD protocol](https://docs.clamav.net/manual/Usage/ClamdProtocol.html),
[официальная Docker configuration](https://docs.clamav.net/manual/Installing/Docker.html).
