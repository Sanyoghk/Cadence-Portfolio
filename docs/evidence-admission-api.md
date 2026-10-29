# TEXT evidence review / AI admission API

Реализовано2026-10-11 как отдельный slice #121. Contract: OpenAPI `cadence.json`, ADR0013.

## Workflow

1. OWNER регистрирует TEXT обычным evidence endpoint.
2. GET `/api/v1/evidence/{evidenceId}/ai-admission` показывает точный текст и version/checksum tokens.
3. Клиент показывает `text` как обычный escaped текст, без HTML execution.
4. После review POST на тот же route передаёт sourceVersion, brandVersion, workspaceVersion,
   contentChecksum из preview. Эти поля обязательны; actor/time/text не принимаются как новые данные.
5. Ответ201 — immutable admission metadata. Cache-Control:no-store на preview, success и errors.
6. Повторный POST409; stale token требует нового preview и review.

```json
{
  "sourceVersion": 0,
  "brandVersion": 0,
  "workspaceVersion": 0,
  "contentChecksum": "<64 lowercase hex characters from preview>"
}
```

Этот illustrative placeholder не является валидным checksum. Версии — соответствующие
lockVersion, не порядковые номера Identity revisions. sourceChecksum регистрационный и включает
`TEXT\n` prefix; contentChecksum описывает только UTF-8 bytes точного текста.

401 anonymous,403 reserved roles/actual inactive/non-OWNER/revoked membership,404 foreign/missing
source,400 malformed IDs/tokens/body,409 stale/replayed/archived/non-TEXT/invalid stored content.
DTO не раскрывают storage keys, credentials или provider diagnostics. Snapshot text ограничен20000
UTF-16 units; CR/LF/tab допустимы, malformed Unicode/прочие controls запрещены.

## Пределы

Это review source input, не approval Identity, не новый analysis и не обещание content safety.
Evidence.status сохраняется PENDING. На этом TEXT route URL/PDF/images дают409. Ни источник, ни admission не
привязываются к прежним analysis receipts: старые analysis evidence не использовали.
Нет AI transmission/model call/генерации citations. Admission не утверждает consent на неизвестный
provider; real provider/data policy и production qualification отдельны.

Before consuming snapshots: bounded server selection, source eligibility/revocation/lifecycle checks,
immutable generation source links, validated citations и publication fencing. Source changes/archive
не переписывают audit history; наличие старого snapshot само по себе не разрешает future generation.
# Дополнение: отзыв и получение актуального snapshot (2026-10-12)

Все маршруты ниже требуют Bearer authentication, actual active DB OWNER и текущую membership.
Ответы и ошибки имеют Cache-Control:no-store. TEXT остаётся недоверенными данными.

- GET `/api/v1/evidence/{evidenceId}/ai-admission/{admissionId}/snapshot` —200:
  `{ "admission": <EvidenceAdmissionDetails>, "text": "exact reviewed text" }`.
  Максимум20000 UTF-16 units, один snapshot. Проверяются source/parent versions, checksums,
  exact text, policyVersion, ACTIVE parents, TEXT/PENDING и отсутствие withdrawal. Stale/withdrawn409.
- POST `/api/v1/evidence/{evidenceId}/ai-admission/{admissionId}/withdrawals` —201:
  `{ "id": "uuid", "admissionId": "uuid", "withdrawnBy": "uuid", "withdrawnAt": "UTC instant" }`.
  Request body отсутствует; UUID выбирает immutable receipt, actor/time определяются сервером.
  Withdrawal допустим при stale/archived source. Повтор409; original review/text не меняются.
  После V33 отзыв любого receipt запрещает ту же sourceVersion во всех parent contexts.

Оба маршрута: anonymous401, reserved roles/revoked account/membership403, чужой или связанный
с другим evidence admission404, malformed UUID400. Отзыв в текущем контракте необратим;
тот же sourceVersion нельзя повторно подтвердить. Admission не является разрешением отправлять
данные неизвестному provider. Snapshot current только в момент transaction, а не навсегда:
future generation обязана повторно проверять withdrawal и source перед publication.
Это не batch selection, AI call, citations, READY transition или durable AI Job.
После V33 parent version change делает snapshot устаревшим; новый GET preview и explicit POST
разрешают новый review context. UNIQUE(evidence_id,source_version,brand_version,workspace_version)
отличает re-review от replay; те же tokens409.1000 reviews/evidence, затем409.
Withdrawn sourceVersion не может быть повторно разрешена даже после parent rename/другим OWNER;
все receipts этой версии исключены из snapshots. Новый review новой sourceVersion требует
actual changed current source/checksum/tokens; public source editing API этим этапом не добавлен.
Regrant той же withdrawn sourceVersion остаётся неутверждённым. [ADR0014](adr/0014-versioned-text-re-review.md).

## Отдельные non-TEXT routes

PDF использует `document-review` и ADR0017–0020. URL textual admission теперь реализован отдельно:
[URL API](url-evidence-admission-api.md), [ADR0022](adr/0022-url-text-admission.md).
Общий withdrawal endpoint выше принимает и URL receipts; current URL snapshot читается только
через `url-review/admissions/{admissionId}/snapshot` с физической проверкой retained bytes.
URL model consumption остаётся disabled до04; TEXT/PDF behavior не меняется.

## Social textual consumers05

[Typed social review API](social-evidence-admission-api.md) использует immutable social-text-v1
snapshot и explicit consent. Trusted origin запрещает generic TEXT fallback, включая legacy
и inherited Identity. Same-account current binding проверяется при review/generation/copy/approval.
Batch с social получает social-evidence-v3; прежние TEXT/PDF/URL receipts сохраняются.
[ADR0025](adr/0025-social-text-review-consumption.md), [qualification05](issue-121-social-consumption-slice.md).
Visual inputs остаются REQUIRES_PROVIDER, live OAuth/privacy не квалифицированы fixtures.

## Bounded admission history (frontend24)

`GET /api/v1/evidence/{evidenceId}/ai-admission/history?page=0&size=20` returns PageResponse
of metadata receipts, sorted reviewedAt DESC/id DESC. Page0..10000; size1..20. Response no-store;
current active OWNER, actual workspace membership and scoped evidence are required. Other
resource/role/scope yields the existing controlled403/404 contract. Bad pagination400.

Includes id/evidenceId, sourceVersion/brandVersion/workspaceVersion, sourceChecksum/contentChecksum,
reviewedBy/reviewedAt/policyVersion, optional artifactId/originalScanId/latestScanId/snapshotId,
scanExpiresAt/bindingExpiresAt and withdrawn/withdrawnBy/withdrawnAt. SnapshotId is the immutable
URL or validated social snapshot selector for that policy. No original bytes, private storage
keys, review text or provider credentials are returned. Withdrawal applies to sourceVersion,
so another context receipt for the same version is also shown as withdrawn. Historical scan
expiry is not a current clean scan. Use the existing kind-specific snapshot route to recheck
physical availability/access/version/scan/expiry/withdrawal before use. Archive history remains
readable with current access; new consent/generation stays denied by existing policies.

[Frontend flow/security/gates](prompt24-identity-report.md).

History projection detail: `withdrawnBy`/`withdrawnAt` refer to the latest actual recorded
withdrawal for the entire sourceVersion (withdrawnAt DESC/id DESC), which can originate from
another context receipt of that same version. They are not invented per-receipt events.
Admission review fields remain original; a later withdrawal changes this effective metadata
projection, without rewriting any stored review/withdrawal record. The endpoint is a paged
admission history with effective version withdrawal, not an unbounded event-log export.
