# ADR0018 — Explicit PDF admission history

Дата: 2026-10-14. Принято для следующего законченного #121 slice.

PDF consent хранится в существующей append-only `evidence_admissions` с policy
`pdf-text-review-v1`, exact artifact/original/latest scan IDs, renderer и scan expiry.
TEXT rows сохраняются с NULL PDF fields и `text-review-v1`; никакого backfill/READY transition.
V36 сохраняет V1–V35 и TEXT uniqueness, добавляя PDF uniqueness по context/artifact/latest scan/expiry.
Обычный `(evidence_id,source_version)` index сохраняет lookup для общих cap/withdrawal queries,
которым partial policy indexes не подходят. SQL ledger lookup использует exact object_key index.
Fresh CLEAN rescan требует нового human confirmation; old receipt не переписывается.
Withdrawal любого admission по-прежнему необратимо блокирует sourceVersion во всех contexts.

POST `/document-review/admissions` принимает selectors из preview, не client text/audit/key.
Три phases используют общий process-local two-slot bulkhead: capture under Account → Workspace
→ Brand → Evidence → Quarantine locks, bounded physical storage read вне transaction, fresh
finalize/save в новой transaction. Actor всегда principal; actual active OWNER/membership
проверяются дважды. Stale/replay/withdrawn/limit1000 дают safe409; storage failure503.
GET `/document-review/admissions/{admissionId}/snapshot` проверяет receipt selectors, current
source/parents/scan/expiry/withdrawal и physical storage теми же phases. Latest scan change
не позволяет молча обновить consent. Старый TEXT snapshot route PDF отвергает.
Отзыв выполняется существующим scoped `ai-admission/{admissionId}/withdrawals`, даже для
archived/stale PDF. Admission/withdrawal сериализуются Account/parent/Evidence locks.

SQL invoker guard проверяет actual actor, parents, source, immutable artifact/scan Job,
latest CLEAN ordering/expiry, ledger/key/bytes, text/checksum/renderer и cap/withdrawal.
SQL не видит private storage: physical witness обеспечивает application orchestration.
Stored expiry имеет независимый SQL bound1s–7d; application использует configured max-scan-age.
Receipt snapshot сравнивает policy expiry с current policy и original expiry, не продлевает её.
Renderer version и text остаются недоверенными данными; prompt instructions не исполняются.

Alternative separate PDF table отвергнута: дублировала бы withdrawal/source-version semantics
и два consent histories для одного evidence. Расширение shared history требует строгого
policy discriminator и независимых TEXT/PDF constraints. JPA/H2 null unique semantics не
заменяют production partial UNIQUE indexes и PostgreSQL tests.

Существующие model selection/analysis/Identity/SQL publication guards остаются TEXT-only.
PDF consent не включает model consumption в этом slice; DTO явно сообщает
`aiConsumptionEnabled=false`. Следующий slice должен повторно проверять PDF bytes/scan/source
перед generation/publication/copy/approval; live providers не выбираются этим решением.
No new dependencies, migration/runtime roles existing grants; no SecurityFilterChain changes.

Threat model: защищаем private text и consent audit. Callers — actual OWNER/member; boundaries
HTTP selectors → current DB graph → bytes witness → committed receipt. Attacks: forged selectors/
tenant/actor, stale/latest ERROR scan, wrong bytes/ledger, withdrawal races, replay/history
rewrite, oversized text and quota abuse. Defense: deny-by-default scoped queries, immutable
DTO/domain/SQL history, parent-first locks, physical checksum, fresh finalize и bounded reads.
DB/runtime identity trusted backend; SQL-only write не доказывает physical bytes availability.
Production quotas/rate limits, privacy/retention и live infrastructure qualification отдельно.
