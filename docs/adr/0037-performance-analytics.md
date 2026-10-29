# ADR0037 — descriptive performance analytics

Статус: техническое решение задания20, baseline27ca07c. Actual #104/#107 прочитаны16 октября.
Источник — human MANUAL_JSON/CSV observations ADR0036. Live/provider truth не заявляется.

## Формулы и сопоставимость

Версия `performance-v1`; COUNT totals и PERCENT rates, BigDecimal scale4/HALF_UP.
EngagementPerReach =100×sum(engagement)/sum(reach); SavesPerReach =100×sum(saves)/sum(reach);
ClicksPerReach =100×sum(profileToSiteClicks)/sum(reach);
SignupsPerClick =100×sum(signups)/sum(profileToSiteClicks). Для каждой формулы свой набор
complete pairs; не среднее individual percentages и не missing→zero. Последняя формула —
отношение co-reported counts, не доказанная funnel attribution. Rate может быть>100 и не
clamp: повторные interactions допустимы. Sum post reach не unique accounts across posts.

Missing pair исключается с coverage count;0/0 даёт known totals0/0 и null percentage.
N>0/D=0 — inconsistent pair, исключается с отдельной причиной; никаких Infinity/NaN/0 вместо
undefined rate. Sums проверены bounds/checked arithmetic; input corruption/overflow safe отказ.
Minimum sample —5distinct Posts с положительным denominator, sum reach>=100 или sum clicks>=20.
Это conservative UI guard, не statistical significance/causal inference. Sparse descriptive
percentage остаётся доступен с SPARSE, comparisonEligible=false. Missing/inconsistent/zero/
stale/unknown attribution тоже явно блокируют comparison, не скрывая known values.

Named timezone + fromDate inclusive/untilDate exclusive, максимум28local days,2000..2200.
Mode DAY: observations должны точно совпасть с `[local startOfDay,next startOfDay)`.
Mode PERIOD: один exact window от начала fromDate до начала untilDate, например7/28days;
weekly/four-week snapshots поддерживаются при точном совпадении, без суммирования daily rows.
Частичные/cumulative/overlapping или не совпадающие с выбранным bucket windows исключаются
и считаются incompatible.
Не распределяем недельный total по дням и не приписываем counters границам другого timezone.
При date-line skipped day zero-duration bucket не создаётся; explicit coverage caveat.
DST23/24/25hour days имеют разные duration seconds/comparisonKey. Каждый group содержит
точные UTC endpoints/local date/timezone/tzdb version; raw cross-day totals/rates не смешиваются.
Все dimensions дополнительно разделены platform/window: PLATFORM, PILLAR, FORMAT. TIME series
представлена DAY groups; PERIOD даёт один common interval, без inferred publication/schedule hour. Между platform методики
не признаны одинаковыми. ComparisonKey включает mode/timezone/tzdb/platform/duration и для pillars exact Strategy
revision; сравнение допустимо лишь при одинаковом key и sufficient coverage, не автоматически.

Latest correction выбирается до window filtering; затем по Post/exact window выбирается max
source observedAt. Повтор import/new key и повторный source snapshot не удваивают sample.
Correction, переместившая window, не оставляет старую revision в прежнем cohort.
Freshness: observedAt older than DB now−7days => STALE; age/source/ingestion times доступны.
Это явная product heuristic, не platform SLA. Исторические rates сохраняются как descriptive
historical values со STALE и comparisonEligible=false; resubmitting raw bytes не обновляет
source time автоматически. Provider adapters должны отдельно подтвердить свои методики.

## Исторический context без fabricated audit

ADR0036 не хранит content revision/pillar: current draft нельзя задним числом считать
источником format. Новая отдельная immutable human attribution связывает exact observation
с exact historical APPROVED Post revision/expectedVersion и pillar ID из exact captured
Post Strategy. Необязательный pillar у manual Post явно выбирает человек; у generated Post
outline pillar определён immutable slot, conflicting choice запрещён. Без attribution
format/pillar UNKNOWN; не backfill и не переносить annotation автоматически на correction.
Existing raw receipt/digest/observation неизменны. Attribution actor/time — новая human action,
не provider output/прошлая approval. Approved revision должна существовать к observedAt;
это human assertion, не proof platform publication или format всего measurement interval.
Immutable source Strategy ID/version/checksum, revision/content checksum и annotation audit
в DTO позволяют объяснить grouping. Later creative edit/new approval/Strategy lock не
переписывают исторический context. Current parent access/archive guards обязательны.
Attribution versions1..100/observation,100000 retained attribution rows/Brand. PUT требует
expectedAttributionVersion (initial0); identical current/prior-version replay возвращает same
row, conflicting stale409. Исправление context создаёт новую immutable attribution с previousId,
без изменения counters/source time. GET `?version=N` читает exact historical context. Analytics
выбирает latest attribution, source DTO содержит её ID/version. Никакого rewrite/delete.
Attribution нового obsolete observation запрещена; existing old attribution читается как history.

## Bounds / architecture / threat model

Scoped SQL scalar projection только normalized fields/provenance, без raw input bytes/entities.
Max1000 latest candidate observations,1001st =>409 без partial aggregate. Page0..1000,size1..50
на group results; query interval/window first bounded. Two concurrent queries/instance,
DB statement timeout3s/transaction5s. Parent Account→Workspace→Brand mutex обеспечивает coherent
snapshot против import/correction/attribution/archive/revocation. Нет external model/network I/O.
Source IDs нужны для explainability, а provider/private payloads не выдаются. No-store DTO.

Защищаем tenant business metrics/history. Caller — current OWNER; HTTP/usecase/DB membership
guards не доверяют frontend/JWT stale scope. BOLA/foreign observation/revision/pillar, mass
assignment, SQL injection, cardinality/DoS, divide-zero/overflow, duplicated evidence и wrong
time-window comparison предотвращаются scoped parameterized queries, bounded domain inputs,
fixed dimension allowlist, checked formulas, immutable SQL constraints и runtime privileges.
Нет новой dependency/AI provider/egress или public debug/admin route.

Alternatives: current content inference проще, но даёт ложную history; chosen explicit annotation.
Raw ORM traversal грузил бы private receipts и N+1 graphs; выбран scalar query. Unbounded SQL
aggregates/pagination totals могут скрыть truncation; cap fail-closed before aggregation.
Exact DAY/PERIOD windows ограничивают supported inputs осознанно: lifetime cumulative,
arbitrary intraday и automatic cross-window rollups unsupported в v1; coverage не скрывает
этот gap. Exposure age/publication time неизвестны, causal claims не делаются. Historical stale rates доступны,
но не объявляются ready для automated recommendations. Production load/rate policy отдельно.
