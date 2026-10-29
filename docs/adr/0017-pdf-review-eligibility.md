# 0017 — Private PDF_TEXT review eligibility

Статус: принято для первого non-TEXT eligibility slice #121.

## Решение

`GET /evidence/{evidenceId}/document-review` показывает original bounded PDF_TEXT artifact
только actual active OWNER/member. PDF source остаётся quarantine FILE/PENDING; admittedToAi
в response всегда false. Существующие admission/model consumers остаются TEXT-only.
Этот endpoint готовит human review, не предоставляет AI consent или admission.

Поддерживается FILE/application/pdf с exact current PDF_TEXT artifact/source binding.
Artifact's original scan и latest scan обязаны быть CLEAN, того же sourceVersion/checksum;
latest scan не из будущего и не старше существующего bounded artifact max-scan-age policy
(default24h, range1s–7days). Latest scan может обновиться, но original artifact scan не
подменяется. Snapshot response фиксирует оба ID и versions для будущего admission contract.
Output checksum проверяет exact UTF-8 text; blank/malformed/oversized text rejected.

Quarantine ledger должен быть REFERENCED, с exact reference/evidence/Brand/Workspace,
actual bytes1..10MiB; tombstone/missing/wrong scope rejected. Capture под Account → Workspace
→ Brand → Evidence → Quarantine locks сохраняет bounded internal snapshot. Storage read
выполняется вне DB transaction: complete bytes, size, PDF header и SHA-256 обязаны совпадать.
Fresh finalize transaction повторяет actual access, source/artifact/latest scan/lifecycle
checks и сравнивает полный snapshot. Deadline30s запрещает отдачу запоздалого preview.
Scan expiry exclusive: запрос в точный момент expiry уже отклоняется. Process-local semaphore
разрешает максимум два параллельных preview; третий получает safe503 без очереди.
Slot освобождается в finally при success, отказе capture, storage failure и finalize failure.
Это ограничивает concurrent bounded reads, но не заменяет gateway rate limiting.

URL/screenshot, FILE images и social inputs fail-closed, независимо от FETCHED/CLEAN.
Нет OCR/multimodal/provider policy; screenshot не считается извлечённым текстом.
Новые поля AI admission/receipt не вводятся и V1–V35 не меняются.

## Alternatives / trade-offs

Сразу разрешить PDF model input упростило бы demo, но потеряло бы exact artifact/latest-scan
review selectors и current storage witness на generation/Identity boundaries. Поэтому первый
slice — fully verified preview. Следующий admission slice должен сохранить selectors и
расширить publication/approval guards, не переиспользовать TEXT-specific checks молча.
Повторный PDF parse в JVM не нужен: bounded text уже получен isolated renderer.
Цена physical storage verification — один bounded private read до10MiB; не долгий DB lock.

## Threat model и границы

Защищаем tenant document text, storage metadata, access и scan provenance.
Границы: HTTP principal → actual DB rights; stored rows → trusted snapshot;
private bytes → verified checksum; external I/O → final response.
Threats: BOLA, forged keys/artifact links, stale/non-CLEAN scans, tombstone/byte loss,
source/archive/access races, oversized/malformed text и unsafe error disclosure.
Defense: scoped queries, principal binding, reviewed locks, immutable source/scan/artifact
history, bounded ports/domain validation, physical witness и full finalize recheck.
DTO не раскрывает object reference/key, lease owner/job metadata или полный PDF.

Readiness актуальна только в момент finalize, не постоянное разрешение. Rogue external
storage deletion после проверки не блокируется DB locks; future consumer обязан проверить
свои prerequisites снова. Scan не доказывает отсутствие всех threats/истинность текста.
Live provider, non-PDF eligibility, persisted PDF admission и production activation отдельны.

Tests: authorized HTTP preview; forbidden/foreign/unsupported; missing/wrong artifact/scan/
ledger; negative latest scan/expiry; missing/mutated/oversized bytes; external I/O без DB
transaction; source/latest scan/access/archive races, bounded parallel reads; tombstone
rejected в domain tests, existing PostgreSQL guards защищают REFERENCED ledger от cleanup.
Фактическая qualification — в отдельном slice report после выполненных запусков.
