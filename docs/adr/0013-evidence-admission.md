# ADR-0013: Явный review текстового evidence перед AI

Дата: 2026-10-11. Статус: принято для TEXT admission slice #121.

Актуализация: [ADR0014](0014-versioned-text-re-review.md) вводит V33 versioned re-review
для неотозванной sourceVersion. Историческое ограничение UNIQUE(evidence_id,source_version)
ниже относится к V31–V32; новый unique tuple включает Brand/Workspace versions.
Withdrawal sourceVersion остаётся необратимым во всех contexts, regrant не утверждён.

## Дополнение 2026-10-12: отзыв и актуальный snapshot

V32 добавляет отдельную append-only EvidenceAdmissionWithdrawal, UNIQUE(admission_id),
server actor/time и invoker trigger. V31 и исходный review не меняются. Withdrawal не требует
ACTIVE parents или совпадения старых versions: actual active OWNER с текущей membership
может запретить использование даже архивированного/устаревшего источника. Повтор —409.
Отзыв необратим в текущем контракте; повторный admission того же sourceVersion запрещён.
Повторное разрешение потребует нового отдельно спроектированного review lifecycle.

GET /{admissionId}/snapshot возвращает bounded text вместе с immutable admission metadata,
только если sourceVersion, Brand/Workspace versions, registration/content checksums, policy,
exact text, TEXT/PENDING/ACTIVE lifecycle актуальны и withdrawal отсутствует. Explicit lookup
связывает admissionId с evidenceId; чужой/несвязанный receipt —404. Original reviewer остаётся
историческим actor, его уход не переписывает review: право чтения проверяется у текущего caller.

Оба commands сохраняют Account→Workspace→Brand→Evidence locks, actual OWNER/membership и
REQUIRES_NEW/READ_COMMITTED. Withdrawal trigger берёт тот же lock order. Snapshot после
committed withdrawal блокируется; снимок, полученный раньше, может уже находиться у клиента.
Отозвать ранее выданные bytes невозможно. Future AI consumer обязан заново проверять source,
withdrawal, permissions и lifecycle перед publication под этими locks, после external I/O.
Этот этап не реализует такой consumer, batch source selection, citations или transmission policy.

Alternatives: mutable withdrawn flag отвергнут, поскольку менял бы immutable review history;
удаление receipt уничтожало бы provenance; запрет withdrawal для archived source оставлял бы
неудобный и небезопасный путь отмены. Дополнительная таблица увеличивает schema, сохраняя
независимый audit и защиту PostgreSQL при обходе application слоя. Runtime имеет INSERT/SELECT
withdrawals без UPDATE/DELETE. Shared DB credentials не подтверждают подлинность JWT actor.

Review обнаружил дополнительную границу: одинаковые parent versions сами по себе не доказывают
tenant identity при SQL reparenting. Domain уже запрещает Brand.setWorkspace для другого parent,
HTTP transfer отсутствует. V32 закрепляет тот же invariant отдельными UPDATE triggers на
brand_evidence.brand_id и brands.workspace_id. Они запрещают изменение parent UUID независимо
от наличия admission, исключая race между admission insert и условной проверкой наличия receipt.
Нет backfill/новых provenance assertions; старые rows остаются без изменений.

## Проблема и граница этапа

Регистрация evidence, FETCHED, CLEAN scan и extraction не означают разрешение использовать
источник для AI. Analysis #114 пока использует только brandName/industry; привязывать к его
старым receipts citations из случайного evidence означало бы выдумать происхождение результата.

Первым реализован полный отдельный review/admission workflow для TEXT. URL/PDF/images и
social snapshots остаются недопустимыми для этого workflow. Real provider/model/data policy
не выбраны; admission не является согласием на передачу неизвестному внешнему provider.
Этот этап не вызывает AI, не создаёт Identity/citations и не переводит source в READY.

## Решение

GET `/api/v1/evidence/{evidenceId}/ai-admission` возвращает точный сохранённый TEXT, версии
Evidence/Brand/Workspace, SHA-256 contentChecksum и policyVersion=text-review-v1.
POST принимает только эти version tokens и contentChecksum. Source определяется сервером,
reviewer — principal, review time — Clock. Client text/reviewer/time не являются writable fields.

EvidenceAdmission — отдельная append-only entity с точной копией текста, исходным registration
checksum, отдельным checksum текста, source/parent versions, reviewer/time и policy version.
Один snapshot допускается на evidence/sourceVersion; повторный POST409. Replay не создаёт
новый audit. Request не содержит model output или citations; metadata response не дублирует text.

Оба operations используют REQUIRES_NEW/READ_COMMITTED и Account→Workspace→Brand→Evidence
locks. Scoped projection выполняется до locks; повторный child lookup включает Brand ID.
Actual DB account activity/OWNER/membership проверяются заново, даже если principal устарел.
DTO materialization внутри transaction; external I/O отсутствует. Preview locks нужны для
согласованного token bundle и сериализации с revocation/archive writers; это короткая transaction,
которая не удерживается во время человеческого review. POST получает новый committed snapshot.

TEXT PENDING здесь означает незавершённый ingest lifecycle, не блокирует отдельный human review.
FAILED/READY/non-TEXT исключены. Ограничение20000 UTF-16 units; unpaired surrogates и controls
кроме newline/CR/tab запрещены. Текст должен совпадать с checksum регистрации SHA256(`TEXT\n`+text).
Нормализация в admission отсутствует: подтверждаем ровно сохранённые данные.

V31 не изменяет V1–V30 и не создаёт legacy admissions. FK, UNIQUE, bounds и invoker trigger
проверяют actual actor/membership, parents, versions, policy, content equality, UTF-8 checksums
и review time. UPDATE/DELETE history запрещены trigger; runtime grants отдельно отзывают их.
Shared DB role всё ещё не доказывает JWT reviewer при компрометации credentials.

## Alternatives и trade-offs

- Автоматическое admission после CLEAN scan отвергнуто: malware scan не определяет релевантность,
  авторство, разрешение передачи данных и prompt injection.
- Перевод READY в этом этапе отвергнут: смешивал бы ingest и AI review, изменял sourceVersion,
  ломая binding существующих scan/artifact receipts.
- Использование client text вместо server snapshot отвергнуто: reviewer мог бы подтвердить одни
  данные, а отправить другую версию; исчезает проверяемое происхождение future model input.
- Отдельный append-only snapshot сохраняет происхождение, но дублирует bounded текст. Retention,
  deletion и отзыв admission требуют отдельного согласованного lifecycle перед live consumption.

## Threat model и следующие gates

Защищаем tenant content и audit; caller — authenticated active OWNER. Trust boundaries:
request→server и database text→future model. IDOR/scope spoofing, actor spoofing, stale review,
archive/revocation races и SQL history rewriting проверяются отдельными слоями и tests.
Если application validation обойдена, V31 продолжает проверять вставляемый snapshot.

Review не делает текст доверенными instructions. Future adapter обязан передавать его как data,
валидировать output, запрещать arbitrary tools/URLs и проверять citation IDs только против
server-selected snapshots. До actual AI call нужны policy/permissions/lifecycle/current source
rechecks, bounded source selection, admission withdrawal semantics и publication fencing.
URL/PDF admission дополнительно требует latest fresh CLEAN binding, exact artifact/source/version
и storage lifecycle/tombstone checks; images требуют собственного bounded model input contract.
