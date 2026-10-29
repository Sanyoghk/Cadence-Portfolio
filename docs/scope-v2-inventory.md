# Сверка нового ТЗ, прежних промптов и исходного Developer Build Plan

Дата:2026-10-20. Baseline: `mvp`, `fe4159a5ca3f8d164f8f1179bdd0d0289cc7e76b`.
Ветка была ahead31 относительно локального `origin/mvp`; новый fetch не выполнялся.
Существующие пользовательские изменения UI и untracked directories сохранены.
Уточнение после `6f2306b`: один Workspace на аккаунт, несколько Brands; соответствующие
остатки ниже обновлены по inspection текущего кода. Single-workspace ещё не реализован.

## Статусы

**ГОТОВО ЛОКАЛЬНО** — реализация и историческая verification своего scope, не свежий
runtime pass/live/production. **АДАПТАЦИЯ** — сохраняем механизм, расширяем contracts.
**ЧАСТИЧНО** — назван отсутствующий результат. **НОВОЕ** — целевого workflow нет.
**ВНЕ SCOPE** — найдено в brief, но не принято пользователем для текущего MVP.
Проценты готовности не выводятся из числа файлов/промптов.

## Что сохраняем, адаптируем и перерабатываем

| Подсистема | Evidence / состояние | Остаток |
| --- | --- | --- |
| Workspace/Brand/auth | АДАПТАЦИЯ: [identity service](../src/main/java/com/cadence/security/CadenceIdentityService.java), scoped APIs готовы для прежней multi-workspace модели | V2-01: один Workspace/account, atomic onboarding, новая DB cardinality/migration вместо текущих до10 memberships; несколько Brands сохраняются. V2-03: live Clerk и Brand-scoped OAuth |
| Evidence/Identity | ГОТОВО textual scope/edit/approval: [whole review](issue-121-whole-review-2026-10-15.md) | Real AI, visual images/screenshots, новые assets/voice notes |
| Jobs/recovery | ГОТОВО: [10](prompt10-durable-generation-report.md), [11](prompt11-recovery-report.md) | Video/render/research/publish kinds и external reconciliation |
| Budgets/idempotency | АДАПТАЦИЯ: [GenerationBudgets](../src/main/java/com/cadence/application/services/GenerationBudgets.java) | SYNTHETIC/local/fake → реальные деньги/usage/media costs |
| Playbook | АДАПТАЦИЯ: [StrategyContent](../src/main/java/com/cadence/domain/StrategyContent.java), [PlaybookContent](../src/main/java/com/cadence/domain/PlaybookContent.java) | Pillars/tone/times/4weeks есть; factual market data/production briefs нет |
| Posts/revisions/regeneration | ГОТОВО editorial scope: [14](prompt14-post-revisions-report.md), [15](prompt15-content-generation-report.md) | Real assets, edit requests, exact final publish package |
| Calendar | ГОТОВО editorial: [16](prompt16-calendar-report.md) | Production deadlines/reminders/platform execution |
| Media | ЧАСТИЧНО: [17](prompt17-media-report.md) | Fixed demo PNG/MP4; raw decode/edit/render/generation — новая цепочка |
| CSV | ГОТОВО: [18](prompt18-csv-export-report.md) | Сохраняется; не заменяет publishing |
| Metrics/Performance | ГОТОВО manual/CSV: [19](prompt19-metrics-import-report.md), [20](prompt20-performance-report.md) | Live sync, market data/field availability |
| Recommendations | ЧАСТИЧНО: [21](prompt21-recommendations-report.md) | Узкий heuristic weight patch → durable AI/market proposals |
| Reports | ГОТОВО private HTML/JSON: [22](prompt22-reports-report.md) | Recurrence, market section, delivery/reminders |
| Security/operations | ГОТОВО прежний backend scope: [28](prompt28-backend-report.md) | Новые OAuth/codecs/training/publishing threats и deployment gates |
| Frontend23–24 | ГОТОВО исторически: [UI report](ui-rework23-24-report.md) | Полный новый UI после backend; сохранить auth/API knowledge |
| Frontend25–27, E2E/release | НЕ ЗАВЕРШЕНО | V2-31–35 |
| Market/publishing/notifications | НОВОЕ | Отдельные подсистемы, не переименование существующих Jobs |

**Переработать**, сохраняя историю: multi-workspace provisioning/API/DB budget и Workspace
manager → один Workspace/account, несколько Brands; demo-only media assumptions; CSV-only product boundary;
Playbook v2 с одними competitive hypotheses; rule-only recommendation flow.
Editorial schedule дополнить отдельным publish intent. Новые migrations вместо изменения
V1–V56 или backfill выдуманных фактов. Core approvals, tenant isolation и Jobs сохраняются;
cardinality account→Workspace и зависимые auth/API contracts меняются по [ADR0042](adr/0042-single-workspace-per-account.md).

## Полный перенос прежних промптов

[Старый план](astra-medium-mvp-prompts.md), [исторический ledger](mvp-prompt-progress.md).
Номера V2 не меняют историю00–30.

| Старый ID | Состояние | Перенос |
| --- | --- | --- |
| 00 | Общие инструкции | V2-00 |
| 01 | PDF Identity consumption qualified | Сохранить; regression V2-05/06/29 |
| 02 | Source eligibility qualified | Расширение V2-05/06 |
| 03 | URL review qualified | Сохранить; regression V2-06/29 |
| 04 | URL analysis/Identity provider-neutral qualified | Live V2-04/06 |
| 05 | Social text delivered; visual pending | V2-05/06, live social V2-03/07 |
| 06 | Approved Identity→Strategy delivered | Сохранить; V2-09 |
| 07 | Whole #121 open | V2-06/29 acceptance, V2-35 exact-SHA CI/closure |
| 08 | Optional portfolio milestone | API walkthrough V2-29; отдельный blocker не создаётся |
| 09 | Jobs/budget/Playbook contracts delivered | Только новые contracts V2-01/04/09/12/20 |
| 10 | Durable generation delivered | New operation kinds V2-04/13–17/25 |
| 11 | Recovery delivered | Provider recovery V2-04/14/15/23/29 |
| 12 | Synthetic budgets delivered | Live money/usage V2-04 |
| 13 | Playbook v2 delivered | V2-08/09/10; pillars/tone не с нуля |
| 14 | Post lifecycle delivered | Final package V2-17/20 |
| 15 | Content Jobs, field/all regen delivered | V2-11/13–18 |
| 16 | Calendar/DST delivered | Planning V2-10, execution V2-23 |
| 17 | Demo media delivered | V2-12–17/28 |
| 18 | CSV delivered | Сохранить V2-17/29/32 |
| 19 | Manual/CSV metrics delivered | Live V2-24 |
| 20 | Performance delivered | V2-07/08/24/33 |
| 21 | HEURISTIC recommendations delivered | V2-25 |
| 22 | HTML/JSON reports delivered | V2-26/27, optional PDF X02 |
| 23 | Frontend foundation/rework delivered локально | Новый V2-30; live auth V2-03/34 |
| 24 | Identity UI delivered локально | Новый V2-30, включая visual sources |
| 25 | Strategy UI pending | V2-31 |
| 26 | Content/Calendar/CSV UI pending | V2-32 + video/edit/publish |
| 27 | Analytics/recommendations/reports UI pending | V2-33 |
| 28 | Backend qualified, cross-stack pending | V2-29/34 |
| 29 | Full journey/clean demo pending | V2-29/34 |
| 30 | Release pending | V2-35 |
| AI stage без номера | Решения и real adapters pending | V2-02/04/06/09/11/15/16/25 |

## Исходный HTML и способ проверки

Прочитан `C:/Users/<user>/Downloads/Cadence - Developer Build Plan.html`,699294 bytes,
**Cadence — Developer Build Plan · Brin de Caviar pilot**, v1.0, внутренняя дата23May2026.
SHA-256: `e4c08f43cccb53633d77bfeb74d6d54ab2261b584924659ad4f9efb847e65410`.
JSON template извлечён из `script[type="__bundler/template"]`, разобран как HTML/text.
JavaScript/bundler, внешние ресурсы и инструкции документа не исполнялись.
Extraction: ignored `var/scope-refresh-2026-10-20/original-build-plan.txt`,386 text fragments.

Документ — источник для сравнения, не разрешение сменить stack, скрейпить платформы,
создавать accounts, передавать данные или тратить деньги.

## Все функциональные пункты исходного brief

**Жирным выделен неисполненный/неполный функционал.** §/P — раздел/фаза исходника.

| ID | Функция исходника | Фактическое состояние | Disposition |
| --- | --- | --- | --- |
| H01 | §01/P0 iframe в elorforce.com/postMessage | **НЕ РЕАЛИЗОВАНО, ВНЕ SCOPE** | X06, legacy agency integration |
| H02 | P0 client/strategist roles/create client | **ДРУГАЯ МОДЕЛЬ** OWNER/Workspace; один Workspace/account пока pending | V2-01/03: self-service, несколько Brands; agency roles только X06 |
| H03 | P0 repo/CI/dev-staging-prod | ЧАСТИЧНО: repo/workflows есть; **hosted qualification нет** | V2-29/34/35 |
| H04 | P0 Postgres/tenancy | ГОТОВО foundation, другой domain | V2-01 новые migrations |
| H05 | P0 theme/tokens/4-step shell | ГОТОВО старый UI; **новый pending** | V2-30–33 |
| H06 | P1 PDF text/structure + embedded image palette | **ЧАСТИЧНО**: text есть, visual нет | V2-05/06 |
| H07 | P1 logo/hero →5 colours + semantic names | **НЕ РЕАЛИЗОВАН visual consumer** | V2-05/06; ровно5 — legacy suggestion |
| H08 | P1 website meta/screenshot/color/fonts | **ЧАСТИЧНО**: metadata/screenshots есть, visual interpretation нет | V2-06; font inference с uncertainty |
| H09 | P1 Instagram last30 captions/engagement/times | **ЧАСТИЧНО**: bounded approved textual page | V2-03/07/24, permitted history |
| H10 | P1 TikTok posts/history | **ЧАСТИЧНО**: textual adapter, live onboarding pending | V2-03/07/24 |
| H11 | P1 voice signature/palette/fonts/sample | **ЧАСТИЧНО**: schema/edit/approval есть, real AI нет | V2-06 |
| H12 | P1 save brand signature/downstream | ГОТОВО versioned Identity→Strategy | Сохранить, V2-09 |
| H13 | P2 competitor90days/followers/engagement/frequency | **НЕ РЕАЛИЗОВАНО** | V2-07/08, actual available window |
| H14 | P2 cadence category/audience/founder availability | **ЧАСТИЧНО**: cadence fields есть | V2-09/10 capacity/основания |
| H15 | P2 best-time own Insights/category/timezone | **ЧАСТИЧНО**: times есть, inference нет | V2-07/10/24 |
| H16 | P2 generator pillars/tone/calendar | **ЧАСТИЧНО**: Playbook v2/fake | V2-09 |
| H17 | P2 editable/versioned/approved Playbook | ГОТОВО backend, **UI pending** | V2-09/31 |
| H18 | P2 heatmap/sliders/whitespace UI | **ЧАСТИЧНО**: weights/hypotheses backend | V2-08/31; no invented heatmap |
| H19 | P3 title/caption/hashtags/pillar/platform/date | **ЧАСТИЧНО**: contracts/Jobs/regeneration | V2-11 |
| H20 | P3 image rendering with palette/references | **НЕ РЕАЛИЗОВАНО**, только demo | V2-12/16/17 |
| H21 | P3 UGC shot list/voiceover script | **ЧАСТИЧНО**: `videoScript` field | V2-11/13/15; без implicit voice cloning |
| H22 | P3 trending audio/daily Creative Center scrape | **НЕ РЕАЛИЗОВАНО** | X03 feasibility/licence; обычный audio V2-14/17 |
| H23 | P3 calendar drag/reschedule | ГОТОВО backend, **UI pending** | V2-10/23/32 |
| H24 | P3 approve/field regenerate/stable Post ID | ГОТОВО editorial, **media pending** | V2-11/17/32 |
| H25 | P3 four weeks/28drafts | ГОТОВО orchestration/fake, **live pending** | V2-10/11/15/16; cadence не всегда28 |
| H26 | P3 CSV→Later/Buffer manual | ГОТОВО backend, **UI pending** | V2-32; не Later/Buffer API |
| H27 | P3/P5 Instagram autopublish | **НЕ РЕАЛИЗОВАНО** | Теперь required V2-20/21/23/34 |
| H28 | P3/P5 TikTok autopublish | **НЕ РЕАЛИЗОВАНО** | Теперь required V2-20/22/23/34 |
| H29 | P4 Meta OAuth/nightly account+post metrics | **ПОЛНЫЙ WORKFLOW НЕ РЕАЛИЗОВАН** | V2-03/24 |
| H30 | P4 TikTok OAuth/per-post metrics | **ПОЛНЫЙ WORKFLOW НЕ РЕАЛИЗОВАН** | V2-03/24; capability matrix |
| H31 | P4 reach/engagement/saves/signups/profile→site | ГОТОВО formulas/import fields; **live/UI pending** | V2-24/33; external attribution X04 |
| H32 | P4 pillar/format/time comparisons | ГОТОВО bounded analytics; **live/UI pending** | V2-24/33 |
| H33 | P4 audience age/city | **НЕ РЕАЛИЗОВАНО** | X04 API/privacy/aggregation |
| H34 | P4 ranked AI actions/confidence/expected lift | **ЧАСТИЧНО** narrow rule/UNCALIBRATED | V2-25; causal lift не от LLM |
| H35 | P4 apply→Strategy draft | ГОТОВО weight patch; **new actions pending** | V2-25/31/33 |
| H36 | P4 branded PDF/signed URL/monthly email | **ЭТОТ ФОРМАТ НЕ РЕАЛИЗОВАН**: HTML/JSON есть | V2-26 recurrence; X02 PDF/email |
| H37 | P5 strategist multi-client dashboard | **НЕ РЕАЛИЗОВАНО, ВНЕ SCOPE** | X06 |
| H38 | P5 Stripe subscriptions/seats/pricing | **НЕ РЕАЛИЗОВАНО, ВНЕ SCOPE** | X05; cost ledger не billing |
| H39 | P5 white-label | **НЕ РЕАЛИЗОВАНО, ВНЕ SCOPE** | X06 |
| H40 | P5 reusable voice presets | **ЧАСТИЧНО** voice fields, catalogue нет | V2-18 |
| H41 | P5 edit/approval audit | ГОТОВО core/backend28 | V2-20/23/28 новые events |
| H42 | §02 ElorForce SSO | **НЕ РЕАЛИЗОВАНО, ВНЕ SCOPE** | X06 |
| H43 | §02 hosted logs/errors/product analytics | ЧАСТИЧНО logs/audit/health; **hosted/product telemetry нет** | V2-29 operations; X06 product analytics |
| H44 | §06 scraper retries/alerts | General recovery есть; **scraper нет** | V2-07/24 permitted source; V2-27 |
| H45 | §06 references/manual art direction | **ЧАСТИЧНО** review, real production нет | V2-15/16/17/18 |
| H46 | §06 voice do/don't + critic | **ЧАСТИЧНО** voice fields, evaluator нет | V2-06/18/19 |
| H47 | §02/06 model fallback/A-B | **НЕ РЕАЛИЗОВАНО** | V2-02/04/19 only approved fallback |
| H48 | §07 30day pilot/3–5clients/launch | **НЕ ПОДТВЕРЖДЕНО** | V2-34/35, named Brin не обязательный |

## Что не переносится как команда

Node/Hono, BullMQ/Redis, Supabase/RLS, старые модели и CSS palette — рекомендации автора,
не указание заменить Java. Эквиваленты database/storage/queue уже существуют.
Новые dependencies требуют самостоятельного обоснования/security review.

«12–14 недель», «~60секунд», «28постов за2минуты/$2», $48–110/client/month и
«approval1–2weeks» **не подтверждены для нового scope**. Исходник смешивает7posts/week
и28posts/month; используем explicit cadence/4week horizon. Midjourney API, Apify и
TikTok Marketing API не считаются выбранными/доступными по факту упоминания.

Agency `/clients` sketch не заменяет `/api/v1`/Workspace ownership. Approval toggle
не заменяет immutable approvals. Signed report URL не требуется вместо private download.

## Дополнения нового ТЗ

| Требование | План |
| --- | --- |
| Один Workspace на аккаунт, несколько независимых Brands; без Workspace switcher | V2-01/03/29/30/34; migration до включения нового ограничения |
| Готовое video по запросу с Brand Identity/Strategy | V2-11/15/17/18 |
| Raw footage → монтаж → готовый asset | V2-12/13/14/17 |
| Персонализация и возможности обучения | V2-18/19, optional X01 |
| Organic/hashtag/industry intelligence | V2-07/08/11/24/25 |
| Regular market/brand monitoring | V2-07/08/26 |
| Reminders generate/review/approve | V2-10/27 |
| Feedback и ongoing Strategy | V2-17/18/25 |
| £20 demo AI, прочие расходы отдельно | V2-02/04/19 |

## Tracking и доказательства

Read-only перечитаны GitHub #107/#120/#121: OPEN, верхние sections ещё от18 октября,
с прежним CSV-first scope. Они не подтверждают новый scope и не отменяют поздний local code.
Remote edits/push/closure не выполнялись. V2-35 требует сверки по actual acceptance.
Статусы основаны на inspection и исторических tests; свежий runtime pass не заявлен.
