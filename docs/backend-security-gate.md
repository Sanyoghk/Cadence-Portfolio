# Backend28 — security, audit и observability contract

Дата: 2026-10-19. Только provider-independent backend09–22. Frontend25–27, live adapters,
whole28 и production deployment не покрываются этим gate. Результаты — в [отчёте](prompt28-backend-report.md).

## HTTP admission и correlation

`ApiRequestBoundary` выполняется перед Spring Security: максимум16 concurrent requests
(config1–32), общий process cap6000 admissions/minute, отказ503/Retry-After:1 до JWT/DB.
Только точный GET `/api/v1/health` обходит admission: это pure liveness без database/provider I/O.
Счётчики используют monotonic time; смена clock/timezone не обнуляет window.
`ApiRateLimitFilter` после authentication и body/upload admission применяет account UUID:
1200 read и120 write/minute по умолчанию; window общая для sessions/tenants/routes этого account.
Повторная отправка, новый token или Idempotency-Key не обходят HTTP rate; durable replay contract
по-прежнему действует после admission. HTTP429 `request_rate_exceeded`, Retry-After:60.

Map максимум2048 accounts (config1–10000), вытесняются только expired windows; при заполнении
новые accounts получают429, existing windows не сбрасываются. Это технические demo caps,
не provider tariff и не согласованный коммерческий usage plan. При нескольких JVM лимит
применяется к каждой отдельно; database budgets/reservations/quotas остаются общими. Перед
public multi-instance hosting нужен квалифицированный edge/distributed admission и load test.

`X-Request-ID` всегда новый UUID сервера; одноимённый client header игнорируется. UUID входит
в MDC — контекст текущего log event — и удаляется/restored в finally. CORS exposes request ID
и Retry-After только для ранее approved routes/origins. Cookie authentication не появляется;
Bearer/API и отсутствие ambient cookie authority по ADR0040 сохраняют основание CSRF policy.

Ответы имеют no-store, nosniff, DENY, no-referrer и default CSP без исполняемого content.
Специальный HTML report сохраняет свой ограниченный CSP. HSTS задаёт Spring для HTTPS;
localhost HTTP не доказывает TLS/reverse-proxy qualification. Request logs содержат только
server request UUID и HTTP status: никаких URLs/query/body/headers/tokens/subjects/exception text.
Не более100 rejection records/process/minute; aggregate counters учитывают все отказы.
Production Hibernate SQL/bind/row-diagnostic logging отключено: PostgreSQL error detail может
содержать user row. Safe application error code и correlation остаются для диагностики.
`WorkerFailureDiagnostics` добавляет bounded categories для metric import/CSV/report catch
branches: operation UUID/attempt/kind/category без raw message/cause/stack. Это diagnostic
event, не committed outcome и не обещание безопасного retry; authoritative Job/reservation
state по-прежнему читается из БД.

Normalized metrics batch получает один `current_timestamp` через repository внутри publication
transaction. Это authoritative ingestion time DB, одинаковый для атомарного batch; original
reported/source timestamps и raw checksum не меняются. Независимые fresh lease/deadline/access
checks до и после publication сохраняются, SQL expiry guards используют текущий wall time.
Timestamp не продлевает lease и не даёт позднему worker право publication.
Result и Job для metrics/CSV/report используют один completion timestamp внутри atomic
publication. Отдельный fresh lease/deadline check после SQL write сохраняется: общий timestamp
не заменяет fencing. PostgreSQL tests принудительно дают следующий clock sample на1µs раньше
или переводят его к deadline после write; проверяются coherent success либо полный rollback.

## Container findings и проверенная применимость

Все локальные serving images получают fresh complete scan: application OS/JRE и JAR,
renderer, PostgreSQL, ClamAV, gateway и existing MinIO fixture. OS updates не меняют engine/API
major versions. PostgreSQL использует Alpine native su-exec с прежним UID/GID/groups/exec
contract. MinIO/mc сохраняют pinned upstream source; Go1.27.2 и committed module graphs/sums
исправляют known dependency findings, build readonly, self-update выключен.

HIGH/CRITICAL и unreviewed UNKNOWN блокируют gate. Единственный специальный applicability
check касается [GO-2026-5932](https://vuln.go.dev/ID/GO-2026-5932.json): advisory запрещает семь
OpenPGP package paths внутри x/crypto, а module-level binary scan сообщает весь module.
Build сохраняет compiler dependency inventory рядом с бинарниками; `verify_security_reports.py
minio` читает его bounded network-none/read-only/cap-drop образом из точного immutable
Metadata.ImageID. Отсутствие affected packages даёт NOT_APPLICABLE, raw UNKNOWN остаётся в
report. Missing/mismatched/malformed proof, affected package, другой advisory/module/binary или
HIGH/CRITICAL не обходятся. Это проверка отсутствующего кода, не разрешение уязвимого runtime.
Source/Go compiler/image trust подтверждается pins/checksums/build provenance; Docker-daemon
administrator остаётся отдельной trust boundary. Новый storage/AI provider не выбирается.

Renderer/application OS имеют retained vendor-unfixed MEDIUM/LOW: их counts, exact images и
limitations указаны в отчёте. Archived MinIO остаётся local/integration fixture; production S3,
IAM/TLS/retention/license/maintained supplier требуют отдельной qualification.

## Liveness, readiness и metrics

- GET `/api/v1/health`: legacy response200, без infrastructure I/O.
- GET `/api/v1/health/ready`:200 `{"status":"READY"}` при доступной обязательной database;
  bounded connection admission, SELECT1/query timeout1s. Failure503 — safe Problem Details.
- Readiness не вызывает AI/social/renderer и не утверждает готовность optional feature.
  Их activation/startup guards, current file scan/storage checks и provider qualification
  независимы. PostgreSQL — обязательная infrastructure для retained demo; H2 — development.
- Другие health paths/methods, actuator/env/metrics и audit API не открываются.
- При явном `spring.jmx.enabled=true`: process-local JMX `com.cadence:type=ApiRequestMetrics`, fixed counters2xx/3xx/4xx/5xx,
  rate/capacity rejections. Existing `com.cadence:type=AiGenerationMetrics` сохраняет enum-keyed
  attempt outcomes и poll failures; logs связывают Job/operation/attempt без prompt/response.
  Нет labels по account, source, URL или request ID. Remote unauthenticated JMX не включён.

## Append-only audit

V56 добавляет `security_audit_events` и фиксированные database triggers: Identity/Strategy/Post
approval; scheduling/cancellation; CSV/report capture/publication; evidence admission/withdrawal;
membership grant/revoke и account role/active change. Publishing в social platforms не существует.
Audit успешного command записывается в той же transaction; rollback/deferred constraint failure
отменяет event. HTTP denials отражаются отдельно в security logs/counters и не выдаются за
committed business actions. Download/read не создают запись с выдуманным mutation outcome.

Поля: server timestamp/transaction ID, action, resource kind/UUID, workspace UUID, domain actor
UUID при наличии исходного receipt, database principal и result COMMITTED. Не сохраняются
prompts, reviewed text, CSV/HTML, email, JWT, private keys или payload dump.
Permission changes вне human business API атрибутируются реальному database principal,
`actor_origin=DATABASE_PRINCIPAL`, actor_id=NULL; target user не выдаётся за администратора.
Привязка к human operator должна быть в approved credential/access management процессе hosting.

Trigger SECURITY DEFINER имеет fixed pg_catalog search_path, identifiers только из trusted
trigger schema через `%I`, query values — параметры. Runtime не владеет schema/functions,
не имеет прямого INSERT/SELECT/UPDATE/DELETE/TRUNCATE/sequence/execute доступа к audit.
Immutable trigger дополнительно запрещает UPDATE/DELETE/TRUNCATE владельцу таблицы; superuser
вне trust boundary может отключить DB guards — отдельный IAM/backup gate, не обещание tamper-proof.

Автоматического удаления/придуманного retention TTL нет. Sequence NO CYCLE/MAXVALUE1000000
задаёт fail-closed demo storage bound: при исчерпании audited mutations откатываются, история
не перезаписывается. Sequence gaps после rollback нормальны; alert заранее по last_value.
Для расширения/архивации нужны отдельная reviewed migration и retention decision, не runtime reset.
V1–V55 не изменяются, прошлые события не реконструируются. Local H2 create-drop не выполняет
PostgreSQL audit triggers и не является qualification retained audit demo.

## Threat/architecture review

| Boundary / attack | Protection / independent verification |
| --- | --- |
| JWT/roles/bootstrap; stolen/foreign IDs | signature/issuer/audience/exp/azp, persisted account + current membership; OWNER-only use cases/scoped queries; JWT/security/PostgreSQL revocation tests |
| Tenant cache/resource confusion | Backend не полагается на frontend cache/scope; current authorization;23–24 session/late response checks historical,25–27/cross-stack повторить отдельно |
| Approval/export/history rewrite | expectedVersion, immutable approved revisions, exact captured links/checksums, parent-first locks, PostgreSQL/deferred triggers/runtime grants; approval/export race suites |
| Worker duplicate/stale publication | persisted source capture, external I/O outside locks, lease/attempt fencing, fresh access/deadline/source checks, atomic Job/result publication; real child-JVM crash/restart suites |
| Budget/retry abuse | account/tenant/operation/digest replay binding; durable reservation, uncertain outcome retains spend; multiple-worker/crash/overflow tests; HTTP caps дополнительны |
| SSRF/rebinding | public-IP validation/pinned socket, redirect revalidation, proxies disabled, exact TLS endpoint/SNI/verification, byte/time bounds; URL transport negative suites |
| Upload/CSV/renderer injection | generated private keys, quarantine/scan/retained bytes rechecks, no arbitrary fetch at review, pinned sandbox, bounded bytes/rows/fields/UTF8, spreadsheet formula neutralization; storage/CSV/Linux/renderer suites |
| XSS/CSRF/CORS | escaped creator HTML report, dedicated DTO/Problem Details, safe headers; Bearer-only auth and exact origins, no credentials wildcard; report/CORS/negative tests |
| Audit forgery/rollback/lost attribution | same-transaction fixed DB trigger, no runtime write/read API, honest DB principal for operational changes; PostgreSQL migration/runtime/rollback/sequence-cap tests |
| Log injection/leak/cardinality/DoS | server UUID only, no raw diagnostics, bounded logs/counters/map/semaphore/windows; hostile headers/body + concurrency/rate tests |
| Supply chain / privileged CI | pinned versions/digests/actions, read-only token, no privileged PR trigger; fresh inventory/scans and explicit report completeness; branch enforcement separately unavailable |

References for review: [OWASP Top10](https://owasp.org/projects/top-ten),
[REST assessment](https://cheatsheetseries.owasp.org/cheatsheets/REST_Assessment_Cheat_Sheet.html),
[Semgrep metrics](https://github.com/semgrep/semgrep/blob/develop/metrics.md).
