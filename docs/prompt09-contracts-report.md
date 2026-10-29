# Задание09 — contracts и follow-up scopes

Дата: 2026-10-15. Baseline `f2f60c9`, mvp, working tree был чистым.
Результат: qualified docs-only design. Implementation10–13 не начат.

## Что было сделано

Сверены actual source contracts, queue/API, security configuration, schema/domain limits,
текущие планы и GitHub issues. Подготовлены [ADR0028](adr/0028-durable-ai-workflow-contracts.md),
[ADR0029](adr/0029-full-playbook-contract.md), [три exact issue drafts](ai-workflow-issue-drafts.md).
#110/#97/#102 closed foundations не переоткрываются; #120/#121/#107 остаются open.
Отдельные подходящие новые issues по выполненному поиску не найдены. Draft A покрывает10–11,
B —12, P —13. Номера не выдуманы; external create/update/comment не выполнены.

## Как это работает / Runtime Flow

```text
OWNER request + bounded DTO/key
  → current DB membership/source capture
  → atomic immutable operation + Job + scoped replay mapping
  → typed worker claim + budget reservation + dispatch witness
  → physical/current checks → model outside transactions
  → validate output + fresh permission/source/lease/deadline checks
  → atomic result/provenance + operation/Job completion + accounting
  → explicit Identity copy / separate human approval
```

Это будущий flow, не уже работающий async runtime. Existing synchronous contracts сохраняются.
Strategy v2 содержит полный согласованный набор Playbook fields; legacy partial history остаётся
partial. Повторный request не заменяет captured source и не создаёт новую operation.

## Архитектурное решение

Используем существующую relational queue, но добавляем отдельную generation operation,
immutable attempts и budget ledger. Shared Job payload/status сам по себе не доказывает
ownership/consent/one-result. Отдельный broker сейчас добавил бы operational complexity.

Provider call нельзя атомарно commit вместе с PostgreSQL. Поэтому гарантируем один local
published result, но не обещаем exactly-once remote execution. Dispatch intent позволяет
после crash честно сказать OUTCOME_UNKNOWN, сохранить hold и не удвоить неизвестный расход.
Existing REQUIRES_NEW completion нужно адаптировать к общему atomic coordinator; нельзя
сначала commit content, потом отдельно отметить success Job и назвать это atomic.

Legacy sync routes должны получить общий budget enforcement в12. Worker повторяет DB actor
checks и не притворяется HTTP caller с сохранённым JWT. Playbook v2 сохраняет один revision/
approval aggregate; output schema/citations защищают структуру, human review — смысловую оценку.

## Security Review

Threat: BOLA, mass assignment, stale actor/access.
Protection: server-captured immutable scope, DTO allowlist, actual DB membership до dispatch/publish.
Verification: actual current guards inspected; negative API/worker/SQL tests заданы A/B/P, не запущены.

Threat: replay/double spend/crash/lost lease.
Protection: scoped key digest, unique result, attempts/lease fences, held unknown cost, atomic settlement.
Verification: state table рассмотрена по crash boundaries; concurrent/failure-injection cases required10–12.

Threat: source prompt injection/private egress и malformed model output.
Protection: bounded server-selected inputs, no tools/fetch, typed output, current source/physical checks.
Verification: existing input/result contracts inspected; future regressions и malicious-input tests явно заданы.

Threat: history corruption/legacy fabrication.
Protection: versioned schemas/digests, no backfill, SQL/runtime immutable constraints и explicit upgrades.
Verification: migration/legacy/raw-SQL acceptance прописана; migrations этим заданием не создавались.

Threat: exhaustion/hidden product spend assumptions.
Protection: bounded input/output/attempt/deadline/history, policy-based quotas, no live dispatch без policy.
Verification: existing5 sources/20000bytes,60s,30s,2 slots и Strategy bounds сверены в source;
new caps являются design choices, не production load qualification.

## Изменённые файлы

- `docs/adr/0028-durable-ai-workflow-contracts.md`: API/capture/worker/lease/replay/accounting, threat model.
- `docs/adr/0029-full-playbook-contract.md`: fields/bounds, versioning, legacy/API/Content semantics.
- `docs/ai-workflow-issue-drafts.md`: discovery evidence, Title/Body A/B/P, mapping10–13.
- `DEVELOPMENT_PLAN.md`: design status, next10 и актуальная approved Identity source вместо legacy claim.
- `DEVELOPMENT_PLAN_RU.md`: те же current statuses/dependencies и corrections.
- `docs/mvp-prompt-progress.md`: qualified09 design, separate remaining runtime/activation scope.
- `docs/astra-medium-mvp-prompts.md`: ссылки на reviewed contracts для продолжения10.
- `docs/adr/README.md`: index0026–0029.
- `docs/prompt09-contracts-report.md`: этот отчёт и границы доказательства.

## Тесты и docs verification

Фактические команды verification этого slice:

```powershell
python -B var/prompt09-doc-check.py
git diff --check
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
```

Результаты: docs-check PASS (9 Markdown files,175 local links, fences, numbering00–30,
plan parity и contract markers); git diff --check PASS; Spotless BUILD SUCCESS
2026-10-15T22:11:18+01:00. Предупреждения Git только LF→CRLF normalization.
Full Maven/Docker/Python runtime/Linux/startup
не запускались: нет product code/configuration/migration changes. Historical1309 run не новый pass.
GitHub read-only discovery — queries/counts/overlap в отдельном draft документе; remote
записи не prerequisites09. Link check проверяет local targets, не доступность external URLs.

## Security Tests и Edge Cases

Required future tests перечислены в ADR и issue acceptance, а не объявлены исполненными:
foreign actor/tenant, stale membership, changed request under same key, SQL bypass,
lease boundary/same workerId/new attempt, crash before/after dispatch and commit,
unknown spend across period rollover, late provider evidence, removed access at completion,
source withdrawal/scan/expiry/corrupt bytes, v1 partial history/v2 downgrade, mixed weights,
four-week counts, channel rounding и Unicode/oversize output.

## Непроверенные аспекты / Production considerations

Нет runtime proof для будущих contracts; он обязателен10–13. Quotas, тарифы, processor,
retention/deletion, live reconciliation и provider limits не согласованы этим design.
Production disabled; local/test synthetic fixtures не реальные стоимость/latency/moderation.
Visual #121 остаётся REQUIRES_PROVIDER; закрытие07 и push ветки не разрешены этим slice.
Security scans/live providers/OAuth/production rollout не выполнялись и не заявляются.

## Что мне важно понять

Очередь даёт возможность продолжить работу после перезапуска, но сама не сохраняет права
пользователя и допустимость source. Это отдельные проверки. Idempotency защищает от повтора
одного запроса, lease fencing — от запоздавшего worker, budget reservation — от перерасхода.
Нужны все три механизма, потому что они предотвращают разные сбои.

## Что мне стоит изучить

1. Immutable snapshot — неизменяемый снимок: worker не подменяет evidence более новой версией.
2. Lease fencing — запрет записи старому worker через attempt/state/time, даже при том же workerId.
3. Idempotency — одинаковый request/key имеет один результат, но изменённый payload конфликтует.
4. Reservation/settlement — резерв верхней оценки до I/O и подтверждённое списание после него.
5. Versioned contract — v1 history не переписывается ради новых v2 fields.

## Факты, выводы, assumptions

Факты inspection: current generation sync, Job API evidence-only, bounded ports и approved
Identity source уже существуют; отдельные scopes drafts не найдены как standalone issues.
Инженерные решения: additive async API, relational queue + operation, conservative unknown
recovery, versioned Playbook schema и limits. Это reviewed design, не результат runtime tests.
Pending: конкретные commercial quotas/retention/provider и будущая qualification. Ничто из
pending не выдаётся за пользовательское утверждение или completed production functionality.
