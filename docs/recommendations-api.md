# Evidence-linked recommendations API

Contract/threat model: [ADR0038](adr/0038-evidence-linked-recommendations.md).
Задание21/#100; report generation из #105 — отдельное задание22.

## Создание и review

```text
POST /api/v1/brands/{brandId}/recommendations
Idempotency-Key: recommendation-request-1
Content-Type: application/json
```

```json
{
  "strategyId": "00000000-0000-0000-0000-000000000001",
  "expectedStrategyVersion": 1,
  "fromDate": "2026-10-01",
  "untilDate": "2026-10-17",
  "timezone": "Europe/London",
  "platform": "INSTAGRAM"
}
```

Здесь version — optimistic lockVersion конкретной APPROVED Strategy, не revision number.
Нужна latest full Strategy, current OWNER/membership, ACTIVE account/Workspace/Brand.
PERIOD window задаётся local dates inclusive/exclusive, максимум28 local days. Counts должны
точно совпадать с этим окном; daily snapshots не суммируются в unique period reach.

Backend выбирает два известных pillars этой Strategy и platform. Engagement/reach — отношение
сумм complete pairs по ADR0037. Для каждого cohort минимум5 distinct Posts и100 reach;
missing/zero denominator/inconsistent/stale/unknown attribution запрещают рекомендацию.
Разница rounded percentages минимум2pp. Предложение — проверить перенос5 points pillar
weight от нижнего результата к верхнему при допустимых weights0..100/sum100.
Max100 supporting observations; cap не является скрытым truncation. Общий reader cap1000.

Результат201, replay200, Location указывает scoped recommendation detail. Это bounded
синхронный HEURISTIC, не async AI Job/live inference. Origin/rule/provider записаны навсегда;
modelVersion/promptVersion/calibratedProbability/expectedLift NULL, confidence UNCALIBRATED,
claimType HYPOTHESIS. Explanation формирует backend из проверенного typed action, не из
непроверенной model prose. Увеличение будущего результата не обещается.

RecommendationInput содержит только bounded counters, pillar weights, exact UTC window и
citation selectors. Tenant/actor IDs, raw receipts/private references, Identity content и
полный Playbook не передаются engine. Live egress/model/cost/privacy не выбраны и не включены.
Текущий adapter — pure local rule; external adapter требует собственной durable Job/budget/
privacy qualification до activation. Cost/moderation не выдумываются из отсутствующих данных.

```text
GET /api/v1/recommendations/{id}
GET /api/v1/brands/{brandId}/recommendations?page=0&size=50
```

List — короткий summary, page0..1000/size1..50. Detail — bounded immutable capture:
source Strategy ID/revision/lock/checksum, original Identity lineage, selected observations,
attribution IDs/versions/content checksums, original source/ingestion times и actors,
original UTC boundaries/tzdb version, snapshot checksum и eligibility time. Raw imports,
provider payloads и storage keys не выдаются. GET показывает историю, не обещает current
eligibility: новый apply повторяет проверки. Все ответы no-store; knowing UUID не даёт доступ.

## Apply / dismiss

```text
POST /api/v1/recommendations/{id}/apply
Idempotency-Key: apply-request-1
Content-Type: application/json

{"expectedVersion":0}
```

Recommendation record immutable (lockVersion0). Первый apply повторяет current access,
source/Identity/evidence/scan/withdrawal/expiry и selected metric checks, проверяет retained
PDF/URL bytes через существующую verification boundary. Потом создаёт новый Strategy DRAFT
с revisedFromId, human actor/time и только proposed5-point patch. Approved source сохраняется.
Outline/cadence/остальные Playbook fields копируются; пользователь проверяет draft целиком.
Дальнейшие edit/approval идут через существующий Strategy API, отдельно от apply.

Application receipt immutable: operation ID, actor/time, new Strategy ID и initial checksum.
Этот checksum не становится checksum более поздней human edit. Same-key replay возвращает
тот же receipt, не создаёт новую revision и не откатывает изменения. Другой apply key после
resolution409. POST `.../dismiss` с тем же body/header сохраняет dismissal без Strategy mutation;
APPLY/DISMISS mutually exclusive. Historical receipts остаются доступны по current authorization.

## Idempotency / failure / recovery

Scope: actual actor+Workspace+operation+SHA256(key). Digest включает Brand/source/query или
recommendation/action/version. Key1..128 ASCII `[A-Za-z0-9._:-]`; unknown fields400.
Admission binding фиксируется до вычисления и переживает failure/timeout/crash. Different
payload на retained key409 даже после failed attempt. До успешной publication тот же pure-rule
request можно повторить с current source rechecks; один successful result/receipt сохраняется
атомарно. Это не обещание retry-safe для внешнего provider. Retained keys10000/Brand,
recommendations1000/Brand; TTL/удаление истории не придуманы.

Два instance slots и bounded executor,3s rule wait,30s overall publication deadline.
Rule/model I/O не удерживает DB locks. Late output не публикуется; process restart не делает
partial recommendation. Same-key concurrent apply может стать replay только при существующем
authorized identical receipt. Access denial не превращается в replay.

Malformed/foreign body400/404; wrong role/revoked membership403; stale/insufficient/conflict/
quota409; unavailable/capacity/timeout503 с безопасным Problem Details. Для нового чтения после
correction или нового Strategy approval нужен новый key и явный request. Source changes не
переписывают original supporting observations/capture. Нет public debug/admin/model bypass.
