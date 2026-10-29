# ADR0023 — URL textual generation и Identity provenance

Дата: 2026-10-14. Задание04 / #121. Prerequisite03: `cbf585b8a57edeb41169ffc64594130fcf5ee7b5`.
[ADR0021](0021-source-kind-ai-eligibility.md) P1–P3 утверждены; product/provider policy не меняется.

## Решение

Существующий `AnalyzeEvidenceUseCase` выбирает на сервере latest admission каждого current
TEXT/PDF_TEXT/URL evidence; максимум5 источников и20000 UTF-8 bytes всех reviewed payloads.
URL JSON overhead входит в бюджет. Набор не обрезается, invalid selected receipt не заменяется
старым. URL dispatch определяется admission policy, а не JSON-похожим содержимым manual TEXT.

`AnalysisTextSource` хранит exact projection и внутренние decoded fields. Canonical equality с
`UrlTextSnapshot.project` запрещает unknown/duplicate/trailing JSON, неправильный format и
подмену values. Fields не сериализуются повторно (`JsonIgnore`); model-visible payload один.
`representation` различает plain-text-v1/url-text-v1. Private references остаются в physical witness.

Citation URL = admissionId + fieldPath(title/description/text) + exact substring decoded value.
Нельзя цитировать format/JSON syntax или соседнее поле. Null fieldPath означает только legacy
TEXT/PDF substring semantics. Дополнительная nullable column V40 не переписывает старые rows.
Для любого набора с URL используется digest prefix `mixed-evidence-v2`; TEXT/PDF сохраняют
побитовый `text-evidence-v1`. Остальная digest encoding неизменна: Brand/industry и ordered
admissionId/evidenceId/sourceVersion/contentChecksum. Projection type однозначно связан с
immutable admission. SQL deferred seal вычисляет тот же digest и общий budget.

## Guards и orchestration

Capture/preflight/publication сохраняют Account→Workspace→Brand→Evidence→Quarantine locks,
actual active OWNER/member, source/parent versions, sourceVersion-wide withdrawal, latest CLEAN,
exact acquisition receipt/job attempt, scan expiry и retained ledger. `UrlAnalysisSourcePolicy`
использует `UrlReviewContent`, поэтому FETCHED/CLEAN сами по себе не permission.

До model call и после него проверяются exact private HTML bytes/size/hash и локальный
website-v2 extraction через существующий review workflow; нового network fetch нет. Перед
publication выполняется fresh transaction. Attempt60s, physical batch30s,2 concurrent analyses.
Final publication сохраняет citations/signature/SUCCEEDED атомарно; source selection receipts
уже committed RUNNING history и при failure остаются audit trail без новой signature.

Identity copy/direct/inherited approval использует общий `EvidencePhysicalSource` witness:
PDF сохраняет свой verifyBytes, URL добавляет hash + exact local extraction. External I/O вне
transaction; повторный capture требует полного равенства witness и fresh access/versions/scan.
Historical provenance read сохраняет URL snapshot ID, original/latest scan IDs, fieldPath и
withdrawn state; withdrawal не стирает immutable APPROVED history.

V40 расширяет analysis/Identity guards и field-specific SQL citation validation. Runtime
UPDATE/DELETE immutable receipts остаются запрещены существующими grants. SQL проверяет DB
metadata, но не может проверять внешние storage bytes; application witness обязателен.

## Alternatives / trade-offs

- Только разрешить FETCHED в query — мало кода, но позволяет обойти bytes/withdrawal/Identity;
  отвергнуто, kind-specific guards обязательны на каждой use boundary.
- Цитировать JSON substring без path — неоднозначное поле и escaped quotes; отвергнуто ADR0021.
- Переписать все digests/citations на v2 — ломает доказанное history; additive nullable path и
  новый prefix только URL batches сохраняют old receipts.
- Новый отдельный URL generator — дублирует orchestration и race guards; переиспользован общий flow.

## Threat model

Защищаем tenant content, private artifacts, consent, immutable provenance/approval. Callers:
actual OWNER; HTTP selectors, storage bytes, webpage text и model output недоверенные.

- BOLA/access revoke: scope + DB membership + fresh Account lock; foreign/role existing gates.
- Prompt injection: data-only bounded projection; port без tools/fetch/commands, exact selected
  citations, validated result. Это доказывает backend boundary, не качество/устойчивость live LLM.
- SSRF/private disclosure: consumers не вызывают UrlEvidenceFetcher; private keys/lease/source
  URL не добавляются к model metadata. Если пользовательский текст сам содержит URL, он остаётся
  буквальными данными, не становится разрешением network access.
- Stale bytes/scan/expiry/withdrawal: physical witness + fresh metadata + SQL, atomic publication.
- Resource exhaustion: общий20k budget,5sources,10quotes, bounded storage/parser,30/60s и bulkhead.
- Mass assignment/history forgery: existing strict DTOs, immutable receipt columns/triggers,
  runtime least privilege и exact field path allowlist.

Обход application metadata check встречает SQL guard; SQL не заменяет physical verification.
Нет новых dependencies, tokens/logging/raw HTML exposure. Bearer-only stateless auth сохраняется;
CSRF/session policy не изменена. Live adapter/privacy, distributed quotas, storage IAM/immutability,
visual understanding/social typed consumers и approved Identity→Strategy остаются отдельно.

[Qualification report](../issue-121-url-consumption-slice.md).
