# Private PDF review API

`GET /api/v1/evidence/{evidenceId}/document-review` возвращает bounded извлечённый текст PDF
для human review. Требуются authenticated OWNER, actual active account и membership текущего
Workspace. Actor берётся из principal; foreign evidence даёт404. Response `Cache-Control: no-store`.

Поддерживается только FILE/application/pdf/PENDING с current immutable PDF_TEXT artifact и
SUCCEEDED extraction Job. Original artifact scan и latest scan должны быть CLEAN и связаны с
exact source version/checksum; latest scan имеет exclusive expiry (default24h, configurable1s–7d).
Ledger должен быть REFERENCED с exact server-generated key, tenant/evidence binding и1..10MiB
actual retained bytes. Missing artifact/scan/ledger, unsupported input или stale state дают409.

Storage read выполняется вне DB transaction с exact expected byte limit. Backend проверяет
полноту, размер, PDF header и SHA-256 bytes, затем в новой transaction повторяет access/source/
artifact/scan/ledger checks. Deadline30s; максимум два simultaneous preview на process. Busy,
unreadable или corrupted storage дают safe503 `document_review_unavailable`.

Response содержит `evidenceId`, `artifactId`, `originalScanId`, `latestScanId`, source/Brand/
Workspace versions, source/content checksums, original text (до16000 UTF-16 code units),
renderer/policy versions, scanExpiresAt/checkedAt UTC. `requiresHumanReview=true`,
`admittedToAi=false`. Storage keys/references, complete PDF и job/lease metadata не возвращаются.

Это read-only операция: source не становится READY, review/admission не сохраняются, AI
provider не вызывается. TEXT admission route остаётся TEXT-only. URL/screenshots,
images и social inputs пока отклоняются. Client должен показывать text как plain text:
извлечённый `<script>` и инструкции внутри PDF остаются недоверенными данными.

Snapshot актуален на момент finalize; следующий consumer обязан заново проверить источник.
CLEAN scan не доказывает истинность текста или отсутствие всех возможных malicious payloads.
Полный OpenAPI contract: `src/main/resources/openapi/cadence.json`; решение: [ADR0017](adr/0017-pdf-review-eligibility.md).

## Explicit PDF admission и актуальный snapshot — ADR0018/V36

`POST /api/v1/evidence/{evidenceId}/document-review/admissions` принимает только selectors из
preview: `artifactId`, `originalScanId`, `latestScanId`, `sourceVersion`, `brandVersion`,
`workspaceVersion`, `sourceChecksum`, `contentChecksum`, `scanExpiresAt`, `policyVersion`.
Все поля обязательны; UUID/versions/checksums/time/policy проверяются. Policy — строго
`pdf-text-review-v1`. Неизвестные fields, включая text/reviewedBy/role/private key, дают400.
Backend actor/time берёт из principal/Clock и сохраняет exact server text, не client text.

Admission повторяет capture → bounded physical read → fresh finalize/save. Versions, scans,
expiry/policy и selectors должны соответствовать preview; stale/replay/withdrawn/limit1000
дают409, unreadable/corrupted/busy storage503. POST201 возвращает immutable receipt metadata:
IDs/versions/checksums/reviewer/time/policy/renderer/expiry и `aiConsumptionEnabled=true`
(расширение ADR0019/V37: backend поддерживает отдельный evidence analysis consumer;
это не утверждение о наличии live production provider).
Source остаётся FILE/PENDING, model не вызывается. Существующий preview GET по-прежнему
возвращает `admittedToAi=false`: он не является lookup существующих consents.

`GET /document-review/admissions/{admissionId}/snapshot` возвращает `{admission,text}` только
после новой полной eligibility/physical/access/withdrawal проверки. Receipt должен совпадать
с current artifact/latest scan/version context/expiry. Новый CLEAN scan требует нового explicit
confirmation; original receipt сохраняется, replay того же exact context даёт409.
Current policy change не продлевает original expiry: требуется новое preview/review.

Отзыв использует существующий `POST /api/v1/evidence/{evidenceId}/ai-admission/{admissionId}/withdrawals`.
Actual active OWNER/member может отозвать archived/stale PDF, без private storage read. Отзыв
append-only, replay409; любой withdrawal блокирует всю sourceVersion во всех contexts/scans.
History/read audit immutable; новый sourceVersion не создаётся этим API.

Receipt metadata и text private: no-store, scoped access, no key/lease/job fields. Old TEXT
snapshot route PDF отвергает. `analyze-evidence` выбирает eligible PDF_TEXT вместе с TEXT
и заново проверяет physical bytes до модели и publication; Identity copy/approval проверяет original publication lineage, physical bytes и fresh access/source fences (V38). Details: [analysis contract](evidence-analysis-api.md), [ADR0019](adr/0019-pdf-evidence-analysis.md).
[Решение ADR0018](adr/0018-pdf-admission-history.md), [qualification report](issue-121-pdf-admission-slice.md).
