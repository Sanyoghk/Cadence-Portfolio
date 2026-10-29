# ADR-0009: Brand analysis port и публикация с проверкой версий

Status: accepted — 2026-10-01, issue #114

## Контекст

Старый BrandIngestService всегда возвращал sample signature, включая production.
Entity передавалась в service, а анализ и запись выполнялись в одной транзакции.
Медленный provider удерживал бы DB connection; поздний ответ мог затереть изменения.
Пользователь выбрал port и production fail-closed, отложив реальный model adapter.

## Решение

BrandAnalysisPort принимает immutable нормализованный command и возвращает
BrandAnalysisResult, который проверяет размеры, palette, тексты и provenance.
Raw provider JSON, credentials, URLs и Entity не являются частью contract.
BrandIngestService контролирует ошибки port, без передачи наружу exception/cause.
Fake package-private и создаётся configuration только при local/test без prod.
Default вне local/test — disabled; unknown adapter и prod+fake запрещают startup.

Orchestration работает с NOT_SUPPORTED. Отдельный Spring bean
BrandAnalysisTransactions использует REQUIRES_NEW: start сохраняет RUNNING и
версии источника; complete проверяет состояние/версии/deadline и атомарно
сохраняет signature с SUCCEEDED receipt; fail сохраняет FAILED, оставляя signature.
Во всех фазах применяется OWNER/scope guard. Блокировки идут Workspace → Brand.
Отдельный bean нужен для Spring transaction proxy: прямой вызов собственного
метода не прошёл бы через proxy и не открыл бы новую транзакцию.

V23 дублирует ограничения в PostgreSQL: FK, статусы, provenance, один RUNNING,
60-second deadline, запрет изменения источника и terminal receipts; actual DB clock
и версии проверяются при успешном переходе. Истёкший RUNNING заменяется следующей
попыткой; старый callback привязан к своему UUID и не завершает новую попытку.
Value-identical publication увеличивает Brand version через PESSIMISTIC_FORCE_INCREMENT,
поскольку Hibernate может пропустить обычный UPDATE. Output checksum использует
SHA-256 и length-prefixed fields/list counts для однозначного представления.

## Alternatives / trade-offs

- Одна транзакция вокруг provider: проще, но удерживает connection/locks во время I/O.
- Только optimistic locking: не ограничивает одновременные попытки и не сохраняет
  отдельный статус отказавшего анализа.
- Durable Job: нужен для долгого анализа с retry/cost budgets, но сейчас provider
  отсутствует; совместимый HTTP contract остаётся синхронным.

Выбраны короткие транзакции и durable attempt ledger. Это orchestration contract,
не Brand Identity approval/citations workflow. Цена: дополнительные записи и три
транзакционные фазы. Deadline запрещает публикацию, но не прерывает произвольный
код adapter. Реальный adapter обязан ограничивать I/O, concurrency/cost budgets,
иметь privacy review и безопасную обработку model output.

## Verification

PostgreSqlBrandAnalysisTest: реальные транзакции без I/O transaction, atomic receipt,
failure/null, brand/workspace changes, concurrent start, identical publication,
deadline/recovery, scope denial, SQL forgery/delete/duplicate RUNNING rejection.
BrandAnalysisFailClosedTest: HTTP 503, OpenAPI schema и сохранность signature.
Configuration tests запрещают fake в prod и mixed profiles.
[Completion report](../issue-114-completion.md) содержит команды и результаты.
