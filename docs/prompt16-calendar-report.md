# Задание16 — calendar scheduling qualification

## Что было сделано

Реализован bounded calendar и отдельная versioned история scheduling для текущего APPROVED
Post. Перенос времени не меняет approved content. Новая content revision делает прежнее
назначение неактуальным; история и original provenance остаются. Scheduling не вызывает publishing.

Исходный HEAD `c0e547399a797aec8212d544da27404b8581f354`, clean `mvp`; prerequisites14–15
проверены по code/ADR/reports и commit ancestry, не реализованы повторно. Актуальные read-only
GitHub #107/#115/#103 сверены16 октября2026. #115 включает assets17, #103 включает export18:
весь issue scope не объявляется завершённым. Local commit16, без push/remote edits/closure.

## Acceptance matrix16

| Требование | Code / contract / verification |
| --- | --- |
| four-week bounded calendar / pagination | CalendarTime.interval, PostScheduleRepository.calendar, CalendarController; four-week/half-open/page negatives |
| timezone / UTC / DST gap/fold | CalendarTime, original tuple+Instant+tzdbVersion, direct JDBC LocalDateTime; London both offsets, Lord Howe30min, SQL roundtrip |
| platform slots / duplicate policy | Brand/platform/UTC key; shared instant different platforms positive, same-platform collision/parallel replay negative |
| draft/approved / exact source | current APPROVED+audit+captured Strategy; DRAFT/stale/locked source negatives, new revision invalidates old assignment |
| immutable content vs scheduling metadata | PostScheduleEvent ledger; content version unchanged, exact revision/checksum, old events immutable |
| schedule/reschedule/history | atomic POST and paged GET routes; schedule→move→cancel/history and cap final cancellation |
| batch atomicity / drag / expectedVersion | same route1–84, dual tokens, no duplicate Posts, final-set swap; failed batch no partial events |
| archive/access/source/edit/approval races | PostgreSqlCalendarTest concurrent tests and lock-observed revocations |
| PostgreSQL/index/migration/runtime | V49 composite FKs, unique/partial indexes, guard/deferred seal, runtime denied mutation, exact inventory/legacy upgrade |
| API/docs/full gates/local commit | OpenAPI route+DTO assertions, API/ADR/report/plans; final full/startup/commit qualification recorded below |

## Как это работает

POST batch содержит Post/revision IDs, expectedRevisionVersion, expectedScheduleVersion и явную
time binding. Backend проверяет actual OWNER/membership, locks parents/source/Post, сверяет оба
tokens и approved source, проверяет valid local offset/DST и final slot set. Все события сохраняются
одной transaction. PostgreSQL повторяет ключевые invariants и проверяет collisions при commit.
GET calendar возвращает heads в bounded UTC interval с original и display local representation;
history возвращает immutable события и текущую eligibility. No platform/provider I/O.

## Архитектурное решение

[ADR0033](adr/0033-calendar-scheduling.md), [API](calendar-api.md). Append-only ledger выбран вместо
mutable scheduledAt в approved content и вместо двойной записи mutable head+audit. У него один
источник истины: последнее событие. Цена — indexed head joins и current eligibility checks.
Post content и schedule имеют независимые version tokens. Immutable revision UUID/checksum не
заменяются после approval/edit/regen. DRAFT scheduling denied по ADR0031; planning tray использует
существующий Posts API. Batch1–84,28 local-day query,366 UTC-day future horizon, minute precision,
1000 events/Post (последний только CANCEL) — явно документированные operational caps.

Source lifetime: scheduling использует исторический approved creative source по ADR0031; не
делает новый AI egress и не перечитывает underlying evidence. Новая AI generation сохраняет
более строгие current Identity/evidence guards ADR0032. READY не означает media/export/publishing
permission. External privacy/provider решения не изменены.

## Security Review

- **Threat:** foreign Post/Brand, forged role/membership. **Protection:** scoped IDs, OWNER-only
  controller/use-case и actual account/membership под Account→Workspace→Brand locks.
  **Verification:** HTTP foreign/reserved-role/anonymous negatives и PostgreSQL revocation races.
- **Threat:** forged actor/provider/approval или timezone system setting. **Protection:** strict
  nested DTO, server actor/DB time; named ZoneId+explicit offset; no system configuration writes.
  **Verification:** unknown field/offset/gap/range tests и independent SQL actor/time binding checks.
- **Threat:** stale drag/replay, duplicate platform slots, content/archive races. **Protection:**
  dual tokens/current revision/source, shared parent mutex, unique event version, deferred slot seal.
  **Verification:** concurrent replay/slots/edit/approval/archive/access/source tests и atomic swap.
- **Threat:** audit/history tampering, content substitution. **Protection:** exact revision composite
  FK, version+canonical checksum, insert-only trigger, runtime UPDATE/DELETE revoked.
  **Verification:** SQL bypass tests, actual runtime-role cancellation/history restrictions,
  SQL/Java Unicode/null/ordered-hashtag checksum equality, legacy migration preserves unknown audit.
- **Threat:** resource exhaustion/injection/data exposure. **Protection:** bounded batch/pages/window/
  horizon/history/indexes, bound repository values, DTOs with references rather than content, safe
  errors; no shell/fetch/tools or new dependencies. **Verification:** malformed/oversize/range tests,
  SQL/runtime/full gates; load/pilot qualification остаётся отдельной.

Authentication/CSRF/CORS/security chain не изменены: API использует explicit Bearer, не cookie
session. DB checks — дополнительная защита, а не замена per-request authorization. SQL actor должен
быть actual eligible OWNER; session principal проверяет application. Secret/prompt/content logging
не добавлялся; ошибки guard не возвращают SQL клиенту.

## Runtime Flow

```text
Bearer Request → SecurityFilterChain → CalendarController + strict DTO validation
  → PostCalendarUseCase (actual account/scope + Account→Workspace→Brand→Strategy→Post locks)
  → CalendarTime / PostScheduleEvent (dual versions, approval, time/checksum, final slots)
  → PostScheduleRepository → PostgreSQL V49 guard + deferred slot seal → commit
  → ScheduleDetails / PageResponse (original UTC/local + display local + current eligibility)
```

## Тесты

Все Maven commands используют `.\mvnw.cmd -o --batch-mode --no-transfer-progress`.

| Команда / selector | Actual result |
| --- | --- |
| `spotless:apply test-compile` | BUILD SUCCESS |
| `spotless:apply '-Dtest=CalendarTimeTest,CalendarApiIntegrationTest,PostgreSqlCalendarTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest,OpenApiContractTest' test` | initial87:5failures/11errors; fixture/JDBC failure found |
| `spotless:apply '-Dtest=CalendarApiIntegrationTest,PostgreSqlCalendarTest,OpenApiContractTest' test` | diagnostic71:1failure/11errors; H2 context fixed, PostgreSQL guard still refusing shifted localTime |
| `'-Dtest=PostgreSqlCalendarTest#javaSqlChecksumContractIsExact+scheduleRescheduleCancelHistoryAndRoundTrip' test` | diagnostic2:1failure/1error; localized timezone binding, fixed detached checksum fixture |
| `spotless:apply '-Dtest=PostgreSqlCalendarTest,CalendarApiIntegrationTest,CalendarTimeTest,OpenApiContractTest' test` |79/79 PASS |
| `spotless:apply '-Dtest=CalendarTimeTest,CalendarApiIntegrationTest,PostgreSqlCalendarTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest,PostgreSqlPostMigrationTest,OpenApiContractTest' test` |95/95 PASS |
| `spotless:apply '-Dtest=CalendarApiIntegrationTest#differentPlatformsMayShareOneBrandInstant,PostgreSqlCalendarTest#differentPlatformsMayShareOneBrandInstant+javaSqlChecksumContractIsExact,PostgreSqlPostMigrationTest' test` |4/4 PASS |
| `'-Dspring.test.context.cache.maxSize=8' spotless:check clean verify` | BUILD SUCCESS,151 suites/1638 tests,0 failures/errors,3 Windows skips |
| `.\scripts\run_linux_storage_tests.ps1` |35/35 PASS, no skips |
| renderer build/run below |29/29 PASS |

```powershell
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt16-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
docker build --tag cadence-prompt16-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt16-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

Scripts Python10/10 PASS. Final full gate finished2026-10-16T08:17:44+01:00,17:54min:
1635 executed tests passed,3 Windows-only filesystem skips independently covered Linux35/35.
Calendar PostgreSQL45/45, H2calendar31/31, CalendarTime8/8, previous PostPG29/29, ContentPG25/25,
real child-JVM crash/restart8/8. Spotless PASS, JaCoCo94.60% lines/71.89% branches; all configured
coverage checks met. Mandatory PostgreSQL/MinIO/ClamAV/renderer/full-pipeline reports PASS with
no required skips. Test-only recovery launcher absent from packaged JAR.

```powershell
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
python -B var/prompt16-collect.py
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt16-verification/bom-baseline.cdx.json --all-packages --format=json --output-file=var/prompt16-verification/osv-results.json
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt16-verification/osv-results.json
.\var\prompt16-startup.ps1
git diff --check
```

Current fresh SBOM194 components matched to newly executed OSV scan of unchanged baseline graph:
0 findings. Scanner2.5.1 cached Windows binary SHA256 matches previously verified artifact
`25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.
Packaged local/test-isolated fake adapters: health200;12 protected anonymous operations401
(including all three calendar routes); discovery401 with fixed Bearer; own process stopped and
port18100 released. PostgreSQL migration/DI/runtime startup also exercised by real Spring suites.
No live provider credentials or deployment used.

605 source/build hashes unchanged through full gate, SBOM and startup; logs/reports/manifests
saved under ignored `var/prompt16-*`. JAR SHA256
`17fcf42189c670f12f1c25950eb3e79232328d2a1beb3b0874f9f48a0a277f33`.
Warnings: existing deprecated Jackson2 compatibility/Testcontainers APIs, Hikari closed-container
context cleanup warnings and CycloneDX metadata-keyword warnings. They did not fail checks; no
required integration suite skipped, no security finding suppressed, no assertion weakened.

Найдена и исправлена JDBC regression нового LocalDateTime field: общий UTC JDBC setting сдвигал
local timestamp при JVM timezone London; PostgreSQL time binding guard отклонял input. Поле
использует direct `SqlTypes.LOCAL_DATE_TIME`, как существующий cadence LOCAL_TIME; guard сохранён.
Первые новые HTTP fixtures также исправлены: MockMvc очищает context, anonymous test обязан явно
очистить OWNER context. Assertions/auth/SQL protections не ослаблены. Targeted79/79 затем95/95
прошли; последние platform/Unicode/legacy scenarios4/4 затем полный gate1638 подтвердили final source.

## Security Tests / Edge Cases

UTC/local roundtrip; London DST gap/fold с обоими offsets; Lord Howe30-minute fold; invalid named
zones/offsets/seconds;28 local-day fall-back interval; half-open boundaries; independent pagination/
range errors; four-week batch; same-platform conflicts/different-platform shared instants; atomic
swap/rollback; stale/replayed content/schedule tokens; DRAFT/foreign/membership/role denials;
new DRAFT invalidation/new approval does not revive; source unavailable cancellation/history;
SQL checksum/actor/draft/timezone/history bypass; cap reserved cancellation; real PostgreSQL
concurrent replay/collision/edit/approval and lock-observed archive/source/account/membership races.

## Непроверенные аспекты / Production considerations

Live Clerk, realistic load/latency and deployed least-privilege/IAM/TLS/backups не квалифицированы
этим local slice. JVM/PostgreSQL tzdb должны обновляться согласованно; mismatched new binding
fails closed. Stored UTC не перемещается автоматически при обновлении rules; original tuple/version
сохранён, display uses installed rules. Platform timezone/publishing limits не заявляются.
Нет выбранного live AI/media provider, assets/moderation/export/publishing не выдуманы.
Consumer17/18 обязан выполнить собственные media/export lifetime checks; live adapters disabled.
Перед production нужны operational monitoring/rate/load/backup/deployment gates общего плана.
Index review: tenant-leading partial `(brand_id,scheduled_at,id)` supports bounded UTC windows/slot
lookups; unique `(post_id,event_number)` supports head/history by backward B-tree scan. Composite FK
binds denormalized Brand; index existence checked on PostgreSQL. Realistic data-volume EXPLAIN/load
measurements are not claimed: append-only history can increase window/count query work even when
returned page is bounded. Benchmark and tune under production-sized per-Brand histories before pilot.

## Что мне важно понять

Approval подтверждает конкретный текст, scheduling — конкретную revision и время. У них разные
tokens: иначе drag мог бы перенести уже другой текст или затереть параллельный перенос. Local
datetime не определяет единственный Instant в DST fold: explicit offset делает решение однозначным.
Append-only история сохраняет intent автора; read-time eligibility показывает, можно ли сейчас
использовать именно это назначение, сохраняя прежние факты.

## Что мне стоит изучить

1. **Optimistic concurrency:** expectedVersion обнаруживает устаревший запрос; здесь отдельно для
   content и schedule.
2. **Append-only ledger:** события добавляются, прежние факты не переписываются; последний event
   определяет head.
3. **DST gap/fold:** местное время может отсутствовать или повторяться; ZoneRules + explicit offset
   предотвращают скрытый сдвиг.
4. **Deferred constraint:** SQL проверяет итог transaction после всех batch changes, разрешая swap.
5. **Defense in depth:** DTO/application/domain/SQL/runtime privileges защищают разные границы.

## Изменённые файлы

| Файл | Назначение |
| --- | --- |
| `DEVELOPMENT_PLAN.md` | Актуальная точка продолжения, qualification16 и remaining17–18. |
| `DEVELOPMENT_PLAN_RU.md` | Синхронное обновление русского плана. |
| `docs/adr/0033-calendar-scheduling.md` | Time/DST/slots/eligibility/bulk/locking/checksum decisions, alternatives и threat model. |
| `docs/calendar-api.md` | HTTP contract, request examples, tokens, errors, calendar/history semantics. |
| `docs/prompt16-calendar-report.md` | Acceptance, security review, actual commands/results, file manifest и remaining scopes. |
| `docs/issue-sync.md` | Local scope16 и remaining #115/#103 acceptance; no remote writes. |
| `docs/mvp-prompt-progress.md` | Qualification ledger16 и следующая задача17. |
| `docs/astra-medium-mvp-prompts.md` | Execution note16: не повторять14–16, не закрывать broad issues. |
| `docker/postgres/runtime-permissions.sql` | Runtime UPDATE/DELETE расписания запрещены; INSERT/SELECT сохранены. |
| `scripts/verify_postgres_report.py` | PostgreSqlCalendarTest обязателен без skips/failures. |
| `scripts/test_verify_postgres_report.py` | Report gate rejects missing/skipped/failed calendar suite. |
| `src/main/java/com/cadence/application/repositories/PostScheduleRepository.java` | Indexed head/window/history/slot queries. |
| `src/main/java/com/cadence/application/results/ScheduleDetails.java` | Explicit metadata DTO: original/display time, exact revision/checksum/audit/eligibility. |
| `src/main/java/com/cadence/application/usecases/PostCalendarUseCase.java` | Scoped transaction, actual OWNER, ordered locks, dual tokens, final batch slot validation. |
| `src/main/java/com/cadence/domain/CalendarTime.java` | Named ZoneId, valid offset, gap/fold, minute/horizon/window bounds. |
| `src/main/java/com/cadence/domain/PostScheduleEvent.java` | Append-only transitions/cap/checksum; direct LocalDateTime JDBC mapping. |
| `src/main/java/com/cadence/presentation/CalendarController.java` | Three OWNER-only calendar/schedule/history routes. |
| `src/main/java/com/cadence/presentation/dto/CalendarScheduleRequest.java` | Strict nested request allowlist and validation. |
| `src/main/resources/db/migration/V49__post_calendar_schedule.sql` | Ledger schema/FKs/indexes, independent SQL guards, deferred slot seal; no legacy backfill. |
| `src/main/resources/openapi/cadence.json` | Reviewed three operations and dedicated request/result/page schemas. |
| `src/test/java/com/cadence/application/CalendarApiIntegrationTest.java` | H2 shared real HTTP calendar contract. |
| `src/test/java/com/cadence/domain/CalendarTimeTest.java` | Deterministic DST/non-hour gap/fold, offsets, horizon and window boundaries. |
| `src/test/java/com/cadence/persistence/PostgreSqlCalendarTest.java` | PostgreSQL API/SQL/runtime/checksum/cap/claims/revocation/edit/approval races. |
| `src/test/java/com/cadence/support/CalendarApiContract.java` | Shared positive four-week/platform/time lifecycle and forbidden HTTP scenarios. |
| `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java` | Exact migration inventory1–49. |
| `src/test/java/com/cadence/persistence/PostgreSqlPostMigrationTest.java` | Legacy content/audit preserved; no fabricated schedule events after upgrade. |
| `src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java` | Exact timestamp inventory52 and existing UTC migration semantics. |
| `src/test/java/com/cadence/presentation/OpenApiContractTest.java` | Calendar DTO fields/enums and exact controller/OpenAPI route mapping. |

## Факты / инженерные выводы / assumptions

Факты: реализованные contracts/code и конкретные выполненные runs выше. Full qualification подтверждена final Maven/Python/Linux/renderer/report/security/startup gates выше. Инженерный вывод: ledger + independent tokens сохраняет approval
history и предотвращает stale overwrite без mutation content. Assumptions: production infrastructure,
tzdb operational updates, media/export policies и live-provider activation требуют собственных gates.


## Git / issue boundary

Finished slice сохраняется local commit `feat(content): qualify versioned calendar scheduling`.
Branch mvp; commit parent c0e547399a797aec8212d544da27404b8581f354. No push/new remote CI/issue
closure/merge/deployment. Task16 delivered locally;17 не начат. #115 assets17 и #103 durable CSV18
остаются required; завершение calendar не закрывает broader acceptance. Production adapters disabled.
Факты: exact executed tests/inspection/hashes/results выше. Инженерные выводы: ledger/dual tokens/
locks/SQL constraints последовательно сохраняют history и актуальность назначения в tested races.
Assumptions/непроверенное: realistic load/tzdb operations/live Clerk/media/export/providers/deployment
не выдаются за выполненную production qualification.
