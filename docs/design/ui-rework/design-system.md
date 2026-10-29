# Cream Orbit — система интерфейса

## Working application2026-10-20

Tokens/composition/assets перенесены в `frontend/src/app/globals.css`, `public-landing.css`,
`CreatorShell`, `PublicLanding` и существующие typed workflows23–24. [Report](../../ui-rework23-24-report.md).
Reference src/public fingerprint unchanged от accepted `aa240ae` (21 files); assets self-hosted,
OFL fonts прежние. Header высота измеряется scoped ResizeObserver с cleanup, чтобы длинный
Workspace/viewport/auth control не перекрывал content; theme changes atomic для contrast.
Public/working API разделены, synthetic domain не перенесён. Motion сейчас CSS press/chevron/
footer с keyboard/reduced-motion/pause; дополнительная cinematic polishing отложена.
Prototype GSAP scenes не инстанцируются рабочим приложением; глобального killAll/observers нет.
Ниже — immutable visual/design history prototype, не заявление о переносе каждого demo timer.

Направление выбрано пользователем: **1 — Cream Orbit**, 2026-10-19.
Исполняемый источник компонентов — [prototype/src](prototype/src/).
Это отдельный design prototype с синтетическими данными, без подключения Cadence API.

## Композиция и типографика

Public: крупный CADENCE → подпись и орбита → панель Identity → sample pillars →
два плана → About → бегущая полоса звёзд. На desktop начало Identity видно следом
за компактным hero; на mobile hero складывается вертикально. Фон, рамка и topbar
фиксированы. По уточнению пользователя2026-10-19 звёзды внешней рамки удалены; линии
рамки сохранены. Остальные орнаменты не перехватывают события и скрыты от screen reader.

Workspace: одна рабочая секция, горизонтальная навигация, отдельный Workspace switcher
и Brand selector. На ширинах до1150px рабочие секции доступны через Sections; на
узких экранах Calendar превращается в agenda. Manager находится в Workspace menu.
Public и Workspace не используют одинаковый масштаб заголовков.

| Роль | Значение |
| --- | --- |
| Display / public titles | Self-hosted Cormorant Garamond; CADENCE italic600, остальные500 |
| Body / controls / app titles | Self-hosted DM Sans; body400, headings500 |
| Public title / lead / body / small |72px с responsive42px /24px /16px /14px |
| Pricing title / body / price |60–128px /18–24px /28–38px; desktop общий baseline двух планов |
| Workspace title / panel title / body / small |28px desktop,24px mobile /20px /16px /14px |
| Inputs | Не менее16px; labels не заменяются placeholder |
| Numbers | `tabular-nums` для Calendar, facts, metrics и истории |
| Text measure | До65ch; короткие description до33ch |
| Spacing |8 /12 /16 /20 /24 /28 /32 /48 /64; внешний gutter clamp24–56px |

Файлы шрифтов скопированы из существующего `frontend/public/fonts`; OFL notices сохранены.
Font CDN и новые UI libraries не добавлены. В отдельный prototype теперь добавлен GSAP3.15.0;
working frontend не изменён. [Motion report](motion-report.md).

## Tokens

| Token | Cream | Dark | Sage |
| --- | --- | --- | --- |
| `--paper` |#f3f0e8 |#191612 |#f2eee4 |
| `--paper-2` |#faf8f2 |#24201a |#faf7ee |
| `--ink` |#1a1814 |#ece8df |#20291f |
| `--muted` |#686157 |#bcb2a2 |#5d6657 |
| `--accent` |#86623e |#d7a878 |#4b6850 |
| `--wash` |#eae4d8 |#373025 |#e3e8d8 |
| `--control` |#8e8576 |#938879 |#7d8974 |
| `--focus` |#835227 |#f2bd74 |#3d633f |

Cream — основной проверенный visual target. Dark/Sage — переключаемые prototype tokens;
их полная contrast/device qualification остаётся перед production переносом.

## Общие компоненты и состояния

| Компонент | Где / поведение |
| --- | --- |
| `Button` | Primary/secondary/disabled; минимум48px; pointer press120ms; keyboard без scale |
| `Field` | Native input/textarea/select внутри связанного label;16px, required/maxLength/bounds |
| `Star` | Crop собственного raster ornament; decorative `aria-hidden`, пустой alt |
| `Pillars` | Единый synthetic mix40/35/25; native meter с доступным label |
| `Modal` | Native dialog: modal focus boundary, Escape, восстановление предыдущего focus |
| Topbar / menu | Native details/summary, click outside и Escape, поворот стрелки, активная секция |
| Scope row | Всегда видимая demo маркировка + Brand select; route не заменяет scope |
| Panel / notice | Спокойные filled surfaces; status text, loading, empty, provider unavailable |
| Approval/history | Save draft отдельно от Approve; immutable approved snapshots в demo history |
| Calendar |7 колонок desktop, одна agenda mobile; несколько постов в дне сохраняют колонку |
| Performance bars | Native meters, четыре конкретных наблюдения, units/window/coverage, empty unknown |

Нет resting shadows. Тени есть у открытых menu/dialog. Рамка по краям и тонкая линия topbar
оставлены по прямому требованию пользователя. Границы полей и Calendar обозначают controls/данные.

## Motion contract

| Переход | Механизм / время / смысл |
| --- | --- |
| Intro | Минимум3000ms; underlying UI `inert`; progress через scaleX; один раз на reload |
| CADENCE | SplitText7 char masks + translateY700ms, stagger40ms; original markup восстанавливается |
| Content text | Неподвижная маска + translateY110%→0; public700ms once при scroll, workspace/dialog240ms при mount |
| Identity → pillars |650ms synthetic preparation, outgoing scale/opacity420ms; текст pillars поднимается из масок |
| Pricing line | ScrollTrigger scrub0.3s, clip-path увеличенной PNG линии в центральном промежутке; без собственного scroll/rAF handler |
| Footer |32s linear loop; отдельная Pause/Resume motion |
| Controls/menu | CSS press120ms, GSAP menu180ms/dialog200ms/Flip220ms; CustomEase из `--ease-out` |

`prefers-reduced-motion` выключает transform/loop/reveal, оставляет содержание и полный
минимум intro по brief. Нет scroll hijacking, параллакса в кабинете или layout spring.
Пауза footer не останавливает функциональные переходы.

## Mobile

`viewport-fit=cover`, safe-area padding,100svh для hero,100dvh для dialog; browser zoom разрешён.
Hover только при `(hover:hover) and (pointer:fine)`. Tap targets44–48px, поля16px, body text
можно выделять. `overscroll-behavior:contain` ограничен menu/dialog, public scroll естественный.
Проверены browser viewports375/390/768/1440; реального iPhone/Android в этой среде нет.

Полный [QA и сравнение с выбранным target](design-qa.md), [отчёт](prototype-report.md).


GSAP contexts и matchMedia принадлежат компоненту и возвращают cleanup. SplitText применяется
только к декоративному h1, с aria:auto и revert; input/reading data не разбиваются. Calendar↔list
Flip разрешён только для pointer:fine/hover, не для keyboard/touch/reduced motion. Предопределённые
intro/footer остаются CSS. Входящие content/menu/dialog имеют независимый watchdog видимости.
`--motion-dialog:200ms`, `--motion-layout:220ms` дополняют существующий `--ui:180ms`.
Контентный текст использует общий React-компонент `RevealText`; поля, navigation, live statuses
и часто меняющиеся controls не маскируются. Keyboard немедленно завершает текущий text reveal.
Подробности и фактические проверки: [Text curtain report](text-reveal-report.md).

По уточнению пользователя внешняя полоса прокрутки страницы скрыта через `html { scrollbar-width:none }`
и `html::-webkit-scrollbar`. Прокрутка остаётся нативной; внутренние scrollbars controls/dialogs сохранены.
Проверено в Chrome1440/390px: скрытая outer полоса, overflow0, wheel, PageDown и touch events
прокручивают страницу; scrollbar диалога остаётся `auto`. Для touch использован `dispatchTouchEvent`:
`synthesizeScrollGesture` в эмуляторе не дал scroll. Физический телефон/Firefox/Safari не проверялись.
`pnpm run lint`, `pnpm run build`, `spotless:check`, `verify_review.py`, `git diff --check` — PASS.

## Уточнение тарифов,2026-10-19

По новым скриншотам пользователя Standard и Plus выровнены симметрично, как две колонки
референса. Cream Orbit, контент, demo-маркировка и поведение Account сохранены.

| Before | After | Why |
| --- | --- | --- |
| Standard ниже Plus из-за `nth-child(2)` | Одинаковая ширина, отступы и шесть общих строк CSS `subgrid` | Заголовки, описания, списки, цены и действия совпадают по высоте даже при переносе текста |
| Desktop heading72px, основной текст16px | При1440px heading108px, body22.32px, price36px | Увеличить масштаб и читабельность по запросу пользователя |
| Линия160px, opacity0.65 | При1440px ширина792px, opacity0.95; размер зависит от центрального промежутка | Сделать рисунок заметнее и не перекрывать описание на планшете |
| Отдельные reveal у каждого тарифа | Один ScrollTrigger, обе колонки одновременно,500ms transform/opacity | Сохранять симметрию во время появления; без stagger и новых plugins |

На mobile тарифы складываются в одну колонку, body18px, линия180px сбоку.
`overflow-clip-margin:8px` оставляет место для keyboard focus outline. Reduced motion показывает
полную линию и оба тарифа сразу. Базовый CSS не скрывает контент при недоступном GSAP.

Проверено локальным headless Chrome:1440/768/390px; одинаковые ширины/внешние отступы и
baseline desktop/tablet, нулевой horizontal overflow, изменение маски при scroll, открытие Account
обоими типами viewport и закрытие Escape. Три перехода public→workspace→public удаляют все
публичные ScrollTriggers на выходе и возвращают стабильные9 triggers. Runtime exceptions:0.
Скриншоты просмотрены; временные QA-файлы — в игнорируемом `craft/browser-profiles/`.
Дополнительно проверены320/760/1920px: overflow0, кнопка помещается, focus сохраняется.
На320/390/760px clip margin8px вмещает outline3px + offset4px.

Команды из `prototype/`: `pnpm run lint`, `pnpm run typecheck`, `pnpm test` (11/11),
`pnpm run test:sites` (4/4), `pnpm run build` — успешно. Из корня:
`.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check` — успешно.
`python docs/design/ui-rework/verify_review.py` — PASS (409 local links,79 images,
0 product changes); `git diff --check` — успешно. Git сообщает только нормализацию LF→CRLF.

Security review: изменены только локальная композиция и выбор targets существующего scoped
GSAP context. Новых входов данных, API, dependencies и секретов нет; backend и рабочий frontend
не затронуты. Unit negative checks synthetic scope/consent/history проходят, но не доказывают
server-side authorization. Физические iPhone/Android, Safari и GPU performance не проверялись;
перед production переносом нужна проверка на целевых устройствах.

Применены design-taste-frontend (композиция/масштаб), emil-design-eng (общие baseline и focus),
animate (синхронный reveal), animation-performance (без анимации layout) и mobile-native
(читаемая вертикальная версия). `subgrid` — дочерняя сетка, наследующая размеры строк родителя:
изменение длины описания увеличивает одну общую строку и сохраняет выравнивание обеих колонок.
