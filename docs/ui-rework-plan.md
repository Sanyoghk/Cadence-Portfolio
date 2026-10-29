# Пересмотр creator UI перед экранами25–27

> **Актуализация расширенного ТЗ20 октября:** новый frontend планируется в
> [V2-30–33](astra-medium-mvp-prompts-v2.md), после backend readiness V2-29.
> Старые23–24 — историческая реализация;25–27 перенесены и расширены. Новый дизайн ещё
> не выбран. Platform consent UX/audit входит в V2-32/34; инструкции ниже исторические.
> Новое правило: один Workspace на аккаунт, несколько Brands. V2-30 убирает Workspace
> switcher/Manage workspaces/создание дополнительных Workspace; оставляет Brand picker,
> управление Brands и общие настройки аккаунта. Старые UI-требования ниже этим заменены.

## Новый порядок2026-10-20 — frontend после backend MVP

Пользователь хочет полностью переработать frontend, но сначала завершить backend с реальным
AI. [Backend completion plan](backend-mvp-completion.md) имеет приоритет над прежним порядком
этого документа. UI implementation25–27 и новая design implementation отложены.
Текущий frontend и design artifacts сохраняются; прежняя Cream Orbit acceptance является
историей, а не новым выбором дизайна второго frontend. Новое UI решение принимается позже.
Backend09–22/28 не реализуются повторно; остаются real AI/usage/visual/product integration
и backend acceptance gates. После них — новый frontend и отдельные cross-stack tests.

## Уточнения после ручной проверки2026-10-20

Пользователь попросил применить сразу: в Workspace topbar только действие Manage workspaces
(создание остаётся в Manager); убрать Explore your brand из hero и две звезды на краях
Let’s read your brand; удалить нижнюю декоративную ленту и её pause control. Один статичный
footer содержит Cadence и явно демонстрационные контакты; Appearance сохраняется.
Это уточнение рабочего UI23–24, не новый scope25–27 и не изменение design prototype.
Изменения и фактические проверки: [отчёт уточнений](ui-rework23-24-refinements.md).

## Execution2026-10-20 — UI rework23–24 qualified locally

После qualified backend28 Cream Orbit перенесён в рабочий `frontend/`.
[Implementation report](ui-rework23-24-report.md): public shell/Clerk/Workspace/Brand и полный
existing evidence→exact review/consent→Identity DRAFT→edit→separate approval/withdrawal/history;
58 unit/66 Chromium+axe, lint/typecheck/build/export PASS. API/auth/backend contracts сохранены.
Следующий frontend scope25→26→27; frontend/cross-stack28 и live/physical-device gates отдельно.
Billing/anonymous analysis не реализованы; prototype domain/timers/results в bundle отсутствуют.
Cinematic intro/text/scroll polishing отложена; формы не зависят от GSAP или synthetic loader.
Ниже сохранён approved design/history context; его прежние «не перенесён» относятся к19 октября.

Дата: 2026-10-19. Baseline: `ce76192`, ветка `mvp`, исходное дерево чистое.
Пользователь проверил доступный local workflow и запросил почти полную переработку web UI
с корректировкой дизайна. После review и уточнений пользователь принял Cream Orbit как
основу UI; анимации планирует дорабатывать вручную позже. Отдельный prototype подготовлен,
перенос в рабочий frontend ещё не выполнен. Начальный baseline выше относится к planning slice.

## Согласованная точка продолжения,2026-10-19

На actual HEAD `aa240ae` (`mvp`) пользователь сообщил, что доволен UI. Design package
достаточен для следующего этапа; дальнейшая motion polishing не является prerequisite.
Источник переноса — [отдельный Cream Orbit prototype](design/ui-rework/prototype/README.md),
[design system](design/ui-rework/design-system.md), [navigation](design/ui-rework/navigation-and-system.md)
и [UI→API mapping](design/ui-rework/ui-api-mapping.md). Это acceptance визуальной основы,
не qualification рабочего приложения, live authentication или API integration.

Следующий implementation scope по утверждённому порядку — **backend28**, затем **UI rework23–24**,
затем **25→26→27**, frontend/cross-stack28 и29–30. Backend28 не требует готовых25–27 или
выбора AI provider: проверить existing auth/membership/tenant/lineage/Jobs boundaries,
logging/audit/limits/health/recovery, исправить доказанные gaps и выполнить применимые gates.
Для полного integration suite нужен доступный Docker/Testcontainers; production-only и live
external gates учитывать отдельно. Delivery09–22 не повторять. Billing и настоящий anonymous
teaser остаются отдельными решениями BG01/BG02, а не prerequisites переноса MVP UI.

Read-only GitHub check2026-10-19: #107/#87/#118/#83/#116/#8 OPEN. Remote tracking содержит
исторические статусы; новый design acceptance им не синхронизировался. Этот docs-only шаг
не запускает backend28, не меняет рабочий код, не выполняет push/issue edits/deployment.

Проверки этой актуализации: `python docs/design/ui-rework/verify_review.py` — PASS
(430 local links,79 images,0 product changes); `git diff --check` — PASS;
`.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check` — BUILD SUCCESS.
Это docs/formatting checks, не новый запуск application suites или qualification28.

## Рекомендация и фактическое состояние

Перепроектировать UX/UI сейчас, до реализации экранов25–27. Перед большой реализацией
нового frontend завершить оставшийся provider-independent backend gate28. Backend09–22
уже квалифицирован локально в своих scopes; повторная реализация этих функций не нужна.
Наличие API/tests не заменяет whole-backend security/operations review и release gates.

23, visual follow-up23 и24 сохраняют историческую qualification. Дизайн можно существенно
менять, сохраняя рабочие use cases, DTO, scoped state и security boundaries. Frontend
Strategy/Content/Performance25–27 пока не реализован: новый shared UI выгоднее определить
до этих экранов, чтобы не переносить сразу обе версии всех страниц.

Clerk development setup, email verification и metadata OWNER настроены локально19 октября.
Проверены реальные Clerk sign-in controls, issuer/JWKS, anonymous denial и CORS. Пользователь
сообщил о ручной проверке нужных функций. Это user-reported smoke, не автоматизированная
полная live auth E2E, проверка self-service OWNER provisioning или production qualification.
Новые frontend business tests либо full Maven этим planning шагом не выполняются.

## Порядок продолжения без перенумерации01–30

| Этап | Результат и граница |
| --- | --- |
| UX/UI design review — принят | Cream Orbit выбран и уточнён; mockups/prototype, navigation, design system и UI→API mapping подготовлены. Motion polish пользователь откладывает. Рабочий код23–24 пока не заменён |
| 28, backend scope | Whole-backend acceptance/security/observability/recovery review по актуальным #87/#118/#83/#8/#116, исправление фактических gaps и required qualification. Не повторять09–22 и не заявлять production readiness по local fake |
| UI rework23–24 | После выбора конкретного дизайна и backend gate28: shared tokens/components/navigation, затем перенос работающих onboarding/evidence/Identity workflows. Это follow-up, не повторная реализация backend/auth |
| 25 →26 →27 | Новые Strategy, Content/Calendar/CSV и Performance/Recommendations/Reports экраны на согласованной основе и actual API |
| 28, frontend/cross-stack →29 →30 | Qualification обновлённого UI, полный creator journey и release по фактически выполненным gates |

ADR0027 сохраняет отдельную ветку **backend28 → AI integration**. Новый UI не является
prerequisite выбора/qualification AI API. Дизайн-проектирование допустимо чередовать с
независимыми backend проверками; этот документ не запускает их одновременно и не разрешает
выбор provider. #120, остальные adapter scopes, visual remainder05 и whole-issue07/#121
остаются явными зависимостями. Пока required visual/adapters acceptance не выполнен,
#121 не закрывать и не выдавать demo generation за real AI.

## Что определить до реализации нового UI

### Design review2026-10-19 и реестр backend gaps

Первый review slice на baseline `5a71df6`: [три направления и recommendation](design/ui-rework/README.md),
[reference screenshots](design/ui-rework/reference-review.md), [navigation/components proposal](design/ui-rework/navigation-and-system.md),
[UI→actual API/state mapping](design/ui-rework/ui-api-mapping.md).
На момент первого review выбор был pending. Сейчас Cream Orbit выбран, desktop/mobile views,
design system и отдельный interactive prototype подготовлены; рабочие frontend/backend не изменены.

[Backend gaps / future work](design/ui-rework/backend-backlog.md) — живой раздел плана:
BG01 anonymous teaser, BG02 Standard/Plus billing, BG03–05 real providers/visual/social,
BG06–08 unsupported demo fields/formats и BG09 remaining qualification. При прототипировании
обновлять evidence/status/acceptance; existing09–22 APIs не называть отсутствующими.
Billing и anonymous data processing пока design concepts, не новое approved MVP requirement.

Подробный [отчёт review](design/ui-rework/review-report.md) отделяет inspected facts от
предложений и непроверенного. Порядок backend28→rework23–24→25–27 и все security gates сохранены.

1. Какие затруднения обнаружены при ручной проверке: навигация, последовательность действий,
   перегруженные технические сведения, readability, forms и discoverability.
2. Карта экранов Creator/Workspace/Brand и контекст текущего Brand. Wizard, sidebar или
   другая модель навигации выбираются по конкретным макетам, не по предположению агента.
3. Что сохраняется от `Cadence Prototype.html`, а что заменяется. Prototype и действующие
   Cream/Dark/Sage — baseline для сравнения; степень их сохранения не решена автоматически.
4. Typography, colors, spacing, density, responsive layouts, focus/contrast, icons и
   общие form/list/table/dialog components. Reuse fonts/assets требует provenance/license
   review; новый CDN, dependency или external data policy не появляется молча.
5. Representative desktop/mobile макеты onboarding/Identity и будущих Strategy/Calendar/
   Performance экранов, чтобы shared components подходили всей цепочке продукта.
6. Для каждого действия: реальный endpoint/DTO, version/source token, role, initial/loading/
   empty/error/conflict/unavailable/Job outcome и ожидаемое восстановление после ошибки.

Показать пользователю конкретные варианты и рекомендованный пакет перед массовой заменой
UI. Wireframes/prototype обозначать как design artifacts; они не подтверждают fetch,
генерацию, approval, Job completion или наличие пользовательских данных. Изменения product
semantics/API, если они действительно нужны новому UX, выделить отдельно до реализации;
переименование кнопки само по себе не является причиной переписывать backend.

## Сохранить при переносе

- ADR0040: static Next.js/React/TypeScript, Clerk SDK, explicit Bearer, validated public env,
  typed client/Problem Details. Cookie/BFF, token bypass и новые auth protocols не вводятся
  ради визуальной переработки. Backend authorization остаётся самостоятельной.
- Session/Workspace/Brand isolation, abort/late-response guards и no private cross-tenant
  cache. Нельзя выдавать optimistic browser state за committed server operation.
- Exact human review/consent, withdrawal/history, current source/version checks, explicit
  DRAFT edit и отдельное approval. APPROVED history/provenance не переписываются.
- Demo/heuristic/unsupported/provider-unavailable states. Визуальное упрощение не скрывает
  missing source, expiry, scan, conflict или ограничения model/provider.
- CORS deny-by-default, safe React text rendering, private downloads и existing server
  bounds. Навигация и скрытые кнопки не являются защитой backend.

## Acceptance и проверки будущего implementation

Approved design package содержит navigation map, desktop/mobile views и UI→API/state mapping
для сохранённых и будущих workflows. Реализация23–24 переносится вертикальными проверяемыми
slices; существующие API clients/security guards переиспользуются либо заменяются только
с эквивалентным contract и regression evidence. Не нужен единовременный rewrite приложения.

Для UI: actual lint/format/typecheck/unit/build/export isolation и browser E2E;
keyboard/contrast/focus/axe, loading/empty/error/401/403/404/409, tenant/session switch,
late responses, exact consent и separate approval. Для затронутого backend —00/AGENTS,
relevant/full Maven/PostgreSQL/runtime/startup и остальные required gates. Исторические
results23–24 не считаются проверкой изменённого UI. Включение workers/providers и настоящая
generation — отдельная configuration/qualification, не эффект нового оформления.

## Почему такой порядок

| Вариант | Correctness/security | Complexity/maintainability | Пользовательская ценность |
| --- | --- | --- | --- |
| Сначала25–27 в старом UI, потом redesign | Можно сохранить guards, но их и E2E придётся переносить повторно | Больше повторной работы над всеми экранами | Позже появится цельный интерфейс |
| Сразу переписать весь frontend, отложив backend review | Готовность backend/операционных границ остаётся непроверенной; риск подменить gaps UI state | Одновременно меняются layout, navigation и интеграции | Быстро меняется вид, end-to-end readiness не доказана |
| Design review сейчас → backend28 → UI rework →25–27 | Стабильные API/security contracts и проверяемый перенос сохраняются | Небольшой design этап до shared foundation; без повторения delivered backend | Новый UI строится один раз на всю цепочку; выбранная рекомендация |

## Security review этого docs-only изменения

Threat: redesign снимает review/approval/tenant guards или маскирует недоставленный provider.
Protection: явные invariants и independent backend authority в plan/prompt prerequisites.
Verification: inspection новых инструкций; runtime доказательства потребуются в implementation.

Threat: новая последовательность ошибочно закрывает backend/AI/live gates.
Protection:28 backend отдельно от frontend28, #121/adapter/release acceptance сохранены;
manual smoke явно отделён от автоматизированной qualification. Новые remote writes не выполняются.

## Изменённые документы и verification

- [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) и [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) — текущая точка продолжения и рекомендуемый порядок.
- [astra-medium-mvp-prompts.md](astra-medium-mvp-prompts.md) — design/rework follow-up и prerequisites25–27 без новых номеров.
- [prototype-ui-adaptation-plan.md](prototype-ui-adaptation-plan.md) — прежний mapping как историческая основа, ссылка на новый review.
- [mvp-prompt-progress.md](mvp-prompt-progress.md) — запрос на rework, не выполненный implementation.
- [frontend-foundation.md](frontend-foundation.md) — текущий presentation plan и честный local Clerk статус.
- [product-scope.md](product-scope.md) — presentation rework без молчаливого изменения MVP/use cases.
- [issue-sync.md](issue-sync.md) — actual read-only #3/#107 и local/remote delta.
- Этот документ — rationale, scope, contracts, acceptance и security review.

Проверки: `python var/verify-ui-rework-docs.py` (локальный ignored audit script) и
`git diff --check`. Фактически PASS:9 Markdown files, strict UTF-8, balanced fences,
442 local links, одинаковые current continuation sections обоих планов, неизменные
идентификаторы00–30, rework prerequisites25–27 и отсутствие product/config/dependency diff.
Git предупреждает только о стандартной LF→CRLF нормализации рабочей копии.
Full Maven/frontend suites не запускались: исполняемый код этим шагом не менялся.
Перед commit выполнено `.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check`:
BUILD SUCCESS,633 Java files и1 POM clean по cache; это formatting check, не новый test run.
Runtime product changes, новая qualification, remote edits, push, issue closure, merge
и deployment не входят в этот planning slice. Существующие local серверы не перезапускались.

## Что важно понять

Система компонентов (design system) задаёт общие tokens/controls/navigation до появления
всех экранов, поэтому их не приходится переоформлять по отдельности. Стабильный API contract
позволяет менять представление, не переписывая правила хранения, ownership и approval.
Вертикальный срез (vertical slice) переносит один полный пользовательский сценарий вместе
с его ошибками/security checks; так регрессию легче найти, чем при одном большом rewrite.

Подтверждённые факты: code/report inspection, actual read-only #3/#107 и выполненные docs
checks. Инженерный вывод: design review до25–27 уменьшит повторную работу, а отдельный
backend28 сохранит проверяемую границу готовности. Пока не подтверждены: конкретный новый
дизайн, оценка объёма его реализации, backend28 и полная live/production qualification.


## Cream Orbit — выбранный prototype slice,2026-10-19

Пользователь выбрал направление1 и разрешил локальный headless Chrome/Edge QA.
Подготовлен отдельный [интерактивный prototype](design/ui-rework/prototype/README.md),
[desktop/mobile макеты и skill review](design/ui-rework/design-qa.md),
[design system](design/ui-rework/design-system.md) и [отчёт](design/ui-rework/prototype-report.md).
Sites project private/unpublished; рабочие frontend/backend и их contracts не заменялись.

Проверено:11 demo domain tests,4 Sites Worker tests,26 browser checks; build, advisory audit,
formatting и artifacts. Это не qualification рабочего API/auth/providers/backend28.
Следующие production slices используют existing UI→API mapping и сохраняют все gates.

### Backend gaps по ходу разработки нового UI

Живой [backend backlog](design/ui-rework/backend-backlog.md#обновления-по-ходу-cream-orbit-prototype)
разделяет отсутствующие возможности (BG01–BG09), already delivered backend contracts и
ещё не перенесённый frontend. При каждом новом control записывать route/DTO evidence,
решение, статус и acceptance; не объявлять demo успешным backend request.


### GSAP motion slice,2026-10-19

В выбранный отдельный prototype добавлен GSAP3.15.0 через pnpm с обновлением его lockfile.
Согласованные composition/functional contracts не менялись. ScrollTrigger — public scenes/line,
SplitText —7 chars CADENCE с revert, Flip — явное desktop Calendar↔list переключение,
CustomEase — существующий CSS curve. Native keyboard/touch/reduced motion/fail-open сохраняются.
[Motion plan и фактические проверки](design/ui-rework/motion-report.md).

Новых backend gaps этот presentation-only slice не создаёт: BG01–BG09 и existing API mapping
остаются актуальными. Ни generation, ни approval, ни consent не зависят от GSAP completion.
