# ADR0032 — Content generation / regeneration Jobs

Status: ACCEPTED technical contract внутри утверждённого scope15; qualification completed locally — [report15](../prompt15-content-generation-report.md).
Baseline clean2c8a2b6; #98/#115/#107 прочитаны. ADR0028/29/30/31 сохраняются.

## Scope и lifetime

CONTENT — новый kind существующих AI operation/attempt/Job/accounting механизмов, storage schema3.
Old analysis1/Strategy2 snapshots и receipts не переписываются. Production provider disabled;
local/test fake явно DEMONSTRATION_ONLY + HUMAN_REVIEW_REQUIRED. Нет real assets, moderation verdict,
measured usage/cost или live privacy решения. Общие deadlines60s/lease≤20s, retries и held-unknown spend
сохраняются; budget одна reservation/operation, replay не резервирует снова.

Creator явно выбирает full APPROVED Strategy ID+lockVersion и1–84 unique outline slot IDs из её четырёх
недель. Slot IDs нормализуются sorted для request digest; output обязан содержать ровно выбранный set.
Complete batch публикуется атомарно. Это requested editorial slots, не executable schedule16.
Новая approval/edit Strategy не подставляет другой source. Exact captured Strategy content/approval
должны сохраниться. Также нужна доказуемая approved Identity lineage Strategy (direct generation либо
revisedFrom chain≤1000 hops с убывающим version и тем же Brand). Manual/legacy Strategy без такой
lineage подходит для manual14, но AI15 отказывает до explicit provenance upgrade через новый Strategy
workflow; missing lineage не backfill текущей Identity и не считается новой human approval.

Current latest Identity должна быть тем же exact APPROVED source, с теми же version/checksum/approval.
Existing EvidenceIdentitySourcePolicy и physical verification проверяются на capture, pre-egress и
completion; withdrawal/expiry/scan/context/access/Identity edit отменяют публикацию. История старых
creative results не удаляется. Human edit/approval creative draft сохраняет manual contract ADR0031:
не запускает egress и не выдаёт perpetually-valid permission для будущих AI consumers. Scheduling/export
потребуют собственную eligibility policy16/18;15 не превращает contentApproved в delivery permission. Underlying evidence text/bytes не передаются Content model повторно:
payload содержит bounded approved Identity signature + full approved Playbook + выбранные slots и
baseline content для regeneration. Историческая lineage сама по себе не egress permission.

## Provider и input/output

Port immutable ContentGenerationCommand/Result. Server IDs/scope/receipts/credentials/private refs
не model inputs; только server-issued correlation UUID и intra-Strategy local slot IDs. Нет tools,
fetch URLs или backend commands. Actual I/O вне transactions. Canonical UTF-8 input/snapshot/output
каждый≤64KiB; результат каждый раз проверяется, включая fake/mocked implementation.

Each entry: exact slotId/channel и validated PostContent по ADR0031 (title, caption, hashtags, format,
mediaPrompt, optional videoScript). Extra/missing/duplicate/foreign slots и format/channel mismatch
invalid. Provider/model/prompt — metadata trusted adapter configuration, не поля, выбираемые моделью.
Fake stamp фиксирован сервером; real adapter обязан отдельно квалифицировать strict JSON decoding
и stamping versions. Receipt хранит исходный validated structured result, не raw response/tokens.
Full output записывается immutable с provider/model/prompt/warnings, operation/attempt/worker/
dispatch witnesses и checksum. Неизвестные fields/schema отклоняются; schema validation не подтверждает
marketing quality. Каждый опубликованный Post — DRAFT, approval никогда не автоматическая.

## Regeneration и API

POST `/api/v1/brands/{brandId}/content-jobs`: expectedBrandVersion, strategyId,
expectedStrategyVersion, slotIds. POST `/api/v1/posts/{postId}/regeneration-jobs`: revisionId,
expectedVersion, slotId, field ALL/TITLE/CAPTION/HASHTAGS/FORMAT/MEDIA_PROMPT/VIDEO_SCRIPT.
Idempotency-Key обязателен. Scope actor/Workspace/kind + complete normalized request digest, включая
target revision/version/field; foreign source404, stale/conflicting replay409. Replay returns existing
operation даже после success без нового provider call. GET existing Job + `/content-results/{id}`
scoped, no-store; result содержит bounded Post/revision links и original provider provenance.

Regeneration сохраняет Post identity и exact Strategy source. После completion recheck latest revision/
version и baseline content; concurrent edit/approve/regeneration отменяет stale publication. Field mode
берёт только выбранное поле output и merge с captured baseline, затем снова validates whole content;
остальные поля не приписываются новой модели. Format-only может отказать при конфликте с сохранённым
script; ALL нужен для согласованного изменения нескольких полей. Existing model output receipt не
переписывается human edits или последующей regeneration.

Всегда новая DRAFT revision с revisedFromId: прежний DRAFT становится SUPERSEDED (content/audit не
меняются), APPROVED остаётся immutable. Generated revision хранит requester/time и receipt, но
editedBy/At до human edit NULL. Human edit audit отдельно; approval валидирует committed content.
SUPERSEDED read-only, не eligible для scheduling/export. At most one active DRAFT. Manual Post без
slot binding может явно выбрать compatible source slot; generated identity bound к исходному slot.

## Persistence / security / tests

Новая V48, old Flyway checksums immutable. Content batch receipt и generation links в Post revisions имеют operation/attempt/slot/
Post revision FK/uniques и immutable guards; only atomic worker publication с actual access/source/
deadline/lease/budget witness. Сначала обязательны Account→Workspace→Brand locks; source capture
далее использует Identity/evidence→Strategy→Post. Existing coordinator current/publish берёт
operation/job после source capture; claim/recovery берёт operation/job перед повторным capture
под уже удерживаемым parent mutex. Все эти source/operation rows относятся к тому же locked Brand,
поэтому interleaving двух противоположных последовательностей на одном graph исключён parent lock.
Supersession actor определяется только через batch, вставленный текущей transaction (xmin),
не через произвольный другой RUNNING Job для той же revision: это исключает захват чужого
Account после Workspace/Brand. Не переносить operation locking до parents и не добавлять
cross-Brand source links. Accounting mutex
порядок сохраняется по ADR0030. Receipt/history не removable.

Threats: IDOR, forged actor/fields/provider, output/source substitution, prompt injection, replay,
expired/lost lease, duplicate workers, stale revision, uncertain spend, payload/resource exhaustion.
Protections: strict DTO/ports, actual OWNER+DB scope, exact frozen capture, provider outside locks,
server-selected sources/slots, output+SQL checks, fencing/unique atomic publication, bounded executor,
existing reservations. Tests: positive mixed platforms/requested four weeks; field/all regeneration;
foreign/roles/stale/replay/malformed/unknown output; provider I/O without transaction; source/access/
withdrawal/expiry races, two workers/duplicate delivery/lost lease, SQL/runtime/migration/full/startup.

Actual qualification:148 suites/1554tests,0 failures/errors;3Windows skips covered Linux35/35.
Content PostgreSQL25/25, child-JVM recovery8/8, Python10/10,renderer29/29, reports/coverage/
packaged startup PASS; current SBOM194/OSV0 findings. Local commit only; no adapter activation.
