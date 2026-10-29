# MVP prompt progress

## V2 — расширенное ТЗ и новый план,2026-10-20

Уточнение2026-10-21: предварительное предпочтение Sol вместо Astra; модель приложения
не переключалась. [Claude frontend-only handoff](frontend-v2-claude-handoff.md) подготовлен
как инструкция, отдельный frontend repository/Claude permissions ещё не настроены.

Planning baseline `fe4159a`, `mvp`. Новый [план V2](astra-medium-mvp-prompts-v2.md),
[product scope](product-scope.md), [inventory всех00–30 и HTML](scope-v2-inventory.md).
Required direct publishing, generated video/raw editing, market/hashtag research,
regular reports/reminders. Organic only; £20 demo AI calls, прочие расходы отдельно.
Уточнение после `6f2306b`: один Workspace на аккаунт, несколько Brands. Single-workspace
migration/enforcement/onboarding в V2-01/03, проверки в V2-29/34, Brand UI в V2-30 —
**NOT_STARTED**. [Решение](adr/0042-single-workspace-per-account.md),
[docs-only отчёт](single-workspace-plan-report.md). Исторический Workspace manager не целевой UX.

| IDs | Scope | Статус |
| --- | --- | --- |
| V2-00 | Общий execution contract | Документирован, не implementation |
| V2-01–06 | Single Workspace/migration/contracts/auth/assets/real budgets/Identity | NOT_STARTED в новом scope |
| V2-07–11 | Research/Playbook/calendar/content briefs | NOT_STARTED |
| V2-12–19 | Media production/personalization/evaluation | NOT_STARTED |
| V2-20–28 | Publishing/sync/reports/reminders/privacy | NOT_STARTED |
| V2-29 | Backend acceptance | NOT_STARTED |
| V2-30–33 | Полностью новый frontend | NOT_STARTED |
| V2-34–35 | Live/cross-stack qualification/release | NOT_STARTED |
| X01–X06 | Training/PDF/audio/audience/billing/agency extensions | DECISION_REQUIRED |

Прежние local qualifications ниже сохраняются в своих scopes. Перенос в V2 не утверждает
providers/data policy и не выполняет runtime tasks. Read-only #107/#120/#121 OPEN;
remote sections ещё по старому scope. No push/remote edits/closure/deployment.
[Отчёт этого docs-only этапа](scope-v2-refresh-report.md).

**Ниже — исторические записи.** Их «следующий шаг» и CSV-only boundary заменены V2.

## Backend MVP до нового frontend — цель подтверждена,2026-10-20

Пользователь выбрал законченный backend с **реальным AI** перед полной переработкой UI.
[Сверка и план завершения](backend-mvp-completion.md), baseline `2f85d43`, `mvp`.
Backend09–22/28 и UI23–24 сохраняют historical local qualification;25–27 отложены.
Следующий шаг — provider/model/privacy/cost decision, затем real adapters/usage budgets,
visual #121, Content/recommendations/media и полный API acceptance/release candidate.
Manual/CSV metrics входят в MVP; billing/direct publishing вне scope.
Сейчас выполнено planning/code inspection, не live integration или новая full qualification.
GitHub #107/#120/#121 перечитаны read-only; OPEN. No push/issue edits/closure/merge/deployment.
Записи «следующий frontend25» ниже описывают прежний execution order.
Уточнение: £20 для демо; provider не выбран. [AI research](ai-adapters-discussion-2026-10-20.md)
— только рекомендации для обсуждения. Integration plan/model/data policy не утверждены.

## UI rework23–24 — QUALIFIED LOCAL,2026-10-20

Prerequisites: qualified01–06/23–24 + backend28; accepted Cream Orbit reference unchanged.
Public landing/shell, actual Clerk wiring, Workspace/Brand selection/create/read, evidence/Jobs,
exact review/consent/current/withdrawal/history и Identity DRAFT/edit/citations/separate approval
перенесены в `frontend/`. Guards/DTO/Bearer/source checks сохранены. Slice1 `edf2d76`;
final slice — local commit, содержащий этот report. [Report](ui-rework23-24-report.md).
58 unit +66 Chromium/axe PASS, lint/typecheck/build/export PASS. No backend changes/full Maven rerun.
Следующий frontend25→26→27; whole28/#3/#107/live Clerk/real adapters/physical-device gates open.
No push/remote CI/issue edits/merge/deployment. Дополнительная cinematic motion polish отложена.

<!-- backend28-execution:start -->
### Backend28 — QUALIFIED LOCAL (2026-10-19)

09–22 не повторяются. [Backend28 report](prompt28-backend-report.md) фиксирует actual
findings/fixes, security/audit/readiness/limits и fresh qualification. Backend pass не завершает
whole28: frontend/cross-stack28 **PENDING**, после23–27 и qualification изменённых adapters.
Следующий backend milestone по ADR0027 — отдельное решение AI API/model/privacy/cost и adapters;
этот шаг provider не выбирает. Принятый UI design и route rework23–24→25–27 сохраняются.
Issues #87/#83/#116/#118/#121 остаются OPEN до соответствующего whole-scope acceptance.
Local only: no push/new remote CI/issue closure/merge/deployment. Production readiness не заявляется.
| Entry28 | Actual status | Evidence / boundary |
| --- | --- | --- |
| Backend09–22 review /28 | QUALIFIED LOCAL | [Report](prompt28-backend-report.md); actual full/PG/renderer/Linux/startup/scans required |
| Frontend/cross-stack28 | PENDING |23–27 + qualified backend; changed UI/adapters/auth/cache/egress re-review; whole28 incomplete |
<!-- backend28-execution:end -->


### UI baseline принят / следующий scope backend28 —2026-10-19

На HEAD `aa240ae`, `mvp`, пользователь доволен Cream Orbit UI и откладывает ручную доработку
анимаций. [Prototype/design package](design/ui-rework/README.md) принят как визуальная основа;
дополнительные design variants/motion polish не требуются для перехода. Working frontend
ещё не перенесён; synthetic prototype не доказывает API/auth integration.
[Актуальный порядок](ui-rework-plan.md): backend28 →UI rework23–24 →25–27 →cross-stack28→29–30.
Read-only #107/#87/#118/#83/#116/#8 OPEN; remote tracking не изменён. Новых backend/frontend
tests в этом docs-only шаге нет; qualification28 и production readiness не заявляются.
Ниже сохранены исторические записи initial design review и его прежних pending состояний.

### UI rework / первый design review slice —2026-10-19

Baseline `5a71df6`, `mvp`, исходное дерево чистое. [Review package](design/ui-rework/README.md):
12 reference/current-public browser captures, статический inspection исходного prototype,
три independent ImageGen направления, navigation/component proposal, actual route/state
mapping и [backend backlog](design/ui-rework/backend-backlog.md). Рекомендация Cream Orbit;
выбор пользователя pending. Detailed desktop/mobile, finalized system и отдельный Sites
prototype — следующий этап после выбора, не delivered application. Рабочие23–24/backend
не изменены;09–22 не повторяются. Actual read-only #3/#107 OPEN; remote snapshot устарел.
Проверки и ограничения: [отчёт](design/ui-rework/review-report.md). Local commit после checks;
push/CI/issue edits/closure/merge/deploy не выполняются. Номера00–30 и route backend28 сохранены.

### UI rework request / планирование —2026-10-19

Actual baseline `ce76192`, `mvp`, clean tree. После manual smoke пользователь запросил
существенную переработку web UI и корректировку дизайна. [План](ui-rework-plan.md):
UX/UI design review сейчас →backend28 →rework23–24 →25–27; frontend28→29–30 после implementation.
Ветка backend28→AI integration остаётся независимой по ADR0027. Новый дизайн ещё не выбран;
rework/backend28/25–27 не объявлены delivered,23–24 historical qualification сохранена.
Local Clerk development setup и OWNER metadata настроены; user-reported manual smoke
не заменяет full live auth E2E или production qualification. Docs-only, no product changes.
Actual #3/#107 перечитаны; remote bodies пока содержат прежний prototype mapping.
Новые GitHub edits/push/CI/closure/merge/deploy этим шагом не выполняются.

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
### Visual follow-up23 — QUALIFIED LOCAL (2026-10-18)

Baseline clean `14805a5`, reference fingerprint совпал. [Report](prompt23-visual-report.md).
Существующий prototype перенесён в shell/actual Workspace/Brand UI; Cream/Dark/Sage,
licensed local fonts, accessible landmarks/keyboard/focus и existing guards сохранены.
Actual35unit/33Chromium+axe, lint/format/typecheck/build, peers/audit/export isolation PASS.
Live Clerk gate fails closed с explicit missing-credentials blocker; это не passing live login.
Backend/auth/API/lockfile unchanged, full Maven не запускался; Spotless check PASS.
Следующий frontend scope24;24–27 не реализованы, #3 open, no push/new CI/closure/deployment.

### Remote issue synchronization (2026-10-18)

По отдельной явной просьбе пользователя обновлены GitHub #3/#107/#121/#120/#115/#103/
#98/#99/#104/#100/#105; exact body/state/labels/title/assignees/milestone read-back PASS11/11.
[Отчёт и mapping](issue-tidy-2026-10-18.md). Current sections отделены от истории;
foundation23 local и pending visual follow-up23→24–27 отражены в #3/#107.
Remote `mvp` всё ещё `a283382c`, local baseline этого шага `db11175`; no push/new CI/closure.
Ниже сохранены исторические записи прежних scopes, включая их тогдашнее «no issue edits».


### План UI уточнён по решению пользователя (2026-10-18)

Baseline clean `3b49c94`; выбран существующий `Cadence Prototype.html` как основной visual
reference. [План, fingerprint и UI→API mapping](prototype-ui-adaptation-plan.md).
Foundation23 остаётся QUALIFIED LOCAL; **visual follow-up23 PENDING** и является следующим
frontend шагом перед24–27. Эти scopes не реализованы данным docs-only изменением.
ADR0040/Bearer/Clerk/CORS/tenant guards сохраняются; backend28/AI route независим.
Новые product tests не запускались; historical23 gates не считаются новым verification.
No push/remote CI/issue edits/closure/deployment.


### Execution note23 (2026-10-18)

Baseline clean `15ad3ec`; actual #3/#107 checked. **23 QUALIFIED LOCAL repository scope; live Clerk blocked.**
Frontend35unit/16Chromium+axe,lint/type/build/peers PASS; npm263/0vulns.
Full2270/0failures/errors,4Windows filesystem skips покрыты Linux37/37;2266 executed PASS, 181 suites. Python10/renderer29/startup/reports PASS; freshMaven194/OSV0.
Prior existing metrics-import SQL guard rejection не воспроизведён, precise cause unconfirmed; report23.
[report23](prompt23-frontend-report.md), [ADR0040](adr/0040-frontend-bearer-onboarding.md),
[setup](frontend-foundation.md). Static Next.js/React/TypeScript + official Clerk React SDK,
explicit Bearer, scoped opt-in CORS, typed bounded API, Workspace/Brand onboarding.
Missing existing Clerk keys/test user blocks actual signup/login; no production auth fixture.
#3 remains open for24–27/live gate; no push/remote CI/closure/deployment.

### Execution note22 (2026-10-17)

Baseline clean5f3e506; actual #105/#107 reread. Approved ADR0039 preserved.
**22 QUALIFIED LOCAL**:
Full2250/0 failures/errors,4 Windows filesystem skips covered Linux37/37; report H266/66,PG76/76,migration1/1;Python10/10,renderer29/29,reports/Spotless/JaCoCo/startup PASS;freshSBOM194/OSV0;743 stable source/build/config hashes. [report22](prompt22-reports-report.md),
[API](creator-reports-api.md), [ADR0039](adr/0039-creator-performance-reports.md).
Durable report Job, private HTML/JSON, immutable normalized capture/original recommendation
support, bounded retention/expiry/lease guards/V55/runtime permissions. No AI/provider call.
No push/remote writes/new CI/closure/deployment;23 не начинался. Actual results в report22.

### Execution note21 (2026-10-17)

Prerequisite13/19/20 actual clean dabc367; #100/#105/#107 checked. **21 QUALIFIED LOCAL**.
Full2104/0 failures/errors,4 Windows filesystem skips covered Linux37/37; PG61/61,migration1/1;Python10/10,renderer29/29,reports/Spotless/JaCoCo/startup PASS;freshSBOM194/OSV0;722 stable source/build hashes. [report21](prompt21-recommendations-report.md),
[ADR0038](adr/0038-evidence-linked-recommendations.md), [API](recommendations-api.md).
HEURISTIC/raw evidence separation, exact captured sources, durable key binding, apply→DRAFT,
no legacy/provider/causal backfill. Report22/live adapter separate;22+ не начаты.
No push/remote edits/CI/closure/merge/deployment.

### Execution note20 (2026-10-16)

Prerequisite19 actual clean27ca07c; #104/#107 checked. 20 QUALIFIED LOCAL (2026-10-17).
Full1984/0 failures/errors,4 Windows skips covered Linux37/37; PG39/39,migration1/1,JDBC timeout3/3;Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;694 stable source/build hashes.
[report20](prompt20-performance-report.md), [ADR0037](adr/0037-performance-analytics.md),
[performance API](performance-api.md). Weighted actual rates/exact DAY/PERIOD cohorts,
immutable human attribution/history и bounded normalized scalar queries. No source backfill,
provider payloads/AI calls. #99 live sync/#120/#121 remaining не закрыты;21+ не начаты.
No push/remote edits/CI/closure/merge/deployment.

### Execution note19 (2026-10-16)

Prerequisite14–18 actual clean9d3a60a; #99/#107 checked.19 QUALIFIED LOCAL.
[report19](prompt19-metrics-import-report.md), [ADR0036](adr/0036-manual-csv-metric-import.md),
[metrics API](metrics-import-api.md). Manual/CSV human-origin imports, immutable raw receipts,
nullable interval counts/correction history and dedicated fenced Job publication. Broader #99
live synchronization remains OPEN;20+ не начаты; no push/external edits/closure/merge/deploy.
Full1897/0failures/errors,4Windows skips covered Linux37/37;MetricsPG45/45,H2API32/32,
parser6/domain3/migration1;Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;
freshSBOM194/OSV0;671stable source hashes/560compiled resources.

Актуализация 2026-10-15: [ADR0027](adr/0027-backend-first-ai-adapters-last.md) утверждает backend-first, AI adapters last
и прямой image → AI → Identity без обязательного отдельного OCR/interpretation stage.
#121 остаётся OPEN; visual gap не отменён и не блокирует независимые backend scopes.


| Номер | Dependencies / baseline | Actual completed scope | Commit / report / checks | Remaining / blockers |
| --- | --- | --- | --- | --- |
| 01 — QUALIFIED | ADR0019/V37; HEAD `71cafd6b93ac5d097984e39390c12e78f2d3fcd4`; actual #121/#107 сверены | PDF Identity copy/read/direct+inherited approval; exact provenance; V38; physical/fresh metadata/access fences | Local commit `feat(identity): qualify PDF evidence copy and approval` (commit этой строки: `git log -1 -- docs/mvp-prompt-progress.md`); [отчёт](issue-121-pdf-identity-slice.md); isolated full1079/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29,mandatory reports/startup PASS | Scope01 без blockers. Whole #121 open; push/CI после whole issue. Other source policy/approved Identity→Strategy и02+ не выполнялись |

Qualification привязана к464 совпавшим source/build SHA-256. Первый root full gate провалился
на несовпадении JaCoCo class IDs после внешней перезаписи bytecode; final isolated run прошёл
с неизменными302 compiled class hashes. Исторические tests не объявляются новым выполнением.
Для01 остальные номера не выполнялись. Ledger не считает создание файла или partial tests завершением.

| Номер | Dependencies / baseline | Actual completed scope | Commit / report / checks | Remaining / blockers |
| --- | --- | --- | --- | --- |
| 02 — QUALIFIED (architecture/product decision) | 01 qualified; HEAD `4f4faab9d1cd477f95d075072e7810ee9f70a3d8`; #121/#107/#120 прочитаны | ADR0021 ACCEPTED: пользователь явно утвердил P1–P3 2026-10-14; exact representations/provenance, review/withdrawal/freshness/retention, social TEXT gap, contracts/threat model и03–05 dependencies | Local commit `docs(evidence): approve source-kind AI eligibility policy`; [отчёт](issue-121-source-policy-decision.md); docs-only: 6 files/110 local links PASS, scope/content/diff checks PASS; runtime tests не запускались | Scope02 без blockers; реализации03–05 не начаты. Visual inputs REQUIRES_PROVIDER; privacy/retention/live egress #120 не утверждены. Whole #121 open |

| 03 — QUALIFIED | 02 ACCEPTED; HEAD `54a0c3fe1f54f705eeebd65408f93e89426d4a3c`; #121/#107 сверены | V39 immutable URL acquisition snapshot; explicit review/admission/current read/shared withdrawal; sourceVersion-wide revoke, cap1000, exact private bytes + local website-v2 checks | [отчёт](issue-121-url-admission-slice.md); local commit `feat(evidence): qualify URL textual review and admission`; targeted76/76; final full1137/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29,mandatory reports/startup PASS;481 source/build hashes | Scope03 без blockers;04/05 не выполнялись; URL AI consumption disabled |

| Номер | Dependencies / baseline | Actual completed scope | Commit / report / checks | Remaining / blockers |
| --- | --- | --- | --- | --- |
| 04 — QUALIFIED | 03 qualified; HEAD `cbf585b8a57edeb41169ffc64594130fcf5ee7b5`; #121/#107 сверены | Mixed TEXT/PDF/URL generation; field-path citations; preserved legacy digests; Identity copy/direct+inherited approval; V40/runtime guards | [отчёт](issue-121-url-consumption-slice.md); local commit `feat(evidence): qualify URL generation and Identity provenance`; targeted96/96;full1226/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29;mandatory reports/startup PASS;489 source hashes | Scope04 без blockers;05 не выполнялся; live provider/privacy #120 отдельно |

| 05 — VISUAL CONTRACT SLICE (completed) | 02 accepted; consumers01/04 qualified; HEAD `449735b` | Visual contracts/blocker: [ADR0024](adr/0024-visual-evidence-provider-boundary.md); existing bytes/screenshots не являются OCR/vision | Docs-only slice: links/fences/diff checks; Maven не запускался | Social implementation описан ниже; visual/provider/privacy #120 blocked; whole #121 open |

| 05 — QUALIFIED LOCAL BOUNDARY; VISUAL BLOCKED | 02 accepted; consumers01/04 qualified; visual boundary commit `be6a03e` | Typed Instagram/TikTok text review/consent/withdrawal, same-account current binding, mixed generation, Identity direct/inherited guards, V41, legacy downgrade closure | [report05](issue-121-social-consumption-slice.md), [ADR0025](adr/0025-social-text-review-consumption.md); local commit `feat(evidence): qualify social textual review and consumers`;final full1283/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29;required reports/startup PASS;506 source hashes/328 class hashes | Visual MVP gap REQUIRES_PROVIDER #120; live OAuth #32/#33 отдельно. Whole #121 open; другие номера не начаты |

| 06 — QUALIFIED LOCAL | 01–05 local supported scope; baseline `e5c4e34`; actual #121/#107 read | Latest APPROVED Identity source, exact immutable audit/analysis link, pre-egress/completion evidence/physical/access guards; V42 | [report06](issue-121-approved-strategy-slice.md), [ADR0026](adr/0026-approved-identity-strategy-source.md); local commit `feat(strategy): bind generation to approved Identity revision`; full1309/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29;mandatory reports/startup PASS;509 source hashes/329 class hashes | Real adapters/privacy/visual #120, live OAuth #32/#33 и whole-issue push/exact-SHA CI отдельно; другие номера не начаты |

| 07 — REVIEWED; CLOSURE BLOCKED | 06 qualified; actual HEAD `c19abc0`; #121/#107/#120 read2026-10-15 | Whole-issue acceptance matrix; current code/API/ADR/tests inspection; obsolete Identity API claims fixed;509 historical qualified source hashes matched | [review07](issue-121-whole-review-2026-10-15.md), [external drafts](issue-121-review-github-drafts.md); docs-only local commit `docs(review): record issue 121 acceptance and visual blocker`; report gates revalidated, no new full run | ADR0021/P2 visual scope not delivered; REQUIRES_PROVIDER is not closure. No push/new CI/remote update/issue closure; whole #121 open |


| Номер | Dependencies / baseline | Actual completed scope | Commit / report / checks | Remaining / blockers |
| --- | --- | --- | --- | --- |
| 09 — QUALIFIED DESIGN | supported01–06; ADR0027; HEAD f2f60c9; GitHub discovery15 октября | ADR0028 async source/lease/idempotency/accounting; ADR0029 Playbook v2; separate exact issue drafts A/B/P; mapping10–13 | [report09](prompt09-contracts-report.md), [drafts](ai-workflow-issue-drafts.md); docs-only checks и Spotless; local commit `docs(architecture): define AI workflow and Playbook follow-ups` | Implementation10–13 pending; next10. Live quotas/pricing/retention/provider pending activation, не blocker neutral design. Remote issues не созданы; #121 open |

| 10 — QUALIFIED LOCAL | 09/ADR0028; pushed baseline ff29281; supported01–06 | Durable operation/attempt/source snapshot;202/Job/result; atomic publication; exact lease/access/physical checks; V43; preserved sync contracts | [report10](prompt10-durable-generation-report.md), [API/recovery](durable-ai-generation.md); full1381/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29;mandatory reports/Spotless/coverage/startup PASS;local commit `feat(ai): add durable fenced generation jobs` | Remaining11/12/13. Baseline push authorized explicitly; CI verify success/security failure. Spring migration отдельно после10 по выбору пользователя; production disabled; #121 open |


| Spring migration — QUALIFIED LOCAL (между10 и11) | User request после10; parent4833e92 | Boot4.0.8/Spring7.0.9, Tomcat11.0.27/Jackson security patches; JSON/history compatibility; fixed ProblemDetail/Bearer/discovery contracts; actual11 readiness | [migration report](spring4-migration-report.md), [handoff11](prompt11-readiness.md); full1385/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29;reports/Spotless/JaCoCo/startup PASS;OSV194/0findings;local commit `chore(platform): qualify Spring Boot 4 migration` | Следующий11 ещё не реализован. Jackson2 compatibility module deprecated: отдельный последующий migration scope; не блокирует11. Новый push/remote CI не выполнены, #121 open, production adapters disabled |

| 11 — QUALIFIED LOCAL | 10 + Spring migration a21e99f; actual #107/#120/#121 checked16 октября | V44 attempt completion/history seals, safe Job codes, ready discovery/bounded poller, correlation/JMX counters, real JVM crash/restart, overload/revocation/SQL races | [report11](prompt11-recovery-report.md), [runbook](ai-jobs-recovery-runbook.md); targeted79/79 + inventory8/8; final full1405/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29,mandatory reports/coverage/startup PASS;fresh OSV validated against current SBOM194/0findings;533 stable source/build hashes;local commit `feat(ai): qualify generation recovery and observability` | Scope A10–11 delivered locally. Next12 budgets/idempotency и13 Playbook не начаты; remote issue A не назначен. No push/new remote CI/closure; #121/#120 open, production adapters disabled |

| 12 — QUALIFIED LOCAL | 09–11; actual clean c8ee4e7; #107 read16 октября | V45 durable shared sync/async ledger, scoped replay, exact numeric policy capture, held unknown/settlement/overrun/caps | [report12](prompt12-budget-report.md), [ADR0030](adr/0030-generation-budget-ledger.md); targeted273/273; full1439/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29;reports/coverage/startup PASS;current SBOM194/OSV0findings;543 stable hashes | Draft B delivered locally; next13 not started; production/live accounting disabled; no push/closure |

| 13 — QUALIFIED LOCAL | 06/09–12; clean8c1bee7; #107 checked16 октября | Complete v2 Playbook, V46 normalized owned values, strict domain/provider/DTO, human edit/history/legacy, async schema2 | [report13](prompt13-playbook-report.md), [ADR0029](adr/0029-full-playbook-contract.md); targeted161/161 + reorder40/40 + remediation11/11; full1457/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29,reports/coverage/startup PASS;current SBOM194/OSV0 findings;554 stable hashes | Local draft P delivered; local commit `feat(strategy): qualify complete versioned Playbook`; no push/CI/closure;14 not started; real adapters disabled |

Последующее explicit user authorization:10–13 + Spring migration pushed в `a283382c8191bfa4020d0fb3c141493cabb9f821`;
[push CI](https://github.com/Sanyoghk/Cadence/actions/runs/38846267446) и
[PR CI](https://github.com/Sanyoghk/Cadence/actions/runs/38846270484): verify/dependency-security SUCCESS.
Это historical baseline14; прежние no-push statements отражали состояние до этой отправки.

| 14 — QUALIFIED LOCAL | prerequisite13; clean a283382; actual #107/#115/#103 checked16 октября | Stable Post identity/revisions, exact approved Strategy source, manual platform content/edit/approval, V47 legacy import, strict DTO и SQL/runtime guards | [report14](prompt14-post-revisions-report.md), [ADR0031](adr/0031-post-revision-lifecycle.md), [API](post-revisions-api.md); targeted57/57 after tag-only SQL regression; full1514/0failures/errors,3Windows skips covered Linux35/35;Python10/10,renderer29/29,reports/coverage/startup PASS;SBOM194/OSV0findings;576 stable hashes; local commit `feat(content): qualify platform-specific post revisions` | #115/#103 broader scopes15–18 open; no push14/closure;15 не начат |

| 15 — QUALIFIED LOCAL |10–14; clean2c8a2b6; actual #98/#115/#107 checked16 октября | CONTENT durable Jobs/schema3, exact Strategy/Identity/evidence lifetime, bounded slots/output, atomic batch, field/all regeneration with immutable lineage, V48/runtime | [report15](prompt15-content-generation-report.md), [ADR0032](adr/0032-content-generation-jobs.md), [API](content-generation-api.md); targeted61/61 +compat26/26 +cross-owner3/3; finalfull1554/0failures/errors,3Windows skips covered Linux35/35;ContentPG25/25,crash8/8;Python10/10,renderer29/29,reports/coverage/startup PASS;currentSBOM194/OSV0;592stable hashes;localcommit `feat(content): qualify durable generation and regeneration` | #98/#115 remain open for16–18; real adapters disabled; no push/closure;16 не начато |


| Номер | Dependencies / baseline | Actual completion | Evidence | Remaining scope |
| --- | --- | --- | --- | --- |
|16 — QUALIFIED LOCAL|14–15; clean c0e5473; actual #107/#115/#103 checked16 октября|V49 append-only scheduling, dual versions, exact approved revision/checksum, explicit DST/UTC policy, atomic batches/calendar/history, SQL/runtime guards|[report16](prompt16-calendar-report.md), [ADR0033](adr/0033-calendar-scheduling.md), [API](calendar-api.md); targeted95/95+final4/4;full1638/0failures/errors,3Windows skips covered Linux35/35;CalendarPG45/45,ContentPG25/25,crash8/8;Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;605stable hashes;localcommit `feat(content): qualify versioned calendar scheduling`|17 не начат; #115 assets17/#103 export18 remain open; no push/CI/closure; live adapters disabled|


| Номер | Dependencies / baseline | Actual completion | Evidence | Remaining scope |
| --- | --- | --- | --- | --- |
|17 — QUALIFIED LOCAL|14–15; clean f029531; actual #98/#107 checked16 октября|Private retained demo PNG/MP4, staged scan/human review, exact revision refs, bounded scoped delivery, V50/SQL/runtime/IAM, replacement/archive/revocation fencing|[report17](prompt17-media-report.md), [manifest](prompt17-changed-files.md), [ADR0034](adr/0034-private-demo-media.md), [API](media-assets-api.md);targeted147/0failures/errors/4Windows skips,compat163/163,final9/9,carousel red→green49;full1731/0failures/errors,4Windows skips covered Linux37/37;MediaPG49/49,ContentPG25/25,crash8/8;Python10/10,renderer29/29,reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;632 stable hashes;localcommit `feat(content): qualify private demo media lifecycle`|18 не начат; live producer/general decoder/privacy/cost/deletion qualification separate; no push/CI/closure; prod demo actions disabled|


### Execution note18 (2026-10-16)

Prerequisite14–17 actual clean abaeeb1; #103/#107 checked.18 QUALIFIED LOCAL.
[report18](prompt18-csv-export-report.md), [ADR0035](adr/0035-durable-csv-export.md), [CSV API](csv-export-api.md).
Durable approved immutable CSV batch, current private download, no provider/publishing changes.
Full1810/0failures/errors,4Windows skips covered Linux37/37;CSV PG43/43,HTTP24/24,domain11/11,migration1/1;AI child-JVM8/8. Python10/10,renderer29/29,mandatory reports/Spotless/coverage/startup PASS;freshSBOM194/OSV0;651 stable source/build hashes.
19 не начато. No push/external issue edits/closure/merge/deployment.
