# ADR-0014: Повторный review TEXT в новом контексте

Дата: 2026-10-12. Статус: принято для ограниченного re-review slice #121.

## Контекст и границы решения

Обновлённые DEVELOPMENT_PLAN требуют начать с versioned re-review и не утверждают новое
разрешение после withdrawal. Source code 3e86c02 сохраняет UNIQUE(evidence_id,source_version):
изменение Brand/Workspace version делает snapshot stale, но повторное подтверждение невозможно.
Реализуем новый review неотозванной sourceVersion в актуальном parent context. Отмена withdrawal
и повторное разрешение отозванной sourceVersion по-прежнему запрещены. Такой запрет не требует
предполагать новое consent/data policy пользователя. Source editing API этим этапом не создаётся.

## Lifecycle и replay

Review context определяется tuple evidenceId/sourceVersion/brandVersion/workspaceVersion.
POST с exact preview tokens/contentChecksum создаёт новый immutable receipt, если этот tuple
ещё не подтверждён и sourceVersion не отозвана. Те же tokens —409, даже если actor другой.
Source/parent changes требуют нового GET preview и явного POST подтверждения. Старые receipts
не переписываются и не становятся вновь актуальными: версии сравниваются с текущими.

Withdrawal любого receipt sourceVersion запрещает snapshots и новые admissions этой же
sourceVersion во всех parent contexts. Это исключает обход отзыва через rename Brand или
повторный review из другого owner account. Отзыв stale receipt остаётся разрешённым actual OWNER.
Withdrawal не переносится автоматически на новую sourceVersion: её отдельный explicit review
требует нового current content/checksum/context; изменения source не публикуются этим API.
Допуск после withdrawal той же sourceVersion остаётся отдельным неутверждённым product решением.

## Архитектура и data integrity

V33 заменяет UNIQUE на четыре version/context поля и обновляет admission insert guard, добавляя
проверку withdrawal по evidence/sourceVersion. V1–V32 неизменны; старые receipts/withdrawals
сохраняют UUID, timestamps, content и provenance. Новый unique index также поддерживает query
по evidence/sourceVersion. JPA contract совпадает с PostgreSQL. API DTO/routes не меняются.
Новые policyVersion/provenance для исторических rows не выдумываются: text-review-v1 остаётся
политикой проверки текста, а re-review lifecycle описан отдельно этим ADR.

Application использует REQUIRES_NEW/READ_COMMITTED и Account→Workspace→Brand→Evidence locks.
Admission и withdrawal insert triggers придерживаются того же порядка. Проверка current tuple,
withdrawal и сохранение review выполняются под locks; конкурентный withdrawal либо блокирует
review после commit, либо отзывает уже созданный review той же sourceVersion. Допустимый
snapshot — только в момент чтения; future generation должна recheck перед publication.
Runtime INSERT/SELECT review/withdrawal и запрет UPDATE/DELETE сохраняются, новые grants не нужны.
Количество immutable reviews ограничено1000 на evidence: application count check и SQL guard
под Evidence lock защищают от бесконечного накопления копий при повторных parent updates.
Существующие history rows при upgrade не удаляются; достигнутый предел блокирует новые inserts.

## Alternatives и trade-offs

- Автоматический перенос старого review в новый context отвергнут: не было human confirmation.
- Mutable receipt/version reset отвергнут: уничтожает audit и допускает replay.
- Разрешение re-review withdrawn sourceVersion не выбрано: план оставляет consent решение открытым.
- Четыре поля unique tuple позволяют re-review без искусственного изменения evidence bytes,
  но добавляют immutable copies текста при каждом изменённом контексте. Retention/quotas и
  production abuse controls требуют общей qualification; arbitrary повтор того же tuple запрещён.

## Threat model и verification

Защищаем tenant text, human review provenance и отзыв разрешения. Caller — actual active OWNER
с current membership. Trust boundaries: HTTP tokens и сохранённый текст → application;
application → PostgreSQL; future snapshot → model. Model integration здесь отсутствует.

Threat: replay/stale context/маскировка review после parent change.
Protection: exact current versions/checksum и UNIQUE context; previous receipts immutable.
Verification: HTTP на H2/PG, old/new snapshot currentness, populated V32 upgrade.

Threat: обход withdrawal через новый context либо другого actor.
Protection: withdrawal existence по evidence/sourceVersion, application и SQL insert guard;
все snapshots этой sourceVersion проверяют тот же запрет.
Verification: stale receipt withdrawal после нового review, parent rename после withdrawal,
SQL forged admission, concurrent review/withdrawal и runtime-role insert.

Threat: IDOR, revoked access, SQL mutation audit и tenant reparenting.
Protection: существующие scoped lookups, actual OWNER/membership, immutable history/parent guards.
Verification: существующий полный security/PG suite повторяется без ослабления assertions.

Provider/privacy/data transmission, batch input/citations, non-TEXT eligibility, approved Identity
selection и durable AI Jobs остаются отдельными задачами. Документ не разрешает live generation.
