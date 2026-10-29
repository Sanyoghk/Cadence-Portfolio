# Контент из-за ширмы —2026-10-19

## Что было сделано

В отдельном Cream Orbit prototype заголовки, абзацы и текстовые списки поднимаются снизу
из-за неподвижной границы. Охвачены public, demo pillars, Workspace и диалоги. Standard/Plus
сохраняют симметрию. Исходный HEAD: `35fc7fc625d89289b4a4cfeb401e25b8950a2714`, ветка `mvp`.

## Как это работает

`RevealText` сохраняет исходный HTML tag (`p`, `h1`–`h3`, `li`, `span`) и React children.
Внутри — неподвижный `text-reveal-mask` с `overflow:clip` и поднимающийся `text-reveal-inner`.
GSAP меняет только transform110%→0; fade не используется. Padding/margin0.12em оставляют
место для выступающих частей букв. `MotionRoot` передаёт готовность после обязательного intro≥3s.

Public:700ms, существующий `--ease-out`, once ScrollTrigger. Pricing — один общий trigger,
без stagger. Workspace/dialog:240ms на mount; обычное изменение children не перезапускает reveal.
CADENCE использует существующий SplitText с char masks,700ms/stagger40ms и последующим revert.

Navigation, labels полей/consent, live statuses, editable values и data controls остаются
мгновенными. Это сохраняет доступность работы при частых действиях и чтении таблиц.

## Архитектурное решение

React владеет маской и текстом. Вариант SplitText для всего динамического JSX отклонён:
императивная перестройка DOM усложнила бы последующие React updates и восстановление children.
SplitText оставлен для неизменного CADENCE. Новых dependencies/plugins нет.

| Before | After | Why |
| --- | --- | --- |
| Смещение и opacity целых групп | Текст движется внутри неподвижной маски | Получить требуемую «ширму», не двигать действия и макет |
| Разные reveal для разных типов copy | Общий `RevealText` | Одинаковое поведение заголовков, пояснений и списков |
| Резервное восстановление после запуска tween | Watchdog установлен до GSAP; keyboard завершает tween | Ошибка или зависший ticker не оставляют текст скрытым |
| Descendant selectors для служебных span | Direct-child selectors у pillar/snapshot | Вложенные маски наследуют исходные размер и цвет текста |

Trade-off: два дополнительных span на текстовый блок и scoped motion context. Реального
пересчёта React каждый animation frame нет. Cleanup удаляет только принадлежащие компоненту
timers/listeners/tweens/ScrollTriggers; глобальные `killAll` не используются.

## Security Review

Threat: DOM XSS через пользовательское название или текст.
Protection: children остаются React children с обычным escaping; нет `innerHTML`/HTML parser
для динамического copy. `as` задаётся только исходным кодом. SplitText получает константу CADENCE.
Verification: inspection компонента;26 прежних browser flows проходят без изменения бизнес-контрактов.
Отдельного penetration test в этом slice не было.

Threat: сокрытие важных действий/consent из-за отказа анимации.
Protection: controls/labels не маскируются; base transform:none; reduced motion и keyboard
показывают текст сразу; независимый watchdog восстанавливает styles при паузе/ошибке GSAP.
Verification: browser tests со stalled tween, injected exception, blocked motion chunk и keyboard.

Новых запросов, secrets, persistence или прав доступа нет. `.env` не читались. Изменения
ограничены design artifacts; backend и working frontend не изменялись.

## Изменённые файлы

Все пути ниже относительно этой папки.

| Файл | Назначение |
| --- | --- |
| [TextReveal.jsx](prototype/src/TextReveal.jsx) | MotionRoot/readiness, общий semantic text component |
| [motion-engine.js](prototype/src/motion-engine.js) | Text scene, masked hero, synchronized pricing, recovery/keyboard |
| [motion-hooks.js](prototype/src/motion-hooks.js) | Text scene и ожидание readiness |
| [styles.css](prototype/src/styles.css) | Маски, reduced/keyboard styles, scoped selectors |
| [Landing.jsx](prototype/src/Landing.jsx) | Public headings/prose/lists/footer |
| [Workspace.jsx](prototype/src/Workspace.jsx) | Заголовки, пояснения и списки пяти рабочих секций |
| [ui.jsx](prototype/src/ui.jsx) | Pillars и title диалога |
| [App.jsx](prototype/src/App.jsx) | MotionRoot, account/history copy, workspace footer |
| [motion-qa.mjs](prototype/scripts/motion-qa.mjs) | Две regression checks + reduced/fallback assertions, отдельный output path |
| [AGENTS.md](prototype/AGENTS.md) | Уточнение пользователя и durable motion rules |
| [design-system.md](design-system.md), [README.md](README.md), этот отчёт | Актуальный motion contract и результаты |

## Runtime Flow

```text
Intro завершён → MotionRoot ready → RevealText/useMotionScene
    → optional motion module → component-scoped context
    → ScrollTrigger once (public) / mount (workspace/dialog)
    → transform внутреннего span → cleanup / keyboard / watchdog
```

Изменение consent, approval и history проходит по прежним React/demo-domain функциям;
результат business action не зависит от завершения motion.

## Тесты

Из `docs/design/ui-rework/prototype`:

```text
pnpm run lint
pnpm run typecheck
pnpm test
pnpm run test:sites
pnpm run build
```

PASS: ESLint/Prettier, targeted checkJs двух motion modules,11 domain tests,4 Sites tests,
production bundle + Sites packaging. Не весь legacy JSX включён в targeted typecheck.

Из корня repository, с разрешённым isolated headless Chrome:

```text
node docs/design/ui-rework/prototype/scripts/motion-qa.mjs http://127.0.0.1:4176/ ../../craft/browser-profiles/text-motion
node docs/design/ui-rework/prototype/scripts/browser-qa.mjs http://127.0.0.1:4176/ ../../craft/browser-profiles/text-flows --no-screens
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
python docs/design/ui-rework/verify_review.py
git diff --check
```

Motion:18/18; прежние user flows:26/26; runtime exceptions:0. Spotless:633 Java files +1POM
чисты (cache); это не запуск backend tests. Doc/artifact verification и diff check — PASS.
CPU throttle4× sample:341 frames, p95 interval12.5ms, max45.8ms, long tasks0,12 samples
с активной motion. Это наблюдение одного headless run, не обещание60/120fps на телефоне.

## Security Tests

11 domain tests включают чужой Brand scope, exact consent, withdrawal, отдельное approval,
immutable history и CSV formula prefixes.26 flows проверяют эти demo decisions через UI.
Эти результаты не доказывают server-side tenant authorization: реальный API не вызывается.

## Edge Cases

Проверены быстрые переключения, keyboard→pointer, unmount во время motion, paused ticker,
injected tween/Flip exception, missing motion chunk, runtime reduced-motion change и touch agenda.
Text reveal не меняет позицию semantic блока; watchdog показывает текст даже при paused tween.
Клавиатурный ввод удаляет transform и не позволяет следующему pointer вернуть текст за маску.
Дополнительный CDP run на1440/768/390/320px: overflow0, текст после reveal целиком виден,
Standard/Plus совпадают по высоте на desktop/tablet. На390px source snapshot сохранил16px
и в родителе, и в маске; Calendar→list меняет heading сразу без повторного transform.
Финальные screenshots desktop/tablet/mobile просмотрены.
Временные screenshots/JSON находятся в игнорируемом `craft/browser-profiles/`.

## Непроверенные аспекты

Физические iPhone/Android, Safari, screen reader на реальном устройстве и GPU profiling
не доступны в этом run. Перед переносом проверить ink clipping, selection/copy, zoom,
навигацию assistive technology и scroll performance на целевых устройствах.

## Что мне важно понять / что изучить

- Маска (clipping): ограничивает видимую область, а не меняет данные или доступ к ним.
- Владение DOM (DOM ownership): React продолжает обновлять children; GSAP меняет только style.
- Постепенное улучшение (progressive enhancement): базовый текст читается даже без motion chunk.

## Production considerations и статус доказательств

Факты: перечисленные команды и browser checks выполнены; новых dependencies/API нет.
Инженерный вывод: React wrappers уменьшают риск конфликта animation DOM с динамическим JSX;
transform не требует layout каждый кадр. Assumptions: реальные устройства и assistive technology
нуждаются в дополнительной qualification. Backend tests/deployment в этот scope не входили.
Изменение сохраняется локальным commit; push/publication не выполняются.

Skills: animate определил curtain/700ms vs240ms и no replay; animation-performance ограничил
motion transform и запретил frame-by-frame React state; emil-design-eng сохранил быстрые
controls, focus, типографику и восстановление после прерывания.
