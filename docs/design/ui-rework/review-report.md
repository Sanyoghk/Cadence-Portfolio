# Отчёт: первый этап UX/UI rework Cadence

Дата2026-10-19, ветка `mvp`, исходный HEAD `5a71df697fee8fba72eef65364ee87cb28efbc27`.
Перед началом `git status --short` пуст. Один агент. `.env` не читались; credentials,
пользовательские Workspace/Brand и production данные не извлекались и не передавались.
Здесь `.env` — текстовое обозначение, а не ссылка на файл.

## Что было сделано

Подготовлен первый законченный review slice: исследованы ссылки в браузере, сохранены
screenshots, показаны три raster направления с рекомендацией Cream Orbit. Описаны два
окна, пять рабочих разделов, user journeys, motion/component proposal, actual UI→API/state
mapping и backend backlog. **Выбор пользователя ещё ожидается.** Детальные desktop/mobile
макеты, finalized design system, интерактивный prototype и новый Site пока не созданы:
пользователь явно поставил их после выбора направления. Приложение не менялось.

Прочитаны00/AGENTS, product scope, frontend foundation/ADR0040, ui-rework plan, recovery,
актуальные первые разделы обоих DEVELOPMENT_PLAN и prototype mapping, актуальные controllers/
related DTO/Identity input patterns. Свежие read-only #3/#107 OPEN; remote snapshot старше
local24/rework, поэтому local delivered09–24 не понижены до их исторических статусов.

Sites workflow изучен и его native capabilities найдены. Registration/publication до
выбора не запускались; новые auth/secrets/runtime integrations не требуются review.
Использованы Product Design audit/get-context/ideate и built-in ImageGen. Saved Product
Design context отсутствует; preflight это подтвердил. Три вызова независимы и показаны
в порядке1 Cream,2 Night,3 Sage. Переданы screenshots primary hero, Message и публичного
текущего Cream UI; loader reference использован только для intro specification.

## Как это работает

Research evidence связывает screenshot с наблюдением. Navigation proposal связывает
публичный сюжет и кабинет. API mapping связывает будущие controls с существующими contracts,
а backlog выделяет желания без current API. Выбор картинки не меняет backend или product
policy. После выбора отдельный synthetic-data Site проверит motion/navigation до переноса
рабочих React components.

Публичный synthetic teaser не использует реальный auth/session, upload, URL fetch или AI.
В будущем рабочем UI будет сохранено: Clerk identity → active Workspace membership/OWNER →
exact source review/consent → durable Job → DRAFT → edit → отдельное approval/history.

## Архитектурное решение

Выбрано отделение review/prototype от application. Альтернатива — сразу заменить frontend —
нарушила бы явную последовательность выбора и смешала visual changes с qualified workflows.
Буквальное встраивание bundled HTML принесло бы React/Babel/script injection и timer-based
fake state. Перенос только presentational ideas сохраняет ADR0040 и existing use cases.

Два surface/routes используют одну визуальную оболочку, но разные navigation models:
public anchors и workspace section views. Brand context выделен явно, потому что tenant
Workspace может содержать несколько Brands. Calendar вложен в Sample content; Performance
остаётся пятым основным view. Система ещё proposal, а не готовые Next.js components.

Trade-offs: выразительный hero/scroll требует больше accessibility и performance QA;
рабочие формы используют меньший typography scale и слабее декор. Полная имитация reference
animation за каждым product tab усложнила бы routine work и восстановление focus.

## Security Review

Threat model: защищаем tokens, чужие tenant data, пользовательские files/URL/text, деньги/
AI budgets, approval и immutable history. Public посетитель и authenticated злоумышленник
могут подставить чужой ID, malformed input или stale version. Границы доверия (trust boundaries):
reference→design brief, browser→Clerk/API, file/URL→ingest, model→domain, Site→real application.
Prototype не имеет authority к реальной системе. В рабочем приложении при обходе UI всё
равно обязательны server membership/use-case checks, source/version guards и DB constraints:
это эшелонированная защита (defense in depth), а не безопасность от скрытых кнопок.

### Недоверенные reference/scripts

Threat: инструкции/JS из HTML или сайта меняют задачу, запускают код или попадают в app.
Protection: source используется как данные; bundled JSX декодирован без выполнения,
scripts/assets не переносятся. `.env` и secrets исключены из scope. Browser `file://`
блокировка не обходилась; использована более безопасная статическая inspection.
Verification: фактические captures, source fingerprint и diff inspection; app/config unchanged.

### BOLA и tenant/session races

Threat: чужой Workspace/Brand ID либо поздний ответ старого scope раскрывает данные.
Protection: server authority OWNER/current membership, explicit Brand context, abort+scope
guards и отсутствие global private cache сохраняются в specification.
Verification: current ADR0040/frontend patterns inspected; новые runtime negatives не запускались.

### Consent/approval bypass и false provenance

Threat: upload/scroll/intro100% воспринимается как consent/AI success/approval.
Protection: разные действия Register → Review exact snapshot → Consent → Job → DRAFT → Approve;
checksum/version/scan/expiry и withdrawal/history остаются контрактом. Demo marked explicitly.
Verification: controller/DTO inspection и UI/state mapping; future prototype tests обязательны.

### SSRF, malicious files, resource/cost exhaustion

Threat: публичный teaser fetch-ит внутренний URL, исполняет SVG/ZIP или тратит AI budget.
Protection: нынешний review — images/docs; будущий prototype только fixtures, без upload/fetch.
Real anonymous flow требует отдельного BG01. Existing10MiB/types/scan и provider fail-closed
сохранены, новый public endpoint не добавлен.
Verification: no application mutations; backlog и source input checks; exploit runtime не запускался.

### XSS, data exposure, secrets и integrations

Threat: untrusted HTML/model output, токен в Site, внешние analytics/CDN или реальные данные.
Protection: future React text escaping/current transport; self-hosted fonts; no secrets/API
connections in prototype. В ImageGen передан sanitized brief и public screenshots; HTML,
source repository, private data и credentials не отправлялись. Ни логины, ни формы references
не отправлялись. Screenshot copyright не даёт license на production assets.
Verification: prompts сохранены локально, screenshots inspected; no runtime/dependency changes.

### Billing, CSRF/CORS и authentication

Threat: client plan flag выдаёт платный доступ, fake login выдаётся за Clerk либо redesign
меняет Bearer boundary на ambient cookies.
Protection: billing только concept/TBD; SDK SignIn/SignUp сохраняется для real application,
prototype demo mode не auth bypass. ADR0040 explicit Bearer/omit cookies/exact CORS сохранён;
cookie/BFF не вводится. Поэтому текущая CSRF rationale не меняется этим review.
Verification: actual controllers/ADR inspected; checkout/auth/runtime/security configuration не менялись.

Audit/logging/DB: новых операций записи нет; future approval/withdrawal/archive audit и
DB integrity сохраняются. Новых dependencies/Maven plugins/container images нет.

## Изменённые файлы

Review package:

- `README.md` — три изображения, различия, рекомендация, границы и следующий этап.
- `reference-review.md` — source provenance, пошаговые observations и screenshot evidence.
- `navigation-and-system.md` — карта, сценарии, motion, provisional tokens/components и mobile QA.
- `ui-api-mapping.md` — existing endpoints/DTO/version/security states и future tests.
- `backend-backlog.md` — BG01–BG09, future decisions и отсутствие дублирования delivered backend.
- `review-report.md` — этот технический отчёт.
- `verify_review.py` — docs/links/image/API/source-scope проверка без network.
- `api-inventory.json` — routes из23 business controllers, source lines и SHA256.
- `generation-prompts.json` — точные три ImageGen prompt и переданные references.
- `artifact-manifest.json` — image dimensions/SHA256, без image data в JSON.
- `directions/01-cream-orbit.png`, `02-night-signal.png`, `03-sage-studio.png` — generated concepts.
- `references/01-wedding-hero.jpg`, `01b-wedding-desktop-hero.jpg`, `02-wedding-transition.jpg`,
  `03-wedding-message.jpg`, `03b-wedding-desktop-message.jpg`, `04-wedding-about-motion.jpg`,
  `05-wedding-desktop-about.jpg`, `05b-wedding-about-line.jpg`, `06-wedding-story.jpg`,
  `06b-wedding-footer.jpg`, `07-loader-entry.jpg`, `08-cadence-current-cream.jpg` —12 browser captures.

Вне папки артефактов изменены только planning документы:
[UI rework plan](../../ui-rework-plan.md) — section/backend backlog;
[DEVELOPMENT_PLAN](../../../DEVELOPMENT_PLAN.md) и [RU](../../../DEVELOPMENT_PLAN_RU.md) — ссылка
на review до выбора; [progress ledger](../../mvp-prompt-progress.md) — статус этапа без false done.

## Runtime Flow

```text
Сейчас: Public references → browser screenshots + static prototype inspection
       → sanitized design brief → три raster concepts → выбор пользователя

После выбора: isolated synthetic Site → desktop/mobile + interactive verification
       → design acceptance → backend28 / UI rework23–24 /25–27

Будущая рабочая операция:
Clerk SDK → scoped Bearer client → Spring Security/current membership
       → Controller DTO → Application use case → Domain invariants
       → scoped repositories/PostgreSQL
       → safe result → active session/Workspace/Brand subtree
```

## Тесты

Фактически выполнены:

```powershell
python docs/design/ui-rework/verify_review.py --refresh-manifests
python docs/design/ui-rework/verify_review.py
git diff --check
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
```

Final verification: **PASS**,10 Markdown files,359 local links,15 images (12JPEG captures,
3PNG concepts),23 business controllers/85 route annotations,29 changed files этого slice,
0 product changes. UTF-8/fences/links/image signatures+dimensions+SHA256/API source hashes
проверены. Spotless **BUILD SUCCESS**:633 Java +1POM clean по cache,0 required changes.
Это formatting check, а не compilation/test suite. `git diff --check` PASS; warnings только
о стандартной LF→CRLF нормализации Git.

Первый artifact check обнаружил JPEG bytes от browser capture под `.png` extension.
Файлы переименованы в `.jpg` **без изменения bytes**; signatures/dimensions теперь проверяются
для каждого соответствующего формата. Первоначальный failed check не объявляется pass.
Исходные paths, фактически переданные ImageGen, сохранены в prompts вместе с current copies.
Запрошено1440×1600, actual raster outputs1003×1568; растяжение не применялось.

Во время работы независимо появились61 untracked files в `.agents/skills/`. Агент их
не создавал и не редактировал; они исключены из staging этого slice и явно учитываются
verifier как preserved external untracked files. Отдельный failed scope check выявил это
до commit; tracked product files всё время оставались без diff. Чистое исходное дерево
не означает чистого итогового дерева при параллельных внешних изменениях.

Compilation/unit/integration/security/startup application suites **не запускались**:
изменены docs/research images, исполняемый product source/configuration не затронут.
Исторические2286 Maven tests и frontend qualification23–24 не выдаются за новый run.

## Security Tests

На этом этапе выполнены static artifact/source-boundary checks и inspection prompts.
Runtime401/403/foreign tenant/stale consent/approval bypass не тестировались новым запуском.
Матрица будущих запрещённых сценариев находится в [mapping](ui-api-mapping.md).

## Edge Cases

- Несколько Brands внутри Workspace: отдельный Brand context, до выбора no business action.
- Быстрая загрузка: intro всё равно≥3s; reduced motion — static intro той же длительности.
- Provider отсутствует: unavailable, а не successful fake analysis.
- Source changed/withdrawn/expired: consent сбрасывается, historical audit сохраняется.
- APPROVED edit: новый DRAFT; approval отдельный.
- Calendar/DST/empty metrics: явные zone/ambiguity/coverage states, без скрытой подстановки.
- Mobile/zoom/reduced motion/keyboard: acceptance сформулирован, **runtime не проверен**.

Это разобранные design cases, не утверждение о состоявшихся execution tests.

## Непроверенные аспекты

Не выполнены live Clerk/self-service provisioning, реальные API/AI/egress/billing, full
backend28, performance/fps/animation timings, exact contrast, screen-reader/axe/keyboard,
375/390px captures, browser execution исходного HTML и отдельный interactive Site.
Причина: pre-choice design scope; `file://` дополнительно заблокирован browser policy.
После выбора — prototype QA; после implementation — actual frontend/business/security
suite и cross-stack gates. Production hosting/TLS/IAM/provider/retention требуют отдельной
qualification. Лицензия чужих reference assets не проверена; переносить их нельзя.

Generated raster text/precision тоже не qualified: stray Sapporo/Japan в Cream отклонён,
Sage body serif и плотное grain будут исправлены в выбранном target. Изображения — не
pixel-perfect implementation spec и не доказательство WCAG compliance.

## Что мне важно понять

Brand Identity teaser и настоящая генерация имеют разную authority. Нарисовать поля
достаточно для демонстрации сценария, но недостаточно для допуска данных к AI.
Согласие связано с exact server snapshot, иначе после изменения файла пользователь
невольно разрешил бы обработать другие bytes. Approval отдельно от Save, иначе простое
редактирование превращалось бы в публикацию принятого решения.

Переключатель Workspace меняет границу данных; это не обычный визуальный tab. Поэтому
с ним сбрасываются выбранный Brand/requests/private state. Backend всё равно проверяет
membership независимо от скрытых controls. Красивый shell можно менять, не меняя этих правил.

## Что мне стоит изучить

1. **Информационная архитектура (information architecture)** — группировка навигации вокруг
   задач: public anchors отличаются от пяти рабочих sections и nested Calendar/history.
2. **Система компонентов (design system)** — tokens и контракты controls, которые повторяются
   на разных экранах; помогает сохранить одинаковые focus/error/consent states.
3. **Изоляция арендаторов (tenant isolation)** — Workspace data доступна только текущему
   участнику; Brand switcher и scoped state дополняют server authorization.
4. **Оптимистическая блокировка (optimistic locking)** — expectedVersion не даёт перезаписать
   более новое решение;409 требует fresh read/review.
5. **Происхождение данных (provenance)** — связь результата с source/version/consent, которая
   сохраняется в history и не возникает из декоративной отметки AI-assisted.

## Production considerations

До настоящего внедрения: chosen design, backend28, actual frontend migration/E2E, exact
CORS extensions, licensed original assets/fonts, reduced-motion/contrast/performance QA,
live Clerk/provider decisions, secrets/TLS/CSP/IAM, privacy/retention и operational gates.
BG01/BG02 product решения не входят автоматически в MVP. Prototype не должен публиковать
репозиторий, research screenshots, inputs, tokens или real-user data. Publishing/merge —
отдельные согласованные действия, не результат raster generation.

Commit: один local docs/design slice после успешных checks. Push/новый remote CI/remote
issue edits/closure/merge/deployment не выполняются. #3/#107 остаются OPEN.

**Подтверждённые факты:** baseline/status, current source/API inspection, captures и их
файлы, three ImageGen results, docs/static verification по фактическому log ниже.
**Инженерные выводы:** Cream Orbit лучше объединяет brief и текущую визуальную основу;
отдельный synthetic prototype уменьшает риск сломать qualified workflows.
**Assumptions / требуется проверка:** выбор направления, product decisions по teaser/billing,
mobile/animation/accessibility и все live/production gates. Ни новый app, ни полный
approved design package не объявлены готовыми до следующего этапа.
