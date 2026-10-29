# Cadence — Cream Orbit design prototype

Отдельный React/Vite demo. Не импортирует рабочие frontend/backend и не вызывает их API.
Все fixtures синтетические. State хранится в памяти этой вкладки и сбрасывается при reload.
Нет login, платежей, AI, настоящих uploads или внешней загрузки URLs.

Локальный preview текущей сессии: http://127.0.0.1:4176/ .
Routes: `#home`, `#brand-identity`, `#plans`, `#about`,
`#workspace/manager`, `#workspace/identity`, `#workspace/strategy`,
`#workspace/content`, `#workspace/performance`.

Последовательность: Demo workspace → Review source → Allow → Prepare Identity → Approve →
Strategy → Prepare → Approve → Content. Existing posts — явно синтетические examples.
Performance apply создаёт новый DRAFT; approved history сохраняется.

Commands из этой директории:

```text
pnpm install --frozen-lockfile --ignore-scripts
pnpm dev --host 127.0.0.1 --port 4177 --strictPort
pnpm test
pnpm run test:sites
pnpm run build
node scripts/browser-qa.mjs
```

Browser QA использует installed `design-taste-frontend/scripts/lib/cdp.mjs` из этого repository,
Node22+ и локальный Chrome/Edge. Screenshot outputs идут в соседний `craft/`.
Browser profiles не включаются в артефакты. В Windows sandbox может потребоваться уже
согласованный пользователем unsandboxed headless run для loopback и Chrome crashpad.

Sites project: `appgprj_6ad5f400cb908191bf93dccbcca4d1ce`. Manifest сохранён официальным helper.
Site зарегистрирован private/unpublished; deploy и публичного URL нет. Worker/packaging
template сохранён. `dist/client`, `dist/server` и `dist/.openai/hosting.json` создаются build.

[Design system](../design-system.md) · [QA](../design-qa.md) · [UI→API](../ui-api-mapping.md).


## GSAP motion

GSAP3.15.0 — direct dependency этого prototype, не импорт из рабочего frontend.
Motion загружается отдельным optional chunk. ScrollTrigger/SplitText/Flip/CustomEase
используются только по назначению; scope cleanup, keyboard/touch/reduced motion и
fail-open реализованы в `src/motion-hooks.js` / `src/motion-engine.js`.

```text
pnpm run lint
pnpm run typecheck
node scripts/motion-qa.mjs
node scripts/browser-qa.mjs http://127.0.0.1:4176/ ../../craft/motion/flows
```

Lint/typecheck переиспользуют уже установленный repository toolchain в `frontend/node_modules`
без изменения рабочего frontend. Typecheck целевой: два новых JS motion-модуля через checkJs;
legacy JSX не мигрировал на TypeScript. Production build/GSAP dependency самостоятельны.
[Подробный motion report](../motion-report.md).
