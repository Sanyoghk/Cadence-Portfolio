# ADR0036 — bounded human metrics imports

Статус: принято техническое решение в scope19. Baseline `9d3a60a`, issue #99/#107
прочитаны16 октября2026. Пользователь явно выбрал manual/CSV ingestion до platform adapters.
Задания14–18 qualified локально; их повторная реализация не требуется.

## Семантика

Raw origin MANUAL_JSON или CSV — утверждение человека, не verified provider response.
Immutable receipt содержит exact UTF-8 bytes, SHA256, schema `metric-interval-v1`, actor,
Brand, DB submission time, deadline, Job и row-to-observation links. Raw bytes не возвращаются
API, не исполняются, не интерпретируются как ссылки/formulas и не передаются AI/provider.
Normalized observation отдельно хранит stable ContentPost UUID/platform, source `observedAt`,
ingestion time, half-open interval `[windowFrom,windowUntil)`, checksum, counts и correction lineage.
Legacy `PostMetric`/`SocialPost` остаются неизменными; нули/старые createdAt не превращаются
в выдуманные verified metrics/window/source time. Новая API не смешивает эти наборы данных.

Все пять counters имеют unit COUNT: reach — заявленное число достигнутых accounts,
engagement — явно сообщённое суммарное число interactions, saves — сохранения,
signups — заявленные регистрации, profileToSiteClicks — заявленные переходы profile→site.
Это reported interval totals; не lifetime cumulative snapshots, не derived rates, не гарантия
одинаковой методики платформ. Нет автоматического суммирования engagement из других полей,
ограничения engagement<=reach, conversion формул или усреднения overlapping windows.
Future analytics20 обязана учитывать неизвестные поля, overlap, source quality и sample size.
Missing/null/пустая CSV cell = unknown; явный0 = known zero. Хотя бы один counter обязателен.
Диапазон каждого целого0..10^12; fractions, negatives, scientific notation и overflow запрещены.

Natural identity — tenant→Brand→ContentPost/platform + observedAt; платформа immutable на Post.
Interval и counts входят в value checksum. Точный повтор normalized values с новым receipt
ссылается на existing latest observation. Другие values требуют `correctionOf` exact current
observation UUID; append revision1..100, без rewrite/delete. Stale/foreign correction запрещена
даже при совпадении values. Конкурентные corrections сериализованы Brand/Post locks.
Повтор original correction с тем же Idempotency-Key возвращает original operation.
Одинаковый ключ другого actor/Brand — иной scope; тот же scoped key/иной raw digest —409.
Digest включает origin и exact bytes: изменение whitespace/CSV quoting требует нового key.
Within batch natural keys уникальны; весь batch публикуется атомарно или не публикуется.

Source time — UTC Instant с microsecond precision, windowFrom>=2000-01-01,
windowUntil<=observedAt<=submission DB time, interval<=366days. Future/invalid time rejected.
Out-of-order distinct historical observations разрешены: insertion order не означает latest
source time. Коррекция сохраняет natural identity; другой observedAt — новая observation.
Query — bounded half-open observedAt interval<=366days, page0..10000, size1..100;
по умолчанию latest correction для каждого observedAt, `history=true` включает все revisions.

Mapping только explicit current nonlegacy ContentPost UUID и matching INSTAGRAM/TIKTOK;
никаких URL/account/post-external-ID guesses. Draft Post допустим: manual assertion не доказывает
факт platform publication. Изменение creative revision или lock исторической Strategy не
уничтожает measurements stable Post identity. Current OWNER/account/membership и ACTIVE
Workspace/Brand обязательны при submit, worker publication и read. Никакого нового fetch,
AI evidence read, media delivery или creative approval эта операция не выполняет.

## API, bounds, recovery

JSON и CSV используют один endpoint/202 Job Location; maximum128KiB,200rows,11CSVcolumns,
128chars/field. Строгий UTF-8 без BOM; exact case-sensitive header, comma separator,
LF/CRLF, RFC double quoting. Ни XLSX, ни provider CSV formats автоматически не распознаются.
Две shared parser slots/instance с fail-closed503; JSON nesting<=4, string<=128, number<=16chars.
JSON unknown/audit fields, duplicate keys, enum ordinals, string/fraction numeric coercions,
trailing content и nested unexpected values rejected. Parser вне transactions; short admission
проверяет mapping и bounds, сохраняет raw receipt+Job. Parsing повторяется worker вне locks.

Dedicated METRICS_IMPORT type reuse DurableJob lease/retry/fencing; generic ingest/AI/export
workers его не claim. Parent-first Account→Workspace→Brand→sorted Post→import→Job locks,
REQUIRES_NEW/READ_COMMITTED. Bounded worker1/instance, scan10 candidates, max3attempts,
lease60s, deadline300s; no provider side effects, pure parsing safe to repeat. Transient DB
failure→FAILED bounded2..10s backoff; malformed/conflict/revoked/archive→DEAD safe code.
Expired lease reclaimed, late worker fenced independently of frontend. Observations+result
links+receipt completion+Job SUCCEEDED commit together. Crash before commit rolls back all;
crash after commit re-delivery sees terminal Job. Error after claim leaves lease for recovery.
No public admin/debug endpoint. `cadence.metrics-import.worker-enabled` explicit defaultfalse;
если worker выключен, новые imports остаются QUEUED/PENDING до activation/deadline.

Retained quotas perBrand:1000 imports (до128MiB raw),100000 normalized revisions,100 perkey.
Quotas сериализованы parent mutex и повторены PostgreSQL. Dedup не расходует новую observation,
но новый receipt расходует retained import quota. Delete/automatic TTL не утверждены:
история retained, archive блокирует доступ/новую публикацию, quota не освобождается молча.
Новая PostgreSQL V52 не изменяет checksum прежних migrations, triggers/FKs/unique/checks и
runtime column grants защищают immutable provenance и coherent publication.

## Alternatives / threat model

Синхронный manual и отдельный async CSV уменьшают latency маленького запроса, но создают два
publication/recovery пути. Выбран один durable path для обоих: небольшая queue latency в обмен
на единые atomicity/idempotency/fencing. Новый storage/parser dependency не нужны при128KiB.
Непосредственная mutation legacy PostMetric нарушила бы source/time/unknown semantics и
mapping stable Post; выбран additive schema с сохранением legacy history.

Защищаем private business metrics/audit и tenant isolation. Caller — authenticated OWNER,
которого backend всё равно перепроверяет по БД. Trust boundaries: HTTP/raw parser/DB/worker.
Атакующий может подменить Post/tenant/actor, replay/correction, вызвать memory/CPU exhaustion,
вставить formula/URL, обойти API SQL записью или дождаться revocation/lost lease. Scoped
resource lookup+actual membership, strict bounded DTO/parser, append-only domain/SQL,
least privilege grants и final publication fences дают defense in depth. SQL parameterized;
parser не обращается к сети/shell. Raw payload/exception/SQL/secrets не логируются. Existing
Bearer-only security/CORS/CSRF contract не меняется. До production нужны rate/load/retention
операционные решения, отдельно от hard caps этого workflow.

## Remaining actual #99 scope

Task19 реализует human ingestion, не live platform synchronization. Actual #99 также требует
AnalyticsSyncService/SyncAnalyticsUseCase и recoverable partial provider failures. Их live
account authorization, provider cursor/rate/retry/retention и mapping qualification ещё нет.
Exact future port должен принимать captured tenant/Brand/qualified connection/platform,
bounded UTC interval/cursor и возвращать bounded immutable raw bytes/checksum, validated
external-post→ContentPost mappings, provider source timestamps, next cursor и per-record
SUCCESS/RETRYABLE/PERMANENT/UNKNOWN outcome. Cursor advances only atomically with persisted
receipts/outcomes; retry dedups natural provider observation identity; missing != zero.
Human origin нельзя повысить до PROVIDER. Этот design boundary не заявляет delivered live port
или provider adapter; #99 остаётся OPEN. Task20 calculations отдельно, здесь не реализуются.
