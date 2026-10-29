# ADR0020 — PDF receipt → Identity copy и approval

Дата: 2026-10-14. Scope: задание 01, часть #121.

Copy и direct/inherited approval используют exact immutable analysis/admission links.
Receipt актуален только при Brand.lockVersion = receipt.brandVersion + 1 и неизменном
Workspace/source context. PDF admission проверяется относительно receipt.brandVersion;
public review/snapshot по-прежнему требуют текущую Brand version без поправок.

Consumer выполняет capture под Account→Workspace→Brand→Identity→Evidence→Quarantine
locks, bounded physical read/hash вне DB transaction, затем fresh locked validation
в новой READ_COMMITTED transaction и mutation. Сравниваются exact immutable PDF selectors,
ledger identity/version/reference/content; batch deadline 30 секунд, concurrency limit 2.
TEXT sources и actual active OWNER/membership проверяются в обеих transaction phases.
Origin inherited branch определяется существующим bounded same-Brand lineage lookup.
Никакой новый analysis или admission для существующей Identity автоматически не создаётся.

Historical provenance read остаётся доступным actual OWNER/member после withdrawal/expiry:
он показывает immutable origin, quotes и PDF artifact/original/latest scan IDs, а не обещание
текущей eligibility. Copy/approval проверяют bytes; historical read не выдаёт raw PDF/text/key.
APPROVED edit создаёт DRAFT с inherited origin; legacy/manual/declared contracts сохраняются.

Threat model: защищаем tenant content, provenance и approval. Caller — OWNER; trust boundaries:
HTTP/principal→actual membership, DB receipt→storage bytes, physical witness→commit.
BOLA предотвращают scoped queries, actor binding и fresh membership. Withdrawal/source/scan/
expiry/ledger races — повторные metadata guards под locks и snapshot equality. Corrupt/missing
bytes — bounded hash/header/size verification. SQL bypass — V38 metadata/lineage guards.
SQL не видит external bytes; filesystem deletion после physical witness остаётся operational
risk, требующий storage IAM/retention qualification. Нет распределённой atomicity DB/storage.

Alternative: держать DB locks во время I/O отвергнута из-за lock exhaustion; ослабить version
check отвергнуто из-за stale copy. Повторное generation меняло бы original attribution.
Новых dependencies нет. Live providers/Strategy и задания 02+ не входят в scope.
