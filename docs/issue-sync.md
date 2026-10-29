# GitHub issue alignment

## Read-only reconciliation2026-10-20 — UI rework23–24

Actual `mvp` baseline `e35d4e1`, issues [#3](https://github.com/Sanyoghk/Cadence/issues/3)
и [#107](https://github.com/Sanyoghk/Cadence/issues/107) OPEN, last remote update2026-10-18.
Их старые pending visual23/24/backend28 статусы не заменяют subsequent local code/reports.
Local backend28 + Cream Orbit rework23–24 qualified; [actual frontend evidence](ui-rework23-24-report.md).
Remote payload не менялся.25–27/whole28/release и live/provider/device qualification OPEN.
No push/new CI/issue closure/merge/deployment; этот раздел фиксирует local→remote delta.

<!-- backend28-execution:start -->
### Backend28 — QUALIFIED LOCAL (2026-10-19)

09–22 не повторяются. [Backend28 report](prompt28-backend-report.md) фиксирует actual
findings/fixes, security/audit/readiness/limits и fresh qualification. Backend pass не завершает
whole28: frontend/cross-stack28 **PENDING**, после23–27 и qualification изменённых adapters.
Следующий backend milestone по ADR0027 — отдельное решение AI API/model/privacy/cost и adapters;
этот шаг provider не выбирает. Принятый UI design и route rework23–24→25–27 сохраняются.
Issues #87/#83/#116/#118/#121 остаются OPEN до соответствующего whole-scope acceptance.
Local only: no push/new remote CI/issue closure/merge/deployment. Production readiness не заявляется.
Actual GitHub read2026-10-19: #87/#83/#116/#118/#107 OPEN; user admin. Rulesets API403
private-repository plan restriction, branch protection403 integration permission. No enforcement,
visibility/subscription/settings changes or remote status edits claimed.
<!-- backend28-execution:end -->


### UI rework planning / actual remote read —2026-10-19

Actual local `ce76192`, clean `mvp`; пользователь запросил существенную переработку UI.
[UI rework plan](ui-rework-plan.md) и оба плана обновлены локально: design review →backend28
→rework23–24 →25–27. Концепция дизайна pending;09–24 не реализуются заново.
Read-only GitHub #3 `[Task 7.2] Build the authenticated creator web application` и #107
`Cadence MVP master checklist — creator-first implementation plan` перечитаны, оба OPEN.
Remote bodies ещё отражают snapshot18 октября (visual follow-up23/24 pending) и не содержат
новый local rework plan. Local24 qualification и local Clerk setup не выдаются за remote acceptance.
Новые remote issues/edits/comments/closure, push/CI/merge/deploy не выполнялись.

### Задание24 — QUALIFIED LOCAL repository scope (2026-10-18)

Baseline clean5dbd15f, supported01–06 +23/visual follow-up23; actual #3/#107 перечитаны.
Evidence/Identity UI, exact consent/checksum/tokens, current/history/withdrawal, durable Jobs,
DRAFT/edit/approval/lineage/citations, inherited demo attribution; scoped history/CORS и
bounded first-auth bootstrap recovery. [Report24](prompt24-identity-report.md),
[47-file manifest](prompt24-changed-files.md). Frontend58unit/56Chromium+axe и все gates PASS.
Final full2286/0failures/errors,4Windows skips covered Linux37;Python10/renderer29/reports/
coverage/startup/actual HTTP PASS;fresh Maven194/OSV0,npm263/0;745 stable backend/build hashes.
Live Clerk/OAuth/model adapters blocked externally, visual sources REQUIRES_PROVIDER.
Следующий frontend25,25–27 не начаты; #3/#121 open. No push/remote writes/new CI/closure/merge/deploy.
Remote #3/#107 по read-back сохраняют snapshot предыдущей синхронизации; local24 не выдаётся
за remote acceptance. Remote mvp `a283382c8191bfa4020d0fb3c141493cabb9f821` unchanged.
## #3 visual follow-up23 — QUALIFIED LOCAL, 2026-10-18

Actual #3/#107 перечитаны на clean `14805a5`; reference fingerprint совпал.
Shell/onboarding перенос квалифицирован локально; [report](prompt23-visual-report.md).
35unit/33Chromium+axe, actual lint/format/type/build/export/peer/audit gates PASS.
Live Clerk blocked; 24–27 pending. Новых remote edits/push/CI/closure этим implementation
slice не выполнялось; нижняя remote synchronization — исторический snapshot до follow-up.

## Актуальная remote синхронизация — 2026-10-18

По явному запросу пользователя обновлены11 issues: #3/#107/#121/#120/#115/#103/#98/#99/
#104/#100/#105. [Полный отчёт, acceptance mapping и проверки](issue-tidy-2026-10-18.md).
Повторное чтение GitHub: body exact match11/11; state open, titles, assignees и milestones
сохранены. С девяти уже начатых задач удалён только устаревший `status: planned`;
#120 остаётся planned, #121 сохраняет прежние labels. Другие labels не менялись.

#3/#107 теперь отражают выбранный prototype, qualified foundation23 и следующий pending
visual follow-up23→24–27; backend28/AI route остаётся независимым. #105 signed URL заменён
утверждённым ADR0039 private authenticated HTML/JSON delivery; #99 live sync и #121 visual
остаток видимы. A/B/P остаются actual local drafts, без выдуманных remote issue numbers.

Remote `mvp` перепроверен: `a283382c8191bfa4020d0fb3c141493cabb9f821`, local baseline
`db1117568fe0d3b2faff3a9398446c5f2e9f9444`; local14–23 не pushed. Исторические remote runs
38846267446/38846270484 перечитаны: completed/success, exact same baseline SHA; новых runs
этим шагом не создано. Ни один issue не закрыт, push/merge/deploy не выполнялись.
Ниже — хронологическая история; прежнее «no remote update» относится к тому моменту.

## Задание22 / #105 — QUALIFIED LOCAL, 2026-10-17

Actual #105/#107 reread on clean5f3e506. User-approved format/retention/private delivery
ADR0039 preserved; old signed-link wording explicitly refined, no public bearer capability.
[report22](prompt22-reports-report.md), [API](creator-reports-api.md),
[ADR0039](adr/0039-creator-performance-reports.md). Report Job/JSON/HTML/V55 implemented;
Full qualification PASS. Full2250/0 failures/errors,4 Windows filesystem skips covered Linux37/37; report H266/66,PG76/76,migration1/1;Python10/10,renderer29/29,reports/Spotless/JaCoCo/startup PASS;freshSBOM194/OSV0;743 stable source/build/config hashes.
Recommendations subset qualified21; #105 full local matrix in report22.
No external writes/push/CI/closure; #107 whole/backend/provider/release gates not assumed complete.

## Задание21 / #100 — QUALIFIED LOCAL, 2026-10-17

Actual #100/#105/#107 прочитаны на clean dabc367. #100: correct Brand, traceable source/time,
idempotent/versioned runs, provider failures preserve history, unit/persistence verification.
[report21](prompt21-recommendations-report.md), [ADR0038](adr/0038-evidence-linked-recommendations.md),
[API](recommendations-api.md). Qualified bounded HEURISTIC/captured metrics/apply→DRAFT.
Full2104/0 failures/errors,4 Windows filesystem skips covered Linux37/37; PG61/61,migration1/1;Python10/10,renderer29/29,reports/Spotless/JaCoCo/startup PASS;freshSBOM194/OSV0;722 stable source/build hashes.
#105 recommendation API subset included; durable report/download scope remains task22.
No external writes/push/new CI/closure;22+ не начаты; live AI #120/#121 remain separate/open.

## Задание20 / #104 — QUALIFIED LOCAL, 2026-10-17

Actual #104/#107 прочитаны на clean27ca07c. #104 AC: scoped normalized analytics, bounded
time/page, formulas/sample caveats, no provider payloads, UTC, missing/stale, auth/empty tests.
Implementation/matrix: [report20](prompt20-performance-report.md), [ADR0037](adr/0037-performance-analytics.md),
[API](performance-api.md). Provider sync #99/AI adapters #120 — отдельный scope, не prerequisite
manual-supported performance.
Full1984/0 failures/errors,4 Windows skips covered Linux37/37; PG39/39,migration1/1,JDBC timeout3/3;Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;694 stable source/build hashes.
#104 local AC fulfilled; No remote edits/push/CI/closure;21+ не начаты.

## Задание19 / #99 — QUALIFIED LOCAL, 2026-10-16

Actual #99/#107 сверены на clean9d3a60a. Manual/CSV scope отделён от live provider sync:
[ADR0036](adr/0036-manual-csv-metric-import.md), [API/runbook](metrics-import-api.md),
[report19](prompt19-metrics-import-report.md). Immutable raw human receipt, normalized nullable
interval counters, stable Post mapping, dedup/correction history, fenced METRICS_IMPORT Jobs.
Full1897/0failures/errors,4Windows skips covered Linux37/37;MetricsPG45/45,H2API32/32,
parser6/domain3/migration1;Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;
freshSBOM194/OSV0;671stable source hashes/560compiled resources.
Actual #99 остаётся OPEN: live AnalyticsSyncService/SyncAnalyticsUseCase, qualified external
mapping/cursors и partial provider failures ещё не delivered.20+ не начаты.
External issue edits/push/CI/closure/merge/deployment не выполнялись.

## Задание18 / #103 — QUALIFIED LOCAL, 2026-10-16

Actual #103/#107 прочитаны16 октября на clean abaeeb1. #103 explicitly requires approved CSV
through durable Job; #107 historical mapping to #98 не расширяет task18 до live media provider.
[ADR0035](adr/0035-durable-csv-export.md), [API/runbook](csv-export-api.md), [report18](prompt18-csv-export-report.md).
Full1810/0failures/errors,4Windows skips covered Linux37/37;CSV PG43/43,HTTP24/24,domain11/11,migration1/1;AI child-JVM8/8. Python10/10,renderer29/29,mandatory reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;651 stable source/build hashes.
No external issue edits/push/CI/closure/merge/deployment.19 не начато.

## Задание17 / #98 — QUALIFIED LOCAL, 2026-10-16

Actual #98/#107 прочитаны на clean f029531;14–15 delivered не повторялись,16 сохранено.
Private demo media lifecycle/refs contract: [ADR0034](adr/0034-private-demo-media.md),
[API](media-assets-api.md), [report17](prompt17-media-report.md). Final full1731/0failures/errors,
4Windows skips covered Linux37/37;MediaPG49/49;Python10/10,renderer29/29,reports/coverage/startup PASS;
freshSBOM194/OSV0;632 stable hashes;finished local slice.
#98 core generation criteria покрыты15; current request добавляет demo media slice.
Live producer/general decoder/privacy/cost policy отдельно, не выбирались и не объявлены delivered.
История17: no push/external issue edits/closure/merge/deployment;18 тогда не было начато.


## Задание16 / #115 / #103 — QUALIFIED LOCAL, 2026-10-16

Актуальные #107/#115/#103 прочитаны перед implementation на clean c0e5473.
14–15 delivered prerequisites не повторялись. Calendar/schedule/DST/atomic history contract
зафиксирован в [ADR0033](adr/0033-calendar-scheduling.md), [API](calendar-api.md),
[report16](prompt16-calendar-report.md). Full1638/0failures/errors,3Windows skips covered Linux35/35;CalendarPG45/45;Python10/10,
renderer29/29,reports/Spotless/coverage/startup PASS;SBOM194/OSV0;605 stable hashes.17 не начат.
#115 остаётся open для assets17 и whole-issue acceptance; #103 — для durable CSV18 и whole-issue gates.
No push/external issue edits/closure/merge/deployment; production adapters disabled.


## Задание14 / #115 / #103 — local scope, 2026-10-16

Actual GitHub descriptions #107/#115/#103 прочитаны перед implementation. Task14 покрывает Post
identity/revisions, manual platform-specific content, exact approved Strategy source, human
edit/approval/history, API и V47/runtime guards. [Contract](adr/0031-post-revision-lifecycle.md),
[API](post-revisions-api.md), [qualification report](prompt14-post-revisions-report.md).
Generation/regeneration15, scheduling/timezone/DST16, media17 и durable CSV18 остаются required
для broader issue acceptance. Ни #115, ни #103 не закрываются по одному lifecycle slice.
Внешние issue descriptions/checklists в этом задании не переписывались; текущий статус local
qualification определяется report14. Push14 не выполнялся; merge/deployment не выполнялись.

Prerequisite baseline13 уже pushed по explicit user request: `a283382c8191bfa4020d0fb3c141493cabb9f821`.
[Push CI38846267446](https://github.com/Sanyoghk/Cadence/actions/runs/38846267446) и
[PR CI38846270484](https://github.com/Sanyoghk/Cadence/actions/runs/38846270484): both verify/security SUCCESS.
Это supersedes historical no-push statements10–13 для этого exact range;14 остаётся local.

## #121 reviewed PDF text analysis — 2026-10-14

Local ADR0019/V37 slice completed: mixed bounded generation/signature publication,
physical checks before egress/publication, current whole-batch guards и exact immutable citations.
Final full1053 tests,0 failures/errors,3 Windows skips;1050 executed pass;Linux35/35 covers skips.
H2PDF47/PGPDF56,V36 migration1,capacity1,OpenAPI3;Python10/10,renderer29/29,mandatory gates PASS.
JaCoCo94.82%,Spotless/JAR/startup health200 и anonymous generation/PDF operations401.
[Report](issue-121-pdf-analysis-slice.md). GitHub #121/#107 reread2026-10-14: both open,
previous approved admission block present. Пользователь отдельно подтвердил
«Разрешаю оба analysis updates» для нового exact payload и обоих destinations.
Analysis block добавлен в оба descriptions; readback подтвердил exact old body + block,
title/state/labels unchanged, оба issues open. External sync завершён.
Feature SHA: `d43af8ca01c81097b52e5c041bc0560f5b080f08`;
[exact draft](issue-121-pdf-analysis-sync-draft.md) опубликован без изменения block.
Docs-only sync follow-up проверяет diff/links; product source/tests unchanged, новый full run не заявляется.
Push/exact-SHA CI after whole #121; merge/deployment separately. Next: PDF Identity copy/approval
fences,other non-TEXT eligibility,APPROVED Identity→Strategy. Production provider disabled.

## #121 explicit PDF admission history — 2026-10-14

ADR0018/V36 completed local consent/snapshot/withdrawal slice; exact selectors/expiry/policy,
original artifact/scan provenance, immutable audit, current physical/access/source/withdrawal
checks. Model consumers остаются TEXT-only; no READY transition или new provider.
Full1024 tests,0 failures/errors,3 Windows skips;1021 executed pass;Linux35/35 covers skips.
H2PDF34/PGPDF42/domain2/V35 upgrade1/OpenAPI3 PASS;Python10/10,renderer29/29,mandatory reports PASS,
JaCoCo94.78%,Spotless/JAR/packaged health200/anonymous new routes401.
[Report](issue-121-pdf-admission-slice.md). Whole #121/#107 open;next PDF consumers/approval,
other non-TEXT policy,APPROVED Identity→Strategy. Push/exact-SHA CI после whole issue.
Новый exact payload не покрывался прежним PDF preview approval; пользователь отдельно
ответил «Разрешаю оба admission updates» для точного admission draft и обоих destinations.
Feature commit: `730a491c7cb0dcb03cff4cf9efbf42af9166a2ce`.
[Точный progress draft](issue-121-pdf-admission-sync-draft.md) добавлен в descriptions #121/#107.
Readback подтвердил exact old body + block и сохранение title/state/labels; оба issues open.
Docs-only follow-up проверяет diff/links, не повторяет product tests.
Merge/deployment не выполнялись; commercial/live qualification отдельно.

## #121 private PDF review preview — 2026-10-13

Local ADR0017 slice завершён: actual OWNER/member PDF_TEXT preview, exact original/latest CLEAN
provenance, REFERENCED ledger и physical bytes checks, fresh finalize и two concurrent slots.
No AI admission; TEXT consumers unchanged. [Report](issue-121-pdf-review-slice.md).
Full982 tests,0 failures/errors,3 Windows skips;979 executed pass;Linux35/35 covers skips.
Domain7/H2PDF18/PGPDF19/OpenAPI3 PASS,Python10/10,renderer29/29,mandatory reports PASS,
JaCoCo94.67%,Spotless/JAR/startup health200/anonymous PDF GET401.
Whole #121/#107 остаются open. Next: persisted PDF admission/consumer fences, other non-TEXT
policy, APPROVED Identity→Strategy. Push/exact-SHA remote CI после whole issue; merge/deploy отдельно.
Предыдущее exact approval покрывало только Identity payload. Automatic approval review ранее
потребовала отдельное подтверждение новых payload/destinations. Пользователь отдельно ответил
«Разрешаю оба PDF updates» для точного PDF draft и обоих destinations.
Feature commit: `53c77de4e95d48280e1d1a63dc2714aea5161f5b`.
[Точный PDF progress draft](issue-121-pdf-review-sync-draft.md) добавлен в descriptions #121/#107.
Readback: exact old body + approved block; title/state/labels сохранены, оба issues open.
Новый docs-only sync follow-up не меняет product source/tests; проверены diff и ссылки.
Предыдущая full qualification не выдаётся за новый test run; push/merge/deployment не выполнялись.

## #121 TEXT Identity provenance — 2026-10-13

Законченный ADR0016/V35 slice: explicit evidence copy/read, immutable copier и original
quotations, actual access/source/withdrawal checks на copy и direct/inherited approval.
Legacy copy остаётся declared-only. [Report](issue-121-identity-evidence-slice.md).
Final full938 tests,0 failures/errors,3 Windows skips закрыты Linux35/35;935 executed pass.
H2API17/17,PG24/24,migration1/1,domain2/2,Python10/10,renderer29/29,mandatory gates PASS;
JaCoCo94.55%,Spotless/JAR PASS. Local commit `feat: preserve evidence provenance in identity drafts (#121)`.
Packaged local health200,anonymous new POST/GET401; hosted/live qualification не выполнялась.
#121/#107 остаются open; approved progress block синхронизирован с actual feature SHA. Push/exact-source-SHA remote CI после
whole issue. Next: non-TEXT eligibility → APPROVED Identity→Strategy. Live adapters#120,
AI Jobs/budgets/full Playbook отдельно; commercial deployment не квалифицирован.

Первоначально automatic approval review отклонила external запись без отдельного подтверждения
конкретного payload/destinations. Пользователь явно ответил «Разрешаю оба updates» для
[точного progress block](issue-121-identity-evidence-sync-draft.md) и GitHub #121/#107.
Оба descriptions обновлены; readback подтвердил точное сохранение прежнего body + approved block,
неизменные title/state и open status. Blocker снят. Push/merge/deployment не выполнялись.
Verified feature commit: `ea2117f40e3d90b7423dfa5f6ecb08d16ab66fcb`.


## #121 bounded TEXT analysis/citations — 2026-10-12

Законченный slice ADR0015/V34 сохранён local commit `feat: analyze reviewed text evidence (#121)`.
Новый explicit action использует bounded server-selected admissions/exact citations;
DB/source/actor history immutable, OWNER/membership/withdrawal rechecked при publication.
Half-surrogate quote regression проверен. [Отчёт](issue-121-evidence-analysis-slice.md).
Final full894,0 failures/errors,3 Windows skips закрыты Linux35/35;891 executed pass.
H2API15/15,PG21/21,port5/5,fake1/1,Python10/10,renderer29/29,mandatory gates PASS.
JaCoCo94.52%,packaged health200/new anonymous POST401; legacy Identity copy deny guard проверен.
#121/#107 остаются open, синхронизируются с actual local SHA. Push/exact-source-SHA CI после
whole issue. Next: evidence receipt/citations → Identity provenance, non-TEXT eligibility,
approved Identity→Strategy. AI Jobs/budgets/Playbook/real #120 отдельно.


## #121 versioned TEXT re-review — 2026-10-12

Обновлённый plan d9c4535 проверен; реализован его следующий ограниченный slice ADR0014/V33.
New context explicit review,exact tuple replay409,sourceVersion withdrawal во всех contexts,
1000 reviews/evidence; original history сохранена. Regrant withdrawn sourceVersion не утверждён.
Final full852,0 failures/errors,3Windows skips закрыты Linux35/35;849 executed pass.
H2HTTP14/14,PG27/27,migrations4/4,Python10/10,renderer29/29,mandatory gates PASS;
JaCoCo94.41%,packaged health200/anonymous admission routes401.
[Отчёт](issue-121-re-review-slice.md). #121/#107 синхронизируются с local commit SHA; остаются open.
Next: bounded generation inputs/citations,non-TEXT eligibility,approved Identity→Strategy.
AI Jobs/budgets/full Playbook/real #120 отдельно. Push/exact-SHA CI после whole issue.

## #121 admission withdrawal/current snapshot — 2026-10-12

Local finished slice: append-only withdrawal V32, current bounded single TEXT snapshot,
actual OWNER/membership, source/parent/checksum/policy/withdrawal checks и immutable tenant parents.
Full840,0 failures/errors,3Windows skips закрыты Linux35/35;837 executed pass. H2HTTP11,PG20,
migrations2,domain1;Python10/10,renderer29/29,mandatory report gates PASS. JaCoCo94.41%,packaged
health200 и anonymous new operations401. [Отчёт](issue-121-withdrawal-slice.md).
GitHub #121/#107 остаются open; commit SHA/qualification добавляются после local commit.
Next: versioned re-review, bounded generation selection/citations/publication fencing,
URL/PDF/image eligibility и approved Identity→Strategy. No real AI/AI Jobs/budgets/full Playbook
completion; push/exact-source-SHA CI после whole issue. No merge/deployment.

## #121 TEXT source admission — 2026-10-11

Local finished slice: OWNER preview/confirmation точного TEXT с source/parent tokens,
immutable content/checksum/version/reviewer snapshot, V31/runtime grants и mandatory PG gates.
Не AI call, READY transition или invented citations. Full827,0 failures/errors,3Windows skips
закрыты Linux35/35; scripts10/10,renderer29/29,required provider reports PASS. Packaged health200,
anonymous GET+POST401. H2 isolation regression исправляет cross-context create-drop interference.
[Отчёт](issue-121-text-admission-slice.md); [API](evidence-admission-api.md); [ADR0013](adr/0013-evidence-admission.md).
Task2.3/#121 остаются IN PROGRESS: URL/PDF/image eligibility, withdrawal/consumption/citations
и approved Identity→Strategy не завершены. #120/AI Jobs/budgets/full Playbook отдельно.
Local commit; push/exact-SHA CI после завершения issue. No merge/deployment.

## #121 original analysis provenance — 2026-10-08

Третий local slice: explicit Identity DRAFT copy из current successful analysis receipt,
server-selected/explicit scoped source, original immutable provenance, V30 and indexed lookup.
Final full801,0 failures/errors,3Windows filesystem skips закрыты Linux35/35; scripts10/10,
renderer29/29,all mandatory PostgreSQL/provider gates и runtime permissions прошли.
Packaged health200/new anonymous route401. [Отчёт](issue-121-provenance-slice.md).
#121/#107 Task2.3 остаются IN PROGRESS: evidence admission/snapshots/citations и approved
Identity→Strategy ещё отсутствуют. Real model #120/AI Jobs/budgets отдельно. No push/merge/deploy;
baseline remote CI не квалифицирует новый feature source. GitHub status обновляется после local commit.

## Brand analysis #114 — 2026-10-01

Согласованный scope: provider-neutral port и production fail-closed; real model adapter
отложен отдельно. Delivered: validated immutable command/result, fake isolation, V23
attempt status/provenance, atomic version/archive/deadline fenced publication.
Final local Maven645: 0 failures/errors, 3 Windows skips; Linux filesystem35/35,
scripts Python10/10, renderer29/29, required real provider reports passed.
Compose V22→V23/runtime permissions/HTTP startup проверены.
[Полный отчёт](issue-114-completion.md), [manifest](issue-114-files.md).
Task2.3 broader real generation/citations/approval остаётся partial; #114 closed completed,
следующая задача #97 (`status: next`). [CI36902726234](https://github.com/Sanyoghk/Cadence/actions/runs/36902726234)
для `5b3118e048a07f0e5a73d011423610268103be3e` прошёл: оба jobs success,
Linux Maven645/645 без errors/failures/skips, scripts10/10, renderer29/29,
required provider gates, enforcing AppArmor/sandbox/screenshot passed;
OSV verified165 Maven dependencies без findings. Последующий docs-only commit
записывает qualification при неизменном product source/tests.

## API contract baseline #111 — 2026-10-01

Repository scope [#111](https://github.com/Sanyoghk/Cadence/issues/111) реализован:
reviewed OWNER-only OpenAPI 3.1.1, common safe ProblemDetail во MVC/security/upload
paths, validation field/code schema, bounded deterministic pagination и audit
Instant/TIMESTAMPTZ V22. Private FILE sourceReference скрыт в DTO. Future feature
issues расширяют schema и controller tests; несуществующие APIs не объявляются готовыми.

Local source `7ee9efa`: full Maven **615 tests, 0 failures/errors, 3 Windows-only
skips**; все **35/35 filesystem cases** отдельно passed на Linux без skips.
Python scripts **10/10**, freshly built Linux renderer **29/29**; required provider
report gates, JAR/JaCoCo и V21 → V22 Compose runtime startup passed.
Подробности: [contract](api-contract.md), [completion](issue-111-completion.md),
[44-file manifest](issue-111-files.md). CI qualification публикуется отдельно для
фактического pushed SHA. Следующая repository работа — #114.

Remote [CI 36893518886](https://github.com/Sanyoghk/Cadence/actions/runs/36893518886)
для `1e5716d82f7cfca13289fa30ef6c98e157b0d572` прошёл: оба jobs success,
**615 Java tests, 0 failures/errors/skips**, Python scripts10/10 и renderer29/29,
required provider reports и enforcing AppArmor/sandbox preflight passed.
OSV verified165 Maven components без findings. Последующий docs-only commit
записывает результаты при неизменном product source; production qualification отдельная.

## Текущий implementation baseline — 2026-10-01

Реализация [#117](https://github.com/Sanyoghk/Cadence/issues/117) завершена локально:
S3-compatible storage, private clamd, isolated screenshots/PDF extraction,
race-safe lifecycle/retention/quotas и approved Instagram/TikTok source adapters.
Contracts и фактические результаты final verification собраны в
[отчёте #117](issue-117-completion.md), [S3](s3-storage.md), [clamd](clamd-scanning.md),
[lifecycle](quarantine-lifecycle.md), [artifacts](evidence-artifacts.md) и
[social](approved-social-evidence.md).

Полный Maven/Python suite выполнен с Docker и реальными PostgreSQL/MinIO/ClamAV/renderer
fixtures: 593 Java tests, 0 failures/errors, 3 Windows-only skips; все 35 filesystem cases
прошли отдельно на Linux без skips. Python report/diagnostics scripts: 10/10; Linux renderer: 29/29,
без failures/skips. OSV: 165 Maven и 4 уникальных Python components без findings на момент scan.
Compose startup, свежие clamd signatures, localhost health 200 и unauthenticated API 401 проверены.
Точные commands приведены в [отчёте](issue-117-completion.md). Результаты remote CI относятся
к конкретному опубликованному SHA; local verification не является доказательством deployment.

Remote [CI 36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248)
для `ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab` прошёл полностью: **593 Java tests,
0 failures/errors/skips**, Python scripts **10/10** и renderer **29/29**. Оба required
report gates прошли; enforcing scoped AppArmor, kernel preflight и real Chromium sandbox
подтверждены на Ubuntu CI host. `dependency-security` также прошёл: все 165 Maven
components проверены OSV без findings. Production deployment qualification остаётся отдельной.

Внешние gates: выбранная production storage/TLS/IAM/network/sandbox infrastructure,
live provider approvals/accounts и public OAuth onboarding/refresh по
[#32](https://github.com/Sanyoghk/Cadence/issues/32)/[#33](https://github.com/Sanyoghk/Cadence/issues/33).
MinIO подтверждает local S3 contract, но не заменяет selected-provider qualification;
server-provisioned social bindings не реализуют self-service OAuth lifecycle.

Execution order на момент завершения #117 по [#107](https://github.com/Sanyoghk/Cadence/issues/107):
[#111](https://github.com/Sanyoghk/Cadence/issues/111) API/OpenAPI contract, затем
[#114](https://github.com/Sanyoghk/Cadence/issues/114) AI Brand Identity.
Этот раздел описывает локальное состояние реализации; состояние remote issues/board
подтверждается отдельной GitHub synchronization после final gate.

## Historical snapshot — 2026-09-11

Следующие pending states, test counts и push/CI сведения относятся исключительно к
снимку 2026-09-11. Они сохранены как история и не определяют текущий baseline #117.

This mapping was checked against the actual issues in [Sanyoghk/Cadence](https://github.com/Sanyoghk/Cadence/issues), not inferred only from commit subjects. The master plan is [#107](https://github.com/Sanyoghk/Cadence/issues/107).

| Issue | Changes in this batch | Still incomplete |
| --- | --- | --- |
| [#9](https://github.com/Sanyoghk/Cadence/issues/9) | Null-safe JWT audience/authorized-party validation, required expiration, real decoder fixtures | Live Clerk configuration verification |
| [#74](https://github.com/Sanyoghk/Cadence/issues/74), [#87](https://github.com/Sanyoghk/Cadence/issues/87) | Use-case roles, authenticated scope validation, owner impersonation denial, archive guards | Reserved-role defect corrected locally; audit, rate limits, security scans |
| [#111](https://github.com/Sanyoghk/Cadence/issues/111), [#117](https://github.com/Sanyoghk/Cadence/issues/117) | Scoped ingest Job status API, safe response DTO, Location header, negative tests | OpenAPI, extraction, production storage/scanner and social adapters |
| [#8](https://github.com/Sanyoghk/Cadence/issues/8), [#118](https://github.com/Sanyoghk/Cadence/issues/118) | CI workflow, PostgreSQL skip gate and its tests, Dependabot configuration; remote CI passed | Deployment, populated upgrade tests, security scanning and E2E remain incomplete |
| [#119](https://github.com/Sanyoghk/Cadence/issues/119) | Removed obsolete pilot/iframe statements from ADR-0001 | No new product scope approval implied |

### Reserved-role correction — historical

The issue audit found that #74 required EDITOR/VIEWER disabled while reads and EDITOR analysis were allowed. The follow-up correction restricts all user-facing controller and use-case operations to OWNER. Scope/ownership checks remain in place; stored roles and migrations are unchanged. Regression tests reproduce the previous unauthorized reads and cover HTTP, direct Spring-bean calls and a misleading OWNER JWT claim for a stored reserved-role identity. See [role correction](owner-only-role-fix.md) for current verification. Earlier synchronization statements below describe the preceding commits.

### Verification baseline — historical

Local `mvnw.cmd clean verify`: 201 tests, 0 failures/errors, 4 PostgreSQL/Testcontainers skips because Docker is unavailable. Python CI-report gate tests pass. Compilation, test-profile startup, Spotless and JaCoCo pass. This is not a PostgreSQL/production verification claim. Earlier maintenance reports are historical snapshots from before commit/push synchronization.

### Next product work — historical

Synchronization pushed commits `692fa6b` and `b50c69a` to `mvp`, updated Issues #8, #9, #74, #87, #107, #111, #117, #118 and #119, and refreshed draft PR #113. Issue #74 was reopened for the reserved-role discrepancy; its automatic closing reference was removed from the PR. No merge or deployment was performed.

[CI run 34544158729](https://github.com/Sanyoghk/Cadence/actions/runs/34544158729) passed for `b50c69a`, including clean verify and the mandatory PostgreSQL report gate. Local Docker skips above remain accurate local results; the remote runner separately verified the PostgreSQL suite without skips.

The remote master checklist still identifies #117 as current source-ingest work, followed by #114. CI/security work and the Job API are supporting slices, not completion of those broad issues. Do not close #8, #87, #111, #117 or #118 based solely on this batch.


## 2026-10-04 — Strategy generation #97

Created #120 for bounded production Brand/Strategy adapters; provider unselected,
production fail-closed. #97 application flow delivered with V24/source snapshot,
versioned drafts, authorization/deadline/archive guards and actual runtime privileges.
Local full qualification:678 Java tests,0 failures/errors,3 Windows filesystem skips;
Linux filesystem35/35 no skips; scripts Python10/10, renderer29/29, all required
PostgreSQL/real-provider gates passed. [Report](issue-97-completion.md),
[every-file manifest](issue-97-files.md). #102 remains next REST/approval lifecycle work;
broader approved Brand Identity/product scope is not marked complete. Push/remote CI
and GitHub completion are recorded separately once performed.

Saved Compose upgraded V23→V24, runtime restrictions verified, health200/private401 passed; ClamAV28143 fresh. No production deployment.


Remote [CI37228608605](https://github.com/Sanyoghk/Cadence/actions/runs/37228608605)
passed for660d4c7d37a9c24b880cd1f95df7d2ec15e5ba31: both jobs success, Linux678/678
with failures/errors/skips0, Python10/10+29/29, mandatory provider reports, enforcing
AppArmor/kernel/sandbox and screenshot1280×720 passed. OSV complete165-component
inventory without findings. #97 closed completed; #102 status:next; #107 updated.
Docs-only qualification update does not change product source/tests; no merge/deploy.


## #102 repository lifecycle — 2026-10-05

Все REST/lifecycle acceptance criteria реализованы и qualified локально: full746,0 failures/errors,3Windows filesystem skips закрыты Linux35/35; PostgreSQL/providers gates passed, Python10/10 и renderer29/29, packaged startup health200 и5routes401. [Report](issue-102-completion.md), [manifest](issue-102-files.md). Push/exact-SHA CI и закрытие GitHub issue записываются после фактического выполнения.
Следующий planned feature [#121](https://github.com/Sanyoghk/Cadence/issues/121): versioned Brand Identity/citations/approval + approved Strategy source; #120 real adapters остаётся planned. Более широкий Playbook/AI Jobs/budgets/frontend/production scope не объявляется completed.

### #102 completion / следующий scope #121

Pushed feature source e400acb8eadbaa5b186b61fa8549e9ed0e70247b (includes3879f23).
[CI37261511659](https://github.com/Sanyoghk/Cadence/actions/runs/37261511659): оба jobs success;
Linux746/746 без failures/errors/skips, scripts10/10+renderer29/29, mandatory reports,
enforcing sandbox/preflight/screenshot и OSV165 clean. #102 closed completed; #107 Task3.2
checked, Task2.3 remains partial; #121 status:next, implementation не начат. #120 remains planned.
Docs-only qualification commit не меняет product source/tests; CI квалифицирует e400acb.
Merge/deployment не выполнялись.

## #121 manual Identity draft slice — 2026-10-05

Статус IN PROGRESS. Первый slice создаёт отдельные BrandIdentity DRAFT revisions и scoped
OWNER create/read/list/PATCH с expectedVersion. V28/grants/deferred constraints защищают
metadata/content/history; legacy signature не переносится и остаётся независимой. Approval,
citations и approved Identity→Strategy source — следующий scope, acceptance checkboxes пока
не completed. [Report](issue-121-draft-slice.md), [ADR-0012](adr/0012-brand-identity-drafts.md).
Baseline docs-only b2a6871 CI37262578710success не квалифицирует новый local feature source.
Push и exact-SHA CI отложены до завершения всего #121; merge/deployment не выполнялись.

Local qualification #121 slice1: full770,0failures/errors,3Windows filesystem skips;
Linux35/35 без skips; Python10/10+renderer29/29, all mandatory PostgreSQL/provider reports,
Spotless/JaCoCo/JAR, packaged startup health200 и4Identity operations401 passed.
Новый feature commit local only; issue остаётся IN PROGRESS.

## #121 Identity approval / revision lifecycle — 2026-10-05

Второй slice реализует separate committed draft approval, actual DB OWNER/membership,
server actor/time, approved/locked PATCH→independent DRAFT с immutable lineage и201/Location.
V29 сохраняет V28 content/version/token/NULL legacy audit; защищает approved root/collections,
audit/lineage и root history, runtime grants расширены только status/audit columns.

Local qualification: full786,0failures/errors,3Windows filesystem skips закрыты Linux35/35;
focused42/42, scripts10/10,renderer29/29, all mandatory PostgreSQL/provider reports и actual
restricted runtime lifecycle tests прошли. [Report](issue-121-lifecycle-slice.md).
#121/#107 Task2.3 остаются IN PROGRESS: citations и approved Identity→Strategy source ещё не
реализованы. Push/exact-SHA CI после всего issue; merge/deployment не выполнялись.

## 2026-10-14 — задание01 / PDF Identity consumer V38

Scope01 qualified locally: PDF analysis→Identity DRAFT/read/direct+inherited approval,
original publication lineage Brand=attempt+1, immutable artifact/original/latest scan IDs,
physical I/O вне transactions и fresh locked source/access/withdrawal/expiry/ledger checks.
V1–V37 unchanged, V38 metadata SQL guard; legacy/manual/TEXT сохранены. [Отчёт](issue-121-pdf-identity-slice.md),
[ADR0020](adr/0020-pdf-identity-consumption.md), [ledger](mvp-prompt-progress.md).

Final isolated `mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify`:
BUILD SUCCESS2026-10-14T04:01:26+01:00;118suites/1079tests/0failures/errors,3Windows skips
covered Linux35/35. PDF H2API57/PG68,migration2,capacity2,OpenAPI3 passed. Mandatory PostgreSQL/
MinIO/ClamAV/renderer/full pipeline reports PASS;Python10/10,rebuilt renderer29/29,
JaCoCo94.85%,packaged health200/Identity copy/approval/provenance401.
464 source/build hashes идентичны workspace,302 class hashes stable. Первый root full run
имел1079tests/0failures/errors, но failed JaCoCo из-за изменившихся class IDs; он не pass.
Snapshot qualification не меняет POM/gates/IDE settings. Production/live provider gates отдельно.

Local commit only; no push/remote CI/closure/merge/deploy. #121/#107 остаются IN PROGRESS.
Remaining: other source eligibility и approved Identity→Strategy; задания02+ не начаты.


## Task23 — frontend foundation (2026-10-18, local only)

Actual #3/#107 read at baseline `15ad3ec`. Frontend foundation/Clerk wiring/onboarding
qualified locally for task23 repository scope; live qualification remains blocked. Evidence: [report23](prompt23-frontend-report.md).
Live Clerk signup/login blocked by missing development keys/test user. Local browser
contract session never claims live authentication. #3 contains remaining24–27 screens;
not closed, no remote update/push/CI claimed. Existing #121 remains open under ADR0027.


## Prototype UI plan — 2026-10-18 (local docs-only)

Actual #3/#107 reread at clean `3b49c94`; both open. User selected existing HTML demo design.
[План переноса/fingerprint/acceptance](prototype-ui-adaptation-plan.md): qualified foundation23
сохраняется, visual follow-up23 pending →24–27 actual API integration. Это не delivery #3,
не изменение backend/provider scope и не remote issue update. Live Clerk/provider blockers,
whole-issue push policy и later release gates остаются. Новая implementation не начата.
