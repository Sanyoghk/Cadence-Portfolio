# #121 — Задание05: social textual evidence и visual boundary

Дата: 2026-10-14. Baseline consumers01/04: `449735b6b48ba0f48058f2ac97a59b3d77fabac0`.
Branch `mvp`; перед работой clean, ahead28. #121/#107 прочитаны, scope02/P1–P3 утверждён.
Visual docs slice сохранён отдельно: `be6a03e`.
Статус: **QUALIFIED local acceptance boundary05** — social text implemented/verified;
visual contracts готовы, visual interpretation остаётся **REQUIRES_PROVIDER #120**.
Local social commit: `feat(evidence): qualify social textual review and consumers` (commit, содержащий этот отчёт).
Push/remote CI/issue closure не выполнялись; whole #121 остаётся open.

## Что было сделано

Social text получил самостоятельный путь review → consent → generation → Identity approval.
Исходный normalized provider JSON и trusted origin сохраняются; модель получает только явно
reviewed текстовую проекцию без provider identifiers. Generic TEXT больше не позволяет обойти
social binding policy. Images/screenshots не объявлены распознанным текстом: точные будущие
contracts и внешние blockers сохранены в [ADR0024](adr/0024-visual-evidence-provider-boundary.md).

Это acceptance boundary локального05 по утверждённому P2: social supported implementation плюс
visual contracts/blockers. Это не полная поддержка visual MVP и не завершение whole #121.

## Как это работает

`SocialTextProjection` проверяет normalized shape и immutable origin, строит deterministic
`social-text-v1`. `SocialTextSnapshot` сохраняет exact payload/hash, source/capture/projection
version и local post mapping. `ReviewSocialEvidenceUseCase` проверяет OWNER/membership,
текущие versions/binding и принимает только selectors показанного preview. Admission — отдельный
immutable receipt с actor/time; retrieval success не создаёт consent.

`SocialAnalysisSourcePolicy` повторяется перед model egress/publication и при Identity
copy/direct/inherited approval. Общий sourceVersion-wide withdrawal закрывает будущий допуск,
не стирая APPROVED history. Renewed expiry требует нового review. Legacy receipts не переписаны;
trusted origin, прикреплённый позже, закрывает новое generic использование.

## Архитектурное решение

[ADR0025](adr/0025-social-text-review-consumption.md) описывает contracts/alternatives/trade-offs.
Переиспользованы current orchestration, bounded input, output validation, parent-first locks и
Identity lineage. Отдельный generation pipeline дублировал бы guards; raw provider JSON раскрыл
бы лишние IDs и обошёл typed policy. Новый immutable snapshot даёт воспроизводимый preview и
post-index mapping ценой bounded storage. V41 добавляет SQL defense in depth без переписывания
checksums прошлых Flyway migrations.

Digest `social-evidence-v3` применяется только к batches с social. TEXT/PDF v1 и URL v2 semantics
сохранены. Physical PDF/URL checks остаются обязательными в mixed batch; social DB text не получает
выдуманные scan receipts. Новых dependencies нет. External I/O остаётся вне DB transactions.

## Security Review

Защищаем tenant content, review consent, approved history и provider provenance. Caller может
быть злонамеренным OWNER; provider content/model output также недоверенные. Trust boundaries:
HTTP → actual actor/membership, origin → projection, reviewed data → model port, model output →
publication. Authenticated principal сам по себе не доказывает current workspace membership.

| Threat | Protection | Verification |
| --- | --- | --- |
| BOLA / role / forged actor / mass assignment | scoped source lookup, actual OWNER/member, principal actor, strict request DTO | HTTP foreign/unknown IDs, EDITOR/VIEWER/anonymous, forged actor and unknown fields |
| Social→generic TEXT downgrade | origin-first application + SQL guards, late-origin parent locks | ordinary route denial, manual JSON stays untrusted, legacy/inherited history regression, SQL direct insert |
| Provider/account switch, expired/revoked scopes | local same-account port, >30s margin, exact expiry and repeated checks | actual Official adapter negative tests, model-time binding/expiry changes, copy/inherited approval failures |
| Prompt/command injection, SSRF | captions data-only, no model fetch/tools/private refs, exact selected-source quote/path | hostile caption preserved, invalid/foreign path and quote rejected, no consumer fetch |
| Source/context/access/withdrawal race | current checks, parent locks, append-only withdrawal, atomic publication | model-time mutation tests, PostgreSQL both lock orders withdrawal/approval |
| Forged provenance/history, corrupt snapshot | immutable origin/snapshot, canonical projection/hash/version/time mapping, runtime grants | V40→V41 unchanged receipts, SQL forbidden mutations/forged metadata, runtime42501 |
| Resource exhaustion | 20 posts,5 sources/20000 UTF-8 bytes, history cap1000, existing generation semaphore/deadlines | parser bounds/UTF-8 overflow, SQL and application history cap |
| Data exposure / secrets | IDs remain local, DTO allowlist, safe errors, existing production fail-closed | serialized command lacks IDs/private references, unknown fields rejected, no new credentials/dependencies |

SQL не видит текущую operator configuration/scopes, поэтому application port обязателен; сохранённый
expiry в БД не является live OAuth verification. Config changes не объявлены транзакционными с БД.
Обход одного Java слоя встречает SQL origin/hash/expiry/withdrawal guards, но SQL не заменяет
processor policy либо real account qualification. CSRF/CORS/auth configuration не изменялись.

## Изменённые файлы

Полный список каждого файла и назначения: [manifest05](issue-121-social-consumption-files.md).
API: [social admission](social-evidence-admission-api.md), machine contract `cadence.json`.

## Runtime Flow

```text
OWNER request + exact preview selectors
  → SocialEvidenceReviewController
  → actual actor/membership + Account → Workspace → Brand → Evidence locks
  → immutable origin + same-account local binding + strict projection
  → SocialTextSnapshot → explicit EvidenceAdmission
  → server-selected bounded BrandAnalysisCommand (social-evidence-v3)
  → fresh all-source checks + physical PDF/URL verification
  → BrandAnalysisPort outside DB transaction → exact citation/output validator
  → fresh locked all-source publication → immutable analysis receipt
  → Identity copy → direct/inherited approval, repeating source policy
```

Withdrawal идёт через общий endpoint, записывает immutable sourceVersion-wide revoke и запрещает
новое использование. Historical authorized provenance read не требует реактивации отозванного source.

## Тесты

Все commands ниже фактически выполнены. Final implementation gate запущен из
`var/prompt05-final-verification`, созданной из tracked/new source files без secrets/ignored data.
506 source/build SHA-256 совпали с root до/после run;328 compiled class hashes не изменились.
Manifest SHA-256: `f56df5fb41ecba5296844d5d048dcb118656201accb303ce6eebca8f05aa84e1`.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
```

- **Final BUILD SUCCESS**,2026-10-14 06:36:09 Europe/London,8m03s.
- **130 suites /1283 tests /0 failures /0 errors /3 Windows-only filesystem skips**;
 1280 tests executed successfully. Все required PostgreSQL/MinIO/ClamAV/renderer/full evidence
 pipeline reports прошли без skipped/failed cases; оба Python report guards PASS.
- JaCoCo6839/7208 lines =94.88%; configured coverage gate PASS.
- Предыдущий full run также SUCCESS:1277 tests/0 failures/errors/3 Windows skips. После него
 scoped snapshot lookup и дополнительные mixed/foreign tests вошли в **повторный final full run**.
- Targeted social/projection/Official binding/OpenAPI/migration run:66/66; mixed URL/PDF/social
 suite:89/89. Это отдельные runs, их counts не прибавляются к final1283.

Точные targeted commands:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply "-Dtest=SocialReviewApiIntegrationTest,PostgreSqlSocialReviewTest,PostgreSqlSocialReviewMigrationTest,SocialTextProjectionTest,OfficialSocialEvidenceSourceTest,OpenApiContractTest" test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply "-Dtest=UrlAnalysisApiIntegrationTest,PostgreSqlUrlAnalysisTest" test
```

Независимые gates выполнены из `var/prompt05-verification`. Все58 соответствующих Python,
filesystem и renderer source files совпадают с final verification copy; повторный запуск
не заявляется. Maven final повторно выполнил их применимые Java/container integrations.

```powershell
python -B -m unittest discover -s scripts -p 'test_*.py'
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt05-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt05-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

Python10/10; **Linux35/35,0 skipped**, включая3 skipped на Windows; renderer29/29.
Final packaged startup выполнен `& ..\prompt05-startup.ps1` из final verification directory:

```text
java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18098
```

Health200; anonymous social-review GET, analyze-evidence POST, Identity from-evidence-analysis POST,
provenance GET →401. Process запущен hidden и остановлен после проверки. Это standalone actual bean
startup; live OAuth/AI credentials не использовались. PostgreSQL DI/schema startup также прошёл
в integration contexts с Flyway41 и Hibernate validation.

Exploratory targeted runs1–3 сначала выявляли fixture setup/restubbing/context errors, ошибочную
OpenAPI const и missing unknown-field rejection нового DTO. Они исправлены; existing production
404 для foreign resource и safe503 для invalid model output сохранены. Tests не отключались.

Warnings: existing deprecated API/unchecked test API/JVM class-sharing warnings; Hikari connection
validation/shutdown warnings после остановки disposable PostgreSQL containers. Они не стали
failed tests; required reports/coverage/class hashes проверены. Dependencies не изменены.
Docs-only final checks:14 изменённых Markdown files,227 local links,balanced fences и `git diff --check` PASS.
Logs остаются в ignored `var/prompt05-*.log`, включая `prompt05-full-final.log`.

## Security Tests

Проверяются запрещённые foreign/role/actor/unknown-field requests; expired/changed binding;
withdrawn source; malformed/duplicate/unknown JSON; metadata-only projection; invalid quote/path;
legacy social TEXT fallback; source/capture/context changes; missing origin; direct SQL forgery;
runtime history mutation; concurrent withdrawal versus approval. Live OAuth fixtures не имитируют.

## Edge Cases

Unicode/escaping и decoded exact quotes; исторические capture times; one-page content; отсутствие
TikTok biography; duplicate/reordered posts; UTF-8 versus Java character budget; expiry ровно на
30-second margin; same-context duplicate; renewed binding/new sourceVersion review; sourceVersion-wide
withdrawal нескольких receipts; archived source withdrawal; late origin attachment; inherited Identity.

## Непроверенные аспекты

Vision/OCR отсутствуют — обязательный product gap REQUIRES_PROVIDER #120, не скрытое исключение из MVP.
Не проверены реальные accounts/scopes/refresh #32/#33, качество live AI, processor privacy/region/
retention/deletion и production IAM/TLS/storage. Нужны выбранные provider/credentials и отдельная
qualification. Remote CI текущего source SHA не запускался; push/closure #121 не выполнялись.

## Что мне важно понять

Checksum подтверждает точный content, version фиксирует состояние, origin доказывает путь acquisition,
а human admission даёт scoped consent. Ни одно из них не заменяет остальные. Withdrawal запрещает
будущую публикацию, но не стирает фактическую историю. Поэтому source checks повторяются после model
call: пользователь мог отозвать доступ во время внешнего I/O. Exact citation доказывает присутствие
цитаты в выбранном поле, но не истинность или смысловое качество model conclusion.

## Что мне стоит изучить

1. Неизменяемый снимок (immutable snapshot): фиксирует bytes/text и provenance без переписывания history.
2. Граница доверия (trust boundary): provider-shaped JSON не равен trusted origin, JWT role не равна current membership.
3. Состояние гонки (race condition): guards до I/O недостаточны; final locks/checks защищают publication.
4. Разделение контракта по версиям (versioned contract): v3 digest добавляет social без изменения старых receipts.
5. Эшелонированная защита (defense in depth): DTO/use-case/domain/SQL/runtime grants защищают разные обходы.

## Production considerations

Согласовать visual provider/decoder/privacy #120 и live account onboarding #32/#33; не включать
реальный AI adapter по факту прохождения synthetic tests. Операционные binding reload/revocation,
privacy retention/deletion/backups, multi-instance quotas/costs и external processor terms требуют
отдельной qualification. Existing local/test fake generation не доказывает качество live outputs.

## Факты, выводы и assumptions

- Факты: final full1283/0failures/errors, Linux35/35, Python10/10, renderer29/29, required reports/startup и source/class hashes PASS; scopes и ограничения отражены выше.
- Инженерный вывод: typed projection и repeated current checks закрывают обнаруженный generic
  downgrade без rewriting history и без отдельного параллельного generation pipeline.
- Assumptions/внешние проверки: runtime configuration и selected provider infrastructure должны
  быть отдельно qualified; live OAuth, vision и privacy approval не получены этим slice.
