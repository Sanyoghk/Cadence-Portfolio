# GSAP motion — Cream Orbit,2026-10-19

## Что было сделано

GSAP **3.15.0** установлен непосредственно в `docs/design/ui-rework/prototype` через
`pnpm add gsap@3.15.0 --save-exact --ignore-scripts --prefer-offline`. Обновлены его
package.json и pnpm-lock.yaml. До изменения GSAP был только в рабочем `frontend`.
`require.resolve('gsap/package.json')` из prototype теперь возвращает его собственный
`prototype/node_modules/.pnpm/gsap@3.15.0/node_modules/gsap/package.json`, version3.15.0.
Пакет переиспользован из local cache,0 downloads. Working frontend/backend не менялись.

Actual branch `mvp`, стартовый HEAD `741f494668b96bfcaf8a31078aa3aae28e7bcd00`.
Untracked61 skill files сохранены отдельно. Preview текущей сессии:
[Cream Orbit](http://127.0.0.1:4176/). Sites остаётся private/unpublished; deploy не выполнялся.

## Motion plan и принятые решения

| Частота / назначение | Реализация |
| --- | --- |
| Rare / Explanation | CADENCE: SplitText7 chars, opacity/translate700ms, stagger40ms после intro≥3s |
| Marketing / Spatial consistency | Public sections/cards: ScrollTrigger once, transform/opacity500ms |
| Marketing / Explanation | Pricing line: небольшой raster clip-path, ScrollTrigger scrub0.3s |
| Occasional / State indication | Identity curtain:420ms exit и500ms pillar entrance, stagger40ms |
| Occasional / Spatial consistency | Native menu180ms от trigger origin, native dialog200ms |
| Explicit pointer layout change | Calendar↔Post list: Flip220ms, scale:true, только fine pointer/hover |
| Frequent / Feedback | Button press остаётся CSS120ms; основной CTA не получает GSAP transform |
| Frequent / Reading | Workspace routing, metrics/counts, form edits и keyboard actions — без motion |

Close/Escape/outside dismissal происходят сразу: native focus/visibility semantics не задерживаются
ради exit. Touch agenda меняет mode сразу, без перелёта читаемых карточек. В reduced motion
нет SplitText/scroll motion/Flip; dialog допускает только короткую opacity подсказку120ms.
Intro и бесконечный footer loop остались CSS, чтобы не зависеть от main-thread GSAP ticker.

## Как это работает и архитектурное решение

`motion-hooks.js` лениво импортирует `motion-engine.js`. Ошибка загрузки возвращает null:
UI уже видим, native controls и state transitions остаются рабочими. Vite выделяет GSAP
в отдельный optional chunk; это React/Vite SPA, Next.js/server components здесь нет.
Working Next.js app и его client/server boundaries не изменялись.

Используются только **ScrollTrigger, SplitText, Flip, CustomEase**. CustomEase читает
существующий `--ease-out`; durations используют `--ui`, `--motion-dialog`, `--motion-layout`.
Не импортированы gsap/all, ScrollSmoother, Draggable или неиспользуемые plugins.

`useMotionScene` создаёт context только для своего root, проверяет live flag после async load
и вызывает `media.revert()` при unmount/dependency change. `gsap.matchMedia` пересоздаёт
сцену при смене reduced-motion. Это следует scoped cleanup модели [GSAP context](https://gsap.com/docs/v3/GSAP/gsap.context()/)
и [matchMedia](https://gsap.com/docs/v3/GSAP/gsap.matchMedia()/). Нет глобального killAll.

SplitText ждёт fonts.ready, использует aria:auto для h1 без interactive children и возвращает
original markup после entrance/при cleanup. См. [SplitText](https://gsap.com/docs/v3/Plugins/SplitText/).
Flip измеряет выбранные post elements только при явном переключении view, связывает их через
scoped data-flip-id и использует transforms. Быстрое повторение останавливает предыдущий
Flip через scoped `Flip.killFlipsOf(targets,false)` и захватывает текущее положение;
следующий transition начинается от него. См. [Flip](https://gsap.com/docs/v3/Plugins/Flip/).

GSAP работает через JavaScript на main thread. Transform/opacity уменьшают rendering cost,
но не превращают GSAP в compositor-thread driver. Поэтому простые CSS press/intro/footer
сохранены; inherited CSS variables не обновляются на каждом кадре. Удалён старый ручной
scroll handler с getBoundingClientRect/requestAnimationFrame и дублирующие CSS entrances.
Layout/paint effects вроде width/height/margin/shadow/blur не анимируются собственным кодом.
Clip-path оставлен только для узкой декоративной линии; его cross-browser cost требует device QA.

Watchdog на обычном setTimeout завершает/очищает transient entrance styles независимо от GSAP
ticker. Business-state timer curtain, consent, approvals и demo preparation не зависят от
animation completion. Если chunk/tween/Flip не работает, интерфейс остаётся доступным.
Controller Flip, media listeners, native toggle listeners и timers очищаются владельцем.
Fail-open относится только к показу уже разрешённого UI при сбое motion; это не bypass
authentication/authorization и не изменение server deny-by-default.

Альтернатива «всё заменить GSAP» отклонена: добавила бы main-thread работу для простых press/loop
и задержку частых действий. Новый animation framework/UI library не нужен; GSAP добавлен по
прямому запросу, native dialog/details сохранены. Trade-off — около61KB gzip отдельного motion chunk.

## Before / After / Why

| Before | After | Why |
| --- | --- | --- |
| GSAP только в working frontend | Exact dependency в prototype + свой lockfile | Runtime использует dependency своего приложения |
| CSS h1 entrance и CSS orbit entrance | Scoped SplitText/GSAP sequence, original markup после entrance | Explanation на редком hero, сохранён accessible heading |
| IntersectionObserver + CSS reveal | ScrollTrigger once с видимым base DOM | Один owner анимации вместо двух drivers |
| Каждый scroll update читал rect в ручном rAF | ScrollTrigger geometry/refresh, bounded line mask | Potential cause: лишние geometry reads; удаление подтверждено inspection |
| CSS curtain и отдельная entrance | Scoped GSAP; state timer независим | State indication не становится prerequisite функциональности |
| Calendar/list мгновенно меняли layout | Pointer-only Flip220ms; rapid retarget и cleanup | Пространственная связь между теми же posts; reading data не двигается самопроизвольно |
| Native menu/dialog без entrance |180/200ms с immediate close/keyboard | Обратная связь без задержки focus и dismissal |
| Потенциальный GSAP/CSS transform конфликт CTA | CTA оставлен только CSS press | Два drivers не борются за один transform |
| Зависшая прозрачность при остановленном ticker | Независимый watchdog и visible defaults | Fail-open проверен остановленной timeline и injected exceptions |

## Runtime Flow

```text
React mount / view action
    ↓
useMotionScene или useContentFlip
    ↓
optional lazy GSAP engine
    ↓
component root + matchMedia + keyboard/pointer policy
    ↓
timeline / ScrollTrigger / Flip (DOM styles, без React setState per frame)
    ↓
revert / kill scoped animation / remove listener / clear timer

Параллельно: native controls → existing demo state machine → React render
```

Последняя цепочка не ждёт GSAP. Server/API/auth/data lifecycle сохраняют прежний scope.

## Security Review

```text
Threat: Motion callback переживает unmount/Workspace switch и меняет чужую рабочую область.
Protection: root scopes, async live guard, controller cleanup, scoped targets и IDs.
Verification: repeated mounts не увеличивают timeline count; public triggers0 в Workspace;
              unmount во время Flip не оставляет старый content/animation.

Threat: Animation failure скрывает form/menu/dialog или блокирует state change.
Protection: visible base CSS, optional chunk, watchdog, independent state timers,
            native dialog/details, no animation-based authorization.
Verification: blocked chunk, injected entrance/Flip exception, paused GSAP tests PASS.

Threat: Supply-chain/data exposure через новую dependency или внешний animation CDN.
Protection: exact GSAP3.15.0, local package/lock, ignore-scripts install, local lazy bundle.
Verification: package resolution, production build, pnpm audit и loopback-only browser requests.
              Нет CDN fetch, credentials, .env reads или настоящих user data.
```

Нет новых endpoints, SQL, uploads, authentication, payments, CORS/CSRF configuration.
Backend auth/tenant isolation не считается проверенным animation tests; его implementation не менялась.
Pure demo lifecycle regression tests повторно проверяют consent/current source/approval/history/CSV.

## Изменённые файлы

Пути ниже относительно `docs/design/ui-rework/`:

| Файл | Назначение |
| --- | --- |
| `prototype/package.json`, `prototype/pnpm-lock.yaml` | GSAP3.15.0, lint/typecheck scripts |
| `prototype/src/motion-engine.js` | Только используемые plugins, contexts, public/menu/dialog/curtain/Flip, fallback |
| `prototype/src/motion-hooks.js` | React ownership, lazy load, font readiness, cancellation и Flip capture/play |
| `prototype/src/App.jsx` | Header root/scene; ready для Landing; удалён старый reveal observer |
| `prototype/src/Landing.jsx` | Root/curtain scene, keyboard policy; удалён ручной scroll/rAF driver |
| `prototype/src/Workspace.jsx` | Calendar root, scoped Flip IDs и mode transition; demo domain не изменён |
| `prototype/src/ui.jsx` | Native Modal motion scene; focus/Escape semantics сохранены |
| `prototype/src/styles.css` | Удалены дубли CSS animations; visible defaults; extended timing tokens |
| `prototype/eslint.config.mjs` | Lint prototype source с reuse установленного repository toolchain |
| `prototype/tsconfig.motion.json` | checkJs новых motion modules; legacy JSX не мигрирует на TS |
| `prototype/scripts/motion-qa.mjs` |16 motion/failure/cleanup checks и CPU×4 sample |
| `prototype/scripts/browser-qa.mjs` | Настраиваемый loopback origin/output, optional no-screens для build QA |
| `prototype/README.md`, `prototype/AGENTS.md` | GSAP decisions, commands и актуальный4176 preview |
| `README.md`, `design-system.md`, `design-qa.md` | Ссылки на motion update и фактические contracts |
| `motion-report.md` | Этот отчёт и motion plan |
| `craft/motion/qa.json`, `performance-sample.json`, `dependency-audit.json` | Machine evidence |
| `craft/motion/*.jpg`, `craft/motion/flows/*` | Desktop/mobile frames и повторный functional QA |
| `craft/motion/production/browser-qa.json` | Сценарии compiled production build |
| `verify_review.py`, `artifact-manifest.json` | Bounded image inventory включает новые motion artifacts |
| `../../ui-rework-plan.md` | Статус GSAP slice; новых backend gaps нет |

Working `frontend/`, `src/`, pom.xml, Sites Worker/hosting/packaging template не изменены.

## Тесты и фактические результаты

Из prototype запускались `pnpm run lint`, `pnpm run typecheck`, `pnpm test`,
`pnpm run test:sites`, `pnpm run build`, `pnpm audit --prod --json`.
В Windows использовался полный executable:
`C:/Users/<user>/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/fallback/pnpm.cmd`.

Из repository root:

```text
node docs/design/ui-rework/prototype/scripts/motion-qa.mjs
node docs/design/ui-rework/prototype/scripts/browser-qa.mjs http://127.0.0.1:4176/ ../../craft/motion/flows
node docs/design/ui-rework/prototype/scripts/browser-qa.mjs http://127.0.0.1:4178/ ../../craft/motion/production --no-screens
node frontend/node_modules/prettier/bin/prettier.cjs --check docs/design/ui-rework/prototype/scripts/motion-qa.mjs docs/design/ui-rework/prototype/scripts/browser-qa.mjs docs/design/ui-rework/prototype/eslint.config.mjs docs/design/ui-rework/prototype/tsconfig.motion.json docs/design/ui-rework/prototype/package.json
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
python docs/design/ui-rework/verify_review.py
git diff --check
```

Результаты: ESLint/Prettier PASS; targeted checkJs PASS;11 domain +4 Worker tests PASS,
0 failed/skipped.16 motion browser checks PASS;26 functional checks PASS в dev и26 PASS
на compiled build. Browser console exceptions0. Build33 modules, Sites outputs на месте;
motion chunk около155KB /61KB gzip, entry около238KB /73KB gzip. Audit0 known vulnerabilities.
Spotless BUILD SUCCESS:633 Java +1 POM clean по cache; это не Java test run.

Первый checkJs обнаружил недекларированный параметр Flip getState; заменён публичным API
killFlipsOf без type suppression. Первый lint потребовал format одного нового файла — исправлено.
Keyboard QA уточнён до настоящего CDP Enter с text carriage return; product assertions сохранены.
`pnpm why` внутри sandbox не смог открыть общий SQLite store; direct local require.resolve
подтвердил installation path/version. Это не build/runtime failure.

Lint/typecheck используют уже установленный `frontend/node_modules` tooling без его изменения.
Typecheck охватывает два новых JS motion-модуля и GSAP/React interfaces, а не весь legacy JSX.
Java/Next.js suites не запускались: их исходники и configuration этим slice не затронуты.

## Security Tests и Edge Cases

Проверены: no/stale/withdrawn consent и current source; immutable approved history; чужой Brand
lookup; approved-only CSV/formula prefix; draft/out-of-range scheduling; Worker API/write rejection.
Browser повторяет эти основные workflows, scoped switching, mobile layouts375/390/768 и desktop1440.
Motion QA проверяет accessible SplitText и revert, быстрые alternating toggles, keyboard instant,
native focus/Escape, touch agenda, reduced-motion runtime change, context cleanup/repeated mounts,
unmount mid-Flip, missing chunk, tween/Flip exception и остановленную GSAP timeline.

## Непроверенные аспекты и performance evidence

[Последний CPU×4 sample](craft/motion/performance-sample.json) содержит rAF intervals,
max/p95 и long tasks за2s скриптовой прокрутки. Assertion требует active GSAP samples, чтобы
замер не оказался статичной страницей. Это короткий diagnostic sample headless browser,
не GPU profile и не доказательство60/120fps. CPU throttling не моделирует слабую GPU/память.
Последний sample:20 отсчётов с active GSAP,445 rAF callbacks, p95≈8.3ms, max≈16.8ms,
long tasks0. Число rAF callbacks не является измерением device FPS.
Полный DevTools trace и физические iOS Safari/Android/120Hz устройства не проверены.

Не выполнены screen reader/axe audit и полноценный cross-browser Safari/Firefox profile.
Особенно следует проверить clip-path маску и touch browser chrome на реальном устройстве.
Не проверялись real Clerk/API/providers/billing: они не подключены, demo data явно обозначены.

## Что мне важно понять / Что мне стоит изучить

1. **Владение ресурсом (resource ownership):** context, listener и timer живут столько же,
   сколько компонент; cleanup предотвращает работу со старым DOM.
2. **Прогрессивное улучшение (progressive enhancement):** content/state работают до GSAP;
   animation failure снижает polish, а не доступность функции.
3. **Конвейер rendering (layout/paint/composite):** transform/opacity дешевле layout properties,
   но JS driver всё равно конкурирует за main thread.
4. **FLIP:** сравнивает геометрию до/после явного layout change и анимирует разницу transform;
   stable scoped IDs связывают те же posts между двумя представлениями.
5. **Motion policy:** редкий marketing entrance и частое рабочее действие имеют разный бюджет;
   keyboard/touch/reduced motion меняют допустимый эффект, сохраняя действие.

## Production considerations

Prototype остаётся design artifact; перенос в Next.js потребует client-only ownership и своих
tests с настоящим typed transport/auth. Не переносить demo timer/model вместо durable Jobs,
exact consent receipts или server authorization. Перед commercial release — device profiling,
full accessibility/cross-browser qualification и проверка лицензии закреплённой dependency.
Новых backend features/gaps не обнаружено; BG01–BG09 и исходный UI→API mapping сохраняются.
Локальный commit сохраняет slice; push/CI/merge/deploy/issues не изменялись.

**Факты:** dependency path/version, local screenshots, executed lint/typecheck/tests/build/audit.
**Инженерные выводы:** scoped optional motion и дешёвые свойства снижают риск задержек/регрессий.
**Assumptions:** реальный phone/GPU/cross-browser feel и production auth/API qualification требуют
дополнительной проверки и не следуют из headless PASS.
