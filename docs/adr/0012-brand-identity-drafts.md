# ADR-0012: Versioned Brand Identity и граница ручного DRAFT

Дата: 2026-10-05. Статус: принято для manual drafts и lifecycle slices #121.
Реализованы manual create/read/list/update, отдельный approval и approved edit→новый DRAFT.
Citations и новый Strategy source описаны как следующие этапы, ещё не доступны.

2026-10-11: отдельный TEXT evidence admission реализован в ADR0013/V31. Он создаёт reviewed
input snapshots, не дописывает citations к прежним declared-only analysis/Identity receipts.

## V30 — draft из проверенного analysis receipt

Добавлен отдельный POST `/api/v1/brands/{brandId}/identities/from-analysis`. Это копирование
существующего успешного результата, не новый AI call. `expectedVersion` — текущий Brand
lockVersion. Без analysisId сервер выбирает bounded текущий SUCCEEDED receipt; явный ID
проверяется через Brand scope. Требуются Brand version=receipt.brandVersion+1, совпадение
Workspace version и checksum canonical Identity content с receipt.outputChecksum.

Workspace→Brand locks сериализуют source capture, analysis publication и archive. Полный
snapshot DTO создаётся в fresh transaction. Created DRAFT не approved; отдельный human review
сохраняется. External I/O здесь нет. Invalid/stale/missing current source не fallback в fake.

Nullable INSERT-only analysis_attempt_id связывает root с immutable generation receipt.
Manual drafts и branches approved revisions получают NULL; branch lineage указывает прежний
root. Ручной edit generated DRAFT сохраняет ORIGINAL receipt: initialOutputChecksum описывает
первоначальный output, а не утверждает, что новые manual edits были сделаны моделью.
Response analysisProvenance содержит только bounded provider/model/prompt/warnings/UUID/time/checksum,
без raw prompts, source text, credentials или private storage references.

V30 проверяет same-Brand successful/current receipt и запрещает смену origin. Deferred INSERT
constraint сверяет первоначальный content после Hibernate collection flush. PostgreSQL core
sha256(bytea) и Java SHA-256 используют одинаковые UTF-8 length-prefixed fields/list counts;
новая dependency/extension не нужна. Контракт legacy BrandAnalysisResult не переписан: если
normalization меняет checksum (например, lowercase palette), copy безопасно отклоняется вместо
приписывания преобразованного результата прежнему receipt.

Alternative — выдумать citations из уже fetched/CLEAN evidence — отвергнута: нынешний analysis
использовал только declared brandName/industry. Evidence admission/review, content snapshots,
validated citation output и approved Identity→Strategy остаются следующих scopes #121.
Alternative — автоматически создавать Identity при каждом legacy analyze — нарушила бы отдельный
API contract и смешала analysis с creator review workflow. Explicit copy сохраняет совместимость,
но требует дополнительной команды; повторная copy не idempotent, lifetime cap1000 сохраняется.
V1–V29 не изменены, legacy roots не получают искусственный provenance.

## Контекст и решение

Brand.signature — embedded mutable BrandSignature с тремя коллекциями. AnalyzeBrandUseCase
публикует текущую signature и отдельный analysis receipt. Это не immutable approval history.
Strategy generation пока использует существующий signature capture/fencing из ADR-0010.

Добавляем отдельный aggregate BrandIdentity: Brand FK, server version, DRAFT status,
JPA lockVersion и bounded content (palette из пяти RGB colors, 1–5 fonts, 1–10 voiceUp,
1–10 voiceDown, caption до 1000 characters). Списки сохраняются отдельно по identity_id
и item_order. Immutable BrandIdentityContent валидирует и копирует значения; DTO не
возвращает Entity. Legacy signature не переносится: такой backfill выдумал бы provenance.

POST создаёт независимый manual DRAFT, PATCH полностью заменяет его content при совпадении
expectedVersion с lockVersion. version определяет номер Identity revision в Brand;
lockVersion определяет конкурентную редакцию конкретного draft. Это разные contracts.
Root metadata неизменяема, DELETE отсутствует. V28 технически допускает только DRAFT.

Use-case получает tenant scope из authenticated principal и проверяет его через method
security. Scoped projection исключает чужой ID до locks. Writes получают locks в порядке
Workspace→Brand→Identity, проверяют active parents; version=max+1 под Brand lock. Fresh
REQUIRES_NEW/READ_COMMITTED transaction исключает reuse старого managed snapshot.
DTO content строится внутри transaction, OSIV=false. External I/O здесь отсутствует.

V28 защищает metadata, allocation, parent lifecycle, RGB/text bounds, collection ownership,
case-insensitive uniqueness и непрерывный порядок. Deferred constraints проверяют окончательные
коллекции при commit: промежуточный delete/insert Hibernate не считается пустым content.
Runtime role имеет UPDATE только editable columns/timestamps/lockVersion и DELETE только owned
collections; root DELETE и status/version UPDATE не разрешены. Ни Entity, ни grants до V29 не позволяли approval; V29 добавляет explicit lifecycle protections.

## Lifecycle V29 — реализован второй slice

- Отдельный approval command читает committed draft, проверяет actual active OWNER/membership,
  принимает expectedVersion, записывает server approvedBy/approvedAt и сохраняет immutable content.
  Lock order Account→Workspace→Brand→Identity. Не объединять edit и approval.
- PATCH APPROVED/LOCKED должен создавать независимый DRAFT с revisedFromId и новым version;
  approval audit не копируется. Generated provenance и revised lineage различаются.

V29 расширяет status и добавляет nullable revisedFromId/approvedBy/approvedAt. Existing
V28 drafts сохраняют NULL audit/lineage. CHECK требует audit для APPROVED/LOCKED и запрещает
его для DRAFT. Trigger проверяет actual active OWNER/membership, valid approval time,
immutable approved root content/audit, lineage same-Brand approved source и revision allocation.
V28 collection guards/deferred validators продолжают действовать. Approval и content replacement
в одном root SQL UPDATE запрещены. Runtime получает только additional status/audit UPDATE;
lineage остаётся INSERT-only, root DELETE запрещён. Public lock endpoint не добавлен:
LOCKED — поддерживаемый защищённый state для последующих workflows.

PATCH DRAFT200, APPROVED/LOCKED201+Location. Approved root lockVersion сохраняется при
создании branch; повторные approved edits могут создать разные independent DRAFTs. Это не
idempotent create contract. Replay approval409. Account revocation ждёт/сериализуется с approval;
после получения locks проверяются actual DB role/activity/membership, не stale claims.
Все mutations fresh REQUIRES_NEW/READ_COMMITTED; separate approval не копирует content/audit
из внешнего managed Entity. Actor берётся из principal, время из Clock.

DB actor/time не доказывают просмотр UI и не являются криптографическим подтверждением JWT
при компрометации runtime credentials. Approved history сохраняет actor FK даже после отзыва
membership: прошлое подтверждение не должно исчезнуть. Staging clock synchronization необходима.

## Следующие contracts #121 — пока не реализованы

- Evidence-backed Identity должна иметь immutable evidence source snapshots; V30 уже сохраняет
  receipt для declared-only analysis. Citations должны быть bounded
  server-selected references на evidence snapshots того же Workspace/Brand. Client/model ID
  не является авторизацией. Сверять IDs с переданным allowlist, current evidence safety/lifecycle,
  source version/checksum и generation deadline. Не хранить arbitrary URLs/private storage keys
  в публичных citation DTO. Rejected/archived source нельзя использовать в новой generation;
  существующую history сохранять с явным состоянием источника.
- Strategy source должен ссылаться на конкретную APPROVED Identity revision и content snapshot;
  повторная source/lifecycle проверка перед publication предотвращает stale completion. Пока
  этот переход не реализован, существующая Strategy generation продолжает current signature flow.
- Эти этапы требуют последующих migrations и расширения runtime grants, negative/concurrency
  tests, reviewed DTO/OpenAPI. V1–V29 после публикации остаются неизменными.

## Alternatives / trade-offs

Mutable BrandSignature не хранит независимые revisions. Автоматический legacy approval нарушил
бы смысл human review. Новая таблица сохраняет старый API, ценой временного сосуществования
двух моделей. Append-only каждого draft edit создаёт ненужную историю до approval; draft edits
сейчас сохраняются на месте. Полный replacement проще атомарно валидировать, чем частичные
операции со списками; PATCH не является JSON Merge Patch.

Lifetime cap 1000 roots/Brand ограничивает стоимость history; это явный MVP product limit,
не замена per-user rate limits/tenant quotas (#87). Смена лимита требует согласованной migration.
Все fonts — строки-названия: приложение не скачивает fonts/URLs и не исполняет caption.
Frontend обязан выводить текст безопасно, без raw HTML. Caption — одна строка без ISO controls.

## Threat model и пределы

Защищаем tenant content/history. Trust boundary: HTTP→DTO→domain и application→database.
Caller только OWNER с workspace membership. Foreign/missing resources дают одинаковый404;
wrong roles403, anonymous401. Dedicated DTO исключают mass assignment; client status/version
поля игнорируются согласно текущему Jackson contract, но не меняют серверные metadata.
Stale edits409, invalid content400, archived writes409. Database constraints/column privileges
защищают от обхода application validation; DB role не заменяет JWT authorization.

Compromised runtime credential всё ещё может писать разрешённый draft content. Shared DB role
не обеспечивает независимый tenant RLS; runtime identity защищается отдельными operational
controls. Raw SQL parent-state guards не заменяют порядок locks всех application writers.
Production rate limiting, retention, external audit sink, actual Clerk/deployment/AI qualification
остаются отдельными gates. Новых dependencies/security-chain изменений нет.
