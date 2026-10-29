# Задание23 — Frontend foundation, Clerk и onboarding

Дата:2026-10-18. Baseline `15ad3ecdaf97f87b894b8debfdcef79588eaa174`, ветка `mvp`,
исходное рабочее дерево чистое. Actual #3/#107 и qualified22 проверены до implementation.
Статус: **23 — QUALIFIED LOCAL (repository scope)**. Frontend/backend gates завершены.
Whole issue#3 и реальная Clerk интеграция не объявлены завершёнными.
Live Clerk signup/login **BLOCKED**, внешняя конфигурация/credentials отсутствуют.

## Что было сделано

Создан отдельный frontend `frontend/`: Next.js16.4.2, React19.2.9, TypeScript5.9.3,
официальный Clerk React SDK6.17.8. Есть landing, signup/sign-in, authenticated Workspace
screen, создание/выбор Workspace, создание/список/чтение Brand. Обязательные состояния:
loading/empty/error/401/403/404/409, archived, uncertain network, отсутствующая configuration.
Responsive layout, подписи полей, keyboard skip link/focus и автоматические axe checks.

[ADR0040](adr/0040-frontend-bearer-onboarding.md) фиксирует архитектуру и threat model;
[setup/runbook](frontend-foundation.md) описывает exact config, API и live gate.
Issue#3 шире задания23: Identity/Strategy/Calendar/Analytics screens24–27 остаются open.
Прототипа/готового frontend в checkout нет; новый shell соответствует creator product scope,
не вводит agency CRM, iframe, billing или publishing.

## Как это работает

Clerk управляет входом пользователя. Browser получает короткоживущий JWT заданного template
и явно прикладывает его к запросу. Backend проверяет подпись/issuer/audience/expiry/azp,
находит local account и заново проверяет OWNER и membership конкретного ресурса.
Frontend не назначает role, не создаёт membership по произвольному ID и не подменяет токен.

Workspace/Brand API уже существовал: implementation этих backend use cases не повторялся.
Добавлен только opt-in CORS для текущих onboarding GET/POST paths. Создание Workspace и
Brand — две независимые операции; потеря ответа POST может означать успешную запись на
сервере. Поэтому UI не повторяет POST автоматически и просит сначала обновить список.

## Архитектурное решение

Выбран browser Bearer и **static export Next.js** с official `@clerk/react`, без BFF,
frontend secret key и authenticated SSR. Это сохраняет existing API trust boundary —
границу доверия между browser input и серверной authorization. Альтернатива cookie/BFF
потребовала бы новый session/CSRF/proxy/secret lifecycle без необходимости для onboarding.
Next server SDK полезен для будущего private SSR, но здесь добавил бы лишний runtime.

Presentation: App Router pages и semantic React components. `AuthRoot` читает только три
явных public build-time settings, `SessionGate` контролирует отображение/redirect.
`CadenceApi` — единственная HTTP boundary: typed request whitelist, runtime response
validation, safe `ProblemDetails`, bounds20/page и512KiB/response,15s deadline, redirect:error,
cookies omitted, no-store/no-referrer. Серверные raw errors/signature/provider fields
не становятся UI data. Schema/form/state/HTTP libraries для этого scope не нужны.

Нет shared/persistent private cache. Account/session React key удаляет прежний private
subtree; Workspace key удаляет прежние Brand state/forms. Cleanup вызывает AbortController,
completion проверяет cancellation. Поэтому старый ответ не записывается в новый Workspace.
Ответ Brand дополнительно обязан совпасть с запрошенным Workspace и resource ID.

Новая dependency нужна только для framework/auth или воспроизводимых dev checks:
Next/React/Clerk, TypeScript/types, ESLint10 + совместимые JS/TS/React Hooks plugins,
Prettier, Vitest/Vite, Playwright/axe и отдельный official Clerk testing helper.
Direct versions закреплены, integrity/transitive graph в pnpm lock. React19.3.1 не выбран,
поскольку не входит в текущий Clerk peer range; React19.2.9 входит. ESLint9 снят с поддержки,
а current eslint-config-next использовал несовместимые ESLint10 peers — вместо override
выбран совместимый ESLint10 stack. `pnpm peers check` не находит conflicts.
`sharp0.35.5` соответствует October security fix; image optimization не используется.

## Security Review

**Threat:** BOLA/IDOR — пользователь подставляет чужой Workspace/Brand, role/owner в body.
**Protection:** Existing server controller/use-case/current membership + OWNER checks;
request whitelist и response scope binding. UUID сам по себе не даёт доступ.
**Verification:** Existing Security/PostgreSQL suites; CORS OWNER200/201 и EDITOR403;
foreign successful response/path injection/mass assignment client tests.

**Threat:** CSRF и доступ постороннего сайта через browser cookies/CORS.
**Protection:** API принимает только explicit Bearer, fetch credentials omitted; CORS по
умолчанию deny, максимум10 exact HTTPS origins (HTTP только loopback), no credentials,
GET/POST и3headers только onboarding paths. Прочие CORS paths denied. CSRF decision API
не меняется: ambient cookie никогда не предоставляет authority. Clerk session — его SDK.
**Verification:** Cookie-only401; allowed preflight без credentials; foreign/null/suffix
origins, forbidden method/header/path403. Backend JWT/resource checks остаются независимыми.

**Threat:** XSS/ошибки раскрывают tokens, SQL, provider/private fields.
**Protection:** React text escaping, нет dangerouslySetInnerHTML/кликабельных untrusted
profile URLs, raw problem detail/instance не отображаются; нет новых application logs.
**Verification:** Malicious `<img onerror>` остаётся текстом; raw private error отсутствует;
malformed UTF8/HTML/JSON/oversize output denied; static export не содержит fixture/secret IDs.

**Threat:** Late response показывает данные прежнего tenant/account.
**Protection:** Keyed private state + cancellation + response Workspace/id validation.
**Verification:** Late old Workspace response, selected private profile/form reset,
pagination selection reset; production session key и conditional unmount inspected.
Live account-switch/revocation SDK behavior требует отдельной реальной qualification.

**Threat:** Configuration/redirect превращает client в утечку Bearer или server-side fetch.
**Protection:** Explicit fixed API URL `/api/v1`, HTTPS/loopback HTTP, no credentials/query/
fragment; typed UUID paths, redirect:error. Frontend не имеет server fetch/proxy capability.
**Verification:** Unsafe base/path/missing token tests; standalone static deployment inspection.
Оператор по-прежнему обязан задать доверенный API origin; проверка синтаксиса не удостоверяет
владение доменом и не заменяет deployment review.

**Threat:** Resource exhaustion/duplicate create/supply chain.
**Protection:** Input/page/response/deadline bounds, no auto POST retry, synchronous submit
guard, backend quotas; exact dependencies/lock, compatible peers, CI audit.
**Verification:** Bounds/overflow/malformed/byte cap/double submit/uncertain POST, actual15s
Chromium timeout; current npm audit0. Existing backend quotas/SQL rules не изменены.

**Defense in depth:** обход UI не обходит JWT, OWNER/membership и DB constraints. Компрометация
browser/XSS остаётся риском для in-memory Bearer; deployment CSP/TLS и Clerk settings нужны
до production. Этот отчёт не объявляет решение commercial production-ready.

## Изменённые файлы

Каждый файл и его назначение перечислены в [полном manifest](prompt23-changed-files.md).
Ключевые components: `AuthRoot`, `SessionGate`, `Onboarding`, `CadenceApi`, `ApiCorsProperties`,
`SecurityConfiguration.corsConfigurationSource`. Новых schema migrations или dependencies
backend нет; V1–V55 и существующие REST DTO/OpenAPI schemas не переписывались.

## Runtime Flow

```text
Public static Next.js shell → Clerk SignIn/SignUp → actual SDK session
    ↓
SessionGate → Onboarding → CadenceApi → getToken(template)
    ↓
Browser fetch (Authorization Bearer, credentials omitted)
    ↓
Spring CORS → JwtDecoder → local CadencePrincipal/current membership
    ↓
WorkspaceController / BrandController → existing OWNER use cases
    ↓
Scoped repositories → PostgreSQL constraints → dedicated response DTO
    ↓
Bounded response validation/scope check → current keyed React state
```

В local contract tests Clerk/session/API подменяются **только отдельным test entry** на4174.
Production static app на4173 берётся из `out/`; тестового auth route/flag в нём нет.
Backend auth tests используют existing deterministic decoder/JWKS fixtures, не live Clerk.

## Тесты

Фактический pnpm executable:
`C:/Users/<user>/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/fallback/pnpm.cmd`.
Команды ниже выполнялись через него из `frontend/` (вызов `& <path> <args>`):

```powershell
pnpm install --frozen-lockfile
pnpm peers check
pnpm lint
pnpm typecheck
pnpm exec tsc --noEmit --incremental false
pnpm test
pnpm build
pnpm exec playwright install chromium
pnpm test:e2e
pnpm audit --json
pnpm test:live
```

- Frozen install/peers PASS. Node24.19.0/pnpm11.25.0;0 peer conflicts.
- ESLint10/Prettier PASS; TypeScript PASS, включая cold `.next` absence check.
- Vitest:2files,**35tests PASS**,0failures/skips.
- Next production static build PASS; landing/sign-in/sign-up/workspaces/not-found exported.
- Chromium: **16/16 PASS**,0skips; axe0violations на landing/desktop/mobile onboarding;
 375px без horizontal overflow, desktop/mobile screenshots визуально проверены.
- Fresh npm audit: **263 dependency entries,0 vulnerabilities** всех уровней.
- `test:live`: **BLOCKED/exit1** до Clerk calls из-за отсутствующих keys/test user/env.
 Это не passed login/signup и не skipped green gate.
- 36 frontend source/config/test file hashes и40 exported artifact hashes frozen.

Backend фактически выполненные команды:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=ApiCorsPropertiesTest,OnboardingCorsIntegrationTest,SecurityIntegrationTest,JwtDecoderTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dspring.test.context.cache.maxSize=8' spotless:check clean verify
```

Targeted final: **41/41PASS**,0failures/errors/skips. Full result: Full2270/0failures/errors,4Windows filesystem skips покрыты Linux37/37;2266 executed PASS, 181 suites. Spotless/JaCoCo PASS: line94.17%, branch71.16%.
Финальный clean full run — отдельный фактически выполненный запуск, не объединение targeted XML.
Python `python -m unittest discover -s scripts -p 'test_*.py'`: **10/10PASS**.
Mandatory PostgreSQL/provider reports, Linux filesystem, renderer, startup и SBOM:
PASS: mandatory PostgreSQL/MinIO/ClamAV/renderer reports без required skips;
Linux filesystem37/37; renderer29/29; fresh Maven SBOM194/OSV0 и independent inventory verifier.
Packaged startup: health200, 38 protected anonymous operations401, discovery401/fixedBearer,
exact CORS preflight200 без credentials, foreign origin403, cookie-only401. Own process stopped,
18100 listener released. 746 backend/build/config hashes стабильны,
643 compiled resources совпадают с JAR.
JAR SHA256: `5547200ad588939283e7fd333edc32facc601bf4143a1ea029d61fb32481c7a9`.

Дополнительно фактически выполнены:

```powershell
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts/run_linux_storage_tests.ps1
docker build --tag cadence-renderer-prompt23 docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-prompt23 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\mvnw.cmd --batch-mode --no-transfer-progress org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=prompt23-current-bom.cdx' '-DoutputDirectory=var'
.\var/osv-scanner-2.5.1.exe scan source -L var/prompt23-current-bom.cdx.json --all-packages --format=json --output-file=var/prompt23-osv-results.json
python scripts/verify_dependency_scan.py var/prompt23-current-bom.cdx.json var/prompt23-osv-results.json
.\var/prompt23-startup.ps1
git diff --check
```

Python здесь вызывался через bundled `C:/Users/<user>/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`.
OSV2.5.1 exe checksum сверён: `25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.
Warnings: existing JSON/S3 deprecations/unchecked fixtures, Mockito/JVM/Hibernate notices,
Hikari reconnect после остановленных test containers, expected SQL negative errors;
CycloneDX meta:enum/deprecated schema warnings при успешном build/verifier.
Playwright NO_COLOR/FORCE_COLOR warning не изменяет results. Required checks не подавлялись.

Наблюдавшиеся red→green проверки не скрыты:

1. Targeted CORS test потребовал403 для незаявленного path, тогда Spring preflight дал200
   без allow headers. Добавлен explicit deny fallback; negative assertion сохранён.
2. Browser run выявил native fetch receiver ошибку (`this.transport` с Window fetch).
   Wrapper исправил фактический Chromium вызов; Node-only test этого не доказывал.
3. Axe выявил два одинаковых navigation labels; теперь Workspace/Brand pagination различимы.
4. Initial full run: Docker выключен,2141reported/3errors/862skips, BUILD FAILURE. Не pass;
   Docker Desktop запущен, повторный full run только после `docker info` успеха, source unchanged.
5. Python sandbox Temp permission errors устранены запуском fixture tests с доступом к temp,
   без изменения assertions. Первый qualification shell loop имел ошибку имени log с `:`;
   browser suite выполнен отдельно и16/16 реальных results сохранены.
6. При review test-only SPA fixture найден преждевременный redirect assertion: теперь тест
   ждёт загрузки отдельной sign-in target и подтверждает0 API calls после загрузки. Усилены
   fixture/test без изменения production code; все frontend gates повторены.
7. CycloneDX goal отказался работать с `-o`; online goal выполнен без compilation и без
   изменения source/classes. Fresh inventory194/OSV0 независимо сверены report verifier.
8. Следующий full run выполнил2270tests, но получил1failure у existing
   `PostgreSqlPerformanceTest.boundedProjectionCapAndActualQueryPlan`: JobDEAD вместо
   SUCCEEDED; SQL23514 `Metrics source or lease unavailable`. Это общий source/lease/time
   guard V52, точное условие в доступном логе не различается. Отдельный test1/1 и затем
   весь class39/39 прошли при **неизменённых source/assertions/limits**. Выполнен ещё один
   clean full run. Первый run не считается PASS; точная первопричина не установлена,
   clock/container/load interaction — лишь гипотеза. Нельзя заявлять, что причина исправлена.

## Security Tests

401/403/404/409/503 UI; actual Bearer-only и cookie denial; foreign/null/suffix origin;
unregistered CORS path, extra header/method, max/unsafe origin config; OWNER/EDITOR; existing
cross-tenant/use-case checks; response foreign Workspace/id; path injection; mass assignment;
unsafe env/secret prefix; malicious names/error detail; invalid UTF8/JSON/HTML/oversize;
late response/aborted token; no request without token; test fixture absent in production.

## Edge Cases

Empty Workspace/Brand list, archived Workspace/Brand,255/2048 field bounds, page boundaries,
deep page rejection, same-name resources keyed by UUID, missing optional links, duplicate
submit, session load/signout, timeout/unknown POST outcome, malformed successful/error body,
changed Workspace during pending read/form. Поведение следует серверному contract; missing
field не превращается в fabricated business data.

## Непроверенные аспекты

Live Clerk signup/login, email verification, server-controlled OWNER defaults, real audience/
azp template, browser→live backend и account-switch/revocation пока недоступны без existing
Clerk development setup/test user. `tests/live` подготовлены, команда явно fails closed.
Перед release выполнить описанный live gate и onboarding с новым пользователем. Остаточный
риск — ошибки внешней Clerk configuration, которые mocks не обнаружат.
Actual JWT template short TTL также требует проверки; sign-out SDK не означает мгновенный
отзыв уже выданного self-contained JWT до expiry. Backend local account/membership checks
сохраняются. Official JWT-template default azp/issuer/expiry contract перепроверен по
[Clerk docs](https://clerk.com/docs/guides/sessions/jwt-templates); claims клиент не создаёт.

Не проводились remote CI/push/deployment, real host CSP/TLS/IAM, load/rate-limit qualification,
browser matrix кроме Chromium, assistive technology manual audit или external penetration test.
Один existing PostgreSQL metrics-import guard failure не воспроизвёлся отдельно/во всём class;
его точная причина остаётся неподтверждённой. Final full result не стирает это наблюдение.
При повторении нужны granular SQL witness/clock diagnostics в отдельной reliability работе;
production clock discipline/load qualification особенно важны. Guard не ослаблялся.
Backend AI/provider gaps ADR0027/#120/#121 не менялись. Future24–27 не реализованы и #3 open.

## Что мне важно понять

Clerk доказывает, кто пользователь; membership backend определяет, что ему разрешено.
CORS управляет доступом browser JavaScript к origin, но не выдаёт права и не защищает от
ручного HTTP клиента. Поэтому backend checks остаются обязательными даже при идеальном UI.

AbortController помогает завершить ненужный запрос, но не откатывает уже принятую сервером
запись. Keyed state/late-completion guard предотвращают смешивание UI данных, а uncertain
POST требует проверки server state перед повтором. Поэтому fixtures и live auth — разные
источники доказательства; нельзя назвать mocked sign-in настоящим входом Clerk.

## Что мне стоит изучить

1. **Bearer authentication** — полномочия передаются явным короткоживущим token header;
   backend проверяет подпись и claims, cookies API не использует.
2. **CORS/preflight** — браузер предварительно проверяет разрешённый origin/method/headers;
   полученный200 не означает authentication или membership.
3. **Гонка ответов (race condition)** — старый запрос может закончиться после смены tenant;
   React key, cancellation и completion checks защищают текущий экран.
4. **Проверка границы во время выполнения (runtime validation)** — TypeScript исчезает после
   compilation, поэтому внешний JSON дополнительно проверяет `CadenceApi`.
5. **Граница доказательства (verification boundary)** — UI fixture подтверждает поведение
   components/client; live Clerk gate подтверждает настоящую интеграцию с provider.

## Production considerations

Deploy только static `out/` с правильными public build values; не dev/fixture server/root.
Настроить существующий Clerk instance/template/approved domains и exact API CORS/azp,
пройти live signup/login/onboarding/revocation. Применить TLS/HSTS, точный hosting CSP с
Next inline-script hashes и нужными Clerk/API domains, no-referrer/nosniff/frame denial,
секреты test runner держать вне frontend. CSP не следует ослаблять wildcard/unsafe-eval
по предположению. Backend abuse controls/load/security review — отдельный release gate.

CI workflow добавляет frozen install/peer/audit/lint/type/unit/build/browser+axe job.
Факт remote execution не заявляется. Push whole outgoing `mvp` range требует действующей
whole-issue policy с unfinished#121; local commit не является deployment approval.

## Git и честный статус

Local commit: `feat(frontend): qualify Clerk onboarding foundation`; exact SHA — Git HEAD после commit. No push/remote CI/issue update/closure/merge/deploy. Task24 не начат.

**Подтверждённые факты:** перечисленные выполненные frontend/targeted checks и code inspection;
финальный full/gates завершены с результатами выше. Предыдущий intermittent rejection не считается исправленным.
**Инженерные выводы:** выбранная static/Bearer архитектура сохраняет existing backend trust
boundary и уменьшает дополнительную session/proxy complexity; defense in depth обоснована
конкретными checks, а не обещанием отсутствия любых уязвимостей.
**Assumptions / требующие проверки:** корректный existing Clerk template/OWNER defaults,
deployed origins/CSP/TLS, живой account lifecycle и дальнейшая operational qualification.
