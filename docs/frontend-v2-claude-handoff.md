# Claude: отдельный второй frontend Cadence

Дата: 2026-10-21. Baseline inspection: `mvp` / `575520e`.
Пользователь подтвердил **Claude Code Desktop на Windows**. Инструкция относится к вкладке
Code; JSON settings ниже применимы только в соответствующей среде исполнения Desktop.
Ниже рекомендованная конфигурация, **не уже применённые настройки**. Frontend не создавался.

## Организация работы

Рекомендация: отдельная папка и самостоятельный Git repository `cadence-frontend-v2`.
Для Desktop+WSL предпочтителен Linux path, например `/home/<user>/projects/cadence-frontend-v2`;
для native Windows возможен `C:/Users/<user>/eclipse-workspace/cadence-frontend-v2`, но без
встроенной shell-изоляции. Claude открывает только новый repository.
Основной `C:/Users/<user>/eclipse-workspace/cadence` остаётся рабочим каталогом backend.
Второй frontend получает собственные `package.json`, lockfile, dependencies, tests и build.
Первый `frontend/` не используется как общая библиотека и не изменяется. Проверенные patterns
можно передать отдельными копиями; symlinks, junctions и импорты в основной repository запрещены.

Это инженерная рекомендация для требуемой изоляции. Отдельная ветка в том же checkout
не разделяет файлы. Worktree разделяет checkout, но разделяет с исходным repository Git
metadata; frontend-only repository проще ограничить по доступу. Позднее перенос нового
приложения в `frontend-v2/` основного repository выполняется отдельным reviewed изменением.

На Windows предпочтителен Claude Code внутри WSL2 со включённым sandbox. Для более строгой
границы можно выделить container/VM: frontend доступен на запись, snapshots contracts только
на чтение, основной repository не смонтирован, host/Docker socket и privileged mode отсутствуют.
Одна папка или текстовый запрет не обеспечивают такую границу сами по себе.

## Конкретные шаги в Claude Code Desktop

1. Открыть вкладку **Code** и новую сессию. В **Environment selector** выбрать установленный
   дистрибутив в разделе **WSL**, например Ubuntu. Нужны WSL2 и Git внутри дистрибутива;
   наличие Desktop само по себе не подтверждает их установку.
2. В **Project folder** выбрать только новый frontend repository. Для WSL рекомендована
   папка внутри Linux filesystem, например `/home/<user>/projects/cadence-frontend-v2`.
   Не выбирать основной `cadence` или весь `eclipse-workspace`. Если выбран saved environment,
   проверить его список папок отдельно.
3. В **Permission mode** выбрать **Plan** для initial design/API inventory, затем **Manual**
   для implementation. В старых версиях Manual назывался Ask permissions. Accept edits
   использовать только после проверки границ; Bypass permissions не включать. Выбор режима
   в UI может перекрыть `defaultMode` из JSON — проверять фактический selector.
4. В **Settings → Claude Code** оставить **Browser tools** включённым для preview нового UI.
   **Allow bypass permissions mode**, если этот переключатель доступен, оставить выключенным.
   Не подключать инструменты записи в основной repository, DB или deployment.
5. Поместить `CLAUDE.md`, `.claude/settings.json` и read-only reference snapshots в новый
   repository. Начать с промпта ниже. Первый frontend и backend не запускать/перезапускать
   из этой сессии; новый preview использует отдельный порт и выделенные test credentials.

Источники: [Desktop controls](https://code.claude.com/docs/en/desktop),
[Desktop in WSL](https://code.claude.com/docs/en/desktop-wsl).
WSL — выбор среды исполнения внутри Desktop, переходить на CLI для этого не обязательно.
В native Windows session приведённый WSL JSON не создаёт shell sandbox; для требуемой
технической изоляции использовать WSL2+sandbox либо отдельную container/VM среду.

## Что выбрать в Claude

| Настройка | Значение для этой задачи |
| --- | --- |
| Project directory | Только `cadence-frontend-v2`, не основной `cadence` и не старый `frontend/` |
| Начало работы | Plan для проверки contracts и структуры экранов; затем Manual (`default`) |
| Автоматические edits | При желании Accept edits после проверки границ доступа; только новый repository |
| Shell | Sandbox включён; без unsandboxed retry и без Bypass permissions |
| Additional directories | Не добавлять основной repository через `/add-dir` |
| MCP/plugins/hooks | Только необходимые для UI; без инструментов DB/backend/deploy/GitHub write и произвольного filesystem доступа |
| Browser | Preview нового frontend на отдельном порту; только выделенные test accounts/data |
| Модель Claude | Выбрать доступную пользователю; эта инструкция не требует конкретной версии или тарифа |

Режимы и права описаны в [официальной документации permissions](https://code.claude.com/docs/en/permissions).
`CLAUDE.md` передаёт инструкции, но не является enforced file policy:
[память и инструкции](https://code.claude.com/docs/en/memory).

## Пример settings для WSL2

Создать **в новом repository**, не в основном: `.claude/settings.json`.
Пример предполагает стандартный mount Windows-диска `/mnt/c`; проверить реальные пути.
Не копировать эти Linux paths в native Windows session.

```json
{
  "permissions": {
    "defaultMode": "default",
    "disableBypassPermissionsMode": "disable",
    "deny": [
      "Read(//mnt/c/Users/<user>/eclipse-workspace/cadence/**)",
      "Edit(//mnt/c/Users/<user>/eclipse-workspace/cadence/**)",
      "Edit(/reference/**)"
    ]
  },
  "sandbox": {
    "enabled": true,
    "failIfUnavailable": true,
    "autoAllowBashIfSandboxed": false,
    "allowUnsandboxedCommands": false,
    "filesystem": {
      "denyRead": ["/mnt/c/Users/<user>/eclipse-workspace/cadence"],
      "denyWrite": [
        "/mnt/c/Users/<user>/eclipse-workspace/cadence",
        "./reference"
      ]
    }
  }
}
```

Разные слои намеренно имеют разные правила: `Read/Edit` контролируют file tools,
`sandbox.filesystem` — shell и дочерние процессы. В native Windows встроенного shell sandbox
нет; для него нужен WSL2. MCP/hooks вне этого sandbox, поэтому лишние capabilities отключены.
Источники: [sandbox](https://code.claude.com/docs/en/sandboxing),
[settings и приоритеты](https://code.claude.com/docs/en/settings).

Перед работой проверить `/permissions` и `/sandbox`, effective working directory, дополнительные
папки и унаследованные allow rules/excluded commands. Unsupported setting или unavailable
sandbox — повод исправить окружение, а не отключить ограничение. Не выдавать доступ всему
`eclipse-workspace` ради cache/install. Package manager cache и registry/browser dependencies
разрешать точечно после проверки команды; не давать blanket `Bash(*)`.
Проверку запрета записи сначала выполнять на отдельной disposable directory, не на рабочих
backend файлах. Этот JSON проверен только на синтаксис; установленная версия Claude неизвестна.

## Что передать как read-only reference

Владелец backend экспортирует в новый `reference/` только необходимые файлы:

- [product-scope.md](product-scope.md), [план V2](astra-medium-mvp-prompts-v2.md),
  [ADR0042](adr/0042-single-workspace-per-account.md) и agreed design/reference images.
- Snapshot [OpenAPI](../src/main/resources/openapi/cadence.json), пояснения
  [API contract](api-contract.md), [Bearer architecture](adr/0040-frontend-bearer-onboarding.md).
- Manifest: source commit SHA, дата, hashes файлов и матрица existing/planned operations.
  Контракты обновляет backend owner; Claude не исправляет snapshot под свои DTO.
- Только public configuration examples: API base URL, Clerk publishable key/template name.
  Не переносить реальные `.env`, access tokens, database/provider/Clerk secret keys.

Старый ADR0040 описывает multi-workspace flow; это история реализации. Целевой UX задаёт
ADR0042: один Workspace на аккаунт, Brand picker. Новые имена сущностей в плане не доказывают,
что соответствующие endpoints уже существуют.

## Граница «рабочий frontend»

Существующие APIs интегрируются реально с отдельным test backend/account. Отсутствующие
single-workspace, media/publishing/research APIs записываются в `docs/backend-requests.md`
нового repository: сценарий, нужный method/path/schema, ошибка/статус и acceptance criterion.
Claude продолжает независимые UI задачи, но не объявляет неподключённую функцию рабочей.
Fixtures допустимы в изолированной test/story/demo среде с явной маркировкой; не fallback
production API и не доказательство выполненного publishing. Full V2 frontend acceptance
по-прежнему зависит от backend readiness V2-29.

Текущий стек первого frontend: Next.js/React/TypeScript, pnpm, Vitest/Playwright/axe;
версию для нового приложения проверить отдельно. Предлагается сохранить эти технологии,
новую композицию/дизайн выбрать отдельно. Browser authentication — Clerk Bearer, ключи
AI providers и DB в frontend не попадают. Не вводить Next API proxy/BFF/server actions
ради обхода отсутствующего endpoint, CORS или проверки JWT.

Отдельный UI port, например3001, требует разрешённого origin. Inspection показал
`CADENCE_CORS_ALLOWED_ORIGINS` и `CLERK_AUTHORIZED_PARTIES`, а также ограниченный набор
CORS routes/methods в `SecurityConfiguration`. Смена только origin не открывает все APIs.
Изменение конфигурации выделенного test backend/Clerk либо CORS contracts выполняет backend
owner отдельным шагом; Claude не правит основной backend и не отключает browser security.
Нельзя ради demo останавливать первый frontend, занимать его порт или менять shared backend.

## Готовый стартовый промпт для Claude

```text
Разработай второй frontend Cadence в текущем отдельном repository cadence-frontend-v2.
Не изменяй основной repository cadence, backend и первый frontend/. Не запускай там
команды, migrations, formatters, generators или dev servers. Не создавай общие symlinks,
package files или зависимости на исходники первого frontend. Не меняй permissions.
Все изменения, tests, отчёты и commits делай только в новом repository; без push/deploy.

Сначала прочитай reference/product-scope.md, reference/astra-medium-mvp-prompts-v2.md,
reference/0042-single-workspace-per-account.md, reference/cadence.json и manifest.
Проверь фактические file names в reference, не придумывай отсутствующие contracts.
Составь матрицу экран→существующий API→недостающий API и предложи UX/структуру до реализации.
Применяй V2-30–33 как frontend scope; V2-00 не разрешает тебе менять backend/main docs.

Целевой flow: вход через Clerk → один Workspace аккаунта → несколько Brands → assets →
Identity edit/approval → Strategy edit/approval → четыре недели calendar → photo/video
generation или raw-video editing → rework → final approval → publication → reports/reminders.
Workspace manager и создание второго Workspace отсутствуют. Brand context не смешивается.

Используй Next.js/React/TypeScript и отдельный package/lockfile. Подключай реальные APIs
по snapshot contracts, с typed client, loading/empty/error/401/403/409/Job states.
Missing APIs/CORS фиксируй в docs/backend-requests.md; fixtures только в test/demo среде.
Не имитируй в production успешную генерацию/публикацию. Не создавай собственный backend,
DB, AI provider adapters, auth bypass или proxy для обхода backend security.
Clerk Bearer и public configuration; secrets/JWT не в logs, URLs или application storage.
При смене Brand/session отменяй устаревшие requests и не показывай чужой context.

Согласованный дизайн реализуй responsive и accessible. Проверь lint, typecheck, unit,
production build и browser/axe сценарии; live интеграцию проверяй отдельно на test data.
Пиши по-русски. После каждого законченного slice: изменённые файлы, фактические команды/
результаты, security negatives, screenshot evidence и missing backend dependencies.
Не выдавай fixture pass за live acceptance. Если готовности API недостаточно — укажи границу.
```

Этот же scope можно сохранить как `CLAUDE.md` в новом repository. Общие инструкции по
безопасности передавать выбранной копией, а не давать доступ к основному checkout.

## Проверка и отчёт об этой подготовке

Изменены только: [AGENTS.md](../AGENTS.md), [README.md](../README.md),
[план V2](astra-medium-mvp-prompts-v2.md), [progress](mvp-prompt-progress.md), этот handoff.
Sol записан как предварительное предпочтение, без смены модели/effort в приложении.
Новый repository, Claude settings, snapshots и UI не создавались; внешние integrations не включались.

Threat: случайная запись в backend/первый frontend. Protection: отдельный repository,
file permissions + sandbox/изоляция ОС. Verification: settings ещё не применены; требуется
preflight effective rules и disposable negative check в целевой среде.
Threat: подгонка backend/security под UI. Protection: read-only contract snapshots,
backend-requests вместо proxy/bypass. Verification: future contract/browser negative tests.

Runtime flow планируется: browser → Clerk token → существующий REST API → existing
server authorization/domain/DB. Frontend generation/editor не заменяет backend service.
Edge cases для qualification: отсутствующий endpoint, CORS rejection, stale contract,
запоздалый ответ после Brand switch, expired token, unknown outcome POST и failed Job.

Фактически выполнены:

```powershell
python var/scope-refresh-2026-10-20/verify_docs.py
.\mvnw.cmd spotless:check
git diff --check
```

Docs verifier: PASS, 19 Markdown files, 641 существующая локальная ссылка, UTF-8/fences,
36 prompt blocks и их dependency order, архивный body и параллельные plan sections сохранены;
backend/build/config/scripts без изменений. JSON example синтаксически валиден.
Spotless: BUILD SUCCESS, 645 Java files и1 POM clean по cache. Git whitespace: PASS;
обычные LF→CRLF warnings для Markdown. Helper в ignored `var/`, в commit не включён.
Application/security tests не запускались; число passed/failed не заявляется.
Пример не проверялся в установленном Claude. Production readiness не заявлена.
Push/merge/deploy не выполняются этой задачей. Исходные UI изменения сохранены.

Что важно изучить: **sandbox** — ограничения процессов ОС; **API contract** — согласованный
формат запросов/ответов; **contract drift** — расхождение UI с версией backend; **tenant scope** —
область доступа аккаунта и Brand. Каждый механизм решает отдельную часть этой задачи.

Факты: inspection текущего checkout, прочитанные official docs и подтверждённый пользователем
Claude Code Desktop. Инженерная рекомендация: frontend-only repository и layered access
restrictions. Assumptions: версия/режим/тариф, WSL2 и test API readiness не подтверждены. До production нужны реальные
integration/security tests, platform consent UX и отдельная hosting/security qualification.

### Уточнение после выбора Desktop

Baseline `56872d8`; изменён только этот документ: добавлены Desktop controls, WSL environment
и Linux project path; неизвестная среда пользователя заменена подтверждённым Desktop.
Settings/WSL/repository фактически не создавались. Повторно выполнены команды из раздела
проверок выше: docs verifier PASS (19 files, 641 local links, JSON syntax), Spotless
BUILD SUCCESS (645 Java/1 POM по cache), `git diff --check` PASS; runtime permission tests
остаются невыполненными. Push/merge/deployment не выполнялись.
