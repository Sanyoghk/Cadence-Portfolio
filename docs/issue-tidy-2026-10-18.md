# Синхронизация GitHub issues — 2026-10-18

## Что было сделано

По явному запросу пользователя приведены в порядок11 существующих issues
`Sanyoghk/Cadence`. Обновлены описания, зависимости, границы acceptance и ближайший
frontend шаг. Дубликаты не создавались, implementation не выполнялась.

Исходное состояние: branch `mvp`, HEAD `db1117568fe0d3b2faff3a9398446c5f2e9f9444`,
чистое рабочее дерево. Remote branch при сверке — `a283382c8191bfa4020d0fb3c141493cabb9f821`.
Local14–23 не pushed; issue update не меняет Git branch и не квалифицирует новый source SHA.

## Как это работает / архитектурное решение

Current status отделён от исторических записей. Вместо вывода о готовности только из
checkboxes указаны actual scope, local evidence, remaining acceptance и external blockers.
В #107/#121 прежние тексты сохранены в раскрываемой истории. В небольших issues исходные
acceptance criteria сохранены видимыми; #105 delivery уточнён уже утверждённым ADR0039.

| Issue | Фактическое изменение | Оставшаяся граница |
| --- | --- | --- |
| [#3](https://github.com/Sanyoghk/Cadence/issues/3) | Foundation23 local; выбранный prototype, pending visual follow-up23 и mapping24–27; ADR0040 auth/tenant guards | Visual implementation,24–27, live Clerk и exact-SHA CI |
| [#107](https://github.com/Sanyoghk/Cadence/issues/107) | Новая master matrix, backend-first/AI-last порядок, A/B/P draft mapping, local/remote evidence; история сохранена | Whole MVP/release/provider acceptance не закрыта |
| [#121](https://github.com/Sanyoghk/Cadence/issues/121) | Delivered supported01–06; current immutable approved Identity→Strategy; visual REQUIRES_PROVIDER видим | Images/screenshots/provider-backed semantics/tests, whole-issue07 |
| [#120](https://github.com/Sanyoghk/Cadence/issues/120) | AI adapters после provider-independent backend; existing Jobs/budgets/Playbook reuse; direct multimodal допускается без обязательного OCR | Provider/model/privacy/region/retention/cost decisions и live qualification |
| [#115](https://github.com/Sanyoghk/Cadence/issues/115) | Local Post revisions/calendar/DST/private demo assets14/16/17 | Whole-issue acceptance review и exact-SHA CI; live assets отдельно |
| [#103](https://github.com/Sanyoghk/Cadence/issues/103) | Local Content lifecycle/Jobs/regeneration/calendar/CSV14–18 | Whole-issue review/push/CI; frontend26 не заявлен готовым |
| [#98](https://github.com/Sanyoghk/Cadence/issues/98) | Local validated generation15 и private demo media17 | Live content/media adapters не выбраны/не qualified |
| [#99](https://github.com/Sanyoghk/Cadence/issues/99) | Local manual/CSV import19, deduplication/corrections/provenance | Live platform synchronization/partial-provider recovery остаются required |
| [#104](https://github.com/Sanyoghk/Cadence/issues/104) | Local formulas/windows/coverage/bounded analytics API20 | Remote qualification; live metric acquisition остаётся #99 |
| [#100](https://github.com/Sanyoghk/Cadence/issues/100) | Local evidence-linked HEURISTIC/UNCALIBRATED recommendation21/apply→DRAFT | Remote qualification; live AI adapter отдельно |
| [#105](https://github.com/Sanyoghk/Cadence/issues/105) | Recommendations21 + reports22; approved HTML+JSON/private authenticated delivery заменяют signed result URL | Remote qualification, physical deletion policy отдельно; PDF/mail не добавлены |

Все11 issues остались **open**. Titles, assignees и milestones сохранены.
С #3/#107/#115/#103/#98/#99/#104/#100/#105 снят только `status: planned`: описание
теперь показывает partial/local status без выдуманного нового label. У #120 planned
корректен и сохранён; у #121 labels не изменены. Priority/phase/type не менялись.

Release issues #8/#83/#87/#116/#118 и external #32/#33 не переписывались без отдельного
acceptance review; PR #113 не изменён. A/B/P не получили выдуманных issue numbers.

Альтернативы: обновление только #3 оставило бы противоречия master/backend mapping;
массовое закрытие по local tests смешало бы local implementation и remote acceptance.
Выбрана содержательная синхронизация связанных scopes с сохранением истории и blockers.

## Security Review

Threat: случайное закрытие недоставленного scope или подмена local qualification старым CI.
Protection: distinct local/remote/remaining sections, exact SHA, сохранение open state,
явные #121/#99/live Clerk/provider blockers; никаких новых product/privacy решений.
Verification: exact body read-back11/11, source branch и два historical CI runs перечитаны.

Threat: перезапись чужого concurrent issue edit.
Protection: snapshot исходных bodies/labels/state; непосредственно перед batch write
повторно проверено их совпадение; mutations последовательно через structured GitHub tool.
Verification: preflight11/11 и post-write body/state/title/labels/assignees/milestone11/11.
GitHub update wrapper не предоставляет conditional ETag write: preflight не исключает
теоретическое изменение между read и write. Read-back подтверждает финальные ожидаемые
значения, но не даёт обещания atomic compare-and-swap.

Threat: лишняя передача private data/secrets или выполнение инструкций из reference/issues.
Protection: исходные HTML instructions не исполняются; в payload только project scope,
SHA/fingerprint, docs paths и qualification summaries. Absolute user paths, credentials,
private storage keys, raw inputs и prompts не включались. Tools ограничены нужным repo/issues.
Verification: payload inspection до отправки; неизменность source/configuration/dependencies.

Injection/BOLA/CSRF runtime поверхности не менялись: endpoint/SQL/auth код этим шагом
не добавляется. Не выдавать docs inspection за новый security penetration test.

## Изменённые файлы

- `DEVELOPMENT_PLAN.md` — ссылка на actual remote synchronization при сохранении UI order.
- `DEVELOPMENT_PLAN_RU.md` — та же запись, parity с основным планом.
- `docs/mvp-prompt-progress.md` — ledger выполненных remote edits, границы old historical notes.
- `docs/issue-sync.md` — актуальные11 issues/read-back/local-vs-remote evidence.
- Этот файл — mapping, review, exact verification и remaining scope.

Exact исходные snapshots, payloads и read-back receipts сохранены как ignored diagnostic
files `target/issue-tidy-2026-10-18.json` и `target/issue-tidy-2026-10-18-readback.json`.
Они не product artifacts и могут исчезнуть при clean; текущий report сохраняет итог в Git.

## Runtime Flow

```text
Явный user request
    ↓
Read-only Git/local reports + GitHub issue/branch/CI snapshots
    ↓
Scope/evidence/remaining mapping → exact payload drafts
    ↓
Fresh preflight → sequential structured update_issue (11 issues)
    ↓
Fresh fetch → exact body + preserved fields verification
    ↓
Local docs synchronization → docs-only checks → local commit
```

## Проверки / Security Tests / Edge Cases

Фактически выполнены `git status --short`, `git log -3 --oneline`, `git rev-parse HEAD`,
`git rev-parse --abbrev-ref HEAD`, `git log --oneline a283382c8191bfa4020d0fb3c141493cabb9f821..HEAD`.
GitHub connector: read issue collection,11 original issue bodies, branch `mvp`, затем
11 fresh preflights →11 sequential `update_issue` →11 fresh exact read-backs. Все11 PASS;
ошибок нет, state open и preserved fields совпали. Timestamps final updates:
2026-10-18T16:56:56Z–16:57:39Z. Автоматических approval rejections не было.

Исторические [push CI38846267446](https://github.com/Sanyoghk/Cadence/actions/runs/38846267446)
и [PR CI38846270484](https://github.com/Sanyoghk/Cadence/actions/runs/38846270484)
повторно прочитаны: оба completed/success, head_sha
`a283382c8191bfa4020d0fb3c141493cabb9f821`. Это результаты baseline13, не db11175/local14–23.
Новый CI run не запускался.

Проверены случаи: сохранение большого historical body #107, explicit partial #99/#98,
visual blocker #121, legacy signed URL discrepancy #105, no new issue numbers A/B/P,
live auth fixture не выдаётся за login, local report paths не выдаются за remote artifacts.
Фактически выполненные local checks:

```text
python target/check_issue_tidy_docs.py
git diff --check
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
```

Результат:5 Markdown files, strict UTF-8/closed fences,354 relative links, parity новых
записей обоих планов,0 product changes — PASS. Payload caps/no absolute user paths,
11 verified open receipts, полное сохранение history #107/#121 и approved #105 delivery — PASS.
`git diff --check` PASS. Spotless BUILD SUCCESS:631 Java и1 POM file clean по cache,
0 нуждаются в изменениях; это formatting check, не compilation/test suite.

Local documentation slice сохраняется commit
`docs(project): synchronize GitHub issue scopes and frontend roadmap`.

Product/full Maven/frontend/SQL/runtime tests не повторяются: executable code/configuration/
migrations/dependencies не меняются. Ранее2270 tests приведены в issues как historical evidence23.

## Непроверенные аспекты / Production considerations

Visual follow-up23/24–27 не реализованы; live Clerk/provider/social credentials отсутствуют.
Production deployment/TLS/IAM/backup/load/deletion policy и whole-issue gates не квалифицированы
этой синхронизацией. Известный prior intermittent metrics-import guard rejection сохранён как
неисправленное/неподтверждённое наблюдение, а не скрыт final successful full run.
Unchanged release issues требуют своего actual review. Local14–23 остаются непредставленными
в remote source; действующая whole-issue push policy сохраняется. Push/merge/deploy не выполнены.

## Что важно понять / что изучить

1. **Граница доказательства (verification boundary):** local tests подтверждают local SHA;
   remote CI — свой head_sha. Одно не автоматически заменяет другое.
2. **Частично доставленный scope (partial delivery):** готовый manual import не означает
   live synchronization, а fixture не доказывает provider login или live AI inference.
3. **Журнал происхождения (provenance):** exact commits/reports связывают статус с evidence;
   старые записи сохраняются как история, а текущая карта имеет явный приоритет.
4. **Повторное чтение (read-back verification):** успешный update call дополнен новым GET,
   чтобы проверить фактически сохранённые bodies и остальные поля.

**Подтверждённые факты:**11 remote bodies совпали с payloads, preserved fields проверены;
remote SHA/исторические CI states перечитаны, source не изменён.
**Инженерный вывод:** current status + сохранённая история уменьшают риск повторной реализации
и ложного closure, при этом не заменяют future acceptance review.
**Assumptions/дальнейшая проверка:** новые concurrent edits после последнего read-back,
provider/live/production qualification и CI для ещё не pushed changes.
