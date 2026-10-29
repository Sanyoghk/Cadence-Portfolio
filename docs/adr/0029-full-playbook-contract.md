# ADR0029 — Полный versioned Strategy Playbook

Дата: 2026-10-15, задание09, baseline `f2f60c9`.
Статус: ACCEPTED technical representation утверждённых MVP fields для задания13.
Новые маркетинговые обещания, provider и pricing не выбираются. Runtime13 qualified locally,
actual gates: [report13](../prompt13-playbook-report.md).
Связи: [product scope](../product-scope.md), [ADR0026](0026-approved-identity-strategy-source.md),
[ADR0028](0028-durable-ai-workflow-contracts.md), [draft P](../ai-workflow-issue-drafts.md#draft-p).

## Current и proposed architecture

`StrategyContent` сейчас содержит pillars, postsPerWeek, timezone, bestTimes. Current bounds:
1–10 unique pillars, weights0–100/sum100; postsPerWeek1–21; valid IANA timezone;
1–7 distinct bestTimes. DRAFT PATCH, approval и approved-edit→new-DRAFT уже существуют.
Strategy generation привязана к latest APPROVED Identity; legacy signature больше не source.

Добавить versioned PlaybookContent v2 к тем же revisions/lifecycle, а не вторую независимую
Strategy entity. Dedicated DTO/domain validator/provider result schema; SQL guards immutable
approved content и collection ownership. Async Strategy operation использует новый result
contract, сохраняя original source/provenance. Нельзя silently расширить historic checksum v1.

## Representation и bounds v2

Ниже engineering bounds для bounded MVP payload, не product quotas и не AI truth claims.
Все strings strip, no control chars; lengths в Unicode code points; entire canonical UTF-8
content ≤65536 bytes. Existing v1 text length semantics сохраняются в старом contract.
Новые collection IDs локальны данной revision, не произвольные DB resource IDs.
В v2 pillars получают explicit local id для outline references; IDs1–64 ASCII chars
[A-Za-z0-9_-], unique within collection. Original v1 не получает fabricated IDs задним числом.

| Field | Semantics и validation |
| --- | --- |
| pillars/cadence | existing rules, weights sum100; postsPerWeek общий на все channels, не per-channel |
| audienceAssumptions | 1–5 entries: local id, segment≤200, needs≤1000, rationale≤1000; всегда assumptions, не verified demographics |
| channels | 1–2 unique INSTAGRAM/TIKTOK, sharePercent1–100/sum100; goals1–3/channel: objective≤500, metricHint≤100 optional, rationale≤1000 |
| toneSpectrum | 1–5 axes: local id, leftLabel/rightLabel≤100 and distinct, position integer0–100, rationale≤1000; не confidence/probability |
| competitiveWhitespace | status HYPOTHESES или INSUFFICIENT_EVIDENCE; hypotheses1–5 или0 соответственно; statement/rationale≤1000 |
| fourWeekOutline | ровно weeks1,2,3,4 unique, каждую неделю postsPerWeek slots; максимум84 slots |
| outline slot | local unique slotId, channel из channels, pillarId из pillars, audienceId из assumptions, topic≤500, intent≤500, dayOffset0–6 |

BestTimes/timezone — рекомендации. Week1 начинается с future Content planning startDate;
в Strategy нет executable schedule, UTC timestamps постов или publishing commands.
Multiple slots/day допустимы в existing total cap; DST и фактическое назначение времени —16.
Outline references должны разрешаться внутри exact revision, model не создаёт foreign IDs.
Channel mix — целевая доля за four-week horizon, а не отдельная weekly quota. Распределение
4*postsPerWeek slots рассчитывается largest remainder: floor(total*share/100), остатки по
убыванию fractional part, tie-break enum INSTAGRAM затем TIKTOK. Validator сверяет counts;
это исключает невозможное требование ровно50/50 при нечётном weekly count. Integer arithmetic.
Pillar weights остаются editorial emphasis; не требовать exact slot percentages при малом count.

Не добавлять числовые KPI targets, scraped competitors, causal/confidence scores или market
research как якобы утверждённый scope. goal metricHint — описание желаемой метрики, не гарантия.
INSUFFICIENT_EVIDENCE честно поддерживает пустое competitiveWhitespace без fabricated facts.
Если product потребует другие axes, KPI taxonomy или точный calendar semantics, это изменение
versioned contract; произвольные поля/неизвестные schema versions отклоняются.

## Identity, evidence и история

Identity задаёт brand voiceUp/voiceDown, palette/fonts и sampleCaption. Strategy toneSpectrum
описывает применение этого голоса к контенту, не заменяет Identity и не переписывает её.
Audience assumptions хранятся в Strategy; не придумывать отсутствующее поле audience в Identity.
Результат обязан ссылаться на captured approved Identity через local provenance, а не через
model-claimed resource ID. Backend не может математически доказать смысловое соответствие
tone бренду: human review обязателен, это явно остаётся ограничением, а не «validator verified».

Сохранить ADR0026: latest revision должна быть APPROVED; new DRAFT во время generation
отменяет publication. Для generation — pre-egress/completion current evidence/physical checks.
Existing Strategy edit/approval/history semantics сохраняются: generated source — historical
lineage, не вечное разрешение повторно посылать исходные evidence. Новый generation повторяет
проверки; новые Content consumers обязаны отдельно определить capture/currentness policy14–15.

Schema/contentFormatVersion хранится immutable на generation receipt. v1 history читается
как LEGACY_PARTIAL; отсутствующие new fields nullable/absent, не generated defaults.
Новый v2 generation требует complete content. Old v1 PATCH/approval API остаётся совместимым,
но v1 approved не становится автоматически eligible full Playbook source для new Content.
Для upgrade v1 →v2 нужен explicit full-content edit: DRAFT в месте по expectedVersion либо
new DRAFT branch для APPROVED/LOCKED. Original v1 receipt сохраняет checksum/schema.
V2 нельзя downgrade до v1. Старый PATCH без v2 fields не должен стирать новый content:
для v2 revision такой запрос409 с safe contract conflict; new API/OpenAPI описывает полный v2 DTO.

Human edits сохраняют original generation lineage и actual edit actor/time отдельно; не
приписывать новые поля модели. Source receipt и human approval immutable. Model output schema
валидация не подтверждает factual correctness или marketing effectiveness.

## Runtime flow и API

Async strategy-jobs → captured approved Identity → worker/validated Playbook v2 result
→ atomic Strategy DRAFT → GET complete DTO → PATCH expectedVersion → separate approval
→ immutable full Playbook revision для Content. Status/Job flow — ADR0028.
Существующие resource paths read/list/edit/approval сохраняются; response включает content
schema/completeness. Не возвращать JPA entity или raw provider JSON. Approval проверяет
committed full v2 fields целиком и current actor/parents, не подменяет content из request.

## Threat / Protection / Verification для13

| Threat | Protection | Required tests (выполнение: report13) |
| --- | --- | --- |
| Model forged refs / mass assignment | typed DTO, intra-revision IDs, server source/actor | foreign slot/pillar/audience, unknown fields/status/provider/approval |
| History overwrite / lost update | expectedVersion, new-DRAFT branching, SQL guards | both edit/approve orders, concurrent generation/version allocation |
| Injection / huge output | plain strings, Unicode/UTF-8 and count caps, no tools | HTML/command strings remain data, cap N/N+1, malformed JSON |
| Incoherent schedule / weights | finite channels, sum100, exact weeks/counts, IANA timezone | duplicates, missing week, mix rounding/ties, overflow, DST handled in16 |
| Fabricated legacy truth | explicit LEGACY_PARTIAL + separate schemas/provenance | populated v1 migration, old reads/approval, explicit upgrade, downgrade denied |
| Cross-tenant / stale source | existing OWNER/DB membership/current generation fences | foreign/latest DRAFT/withdrawal/expiry/access/archive races |

Application validators + FK/check/unique/deferred SQL content guards + restricted runtime role.
No new dependency required. Single v2 content aggregate keeps one coherent revision/approval;
separate tables per independent editable feature risk partial approval. Concrete normalized SQL
layout определяется13 по query/runtime constraints, без произвольного unvalidated JSON blob.
Alternative free-form prose проще, но не даёт безопасных references/Content planning/consistent UI.

## Acceptance и limits of design

Доказательство13: domain/result validation, HTTP/direct-bean negatives, PostgreSQL migrations
на populated v1/v2 data и runtime bypass tests, inheritance/edit/approval/generation races,
full gates/startup; TEXT/PDF/URL/social and Strategy regressions preserved.
Schema contract здесь reviewed; execution/OpenAPI delivered13, qualification в report13.
Нет live AI/market effectiveness qualification; product pricing/retention pending ADR0028.

## Implementation13 — 2026-10-16 (QUALIFIED LOCAL)

Реализация использует семь normalized owned collections: pillar IDs, audience, channels,
goals, tones, hypotheses, slots. Все принадлежат одной Strategy; FK для slot references
и deferred content checks видят согласованный итог transaction. Goal order и value order
сохраняются явно. Новая migration V46 не переписывает V1–45.

Response: contentFormatVersion1/2, completeness LEGACY_PARTIAL/COMPLETE, nullable playbook,
editedBy/editedAt, readyContentSource. Последний флаг означает только complete APPROVED
revision с реальным approval audit; это не permission на будущий AI egress. Consumer14–15
обязан проверить actual actor/tenant/current source и определить capture policy.

PATCH продолжает принимать cadence/contentPillars; optional playbook содержит schemaVersion2
и полный aggregate. Для существующего v2 playbook обязателен. Unknown fields теперь отвергаются,
а не игнорируются. Human edit обновляет editedBy/editedAt; original receipt не изменяется,
approved branch сохраняет revisedFromId и не присваивает human content provider attribution.

Generation receipt имеет nullable generation_content_format/output_checksum: новые v2 получают
format2 и SHA256 canonical JSON исходного content (pillars/cadence + playbook).
Provider/model/prompt/warnings остаются отдельными immutable receipt fields и не входят в content digest. Последующий human edit не переписывает
этот checksum. Legacy provenance остаётся NULL без fabricated checksum. JSON canonical encoding
в PlaybookContent явно отделён от исторических v1 digest formats.

Async Strategy snapshot получает storage schemaVersion2; analysis snapshot остаётся schema1.
Старые terminal Strategy operations читаются как прежде. Pending schema1 Strategy capture
безопасно отвергается перед egress, не интерпретируется как запрос нового формата; operator
должен дождаться termination и создать новый request/key. In-place snapshot migration запрещена.

No new dependencies, provider activation, executable calendar or live marketing claims.
[Текущий отчёт13](../prompt13-playbook-report.md) фиксирует actual verification.

Совместимость shared base fields: существующие pillar name/description сохраняют прежние
UTF-16 length bounds и в shared DTO; Unicode code-point bounds применены к новым v2 fields.
Это техническое уточнение representation, не новые product fields. Canonical full output
ограничен64 KiB в domain. SQL дополнительно ограничивает normalized value footprint128 KiB:
этот размер включает повторяющиеся служебные keys/columns и не является transport budget.
Count/field/FK/state bounds независимо enforce на PostgreSQL.
