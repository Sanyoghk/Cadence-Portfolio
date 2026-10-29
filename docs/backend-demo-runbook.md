# Backend demo: запуск, диагностика и безопасное recovery

Для provider-independent backend28. Это не production hosting/backup/IAM qualification.
Contract и threats: [backend-security-gate](backend-security-gate.md),
операционные результаты/tabletop: [report28](prompt28-backend-report.md).

## Запуск и probes

Требуются Java21, Maven wrapper, Docker Desktop Linux engine и свободное место для image
layers/scanner database (Trivy database может занимать более1.5GiB). Начать с actual HEAD/status;
не перезаписывать пользовательские изменения. Основные команды из repository root:

```powershell
java -version
docker info
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply clean verify
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=8080
```

Default local H2 — disposable development, не retained audit demo; restart теряет business data.
Retained demo использует PostgreSQL/Flyway и separate migration/runtime identities согласно
[database migrations](database-migrations.md), reviewed storage/scanner/renderer profiles и
[storage/scanner setup](storage-scanner-design.md). Подробные current prerequisites также
в README и [verification](verification.md).
Real Clerk setup — [frontend foundation](frontend-foundation.md); JWT bypass не добавлять.
Fake adapters доступны только local/test; production disabled до ADR0027/#120 qualification.

Если работающий JAR держит Windows file lock, `clean` обязан завершиться failure. Не удалять
его силой и не терять H2 session ради build. Остановить только свой согласованный dev process
либо квалифицировать точную isolated source snapshot, сверив source/build SHA256 и явно указав
пути/results. Старый работающий JAR не считается новой реализацией.

Probe GET `/api/v1/health` — liveness, GET `/api/v1/health/ready` — core DB readiness. Прокси
должен сохранять точные paths/methods и не публиковать actuator/JMX/debug. Liveness не опрашивает
provider; не перезапускать здоровый процесс только из-за недоступности внешнего AI. Readiness503
должен убрать instance из serving pool, а не создавать бесконечный restart loop. Optional source/
storage/provider unavailable остаётся явной feature error, readiness не обещает всех workflows.

## Диагностика

| Сигнал | Проверка / действие |
| --- | --- |
| 401 | Clerk issuer/audience/exp/azp и backend account; не вставлять JWT в чат/logs/URL |
| 403/404 | Actual active OWNER/membership/resource scope; не расширять scope по client IDs |
| 409 | Обновить показанный exact snapshot/version; consent/approval не повторять вслепую |
| 429 request_rate_exceeded | Retry-After60, scope — account across routes/sessions/tenants; не создавать другой key для обхода |
| 503 admission | Retry-After1, проверить process counters/threads; bounded retry/backoff, не бесконечный retry |
| 503 readiness | DB/pool/migration/runtime grants; вывод probe не раскрывает connection URL/credentials |
| Job unknown/expired/revoked | [AI recovery runbook](ai-jobs-recovery-runbook.md); dispatch witness, exact attempt, lease и budget hold |
| Source scan/storage unavailable | Отказ функции допустим; не менять CLEAN/FETCHED вручную и не fabricate reviewed content |
| Audit approaching capacity | Privileged operator смотрит sequence last_value, заранее останавливает новые admissions; separate reviewed migration/retention decision |

`X-Request-ID` из ответа сопоставляется с safe rejection log/MDC. Client header не trusted.
Отказы sampled до100 records/minute; HTTP/JMX counters считают все. Job log использует
Job/operation/attempt, outcome/reason enum. Для durable outcome смотреть scoped Job API или
privileged SQL из recovery runbook; counters после restart не финансовый ledger.

Для локального operator inspection включить `--spring.jmx.enabled=true` и читать platform
MBean server с разрешением владельца JVM: `com.cadence:type=ApiRequestMetrics` (Successes,
Redirects,ClientErrors,ServerErrors,RateLimited,CapacityRejected) и existing AiGenerationMetrics.
По умолчанию JMX export выключен. Не добавлять unauthenticated remote JMX/RMI flags или public
Actuator endpoint; hosting monitoring/access policy квалифицируется отдельно.

Audit читает только выделенный operator через отдельную credential policy. Пример bounded SQL
для авторизованного оператора (значения — bind parameters, никогда SQL string concatenation):

```sql
SELECT id,occurred_at,transaction_id,action,resource_kind,resource_id,
       workspace_id,actor_id,actor_origin,database_principal,result
FROM security_audit_events
WHERE workspace_id=:workspace_id AND occurred_at>=:from_utc AND occurred_at<:until_utc
ORDER BY occurred_at,id LIMIT 100;
SELECT last_value FROM security_audit_events_id_seq;
```

Нет business audit endpoint и runtime privileges на чтение/редактирование ledger. Не выдавать
application DB credential оператору как замену персональной audit identity. При role/membership
SQL changes actor_origin=DATABASE_PRINCIPAL: human actor неизвестен, его нельзя backfill.

## Fresh security reports

Pinned tools/checksums и команды CI находятся в `.github/workflows/source-security.yml`;
actual local commands/image IDs/results — в report28. Сохранять полный report, включая все
severities. Dependency inventory должен совпадать с Maven runtime/test graph; OSV output нужен
с `--all-packages`. SAST parse/download failure, пустой inventory или missing artifact — failure.

Для MinIO применять `python scripts/verify_security_reports.py minio <trivy-report.json>`:
он сохраняет raw UNKNOWN и companion compiler-packages JSON, проверяя применимость только
GO-2026-5932 к точному local image ID. Не заменять это ignore/severity filter или ручным
редактированием report. Отсутствующие/чужие inventories и любой другой UNKNOWN блокируют gate.
При новом HIGH/CRITICAL сначала установить affected component/достижимость и исправление;
не менять scanner thresholds ради зелёного результата. После OS/module fixes повторить реальные
provider/runtime tests и записать новый image digest. Rebuild может получить новые distro
packages даже при том же source SHA; прошлый scan не квалифицирует новые bytes.

## Release rollback, credentials, lifecycle

До внешнего release: recorded immutable source/image SHA, matching CI/gates, schema compatibility
и отдельное разрешение deployment. При regression остановить новый traffic/worker admission,
сохранить Job/attempt/reservation/audit records, откатить только приложение на совместимый JAR.
Нельзя слепо запускать старый JAR на новой schema или менять checksums Flyway. При несовместимости
нужна forward repair migration либо отдельно rehearsed restore с остановленными writers.

При возможной утечке credential: изолировать affected access; ротировать credential на стороне
Clerk/provider/DB/storage; обновить secret injection и проверить old credential denial. Не сохранять
секрет в Git/report. Existing auth не заменяется demo tokens. Конкретные hosting secret manager,
IAM/TLS/network, revoke propagation и rotation rehearsal — deployment gate #116/#87.

Withdrawal/archive закрывают дальнейшую eligibility/download/publication согласно kind contract,
но не означают физического удаления retained history или remote provider cancellation. Reports
после24h result unavailable; bytes/audit остаются по ADR0039. Global deletion/privacy policy пока
отдельная; не делать SQL DELETE/sequence reset/force approval в качестве cleanup.

Backup/PITR/restore, production load/pilot и provider outages с real credentials не rehearsed
этими local tests. Перед коммерческим deployment нужны отдельные recorded drills, qualified
monitoring/alerts, disk/audit capacity planning и access-managed backup storage.
