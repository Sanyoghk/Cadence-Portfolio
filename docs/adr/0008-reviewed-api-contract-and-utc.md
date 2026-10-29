# ADR-0008 — Reviewed API contract, safe errors и UTC

Status: accepted, issue #111, 2026-10-01.

## Контекст

API уже использует `/api/v1`, DTO, stored OWNER membership и scoped repositories.
Однако filters/MVC/Spring Security имели разные errors; arbitrary exception messages
могли раскрыть diagnostics. Audit timestamps были `LocalDateTime.now()` без timezone
в БД. Сортировка по одинаковым именам не задавала порядок между страницами.

## Решение

1. `ApiProblems` создаёт один ProblemDetail contract: safe fixed descriptions,
   status/code/retryable, UUID instance, validation field/code без rejected values.
   MVC advice и servlet security/upload handlers используют один factory.
   Bearer challenge сохраняет protocol error code, удаляя decoder descriptions/URIs.
2. Checked-in OpenAPI 3.1.1 JSON публикуется OWNER-only `/api/v1/openapi`.
   Exact route inventory, DTO/enum fields и реальные workflow responses проверяются
   существующим Spring/Jackson/JUnit stack, без новых dependencies.
3. `ApiPagination` ограничивает page/size/field и добавляет unique id tie-breaker.
4. `BaseEntity` и audit DTO используют `Instant`; V22 конвертирует 22 columns
   в TIMESTAMPTZ(6) с explicit UTC interpretation старых timestamps.
5. FILE private reference остаётся во внутреннем Entity/storage contract, внешнее
   поле `sourceReference` возвращает null. Это deliberate privacy correction.

## Alternatives и trade-offs

- Runtime OpenAPI генератор уменьшил бы ручную работу, но добавил dependency и
  зависимость публичного contract от annotations/Entity introspection. Reviewed
  DTO contract выбран для explicit API evolution; tests обнаруживают drift.
- Default Spring errors не покрывают servlet filters одинаково и могут передать
  exception details. Общий factory требует больше явных mappings, зато один
  контролируемый representation проходит через разные execution paths.
- UTC formatting одного `LocalDateTime` не исправляет неопределённый момент времени.
  `Instant` + TIMESTAMPTZ устраняет неоднозначность будущих записей. Legacy timezone
  невозможно вывести из самих timestamps. User подтвердил demo-only data, поэтому
  UTC interpretation явно документирована, без удаления и эвристического сдвига.
- Cursor pagination масштабируется лучше и устойчивее к concurrent inserts;
  текущий bounded PageResponse сохраняет существующий API. Unique tie-breaker
  обеспечивает порядок при равных primary fields, но не snapshot isolation.

## Consequences / проверка

Все будущие controllers расширяют OpenAPI и contract tests в том же change.
Parser/edge errors до Spring требуют отдельной deployment qualification.
Security configurations остаются deny-by-default, documentation endpoint не public.
Unexpected error logs теряют произвольный stack/message ради data minimization;
opaque instance позволяет связать безопасное событие с клиентской ошибкой.
Structured tracing/metrics остаются отдельной #116.

Тесты: malformed UUID/JSON, validation schema, 401/403/BOLA, framework status/Allow,
internal failure/token diagnostics, private key exclusion, all published routes,
mass assignment, deep pagination, adjacent pages with equal names, populated V21
UTC upgrade и existing PostgreSQL constraints. Полные результаты конкретной revision:
[completion report](../issue-111-completion.md).

Migration требует stopped writes/old runtime, backup и capacity/lock assessment
на production data. Unknown historical timezone и rolling old/new mappings не
квалифицированы; см. [recovery](../database-migrations.md).
