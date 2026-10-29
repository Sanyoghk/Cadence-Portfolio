# Performance analytics API

Contract/formulas/threat model: [ADR0037](adr/0037-performance-analytics.md).
Current OWNER/account/membership, ACTIVE Workspace/Brand, Bearer-only. No-store DTO;
никаких provider payloads/raw import bytes/model calls. Human manual/CSV data не verified truth.

## Query

```text
GET /api/v1/brands/{brandId}/performance?fromDate=2026-09-18&untilDate=2026-10-16&timezone=UTC&bucket=PERIOD&dimension=PILLAR&platform=INSTAGRAM&page=0&size=50
```

Dates local inclusive/exclusive, максимум28days; named installed timezone,2000-01-02..2199-12-31.
Default `bucket=DAY`, `dimension=PLATFORM`, page0,size50. Optional platform INSTAGRAM/TIKTOK;
без фильтра platforms всегда отдельные groups. Dimensions PLATFORM/PILLAR/FORMAT; time series
идёт через DAY buckets. PERIOD использует один common exact interval, например7/28days.
Никакого inferred publishing hour: schedule не подтверждает публикацию в платформе.

Observation должна точно совпасть с UTC boundaries bucket: local startOfDay→next startOfDay
для DAY, fromDate startOfDay→untilDate startOfDay для PERIOD. IANA startOfDay — первый valid
instant даты, при midnight fold earliest offset; skipped date учитывается отдельно. DST дни
могут иметь23/25h. Mode/timezone/tzdb/platform/duration входят в comparisonKey; pillar также
scoped к exact Strategy ID/revision. Сравнивать только equal keys + comparisonEligible=true,
и всё равно descriptive, не causal/statistically significant. Same named pillar разных
Strategy не объединяется. Source exposure age/publication time неизвестны.

Latest correction выбирается до interval filtering, затем latest observedAt per Post/exact
window. Слишком широкие/partial/rolling/cumulative windows — incompatibleWindows, не silently
пропорционально распределённые counters. Weekly/PERIOD inputs не суммируются из daily data:
например, daily reach одного account нельзя считать distinct period reach простым сложением.

Max1000 candidate observations;1001st =>409, не partial statistics. Page0..1000,size1..50
пагинирует **groups после полного bounded расчёта**, не первые50 inputs. Narrow dates/platform
при превышении cap.2concurrent analytics calls/instance, statement timeout3s/transaction5s;
capacity/DB query unavailable503. Invalid dates/enum/bounds400; foreign resource404; wrong
role/revoked membership403; archive/conflicting source state409. Нет public debug/query bypass.

## Formulas / states

Version `performance-v1`. Для каждой формулы independently complete pairs:

| Formula | Numerator | Denominator | Minimum denominator |
| --- | --- | --- | --- |
| ENGAGEMENT_PER_REACH |sum engagement|sum reach|100|
| SAVES_PER_REACH |sum saves|sum reach|100|
| CLICKS_PER_REACH |sum profileToSiteClicks|sum reach|100|
| SIGNUPS_PER_CLICK |sum signups|sum profileToSiteClicks|20|

Percent=100×N/D, BigDecimal4places HALF_UP; weighted ratio, не average per-Post percentages.
Minimum5distinct Posts с positive D, это conservative policy guard, не statistical evidence.
Source COUNT units, rate PERCENT. Values>100 допустимы, repeated interactions не probability.
Reach summed perPost не unique Brand audience; signups/clicks — co-reported ratio, не proof
causal funnel conversion.

Каждый rate возвращает percent, usable pair numeratorTotal/denominatorTotal, knownPairs,
usablePosts, missingPairs, zeroDenominatorPairs, inconsistentPairs, stalePairs, minimums,
comparisonEligible и issues. Missing/null pair исключается с MISSING; не подставляется0.
0/0 — known totals0, null percent и ZERO_DENOMINATOR. N>0/D=0 — INCONSISTENT_PAIR, исключается.
Если consistent pairs нет, totals тоже null. SPARSE rate остаётся descriptive при D>0;
STALE — source observedAt strictly older than generatedAt−604800seconds. Исторические rates
сохраняются со STALE, не становятся current-ready от нового ingestion/replay. UNKNOWN_ATTRIBUTION
для format/pillar без explicit context. Любая issue делает comparisonEligible=false.

Response содержит formulaVersion/generatedAt/source snapshotChecksum, timezone/rulesVersion/
dates/bucket/dimension, input/selection/dedup/incompatible/skipped counts, pagination и caveats.
Groups включают exact UTC window/local date/seconds/platform/pillar Strategy/format, sample
coverage, rates и bounded sources. Каждая contribution содержит exact observation/revision/
value checksum, source/ingestion times, raw receipt ID/checksum/origin/actor, captured Strategy
ID/version/approval/checksum и attribution ID/version/actor/time/content revision/checksum.
Raw bytes, captions, private storage keys, provider payloads не возвращаются.

## Explicit historical attribution

Без нового action старые measurements не получают current format/pillar автоматически.

```text
PUT /api/v1/metric-observations/{observationId}/attribution
Content-Type: application/json
```

```json
{
  "postRevisionId": "00000000-0000-0000-0000-000000000001",
  "expectedVersion": 1,
  "expectedAttributionVersion": 0,
  "pillarId": "education"
}
```

`expectedVersion` — exact approved Post revision lockVersion, не Strategy version.
Revision должна принадлежать observation Post и быть approved не позже observedAt. Можно
выбрать historical approved revision; latest draft её не заменяет. `pillarId` — ID внутри
captured Post Strategy; optional/null у manual Post = UNKNOWN. Generated outline задаёт pillar,
конфликт запрещён. Actor/time/ownership/content checksum/pillar origin/name — server-owned.
First expectedAttributionVersion0 создаёт version1. Исправление с expected current version
создаёт новую immutable context revision/previousId; measurements/raw receipt/source time
не меняются. Same current payload/current-or-prior expected version replay возвращает same
row; stale different payload409.100versions/observation и100000retained context rows/Brand.

`GET /api/v1/metric-observations/{id}/attribution` — latest context;
`GET .../attribution?version=1` — exact historical context. New attribution obsolete corrected
observation rejected; historical GET сохранён. Counter correction не наследует classification:
нужен explicit context нового observation. Нельзя обновлять/delete context history.

## Operational boundary

Не требует отдельного worker/provider. Existing metrics imports используют worker19;
performance query — bounded read-only calculation в короткой coherent parent-locked transaction.
SQL scalar reader не загружает raw bytea/JPA graphs; schema V53 index supports window lookup,
current correction/context lookup uses unique composite indexes. Production load at100000rows,
gateway/user quotas, DB capacity/lock latency и auth/backup/restore остаются отдельными gates.
Нет live analytics synchronization или рекомендаций21; #99/#120 не закрываются этой API.
