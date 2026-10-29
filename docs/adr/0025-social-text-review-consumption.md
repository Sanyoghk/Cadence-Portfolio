# ADR0025 — Typed social review и consumers

Дата: 2026-10-14. Scope05, #121. Policy: P1/P3 [ADR0021](0021-source-kind-ai-eligibility.md).
Baseline consumers: `449735b`; visual boundary: `be6a03e`, [ADR0024](0024-visual-evidence-provider-boundary.md).
Статус: IMPLEMENTED / QUALIFIED local social scope, qualification в [отчёте05](../issue-121-social-consumption-slice.md).

## Решение

Official social ingest остаётся normalized `approved-social-v1` TEXT + immutable trusted
`EvidenceSocialOrigin`. Новый `SocialTextSnapshot` хранит точную проекцию `social-text-v1`,
sourceVersion/checksum, исходный capture time evidence и provider capture time, providerCallId,
локальную ordered post-ID map, projection version и SHA-256 payload. Связанный origin хранит
provider/account/API version. При dedup исходные receipt/time не обновляются.

`SocialTextProjection` строго проверяет shape, duplicate keys, trailing data, Unicode, размеры,
provider/account/API equality, ≤20 уникальных posts в исходном ID order и timestamp bounds.
Проекция фиксирует порядок format/accountName/biography/posts и publishedAt/text/title (TikTok).
Account ID/post ID/API version исключены из model metadata; содержательные строки не редактируются.
Одна projection ≤20000 UTF-8 bytes; complete batch ≤5 sources /20000 UTF-8 bytes включая JSON.
Oversize и пустой substantive content отвергаются, скрытого truncation после review нет.
Предупреждение review сообщает historical one-page/text-only и возможный upstream truncation.

## Review и consent

`GET /api/v1/evidence/{id}/social-review` выполняет actual OWNER/membership/scoped lookup,
Account → Workspace → Brand → Evidence locks, current origin/binding validation. Создаёт или
читает bounded immutable snapshot; создание snapshot **не** создаёт consent/admission.
GET может материализовать immutable derived snapshot, но не выполняет network fetch и не
изменяет original acquisition. Полный preview показывает exact text, provider/capture time,
policy, warnings, selectors и binding expiry; responses no-store.

`POST .../social-review/admissions` принимает только snapshotId, source/brand/workspaceVersion,
contentChecksum, bindingExpiresAt. Actor берётся из authenticated principal; неизвестные поля
отвергаются. Snapshot/expiry/context должны совпасть с preview. Receipt append-only содержит
review actor/time и policy `social-text-review-v1`. Current read:
`GET .../social-review/admissions/{admissionId}/snapshot`. Withdrawal использует существующий
общий endpoint и действует на весь sourceVersion. Он доступен и после archive/binding expiry.

Same-context duplicate отвергается. Изменённый binding expiry требует нового explicit review;
новый receipt не оживляет старый. Новый sourceVersion создаёт новый snapshot. Snapshot и review
history ограничены по1000 на evidence. Withdrawal старого receipt блокирует все receipts той же
sourceVersion. Immutable original origin/checksum при этом сохраняются.

## Binding и consumers

`ApprovedSocialEvidenceSource.requireApprovedAccount(brand, provider, accountId)` — local-only
port без HTTP/storage I/O и credentials в return. Default implementation fail-closed.
`OfficialSocialEvidenceSource` проверяет current enabled/approved binding, required scopes,
тот же account/provider/brand и expiresAt > now+30 seconds; возвращает только expiry.
Review/analysis/copy/approval повторяют проверку. Изменение expiry не переносит старый consent.
Исторический snapshot не получает произвольного TTL; binding expiry не является retention policy.

`AnalysisTextSource` различает plain/URL/social; social citations — accountName, biography,
posts[0..19].text/title, exact decoded substring. publishedAt остаётся контекстом, самостоятельная
timestamp citation не считается substantive evidence. Модель не выбирает источники или paths
вне server-selected payload. Output проходит существующий `BrandIngestService` validator.
Digest prefix `social-evidence-v3` применяется к batch с social; TEXT/PDF `text-evidence-v1`
и URL `mixed-evidence-v2` остаются неизменными. Старые receipts не переписываются.

`SocialAnalysisSourcePolicy` участвует в start/preflight/final publication и Identity copy,
direct/inherited approval. Physical FILE/URL witnesses остаются отдельными: social DB text
не получает выдуманный malware scan. URL/PDF physical checks не пропускаются в mixed batch.
TEXT withdrawal сохраняет прежний `SUPERSEDED`; post-model preflight обязателен для structured
social/URL и PDF. Production AI adapter по-прежнему fail-closed; external egress не разрешён.

## Закрытие generic TEXT downgrade

Trusted origin означает только typed route. Generic preview/admit/current read, generation,
Identity copy и direct/inherited approval отклоняют originated source с `text-review-v1`.
Это касается legacy receipts и origin, прикреплённого позднее после dedup; APPROVED history
и authorized historical provenance остаются неизменными. JSON без origin остаётся обычным
недоверенным TEXT и не получает provider attribution по содержимому.

V41 сериализует origin attachment через Workspace → Brand → Evidence, чтобы concurrent
origin insert не прошёл между проверками и publication. V41 также добавляет append-only
snapshot/admission guards, canonical projection/hash/provenance/expiry/withdrawal checks,
typed citations, digest и Identity guards. Runtime role не может UPDATE/DELETE snapshots.

## Defense in depth и границы

- HTTP role/scoped DTO → actual actor/membership/parent locks → immutable source/projection →
  fresh current policy → bounded model port → exact output → locked publication/SQL guards.
- SQL проверяет immutable origin, provenance, payload, context и сохранённый expiry. SQL не
  видит mutable operator configuration/scopes: current binding проверяется application port.
  Config reload/revocation не является распределённой транзакцией с PostgreSQL; production
  управление bindings требует собственной qualification, синхронный live OAuth здесь не создан.
- Никаких model tools/arbitrary fetch, media links или private refs. Caption instructions
  остаются данными. Scan не защищает от prompt injection; guards и human approval обязательны.
- Safe errors не включают provider tokens/URLs/content. Новых dependencies нет.

## Alternatives

Generic JSON TEXT проще, но отправляет лишние identifiers и обходит binding policy — отвергнут.
Новый parallel generation pipeline дублировал бы security guards — переиспользованы текущие
orchestration и Identity lineage policies. Перезапись legacy receipts нарушила бы audit history —
сохранена история и введён запрет нового использования. Новая таблица snapshot даёт immutable
preview/model mapping и воспроизводимые citations ценой дополнительного bounded storage.

Visual inputs не реализованы этим ADR: обязательный MVP gap REQUIRES_PROVIDER #120 сохраняется.
#32/#33 live OAuth/account onboarding и provider privacy/retention не квалифицируются fixtures.
