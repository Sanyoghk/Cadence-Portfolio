# Bounded TEXT/PDF/URL evidence analysis

`POST /api/v1/cadence/brands/{brandId}/analyze-evidence` — отдельное OWNER action,
без request body. Actor и Workspace scope берутся из authenticated principal;
backend повторно проверяет active DB account и current membership.

Сервер выбирает latest reviewedAt/id admission каждого TEXT/PDF_TEXT/URL textual evidence в актуальном
source/Brand/Workspace context, в порядке evidence UUID. Client не выбирает source IDs.
PDF требует explicit consent с current artifact, exact original/latest CLEAN scans и expiry;
при неактуальном latest consent fallback к старому receipt запрещён.
Нужны 1–5 источников, общий предел — 20000 UTF-8 bytes. Нулевой набор, превышение,
withdrawal, stale versions или archived parents дают 409. Набор не усекается молча.
Неотозванный review актуален только в своём source/Brand/Workspace version context.
После успешного анализа Brand version растёт: для следующего evidence action нужен
новый explicit review в новом context. Отозванная sourceVersion не допускается повторно.

200 возвращает `analysisId`, `signature`, `citations`, `warnings`, `provider`,
`modelVersion`, `promptVersion`; `Cache-Control: no-store`. Каждая citation содержит
`admissionId`, `quote` и nullable `fieldPath`: TEXT/PDF сохраняют точную подстроку reviewed text
и null path; URL требует title/description/text и exact substring decoded field value.
Максимум 10 citations; quote ≤500 UTF-16 units и ≤1000 UTF-8 bytes.
Foreign/missing/invented/duplicate цитаты от adapter дают controlled 503,
publication не происходит. Неизвестный/чужой Brand скрывается 404, actual access
revocation — 403, invalid UUID — 400, anonymous request — 401.

Для PDF/URL перед моделью и перед publication выполняется полная физическая проверка bytes
через private snapshot workflow вне DB transactions. После каждого batch весь набор
источников перепроверяется в новой transaction; после модели есть дополнительный metadata
preflight перед storage read. Лимит batch —30s, всего attempt —60s; максимум два simultaneous
analysis на process, busy503. Эти лимиты не являются distributed quotas/provider timeouts.

Sources и цитаты сохраняются как immutable attempt history. Publication после
external I/O заново проверяет account, membership, lifecycle, versions, withdrawal
и 60-second deadline. Sources/citations/signature/SUCCEEDED фиксируются атомарно;
отказ сохраняет FAILED/SUPERSEDED без замены signature.

## Что пока не поддерживается

- URL требует explicit admitted textual snapshot и повторное local extraction. Image/social
  typed consumers остаются отдельным scope05; social generic TEXT gap ADR0021 ещё открыт.
  Ни один input не допускается только по FETCHED/CLEAN. Для PDF одного
  CLEAN также недостаточно: требуются artifact, explicit consent и physical revalidation.
- Live adapter не выбран; production остаётся fail-closed. Local/test fake явно
  возвращает DEMONSTRATION_ONLY и HUMAN_REVIEW_REQUIRED. Цитаты fake демонстрируют
  contract, его цвета/voice не являются доказанными выводами из источников.
- Exact quotation — доказательство происхождения текста, а не его истинности,
  смысловой релевантности или устойчивости live модели к prompt injection.
- Legacy `Identity from-analysis` принимает declared-only receipts. Evidence receipts
  используют explicit `from-evidence-analysis` с actual actor/source/withdrawal checks.
  PDF receipts поддерживаются с V38: copy/approval проверяет original publication lineage,
  exact PDF receipt и свежие physical bytes; [ADR0020](adr/0020-pdf-identity-consumption.md).
  [Identity contract](brand-identity-api.md) описывает citations/read/approval/lineage.
  APPROVED Identity → Strategy остаётся отдельным незавершённым этапом.
- Durable AI Jobs, budgets, rate/cost controls и real provider timeouts — отдельные
  незавершённые production scopes; данный synchronous action их не заменяет.

[ADR0015](adr/0015-bounded-text-analysis-citations.md),
[PDF extension ADR0019](adr/0019-pdf-evidence-analysis.md).

URL extension: [ADR0023](adr/0023-url-analysis-identity.md). Mixed URL batches используют
`mixed-evidence-v2`; прежние TEXT/PDF digests сохраняются. Provider activation отдельно #120.

## Social textual consumers05

[Typed social review API](social-evidence-admission-api.md) использует immutable social-text-v1
snapshot и explicit consent. Trusted origin запрещает generic TEXT fallback, включая legacy
и inherited Identity. Same-account current binding проверяется при review/generation/copy/approval.
Batch с social получает social-evidence-v3; прежние TEXT/PDF/URL receipts сохраняются.
[ADR0025](adr/0025-social-text-review-consumption.md), [qualification05](issue-121-social-consumption-slice.md).
Visual inputs остаются REQUIRES_PROVIDER, live OAuth/privacy не квалифицированы fixtures.
