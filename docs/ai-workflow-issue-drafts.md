# Задание09 — issue discovery, mapping и точные drafts

Дата: 2026-10-15, baseline `f2f60c9`. Ниже готовые Title/Body для трёх отдельных issues.
Они НЕ отправлены и не получили GitHub numbers. Создание не prerequisite local design.
Перед будущим create повторить duplicate search; не заменять тела существующих issues.

## Discovery evidence

GitHub connector search, все состояния, topn100, repository `Sanyoghk/Cadence`:
`is:issue "AI"`, `is:issue "budget"`, `is:issue "Playbook"`, `is:issue "idempotency"`,
`is:issue "Jobs"`. Результаты соответственно23/7/5/10/9, не достигли topn.
Прочитаны полные issues107/120/121/110/97/102; titles и scope сопоставлены с actual code.

| Existing issue | Actual overlap / решение |
| --- | --- |
| [#110](https://github.com/Sanyoghk/Cadence/issues/110), closed | Общая jobs/retry/idempotency foundation; reuse, не reopening как AI acceptance |
| [#114](https://github.com/Sanyoghk/Cadence/issues/114) | Existing provider-neutral analysis; не durable generation |
| [#97](https://github.com/Sanyoghk/Cadence/issues/97), closed | Existing Strategy generation port; не full fields/async |
| [#102](https://github.com/Sanyoghk/Cadence/issues/102), closed | Existing Strategy REST lifecycle; не full Playbook |
| [#121](https://github.com/Sanyoghk/Cadence/issues/121), open | Source/Identity contracts prerequisite, explicitly excludes new Jobs/budgets/full Playbook |
| [#120](https://github.com/Sanyoghk/Cadence/issues/120), open | Adapter integration, потребляет будущие guards; не реализация всех трёх scopes |
| [#107](https://github.com/Sanyoghk/Cadence/issues/107), open | Master mapping; follow-ups ещё без отдельных найденных issues |
| #32/#33/#117 | Ingest/provider budgets, не AI operation accounting |
| #115/#118/#3 | Content/E2E/frontend consumers, не эти самостоятельные backend scopes |

По выполненному поиску отдельные актуальные issues для A/B/P не найдены. Это search evidence,
не заявление о всех возможных совпадениях: повторить поиск непосредственно перед созданием.

| Prompt | Local draft | Dependencies / acceptance boundary |
| --- | --- | --- |
| 10 | A | ADR0028; qualified01–06 supported sources; minimal scoped key mapping обязателен |
| 11 | A | 10; recovery/observability завершает A, не дублирует уже доказанное10 |
| 12 | B | A operation/attempt/dispatch contracts; все generation entry points закрыты budget guards |
| 13 | P | A+B; ADR0029, qualified06 approved source |

## Draft A

Title: [Task 2.4] Durable Brand/Strategy generation operations and recovery

Body:

Реализовать provider-independent async generation по ADR0028. Dependencies: #110 queue,
#114 analysis, #97/#102 Strategy, qualified supported #121 source contracts, #107 master.
Закрытие #121 и выбор provider #120 не prerequisites; production остаётся fail-closed.

Scope:
- Additive POST analysis-jobs/strategy-jobs →202 + Job Location; scoped typed GET Job/result.
- Immutable server-captured actor/scope/source/checksum/policy/deadline; minimal scoped
  request-digest idempotency mapping, transactional operation+Job creation.
- Typed queue claim, durable attempts/dispatch intent, parent-first lock order, source/physical
  and actual DB permissions before egress/publication. Internal worker не доверяет saved JWT.
- Atomic one-result publication; analysis receipt/signature и explicit Identity copy сохраняются,
  Strategy только DRAFT. Existing synchronous API/history/citations compatibility.
- Safe pre-dispatch retry, bounded restart/recovery, uncertain post-dispatch outcome без blind
  retry; correlation/safe error/metrics/runbook. Job status не human approval.

Acceptance:
- [ ] Submit→worker→one committed result→scoped status/read; same-key replay без новой operation.
- [ ] Anonymous/role/foreign/forged actor/unknown fields denied, membership revoked after submit denied.
- [ ] Double workers, lost lease/same workerId new attempt, restart at every dispatch/commit boundary tested.
- [ ] Source/Identity edit/archive/scan/expiry/withdrawal races и corrupt/unavailable bytes no stale publication.
- [ ] Ambiguous execution returns OUTCOME_UNKNOWN; no invented zero cost or automatic chargeable retry.
- [ ] PostgreSQL FK/unique/transition/runtime-role guards и populated migration preserve old data/digests.
- [ ] TEXT SUPERSEDED, PDF/URL/social, Identity approval и Strategy edit/history regressions pass.
- [ ] Full Maven/Docker/Python/Linux/renderer/startup gates, API/docs/report/local qualified commits.

Delivery slices: prompt10 submission/worker/publication; prompt11 remaining recovery/operations.
Не закрывать A по одному10. Budgets/settlement — draft B; production activation требует A+B.
No provider selection, public cancellation, auto approval, deployment or fake production fallback.

## Draft B

Title: [Task 2.5] Scoped generation idempotency and durable budget accounting

Body:

Реализовать uniform admission/accounting по ADR0028. Dependencies: draft A (заменить ссылкой
после фактического создания), #107; будущие #120 adapters обязаны использовать этот contract.

Scope:
- Actor/workspace/operation/key digest + canonical request replay/conflict, bounded retained keys.
- Atomic account/workspace/global queued/running/spend reservations и immutable policy/period/unit.
- RESERVED/SETTLED/RELEASED/HELD_UNKNOWN; dispatch intent, safe retries, late accounting,
  duplicate reconciliation и explicit overrun. No floating-point currency arithmetic.
- Sync legacy и async entry points используют общий enforcement; retries не обходят quota.
- UNKNOWN/ESTIMATED/MEASURED/SYNTHETIC usage, separate queue/provider latency и moderation state.
- Validated bounded configuration; production disabled без approved cost/privacy policy.
  Synthetic fixtures не являются тарифами или настоящими платежами; legacy NULL не backfill.

Acceptance:
- [ ] Concurrent same key exactly one operation/reservation; changed payload409, foreign key no disclosure.
- [ ] Multiple workers/actors/workspaces не обходят applicable caps, cap boundaries/overflow tested.
- [ ] Crash after dispatch не release unknown spend; rollover сохраняет hold; repeated settlement no double debit.
- [ ] Superseded/invalid output still accounts incurred cost; actual overrun recorded, new calls denied.
- [ ] Removed access не возвращает replay result, saved JWT и policy change не rewrite captured provenance.
- [ ] Restricted runtime/SQL ledger invariants, populated migration и current API compatibility proven.
- [ ] Full gates/startup, safe errors/logs/metrics, API/config/runbook/report/local commit.

Не добавлять billing/payment API, approved-edit idempotency или продуктовые тарифы по догадке.
Real provider pricing/retention/reconciliation qualification — после backend по ADR0027.

## Draft P

Title: [Task 3.3] Complete versioned Strategy Playbook and Content source contract

Body:

Доставить approved MVP fields по ADR0029. Dependencies: #97/#102, qualified06 из #121,
drafts A+B (заменить реальными ссылками после создания); master #107.

Scope:
- Versioned PlaybookContent v2: audience assumptions, channels/goals/mix, tone spectrum,
  competitive whitespace hypotheses/insufficient evidence и four-week outline.
- Existing pillars/cadence/Identity source, shared field semantics, bounded typed DTO/domain/provider output.
- Async generate→DRAFT→edit expectedVersion→human approval→immutable history/full Content source.
- Explicit LEGACY_PARTIAL v1; no fabricated fields/audit/citations, explicit upgrade, no v2 downgrade.
- SQL content/collection/approval guards и API/OpenAPI completeness/schema representation.

Acceptance:
- [ ] Full Playbook generation/edit/approval/read works with deterministic isolated fixtures.
- [ ] Missing/duplicate weeks, cross-revision refs, bad weights/channel mix/rounding/timezone and oversized text denied.
- [ ] Foreign/role/forged audit, new Identity DRAFT, source/access/withdrawal/expiry races denied during generation.
- [ ] Approved edit new DRAFT, original provenance immutable; stale edits and SQL bypass fail.
- [ ] Populated v1 migration/read/approval preserved; upgrade explicit; old partial PATCH cannot erase v2.
- [ ] Complete approved v2 distinguished from partial legacy for subsequent Content consumer14–15.
- [ ] Full PostgreSQL/runtime/security/Maven/Docker/Python/Linux/startup gates, API/docs/report/local commit.

No executable Post schedule, content generation, external market research, provider selection or
claim of marketing correctness. Dates/DST belong to Content16; human review required.

## External publication status

Только точные local drafts; create/update/comment/push не выполнялись. #121/#107 остаются open.
Пользователь разрешил local drafts как done boundary09; отсутствие remote issue IDs не блокирует10.
При external creation применять актуальные approvals; никакого automatic review rejection не было.

## Delivery update10

[Runtime contract](durable-ai-generation.md), [report10](prompt10-durable-generation-report.md):
реализован submission/worker/publication slice; final qualification фиксируется в отчёте.
Это не closure draft A: operational11 и отдельный budget draft B ещё впереди.
По явному запросу baseline09 pushed; прежняя строка «push не выполнялся» относится к09.
Новых remote issues/updates/closure в10 нет; local drafts сохраняют exact mapping.

## Delivery update11

Scope A recovery/operations реализован поверх10: completion provenance/V44, safe diagnostic
codes, bounded discovery/poller/capacity, correlation/counters и actual JVM crash/restart drills.
Фактическая qualification: [report11](prompt11-recovery-report.md), [runbook](ai-jobs-recovery-runbook.md).
Это не budget B/12, full Playbook P/13 или provider activation; #121/#120 остаются open.
Remote issue number для A не назначен; новый push/issue update/closure не выполнялись.

## Delivery update12

Draft B реализован и qualified локально; фактические результаты в [report12](prompt12-budget-report.md).
Shared sync/async ledger, exact key/request binding, all-scope caps, unknown holds, typed neutral
quote/usage и isolated synthetic policy описаны [ADR0030](adr/0030-generation-budget-ledger.md).
Live policy/provider evidence qualification остаётся #120 scope; task13 не начат.
Remote issue B не создан/не выдуман, GitHub acceptance boxes не изменялись без actual closure.

## Delivery update13

Draft P implemented and qualified locally; actual evidence in [report13](prompt13-playbook-report.md).
API, domain/provider v2, normalized V46 storage, explicit legacy upgrade, actual human-edit
attribution and async schema2 reuse qualified Jobs/budgets. No remote issue number invented;
#121/#120 remain open. Content capture/scheduling is a separate next scope14–16.
