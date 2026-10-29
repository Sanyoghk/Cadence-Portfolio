# ADR-0011: Scoped Strategy lifecycle, revision lineage and approval audit

Status: accepted for #102 repository scope. Date: 2026-10-05.

## Контекст

V24 запрещает mutation approved content/collections. REST должен позволять human review,
не теряя историю и не подтверждая content, который другой запрос успел изменить.
JPA flush обновляет root раньше owned collections; combine-edit-and-approve может
попытаться переписать уже APPROVED collection. Старые записи не содержат approval actor/time.

## Решение

Существующие create/read/list дополняются PATCH полного editable content и отдельным
POST approval с expectedVersion=JPA lockVersion. StrategyContent — immutable validated
value, DTO — allowlist внешних writable полей. DRAFT edits сохраняются на месте.
APPROVED/LOCKED edits создают новый DRAFT max(version)+1 с immutable revisedFromId;
generation receipt и approval audit не копируются. Source сохраняется неизменным.

ManageStrategyUseCase использует REQUIRES_NEW/READ_COMMITTED, scope-first lookups,
Workspace→Brand→Strategy locks; approval сначала Account lock, затем тот же порядок.
Approval читает committed content в fresh persistence context, проверяет current DB
active OWNER/membership, записывает server actor/time и не изменяет content.
Snapshot DTO строится до закрытия transaction; OSIV=false.

V27 добавляет self/user FK, paired audit constraint, immutable audit/lineage trigger
и расширяет существующий deferred content validator для revised/newly-approved rows.
V1–V26 не меняются. Legacy actor/time/lineage остаются NULL, поскольку backfill выдумал
бы историю. Non-owner runtime имеет минимальный column UPDATE и не может delete roots.

## Alternatives и trade-offs

- Mutable approved Entity разрушает review history и несовместима с V24.
- Clone source.version+1 конфликтует с более новыми Brand versions; max под Brand lock
  обеспечивает согласование с generation completion.
- Combine-edit-and-approve подтверждает непрочитанный content и зависит от flush order;
  два commands делают human review явным, ценой двух запросов.
- Полный replacement content проще проверять атомарно, чем частичные pillar mutations;
  PATCH здесь не реализует JSON Merge Patch.
- Append-only каждого draft edit увеличил бы model/retention сложность; MVP сохраняет
  draft in place и audit самого approval. Полный draft-edit event ledger не заявлен.
- Несколько approved edits намеренно создают разные DRAFT branches. Request idempotency,
  global rate/retention quotas и AI Jobs остаются будущими gates.

DB audit связывает actor с существующим active tenant OWNER; это не криптографическое
доказательство HTTP identity при компрометации runtime credentials. Защиту JWT обеспечивает
Spring Security/Clerk; отдельный immutable external audit sink — будущий #87 scope.

## Проверка и границы

Shared H2/PostgreSQL HTTP contract покрывает success, denied roles, foreign IDs,
stale tokens, safe errors и mass assignment. PostgreSQL tests проверяют direct SQL
tampering, deferred integrity, concurrent edit/approve, branch/generation allocation,
archive lock order. Populated V23→latest upgrade сохраняет old content и NULL audits.
Full-provider integration проверяет actual restricted runtime role после latest migrations.
Реальные providers, deployed Clerk/production infrastructure и broader Playbook/Identity
source contract этим ADR не квалифицируются.
