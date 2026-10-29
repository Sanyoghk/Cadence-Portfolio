# Актуализация плана — 2026-10-12

## Что было сделано

На чистой ветке `mvp`, HEAD `d2da970219d61ea62c1a801a2832ccfd2022049a`,
актуализированы оба DEVELOPMENT_PLAN по source `3e86c02`, текущим ADR/contracts,
review и прочитанным GitHub #107/#121/#120. Это docs-only изменение.

Убраны противоречащие текущему состоянию исторические «следующие задачи»; история
остаётся в отдельных completion/slice reports, на которые даны ссылки.
Реализованные Identity/Strategy lifecycle отмечены отдельно от оставшихся AI gaps.

## Как это работает / Runtime Flow

Runtime не меняется. Порядок работы: оставшийся #121 → отдельные AI Jobs,
budgets/idempotency и Playbook → Content/CSV → analytics/recommendations → full journey.
Frontend развивается по стабильным contracts; real providers и production qualification
остаются отдельными gates. Будущие routes не объявляются реализованными.

## Архитектурное решение

Сохранены stack, product scope и существующие issues. Task 3.1/3.2 приведены к нумерации #107.
Для Task 2.4, 2.5 и 3.3 добавлены scope/dependencies/acceptance criteria без выдуманных issue IDs.
Вместо добавления ещё одного противоречивого continuation в конец обновлены активные разделы.
Цена двух планов — необходимость сохранять их смысловую согласованность; новые общие разделы
совпадают. Старые англоязычные разделы сохранены, новые пояснения написаны по-русски.

## Security Review

Threat: трактовка reviewed/CLEAN source как автоматически разрешённого model input.
Protection: отдельные eligibility, source binding, citation validation и publication fencing gates.
Verification: сверка плана с ADR0012/0013 и текущим Strategy source code.

Threat: незаметное изменение политики withdrawal при планировании re-review.
Protection: текущая необратимость явно сохранена; будущая политика требует отдельного ADR/решения.
Verification: одинаковые ограничения в обоих планах; runtime/API/migrations не изменены.

Threat: выдача local fake, ingest Jobs или старого CI за готовый production AI.
Protection: отдельные Jobs/budget/provider gates, маркировка текущих synchronous/non-idempotent actions.
Verification: inspection adapter configuration и docs-only diff.

## Изменённые файлы

- `DEVELOPMENT_PLAN.md` — актуальные статусы, #121 slices, follow-ups, API availability и порядок.
- `DEVELOPMENT_PLAN_RU.md` — те же статусы, зависимости и acceptance criteria.
- `docs/product-scope.md` — Docker обязателен для full verification, lightweight local остаётся возможным.
- `docs/plan-refresh-2026-10-12.md` — этот отчёт и границы проверки.

## Тесты / Security Tests / Edge Cases

Docs-only verification: `git diff --check`; Python content/link/parity checks через
`python -B -` (PowerShell here-string). Проверены существование относительных ссылок,
совпадение новых разделов двух планов, уникальность Task IDs, отсутствие устаревшей
очереди #114/#97 и отсутствие product changes. Результат: 62 относительные ссылки существуют,
4 общих раздела планов совпадают, Task IDs уникальны, проверки прошли.

Maven/security/integration tests заново не запускались: application/configuration/migrations
не менялись. Предыдущий полный run описан в project review и не считается новым запуском.
Edge cases в плане: stale/replayed review, withdrawal, foreign citations, revoked access,
lease loss, duplicate publication, ambiguous timeout и несколько approved Identity revisions.
Это acceptance criteria будущих implementation tasks, не новые выполненные security tests.

## Непроверенные аспекты

Новые follow-up GitHub issues ещё не созданы; удалённые issues в этой задаче только прочитаны.
Push/remote CI/merge/deployment не выполнялись. Реализация новых planned contracts не проверялась,
потому что её ещё нет. Live provider/data policy и re-review policy не выбраны этим изменением.

## Что мне важно понять / Что мне стоит изучить

- Критерии приёмки (acceptance criteria) отличают конкретный завершённый scope от общего намерения.
- Зависимость задач (dependency) задаёт необходимый готовый contract: Content требует approved
  Strategy, а production AI — source/currentness и budgets, а не только работающий adapter.
- Идемпотентность (idempotency) — определённое поведение повтора запроса; текущий generated POST
  может создавать новую revision, поэтому её нельзя объявлять существующим invariant.

## Production considerations

Сохраняются полный PostgreSQL/provider verification, live auth/provider/IAM/TLS/sandbox,
backup/restore/load/E2E gates. Обновление плана не закрывает ни один из них.

Факты: документация сверена с текущим source и issues, изменены только перечисленные Markdown.
Инженерный вывод: существующий roadmap пригоден, требовалось уточнение статусов и зависимостей.
Открытые решения: re-review после withdrawal, выбор current approved Identity, provider/data policy.
