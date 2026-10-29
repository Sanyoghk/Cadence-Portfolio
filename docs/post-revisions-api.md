# Post identity/revision API — задание14

Contract: [ADR0031](adr/0031-post-revision-lifecycle.md), reviewed OpenAPI:
`src/main/resources/openapi/cadence.json`. OWNER-only с actual active account/membership.
Все timestamps UTC; foreign/missing resource404, invalid input400, stale/lifecycle/replay409.
Archived-parent history readable; mutations denied. PostgreSQL/runtime guards независимы от HTTP.

## Endpoints

| Method / path под `/api/v1` | Result |
| --- | --- |
| POST `/brands/{brandId}/posts` |201 + Location Post identity; manual DRAFT revision1 |
| GET `/brands/{brandId}/posts?page=0&size=20` | latest revision каждой identity, bounded PageResponse |
| GET `/posts/{postId}` | latest revision |
| GET `/posts/{postId}/revisions?page=0&size=20` | история, revisionNumber DESC/id tie-breaker |
| GET `/posts/{postId}/revisions/{revisionId}` | exact historical revision |
| PATCH `/posts/{postId}/revisions/{revisionId}` |200 DRAFT replacement или201 new DRAFT + revision Location |
| POST `/posts/{postId}/revisions/{revisionId}/approval` |200 committed DRAFT approval |

List size1–100, page0–10000; Post count≤10000/Brand, history≤1000 revisions/identity.
No destructive history endpoints. No provider/schedule/export operation in14.
POST create always means a new identity; idempotency from generation12 не приписывается этому API.
Для mutation retry сначала read: старый version/revision даёт409, а не вторую approved branch.

Create body (UUID реальной approved Strategy и её текущий lockVersion берутся из Strategy API):

```json
{
  "strategyId": "00000000-0000-0000-0000-000000000001",
  "expectedStrategyVersion": 1,
  "platform": "INSTAGRAM",
  "content": {
    "title": "Знакомство с брендом",
    "caption": "Текст для проверки автором",
    "hashtags": ["#cadence"],
    "format": "REEL",
    "mediaPrompt": "Идея визуального сопровождения",
    "videoScript": "Короткий сценарий ролика"
  }
}
```

PATCH body: `expectedVersion` и полный `content` того же формата. Approval body: только
`{"expectedVersion": 0}`. Unknown fields во всех nested objects400; actor/time, provider,
approval, source ownership и checksum client не выставляет. Nullable optional videoScript
допускает отсутствие; mediaPrompt обязателен и не означает наличие generated asset.
Instagram поддерживает IMAGE/CAROUSEL/REEL/STORY; TikTok VIDEO/CAROUSEL. Script только VIDEO/REEL.
Новые captions≤2200/4000 соответственно, hashtags≤30, остальные bounds — ADR/OpenAPI.
Это внутренние supported editorial limits, не квалификация live publishing API.
Переводы строк/табуляция в editorial text допустимы; NUL/прочие controls/unpaired surrogates400.
Plain content с командами/URL не исполняется и не приводит к fetch.

## Identity, source и audit

Response `id` — стабильный Post identity UUID; `revisionId` — UUID конкретной revision.
`revisionNumber` — порядковый номер immutable branch; `lockVersion` — optimistic token текущего
содержимого этой revision. Эти три значения не взаимозаменяемы. Approval повышает lockVersion.
Изменение approved создаёт revisionNumber+1, новый revisionId и lockVersion0; прежняя approval
остаётся в history. Мутировать можно только latest revision, в том числе для approved branching.

`source` хранит exact Strategy ID, revisionNumber, lockVersion, approvedBy/At и SHA256 canonical
**approved content** по ADR0029. Это checksum source после человеческих edits, а не переписанный
original model-output receipt. Source generation/Identity/evidence history остаётся в самой immutable
Strategy. Captured source не заменяется при новой approval/edit Strategy; тот же exact source должен
оставаться full APPROVED. LOCKED/изменённый source даёт409. Без full source создать Post нельзя.

`authoredBy/At` — создатель данной revision; `editedBy/At` — последний actual human editor;
`approvedBy/At` — отдельный reviewer. Никакие эти поля не считаются model provider output.
В14 нет generation receipt; generation15 добавит собственный validated contract, не backfill.

`current` означает latest revision на момент read, а `contentApproved` — current APPROVED с audit.
Оба флага не предоставляют authorization и не обещают media/schedule/export readiness.
Consumers16/18 должны повторить current actor/parents/source/revision checks и свои guards.
Manual workflow14 не читает исходные evidence bytes и не делает model egress; withdrawal evidence
не стирает historical creative source автоматически. New AI generation15 требует отдельного review
допустимых inputs/currentness, без использования этих флагов как egress permission.

## Migration и recovery

V47 выполняется после V46 отдельной migration identity. V1–46 не изменяются.
До upgrade остановить legacy SocialPost writers; после migrations применить актуальный
`docker/postgres/runtime-permissions.sql` через migration/admin connection и запускать приложение
только как runtime user. Runtime не имеет INSERT/UPDATE/DELETE к retired legacy tables.

Каждая existing SocialPost становится identity с тем же UUID и revision1 LEGACY. Original
title/caption/hashtags/format/status/scheduledAt/timestamps сохраняются; missing platform/source
approval/actor NULL. Old metrics FK и original tables сохранены. LEGACY read-only и не eligible.
Automatic reinterpretation/upgrade отсутствует: creator создаёт новый Post с full source и explicit
content. Mapping legacy metrics к новой модели — отдельная задача19; fabricated approval запрещена.

Новые таблицы добавляются без удаления old tables. При проблеме остановить application, сохранить
БД и диагностировать Flyway; не удалять approved history и не править checksum старой migration.
Downgrade к legacy writer после cutover не поддерживается; восстановление — согласованный restore
из проверенного backup либо forward migration. Backup/restore на production не квалифицированы14.

## Оставшийся scope

15 — provider-neutral generation/regeneration Jobs и provenance;16 — actual UTC/local scheduling,
DST/cancellation/reschedule;17 — media references/readiness;18 — durable safe CSV. #115/#103 остаются
open до своих полных acceptance criteria. Pure human lifecycle14 не является всей Content workflow.
