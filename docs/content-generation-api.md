# Content generation / regeneration — task15

Contract: [ADR0032](adr/0032-content-generation-jobs.md). Existing [Post API](post-revisions-api.md)
и [durable Jobs](durable-ai-generation.md) сохраняются. Все маршруты требуют Bearer authentication,
actual active OWNER и актуальную membership. Production adapter по умолчанию `disabled`;
`fake` допустим только в local/test без prod. Результат fake помечен `DEMONSTRATION_ONLY`.

## Submission

`POST /api/v1/brands/{brandId}/content-jobs`, обязательный `Idempotency-Key` (ASCII1–128):

```json
{
  "expectedBrandVersion": 0,
  "strategyId": "00000000-0000-0000-0000-000000000001",
  "expectedStrategyVersion": 1,
  "slotIds": ["w1s0", "w2s0", "w3s0", "w4s0"]
}
```

IDs и versions здесь — пример формы, не fixture, пригодная для реального запроса. Нужна конкретная
full APPROVED Strategy с доказуемой approved Identity lineage. `slotIds` —1–84 уникальных local IDs
из exact four-week outline; сортировка нормализует digest. Можно выбрать все slots или subset.
Это editorial plan, ещё не executable calendar/timezone/DST schedule16.

Ответ `202`, `Location: /api/v1/jobs/{jobId}`, `Cache-Control: no-store`, existing Job view с
`kind: CONTENT`. Replay того же actor/workspace/kind/key и нормализованного request возвращает
ту же operation; terminal replay —200. Изменённый payload с тем же key —409. Replay не создаёт новую
reservation или provider call. Ошибки validation400, stale/ineligible source409, unavailable503,
budget/cap429 согласно общим contracts. Foreign Strategy/Post/result скрыты404; reserved roles403.
Writable DTO исключает actor, owner, provider, source provenance и approval; unknown fields400.

## Result и review

`GET /api/v1/jobs/{jobId}` возвращает durable status и после success
`resultUrl: /api/v1/content-results/{batchId}`. Этот GET возвращает immutable original provider output,
его SHA-256, operation/attempt, captured Strategy/Identity audit и `field`, а также exact
`slotId → postId/revisionId` links. History доступна по Post API, даже если underlying evidence
впоследствии withdrawn. Read history не означает разрешение нового AI egress или delivery.

Каждый item содержит `title`, `caption`, `hashtags`, `format`, `mediaPrompt`, nullable `videoScript`;
channel/format/bounds соответствуют ADR0031. Provider output должен совпасть с выбранным set slots,
не может добавить foreign slot, Post ID, asset URL, moderation verdict или cost. Полный input,
private snapshot и output ограничены каждый64KiB UTF-8. Очень большой, но структурно approved Playbook
может не поместиться вместе с Identity/baseline в этот envelope: capture отвечает400 без сохранённого Job
и расхода; truncation или обход лимита отсутствуют. Batch публикуется целиком либо откатывается.

Posts имеют `DRAFT`; `editedBy/editedAt` до первого human edit NULL, requester/time и generation
receipt сохранены отдельно. Необязательные `generationBatchId/generationSlotId` появляются в Post
response только для generated revisions, сохраняя прежний JSON shape ручных Posts.
Human edit и отдельная approval выполняются existing Post API с `expectedVersion`.
Human edit не переписывает original provider receipt; все response snapshots — DTO, не JPA entities.

## Regeneration

`POST /api/v1/posts/{postId}/regeneration-jobs`, обязательный новый `Idempotency-Key`:

```json
{
  "revisionId": "00000000-0000-0000-0000-000000000002",
  "expectedVersion": 0,
  "slotId": "w1s0",
  "field": "CAPTION"
}
```

`field`: `ALL`, `TITLE`, `CAPTION`, `HASHTAGS`, `FORMAT`, `MEDIA_PROMPT`, `VIDEO_SCRIPT`.
Brand, Strategy, actor и existing platform сервер определяет по scoped Post. Revision должна быть
latest DRAFT/APPROVED и иметь exact expectedVersion. Generated Post связан с original slot;
manual Post может выбрать compatible slot своего exact Strategy, если доказуема Identity lineage.

Всегда создаётся **новая DRAFT revision того же Post**. Старый DRAFT становится `SUPERSEDED`, его
content/human audit неизменны; старый APPROVED остаётся APPROVED. Approval не копируется. Provider
возвращает полный valid item, сервер берёт только выбранное поле и merge с captured baseline, затем
проверяет весь content. Несовместимый FORMAT-only/script combination отклоняется: используйте ALL.
Original full provider output и applied field доступны в receipt; unchanged human fields не
приписываются provider. История ограничена1000 revisions, Brand —10000 Posts.

## Recovery / operation

Используется существующий AI_GENERATION worker: input/schema3 persisted, maximum3 pre-dispatch
attempts, total deadline60s, lease≤20s и bounded executor2. Ingest queue не выполняет generation.
I/O идёт вне DB transactions. Actual access, Strategy, latest exact Identity и inherited TEXT/PDF/URL/
social evidence eligibility/physical state перепроверяются до egress и publication. Новая Identity
draft отменяет stale source; новая Strategy revision не подставляется вместо captured ID.

Expired lease до dispatch позволяет bounded retry. После dispatch без подтверждённого исхода —
`OUTCOME_UNKNOWN`, расход `HELD_UNKNOWN`; автоматический повтор запрещён. Подтверждённый local fake
completion имеет `SYNTHETIC` usage, включая случай rejected publication; это не live cost/latency.
Stale source/access/target revision или lost lease не публикуют Posts. Durable attempt reason,
Job/operation/attempt correlation и bounded counters — в [runbook](ai-jobs-recovery-runbook.md).

При deployment применить V48 и `docker/postgres/runtime-permissions.sql` migration identity;
runtime не получает UPDATE/DELETE receipt или UPDATE revision provenance. Public debug bypass нет.
Настоящие Content/media provider, assets, moderation, privacy и measured accounting отложены на
AI integration stage; scheduling16, asset contracts17 и export18 — отдельные задачи.
