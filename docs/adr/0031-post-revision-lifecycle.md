# ADR0031 — Post identity, revisions и approved Strategy source

Status: принятое техническое решение в утверждённом MVP scope; QUALIFIED LOCAL14, 2026-10-16.
Связи: #115/#103, ADR0029, задания14–18. Это не closure всего scope этих issues.

## Contract перед migration

Post identity принадлежит Brand и имеет неизменяемую platform. Revision имеет собственный UUID,
порядковый revisionNumber и optimistic lockVersion. APPROVED immutable; edit создаёт новый DRAFT
с revisedFromId. Менять можно только latest revision: повтор approved edit после создания branch
даёт409. DRAFT edit заменяет весь content с expectedVersion; approval отдельная операция с тем же
token. Одновременно существует не более одного DRAFT. До1000 revisions/Post, до10000 Posts/Brand:
технические caps для bounded history/resource exhaustion, не тарифные обещания.

Source при create — явно указанный Strategy UUID и expectedStrategyVersion (lockVersion), complete
v2 APPROVED с реальным approval audit в том же Brand и выбранным channel. Не обязательно latest
Strategy: creator явно выбирает конкретную approved revision. Capture сохраняет ID, revision number,
lockVersion, approvedBy/At и SHA256 canonical full Playbook. Новая Strategy revision не подменяет
source. Manual edit/approval проверяют прежний exact source и active parents; underlying evidence
не читается и не передаётся модели. Identity/evidence lineage доступна через immutable Strategy.
Это historical creative source, не разрешение на новый AI egress. Task15 обязан определить собственные
pre-egress/completion guards и provider provenance; task14 не создаёт generation receipts.

Content schema1: title≤255, caption≤2200(INSTAGRAM)/4000(TIKTOK), hashtags0–30, каждый # + Unicode
letters/digits/underscore≤100, unique; mediaPrompt1–2000; optional videoScript1–5000 только VIDEO/REEL.
Instagram: IMAGE/CAROUSEL/REEL/STORY; TikTok: VIDEO/CAROUSEL. TEXT не поддерживается в новом contract.
Это внутренние поддерживаемые editorial bounds, не утверждение актуальных publishing API limits.
Plain text; URLs в тексте не fetchятся. MediaPrompt — инструкция/идея автора, не asset/vision observation.
UNKNOWN/extra fields rejected во всех request objects. Client не назначает actor, status, approval,
source owner, checksum или provider. Human authorship хранится отдельно от future model output.

## Eligibility для16/18

APPROVED latest revision с audit + active Workspace/Brand + current OWNER permission + valid exact
approved source структурно подходит для scheduling/export. Новый DRAFT делает предыдущую revision
не current; история остаётся доступной. Флаг contentApproved — лишь структурный статус, не capability.
Actual scheduling требует отдельной append-only schedule записи с exact revision/checksum и timezone/DST
contract16. Export capture требует currentness/revision checks18; task14 не назначает UTC slot, не
экспортирует CSV, не проверяет media availability. Media requirements17 остаются отдельным gate.
Изменение approved content не изменяет существующий captured export/schedule молча; их revalidation,
cancellation и race policy определяются соответствующими consumers до их implementation.

## Legacy и persistence

Новые normalized content_posts/post_revisions/post_revision_hashtags отделены от legacy SocialPost,
чтобы не переопределить старые metrics FK и значения статусов. V47 копирует каждый имеющийся Post
с тем же identity ID, legacy revision1, original title/caption/format/hashtags/status/scheduledAt/audit
timestamps. Platform, human actor, source approval/checksum неизвестны и остаются NULL. Legacy revision
read-only, LEGACY, не eligible; никакого fabricated approval или автоматического upgrade. Original
social_posts/post_metrics сохранены; новые consumers используют новую identity/revision модель.
После migration legacy writer закрыт runtime grants; старые domain fixtures не публичный API.

Locks: Account → Workspace → Brand → exact Strategy → Post identity → latest revision. Все mutations
проверяют active OWNER/membership под locks; reads проверяют актуальную membership. Parent locks
сериализуют archive/source lifecycle; identity lock — edit/approval/revision allocation. SQL независимо
защищает ownership/source/lineage/format/audit/history, child hashtags и runtime column privileges.
Hashtags mutation требует parent row, записанной в той же transaction (PostgreSQL xmin vs current
transaction ID с учётом epoch); одного row lock недостаточно. DRAFT root update требует fresh
editedAt и lockVersion+1. Это не позволяет изменить child content в обход revision token/audit.
Ни external I/O, ни новые dependencies не требуются.

## Alternatives и threat model

Одна mutable SocialPost проще, но теряет approved history. Snapshot JSON проще таблиц, но переносит
всю integrity на Java. Отдельные identity/revisions дают стабильный ID, FK и независимые SQL guards;
цена — дополнительный join и migration. Отдельные DTO сохраняют API/domain separation.

Защищаем tenant content, approval audit и source lineage. Callers — authenticated OWNER, также hostile
клиенты и внутренние bean callers. Trust boundaries: HTTP, application service, persisted user content.
IDOR/role forgery блокируются scoped resolution+actual account/membership; mass assignment — dedicated
strict DTO; stale/replay/races — expectedVersion+latest check+locks+unique SQL; history tampering —
immutable triggers+restricted grants; exhaustion — count/text/page caps; injection — bound SQL и plain
text без tools/fetch/commands. При обходе HTTP остаются domain/application checks; при обходе Java —
FK/check/trigger/runtime privileges. DB role не заменяет per-request authorization.

Verification14: domain transition matrix/bounds; shared H2/PostgreSQL real HTTP tests; foreign/role/
actor/unknown/stale/replay/source negatives; PostgreSQL populated migration, bypass/runtime/history
guards, concurrent edit/approve/branch/archive. Full Maven/Python/Linux/renderer/startup gates и report.

Actual qualification: [report14](../prompt14-post-revisions-report.md). Full1514, failures/errors0,
3Windows-only skips covered Linux35/35;PostgreSQL Post API29/29, migration1/1, H2Post18/18, domain9/9;
Python10/10,renderer29/29, mandatory reports/coverage/packaged startup PASS. No provider activation.
