# ADR0026 — APPROVED Identity как source новой Strategy

Статус: принято для implementation06 в утверждённом scope #121; 2026-10-14.
Prerequisites: ADR0021–0025, qualified local01–05, HEAD e5c4e34.

## Решение и варианты

Сервер выбирает последнюю revision по монотонному `version` внутри Brand. Она должна
быть именно APPROVED с persisted approval actor/time. Missing, DRAFT и LOCKED дают
safe conflict до модели. Новый DRAFT означает незавершённый review: fallback к более
старой APPROVED запрещён. Это консервативное правило исключает неоднозначность при
concurrent edit/approval; отдельный выбор исторической revision не входит в API06.
Альтернатива latest APPROVED игнорировала бы новый DRAFT; explicit client source selector
добавил бы контракт исторического выбора. Legacy signature не доказывает approval.

Ручные Identity и declared-analysis Identity поддерживаются после настоящего approval.
Им не приписываются evidence/citations. Для evidence-derived revision, включая inherited
lineage, повторно применяется существующая policy TEXT/PDF_TEXT/URL_TEXT/SOCIAL_TEXT:
original source/context/checksum, latest scan, expiry, withdrawal, provider binding и
physical PDF/URL bytes/extraction. Visual inputs остаются REQUIRES_PROVIDER (#120).
Новая Strategy не посылает raw evidence/private references модели: только bounded
approved Identity content и Brand name/industry через существующий validated port.

## Capture, повторные проверки и история

Capture под Account → Workspace → Brand → Identity → evidence locks фиксирует actual
DB active OWNER/membership, exact caller scope, parent versions, Identity ID/revision/
lockVersion/content SHA-256, approval actor/time, original analysis ID (nullable),
generation actor/start/deadline. Historical analysis/citation graph immutable и остаётся
доступным через Identity source link; receipt digests не меняются.

До model egress и перед publication: fresh authorization/current latest revision и
evidence metadata capture → private bytes verification вне transactions → fresh capture.
Модель вызывается вне DB locks. Completion повторяет проверки и atomically inserts DRAFT
с immutable source receipt. Deadline60s и process capacity2; physical window30s. Любое
появление новой revision, изменение source/status/parents/access/scan/expiry/withdrawal
отменяет publication; captured source никогда не заменяется. Revocation, завершившийся
после final locked validation, относится к последующей операции и не переписывает history.

Strategy edit/approval/history сохраняют существующие правила: source — историческое
происхождение generation, не бессрочное разрешение повторно передавать evidence модели.
Approved Strategy edit создаёт новый DRAFT с revisedFrom; original generation source
остаётся на исходной Strategy. Legacy rows получают NULL, без approval backfill.

## Threat model и ограничения

Защищаем tenant data, consent и историю решений. Caller — недоверенный authenticated
клиент; DB user content и model output тоже недоверенные. BOLA/mass assignment закрыты
server selection, scoped OWNER authorization и отсутствием writable source/actor fields.
Stale JWT блокируется actual DB actor/membership. TOCTOU закрывается fresh checks и
parent locks, SQL guards независимо проверяют actor/source/checksum/lineage/currentness.
Physical witness нельзя сделать атомарным с rogue storage mutation: storage IAM и
retention остаются deployment boundary. SQL не доказывает physical bytes или live OAuth.
Нет новых dependencies, fetch/tools, OCR/vision или privacy decisions. #120 остаётся
production fail-closed; distributed budgets и durable jobs — отдельные scopes.

## Qualification

Требуются positive/manual/evidence source, missing/DRAFT/foreign/stale/withdrawn/expired,
new revision/approval/archive/access races; PostgreSQL/runtime/migration, existing
Strategy lifecycle и TEXT/PDF regressions; full gates/startup. Qualification выполнена: [отчёт06](../issue-121-approved-strategy-slice.md).
Local acceptance boundary06 qualified; remote exact-SHA CI и production gates отдельно.
