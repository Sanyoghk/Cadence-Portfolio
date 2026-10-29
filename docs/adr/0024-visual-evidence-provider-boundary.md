# ADR0024 — Visual evidence: точный контракт и provider blocker

Актуализация 2026-10-15: [ADR0027](0027-backend-first-ai-adapters-last.md) утверждает backend-first, AI adapters last
и прямой image → AI → Identity без обязательного отдельного OCR/interpretation stage.
#121 остаётся OPEN; visual gap не отменён и не блокирует независимые backend scopes.


Дата: 2026-10-14. Задание05, #121. Baseline `449735b`.
Статус: ACCEPTED technical boundary в пределах P2/P3 [ADR0021](0021-source-kind-ai-eligibility.md).
Реализация visual interpretation **REQUIRES_PROVIDER**, не fulfilled MVP input.

## Фактический input

FILE PNG/JPEG — retained original bytes и scan receipts. URL SCREENSHOT — private PNG
1280×720, output checksum, renderer `isolated-v1`, Job/attempt/lease и source/scan links.
`EvidenceRenderer.screenshot(byte[])` возвращает bytes, dimensions и renderer version.
Он не возвращает OCR, palette, layout, captions или semantic observations. Header/IHDR
проверка не квалифицирует image decoder. Social adapter получает только normalized JSON;
social media bytes/video/screenshots отсутствуют. PDF_TEXT не доказывает наличие OCR.

## Будущий port: обязательные поля и invariants

Это design contract для выбранного адаптера, а не зарегистрированный bean или HTTP endpoint.
До выбора provider нельзя безопасно объявить рабочую реализацию либо готовые decoder limits.

`VisualInput` содержит server-issued snapshotId, evidenceId/sourceVersion, representation
(`UPLOAD_IMAGE` либо `URL_SCREENSHOT`), exact byte checksum, media type, dimensions,
immutable original generation witness и derivative generation witness (если существует).
Witness содержит ledger ID/version, original/latest scan ID, scan expiry; для screenshot —
artifactId, original source checksum, renderer version, Job ID/attempt/lease provenance.
Private references доступны только server-side resolver, не модели и не клиентскому DTO.
Tenant/context IDs, actor/time, admission policy и withdrawal scope принадлежат локальному receipt.

`VisualInterpretationPort.interpret(AdmittedVisualInput, OperationDeadline)` может получить
только проверенные retained bytes, никогда client URL, storage key, arbitrary tools или OAuth
token. `AdmittedVisualInput` требует explicit human preview/consent exact snapshot и отдельную
processor/privacy policy. Непосредственно перед egress повторяются actual OWNER/membership,
source/context versions, latest scan, physical checksum/retention и withdrawal проверки.
После external I/O вне DB transaction повторяются guards до атомарной publication.

`VisualOutput` связывает request/snapshot checksum, provider/model/version, output schema,
observations с typed selectors и warnings. Нельзя принимать произвольные backend commands,
новые source IDs, URLs для fetch или provider-claimed access. Output validator ограничивает
размер, число observations, Unicode и допустимые selectors. Конкретные limits, confidence
semantics, region/coordinates и reproducibility должны быть зафиксированы выбранной versioned
policy вместе с provider qualification; сейчас они **не определены и не считаются verified**.

OCR output — отдельный derived text snapshot: engine/model/version, original image checksum,
exact UTF-8 text checksum, warnings, extraction time, immutable provenance, новый human review.
Consent изображения не переносится автоматически на OCR text. Без OCR engine нет OCR receipt.

## Решения, которые ещё нужны (#120)

1. Vision versus OCR либо оба пути. Vision покрывает visual style, OCR только читаемый текст;
   OCR alone не выполняет весь visual MVP scope. Рекомендация: квалифицировать vision для
   visual style и отдельно оценить необходимость OCR; это рекомендация, не выбор provider.
2. Конкретный provider/model, supported formats/limits и decoder isolation/preprocessing policy.
3. Разрешённый processor/region, data transmission, retention/deletion/backups, terms и consent UI.
4. Реальные credentials и qualification, включая image decoder abuse, prompt injection,
   timeout/cost/resource limits, malformed output, privacy и revocation races.

Ни один из этих выборов не заменён fake credentials. #32/#33 остаются live social onboarding
gates. Social photos/video дополнительно требуют ingestion contract; existing account text
binding не даёт разрешения произвольно скачивать linked media.

## Threat / Protection / Verification

- Threat: image bomb/decoder exploit. Protection: selected isolated decoder с bounded bytes,
  pixels, memory/time, no network. Verification: pending provider/decoder qualification.
- Threat: prompt injection/SSRF. Protection: bytes-only port, data-only observations,
  server-selected sources, strict output validator. Verification: pending visual adapter;
  existing renderer contract сам по себе не доказывает безопасность будущего vision path.
- Threat: BOLA/privacy leak/stale consent. Protection: scoped parent locks, exact immutable
  receipt, current checks before egress/publication. Verification: pending visual runtime.
- Threat: fabricated observations. Protection: no semantic fallback from filename/metadata,
  explicit REQUIRES_PROVIDER. Verification: inspection текущих renderer/scan/social contracts.

## Alternatives и цена решения

Отправлять bytes после CLEAN проще, но scan не означает consent и не проверяет processor policy.
Выдавать metadata за vision неверно по смыслу. Удалить images из MVP противоречит P2.
Выбран точный blocked contract: он сохраняет product gap видимым и не добавляет неиспользуемые
production interfaces с выдуманными limits. Social textual slice05 продолжается независимо.

## Qualification и границы

Docs-only: проверка relative links, Markdown fences, `git diff --check`, inspection contracts.
Maven не запускался для этого ADR; старые passing tests04 не являются visual qualification.
Runtime/API не изменены, visual model input по-прежнему отсутствует. #121 не закрывается.
