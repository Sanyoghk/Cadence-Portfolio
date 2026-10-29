# Private S3-compatible quarantine storage

`S3EvidenceObjectStorage` реализует `EvidenceObjectStorage` и отдельную capability
`EvidenceObjectLifecycleStorage`. Application layer передаёт только server-generated key и
внутреннюю ссылку `quarantine:quarantine/...`. Bucket и endpoint выбираются конфигурацией сервера;
клиент не может выбрать другую инфраструктуру или получить public/presigned URL.

## Контракт и архитектура

```text
Authorized registration / worker
    → durable write intent with unique generation
    → private bucket validation
    → conditional single-part PutObject
    → original complete bytes + SHA-256 metadata
    → database registration / durable scan job

Durably claimed orphan / retention generation
    → private bucket validation
    → idempotent DeleteObject
    → durable cleanup completion
```

Storage adapter не определяет ownership, срок хранения или право удаления. Эти решения
принадлежат application/domain layer и БД. Уникальная generation и durable cleanup claim
обязательны: удалять deterministic shared key по одному лишь возрасту опасно при конкурентной
регистрации. Adapter не выполняет bucket listing и не создаёт bucket автоматически.

Запись использует `If-None-Match: *`: один completed object никогда не перезаписывается этим
adapter. `412 Precondition Failed` считается idempotent success только если прочитанные
существующие bytes полностью совпадают. `409`, timeout и другие ошибки означают safe failure;
durable job/write-intent определяет последующую попытку. SDK не выполняет скрытые retries.
Ссылки не содержат endpoint, credentials или публичный URL.

Чтение ограничено `1..10 MiB`, проверяет declared length, реальную длину и SHA-256 metadata.
Пустой, truncated, oversized object или missing/tampered checksum отклоняются. Worker дополнительно
сравнивает checksum с evidence provenance из БД. Это защищает целостность даже если оператор
изменил object напрямую.

## Конфигурация

Активировать профиль `s3` вместе с базовым `local`, `test` или `prod`. Для production нужны
`prod,s3,clamd`; одновременное смешивание development security profile с `prod` запрещено
глобальной profile validation.

| Property `cadence.evidence.s3.*` | Значение / ограничение |
| --- | --- |
| `endpoint` | Полный `https://host[:port]`; без userinfo/path/query/fragment |
| `region` | По умолчанию `us-east-1`, lowercase region identifier |
| `bucket` | Dedicated lowercase bucket, 3–63 символа, без dots |
| `access-key`, `secret-key` | Оба заданы либо оба отсутствуют; отсутствие использует стандартный AWS credentials provider chain |
| `allow-insecure-local` | По умолчанию false; HTTP разрешён только без `prod`, с явным true |
| `minio-fixture` | По умолчанию false; точный synthetic private ACL MinIO допустим только без `prod` |
| `operation-timeout` | По умолчанию `15s`, диапазон `1s..60s` |
| `connect-timeout` | По умолчанию `3s`, диапазон `1s..10s`, не больше operation timeout |
| `max-concurrent-operations` | По умолчанию 8, диапазон `1..64` |

Properties не имеют generated `toString` с credentials. Ошибка configuration validation
содержит фиксированное сообщение без значения endpoint/secret. Production не может включить
plaintext local opt-in. TLS certificate validation остаётся включённой.

Одна semaphore ограничивает write/read/delete и bucket checks вместе. При исчерпании capacity
операция сразу завершается `UNAVAILABLE`, не создавая неограниченную очередь. Один monotonic
deadline охватывает bucket checks и object I/O. Каждый SDK request получает оставшееся время,
connection/socket/acquisition timeouts ограничены. Для streaming body отдельно установлен
watchdog `abort`: SDK call timeout сам по себе завершается после response headers. Oversized
response немедленно abort, без draining body. Все provider errors превращаются в фиксированные
`QuarantineReadFailure` codes без raw cause.

## Privacy, versioning и least privilege

При startup и перед каждой операцией проверяются:

1. Bucket существует и доступен через `HeadBucket`.
2. Bucket policy отсутствует (`NoSuchBucketPolicy`) либо содержит исключительно `Deny`
   statements. Любой `Allow` отклоняется, даже если он кажется частным: ограниченный поддерживаемый
   контракт исключает ошибочное определение anonymous/cross-account доступа.
   Duplicate JSON keys, trailing JSON, nesting больше 32 и malformed policy отклоняются.
3. Bucket ACL содержит только canonical owner grants. `AllUsers`, `AuthenticatedUsers`,
   unknown group и foreign canonical user отклоняются.
4. Versioning никогда не включалось: и `Enabled`, и `Suspended` отклоняются. Иначе обычный delete
   создал бы marker и сохранил sensitive historical versions после retention.

Ошибка/unsupported API/AccessDenied проверки приводит к fail-closed startup или операции.
Dedicated bucket должен предоставлять policy через identity IAM, а не `Allow` bucket policy.
Поддерживаются S3-compatible providers, реализующие перечисленные APIs и conditional writes.
Это явный ограниченный контракт, а не предположение о произвольном S3-compatible продукте.

Минимальные runtime IAM actions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket", "s3:GetBucketAcl", "s3:GetBucketPolicy", "s3:GetBucketVersioning"],
      "Resource": ["arn:aws:s3:::YOUR_PRIVATE_BUCKET"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::YOUR_PRIVATE_BUCKET/quarantine/*"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject"],
      "Resource": ["arn:aws:s3:::YOUR_PRIVATE_BUCKET/media/*"]
    }
  ]
}
```

`ListBucket` требуется для `HeadBucket`; приложение не предоставляет listing API. Runtime
credentials не могут создавать bucket, менять policy/ACL/versioning, администрировать service
accounts или читать/писать вне разрешённых quarantine/media prefixes. Media prefix допускает только Put/Get, без Delete;
private demo asset admission и human review описаны в [ADR0034](adr/0034-private-demo-media.md). Account public-access-block и network isolation
должны обеспечиваться инфраструктурой; adapter повторяет проверяемые bucket safeguards, но не
может предотвратить последующую злонамеренную административную смену настроек. Проверки и
single operation не являются distributed transaction с IAM changes.

## MinIO local/integration fixture и supply chain

`docker/minio/Dockerfile` собирает server из official security release
[`RELEASE.2025-10-15T17-29-55Z`](https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z),
commit `9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a`, source archive SHA-256
`45521908307306e925c98d629e1c17d78c8b72b6ee242b1bfb1409f7d8ee5841`.
Эта release исправляет session-policy privilege escalation. Последняя готовая community image
2025-09-07 предшествует исправлению и не используется.

`mc` собран из official signed release
[`RELEASE.2025-08-13T08-35-41Z`](https://github.com/minio/mc/releases/tag/RELEASE.2025-08-13T08-35-41Z),
commit `7394ce0dd2a80935aded936b09fa12cbb3cb8096`, archive SHA-256
`95cd293c7119f16921a6dc515a1fb74a2227f19fd994b9c8b770a154e802ac44`.
Полные upstream SHA проверены по official release pages; hashes вычислены по HTTPS downloads
этих immutable commits. `ADD --checksum` отклоняет последующее изменение archive bytes.

Builder — official Go `1.27.1-alpine`, pinned manifest digest
`sha256:8a5910f31396cd4d89662f56c68b3ae31d374308270a1c3bd96672ee5ed43414`; supported Go version
проверена по [official download page](https://go.dev/dl/), manifest по Docker registry.
Runtime — official Alpine `3.24.0`, digest
`sha256:a2d49ea686c2adfe3c992e47dc3b5e7fa6e6b5055609400dc2acaeb241c829f4`.
Go dependencies используют upstream `go.mod`/`go.sum`, `-mod=readonly` и checksum database.
Runtime user `10001:10001`; secrets передаются извне, default passwords отсутствуют.

MinIO server и mc community repositories архивированы upstream в 2026 году. Эта сборка
используется только как изолированный local/integration fixture, не как рекомендованный
commercial storage deployment. Production должен выбрать maintained provider, пройти live
contract tests и vulnerability review. Pinned patched source не доказывает отсутствие всех новых
vulnerabilities в архивированных transitive Go dependencies.

MinIO не реализует bucket ACL: `GetBucketAcl` возвращает синтетические пустые owner/canonical IDs
и единственный `FULL_CONTROL` grant; `PutBucketAcl(PUBLIC_READ)` не реализован. Для этой
local-only capability требуется `minio-fixture=true`. Допускается исключительно точная
synthetic private форма; дополнительные grants, group, foreign ID или другая permission
отклоняются. Проверки policy и versioning остаются обязательными перед каждой операцией.
Production startup отклоняет этот флаг даже при HTTPS. Для AWS/другого production provider
по умолчанию требуется настоящий canonical owner ID. MinIO IAM не поддерживает action
`s3:GetBucketAcl`; local runtime policy его не включает. Это явно ограниченный fixture contract,
а не ослабление production ACL validation или доказательство обнаружения ACL drift в MinIO.

AWS SDK v2 `2.55.7` выбран из [official releases](https://github.com/aws/aws-sdk-java-v2/releases)
и закреплён BOM. SDK выполняет стандартное SigV4 signing/TLS/protocol handling; реализовывать
собственную криптографию для S3 нельзя. Подключены только S3 и Apache sync HTTP client,
неиспользуемый Netty async client исключён. Новая dependency требуется для поддерживаемого
production S3 protocol и conditional writes; существующий filesystem/JDK не реализует этот
контракт. [Official conditional write semantics](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html)
и [retry strategy API](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/retry-strategy.html)
определяют используемый механизм.

## Проверки и граница доказательства

`S3EvidenceObjectStorageTest` проверяет immutable conditions, exact duplicate bytes, checksum,
safe errors, malformed keys, public/cross-account ACL, policy drift, versioning и concurrency.
`S3EvidenceStoragePropertiesTest` проверяет production TLS, секреты и fail-closed startup.
`MinioEvidenceObjectStorageTest` проверяет реальный HTTP S3 protocol, конкурентные записи,
anonymous denied, least-privilege credentials, retained versions gate, bounded reads и tampering.

Точные фактические результаты полного Maven/Docker suite фиксируются в итоговом issue report.
Live commercial provider TLS chain, IAM identity, encryption at rest, account access-block,
backup deletion guarantees, firewall egress и administrative drift monitoring требуют отдельной
deployment validation на реальной инфраструктуре. MinIO integration success не подтверждает эти
production deployment properties.
