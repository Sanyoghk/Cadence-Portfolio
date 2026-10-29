# ADR0022 — URL acquisition snapshot и explicit human admission

Дата: 2026-10-14. Scope03, #121; prerequisite [ADR0021](0021-source-kind-ai-eligibility.md)
P1–P3 утверждены. Baseline `54a0c3fe1f54f705eeebd65408f93e89426d4a3c`.

## Решение

Новый `UrlTextSnapshot` записывается атомарно с успешным URL fetch/extraction в `UrlFetchWorker`,
пока оригинальный Job attempt ещё RUNNING. Snapshot хранит source version/URL checksum,
fetched checksum/private generation reference, capturedAt/fetchedAt/extractedAt, website-v2/warnings,
exact `url-text-v1` payload/hash и original fetch Job/attempt/lease owner. После receipt Job
завершается в той же transaction. V39 запрещает подделку связи и rewrite/delete snapshot.
Без такого receipt legacy metadata не становятся eligible. Старые rows/migrations не изменяются.

Projection соответствует ADR0021: фиксированный порядок format,title,description,text;
compact JSON UTF-8, без BOM; только quote/backslash escaping, исходный Unicode сохраняется.
WebsiteMetadata запрещает controls; projection дополнительно отвергает unpaired surrogates.
SQL `url_text_projection` и Java дают одинаковый payload, это проверяет insert guard.
Acquisition сохраняет bounded extractor output даже если он слишком велик для AI. Review
отклоняет payload >20000 UTF-8 bytes или без содержательных title/description/text; дополнительной
невидимой обрезки нет. Неполный body допускается только с видимыми metadata/warnings.

URL API выделен в `url-review`, чтобы не менять TEXT/PDF selectors. Прежний общий withdrawal
используется для URL receipts: sourceVersion-wide, append-only, OWNER actor/time, replay rejected.
Уникальность admission включает snapshot/scan/expiry/parent versions; возможен explicit re-review
после parent version или latest CLEAN scan change. Withdrawal старого receipt запрещает всю
sourceVersion, включая новые parent contexts. Cap1000/evidence общий для всех policies.

## Проверка перед ответом/consent

`UrlReviewTransactions` capture под Account→Workspace→Brand→Evidence→Quarantine locks:
actual active OWNER/member, active context, immutable snapshot equality, SUCCEEDED original
fetch attempt, latest matching CLEAN и scan expiry (default24h, configured1s–7d), referenced
scoped generation. `legacy-unknown` scan provenance отвергается. Scan checksum — fetched bytes,
source checksum — URL registration identity: они не подменяются друг другом.

`ReviewUrlEvidenceUseCase` вне DB transaction читает exact private bytes (≤2MiB), проверяет
size/SHA-256 и повторяет локальный `WebsiteMetadataExtractor`. Exact metadata equality включает
version/warnings. URL fetching, redirects, screenshots и model calls здесь отсутствуют.
Вторая REQUIRES_NEW/READ_COMMITTED transaction повторяет весь capture и сравнивает snapshot;
deadline30s и two-slot bulkhead ограничивают операции. Изменившиеся permissions/source/scan/
context/withdrawal дают отказ. Network/storage failures возвращают safe503 без private refs.

Admission сохраняет reviewed payload, hash, snapshot ID, actor/time и capture-time scan.
`originalScanId=latestScanId` на момент review: это original scan consent, **не** выдуманный scan
до inline extraction. Последующий scan требует нового review; old receipt остаётся immutable.
Scan row содержит original scanner engine/signatures/Job/attempt. Current read повторяет physical
verification; stale historical receipt не возвращается как current. Withdrawal доступен и для
stale/archived source, не требует working storage.

## Альтернативы и границы

Копировать только inline metadata при review дешевле, но не доказывает original fetch attempt;
отвергнуто. Refetch по URL изменил бы источник и повторил SSRF boundary; отвергнуто. Locks во
время storage/parser I/O увеличивают lock exhaustion; вместо них capture/verify/final guards.
Нет общей distributed transaction DB/storage: rogue physical deletion после witness остаётся
operational risk, требующий private storage IAM/immutability qualification.

Snapshot acquisition добавлен сейчас, а не backfill: это намеренно делает старые URL ineligible.
Existing registration dedup не является refresh API; повтор того же URL не создаст автоматически
новый verified snapshot. Для legacy требуется отдельный будущий explicit re-acquisition lifecycle.
Произвольный rescan/new consent не изобретает отсутствующую fetch history.

SQL защищает metadata, actor/membership/context, latest scan, withdrawal/cap и immutability;
SQL не читает external bytes. Runtime имеет INSERT/SELECT, без UPDATE/DELETE receipts.
Новых dependencies нет. URL admissions не включены в `findCurrentReviewed` и model consumption;
`aiConsumptionEnabled=false`. Scope04 analysis/citations/Identity и05 social/visual не реализованы.

## Threat / Protection / Verification

- BOLA/forged actor/role: scoped ownership + method security + actual DB checks; API/direct-bean tests.
- Stale/revoked consent: fresh versions/latest scan/withdrawal + SQL guards; both PostgreSQL race orders.
- Spoofed bytes/extraction: private hash/size + exact local re-extraction; corrupt/missing/changed tests.
- SSRF/prompt injection: no URL fetch or model tools during review; text remains data, no AI call;
  tests verify fetcher/analyzer not invoked after acquisition.
- Legacy fabricated provenance: no V39 backfill, only RUNNING exact original acquisition can insert;
  populated migration and legacy eligibility tests.
- Resource exhaustion/history growth:2MiB source,20k UTF-8 projection,1000 receipts,2 concurrent reads,
 30s lifetime; boundary tests, existing bounded storage/parser configuration.

Фактические результаты qualification см. [отчёт03](../issue-121-url-admission-slice.md).
