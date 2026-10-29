# Approved Instagram/TikTok source adapters

`POST /api/v1/brands/{brandId}/evidence/social` принимает только provider enum:

```json
{"provider":"INSTAGRAM"}
```

Второе допустимое значение — `TIKTOK`. Endpoint доступен OWNER своего workspace/brand.
Клиент не передаёт URL, token, account ID, API version, scopes или approval. Сервер выбирает
заранее provisioned credential binding конкретной brand/provider. Без binding/approval — safe
`503`, без HTTP request.

## Architecture и Runtime Flow

```text
Authenticated OWNER → controller ownership
  → RegisterSocialBrandEvidenceUseCase (external I/O вне DB transaction)
  → EvidenceProviderQuota (scoped Brand lookup → no-I/O approved-binding preflight)
  → provider → workspace → brand locks + committed attempt receipt (REQUIRES_NEW)
  → OfficialSocialEvidenceSource (approval/expiry/scopes + fixed official hosts)
  → bounded HTTPS profile + single page posts
  → allowlisted canonical approved-social-v1 JSON
  → publication transaction: TEXT registration + ingest Job + EvidenceSocialOrigin
  → 202 Accepted + durable Job Location
```

Источник читает небольшую страницу только одобренного аккаунта. Captions/profile text остаются
недоверенными данными: будущий analysis использует их как пользовательский контент, не команды,
системные инструкции или HTML. Источник не scraping и не publishing.

`EvidenceSocialOrigin` создаётся сервером после approved provider call. Пользователь может
прислать похожий JSON через обычный TEXT API, но у него не появится trusted origin. `format` и
`provider` внутри JSON **не доказывают происхождение**. Downstream обязан проверять origin и
checksum. Evidence, ingest Job и origin сохраняются в одном transaction; при ошибке provenance
insert они откатываются вместе, committed quota attempt сохраняется.

До global provider lock и записи attempt `ApprovedSocialEvidenceSource.requireApprovedBinding`
локально проверяет enabled binding, approval, required scopes, expiry и HTTP runtime policy.
Scoped Brand lookup выполняется раньше этой проверки: чужой Brand не раскрывает binding и
вообще не обращается к source port. Missing/expired/revoked binding не резервирует ни tenant,
ни shared provider quota и не выполняет HTTP. Поэтому OWNER без доступного approved account
не может исчерпать общий budget серии дешёвых `503`. `fetch` повторяет binding checks;
после успешного admission actual provider outage/429 или произошедшая revocation всё равно
расходует committed attempt. Во время binding preflight network/storage I/O запрещён contract.

V19 PostgreSQL trigger проверяет matching provider receipt, brand/workspace, TEXT type, checksum
и capture time. Origin запрещено изменять/удалять; content/brand/type/checksum evidence с origin
нельзя менять. Идентичный повтор использует существующий matching origin.

Canonical content содержит provider/account/version и нормализованные поля profile/posts,
но не время текущего HTTP retrieval:

```json
{"format":"approved-social-v1","provider":"INSTAGRAM","accountId":"17841400000000001","accountName":"creator","biography":"Ideas","apiVersion":"v25.0","posts":[{"id":"18000000000000001","publishedAt":"2026-09-28T10:00:00Z","text":"Example caption"}]}
```

`Snapshot.retrievedAt` передаёт capture time отдельно; первый successful publication сохраняет
его в immutable `EvidenceSocialOrigin.capturedAt`. Повторное чтение одинакового content позже
сохраняет тот же canonical JSON/checksum и возвращает существующие Evidence, Job и origin.
Время original capture и receipt не переписываются. Каждый approved повтор всё равно требует
нового provider quota admission, поскольку реальный HTTP call расходует budget; failed refresh
также сохраняет committed attempt, не изменяя первоначальные evidence/provenance.
Regression tests перемещают Clock для обоих providers и проверяют HTTP duplicate с разными
capture times, неизменный origin и сохранённую quota после failed duplicate refresh.

## Official API contracts

Instagram — **Instagram API with Facebook Login**, Professional account:

| Operation | Fixed host/path | Fields |
| --- | --- | --- |
| Profile GET | `https://graph.facebook.com/{reviewed-version}/{bound-ig-account}` | `id,username,biography` |
| One media GET | Account + `/media`, `limit=20` | `id,caption,timestamp` |

API version задаётся оператором явно: default latest version отсутствует. Supported version и
exact fields нужно подтвердить в Meta app при deployment. Нужны `instagram_basic` и
`pages_read_engagement` выбранного Facebook Login path. Scopes Instagram Login path не
взаимозаменяемы. Account должен быть Professional и связан с Page. Ограничения и Bearer auth
описаны в [official Meta Postman collection](https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api?entity=request-23987686-b9dac9f7-5415-4659-bcfd-795298a07e91).
Часть страниц developers.facebook.com требует sign-in; доступ в текущей среде не подтвердился.
Live API version validation не заявляется.

TikTok — Display API:

| Operation | Fixed host/path | Scope / fields |
| --- | --- | --- |
| GET profile | `https://open.tiktokapis.com/v2/user/info/` | `user.info.basic`; `open_id,display_name` |
| POST one video page | `https://open.tiktokapis.com/v2/video/list/` | `video.list`; `id,create_time,title,video_description`; body `{"max_count":20}` |

Primary sources: [Get User Info](https://developers.tiktok.com/docs/en/tiktok-api-v2-get-user-info),
[List Videos](https://developers.tiktok.com/docs/en/tiktok-api-v2-video-list),
[Video Object](https://developers.tiktok.com/docs/en/tiktok-api-v2-video-object).
Adapter не запрашивает profile/stats scopes, comments, private videos, followers или publish rights.

## Configuration и secret provisioning

```properties
cadence.evidence.social.enabled=false
cadence.evidence.social.deadline=10s
cadence.evidence.social.max-requests-per-hour=100
cadence.evidence.social.max-workspace-calls-per-hour=100
cadence.evidence.social.max-provider-calls-per-hour=50
cadence.evidence.social.ledger-retention-enabled=false
cadence.evidence.social.ledger-retention-interval=PT1H
```

Operator secrets/config задают `cadence.evidence.social.bindings[index].*`:

| Field | Требование |
| --- | --- |
| `brand-id` | Exact brand UUID; brand/provider binding уникален |
| `provider` | `INSTAGRAM` либо `TIKTOK` |
| `account-id` | Instagram numeric Professional ID; TikTok authorized application open_id |
| `token` | Только deployment secrets; ASCII allowlist, total length 10..4096 включая padding; никогда Git/client/URL/log |
| `api-version` | Instagram reviewed explicit version; TikTok всегда v2 |
| `expires-at` | Реальная OAuth expiration, больше now+30s при вызове |
| `approved` | true после OAuth consent, product review и ownership verification |
| `scopes` | Фактически выданные scopes, включая required минимальные |

Enabled config с пустым list/unapproved binding/malformed token/account/version/missing scope
отклоняется при startup. Credentials классы не имеют generated `toString`; error сообщения
фиксированы без secrets. Token не попадает в evidence/quotas. Оператор provision минимальные
scopes и управляет revoke/rotation/refresh через secrets system. `approved=true` самостоятельно
не выполняет OAuth consent/provider review.

Public OAuth callbacks, PKCE/state validation, self-service linking и automated refresh/revocation
не реализованы: live integrations #32/#33 остаются отдельными deployment/product задачами.
Этот MVP adapter использует уже авторизованные server-provisioned bindings.

При включении настоящего social adapter JVM запускается с фиксированной retry policy:

```text
java -Djdk.httpclient.disableRetryConnect=true -Djdk.httpclient.redirects.retrylimit=1 -Djdk.httpclient.enableAllMethodRetry=false -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,s3,clamd,renderer
```

Эти flags закреплены также в Docker application ENTRYPOINT и Surefire fork. По
[official JDK21 networking properties](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/module-summary.html)
HttpClient обычно может повторять connection/failure requests. Поэтому `SocialHttpRuntimePolicy`
проверяет actual JVM startup arguments вместе с effective properties при enabled startup и перед
network request. Late `System.setProperty` не заменяет startup flags: JDK может уже кешировать
значения. Missing/changed flags отклоняют enabled configuration/operation без HTTP I/O. Это
необходимо, чтобы один snapshot не обходил global raw-call budget через скрытые HTTP retries.

При enabled adapter diagnostic properties `jdk.httpclient.HttpClient.log`,
`jdk.internal.httpclient.debug` и `jdk.internal.httpclient.hpack.debug` должны быть
unset либо empty. Любое nonempty значение в startup arguments или effective properties
отклоняется до HTTP I/O. JDK diagnostic logging может выводить request headers и raw responses;
application-level redaction не защищает этот отдельный logger. В частности, официальный
[JDK21 Http1Request](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.net.http/share/classes/jdk/internal/net/http/Http1Request.java)
передаёт собранные headers в INFO logging. Поздняя очистка property не доказывает, что
ранее инициализированный logger выключен.

`jdk.internal.httpclient.disableHostnameVerification` допускается только unset или explicit
`false`. Пустое значение в JDK21 отключает hostname verification, поэтому guard проверяет
его независимо от наличия HTTPS в URI; это поведение подтверждено
[JDK21 Utils](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.net.http/share/classes/jdk/internal/net/http/common/Utils.java).
TLS certificate chain и соответствие hostname сохраняются; отключение проверки
отклоняет enabled startup и каждый сетевой вызов. Регрессионные tests проверяют late
property changes, unsafe startup flags и границу token 4096/4097 включая padding.

Domain enum `SocialEvidenceProvider` принадлежит domain layer. `EvidenceProviderCall` и
`EvidenceSocialOrigin` принимают domain values и не зависят от application port DTO. Adapter
возвращает typed `ApprovedSocialEvidenceSource.Snapshot`; application layer преобразует его
в immutable origin entity в общей publication transaction. Approved provenance подтверждает
источник данных; captions и biography остаются недоверенным пользовательским content.

## Security Review

**Threat:** IDOR/BOLA и чужие provider credentials.
**Protection:** controller ownership + service role/scope + active-brand quota preflight +
brand-bound token + profile account equality + publication ownership recheck + composite FK.
**Verification:** unauthenticated/EDITOR/foreign workspace/archived brand/wrong account tests.

**Threat:** SSRF/token leakage через redirects, pagination или media URLs.
**Protection:** fixed HTTPS hosts, no custom ports/userinfo, `Redirect.NEVER`, Bearer header,
single page. Returned `paging.next`, cursor и media URLs не скачиваются.
**Verification:** fixture next URL указывает на metadata endpoint; transport allowlist negatives.

**Threat:** oversized/malformed/slow responses и uncontrolled parallelism.
**Protection:** две операции одновременно на application instance; общий monotonic deadline `1s..30s`; connection timeout
3s; custom reactive subscriber ограничен 65,536 bytes и cancels oversized body. До 20 posts,
captions до 300 code points, title до 100, result до 20,000 characters.
**Verification:** bounded subscriber, malformed UTF-8, excessive posts, timeout, quota tests.
Strict JSON parser отклоняет duplicate keys, trailing JSON и nesting больше 32; ambiguous
provider identity не может быть переинтерпретирована разными parser layers. Cross-provider
concurrency test удерживает две операции и доказывает немедленное отклонение третьей.

**Threat:** abuse/cost retry amplification и replica quota race.
**Protection:** durable workspace/provider rolling-hour ledger under workspace lock; failures
consume quota; durable provider-wide rolling-hour cap under two fixed provider row locks;
per-process provider HTTP fixed-hour cap; no automatic retries. Snapshot требует максимум два
HTTP calls. Global default 50 admitted snapshots/provider/hour означает максимум 100 raw HTTP
calls через все application replicas/workspaces. `max-provider-calls-per-hour` имеет диапазон
`1..500`; `max-workspace-calls-per-hour` — `1..1000`. Locks всегда provider → workspace → brand,
в committed REQUIRES_NEW admission transaction; external I/O не удерживает database locks.
Admission явно требует READ_COMMITTED: после ожидания provider lock COUNT должен видеть
receipt предыдущего transaction. PostgreSQL race test использует datasource default
REPEATABLE_READ и доказывает method-level READ_COMMITTED override; stale snapshot не допускается.
V21 seeds exactly INSTAGRAM/TIKTOK lock identities, FK связывает receipts с provider identity,
UPDATE/DELETE identity запрещены trigger. H2 seeder активен исключительно `(local | test) & !prod`.
Missing identity fail closed. Global cap защищает общий budget, не гарантирует fairness между
tenants: operator должен выделить tenant limits и provider/app plan согласно коммерческой квоте.
**Verification:** PostgreSQL competing reservations двух brands одного workspace разрешают одну;
два разных workspace одновременно не oversubscribe global provider limit; 429 не retries;
failed calls не освобождают durable workspace/provider-wide budget.
50 rejected unapproved attempts сохраняют весь provider-wide budget для approved workspace;
HTTP negatives используют настоящий binding validator для missing/expired/revoked/scopes/disabled
cases и доказывают отсутствие ledger, origin, evidence и внешнего HTTP I/O.

**Threat:** reflected tokens/provider errors.
**Protection:** fixed exceptions без provider causes, actual-token check canonical selected fields,
allowlisted fields, raw errors/unknown fields/paging не сохраняются.
**Verification:** fake Bearer exception/token fixtures и API error redaction.
Actual token внутри ignored `paging.next` отбрасывается вместе с URL, без follow и публикации.
Это позволяет читать legitimate Graph API pages, которые включают credentials в pagination URL,
сохраняя запрет token reflection в сохраняемых captions/profile fields.

**Threat:** fake approved origin через обычный TEXT API.
**Protection:** отдельный immutable `EvidenceSocialOrigin`, receipt ownership и checksum
constraints, общая publication transaction. Content JSON не даёт privilege/trust.
**Verification:** provider-shaped manual TEXT не создаёт origin; SQL negatives проверяют origin
mutation/delete, wrong receipt/provider и изменение bytes после approved origin.

## Retention, tests и deployment boundary

Ledger не содержит token/body. PostgreSQL запрещает UPDATE и DELETE раньше 90 days.
Optional worker удаляет максимум 500 старых **unbound** attempts за запуск. Receipts с origin
сохраняются вместе с provenance; coordinated origin/evidence retention требует отдельной policy,
не cascade удаления доказательства. Hourly count использует indexed timestamps.

Fixtures `src/test/resources/social/` синтетические и следуют documented response shapes;
это не live responses. Security integration использует real service/database boundaries.
PostgreSQL tests проверяют race и DB constraints. Точные фактические suite результаты находятся
в итоговом issue report.

Deployment validation: actual consent/scopes/app review, account identity, Meta version fields,
TLS/egress policy, provider-wide quotas, token rotation и contractual retention permission.
Local fixture success не подтверждает production approval и не завершает public OAuth #32/#33.

## Social textual consumers05

[Typed social review API](social-evidence-admission-api.md) использует immutable social-text-v1
snapshot и explicit consent. Trusted origin запрещает generic TEXT fallback, включая legacy
и inherited Identity. Same-account current binding проверяется при review/generation/copy/approval.
Batch с social получает social-evidence-v3; прежние TEXT/PDF/URL receipts сохраняются.
[ADR0025](adr/0025-social-text-review-consumption.md), [qualification05](issue-121-social-consumption-slice.md).
Visual inputs остаются REQUIRES_PROVIDER, live OAuth/privacy не квалифицированы fixtures.
