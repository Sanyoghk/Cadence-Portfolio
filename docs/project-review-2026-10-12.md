# Cadence: состояние проекта, user stories и API — 2026-10-12

## Что было сделано

Выполнен обзор локального backend на ветке `mvp`, исходный HEAD
`3e86c02bcc116ce4e637375bfa7fd56d12e2cba4`. До review рабочее дерево чистое.
Прочитаны controllers, ключевые use cases/security/configuration, OpenAPI, migrations inventory,
ADR, product scope, планы и актуальные GitHub #107/#121/#120. Это обзор возможностей и
архитектурных границ, а не исчерпывающий построчный аудит каждого класса или penetration test.

**Итог:** реализован существенный backend фундамент с изоляцией Workspace, ingestion,
human review и immutable revisions. Полный пользовательский MVP ещё не готов:
нет web application, реального AI, связки approved Identity → Strategy, content/export,
analytics и recommendations workflows. Доменные классы последних модулей не равны готовым функциям.

Под «доступен endpoint» здесь понимается наличие реализации в текущем локальном source.
Публичный deployment и доступность этих маршрутов в интернете не проверялись.
Новые #121 commits локальные; актуальный issue подтверждает отсутствие push этого scope.
Удалённый CI старых commits не подтверждает текущий HEAD.

## Существенные выводы review

| Приоритет | Наблюдение и практическое последствие | Основание |
|---|---|---|
| Блокер полного AI journey | Approved Identity пока не является источником Strategy. Approval Identity сам по себе не влияет на следующую генерацию Strategy | `StrategyBuildTransactions.signature()` читает `Brand.getSignature()`; approved Identity ID в command не передаётся |
| Блокер production AI | Реальные Brand/Strategy adapters отсутствуют; production отказывает, local/test используют deterministic fake | `BrandAnalysisConfiguration`, `StrategyGenerationConfiguration`, открытый #120 |
| Блокер evidence-grounded AI | Analysis использует declared brandName/industry; загруженные документы, URL и reviewed TEXT пока не становятся model input/citations | `BrandAnalysisTransactions`, ADR0013, #121 |
| Блокер законченного продукта | Нет frontend, Calendar/Post API, CSV export, metrics/analytics/recommendation API | Controller inventory и #107 |
| Открытый lifecycle gap | Изменение parent version делает admission устаревшим; UNIQUE(evidence_id,source_version) запрещает повторный review той же source version. После withdrawal повторное admission также невозможно | ADR0013, V31/V32, admission API contract |
| Ограничение надёжности | AI actions synchronous; нет durable AI Jobs, request idempotency и полноценных AI cost budgets. Повторный успешный Strategy POST создаёт новую revision | `BuildStrategyUseCase`, Strategy API/ADR0010 |
| Условие безопасного onboarding | Первичная role/membership берётся из подписанных Clerk claims; их содержимое должно быть server-controlled. Подпись JWT не делает user-editable metadata безопасными | `CadenceIdentityService.provision()`, Clerk contract |
| Ограничение локального запуска | Default local — H2 in-memory/create-drop, без Flyway, fake AI. Это не persistence окружение для реальных данных | `application.properties`, `application-local.properties` |
| Долг документации | В планах остались старые baseline-фразы; в product scope Docker назван optional, тогда как полный verification требует Docker. #120 содержит устаревшее «следующая #102» | Текущие source/configuration и более новые continuation sections имеют приоритет при оценке реализации |

Это подтверждённые ограничения и незавершённые требования, а не утверждение о найденной
эксплуатируемой уязвимости. Отдельной новой vulnerability в рамках этого обзора не доказано.

## Какие user stories реализованы

| User story от лица creator | Статус и фактическая граница |
|---|---|
| Я вхожу через Clerk и работаю со своими данными | JWT validation и локальная authorization реализованы. Live Clerk configuration и полноценный signup UI этим review не проверены |
| Я создаю Workspace, меняю его данные, смотрю список, архивирую | Реализовано. OWNER-only; default максимум 3 Workspace/account, лимит configurable |
| Я создаю Brand в своём Workspace и управляю им | Create/read/list/update/archive реализованы; hard delete/restore/transfer API отсутствуют |
| Я добавляю текст, URL или файл как источник информации о Brand | JSON TEXT/URL и multipart PDF/PNG/JPEG, deduplication и ingest Jobs реализованы. Registration не означает готовность к AI |
| Я получаю evidence из Instagram/TikTok | Endpoint и official API adapters есть; нужны server-bound credentials, approved accounts/configuration. Live OAuth onboarding/refresh не завершены |
| Я отслеживаю обработку источника | Scoped Job status GET реализован; cancel/retry management API отсутствует |
| Я проверяю точный текст перед будущим AI-использованием | Preview, admission, current snapshot и withdrawal реализованы для TEXT. Это не отправка в AI и не проверка истинности текста |
| Я создаю Brand Identity вручную | Реализованы palette, fonts, voiceUp/voiceDown, sampleCaption, draft edit, history, approval |
| Я создаю Identity из результата анализа | Копирование текущего успешного analysis receipt реализовано; endpoint сам не вызывает AI. `expectedVersion` здесь относится к Brand |
| Я редактирую approved Identity без потери истории | Создаётся новый DRAFT с lineage; прежняя approved revision сохраняется |
| Я генерирую и утверждаю Strategy | REST lifecycle реализован, generation работает с configured local fake; production generation disabled. Источник — legacy signature |
| Я получаю полный Strategy Playbook | Частично: cadence/timezone/bestTimes и weighted content pillars. Полные audience/goals/channel mix/whitespace/four-week outline отсутствуют |
| Я получаю календарь, посты и CSV | Не реализован законченный workflow/API; `SocialPost` entity не заменяет его |
| Я загружаю метрики и получаю рекомендации | Не реализован workflow/API; `PostMetric`/`Recommendation` — доменный задел |
| Я работаю через готовый web UI | Нет frontend application; HTML prototype — reference |

## Полный каталог endpoints

32 уникальные пары HTTP method + path. POST evidence имеет два controller handlers
для JSON и multipart, но считается одной парой method/path.
Во всех таблицах prefix `/api/v1`. Все маршруты, кроме health, требуют Bearer JWT и OWNER;
resource routes дополнительно ограничены Workspace membership.

### Служебные

| Method | Path | Назначение |
|---|---|---|
| GET | `/health` | Публичная строка о работе backend; не readiness check PostgreSQL/providers |
| GET | `/openapi` | Защищённый JSON API contract; это не Swagger UI |

### Workspace и Brand

| Method | Path | Назначение |
|---|---|---|
| POST | `/workspaces` | Создать Workspace, 201 |
| GET | `/workspaces` | Список своих Workspace |
| GET | `/workspaces/{workspaceId}` | Прочитать Workspace |
| PATCH | `/workspaces/{workspaceId}` | Изменить name/industry с expectedVersion |
| POST | `/workspaces/{workspaceId}/archive` | Архивировать с expectedVersion |
| POST | `/workspaces/{workspaceId}/brands` | Создать Brand, 201 |
| GET | `/workspaces/{workspaceId}/brands` | Список Brand |
| GET | `/brands/{brandId}` | Прочитать Brand |
| PATCH | `/brands/{brandId}` | Изменить name/websiteUrl/Instagram/TikTok handles с expectedVersion |
| POST | `/brands/{brandId}/archive` | Архивировать с expectedVersion |

### Evidence и Jobs

| Method | Path | Назначение |
|---|---|---|
| POST | `/brands/{brandId}/evidence` | JSON `{type,value}` для TEXT/URL либо multipart part `file`; 202 с Job Location |
| GET | `/brands/{brandId}/evidence` | Paginated evidence metadata |
| POST | `/brands/{brandId}/evidence/social` | Выбор provider; server-bound social ingestion, 202 |
| GET | `/jobs/{jobId}` | Статус своего ingest Job |
| GET | `/evidence/{evidenceId}/ai-admission` | Preview точного TEXT и version/checksum tokens |
| POST | `/evidence/{evidenceId}/ai-admission` | Подтвердить review: sourceVersion, brandVersion, workspaceVersion, contentChecksum; 201 |
| GET | `/evidence/{evidenceId}/ai-admission/{admissionId}/snapshot` | Получить актуальный reviewed snapshot |
| POST | `/evidence/{evidenceId}/ai-admission/{admissionId}/withdrawals` | Отозвать admission, 201; повтор 409 |

AI admission сейчас отклоняет URL/PDF/images/social. CLEAN scan, artifact extraction и
регистрация источника не эквивалентны human approval. Public file download API отсутствует.
Workers/providers могут быть отключены: принятие регистрации не гарантирует завершение Job.

### Analysis и Identity

| Method | Path | Назначение |
|---|---|---|
| POST | `/cadence/brands/{brandId}/analyze` | Анализ declared Brand fields и публикация legacy signature; provider-dependent |
| POST | `/brands/{brandId}/identities` | Создать manual DRAFT из content, 201 |
| GET | `/brands/{brandId}/identities` | История Identity revisions |
| POST | `/brands/{brandId}/identities/from-analysis` | Копировать qualified current analysis в DRAFT, 201 |
| GET | `/identities/{identityId}` | Прочитать revision |
| PATCH | `/identities/{identityId}` | Полная замена editable content: 200 для DRAFT, 201 для новой ветки approved/locked |
| POST | `/identities/{identityId}/approval` | Подтвердить сохранённый DRAFT с expectedVersion |

### Strategy

| Method | Path | Назначение |
|---|---|---|
| POST | `/brands/{brandId}/strategies` | Generated DRAFT из current signature, 201; не manual-create endpoint |
| GET | `/brands/{brandId}/strategies` | История revisions |
| GET | `/strategies/{strategyId}` | Прочитать revision |
| PATCH | `/strategies/{strategyId}` | Полная замена cadence/contentPillars; 200 или новый DRAFT с 201 |
| POST | `/strategies/{strategyId}/approval` | Approval сохранённого DRAFT с expectedVersion |

### Общие API contracts

- Pagination: default page=0,size=20; size 1..100, page 0..10000, allowlisted sort и UUID tie-breaker.
- `version` — номер бизнес-ревизии; `lockVersion` — concurrency token. Для edit/approval
  `expectedVersion` равен последнему `lockVersion`, не номеру revision.
- `from-analysis.expectedVersion` — Brand.lockVersion; admission имеет три самостоятельных tokens.
- PATCH Identity/Strategy — полный replacement content, не JSON Merge Patch.
- Actor, audit time, ownership, status и provenance назначаются сервером; DTO не открывает Entity.
- Anonymous 401, запрещённая роль 403; foreign resources дают 403 или 404 в зависимости от
  существующего route contract. Не обещаем универсальный 404 для всех routes.
- Invalid input 400; stale/conflicting/archived writes 409; unavailable AI provider 503.
- Approved/locked edit создаёт новый ID: клиент должен использовать возвращённые ID/Location.
- Отдельного endpoint перевода в LOCKED нет. DELETE, restore, members management и `/me` нет.
- Repeated generated create/approved edit не идемпотентны: после ambiguous timeout нужна
  проверка history перед retry.

## Как это работает / Runtime Flow

Обычная бизнес-операция:

```text
Bearer JWT → Spring Security signature/issuer/time/audience/azp validation
→ CadenceIdentityService: local user + memberships
→ Controller: OWNER + request DTO validation
→ Use case: scoped resource lookup + domain rules
→ Repository → PostgreSQL constraints / transaction → response DTO
```

Approval/review дополнительно проверяют actual active OWNER/membership в новой transaction.
Parent-first locks согласуют конкурирующие archive, edit, approval и withdrawal.

Generation:

```text
Короткая snapshot transaction → validated port call вне DB transaction
→ completion transaction → permissions/source/version/deadline checks
→ публикация результата; Strategy остаётся DRAFT
```

Текущие независимые пользовательские пути:

```text
Workspace → Brand → manual Identity DRAFT → edit → approval → новая DRAFT revision
Workspace → Brand → local fake analysis → legacy signature → fake Strategy → edit → approval
Brand → TEXT registration → preview → admission → current snapshot → withdrawal
```

Между третьим путём и AI generation пока нет consumer. Между approved Identity первого
пути и Strategy второго пути пока нет source binding.

## Архитектурное решение

Java 21 / Spring Boot 3.5.16, Maven, Spring Security resource server, Spring Data JPA,
PostgreSQL/Flyway V1–V32. Один deployable backend, разделённый по слоям, с ports/adapters
для внешнего I/O. Вызов внешнего provider отделён от DB locks. Это уменьшает время удержания
connections и блокировок. Цена — необходимость повторных проверок после внешнего вызова.

Существующую архитектуру целесообразно сохранять: microservices сейчас увеличили бы
сложность transactions, authorization и эксплуатации без показанной потребности.
При этом «modular monolith» описывает направление; пакеты по слоям сами по себе не являются
автоматически enforced независимыми бизнес-модулями.

Сильные стороны: scoped queries, отдельные DTO, OSIV=false, immutable approved history,
version tokens, короткие transactions, реальные PostgreSQL constraint/race tests,
контролируемые provider failures, подробные ADR и migration history.

Trade-offs: SQL triggers и grants усиливают целостность, но требуют PostgreSQL tests и
аккуратной эволюции schema. Две Identity модели временно усложняют понимание source of truth.
Paginated collection reads могут порождать дополнительные queries; границы page ограничены,
но нагрузочная стоимость не измерена. HTTP transaction annotations ещё встречаются в старых
Workspace/Brand controllers, тогда как новые read DTO формируются внутри application transactions.

## Security Review

Защищаем tenant content, private files, approval/review history, credentials и provider budgets.
Caller — OWNER; внешние trust boundaries: JWT issuer, HTTP input, uploaded bytes/URLs,
stored user text и будущий model output. Shared runtime DB account не даёт независимой tenant RLS.

**Threat:** IDOR/BOLA и повышение роли.
**Protection:** JWT validation, local membership authority, OWNER policy, scoped lookups,
method security на use cases; approval/review перепроверяют actual account.
**Verification:** SecurityIntegrationTest, UseCaseMethodSecurityTest, Identity/Strategy/admission HTTP
и PostgreSQL suites. Live Clerk metadata policy остаётся внешним обязательным условием.

**Threat:** stale writes и переписывание approved/audit history.
**Protection:** expectedVersion, parent-first locks, immutable branching, FK/UNIQUE/checks/triggers,
отдельные runtime grants; source/deadline completion checks.
**Verification:** PostgreSQL lifecycle, migration, direct SQL и concurrency tests.

**Threat:** SSRF, malicious upload, подмена MIME, parser resource exhaustion.
**Protection:** bounded/pinned public URL fetching, quarantine, signature/type/size checks,
ClamAV, private storage, isolated renderer, deadlines/quotas/lease fencing.
**Verification:** URL policy, file validation, MinIO/ClamAV/renderer/full-provider suites.
Deployment IAM/TLS/network/sandbox подтверждаются отдельно от fixtures.

**Threat:** mass assignment, injection и раскрытие данных.
**Protection:** writable DTO allowlists, validation/domain invariants, parameterized persistence,
safe Problem Details и ограниченные responses. Хранимый markup остаётся текстом.
**Verification:** HTTP negative contracts и SQL invariants. Frontend output escaping пока
не проверяется, потому что frontend отсутствует.

**Threat:** CSRF.
**Protection:** authentication только explicit Bearer header, STATELESS; поэтому CSRF отключён.
**Verification:** SecurityConfiguration inspection. Введение cookie auth потребует нового review.

**Threat:** злоупотребление real AI, prompt injection и supply-chain vulnerabilities.
**Protection:** production AI disabled; future output — недоверенные данные. Ingest имеет bounds,
но это не полный AI rate/cost budget. CI содержит dependency inventory + OSV и pinned Actions.
**Verification:** adapter configuration negatives и inspection workflow; новый vulnerability scan
в этом review не выполнялся. SAST/container scan и live AI policy не считаются закрытыми.

Обход одного application validation слоя ограничивается PostgreSQL constraints/grants;
компрометация shared runtime credentials всё равно выходит за гарантию HTTP tenant isolation.
Admission подтверждает review текста, но не делает текст безопасной инструкцией для модели.

## Тесты

Новый запуск на проверяемом source завершился 2026-10-12, 03:00 Europe/London:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress clean verify *> var/project-review-2026-10-12-verify.log
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
git diff --check
```

- Maven BUILD SUCCESS за 4:03: 101 suites, 840 tests, 0 failures, 0 errors,
  3 Windows filesystem skips; 837 tests выполнены успешно.
- Отдельно Linux filesystem: 35/35 без skips, включая три Windows-skipped сценария.
  Перекрывающиеся tests не складываем с Maven count.
- Mandatory PostgreSQL/MinIO/ClamAV/renderer/full-pipeline report gates passed без required skips.
- Python scripts 10/10; renderer Python 29/29 в существующем image
  `cadence-verification-20261008`; отдельный rebuild этого image не выполнялся.
- Compilation, JAR packaging, Spotless check и JaCoCo passed; line coverage
  5048/(5048+299) = 94.41%. Coverage — не доказательство отсутствия всех defects.
- Python scripts сначала получили PermissionError при работе с temp directory внутри sandbox;
  повтор вне sandbox прошёл 10/10. Docker также потребовал выхода из sandbox.
- Существующие warnings: deprecated APIs/unchecked test code и JDK class-data-sharing;
  они не были исправлены или подавлены этим docs-only review.

Результаты получены в этой задаче, а не перенесены из прежних отчётов.

## Security Tests и Edge Cases

Проверяемые suite сценарии: anonymous/EDITOR/VIEWER, чужой Workspace/resource/receipt,
forged actor/scope, inactive account/revoked membership, stale versions, malformed input,
mass assignment, archived writes, repeated approval/withdrawal, parent/source changes,
SQL audit/content tampering, MIME spoofing, unsafe URLs, provider failures/deadlines,
concurrent edits/approval/archive и runtime privilege restrictions.

Наличие теста не означает проверку production deployment. Фактический итог запусков указан выше.
Особенно важно: withdrawal сохраняет историю; ранее выданные bytes невозможно отозвать,
поэтому будущий AI consumer обязан снова проверить источник перед публикацией результата.

## Изменённые файлы

Только `docs/project-review-2026-10-12.md` — этот отчёт. Product code/configuration/tests
не изменялись. Диагностический log находится в ignored `var/`.

## Непроверенные аспекты

Live Clerk, реальные Meta/TikTok accounts/OAuth, real AI/provider privacy, deployed networking,
IAM/TLS, production startup и backup/restore, нагрузка, fresh OSV/SAST/container scan,
hosted AppArmor и exact-HEAD remote CI не проверены в этом review.
Проверять отдельно: controlled live smoke с согласованной configuration, deployment qualification,
restore rehearsal, load tests и CI на отправленном SHA. Без этого нельзя заявлять commercial readiness.
Новый packaged-JAR startup smoke не запускался; Spring contexts запускаются внутри test suite.

## Что мне важно понять

Backend endpoint, работающий local fake и готовый коммерческий user journey — разные уровни.
Зелёный suite доказывает проверенные contracts, но не наличие незапрограммированных функций.
Главный текущий разрыв — соединение уже реализованных частей, а не добавление ещё одной Entity.
Evidence ingest, human review, model generation и approval — четыре самостоятельных решения.
Объединение их одним статусом потеряло бы контроль над происхождением и разрешением использования.

## Что мне стоит изучить

1. Изоляция арендаторов (tenant isolation): UUID не даёт доступ; запрос ограничивается membership.
2. Оптимистическая блокировка (optimistic locking): lockVersion предотвращает потерю чужого edit.
3. Происхождение данных (provenance): какой source/provider/prompt породил результат; ручной edit
   не становится новым AI generation receipt.
4. Проверка перед публикацией (publication fencing): после внешнего I/O снова проверяются source,
   права, deadline и lifecycle, поскольку они могли измениться во время вызова.
5. Эшелонированная защита (defense in depth): HTTP validation, application rules и DB constraints
   защищают один invariant на разных границах.

## Production considerations и следующий порядок

1. Завершить #121: re-review policy, bounded evidence selection/consumption, validated citations,
   non-TEXT eligibility и approved Identity → Strategy source/completion binding.
2. Реализовать отдельно durable AI Jobs, budgets/idempotency и недостающие Playbook fields.
3. Content/calendar/review и CSV export; затем metrics/analytics/recommendations и frontend.
4. #120: согласовать provider/model/privacy/retention/cost, добавить реальные adapters и live smoke.
5. Release gates: user journey E2E, auth onboarding, rate limits/audit/monitoring,
   secrets/runtime roles, security scans, deployment/backup/restore/load qualification.

Frontend можно проектировать параллельно по стабильным API contracts, но UI не закрывает
отсутствующую серверную цепочку. Push/merge/deployment в рамках review не выполняются.

## Факты, выводы и assumptions

- **Факты:** 32 method/path, source/configuration/ADR inspection, #107/#121/#120 открыты,
  перечисленные implemented workflows и явные отсутствующие source links/providers.
- **Инженерный вывод:** backend фундамент развит, но end-to-end MVP и production readiness
  пока не достигнуты; следующий высокий приоритет — связность Identity/evidence/Strategy.
- **Assumptions / отдельная проверка:** live claims server-controlled, deployed secrets/network/IAM,
  реальные provider agreements, production limits и публичная доступность приложения.

Источники: [master checklist](https://github.com/Sanyoghk/Cadence/issues/107),
[Identity scope](https://github.com/Sanyoghk/Cadence/issues/121),
[real adapters](https://github.com/Sanyoghk/Cadence/issues/120),
[OpenAPI](../src/main/resources/openapi/cadence.json),
[Identity API](brand-identity-api.md), [Strategy API](strategy-api.md),
[admission API](evidence-admission-api.md).
