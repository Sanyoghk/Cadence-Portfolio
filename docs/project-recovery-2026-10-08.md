# Cadence — восстановленный контекст проекта

Дата восстановления: **2026-10-08**. Это рабочий snapshot после восстановления проекта в Codex.
При дальнейшем продолжении сначала проверять фактические Git status/HEAD и актуальные issues.

## Что было сделано

Восстановлены product scope, архитектура, договорённости о работе, настройки local stack,
реализованные workflows, границы verification и точка продолжения. Контекст сохранён в Git
repository, а правила будущей работы — в [AGENTS.md](../AGENTS.md). Application source,
tests, migrations, dependencies и runtime configuration в этой задаче не изменялись.

Исходный код уже находился на диске; создавать его заново или откатывать к старому handoff
не требовалось. Восстановление контекста не означает возврат всех удалённых настроек Codex,
всех когда-либо существовавших чатов, secret values или облачной инфраструктуры.

## Источники и границы восстановления

Прочитаны все доступные страницы трёх чатов Cadence, включая пользовательские решения
и финальные отчёты; результаты сопоставлены с repository и read-only GitHub issues:

| Источник | Идентификатор / назначение |
| --- | --- |
| «Продолжить работу над проектом» | `<private-chat-id>`: исходные договорённости, #117/#111/#114/#97, tempo, Git и AI choices |
| «Провести глобальный security review» | `<private-chat-id>`: независимый review и remediations |
| «Продолжить разработку Cadence» | `<private-chat-id>`: handoff, #102, два Identity slices и восстановление |
| Пользовательский pasted handoff | `Вставленный текст.txt`, attachment `<private-attachment-id>`: правила работы; прежний baseline `90686e5` устарел |
| Repository | Git, README, оба DEVELOPMENT_PLAN, product scope, ADR, contracts, qualification reports, pom.xml и .env.example |
| GitHub | [#107 master checklist](https://github.com/Sanyoghk/Cadence/issues/107), [#121](https://github.com/Sanyoghk/Cadence/issues/121), [#120](https://github.com/Sanyoghk/Cadence/issues/120) прочитаны 2026-10-08 |

В доступном Codex archive чатов нет; доступный ChatGPT archive не содержит Cadence.
Активный список ограничен 50 последними неприкреплёнными к верхней части sidebar чатами
плюс pinned entries; поэтому полнота всей исторической переписки аккаунта не доказана.
Чат «Анализ проекта Git» относится к другому учебному проекту и не использован как Cadence scope.

Проект снова зарегистрирован как **CadenceMVP**, local project ID
`<private-project-id>`, workspace
`C:\Users\<user>\eclipse-workspace\cadence`. Три указанных чата связаны с ним.
Отдельный ChatGPT project **Cadence** имеет ID `<private-project-id>`;
его наличие не доказывает импорт всех его бесед в локальный проект.

При конфликте источников: новые явные решения пользователя и approved product/ADR contracts
определяют требования; actual code/tests и свежие issues определяют степень выполнения.
Исторические estimates и фразы «следующий issue» не заменяют текущий статус.

## Что мы строим

Cadence — self-service SaaS для individual entrepreneurs и content creators. Creator
сам создаёт Workspace и Brand, получает reviewed Brand Identity и Strategy Playbook,
готовит Instagram/TikTok content, затем анализирует результаты и рекомендации.

```text
Creator → Workspace → Brand Identity → Strategy Playbook
    → Content Calendar → Review / CSV export → Performance → Recommendations
```

MVP должен давать законченный пользовательский сценарий для демонстрации в GitHub/CV.
Коммерческая готовность — отдельный milestone с live-provider, security и operational gates.
Prototype — визуальный/interaction reference. Agency CRM, strategist/client terminology,
named pilot и iframe constraint не входят в утверждённые требования.

Основные решения из [product scope](product-scope.md):

- `CadenceUser → Workspace → Brand` — ownership и tenant boundary: граница изоляции данных.
- OWNER — активная MVP role; EDITOR/VIEWER оставлены для будущего collaboration.
- Clerk authentication; Cadence authorization/membership на сервере.
- Human review перед approval/export; immutable approved versions и сохранение истории.
- Strategy Playbook должен включать pillars, goals, audience, tone, cadence/timezone,
  channel mix, whitespace и four-week outline; текущий contract ещё не покрывает всё.
- Instagram/TikTok — первые platforms; CSV export для ручного использования в platform tools.
- Direct publishing, billing, marketplace и agency workflows отложены.
- Recommendations должны ссылаться на metrics; применение создаёт новый Strategy DRAFT,
  а не незаметно изменяет approved version.

## Восстановленные характеристики и опции работы

| Область | Договорённость и текущий предел |
| --- | --- |
| Модель / effort | Прежний пользовательский выбор **Sol / Medium**. Текущий model ID/effort не прочитан и не изменён; название не трактуется как конкретная новая версия модели |
| Темп | Автономная последовательная работа, законченные vertical slices; закрыть issue за turn, если позволяет scope |
| Делегирование | Один агент; subagents/новые implementation чаты только по явной просьбе |
| Роли помощника | Senior Engineer, Architect, Application Security Engineer, Technical Mentor |
| Язык / объяснения | Русский; сначала практический смысл, затем точный механизм; уровень студента второго курса |
| Git | Проверенный slice → local commit; завершённый issue → push → exact-source-SHA CI → закрытие/update #107 |
| Merge / deployment | Отдельное согласование; не разрешены одной лишь просьбой продолжить разработку |
| Безопасность | Security by Design, deny-by-default, server-side ownership, negative tests, defense in depth |
| Отчётность | Краткий ответ плюс подробный docs report; факты отдельно от выводов/assumptions |
| AI | По явному выбору пользователя: port + production fail-closed; real adapter отдельным шагом |
| Secrets | Server-side environment/deployment secrets; реальные .env/credentials не попадают в Git/logs |

Настройки sidebar, pin order, текущая модель/effort, private accounts, реальные credentials
и выбранный hosting из доступных источников не восстановлены и не назначены автоматически.

## Архитектурное решение

Сохраняем модульный монолит (modular monolith) на Java 21/Spring Boot: один backend с
разделёнными responsibilities. Это позволяет сохранять relational transactions и существующие
tests без преждевременного microservice split. [ADR-0001](adr/0001-spring-boot-modular-monolith.md).

| Layer | Responsibility |
| --- | --- |
| presentation | Controllers, versioned request/response DTO, validation, safe Problem Details |
| security | Clerk JWT verification, principal, local role/membership и scope |
| application | Use cases, orchestration, transactions, ports и publication checks |
| domain | Business invariants, immutable content contracts, lifecycle/versioning |
| repositories / infrastructure | Scoped persistence, PostgreSQL, storage/scanner/renderer/AI adapters |

Provider-neutral ports позволяют менять external provider без изменения domain contracts.
Provider output недоверенный: schema/domain validation обязательны; не исполняем raw output.
Trade-off — больше явных contracts и qualification tests, но меньше зависимости business logic
от credentials/provider diagnostics и возможность безопасного fail-closed.

Точный backend stack из pom.xml: **Java 21, Spring Boot 3.5.16**, Spring MVC, Spring Security
OAuth2 resource server, Spring Data JPA/Hibernate, Jakarta Validation, Maven Wrapper, Flyway,
JUnit 5, Mockito, Testcontainers. PostgreSQL — production target; H2 — local/test convenience,
не доказательство PostgreSQL invariants. Flyway содержит **29 versioned migrations V1–V29**.
Frontend **Next.js/React/TypeScript** запланирован; реализованного frontend/package.json нет.

Необходимые cross-cutting mechanisms: bounded `/api/v1` API/DTO, UTC instants,
optimistic `lockVersion`/`expectedVersion`, ordered locks, DB constraints/runtime grants,
durable leases/retries/idempotency и safe provider errors. Business revision `version`
не равна concurrency token `lockVersion`.

## Реализовано и что остаётся

| Scope | Состояние при восстановлении |
| --- | --- |
| Persistence, migrations, Jobs | Foundation реализован; production rollout/recovery отдельно |
| Workspace/Brand lifecycle | Scoped create/read/update/archive и pagination, OWNER policy |
| #117 evidence ingest | Repository implementation завершён: URL/text/PDF/PNG/JPEG, quarantine, S3-compatible adapter, clamd, isolated artifacts, lifecycle/quotas, approved social snapshots |
| #111 API contract | OpenAPI, common safe errors, UTC, bounded pagination для существующих workflows |
| #114 Brand analysis | Validated port, persisted attempts/provenance, fenced current signature publication; production fail-closed |
| #97 Strategy generation | Bounded generation application flow, versioned DRAFT, source/deadline guards; real AI отдельно |
| Независимый review | Шесть подтверждённых defects исправлены, квалификация описана в remediation report |
| #102 Strategy REST lifecycle | Завершён: create/read/list/PATCH, approval, lineage, server audit и immutable history; pushed и source CI documented |
| #121 Identity manual lifecycle | Два законченных local slices: drafts + отдельный approval/approved branching; whole issue IN PROGRESS |
| #121 citations / Strategy source | Ещё не реализованы: evidence-linked provenance/citations и approved Identity → Strategy |
| #120 real AI | Planned; provider/model/data policy ещё не выбраны |
| Durable AI Jobs / budgets | Ещё не завершены; ingest Jobs не закрывают этот scope |
| Полный Playbook / Content / export | Следующие задачи; domain foundations не равны полному workflow |
| Analytics / Recommendations | Следующие задачи; domain models не равны готовому пользовательскому продукту |
| Frontend / E2E / deployed demo | Не завершены |

Review исправил: URL FAILED/metadata constraint mismatch; bypass account Workspace quota;
populated migration upgrade без scan receipt; child mutation/archive race; TEXT/social limits;
stale worker completion. Источник: [remediation report](review-remediation-90686e5.md).
Исправления не являются доказательством отсутствия любых других vulnerabilities.

Evidence `FETCHED`, наличие artifacts и malware `CLEAN` не означают `READY` или human approval.
S3/clamd/renderer contracts implemented не означают qualified commercial cloud deployment.
MinIO — local/integration fixture; live social permissions/OAuth onboarding/refresh — #32/#33.

## Точное состояние Git и точка продолжения

При начале восстановления рабочее дерево чистое, branch **mvp**:

| Commit | Значение |
| --- | --- |
| `863ff753c62daf5719cafa41617cb63ac4749a68` | Текущий product HEAD: Identity approval/history, V29 |
| `e514ad75984016e2a389d5e2763368d1dc5a8ac7` | Identity manual drafts, V28 |
| `b2a6871ead3f2f18d5998db09fe358881282b4a6` | Локальный tracking ref origin/mvp: #102 completion documentation |
| `e400acb8eadbaa5b186b61fa8549e9ed0e70247b` | Qualified/pushed #102 lifecycle source, V27 |
| `9f6f530ac1bf580aa04406bbf1f9322bfa025f24` | Qualified review remediations, V25/V26 |

`git rev-list --left-right --count origin/mvp...HEAD` вернул **0 / 2** до recovery commit.
Это сравнение с локальным tracking ref, не новый fetch server branch. GitHub #121 подтверждает
два unpushed slices. Recovery documentation добавляет отдельный commit сверх этих двух;
не считай его третьей feature revision. Feature push пока не выполняется: #121 незавершён.

Следующий законченный scope:

1. Изучить [ADR-0012](adr/0012-brand-identity-drafts.md), analysis/evidence contracts и actual tests.
2. Спроектировать и реализовать bounded, server-selected evidence snapshots/citations/provenance.
   Client/model не могут выбирать чужие IDs, private storage references или arbitrary URLs.
3. Привязать Strategy capture/completion к конкретной immutable APPROVED Identity revision;
   повторно проверять source/lifecycle/permissions/deadline перед publication.
4. PostgreSQL migrations/grants, domain/HTTP/direct-bean/SQL/concurrency negative tests,
   full verification, startup, docs; по завершении всего issue — push и exact-SHA CI.

Current Strategy generation пока использует legacy Brand.signature. Legacy data не получила
выдуманный approval/provenance; completion #102 не делает approved Identity source готовым.

Очередь из актуального [#107](https://github.com/Sanyoghk/Cadence/issues/107):
`#121 → AI Jobs/budgets/full Playbook gaps → #115/#98/#103 → #99/#104 → #100/#105
→ frontend #3 → #87/#118/#83/#8/#116`.
Frontend slices можно добавлять после стабилизации соответствующих APIs.
В #120 сохранилась старая фраза «следующая #102» — current #107/#121 и Git supersede её.

Прежняя оценка 2026-10-05: 25–41 содержательных turns до demo MVP и ещё 21–37 до production MVP
(46–78 всего). Это исторический forecast до дальнейших slices, не свежая оценка оставшейся работы.
План independent review: baseline; после Identity/Strategy/Content/export; перед production release.

## Как это работает и Runtime Flow

```text
Bearer request → Spring Security / Clerk JWT verification
    → OWNER Controller + validated DTO
    → scoped application use case + actual membership/lifecycle checks
    → domain transition + ordered locks + expectedVersion
    → scoped repository / PostgreSQL constraints and runtime grants
    → immutable response DTO / safe Problem Details
```

Identity: PATCH DRAFT меняет content на месте (200); отдельный approval подтверждает уже
committed content с серверными actor/time. PATCH APPROVED/LOCKED создаёт новый DRAFT
(201 + Location + revisedFromId); source content/audit сохраняются. Approval проверяет actual
DB active OWNER/membership, а не только прежние claims. Invalid token — 400, stale/replay — 409,
anonymous — 401, reserved roles — 403, foreign/missing — одинаковый 404.

Evidence: admission/authorization → bounded validation → внешнее I/O вне долгой DB transaction
→ short transaction с повторными lifecycle/idempotency/source checks → evidence + durable Job
→ claim/lease → scan/extraction → fenced completion. Устаревшая попытка не публикует результат.

## Local stack и configuration options

Defaults ниже прочитаны из .env.example/README; secret values не восстанавливались.
Production flags не включались, services в этой recovery задаче не запускались.

| Option | Зафиксированное значение |
| --- | --- |
| Local evidence app / PostgreSQL / MinIO ports | `18080` / `15432` / `19000` |
| Startup script | `.\scripts\start_local_evidence.ps1 -UpdateSignatures` |
| Secrets для local Compose | Script генерирует ignored values; не выводить их в чат/Git |
| Public API | Только `/api/v1/health`; business APIs требуют JWT/OWNER/membership |
| max Workspaces/account | Default 3, допустимо 1..10, archived тоже учитываются |
| Identity roots/Brand | Lifetime cap 1000; history ограничена, это не rate limiting |
| Pagination | size 1..100, page 0..10000, stable tie-break |
| Evidence non-multipart body | 131072 bytes (128 KiB) |
| Concurrent uploads/Workspace | 2 |
| Evidence rows / registrations per hour | 1000 / 100 |
| Workspace quarantine bytes / objects / writes per hour | 536870912 bytes / 1000 / 100 |
| S3 overall / connect / concurrency | 15 s / 3 s / 8 |
| clamd signature max age / deadline / concurrency | 48 h / 30 s / 2 |
| Renderer deadline | 20 s |
| Workers / production activation / egress / retention acknowledgements | Explicit opt-in; defaults false |

Profiles: local/test H2 convenience; prod PostgreSQL + Hibernate validate + Flyway.
Local isolated evidence network намеренно не даёт live URL fetch/JWKS egress; startup health
не означает live Clerk onboarding. Для полного verification suite Docker обязателен.
Старая фраза product-scope «Docker optional locally» относится к раннему baseline и не отменяет
текущие обязательные PostgreSQL/provider qualification gates.
Production runtime DB user отделяется от schema migration/admin identity.

## Security Review

Threat: чужой tenant/resource ID, stale membership и forged actor.
Protection: OWNER-only, scoped repositories, application authorization, actual DB membership
для approval и server actor/time; независимые PostgreSQL guards/runtime grants.
Verification: в latest lifecycle report зафиксированы HTTP/direct-bean/SQL/concurrency negatives.
В этой recovery задаче их повторный запуск не выполнялся.

Threat: approved history tampering, lost update и stale worker/source publication.
Protection: immutable history/lineage, lockVersion, parent-first locks, DB constraints,
lease/attempt fencing и source/deadline rechecks.
Verification: исторические PostgreSQL migration/runtime/race tests documented в #102/#121 reports.
Approved Identity→Strategy source fencing пока отсутствует и остаётся scope #121.

Threat: SSRF, malicious files, resource exhaustion и model prompt injection/data exfiltration.
Protection: pinned public-IP URL fetching, quarantine, bounded signatures/MIME/size, private clamd,
isolated rendering, deadlines/quotas; model/provider boundary fail-closed без real adapter.
Verification: historical mandatory provider suites; реальные model attack cases/live qualification
остаются #120, включать provider из recovery документа нельзя.

Threat: утечка secrets и превращение старого отчёта в ложную гарантию безопасности.
Protection: читались публичные placeholders, reports и metadata; реальные .env/secret files
не читались. Dates/SHA/scope явно разделены; settings не восстановлены угадыванием.
Verification: recovery diff ограничен тремя documentation/instruction files; product unchanged.
Новые dependencies или SecurityFilterChain changes отсутствуют; новый endpoint не добавлен.

## Тесты и доказательства

**В этой задаче Maven/application/security tests не запускались**: изменены только инструкции
и документация. Выполнены read-only Git/file/chat/GitHub inspection и document checks:

```powershell
git status --short
git log -6 --format='%h %s'
git rev-parse origin/mvp
git rev-list --left-right --count origin/mvp...HEAD
git remote get-url origin
git diff --check
```

Дополнительно Python проверяет существование относительных Markdown file links в трёх
изменённых файлах и отсутствие изменений tracked source/tests/configuration. Точный
invocation и результат записываются в recovery QA section ниже после выполнения.

**Исторический результат для product HEAD 863ff75**, из latest local report и #121:
`.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify` — BUILD SUCCESS;
786 tests, 0 failures/errors, 3 Windows filesystem skips; отдельно Linux filesystem **35/35**,
Python scripts **10/10**, renderer **29/29**, mandatory PostgreSQL/MinIO/ClamAV/renderer reports.
JaCoCo LINE: 4778 covered / 294 missed (**94.20%**). Packaged JAR health 200, пять Identity
operations без JWT — 401. Это записанный прежний запуск, не новый recovery run.

Исторический remote #102 source CI [37261511659](https://github.com/Sanyoghk/Cadence/actions/runs/37261511659):
e400acb, verify/dependency-security success, Linux 746/746 без skips; OSV 165 Maven components
без findings на момент scan. Docs baseline b2a6871 CI37262578710 documented success.
Эти результаты **не квалифицируют** новые unpushed Identity commits или новый recovery commit.
Свежая vulnerability scan/remote CI в recovery задаче не выполнялись.

## Security Tests и Edge Cases

Исторически documented: 401/403/404; invalid/stale/replay tokens; forged actor/widened scope;
actual inactive/wrong-role/no-membership accounts; raw SQL audit/content/lineage tampering;
edit/approve, revocation/archive и parallel branches; cap1000; populated V28→V29 migration;
runtime role может approve/branch, но не переписать immutable source. Подробности:
[lifecycle report](issue-121-lifecycle-slice.md) и [manifest](issue-121-lifecycle-files.md).

Recovery-specific edge cases: старый 90686e5 handoff не откатывает новые commits; историческое
«следующая #102» не отменяет current #121; skipped tests не переименованы в passes; model
preference не объявлена текущей настройкой; сторонний Git project не смешан с Cadence;
не найденные чаты/secret values не выдуманы. Эти проверки — inspection, не новые runtime tests.

## Изменённые файлы

- [AGENTS.md](../AGENTS.md): долговременные project instructions для новых сессий.
- [project-recovery-2026-10-08.md](project-recovery-2026-10-08.md): product/architecture/preferences,
  sources, state, next scope, verification boundaries и recovery report.
- [README.md](../README.md): ссылка на recovery и project instructions для обнаружения контекста.

## Непроверенные аспекты

Не доказана полнота всей истории аккаунта; недоступные/окончательно удалённые чаты не восстановлены.
Current Codex model/effort/sidebar и provider account settings не прочитаны/не изменены.
Не проверялись live Clerk signup/JWT, real AI, production IAM/TLS/sandbox, frontend E2E,
production migrations/load, backup/PITR restore и external audit. Проверять на отдельно
configured staging и выбранных accounts/providers. Repository и fixture tests не заменяют это.

## Что мне важно понять

Repository сохранил выполненную работу; «восстановить проект» здесь означает вернуть точные
решения и контекст, а не переписать код. AGENTS.md хранит постоянные правила, recovery doc —
датированный snapshot, ADR — причины architecture decisions, GitHub — актуальную очередь.
Один старый успешный test run подтверждает конкретный source/scope, а не все будущие commits.

## Что мне стоит изучить

1. Architecture Decision Record (ADR): документ причины выбора архитектуры; здесь сохраняет
   modular monolith, auth, export-first, provider и revision contracts.
2. Граница tenant (tenant boundary): scope данных пользователя; Workspace membership определяет
   доступ, знание UUID не даёт permissions.
3. Происхождение данных (provenance): связь результата с source/model/prompt/version;
   citations и новый Strategy source должны получить проверяемые server-owned references.
4. Безопасный отказ (fail-closed): без qualified real provider production generation отклоняется,
   а не незаметно использует fake.
5. Привязка проверки к версии кода (source qualification): tests/CI связываются с SHA,
   поэтому #102 CI нельзя выдавать за проверку новых Identity changes.

## Production considerations

До commercial deployment завершить approved Identity citations/source integration, real AI
provider/privacy/region/cost decision, AI Jobs/budgets, пользовательский product loop,
rate/audit/retention controls, branch required checks, live auth/provider qualification,
deployment IAM/TLS/sandbox, monitoring и backup/recovery drills. Новая recovery документация
не снимает эти gates и не включает production flags.

**Факты:** три доступных Cadence чата прочитаны до начала; repo/Git/публичная configuration
и #107/#120/#121 inspected; local product HEAD 863ff75 и два unpushed feature commits подтверждены.
**Инженерный вывод:** сохранённые project instructions и snapshot позволяют продолжить текущий
scope без зависимости от памяти одного чата.
**Assumptions/дополнительная проверка:** недоступная история, actual model/effort, secret/account
settings, live deployment и новый exact-SHA CI. Исторические tests не запускались повторно.

## Recovery QA: фактически выполненная проверка документов

До stage/commit выполнена следующая PowerShell команда; exit code 0:

```powershell
@'
from pathlib import Path
import re, subprocess
root = Path.cwd()
files = [root / 'AGENTS.md', root / 'README.md', root / 'docs/project-recovery-2026-10-08.md']
count = 0
for file in files:
    body = file.read_text(encoding='utf-8')
    assert '\ufffd' not in body, f'Invalid encoding: {file.name}'
    assert len(re.findall(r'^```', body, re.MULTILINE)) % 2 == 0, f'Unbalanced code fences: {file.name}'
    for target in re.findall(r'\]\(([^)]+)\)', body):
        if re.match(r'^[a-zA-Z]+://', target) or target.startswith('#'):
            continue
        path = target.split('#', 1)[0].strip('<>')
        assert (file.parent / path).is_file(), f'Broken link: {file.name} -> {target}'
        count += 1
status = subprocess.check_output(['git', 'status', '--short'], text=True, encoding='utf-8')
expected = {'AGENTS.md', 'README.md', 'docs/project-recovery-2026-10-08.md'}
paths = {line[3:].replace('\\', '/') for line in status.splitlines()}
assert paths == expected, f'Unexpected changes: {paths}'
changed = subprocess.check_output(['git', 'diff', '--name-only'], text=True, encoding='utf-8').splitlines()
assert changed == ['README.md'], f'Unexpected tracked product diff: {changed}'
assert subprocess.run(['git', 'diff', '--quiet', '--', 'src', 'pom.xml', '.env.example', '.github', 'scripts', 'DEVELOPMENT_PLAN.md', 'DEVELOPMENT_PLAN_RU.md']).returncode == 0
print(f'Document QA PASS: {len(files)} UTF-8 documents, {count} existing local file links, balanced code fences, exactly 3 intended files, no product/test/configuration changes.')
'@ | python -
git diff --check
```

Результат: **3 UTF-8 documents, 47 existing local file links**, code fences balanced,
ровно три intended files, tracked product/tests/configuration unchanged.
Эта команда проверяет pre-commit working tree; после commit состояние намеренно другое.
После stage `git diff --cached --check` выполнен успешно, включая новые files.
Staged manifest: AGENTS.md, README.md и этот recovery document; других файлов нет.
Git сообщил LF→CRLF warnings для двух новых files: это нормализация окончаний строк
в Windows checkout, не ошибка проверки содержимого. Push/merge/deployment не выполнялись.

## Короткий handoff для нового чата

> Продолжай Cadence в текущем workspace. Прочитай AGENTS.md и
> docs/project-recovery-2026-10-08.md, затем проверь фактические branch/HEAD/status,
> актуальные #107/#121 и ADR-0012. На snapshot 2026-10-08 следующий scope —
> evidence-linked Identity citations/provenance, затем approved Identity→Strategy.
> Работай одним агентом, автономными законченными slices, на русском, с security review
> и фактическими tests. Commit после slice; push после whole issue; merge/deploy отдельно.
> Сохрани existing changes. Не откатывай к 90686e5 и не включай production fake/AI provider.
