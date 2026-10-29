# Social textual review, generation и Identity

Policy: [ADR0021](adr/0021-source-kind-ai-eligibility.md), implementation
[ADR0025](adr/0025-social-text-review-consumption.md). Только approved Instagram/TikTok text
projection. Изображения/screenshots/OCR — REQUIRES_PROVIDER; [точная граница](adr/0024-visual-evidence-provider-boundary.md).
Это не разрешение live AI egress и не qualification OAuth #32/#33.

## HTTP

Все операции требуют authenticated actual OWNER и текущую membership, scoped tenant lookup.
Responses no-store; foreign resource не раскрывается. JSON body не принимает actor/role/provider/
account/content: actor берётся из principal, content — из immutable server snapshot.

1. `GET /api/v1/evidence/{evidenceId}/social-review` возвращает snapshotId, source/brand/
   workspaceVersion, sourceChecksum/contentChecksum, exact `text` JSON, provider, capturedAt,
   bindingExpiresAt, policyVersion и warnings. GET материализует immutable projection при первом
   preview; не выполняет fetch, не создаёт consent. Projection содержит accountName, biography,
   posts[publishedAt,text,title only TikTok]. Reviewer видит historical one-page/text-only limits.
2. `POST /api/v1/evidence/{evidenceId}/social-review/admissions` возвращает201. Тело:

```json
{
  "snapshotId": "00000000-0000-0000-0000-000000000001",
  "sourceVersion": 0,
  "brandVersion": 0,
  "workspaceVersion": 0,
  "contentChecksum": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "bindingExpiresAt": "2026-10-14T12:00:00Z"
}
```

Это placeholders для формы, не действительный token. Использовать значения exact preview.
Ответ `SocialAdmissionDetails`: `review` (immutable actor/time/context/hash/policy receipt),
snapshotId, capturedAt, bindingExpiresAt. Unknown fields и malformed selectors —400;
stale/withdrawn/duplicate/history cap —409; unavailable approved binding —503.

3. `GET /api/v1/evidence/{evidenceId}/social-review/admissions/{admissionId}/snapshot`
   возвращает current `admission` и exact `text`. Все source/context/binding/withdrawal guards
   повторяются. Binding expiry должен оставаться строго дальше now+30 seconds и совпадать
   с receipt. Renewed binding требует нового explicit review, а не молчаливого обновления receipt.
4. Общий `POST /api/v1/evidence/{evidenceId}/ai-admission/{admissionId}/withdrawals` отзывает
   будущий допуск всей sourceVersion. Current read/generation/copy/approval затем запрещены;
   history сохраняется. Endpoint доступен при archive или expired binding. Точный общий contract:
   [evidence admission API](evidence-admission-api.md).

Generic TEXT review запрещён, если существует trusted social origin, даже если typed snapshot
malformed/unavailable. JSON без trusted origin остаётся ordinary untrusted TEXT.

## Consumers

Используются existing analyze-evidence, Identity from-evidence-analysis, provenance и approval
endpoints. Batch выбирает backend, максимум5 sources и20000 UTF-8 bytes, все источники проверяются
совместно. Social→`social-evidence-v3`; old TEXT/PDF/URL digest formats не переписываются.

`AnalysisCitation.fieldPath`: accountName, biography, posts[0..19].text/title; quote — exact
decoded substring. timestamp не может быть самостоятельной содержательной citation. Nullable
`socialSnapshotId` в Identity provenance связывает цитату с immutable projection, original post-ID
mapping остаётся в локальном snapshot. В model payload нет provider account/post IDs, API version,
credentials, private storage refs или произвольных fetch/tools.

Withdrawal или изменение permissions/source/context/binding до final transaction запрещает
publication всего batch. Copy и direct/inherited approval повторяют current source policy;
APPROVED history и historical provenance не переписываются при последующем revoke.

Срок хранения, external processor consent/region/retention и live OAuth остаются отдельными
решениями. Историческая дата snapshot не является выдуманным marketing TTL или malware scan.
