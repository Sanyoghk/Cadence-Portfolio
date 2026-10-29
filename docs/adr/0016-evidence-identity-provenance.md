# 0016 — Evidence receipt → Identity provenance и approval

Статус: принято; TEXT Identity slice квалифицирован локально 2026-10-13.

## Решение

Отдельный `POST /brands/{brandId}/identities/from-evidence-analysis` создаёт DRAFT из
конкретного SUCCEEDED evidence receipt. Existing declared-only copy остаётся отдельным.
Request содержит только analysisId/expectedVersion; actor берётся из principal.
Copy сохраняет immutable analysisAttempt и copiedBy; citations не дублируются.
`GET /identities/{identityId}/evidence-provenance` возвращает bounded exact citations,
input digest, исходный Identity ID и withdrawal flags, без raw text/storage metadata.

Copy и approval проверяют actual active OWNER/membership под Account → Workspace → Brand
locks, затем Identity (если существует) и Evidence. Sources обязаны соответствовать
original admission/receipt context и текущему publication lineage: Brand version =
attempt.brandVersion + 1, Workspace version совпадает; source versions/checksums/text/policy
не изменились и sourceVersion не withdrawn. Completed receipt не истекает по generation
deadline: deadline уже проверен при publication; copy/approval используют текущие fences.

Manual DRAFT edits сохраняют original generation receipt и quotations, но не делают их
доказательством изменённого content. Approved edits создают новый manual DRAFT с lineage;
origin ищется по same-Brand цепочке строго меньших revision versions, максимум 1000 hops.
Approval проверяет inherited evidence origin, чтобы branching не обходил withdrawal.
Withdrawal после approval не переписывает immutable history. Future Strategy consumer
обязан заново проверять source eligibility; его integration — отдельный этап #121.

## Defense in depth и trade-offs

Domain factories различают declared/evidence modes. Application проверяет permission и
source graph. V35 (без изменения V1–V34) дублирует actor/source/copy/approval guards,
включая inherited lineage; runtime не получает UPDATE copiedBy. Нет legacy backfill.
Read historical provenance остаётся разрешённым для actual OWNER/member после withdrawal
и parent archive; flags не являются утверждением об общей eligibility.

Alternative: включить evidence в legacy copy без отдельного action — меньше API, но скрывает
изменение source contract. Дублировать citations в Identity — усложняет immutable history.
Выбраны explicit action и existing immutable receipt links. Цена: дополнительный bounded
origin lookup и повторные source checks при approval. Full approved Identity → Strategy,
non-TEXT, live provider, privacy policy, AI Jobs/budgets не входят в этот slice.

## Threat model

Защищаем tenant quotes/content, approval и provenance. Caller — current OWNER/member.
Границы доверия: principal → DB membership, receipt graph → Identity copy/approval,
исторический content → DTO. Attacks: foreign attempt/Identity IDs, stale principal,
withdrawal/source edit/parent archive races, SQL forgery, lineage laundering и mass assignment.
Scoped lookups, actor binding, locks, bounded graph validation и PostgreSQL triggers
защищают эти границы независимо от frontend. Exact quotation не доказывает truth/relevance.

Tests: authorized copy/read/edit/approval, denied access/foreign IDs, withdrawn/stale source,
inherited lineage, immutable audit, runtime SQL constraints, revocation/withdrawal lock races,
populated V34 upgrade, full Docker/Maven/Python/Linux gates и packaged startup.

## Дополнение 2026-10-14 — PDF Identity

Ограничение TEXT-only Identity этого исторического slice заменено [ADR0020](0020-pdf-identity-consumption.md)
и V38. PDF copy/direct+inherited approval теперь проверяют original publication lineage,
physical bytes вне transactions и fresh locked metadata/access. Остальной contract сохраняется.
