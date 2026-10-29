# Задание10 — durable generation: отчёт

Дата: 2026-10-15. Branch `mvp`, baseline/pushed SHA
`ff2928180077596c6ebf83ed2830224f27516aaf`. Prerequisite09: ADR0028/0029 и qualified
supported01–06 scope сверены. [Контракт и API](durable-ai-generation.md).
**QUALIFIED LOCAL — slice10 завершён.** Full/local gates подтверждены ниже; security CI blocker
Spring остаётся отдельно согласованным follow-up, production не активирован.

## Что было сделано

Реализованы persisted Brand analysis / explicit Identity copy и Strategy generation jobs.
Submission быстро возвращает202; worker выбирает только server-captured input и публикует
один validated result. Job не означает APPROVED. Existing synchronous API остаётся прежним.
Draft A/11, budgets12 и Playbook13 этим slice не завершены; #121 остаётся open.

## Как это работает / Runtime Flow

Controller → secured use case → actual DB actor/tenant/source capture → operation + Job commit
→ typed claim + durable attempt → physical preflight → dispatch witness commit → model I/O
outside transactions → repeated physical/access/source/lease checks → atomic result/Job commit.

Подробная схема, terminal states и configuration: [durable contract](durable-ai-generation.md).
Result history не является разрешением на повторное AI consumption. Copy/approval повторяют
существующие kind-specific policies, включая PDF/URL retained bytes и social provider binding.

## Архитектурное решение

Используется existing relational jobs queue с отдельными immutable operation/attempt/source
таблицами. Generic ingest recovery не захватывает AI jobs. Broker и новые dependencies не добавлены.

Private snapshot schemaVersion1 отделён от model JSON: annotations model payload скрывают
внутренние structured fields, поэтому сохранение самого model JSON теряло бы URL/social
citation semantics при restart. Existing source digests и historical receipts не переписываются.

External call не входит в DB transaction. Atomic local publication выполняется общей transaction;
нельзя отдельно commit Strategy/signature, а затем Job. Используются existing validated ports,
source policies и IdentitySourceVerification. Publication orchestration имеет отдельный durable
coordinator, чтобы сохранить synchronous contracts без широкого изменения их transaction semantics.

Lease/attempt fencing означает проверку права конкретной попытки завершить job. Истёкшая
попытка не может publish, даже если модель вернула хороший ответ. После dispatch crash исход
может быть неизвестен: OUTCOME_UNKNOWN не превращается автоматически в повторный платный вызов.
Гарантия — один local committed result; exactly-once external execution не заявляется.

Trade-offs: дополнительные SQL guards/records и60s total deadline; очередь может истечь без
модели. History cap технический, не pricing policy. Production disabled до11/12/#120 qualification.

## Security Review

Threat: BOLA, forged actor, stale membership/role.
Protection: authenticated actor captured server-side; fresh DB OWNER/membership checks before
submission/replay/egress/publication; foreign jobs/results404; internal worker не создаёт JWT/principal.
Verification: anonymous/VIEWER/forged actor/foreign HTTP negatives; membership/account revocation
before claim и during model; PostgreSQL current access publication guard.

Threat: prompt injection, foreign citations, private egress.
Protection: server-selected max5/20000 UTF-8 bytes; data-only typed model port без tools/fetch;
exact selected-source quotation validation, no private reference in job DTO. New network fetch нет.
Verification: malicious instructions остаются данными; forged citation rejects publication;
mixed PDF/URL/social positive и bytes/scan/withdrawal/provider-binding negative scenarios.

Threat: duplicate/re-delivery/lost lease и partial publication.
Protection: parent-first locks; refresh после lock wait; unique operation/idempotency/attempt,
immutable dispatch witness; current lease/deadline; atomic result+Job commit и SQL seal.
Verification: concurrent same key, two workers, restart before dispatch, post-dispatch lease loss,
late result, bounded blocking call, redelivery и SQL failure injection rollback.

Threat: SQL bypass / history rewrite / fabricated legacy provenance.
Protection: V43 FK/check/immutable triggers, private snapshot SHA-256, operation/job binding,
terminal queue immutability и restricted runtime column grants. V1–42 не изменены.
Verification: PostgreSQL tamper/rewrite/foreign binding negatives, runtime permission SQLSTATE42501,
populated V42→43 migration without backfill, existing migration inventory updated to43.

Threat: exhaustion / secrets exposure / silent spend assumptions.
Protection: executor2/no backlog, bounded deadline/attempts/storage, safe errors/no-store,
capability fail-closed в prod. No cost/latency/moderation backfill и no new dependency.
Verification: timeout test, disabled prod capability despite fake property, no raw provider error,
existing request-size/security regressions. Real load/rate/budget/provider tests остаются отдельными.

## Тесты

Финальный `clean verify` завершён15 октября23:08:37 Europe/London, BUILD SUCCESS,7:31.
134 suites,1381 tests:1378 successful,0 failures,0 errors,3 Windows filesystem symlink skips.
Все3 соответствующих scenarios выполнены в отдельном Linux suite35/35,0 skips.
PostgreSQL async31/31 и populated migration1/1 прошли. Spotless/JaCoCo gates PASS.

Фактически выполненные verification commands:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt10-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt10-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\prompt10-startup.ps1
python -X utf8 -B var/prompt10-doc-check.py
git diff --check
```

Python validators10/10; renderer Python29/29; mandatory PostgreSQL/MinIO/ClamAV/renderer/full
pipeline report gates PASS. Packaged local startup с `cadence.ai-jobs.worker-enabled=true`:
health200, четыре anonymous protected requests401, no startup ERROR/Exception. Process остановлен
после проверки. Это local startup, не production deployment.

Docs links/fences, plan parity, OpenAPI refs и V1–42 checksums PASS. Manifest525 source/build
files не изменился во время final qualification. Reports/coverage/manifest сохранены локально
в ignored `var/prompt10-verification`; logs `var/prompt10-final-full.log`,
`var/prompt10-linux.log`, `var/prompt10-python.log`, `var/prompt10-renderer-python.log`,
`var/prompt10-startup-check.log`.

Промежуточные runs не объявляются pass. Первый full1379 имел1 failure UTC-column inventory
и1 error audit time fixture; V43 добавила4 audit columns, а округление H2 timestamps требовало
clock fixture не раньше persisted creation. Assertions не ослаблены. Final run включает также
simultaneous redelivery и усиленные SQL guards. Первоначальный Python run в sandbox получил
PermissionError на tempfile; повтор вне sandbox выполнил все10 tests успешно.

Warnings: Mockito/JDK dynamic-agent/deprecation notices, ожидаемые negative-configuration
context failures и Hikari connection/shutdown warnings после остановки disposable PostgreSQL
containers. Final required suites без failures/errors/skips;3 platform skips отдельно покрыты Linux.

## Security Tests и Edge Cases

Описанные выше сценарии выполняются на H2 и реальном PostgreSQL; mixed sources используют
existing review/ingest fixtures и реальную migration schema. Никакие live OAuth credentials не
подставлялись. PostgreSQL deferred guards проверяются actual commit, не только flush.

Transient storage failure до dispatch повторяется с тем же snapshot; после dispatch неизвестный
исход не retry. New Identity draft после submission не подменяет APPROVED source. Legacy TEXT
SUPERSEDED, receipt digests, Identity copy/approval и Strategy edit/history остаются в full regressions.

## Push, CI и remaining scopes

По явному запросу пользователя ДО implementation выполнен `git push origin mvp`:
`b2a6871..ff29281`. Проверен именно source SHA `ff2928180077596c6ebf83ed2830224f27516aaf`:
[run38810265479](https://github.com/Sanyoghk/Cadence/actions/runs/38810265479).
`verify` SUCCESS, `dependency-security` FAILURE. Это CI baseline09, не нового implementation10.

OSV сообщил Spring WebMVC6.2.19 GHSA-j9f9-w8pj-32f8 / CVE-2026-47890 и
GHSA-pc63-qcmh-9cmg / CVE-2026-47884. Official advisories:
[47890](https://spring.io/security/cve-2026-47890/),
[47884](https://spring.io/security/cve-2026-47884/).
Исправление6.2.20 требует Enterprise Support; OSS fix7.0.9 требует совместимой миграции stack.
Пользователь явно выбрал: закончить10 локально, затем отдельная миграция Spring.
Inspection `src/main` не нашёл прямого использования XsltView, SseEmitter или FragmentRendering.
Это не доказательство отсутствия dependency attack surface и не основание suppress finding.
Scanner не отключён, findings не подавлены. Security gate НЕ green; production blocker сохранён.

Новый task10 slice сохранён local commit вместе с этим отчётом; дополнительный push, issue closure, merge/deploy не выполнялись.
Actual standalone issue A не создан: retained exact local draft разрешён в09. #107/#121 остаются open.
Дальше11 operational recovery/observability,12 budget/reservation/settlement и policy activation,
13 full Playbook. Spring migration согласована отдельно, не выбирает AI provider.

## Непроверенные аспекты / Production considerations

Real model API, remote billing/idempotency, retention/privacy policy, live OAuth32/33,
production IAM/TLS/load/pilot/backup qualification не выполнялись. Production generation disabled.
Интеграционные fake providers доказывают backend contract, не качество модели или внешнюю
exactly-once guarantee. UNKNOWN costs не заменены нулём; финансовый ledger —12.

## Что мне важно понять

Сохранённый request не является бессрочным разрешением: права и consent могут измениться,
пока job ждёт или модель отвечает. Поэтому capture фиксирует ЧТО выбрано, а fresh checks
проверяют МОЖНО ЛИ сейчас отправить/опубликовать. DB и external API нельзя commit атомарно;
после crash честный неизвестный исход безопаснее автоматического повторного списания.

## Что стоит изучить

1. Durable state machine: состояния в БД переживают потерю процесса; in-memory очередь — нет.
2. Lease fencing: временное право worker проверяется вместе с номером attempt при commit.
3. Idempotency: одинаковый scoped request/key получает одну operation вместо повторной генерации.
4. Transaction boundary: связанные local writes commit вместе, network I/O выполняется снаружи.
5. Immutable provenance: история хранит фактический source/approval/actor, а не latest mutable value.

## Границы доказательства

Факты — source inspection, full/local gates и результаты команд выше подтверждены.
Инженерные выводы — fences и atomic writes предотвращают stale/duplicate local publication
в рассмотренных scenarios. Assumptions — trusted DB/admin/storage perimeter, future adapter
isolation и коммерческие policies требуют отдельной проверки, не объявлены готовыми.

## Изменённые файлы

- [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) — current checkpoint, Task2.4/2.5 и remaining scopes.
- [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) — синхронный русский checkpoint и зависимости.
- [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) — least-privilege transition grants для новых tables.
- [docs/adr/0028-durable-ai-workflow-contracts.md](../docs/adr/0028-durable-ai-workflow-contracts.md) — implementation bounds и связь с remaining11/12.
- [docs/ai-workflow-issue-drafts.md](../docs/ai-workflow-issue-drafts.md) — delivery10 mapping без ложного closure A.
- [docs/astra-medium-mvp-prompts.md](../docs/astra-medium-mvp-prompts.md) — execution note и согласованный Spring follow-up.
- [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) — ledger10 и baseline push/CI boundary.
- [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) — regression проверки report gate.
- [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) — обязательные async/migration suites без skips.
- [src/main/java/com/cadence/application/repositories/DurableJobRepository.java](../src/main/java/com/cadence/application/repositories/DurableJobRepository.java) — исключение AI_GENERATION из generic ingest claims/recovery.
- [src/main/java/com/cadence/domain/DurableJob.java](../src/main/java/com/cadence/domain/DurableJob.java) — typed operation payload binding и bounded terminal cleanup.
- [src/main/java/com/cadence/presentation/JobController.java](../src/main/java/com/cadence/presentation/JobController.java) — typed AI status с сохранением ingest response.
- [src/main/resources/application.properties](../src/main/resources/application.properties) — worker enabled=false и technical retained cap configuration.
- [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) — request/result/job schemas и additive routes; legacy routes сохранены.
- [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) — migration inventory43.
- [src/test/java/com/cadence/persistence/PostgreSqlUrlAnalysisTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUrlAnalysisTest.java) — явные test+postgres profiles/driver для mixed async tests.
- [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) — audit timestamp inventory с новыми V43 columns.
- [src/test/java/com/cadence/presentation/OpenApiContractAssertions.java](../src/test/java/com/cadence/presentation/OpenApiContractAssertions.java) — строгая проверка ровно одной oneOf response schema.
- [src/test/java/com/cadence/support/UrlAnalysisApiContract.java](../src/test/java/com/cadence/support/UrlAnalysisApiContract.java) — mixed PDF/URL/social async round trip, private read retry и revocation fences.
- [docs/durable-ai-generation.md](../docs/durable-ai-generation.md) — runtime/API/recovery/configuration/security contract.
- [docs/prompt10-durable-generation-report.md](../docs/prompt10-durable-generation-report.md) — итоговый отчёт, команды и границы qualification.
- [src/main/java/com/cadence/application/repositories/AiGenerationAttemptRepository.java](../src/main/java/com/cadence/application/repositories/AiGenerationAttemptRepository.java) — unique attempt lookup.
- [src/main/java/com/cadence/application/repositories/AiGenerationOperationRepository.java](../src/main/java/com/cadence/application/repositories/AiGenerationOperationRepository.java) — scoped replay/status/result, bounded discovery и row locks.
- [src/main/java/com/cadence/application/services/AiGenerationService.java](../src/main/java/com/cadence/application/services/AiGenerationService.java) — submission, claim/recovery, bounded I/O и atomic publication coordinator.
- [src/main/java/com/cadence/application/services/AiGenerationSnapshot.java](../src/main/java/com/cadence/application/services/AiGenerationSnapshot.java) — private versioned persistence schema для command round trip.
- [src/main/java/com/cadence/application/services/AiGenerationSources.java](../src/main/java/com/cadence/application/services/AiGenerationSources.java) — actual actor/source policies и frozen capture comparison.
- [src/main/java/com/cadence/application/usecases/AiGenerationUseCase.java](../src/main/java/com/cadence/application/usecases/AiGenerationUseCase.java) — secured HTTP application boundary.
- [src/main/java/com/cadence/domain/AiGenerationAttempt.java](../src/main/java/com/cadence/domain/AiGenerationAttempt.java) — immutable lease/attempt и durable dispatch witness.
- [src/main/java/com/cadence/domain/AiGenerationOperation.java](../src/main/java/com/cadence/domain/AiGenerationOperation.java) — immutable input/source/result aggregate и lifecycle.
- [src/main/java/com/cadence/infrastructure/analysis/AiGenerationScheduling.java](../src/main/java/com/cadence/infrastructure/analysis/AiGenerationScheduling.java) — opt-in bounded polling через existing service.
- [src/main/java/com/cadence/presentation/AiGenerationController.java](../src/main/java/com/cadence/presentation/AiGenerationController.java) — additive POST202/result API, bounded DTO/unknown-field rejection.
- [src/main/resources/db/migration/V43__durable_ai_generation.sql](../src/main/resources/db/migration/V43__durable_ai_generation.sql) — таблицы, immutable scope/history, source/Job/lease seals.
- [src/test/java/com/cadence/persistence/PostgreSqlAiGenerationMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiGenerationMigrationTest.java) — populated V42 upgrade без synthetic history.
- [src/test/java/com/cadence/persistence/PostgreSqlAiGenerationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiGenerationTest.java) — real PG contract, SQL failure injection и runtime positive/negative checks.
- [src/test/java/com/cadence/security/AiGenerationIntegrationTest.java](../src/test/java/com/cadence/security/AiGenerationIntegrationTest.java) — H2 HTTP/use-case shared contract.
- [src/test/java/com/cadence/support/AiGenerationContract.java](../src/test/java/com/cadence/support/AiGenerationContract.java) — positive/negative API, capture, replay, concurrency, timeout, revoke и recovery scenarios.
