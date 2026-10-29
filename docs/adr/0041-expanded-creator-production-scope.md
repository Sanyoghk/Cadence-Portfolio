# ADR0041 — расширенный creator workflow: media production и approved publishing

Дата:2026-10-20. **Product scope принят прямым запросом пользователя.**
Технические contracts/реализация ниже планируются; provider/cost/privacy decisions не приняты.
[Product scope](../product-scope.md), [план V2](../astra-medium-mvp-prompts-v2.md).

## Изменение решения

Прежний CSV-first MVP ограничивал Content editorial drafts/demo assets и manual metrics.
Пользователь потребовал готовое video, raw-footage editing, organic market/hashtag analysis,
approved publication в Instagram/TikTok, регулярные reports/reminders и ongoing Strategy.
Поэтому direct publishing и platform synchronization теперь required target capabilities.
CSV остаётся supported output. Billing/agency/paid ads не включены.

Supersession ограничен scope, а не отменой технических защит:

- [ADR0003](0003-export-first-publishing.md): запрет direct publishing как граница MVP заменён.
- [ADR0027](0027-backend-first-ai-adapters-last.md): независимый backend уже построен;
  завершение real-AI backend до нового UI сохраняется, новые providers по-прежнему требуют решения.
- [ADR0029](0029-full-playbook-contract.md): v2 history сохраняется; factual market research
  и production guidance планируются новой schema.
- [ADR0034](0034-private-demo-media.md): ограничения demo fixtures не разрешают real bytes;
  новый media path получает отдельный decoder/rights/privacy/usage contract.
- [ADR0038](0038-evidence-linked-recommendations.md): heuristic остаётся честным historical
  origin; расширенные AI actions получают отдельный durable contract.
- [ADR0039](0039-creator-performance-reports.md): HTML/JSON/retention package сохраняется;
  recurrence/market section добавляются, PDF/email остаются отдельным решением.

## Предлагаемая архитектура

Modular monolith сохраняется. Новые boundaries: market snapshots/research, media production,
social account connection/publishing и notification delivery. Названия будущих сущностей
в плане описательные до V2-01, не существующие Java classes.

Два производственных пути используют общий PostRevision/final package:

```text
approved Identity + Strategy + market evidence + brief
  → generated scenes OR raw footage → validated EditPlan/assembly
  → isolated renderer + media validation → final asset
  → human review + exact final approval → separate PublishIntent/consent
  → durable dispatch/reconciliation → verified platform result
  → metrics/market refresh → report/recommendation → new Strategy DRAFT
```

Принципиальная граница: согласие на sample не даёт права публиковать изменённый final render.
Local atomic intent/outbox устраняет потерянные задания; remote outcome остаётся uncertain
при некоторых сбоях. Не обещаем exactly-once delivery без platform guarantees.

## Security и alternatives

Untrusted media декодируется вне API process в sandbox с resource limits. Model output —
typed instructions, не shell. OAuth credentials tenant-bound/encrypted/server-only;
fresh access/consent/source/lease checks перед egress и DB publication. Новые SQL constraints
защищают ownership/history/cost invariants; старые migrations/checksums неизменны.

Общий универсальный agent с правами download/render/publish отклоняется: prompt injection
дала бы authority внешним текстам/видео. Отдельные validated ports упрощают negative testing.
Переписывание всей backend архитектуры не требуется. Standalone ML service не добавляется
только ради названия AI; isolated renderer/GPU worker выбирается по реальной нагрузке.

## Gates

Backend/API readiness → новый frontend → platform UX/audit/live full journey → release.
External app approvals/данные/качество/расходы квалифицируются отдельно от fixtures.
Docs-only принятие требований не активирует ни один provider и не разрешает deployment.
