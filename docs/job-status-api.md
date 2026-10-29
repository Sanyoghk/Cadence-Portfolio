> Current policy: only OWNER can read Jobs; EDITOR/VIEWER receive 403 even within assigned Workspaces.

# API статуса Jobs

## Текущие typed workflows

`GET /api/v1/jobs/{jobId}` сохраняет ingest `JobResponse` и отдельно поддерживает
AI_GENERATION (`AiGenerationJob`) и CSV_EXPORT (`CsvExportDetails`). Неподдерживаемые/foreign
types не получают доступ автоматически. Новые CSV submission/status/private download описаны
в [CSV API/runbook](csv-export-api.md), [ADR0035](adr/0035-durable-csv-export.md).
Actual active OWNER/membership обязательны; CSV также требует active parents. Исторический
SUCCEEDED не обещает текущую download eligibility; private CSV rechecks источники/expiry/access.

Разделы и201-test результаты ниже — исторический initial ingest slice, не qualification нового
CSV workflow. Актуальные gates нового scope: [report18](prompt18-csv-export-report.md).

## Что было сделано

Реализован следующий участок Task 7.1: `GET /api/v1/jobs/{jobId}` для существующих `BRAND_EVIDENCE_INGEST` Jobs. JSON и multipart регистрация evidence возвращают `202` с относительным Location на этот endpoint; структура response body сохранена. Идемпотентный повтор регистрации возвращает ту же ссылку на Job.

## Контракт

GET требует Bearer authentication. OWNER могут читать Jobs внутри своих Workspaces. Ответ `200 application/json`:

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "status": "PENDING",
  "attempts": 0,
  "maxAttempts": 3,
  "nextAttemptAt": "2026-09-11T00:00:00Z",
  "completedAt": null
}
```

`nextAttemptAt` присутствует со значением только для PENDING/FAILED; это время, начиная с которого можно повторно забрать задание, а не обещание времени запуска. `completedAt` имеет значение для SUCCEEDED/DEAD. Все шесть полей включены; неактуальные времена равны null. Cache-Control: no-store.

| Состояние | Значение |
| --- | --- |
| PENDING | Ожидает обработки; worker может быть выключен |
| RUNNING | Попытка выполняется |
| FAILED | Попытка не удалась; допускается retry |
| SUCCEEDED | Текущий Job завершён |
| DEAD | Терминальное завершение без успеха |

Успех URL fetch Job означает, что bytes и metadata помещены в карантин и отдельный внутренний malware scan Job атомарно создан; evidence остаётся FETCHED, а не READY. Этот scan Job пока не доступен через публичный endpoint. FILE ingest Jobs выполняют [durable scan](durable-file-scanning.md), а URL chaining описан [отдельно](url-malware-scanning.md). TEXT extraction ещё не реализован. Endpoint не запускает worker и не меняет состояние Job.

Без authentication возвращается 401. Неизвестная роль — 403. Чужой, отсутствующий, orphan Job, malformed payload reference или неподдерживаемый Job type — одинаковый 404 с detail `Job not found`. Некорректный UUID в path — 400. Mutations Jobs через этот API отсутствуют. Архивация Brand/Workspace не закрывает чтение истории для сохраняющих membership пользователей.

## Архитектурное решение

Controller преобразует authenticated principal в разрешённый scope. GetJobUseCase проверяет роль и subset scope через method security, затем выполняет scoped repository query. Query выбирает конкретный Job по PK, разрешает только известный type и требует существующее evidence с соответствующей строкой `evidence:<UUID>` в разрешённом Workspace. Используется тот же формат связи, что у URL worker. Новые типы Jobs не получают доступ автоматически: им понадобится явная ownership-модель.

Непроверенный payload не приводится к UUID в SQL: строковое сравнение с ID evidence позволяет повреждённой ссылке дать 404 без ошибки преобразования. Отсутствие Job→evidence foreign key в текущей схеме не выдаёт orphan Jobs: отсутствие связанного evidence закрывает доступ. Миграции и dependencies не добавлялись.

JobResponse — отдельный DTO с allowlist полей. DurableJob entity, payloadReference, idempotencyKey, lease owner/expiry и lastError не сериализуются. Даже сохранённая ошибка с чувствительным содержимым не попадёт в ответ.

## Threat model и Security Review

1. Данные: принадлежность Job к Workspace, сведения о работе, внутренние payload/errors/storage references.
2. Caller: любой authenticated пользователь, включая пользователя другого Workspace.
3. Границы доверия: path UUID, authenticated scope, сохранённая ссылка Job на evidence.
4. Атаки: подстановка чужого UUID, расширение scope при прямом вызове bean, перечисление Jobs, раскрытие внутренних ошибок, интенсивный polling.
5. Защита: role/scope method security; SQL-фильтрация ownership; разрешён только известный Job type; единый 404; минимальный DTO; no-store.
6. Обход controller не отключает method security. Повреждённая или отсутствующая evidence-ссылка закрывает чтение. Небезопасный lastError не раскрывается благодаря DTO.
7. Defense in depth: HTTP auth → method security → scoped query → DTO. Новых SQL/command injection путей, secrets и внешних запросов нет.

Endpoint читает один Job и не делает network fetch, parsing или mutations. Серверный rate limiting пока не реализован и требуется перед production. Клиенту следует использовать polling с паузой и backoff, останавливая его при terminal status; интервал не является серверной защитой. До значительного объёма evidence необходимо проверить PostgreSQL query plan: текущая связь через строковый payload не заменяет нормализованный FK/index для масштабирования.

## Runtime Flow

POST evidence → authorization → сохранение evidence/Job → 202 + Location → GET Job → authentication → role/scope → Job/evidence/Workspace query → JobResponse.

## Изменённые файлы этапа

| Файл | Назначение |
| --- | --- |
| src/main/java/com/cadence/application/repositories/DurableJobRepository.java | Scoped query для evidence Jobs |
| src/main/java/com/cadence/application/usecases/GetJobUseCase.java | Авторизованное чтение, default deny |
| src/main/java/com/cadence/presentation/JobController.java | GET endpoint и no-store |
| src/main/java/com/cadence/presentation/dto/JobResponse.java | Разрешённые публичные поля |
| src/main/java/com/cadence/presentation/BrandEvidenceController.java | Location для обоих способов регистрации |
| src/test/java/com/cadence/security/JobApiIntegrationTest.java | HTTP/direct-bean, lifecycle, privacy, ownership, Location |
| src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java | PostgreSQL fixture scoped lookup |
| README.md | Описание готового API |
| DEVELOPMENT_PLAN.md | Статус реализации |
| DEVELOPMENT_PLAN_RU.md | Статус реализации |
| docs/verification.md | Новый защищённый use case |
| docs/use-case-authorization.md | Ссылка на политику Jobs |
| docs/job-status-api.md | Этот контракт и review |

## Security Tests и Edge Cases

JobApiIntegrationTest проверяет три разрешённые роли, отсутствие authentication, неизвестную роль, чужой/пустой scope, неизвестный Job, неизвестный type, неверную и orphan evidence-ссылку, архивирование, malformed UUID, отсутствие mutation API, прямые вызовы с forged/null scope. Проверяются все пять статусов и ровно шесть разрешённых response fields, включая отсутствие synthetic secret из lastError. Location проверен для JSON, multipart и идемпотентного повтора.

## Что мне стоит изучить

- HTTP 202 + Location: принятие задания и последующее чтение его результата — разные операции.
- Job status и evidence status: завершение загрузки не означает завершение всего pipeline.
- DTO allowlist: безопаснее выбирать публичные поля явно, чем пытаться скрывать отдельные поля Entity.
- Tenant scoping: UUID указывает ресурс, а право чтения определяется принадлежностью Workspace.

## Тесты

- `.\mvnw.cmd spotless:apply '-Dtest=JobApiIntegrationTest,SecurityIntegrationTest' test`: BUILD SUCCESS, 25 сценариев без failures/errors.
- `.\mvnw.cmd spotless:apply clean verify`: BUILD SUCCESS, 201 тест, 197 прошли, 4 PostgreSQL-теста пропущены. Все 14 новых Job API сценариев прошли; compilation, Spotless, JaCoCo threshold и Spring startup в test profile прошли.
- `git diff --check`: без ошибок whitespace.

PostgreSQL fixture дополнен проверками своего/чужого Workspace и неподдерживаемого type, но при отсутствии Docker он не выполнялся. Это написанный тест, а не подтверждённый PostgreSQL результат.

## Непроверенные аспекты и Production considerations

PostgreSQL/Testcontainers fixture требует Docker; локальные H2-тесты не подтверждают PostgreSQL execution plan/constraints. Live Clerk, remote CI, rate limits и production adapters остаются непроверенными или незавершёнными. Extraction/AI и общая модель ownership будущих Job types не реализованы этим API. Commit, push и deployment не выполнялись.
