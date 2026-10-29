# Brand Identity API — drafts и lifecycle #121

Доступен OWNER с membership Workspace. Authentication: Clerk JWT/Spring Security.
Identity drafts независимы от legacy Brand.signature; creation не запускает AI и не меняет
Strategy source. TEXT evidence provenance/citations доступны отдельным reviewed workflow;
approved Identity→Strategy source ещё отсутствует. Все responses — immutable DTO.

| Operation | Success | Поведение |
|---|---|---|
| POST /api/v1/brands/{brandId}/identities | 201 + Location | Новый manual DRAFT, server version=max+1 |
| GET /api/v1/brands/{brandId}/identities | 200 | Page, defaults page=0,size=20,sort=version,desc |
| GET /api/v1/identities/{identityId} | 200 | Scoped content/history |
| PATCH /api/v1/identities/{identityId} | 200/201 + Location | DRAFT edit на месте; APPROVED/LOCKED → новый DRAFT с lineage |
| POST /api/v1/identities/{identityId}/approval | 200 | Подтверждение committed DRAFT с expectedVersion, server actor/time |
| POST /api/v1/brands/{brandId}/identities/from-evidence-analysis | 201 + Location + no-store | Explicit successful TEXT receipt copy с current actor/source checks |
| GET /api/v1/identities/{identityId}/evidence-provenance | 200 + no-store | Original citations через lineage, input checksum, copiedBy и withdrawal flags |

```json
{
  "content": {
    "palette": {"primary":"#112233","secondary":"#445566","accent":"#778899","neutral":"#AABBCC","background":"#FFFFFF"},
    "fonts": ["Inter"],
    "voiceUp": ["Clear"],
    "voiceDown": ["Spam"],
    "sampleCaption": "A clear creator caption"
  }
}
```

PATCH добавляет `expectedVersion` (неотрицательный long) к тому же body. Response содержит
id,brandId,workspaceId,version,lockVersion,status,content,revisedFromId,approvedBy,approvedAt,createdAt,updatedAt. Использовать
response.lockVersion следующим expectedVersion; response.version — номер revision.
При отсутствии фактических изменений Hibernate может оставить lockVersion прежним.

Content: RGB `#RRGGBB` (input case-insensitive, output uppercase), fonts1–5, voice lists1–10,
каждый item1–100, caption1–1000. Text strip, case-insensitive duplicates внутри каждого списка
и ISO control characters запрещены. Порядок списков сохраняется. Максимум1000 roots на Brand.
Unknown JSON fields игнорируются existing policy; status/version/ownership не writable.

Errors:401 anonymous,403 denied role/scope,404 foreign/missing resource,400 malformed UUID/body/
pagination,409 stale expectedVersion/archived parent/lifetime cap. Common Problem Details
без SQL, internal paths или content echo. Archived parents сохраняют read history, writes запрещены.
Page size1–100,page0–10000, sort version/createdAt/updatedAt с stable id tie-break.
PATCH — full replacement, не JSON Merge Patch. Root DELETE/lock API отсутствуют.

Approval body: `{"expectedVersion":1}`. Actor/time берутся на сервере из authenticated principal
и Clock; extra content/approvedBy/approvedAt не меняют подтверждаемый content/audit. Сервер
повторно проверяет actual active OWNER/membership в БД; revoked/absent/invalid actor403.
Stale/repeated approval409. Approval не редактирует content, а подтверждает committed draft.

Approved/locked edit сохраняет старую revision, создаёт max(version)+1 DRAFT с revisedFromId,
lockVersion0, approvedBy/approvedAt=NULL. Новый ID и Location возвращаются с201. Используйте
его для следующего edit/approval. Повторный PATCH approved source с актуальным source token
может создать ещё один draft: этот create contract не idempotent. Source content/audit/token
остаются неизменными. APPROVED→DRAFT transition и mutable approved collections запрещены БД.
History может содержать несколько approved revisions. С V42 сервер выбирает последнюю Identity
revision для новой Strategy и требует именно APPROVED; более новый DRAFT блокирует fallback.
Source ID/version/checksum/approval audit сохраняются в Strategy generation receipt, eligibility
повторяется до model egress и publication. См. [ADR0026](adr/0026-approved-identity-strategy-source.md)
и [Strategy API](strategy-api.md). Legacy history не получает выдуманного approval.

Схема: [OpenAPI](../src/main/resources/openapi/cadence.json).
Решения и следующие contracts: [ADR-0012](adr/0012-brand-identity-drafts.md).

## Draft из существующего analysis — V30

POST `/api/v1/brands/{brandId}/identities/from-analysis` возвращает201 + Identity Location.
Body: `{"expectedVersion":1}`; optional `analysisId` выбирает конкретный receipt.
Здесь expectedVersion — **Brand.lockVersion**, не Identity token. Без analysisId текущий
успешный receipt выбирается сервером. Missing/foreign explicit receipt404; нет текущего
успешного receipt, stale source/checksum или archived parent409; invalid body400.
OWNER/membership guards сохраняются на Controller/use-case; чужой Brand404.

Content сервер копирует из текущей signature под Workspace→Brand locks. Receipt должен
принадлежать этому Brand, иметь SUCCEEDED, совпадающие source versions и initial checksum.
Endpoint не вызывает AI, не принимает content/provenance от клиента, не создаёт citations
и не меняет Strategy source. Production AI provider остаётся disabled; legacy signature без
qualified receipt нельзя превращать в якобы generated revision.

Response добавляет nullable `analysisProvenance`: analysisId,provider,modelVersion,promptVersion,
warnings,initialOutputChecksum,generatedAt. Это ORIGINAL generation receipt. После manual edit
draft checksum текущего content может отличаться: UI не должен выдавать edit за новый AI output.
У manual drafts/approved branches metadata=NULL; branch source доступен через revisedFromId.
Read/list сохраняют происхождение после последующих analyses или archival. Canonical checksum
проверяется application/domain и PostgreSQL при initial insertion. Normalize-changing source
отклоняется; исторические receipts не переписываются. Copy не idempotent, cap1000 действует.

## Reviewed TEXT receipt → Identity — V35

POST `/api/v1/brands/{brandId}/identities/from-evidence-analysis`:
`{"analysisId":"UUID","expectedVersion":1}`. Оба поля обязательны; expectedVersion — Brand
token после analysis publication. Server actor = authenticated principal, actual active OWNER
и membership проверяются снова. Account → Workspace → Brand → Evidence locks; source graph
должен совпадать с immutable original admissions и текущим publication context:
Brand.version = attempt.brandVersion+1, Workspace version совпадает, source version/text/
checksum/policy не менялись и sourceVersion не withdrawn. 201 + Location + no-store.
Legacy from-analysis продолжает отклонять evidence-mode receipts. Copy не вызывает provider,
не принимает content/citations/copiedBy/role от клиента, не означает approval и не idempotent.

GET `/api/v1/identities/{identityId}/evidence-provenance` требует actual OWNER/membership:
identityId, nullable originIdentityId/analysisId/copiedBy/sourceChecksum и citations[]
(admissionId,evidenceId,sourceVersion,quote,artifactId,originalScanId,latestScanId,withdrawn). До 10 citations с bounds исходного
analysis. Manual/declared origins дают пустой массив и nullable evidence metadata.
History после withdrawal/parent archive остаётся читаемым; flags не являются общим eligibility
решением или доказательством истинности текста. Все responses no-store.

Approval повторно проверяет source graph/withdrawal и parent context. DRAFT edits сохраняют
original receipt; manual content не объявляется model-generated. Approved edits создают
новый DRAFT через revisedFromId; provenance читает original generation origin через same-Brand
lineage (strictly decreasing revision versions, максимум 1000 hops). Approval такого DRAFT
проверяет inherited evidence, поэтому branching не позволяет обойти withdrawal.
Withdrawal после approval не переписывает immutable APPROVED history. Strategy consumer V42
повторно проверяет inherited evidence eligibility, включая withdrawal/expiry/current scan и
private PDF/URL bytes, до модели и перед publication; approval не означает бессрочный consent.

Errors:400 malformed/missing input,401 anonymous,403 reserved role/actual revoked access,
404 foreign/missing Brand/receipt/Identity,409 stale source/context/version или archived write.
Generation deadline не является TTL completed receipt: она уже проверена при SUCCEEDED.
[ADR0016](adr/0016-evidence-identity-provenance.md), [qualification report](issue-121-identity-evidence-slice.md).

## PDF Identity consumer — V38

Reviewed PDF_TEXT receipts поддерживаются тем же explicit copy action. Успешная publication
увеличивает Brand.lockVersion: guard требует ровно attempt.brandVersion+1; original admission
проверяется относительно версии attempt, без ослабления current context checks.
Artifact/original/latest scan IDs в citations immutable и nullable для TEXT/legacy.
Historical read возвращает origin/quotes после withdrawal/expiry, без raw bytes/storage keys.

Copy и direct/inherited approval: locked capture → bounded private bytes read/hash вне DB
transaction → fresh locked account/membership/source/scan/expiry/ledger check → commit.
Concurrency2/process, batch30s; физический источник должен сохранять exact content и ledger
version. Safe503 при unavailable/corrupt bytes или перегрузке; stale metadata409.
APPROVED branches сохраняют inherited provenance и не обходят withdrawal. Нового model call
нет. Metadata SQL guards не могут проверять external bytes: storage qualification отдельно.
[ADR0020](adr/0020-pdf-identity-consumption.md), [отчёт](issue-121-pdf-identity-slice.md).

## URL evidence extension04

[ADR0023](adr/0023-url-analysis-identity.md): existing from-evidence-analysis и direct/inherited
approval принимают admitted URL projection с fresh physical/source/access/withdrawal guards.
Evidence provenance citations дополнены nullable fieldPath и urlSnapshotId; старые TEXT/PDF
сохраняют null fields и прежний смысл. Historical authorized read сохраняется после withdrawal.

## Social textual consumers05

[Typed social review API](social-evidence-admission-api.md) использует immutable social-text-v1
snapshot и explicit consent. Trusted origin запрещает generic TEXT fallback, включая legacy
и inherited Identity. Same-account current binding проверяется при review/generation/copy/approval.
Batch с social получает social-evidence-v3; прежние TEXT/PDF/URL receipts сохраняются.
[ADR0025](adr/0025-social-text-review-consumption.md), [qualification05](issue-121-social-consumption-slice.md).
Visual inputs остаются REQUIRES_PROVIDER, live OAuth/privacy не квалифицированы fixtures.
