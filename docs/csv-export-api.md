# CSV export API v1

Контракт: [ADR0035](adr/0035-durable-csv-export.md), task18/#103. Все endpoints OWNER-only,
Bearer authentication + текущая active Account/membership. DTO строгие, unknown fields400.
Ниже ID placeholders, не credentials. Foreign/nonexistent export/Job404, роль403, anonymous401.

## Submit / status / download

`POST /api/v1/brands/{brandId}/exports`, header `Idempotency-Key: export-demo-key-001`:

```json
{
  "from": "2026-10-17T00:00:00Z",
  "until": "2026-11-14T00:00:00Z",
  "timezone": "Europe/London",
  "selections": [
    {
      "postId": "00000000-0000-0000-0000-000000000001",
      "revisionId": "00000000-0000-0000-0000-000000000002",
      "revisionVersion": 1,
      "scheduleVersion": 1
    }
  ]
}
```

Период positive half-open, максимум28 local days; переход DST может давать671/673UTC hours.
Named timezone только для отображения, не меняет серверные настройки. Точность instants до
microseconds. Выбор1–84 разных Posts, все в этом Brand; только current APPROVED с audit,
current SCHEDULE head и exact still-approved full Strategy. Нельзя указать старую approval,
когда уже существует новый DRAFT. Interval не означает автоматический выбор всех Posts:
клиент явно выбирает записи из bounded calendar API; ошибки любого элемента отклоняют всё.

Response202 `CsvExportDetails`, `Location: /api/v1/jobs/{jobId}` и `Cache-Control: no-store`.
Key16–128 ASCII letters/digits/`_`/`-`; scope actor+Brand+CSV_EXPORT. Порядок selections
нормализуется; same key/same request возвращает same operation, даже после её terminal outcome.
Другой payload с тем же scoped key409. Новый key нужен для нового capture; новый key не
обходит общий Brand quota. Иной actor/Brand — отдельный namespace, не доступ к чужой операции.

`GET /api/v1/jobs/{jobId}` и `GET /api/v1/exports/{exportId}` возвращают одинаковый typed status:
`id/jobId/type/status/attempts/brandId/capturedBy/capturedAt/deadline/downloadExpiresAt/rowCount/
snapshotChecksum/artifactChecksum/errorCode/downloadPath`. Нет key, worker ID, lease,
input text, private storage key или provider diagnostics. `SUCCEEDED` — исторический факт;
`downloadPath` не является capability или подтверждением текущей eligibility.

`GET /api/v1/exports/{exportId}/download`: авторизованная выдача bytes, без redirect/presigned URL.
Повторно проверяются current access/parents, exact source/revision/schedule и private media.
Изменение между capture и финальной проверкой409/403; отсутствующие/corrupt private media503.
До SUCCEEDED, после exclusive expiry24h или при stale source409. Никаких частичных файлов.
Каждый ответ проверяется перед возвратом; уже полученные bytes невозможно отозвать.

Headers: `Content-Type: text/csv;charset=UTF-8`, `Cache-Control: no-store, private`,
`Content-Disposition: attachment; filename="cadence-export-{serverUUID}.csv"`, `nosniff`,
CSP `default-src 'none'; sandbox`, `Accept-Ranges: none`, `X-Cadence-CSV-SHA256`.
Любой Range400; нет conditional304, бессрочных bearer URL или client filename/path.

## CSV columns / semantics

Порядок41 columns фиксирован `CsvDocument.COLUMNS`:

```text
contract,source_policy,export_id,exported_by,captured_at,
post_id,revision_id,revision_number,revision_version,content_sha256,
platform,title,caption,hashtags,format,media_prompt,video_script,
strategy_id,strategy_revision,strategy_version,strategy_sha256,
schedule_id,schedule_version,scheduled_utc,schedule_timezone,schedule_local,
schedule_offset,schedule_rules_version,view_timezone,view_local,view_offset,
authored_by,authored_at,edited_by,edited_at,approved_by,approved_at,
media_state,media_references,generation_batch_id,generation_slot_id
```

UTF-8 BOM; commas; quote every cell; embedded quotes doubled; CRLF records; embedded newlines
и Unicode сохраняются. Hashtags соединены space. ISO dates/times; пустая nullable cell означает
отсутствующее значение, без fabricated attribution. Rows отсортированы scheduledUTC/PostUUID.
`CADENCE_CSV_V1`, `APPROVED_CREATIVE_V1`: snapshot approved creative work, не AI evidence consent.
`content_sha256` — оригинальный ADR0033 checksum, до безопасного spreadsheet escaping;
`snapshotChecksum` — SHA256 canonical ordered rows без export_id, `artifactChecksum` — точные
bytes CSV, включая BOM/CRLF и server export_id. Replays/retries не меняют capturedAt или bytes.

Поля с формулой после leading Unicode whitespace/control/format получают ведущий apostrophe;
leading TAB/CR/LF также neutralized. Затем CSV quoting. Это намеренное изменение опасной
cell representation, не перезапись оригинального Post. Импорт в конкретный spreadsheet/tool
не должен отменять защитный prefix. Generic manual-use CSV, без обещания Later/Buffer schema.
Правило действует на все cells, в том числе signed offset `+01:00`: его безопасная CSV
representation начинается с apostrophe. Это literal spreadsheet text, не исполняемая formula;
машинный importer должен учитывать объявленный safety prefix, а не молча запускать formulas.

`media_state=TEXT_ONLY` при пустых refs; иначе `DEMO_REFERENCES` и ordered semicolon-separated
`UUID:sha256:DEMO_FIXTURE:APPROVED_DEMO`. Нет bytes/URL, нового moderation/AI-generated claim.
Generation batch/slot nullable; original model/prompt/attempt/output checksum остаются в
immutable generation receipt, связанном с revision/lineage. Human edit не становится model output.

## Recovery / retention / operations

`CSV_EXPORT`: PENDING→RUNNING→SUCCEEDED; transient private input failure→FAILED→retry;
permanent stale/access failure или max3 attempts→DEAD. Controlled codes:
`CSV_INPUT_UNAVAILABLE`, `CSV_SOURCE_UNAVAILABLE`, `CSV_DEADLINE_EXCEEDED`,
`CSV_LEASE_EXHAUSTED`, fallback `CSV_FAILED`. Никаких exception messages в API.

Worker `cadence.csv-export.worker-enabled` / `CADENCE_CSV_EXPORT_WORKER_ENABLED`, defaultfalse.
Activate после migration/runtime grants; tick1s, один process-local worker, до10 candidates,
lease60s, submission deadline5min, backoff2–10s, scheduler infrastructure backoff30s.
Pure rendering/private verification вне DB transaction; final parent/source/lease/attempt
checks и atomic artifact+Job commit. Crash до commit оставляет RUNNING; restart/recovery
возвращает bounded FAILED/claim или DEAD. Crash после commit видит тот же SUCCEEDED,
не создаёт новый файл. AI budgets/provider outcome не участвуют: внешнего вызова нет.

Лимиты:2MiB CSV,100 retained exports/Brand (резерв максимум200MiB), включая failed/expired.
Download expiry не означает hard deletion/refund quota. Retention/delete product policy остаётся
отдельной: не выдавать очистку через SQL за approved lifecycle. Исторические references/audit
не удаляются. Для эксплуатации контролировать накопление и quota, pending/dead Jobs, health,
gateway rate limits, backup/restore и storage access. Нет публичного retry/debug/admin bypass.

Проверка recovery: submit valid approved scheduled selection → остановить worker после claim →
дождаться lease expiry → restart с worker enabled → один SUCCEEDED/artifact либо controlled DEAD.
Не менять вручную lease/attempt в production. SQL clock overrides в tests — failure injection,
не публичный API. После изменения Post/schedule/asset скачать старый Job нельзя; сначала явно
review/approve/schedule актуальную revision, затем submit новым key.
