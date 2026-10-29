# Cadence — подробный Developer Implementation Plan

## Актуальный план V2 — расширенное ТЗ2026-10-20

Исполняемый маршрут: [промпты V2](docs/astra-medium-mvp-prompts-v2.md).
Текущее [описание продукта](docs/product-scope.md), [инвентаризация старых требований](docs/scope-v2-inventory.md)
и [ADR0041](docs/adr/0041-expanded-creator-production-scope.md) заменяют прежний CSV-first scope.
Required: ready-video generation, raw-video editing, photo/video packages, organic
market/competitor/hashtag analysis, rich Playbook/4week calendar, approved Instagram/TikTok
publishing, live metrics, regular reports/reminders и ongoing Strategy.
£20 — demo AI calls; остальные costs отдельно. Providers/training не выбраны.
Сначала backend/API acceptance, затем новый frontend и final platform UX/live audit.
Старые qualified foundations сохраняются; новые V2 implementation tasks NOT_STARTED.
Уточнение: **один Workspace на аккаунт, несколько Brands**. V2-01 начинает с миграции,
server/DB enforcement и idempotent onboarding; V2-03/29 проверяют flow, V2-30 заменяет
Workspace manager на Brand management. [ADR0042](docs/adr/0042-single-workspace-per-account.md).

**Всё ниже — исторический план и execution evidence.** Его прежние next-task указания,
запрет direct publishing в MVP, multi-workspace flows и оценки не применяются вместо V2.

## Исторический приоритет — backend MVP до нового frontend,2026-10-20

Пользователь подтвердил рабочий MVP с реальным AI до полной переработки frontend.
[Актуальный план завершения backend](docs/backend-mvp-completion.md) имеет приоритет над
историческими указаниями «следующий frontend25» ниже. UI implementation25–27 отложена.
Backend09–22/28 уже qualified locally в своих scopes; дальше provider/model/data/cost
решения →real adapters и usage budgets →visual Identity →Content/recommendations/media
→self-service/API acceptance →полный backend release candidate. Затем новый frontend.
Manual/CSV metrics достаточно для выбранного пути; billing/direct publishing вне MVP.
Это planning decision, не реализация adapters, fresh full verification или production readiness.
Уточнение: provider пока не выбран, демо-бюджет £20. Сначала анализ AI adapters и обсуждение;
предложенные этапы/provider/model/privacy/cost policy **не утверждены** и не запускаются.

<!-- ui-rework23-24-execution:start -->
### UI rework23–24 — QUALIFIED LOCAL (2026-10-20)

Принятый Cream Orbit перенесён в `frontend/`: public landing/shell, responsive navigation,
Clerk entry и существующие Workspace/Brand/evidence/exact consent/Identity/edit/separate approval/
withdrawal/history. ADR0040, API clients и tenant/session/version/checksum guards сохранены.
[Отчёт и actual gates](docs/ui-rework23-24-report.md).58 unit +66 Chromium/axe tests PASS;
lint/typecheck/build/export PASS. Backend не изменён; backend28 qualification не повторялась.
Следующий frontend scope:25→26→27, затем frontend/cross-stack28→29–30. Cinematic motion polish
отложена пользователем; real AI/live Clerk provisioning/physical-device/release gates отдельно.
No push/remote CI/issue edits/merge/deployment. #3/#107 OPEN;25–27 не заявлены delivered.
<!-- ui-rework23-24-execution:end -->

<!-- backend28-execution:start -->
### Backend28 — QUALIFIED LOCAL (2026-10-19)

09–22 не повторяются. [Backend28 report](docs/prompt28-backend-report.md) фиксирует actual
findings/fixes, security/audit/readiness/limits и fresh qualification. Backend pass не завершает
whole28: frontend/cross-stack28 **PENDING**, после23–27 и qualification изменённых adapters.
Следующий backend milestone по ADR0027 — отдельное решение AI API/model/privacy/cost и adapters;
этот шаг provider не выбирает. Принятый UI design и route rework23–24→25–27 сохраняются.
Issues #87/#83/#116/#118/#121 остаются OPEN до соответствующего whole-scope acceptance.
Local only: no push/new remote CI/issue closure/merge/deployment. Production readiness не заявляется.
<!-- backend28-execution:end -->


Status: active plan

Updated: 2026-10-19

GitHub синхронизирован 2026-10-18 по явному запросу пользователя: #3/#107 и девять
связанных issues. [Фактические изменения и read-back checks](docs/issue-tidy-2026-10-18.md).
Remote `mvp` остаётся `a283382c`; local14–24 не pushed, whole-issue acceptance не закрыта.

## Предыдущая точка продолжения — история2026-10-19/20

**2026-10-19: пользователь принял Cream Orbit UI как основу; анимации доработает вручную позже.**
[UI rework plan](docs/ui-rework-plan.md) сохраняет прежний design/execution context.
На тот момент: **28 backend и UI rework23–24 qualified locally; следующим планировался25→26→27**.
Design package/prototype перенесён в рабочий frontend; дополнительная motion polishing отложена.
09–22 и foundation23/24 не реализуются заново. ADR0027 сохраняет независимый маршрут
backend28→AI integration; UI не становится prerequisite выбора provider. Затем frontend28→29–30.
Local Clerk development setup выполнен, пользователь сообщил о manual smoke; полная
live auth E2E/self-service provisioning/production qualification этим не заявляется.
Ниже — исторические execution records; актуальные границы qualification приведены выше.

Design review2026-10-19: [Cream Orbit prototype, desktop/mobile views, design system, navigation/API mapping](docs/design/ui-rework/README.md)
подготовлены и приняты как визуальная основа. Rework23–24 перенесён20 октября в существующий
Next.js/Clerk/Bearer frontend с сохранением guards; backend не изменялся этим переносом.

**24 — QUALIFIED LOCAL repository scope (2026-10-18); до rework request следующим был25.**
Baseline clean `5dbd15f`, supported01–06 + qualified23/visual follow-up23; #3/#107 проверены.
[Report24](docs/prompt24-identity-report.md), [47-file manifest](docs/prompt24-changed-files.md).
Evidence registration/Job/review/consent/current/withdrawal/history и Identity EVIDENCE
DRAFT/edit/citations/approval/revisions работают через actual API. Scoped metadata history,
CORS extension и bounded concurrent first-auth recovery; ADR0040 Bearer architecture сохранена.
Frontend58unit/56Chromium+axe, lint/contracts/format/type/build/export/peers/audit PASS.
Final full2286/0failures/errors,4Windows skips covered Linux37/37; Python10/renderer29,
mandatory PostgreSQL/provider reports, coverage/startup и actual HTTP browser workflow PASS.
Fresh Maven194/OSV0; npm263/0vulnerabilities.745 backend/build source hashes stable.
Live Clerk/OAuth/AI providers остаются external blockers; images/screenshots REQUIRES_PROVIDER.
25–27 этим шагом не начаты; #3/#121 open, no push/remote CI/closure/merge/deployment.
Visual follow-up23 остаётся qualified: [report](docs/prompt23-visual-report.md).

**23 — QUALIFIED LOCAL repository scope (2026-10-18); live Clerk BLOCKED.**
Baseline `15ad3ec`, stable Workspace/Brand API; actual #3/#107 перечитаны.
[ADR0040](docs/adr/0040-frontend-bearer-onboarding.md),
[setup/live gate](docs/frontend-foundation.md), [report23](docs/prompt23-frontend-report.md).
Standalone static Next.js/React/TypeScript, official Clerk React SDK + explicit Bearer,
typed bounded API, scoped opt-in CORS и OWNER Workspace/Brand onboarding.
Live Clerk credentials/test user отсутствуют: signup/login остаются external blocker,
не заменяются UI fixtures. #3 шире23:24–27 остаются open.07 closure/08/AI provider не требуются.
Frontend35unit/16Chromium+axe, lint/type/build/peers PASS; npm263/0vulns.
Full2270/0failures/errors,4Windows filesystem skips покрыты Linux37/37;2266 executed PASS, 181 suites. Python10/renderer29/startup/mandatory reports PASS; fresh Maven194/OSV0.
Один prior existing metrics-import guard failure не воспроизведён; exact cause unconfirmed, см. report23.
No push/remote CI/issue closure; дальнейшие номера не начаты.


**22 — QUALIFIED LOCAL (2026-10-17):**
[ADR0039](docs/adr/0039-creator-performance-reports.md),
[contracts/test matrix](docs/prompt22-report-contract.md).
Пользователь утвердил HTML+JSON,24h result access, bounded retained history/bytes и private
authenticated delivery вместо legacy signed-link wording #105. Report Job/JSON/HTML/V55 реализованы;
full qualification PASS: [report22](docs/prompt22-reports-report.md), [API](docs/creator-reports-api.md).
Full2250/0 failures/errors,4 Windows filesystem skips covered Linux37/37; report H266/66,PG76/76,migration1/1;Python10/10,renderer29/29,reports/Spotless/JaCoCo/startup PASS;freshSBOM194/OSV0;743 stable source/build/config hashes.
Prerequisites19–21 delivered; они не повторяются.

**21 — QUALIFIED LOCAL (2026-10-17):** [ADR0038](docs/adr/0038-evidence-linked-recommendations.md),
[recommendation API](docs/recommendations-api.md), [report21](docs/prompt21-recommendations-report.md).
Actual prerequisite13/19/20 на clean dabc367; #100/#105/#107 прочитаны2026-10-17.
Full2104/0 failures/errors,4 Windows filesystem skips covered Linux37/37; PG61/61,migration1/1;Python10/10,renderer29/29,reports/Spotless/JaCoCo/startup PASS;freshSBOM194/OSV0;722 stable source/build hashes.
Bounded HEURISTIC, exact supporting metric/context capture, immutable admission binding,
review/apply→new editable Strategy DRAFT, SQL/runtime guards; no invented live AI/confidence.
#105 report scope22 не выполнен;22+ не начаты. No push/remote CI/closure/merge/deployment.

**20 — QUALIFIED LOCAL (2026-10-17):** [ADR0037](docs/adr/0037-performance-analytics.md),
[performance API](docs/performance-api.md), [report20](docs/prompt20-performance-report.md).
Bounded performance formulas, DAY/PERIOD windows, historical attribution и coverage caveats.
Full1984/0 failures/errors,4 Windows skips covered Linux37/37; PG39/39,migration1/1,JDBC timeout3/3;Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;694 stable source/build hashes.
Prerequisite19 qualified: [report19](docs/prompt19-metrics-import-report.md), baseline27ca07c.
21+ не начаты; push/remote CI/closure не выполнялись; #99 live sync/#120/#121 остаются open.

**18 — QUALIFIED LOCAL:** [ADR0035](docs/adr/0035-durable-csv-export.md),
[CSV API/runbook](docs/csv-export-api.md), [report18](docs/prompt18-csv-export-report.md),
[manifest](docs/prompt18-changed-files.md).
Durable CSV_EXPORT, explicit immutable approved/scheduled batch, scoped idempotency,
fenced recovery/atomic private artifact, formula-safe CSV and fresh authorized download. V51/runtime guards.
Full1810/0failures/errors,4Windows skips covered Linux37/37;CSV PG43/43,HTTP24/24,domain11/11,migration1/1;AI child-JVM8/8. Python10/10,renderer29/29,mandatory reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;651 stable source/build hashes.
История18:19 тогда не было начато; no push/remote CI/issue closure. #121/#120 live provider/visual scope remains open.

**17 — QUALIFIED LOCAL:** [ADR0034](docs/adr/0034-private-demo-media.md),
[Media API](docs/media-assets-api.md), [report17](docs/prompt17-media-report.md).
Private retained PNG/MP4 demo assets, scan→human review→revision refs/delivery, V50/runtime/IAM;
632 source/build hashes unchanged. Targeted147/0failures/errors/4Windows skips +compat163/163 +final9/9;
carousel regression red→green49/0failures/errors/4Windows skips. Final full1731/0failures/errors,
4Windows skips covered Linux37/37;MediaPG49/49,PostPG29/29,calendar45/45,ContentPG25/25,crash8/8.
Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;local commit only.
История17:18 тогда не было начато; live media/decoder/provider decisions остаются отдельной qualification, no push/closure.


**16 — QUALIFIED LOCAL:** [ADR0033](docs/adr/0033-calendar-scheduling.md),
[Calendar API](docs/calendar-api.md), [report16](docs/prompt16-calendar-report.md).
Append-only schedule history, dual tokens, explicit DST offsets, bounded calendar и atomic batches.
Actual prerequisite14–15: clean c0e5473. Targeted95/95 +final4/4; full1638/0failures/errors,
3Windows skips covered Linux35/35;CalendarPG45/45,ContentPG25/25,crash8/8.
Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;605 stable hashes.
История16:17 тогда не было начато; #115/#103 остаются open для media/export и whole-issue qualification.


**15 — QUALIFIED LOCAL:** [ADR0032](docs/adr/0032-content-generation-jobs.md),
[Content API](docs/content-generation-api.md), [полный отчёт15](docs/prompt15-content-generation-report.md).
Durable CONTENT Jobs + approved Strategy/Identity/evidence capture → atomic DRAFT batch;
field/all regeneration с immutable lineage и current-transaction actor fencing. V48/runtime.
Full1554:0failures/errors,3Windows skips covered Linux35/35;ContentPG25/25,recovery8/8;
Python10/10,renderer29/29,reports/coverage/startup PASS;freshSBOM194/OSV0;592 stable hashes.
История15:16 тогда не был начат. #98/#115 остаются open для wider scheduling/media/export scopes16–18.
Production adapters disabled; local commit only, push/closure не выполнялись.

**14 — QUALIFIED LOCAL:** [ADR0031](docs/adr/0031-post-revision-lifecycle.md),
[Post API](docs/post-revisions-api.md). Stable Post identity, exact approved full Strategy source,
manual DRAFT/edit/approval/new revision, explicit legacy history/V47 и SQL/runtime guards.
Full1514:0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29,
mandatory reports/Spotless/JaCoCo/packaged startup PASS;SBOM194/OSV0findings;576 stable hashes.
[Полный отчёт14](docs/prompt14-post-revisions-report.md). 15 qualified locally;16 qualified locally; #115/#103 шире14 и остаются open.

Prerequisites10–13 + Spring migration pushed по explicit user request: `a283382c8191bfa4020d0fb3c141493cabb9f821`.
[Push CI](https://github.com/Sanyoghk/Cadence/actions/runs/38846267446) и
[PR CI](https://github.com/Sanyoghk/Cadence/actions/runs/38846270484): verify/dependency-security SUCCESS.
Это remote qualification baseline13; новые изменения14 пока local.

**13 — QUALIFIED LOCAL:** [report13](docs/prompt13-playbook-report.md),
[ADR0029](docs/adr/0029-full-playbook-contract.md). Complete v2 Playbook, normalized collections,
explicit legacy upgrade, human-edit attribution, immutable history и async schema2 реализованы.
Targeted161/161 + reorder/domain40/40 + remediation11/11 passed. Final full1457:0failures/errors,
3Windows skips covered Linux35/35;Python10/10,renderer29/29,reports/coverage/startup PASS;
current SBOM194/OSV0 findings;554 stable source/build hashes.
Историческая qualification13; последующие push/CI и14 отражены выше.

**12 — QUALIFIED LOCAL:** [report12](docs/prompt12-budget-report.md),
[ADR0030](docs/adr/0030-generation-budget-ledger.md). Shared durable sync/async budgets,
scoped replay, unknown holds и typed usage реализованы; targeted273/273 passed.
Final full1439: 0failures/errors,3Windows skips covered Linux35/35; Python10/10,renderer29/29,
mandatory reports/coverage/startup PASS; current SBOM194/OSV0findings. Это baseline12; no push/closure.

**11 — QUALIFIED LOCAL (prerequisite12):**
[Отчёт11](docs/prompt11-recovery-report.md), [runbook](docs/ai-jobs-recovery-runbook.md).
Completion provenance/V44, controlled codes, metrics/correlation, ready discovery, bounded
polling и process crash/restart tests. Full1405/0failures/errors,3Windows skips covered
Linux35/35;Python10/10,renderer29/29,reports/coverage/startup PASS;current SBOM194/OSV0 findings.
Scope12 (budget/idempotency, draft B) qualified поверх этого baseline.

**Spring migration — QUALIFIED LOCAL (история prerequisite11):**
Boot4.0.8/Spring7.0.9, Tomcat11.0.27 и Jackson security patches;
[решения и отчёт](docs/spring4-migration-report.md).
Fresh OSV194 dependencies/0 findings; full1385/0failures/errors,3Windows skips covered
Linux35/35;Python10/10,renderer29/29,reports/Spotless/coverage/packaged startup PASS.
Migration local commit; новый push/remote CI не выполнялись.
Исходный checklist11: [readiness matrix](docs/prompt11-readiness.md).
No public cancellation/provider activation; #121 остаётся open.

**10 — QUALIFIED LOCAL:** [контракт](docs/durable-ai-generation.md),
[отчёт10](docs/prompt10-durable-generation-report.md). Durable operation/attempt/source capture,
202/Job/result API, atomic publication и lease/access/physical fences; V43.
Full1381/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29;
mandatory reports, Spotless/coverage и packaged worker startup PASS.
09 design остаётся принят: [ADR0028](docs/adr/0028-durable-ai-workflow-contracts.md),
[ADR0029](docs/adr/0029-full-playbook-contract.md), [точные drafts A/B/P](docs/ai-workflow-issue-drafts.md).
Remote issue numbers не назначены. Scope A реализован10–11; remaining12 (B),13 (P), без ожидания #121/#120.
По explicit user request baseline09 `ff29281` pushed; exact-SHA CI verify SUCCESS,
dependency-security FAILURE (Spring advisories). Пользователь выбрал завершить10 локально,
затем отдельную Spring stack migration; scanner не подавляется, production blocked.

Утверждён новый порядок: [ADR0027](docs/adr/0027-backend-first-ai-adapters-last.md).
Сначала provider-independent MVP backend, затем выбор AI API и adapters. #121 остаётся open,
но не блокирует независимые Task 2.4/2.5/3.3 и последующие backend scopes.

Product HEAD review07: `c19abc0`; прежний обзор: [review](docs/project-review-2026-10-12.md).
Состояние #121 — **OPEN, provider-dependent остаток отложен**. Реализованы Identity drafts/approval/history,
копирование qualified analysis, TEXT review/admission, current snapshot и withdrawal (V28–V32).
Versioned re-review неотозванной TEXT sourceVersion в новом context реализован отдельным slice
([ADR0014](docs/adr/0014-versioned-text-re-review.md), [отчёт](docs/issue-121-re-review-slice.md)).
TEXT analysis/citations → Identity реализованы (V34/V35); PDF preview и explicit admission
history/snapshot/withdrawal реализованы (ADR0018/V36). PDF_TEXT generation/signature publication
добавлены в [текущем slice](docs/issue-121-pdf-analysis-slice.md), ADR0019/V37.
PDF Identity copy/read/direct+inherited approval реализованы в V38 (задание 01); qualification — [отчёт](docs/issue-121-pdf-identity-slice.md).
01–05 qualified locally: TEXT/PDF/URL/social text consumers и Identity guards (V38–V41).
Visual inputs остаются REQUIRES_PROVIDER #120; live OAuth #32/#33 не квалифицирован.
06: approved Identity → Strategy, [ADR0026](docs/adr/0026-approved-identity-strategy-source.md);
V42 qualified locally: [отчёт06](docs/issue-121-approved-strategy-slice.md).
Full1309/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29;
mandatory reports/runtime/startup PASS (historical run14 октября). #121 open до whole-issue
acceptance и push/exact-source-SHA CI. Real adapters/privacy/visual #120 отдельно.
Production AI остаётся disabled; real adapters — #120. AI Jobs реализованы10–11; budgets/idempotency12
проходят final qualification. Full Playbook13 delivered locally отдельно от незавершённого provider scope #121.
GitHub #107/#121/#120 повторно прочитаны 2026-10-15; новые issue numbers этим планом не назначаются.

**07 — whole-issue review выполнен, closure BLOCKED:** [acceptance matrix](docs/issue-121-whole-review-2026-10-15.md).
Утверждённый ADR0021/P2 прямо запрещает полное завершение #121 при visual REQUIRES_PROVIDER.
PNG/JPEG и URL screenshot interpretation/admission/consumer/tests не delivered; ADR0024 —
design contract. #120 dependency не исключает этот gap из #121. До снятия blocker — no push,
no exact-SHA CI claim, no closure. Code06 unchanged; review07 docs-only. Другие номера не начаты.
Оба DEVELOPMENT_PLAN содержат одинаковые статусы, зависимости и критерии продолжения.

## 1. Product definition

Cadence — self-service AI workspace для individual entrepreneurs и content creators. Основной цикл:

`Creator → Workspace → Brand Identity → Strategy Playbook → Content → Performance → Recommendations`

HTML prototype используется как UX reference, но не определяет ownership model или technology stack. Agency-specific concepts отклонены. Spring Boot backend остается system of record.

### MVP decisions

- Tenancy model: `CadenceUser → Workspace → Brand`.
- Активная MVP role — `OWNER`; `EDITOR` и `VIEWER` зарезервированы для collaboration.
- Clerk отвечает за identity/JWT, Cadence — за authorization.
- Первые platforms: Instagram и TikTok.
- Каждый AI result требует human review.
- Approved content экспортируется в CSV; direct publishing не входит в MVP.
- Production database — PostgreSQL; Docker обязателен для полного verification suite с PostgreSQL, MinIO, ClamAV и isolated renderer.
- Медленные/retryable operations выполняются как durable Jobs.

## 2. Current baseline

Уже implemented:

- Java 21/Spring Boot quality gates;
- Flyway migrations;
- domain foundations для Workspace, Brand, Strategy, SocialPost, PostMetric, Recommendation, CadenceUser и Job;
- Workspace-scoped repositories;
- Clerk JWT authentication и object authorization;
- paginated Workspace/Brand create/read/update/archive API;
- idempotent URL/text/file Brand evidence registration с provenance и durable ingest Jobs;
- quarantined PDF/PNG/JPEG upload с MIME/magic-byte validation, size limits, storage и malware-scanning ports;
- typed scanner outcomes CLEAN/INFECTED/UNAVAILABLE/ERROR и fail-closed HTTP 503 при сбое; реализован private clamd adapter с deadlines, signature freshness и immutable scan receipts ([контракт](docs/clamd-scanning.md));
- immutable S3-compatible storage с conditional writes, проверкой private/nonversioned bucket, bounded reads, checksum и concurrency limits; MinIO используется для local/integration tests ([контракт](docs/s3-storage.md));
- checksum-bound durable FILE/URL Jobs сохраняют immutable Job/source/engine/signature provenance с fenced completion ([контракты](docs/durable-file-scanning.md), [URL chaining](docs/url-malware-scanning.md));
- durable Job queue с claiming, retry, lease и recovery;
- загрузка файлов выполняет I/O вне транзакций БД; перед атомарной записью evidence/Job повторно проверяет архивирование и дубликаты под блокировкой ([контракт](docs/file-upload-transactions.md)); unique generations, publication fencing, permanent tombstones, retention и Workspace quotas обеспечивают race-safe orphan cleanup ([lifecycle](docs/quarantine-lifecycle.md));
- isolated website screenshots и PDF text extraction, bounded artifacts, повторная проверка последнего CLEAN/source/lease и immutable extraction provenance ([artifacts](docs/evidence-artifacts.md));
- approved Instagram/TikTok official API adapters с server-bound credentials, OWNER-scoped registration, ограниченным snapshot одной страницы, durable Workspace/provider quotas и append-only retrieval origins ([social contract](docs/approved-social-evidence.md));
- Workspace-scoped GET /api/v1/jobs/{id} для существующих evidence ingest Jobs; registration возвращает Location;
- unit, security/MVC, repository scoping и обязательные в CI PostgreSQL/Testcontainers tests.
- website-v2 строго декодирует UTF-8 и явные UTF-16LE/BE BOMs для новых URL metadata; исходные bytes остаются в quarantine ([контракт](docs/website-unicode-decoding.md)).

Реализованные последующие slices: Strategy generation #97 и REST/edit/approval #102;
Identity drafts/approval/history, original analysis provenance и TEXT admission/snapshot/withdrawal
#121 (V28–V32). Их наличие не завершает evidence-grounded AI и approved Identity → Strategy.

Partial/placeholder:

- Brand analysis использует validated port и persisted attempts; fake доступен только local/test, production fail-closed; реальная generation отложена; Identity/Strategy approval реализован;
- Strategy REST lifecycle реализован; полный Playbook и content/analytics workflows ещё не завершены;
- frontend foundation23 реализован; экраны24–27 и live Clerk qualification ещё не выполнены;
- реализация source ingest #117 завершена локально: opt-in URL fetch с public IP pinning, строгий UTF-8/явный UTF-16 BOM, PDF/PNG/JPEG quarantine, S3/clamd adapters, isolated artifacts, lifecycle и approved social snapshots. Итог фактического verification записывается в [отчёт #117](docs/issue-117-completion.md). Deployment qualification выбранной infrastructure и live Meta/TikTok OAuth onboarding/refresh (#32/#33) требуют внешних accounts/services;
- нет real AI, export и analytics adapters.

## 3. Target architecture

- `domain`: invariants и state transitions;
- `application`: use cases и provider-neutral ports;
- `presentation`: versioned HTTP DTOs и Problem Details;
- `security`: Clerk adapter и local Workspace authorization;
- `infrastructure`: PostgreSQL, AI, ingest, storage, export и analytics adapters.

Provider output разбирается по schema, проходит domain validation и сохраняется с provenance. Controllers не отдают JPA entities. Business lookup всегда ограничен authorized Workspace IDs.

## 4. Implementation tasks

### Phase 0 — Contract и repository baseline

#### Task 0.1 — Product contract

- `docs/product-scope.md` является authoritative scope.
- Source HTML остаются неизменными references.
- Material decisions фиксируются в ADR.
- GitHub Master Checklist и issues синхронизируются с этим plan.

#### Task 0.2 — Build и quality gates

Status: partially delivered — Maven quality gates and dependency-security CI are implemented; mandatory branch-check enforcement remains blocked/unverified. Deployment is separate. See [dependency security](docs/dependency-security.md).

- Enforce Java/Maven versions, Spotless, tests и JaCoCo.
- Добавить CI для `clean verify` и dependency/security checks.
- Merge `mvp` → `main` разрешать только после green checks.

### Phase 1 — Persistence и asynchronous work

#### Task 1.1 — Production persistence

Status: Реализовано: persistence foundation; deployment/backup/restore qualification отдельно.

- Flyway управляет PostgreSQL; Hibernate в production использует `validate`.
- Applied migration не менять — добавлять только новую versioned migration.
- Empty и upgrade paths проверяются на PostgreSQL с Docker как обязательный full-suite gate.

#### Task 1.2 — Durable Jobs

Status: Реализовано для ingest Jobs (#110); AI workflow остаётся Task 2.4.

- States: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED` (retryable), `DEAD` (terminal).
- Atomic claim, lease recovery, retry limit, idempotency key, progress и safe errors.
- Workers добавлять вместе с соответствующими use cases.

### Phase 2 — Creator onboarding и Brand Identity

#### Task 2.1 — Завершить Workspace/Brand management

Status: Реализовано в #112; следующий scope не требует повторять CRUD/archive.

- Paged lists, update и archive operations.
- Membership enforcement в use cases и queries, не только controllers.
- Optimistic versions и HTTP 409 для stale state.
- Hard delete отложить до утверждения retention policy.

#### Task 2.2 — Source ingest

Status: реализация #117 завершена локально. См. [итог и verification](docs/issue-117-completion.md) и [ADR-0007](docs/adr/0007-private-evidence-pipeline.md). Deployment validation отделена от реализации.

- Website URLs, pasted text и validated PDF/PNG/JPEG uploads; approved social ingest принимает provider enum и использует server-provisioned brand/account bindings.
- Сохраняются capture time, checksum, warnings, scan/extraction receipts и trusted social retrieval origins. Содержимое evidence остаётся недоверенным.
- Реализованы SSRF, MIME/magic-byte и size limits, private S3 immutable storage, private clamd freshness/deadlines, isolated renderer output bounds, retention, durable quotas и race-safe orphan deletion.
- FETCHED, CLEAN и наличие artifact не означают READY; downstream Brand Identity generation требует отдельного contract для approval/citations.
- Перед production activation проверить реальные storage/TLS/IAM/network/sandbox/signature-update infrastructure и provider permissions. Public OAuth linking/refresh и live product review остаются #32/#33; local fixtures не доказывают наличие live credentials/account approval.

#### Task 2.3 — AI Brand Identity

Status: **PARTIAL**, #114 completed в repository scope; #121 IN PROGRESS; #120 planned.

Уже реализовано: validated analysis port/attempts, local/test fake и production fail-closed;
manual Identity DRAFT create/read/list/edit, separate approval, immutable approved history,
edit-as-new-DRAFT, qualified analysis copy с original provenance; TEXT preview/admission,
current single snapshot и append-only withdrawal. Реализованные editable поля: palette,
fonts, voiceUp/voiceDown, sampleCaption. Current analysis использует brandName/industry,
не evidence. Отдельный `analyze-evidence` использует bounded reviewed TEXT inputs/citations;
Explicit evidence receipt → Identity copy/read/approval реализованы ADR0016/V35;
original citations сохраняются через lineage с actual access/source/withdrawal checks.
Themes/audience assumptions не объявляются реализованными Identity fields.

Оставшийся #121 выполняется законченными этапами:

1. **Versioned re-review: ограниченный lifecycle реализован.** ADR0014/V33 разрешают explicit
   confirmation нового source/Brand/Workspace version context только для неотозванной sourceVersion.
   Exact tuple replay409; previous receipts immutable;1000 reviews/evidence. Withdrawal любого
   receipt блокирует эту sourceVersion во всех contexts. Source editing API не добавлен.
   Regrant withdrawn sourceVersion остаётся отдельным неутверждённым product решением.
   Tests: stale/replay/source/parent changes, SQL/runtime guards, populated V32 upgrade и обе
   очередности review/withdrawal races. Фактическая qualification — в slice report выше.
2. **Bounded server-selected evidence input и validated citations: TEXT analysis → Identity реализован.**
   ADR0015/V34: отдельный action, 1–5 sources / 20000 UTF-8 bytes, immutable source links и exact
   quotes, actual OWNER/membership и source/withdrawal fencing. [Slice report](docs/issue-121-evidence-analysis-slice.md).
   ADR0016/V35: explicit Identity copy, bounded citation read, inherited source/withdrawal approval
   fence; [Identity slice](docs/issue-121-identity-evidence-slice.md). Общая цель этапа: сохранить
   конкретные admitted snapshots и generation source links, исключить client/model-selected
   foreign IDs. После external I/O повторно проверить permissions, source, withdrawal и lifecycle
   перед публикацией (publication fencing). Done: модель не может выдумать citation вне выбранного
   набора; negative tests foreign/missing/stale/withdrawn source, oversized inputs и concurrent
   revocation; old declared-only receipts не получают выдуманных citations.
3. **Non-TEXT eligibility — PARTIAL: PDF и URL consumers04 qualified.**
   ADR0017: scoped OWNER PDF_TEXT preview, original/latest CLEAN scans, physical bytes verification,
   fresh final access/version checks; no AI admission. [PDF slice](docs/issue-121-pdf-review-slice.md).
   ADR0018/V36: explicit PDF selectors/consent history, current physical snapshot, sourceVersion withdrawal;
   [PDF admission slice](docs/issue-121-pdf-admission-slice.md).
   ADR0019/V37: bounded TEXT/PDF_TEXT analysis, physical checks before model and publication,
   immutable sources/exact citations, current all-source access preflight; production adapter disabled.
   PDF Identity: exact immutable receipt links, physical bytes outside transactions, fresh locked copy/approval и inherited provenance (V38).
   Source policy02: [ADR0021](docs/adr/0021-source-kind-ai-eligibility.md) принят, пользователь явно
   утвердил P1–P3 2026-10-14; docs-only, runtime eligibility не изменена. Приняты URL/social text
   projection →03–05; images/screenshots остаются MVP gaps REQUIRES_PROVIDER (#120).
   Baseline02 social использовал generic TEXT admission;05 закрывает origin/binding downgrade
   до qualification. Raw bytes/CLEAN не разрешают AI; privacy/retention/live egress не утверждены.
   URL admission03: QUALIFIED; [ADR0022](docs/adr/0022-url-text-admission.md),
   [API](docs/url-evidence-admission-api.md), [report](docs/issue-121-url-admission-slice.md).
   V39: immutable acquisition provenance, exact private-byte/local extraction checks, explicit consent,
   current read/withdrawal и bounded versioned re-review; legacy не backfill. Full1137/0failures/errors,
   3Windows skips покрыты Linux35/35;Python10/10,renderer29/29,reports/startup PASS. На baseline03 URL consumption disabled.
   URL consumers04: QUALIFIED: mixed TEXT/PDF/URL generation,
   fieldPath citations/mixed-evidence-v2, Identity copy/direct+inherited approval, V40.
   Baseline04 `cbf585b8a57edeb41169ffc64594130fcf5ee7b5`;
   [ADR0023](docs/adr/0023-url-analysis-identity.md), [report](docs/issue-121-url-consumption-slice.md).
   Full1226/0failures/errors;3Windows skips покрыты Linux35/35;Python10/10,renderer29/29,
   mandatory reports/startup PASS;489 source hashes. Local commit `feat(evidence): qualify URL generation and Identity provenance`.
   Visual boundary05: [ADR0024](docs/adr/0024-visual-evidence-provider-boundary.md), exact future port/receipt contracts; REQUIRES_PROVIDER #120.
   Social05: QUALIFIED local acceptance boundary — typed immutable review/admission,
   current same-account binding, mixed generation, exact field citations и direct/inherited Identity
   guards; legacy generic downgrade закрыт без rewriting history. [ADR0025](docs/adr/0025-social-text-review-consumption.md),
   [report05](docs/issue-121-social-consumption-slice.md). Runtime V41; provider/OAuth/visual/privacy не обходятся fixtures.
   Final full1283/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29,
   required reports/startup PASS;506 source hashes/328 stable class hashes. Visual contracts commit `be6a03e`;
   social commit `feat(evidence): qualify social textual review and consumers`. No push/remote CI/closure.
   Remaining: visual provider/privacy qualification #120, live OAuth #32/#33, approved Identity→Strategy и остальные #121 outcomes.
   Реализовать оставшиеся consumers по утверждённой policy: exact
   source/artifact binding, latest acceptable CLEAN scan, storage/tombstone/lifecycle checks,
   bounded content и review policy. Done: отсутствующие/устаревшие scan/artifact либо неподдержанный
   тип дают safe отказ, cross-tenant/storage/scan races проверены. Не включать такие inputs
   автоматически по одному признаку FETCHED/CLEAN.
4. **Approved Identity → Strategy.** Выбирать конкретную APPROVED revision, сохранять immutable
   source ID/version; без допустимого source отказывать. Перед completion проверить source,
   permissions и lifecycle. Правило выбора среди нескольких approved revisions зафиксировать
   в ADR до implementation. Done: old Strategy history сохранена; отсутствие approved source,
   concurrent approval/edit/archive и revoked access проверены на PostgreSQL.

Whole-issue gate: API/OpenAPI/docs, migration/runtime constraints, negative/concurrency tests,
full Maven/Python/provider/Linux checks и startup для configuration/JPA/DI changes;
затем push, exact-source-SHA CI и синхронизация #107. Частичный slice не закрывает #121.
Contracts: [Identity](docs/brand-identity-api.md), [admission](docs/evidence-admission-api.md),
[ADR0012](docs/adr/0012-brand-identity-drafts.md), [ADR0013](docs/adr/0013-evidence-admission.md).

#### Task 2.4 — Durable AI Jobs (slices10–11 qualified locally; local draft A)

Отдельный scope под #107; существующая ingest queue не означает готовность AI Jobs.
Supported #121 inputs/source contracts01–06 qualified; closure07 не prerequisite по ADR0027.
ADR0028/draft A: slice10 реализует scoped submission/status/result, persisted source capture,
atomic claim/publication, lease/attempt fences и safe bounded recovery; qualification в
[отчёте10](docs/prompt10-durable-generation-report.md). Additive API уже описан в OpenAPI.
Slice11 добавляет completion provenance/SQL seals, safe codes/counters, bounded ready polling
и реальные child-JVM crash/restart scenarios. [Qualification11](docs/prompt11-recovery-report.md).
Local boundary A отделён от budget12 и live provider qualification120; remote issue не закрывался.

Done: 202 + Job Location для нового asynchronous contract; повтор worker delivery не публикует
две revisions, потерявший lease worker не пишет результат, restart/recovery/provider timeout
и membership/source revocation проверены. Использовать существующую queue где подходит,
не смешивая ingest status с approval и AI provenance.

#### Task 2.5 — AI budgets и request idempotency (planned follow-up, GitHub issue ещё не выделен)

ADR0028/draft B (задание12): реализован и qualified locally: full1439/0failures/errors,3Windows skips covered Linux35/35.
[ADR0030](docs/adr/0030-generation-budget-ledger.md), [report12](docs/prompt12-budget-report.md).
Продуктовые spend/retention значения не утверждены, live dispatch fail-closed без policy.
Реализовать tenant/account limits, concurrent calls, bounded request/output, cost reservation
и settlement, retry accounting и ambiguous provider outcomes. Клиентский idempotency key
связывается с actor/scope/operation/request digest; другой payload под тем же key отклоняется.
Новые async POST10 имеют minimal scoped request/key replay. Legacy synchronous POST и
approved-edit branching не получают implicit idempotency; общий durable budget enforcement
для sync/async реализован12. Fixture accounting явно SYNTHETIC, live policy/provider disabled.
Определить generation telemetry: measured latency/cost и moderation outcome с явным
unknown/not-run для отсутствующих данных; не backfill выдуманные показатели старым receipts.

Done: concurrency/retry не обходят quota, crash не освобождает расход ошибочно, повтор request
не создаёт duplicate publication в определённом contract, safe errors не раскрывают secrets.
Task 2.4/2.5 совместно необходимы перед production AI activation. #120 интегрирует эти guards
в реальные adapters; выбор provider/model/privacy/cost policy остаётся отдельным решением.

### Phase 3 — Strategy Playbook

Нумерация согласована с #107: Task 3.1 — generation #97, Task 3.2 — REST lifecycle #102.

#### Task 3.1 — Strategy generation application flow

Status: **DELIVERED в repository scope #97**. Validated port, short snapshot/completion
transactions, source/deadline checks, new DRAFT и generation provenance реализованы.
Current source — exact latest APPROVED Identity (qualified06/V42); generation synchronous, provider disabled.
Approved Identity binding delivered locally; Jobs — Task 2.4, budgets/idempotency — Task 2.5,
real adapters — #120. Эти ограничения не требуют повторно реализовать завершённый #97.

#### Task 3.2 — Strategy REST lifecycle и approval

Status: **DELIVERED в repository scope #102**. Generated create, scoped read/list,
full-content PATCH, expectedVersion, separate approval и approved/locked edit → новый DRAFT
с lineage реализованы. Root DELETE/LOCK API не заявлены; human review не нужно реализовывать
заново. Contract: [Strategy API](docs/strategy-api.md).

#### Task 3.3 — Полный Strategy Playbook (QUALIFIED LOCAL, draft P; GitHub issue ещё не выделен)

Legacy v1 fields: weighted contentPillars и cadence; новые complete v2 revisions включают full Playbook.
ADR0029/draft P (задание13): implementation и full qualification завершены локально.
[Отчёт13](docs/prompt13-playbook-report.md) фиксирует actual acceptance/gates.
Реализованы audience assumptions, channel goals/mix, tone spectrum, competitive whitespace
и four-week outline. Field semantics согласованы ADR0029: Strategy применяет voice Identity,
не создаёт вторую независимую Identity. Source — approved Identity по #121.

Done: versioned validated domain/DTO/provider contracts, migrations с сохранением legacy history,
review/edit/approval новых fields, schema/weights/timezone/channel bounds, malformed model output
и cross-tenant negatives; отсутствие новых полей в legacy не заменяется выдуманными данными.
Завершить contract перед Content generation, чтобы posts ссылались на определённый approved source.

### Phase 4 — Content workflow

#### Task 4.1 — Calendar и Post lifecycle

- Platform-specific draft posts из approved Strategy.
- Edit, field/all regeneration, approval, scheduling и drag-reschedule semantics.
- Post identity сохраняется; revisions содержат actor/time/provenance.

#### Task 4.2 — Media и export

- Object-storage/media-generation ports и format/size/moderation validation.
- Deterministic CSV export только approved posts.
- Сохранять checksum, Strategy/Post versions, creator и timestamp.
- Не вызывать social publishing APIs.

### Phase 5 — Identity и security

#### Task 5.1 — Clerk authentication

Status: Repository authentication реализована в #9; live Clerk setup/claims qualification отдельно.

- Validate issuer, signature, expiry и audience/authorized party.
- Provision local user из trusted claims; local authorization authoritative.
- Development token setup документировать без secrets в Git.

#### Task 5.2 — Workspace authorization и audit

- Deny cross-Workspace access во всех repositories/use cases/controllers.
- Least privilege для `OWNER`; permissions `EDITOR`/`VIEWER` решить до collaboration release.
- Audit sensitive changes; redact tokens, prompts и personal data.

### Phase 6 — Analytics и recommendations

#### Task 6.1 — Metric ingestion

- Начать с validated CSV/manual import; platform adapters позже.
- Idempotency по Workspace/platform/post/observation time.
- Raw provenance хранить отдельно от normalized metrics.

Execution19 QUALIFIED LOCAL: manual/CSV workflow реализован по [ADR0036](docs/adr/0036-manual-csv-metric-import.md),
[API/recovery](docs/metrics-import-api.md). Immutable raw human receipts, nullable interval counts,
stable ContentPost mapping, append-only corrections и dedicated fenced METRICS_IMPORT Jobs.
Full1897/0failures/errors; qualification — в [report19](docs/prompt19-metrics-import-report.md). Actual #99 также требует
live AnalyticsSyncService/account mapping/cursor/partial provider failure recovery; этот более
широкий scope остаётся OPEN. Provider origin не выдуман; analytics20 не реализуется здесь.

#### Task 6.2 — Performance analysis

- Explicit formulas для engagement/conversion measures.
- Сравнивать pillar/format/platform/time только при достаточном sample size.
- Явно показывать missing/stale data.

Execution20: [ADR0037](docs/adr/0037-performance-analytics.md), [API](docs/performance-api.md),
[report20](docs/prompt20-performance-report.md). Реализованы actual complete-pair weighted
percentages, DAY/PERIOD cohorts, explicit missing/zero/sparse/stale/coverage и immutable
human attribution к historical approved Post revision/Strategy pillar. Source history не
backfill/rewrite. Qualification PASS в report; #104 external closure только после whole-issue
policy/actual push+CI. #99 live sync/#120 adapters и21+ не реализовывались.

#### Task 6.3 — Recommendations и reports

Execution22: report workflow QUALIFIED LOCAL; [contract22](docs/prompt22-report-contract.md).
Report Job/JSON/HTML реализованы; qualification status в report22. External closure #105 отдельно.

Execution21: [ADR0038](docs/adr/0038-evidence-linked-recommendations.md), [API](docs/recommendations-api.md),
[report21](docs/prompt21-recommendations-report.md). #100 recommendations QUALIFIED LOCAL;
#105 recommendation API subset included, report22 remains separate. Legacy/history не backfill.

- Recommendation ссылается на supporting metrics и confidence.
- Apply recommendation открывает новый Strategy draft.
- Creator-facing report после утверждения contract и retention.

### Phase 7 — API и frontend

#### Task 7.1 — API contract

Существующие workflows реализованы в #111: reviewed OWNER-only OpenAPI, общие safe errors, bounded pagination, UTC instants и controller contract tests. Будущие feature APIs расширяют этот contract; несуществующие target routes не публикуются как доступные.

- `/api/v1` OpenAPI для Workspaces, Brands, Jobs, Brand Identity, Strategies, Posts, exports, metrics и recommendations.
- Standard pagination, idempotency, optimistic versioning и `application/problem+json`.
- Long-running action возвращает `202` и Job location.

#### Task 7.2 — Creator web application

- Сохранить foundation23 (`3b49c94`, ADR0040): Next.js/Clerk/Bearer, typed API, scoped CORS и tenant/session isolation.
- Visual follow-up23 (`5dbd15f`) и24 qualified locally: shared prototype shell + actual evidence/Identity workflow; [mapping/acceptance](docs/prototype-ui-adaptation-plan.md), [report24](docs/prompt24-identity-report.md).
- Следующий frontend шаг25, затем26–27; эти scopes и live Clerk/provider qualification остаются pending. Reference не создаёт новые roles, integrations или provider output.
- Onboarding, Brand Identity editor, Strategy Playbook, Content Calendar, export, analytics и recommendations.
- Typed API clients, accessibility, loading/empty/error states; никаких provider secrets.

### Phase 8 — Operations и release

#### Task 8.1 — Observability и security hardening

- Structured logs, correlation/Job IDs, metrics, traces, health/readiness, alerts и runbooks.
- Rate limits, timeouts/circuit breakers, secret rotation, scanning, backup/restore drill и abuse controls.

#### Task 8.2 — MVP validation

- Полный journey с real creators и measurable feedback.
- Проверить cross-tenant denial, provider outage recovery, accessibility, privacy и data export/deletion.
- Merge в `main` только после critical checks и acceptance journeys.

## 5. API: реализованный contract и будущие направления

Доступный contract: [OpenAPI](src/main/resources/openapi/cadence.json) и
[полный каталог 32 method/path](docs/project-review-2026-10-12.md).
Реализованы Workspace/Brand management, evidence registration/list/social, ingest Job read,
Brand analysis, Identity revisions/from-analysis/approval, Strategy revisions/approval,
TEXT admission/snapshot/withdrawal, health и OWNER-only OpenAPI.
Identity использует plural `/brands/{brandId}/identities` и `/identities/{identityId}`;
старый singular `/brands/{id}/identity` не является доступным route.

Будущие contracts, **не реализованные endpoints**: live AI policy/settlement administration (production activation, не public endpoint12),
Content/Post/calendar, exports, metric imports, analytics и recommendations.
Конкретные routes/body/status/idempotency/versioning определить в feature ADR/OpenAPI при
реализации; не публиковать speculative routes как готовые. Сейчас analysis/Strategy generation
synchronous; 202 + Job Location уже относится к evidence registration, не ко всем AI actions.

## 6. Tracked follow-ups

- GitHub Actions verification и Dependabot configuration сохранены в repository; required branch checks остаются внешним release gate. Точные executed results — в [verification](docs/verification.md).
- Docker обязателен для полного Maven/Python и provider integration suite; пропущенные provider tests не завершают full gate. Каждое изменение требует собственного результата.
- Проверить реализованные S3/clamd/renderer adapters на выбранной production infrastructure; live consent/scopes и OAuth lifecycle остаются #32/#33.
- #111 реализует API/OpenAPI contract существующих workflows; новые feature issues расширяют его своими endpoints. Текущая работа — оставшийся #121; порядок и отдельные follow-ups определены в разделе 8.

## 7. Data/security invariants

- UUID не является authorization evidence.
- Brand принадлежит ровно одному Workspace.
- Approved Brand Identity/Strategy revisions immutable.
- Целевое требование: remote/generative actions attributable, с явно определённым retry/idempotency contract. Async generation POST имеют scoped key/digest replay. Synchronous POST и approved-edit/new-DRAFT сохраняют прежний contract без implicit idempotency; generation routes используют общий budget gate12.
- Secrets/OAuth/JWT не попадают в logs или Git.
- По умолчанию archive; cascade deletion published history, metrics и audit запрещен.

## 8. Порядок продолжения и gates

1. #121 оставить OPEN: visual scope REQUIRES_PROVIDER и whole-issue closure pending.
   Approved Identity → Strategy уже qualified locally; не реализовывать повторно.
   Прямой image → AI adapter → validated Identity DRAFT утверждён ADR0027;
   отдельные OCR/visual observations не обязательны. Production остаётся fail-closed.
2. Scopes10–11 (Task 2.4, draft A) и12 (Task 2.5, draft B) qualified locally.
   Scope13 (Task 3.3, draft P) и14 Post lifecycle qualified locally. Scope15 также qualified locally;
   gates15 passed; 16 qualified locally; 17 qualified locally; следующий18 не начат.
   Exact issue drafts готовы; GitHub numbers A/B/P ещё не назначены.
3. Content/calendar/review и safe CSV export: #115/#98/#103; approved Strategy source,
   timezone/DST, versioned posts, formula-injection protection и export provenance.
4. Metrics/analytics #99/#104 → recommendations/reports #100/#105.
5. Завершить provider-independent backend security/operations/verification scopes
   #87/#118/#83/#8/#116: relevant/full tests, runtime/migrations/startup, audit и observability.
   Frontend #3 развивается по стабильным contracts, но не prerequisite выбора AI API.
6. После готовности остального MVP backend выбрать provider/model/privacy/retention/region/cost
   и реализовать AI adapters: Brand/Strategy #120, остальные по явно определённым scopes.
   Завершить visual admission/consumption/qualification #121, whole-issue gates, push/exact-SHA CI
   и только затем закрывать fulfilled issues. Изолированные fixtures не доказывают live readiness.
7. Live provider/Clerk/IAM/TLS/sandbox, backup/restore/load и полный creator E2E — отдельные
   обязательные activation/release gates; merge/deployment согласуются отдельно.

Для docs-only изменений: content/link/diff checks; не объявлять прежний Maven run новым.
Для implementation: compilation/Spotless/relevant/full suites, PostgreSQL и required provider
report gates без skips; Linux filesystem/renderer checks по scope. Docker обязателен для полного
suite. Local default H2/fake не подтверждает PostgreSQL integrity или production readiness.

Исторические статусы и прежние «следующие задачи» заменены этим текущим порядком.
Подробная история сохранена в [verification](docs/verification.md),
[#114](docs/issue-114-completion.md), [#97](docs/issue-97-completion.md),
[#102](docs/issue-102-completion.md), [Identity drafts](docs/issue-121-draft-slice.md),
[lifecycle](docs/issue-121-lifecycle-slice.md), [provenance](docs/issue-121-provenance-slice.md),
[TEXT review](docs/issue-121-text-admission-slice.md),
[withdrawal](docs/issue-121-withdrawal-slice.md). Исторические test runs относятся к своим revisions.

## Задание15 — Content generation / regeneration (2026-10-16)

QUALIFIED LOCAL: [report15](docs/prompt15-content-generation-report.md).
[ADR0032](docs/adr/0032-content-generation-jobs.md), [API](docs/content-generation-api.md).
Новый CONTENT kind использует existing durable Jobs/attempts/fencing/budgets и schema3 snapshots;
approved Strategy/Identity/evidence → requested slots → atomic reviewable DRAFT Posts.
Field/all regeneration сохраняет Post identity и append-only history. Production disabled; demo
fake изолирован. #98/#115 шире этого slice: scheduling/DST16, assets17 и export18 ещё не delivered.
10–14 не повторяются; задача16 qualified locally;17 не начато. #121/#120 остаются open, push/closure не выполнялись.

Qualification15: full1554/0failures/errors;3Windows skips covered Linux35/35; ContentPG25/25,
crash/restart8/8,Python10/10,renderer29/29,reports/coverage/startup PASS,SBOM194/OSV0findings.
16 qualified locally; 17 qualified locally; следующий18 не начат; #98/#115/#103 остаются open для wider scheduling/media/export acceptance.
