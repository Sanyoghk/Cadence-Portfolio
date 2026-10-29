# #121 — Задание06: APPROVED Identity → Strategy

Дата: 2026-10-14. Baseline `e5c4e347ac812465f06819264e8871d770957ae3`, branch `mvp`,
clean/ahead30 до работы. Actual #121/#107 и prerequisites01–05 сверены.
Статус: **QUALIFIED local acceptance boundary06**. Все required local gates прошли.
Commit: `feat(strategy): bind generation to approved Identity revision` (commit этого отчёта).
Push, remote CI, issue closure и другие номера не выполнялись.

## Что было сделано

Новая Strategy создаётся из действительно утверждённой Identity. Текущее legacy
`Brand.signature` больше не является источником новой generation. История сохраняет,
какая revision была использована и кто запросил generation. Отзыв evidence consent,
изменение latest revision или потеря доступа во время model call отменяют publication.

## Как это работает

`StrategyBuildTransactions.snapshot` выбирает последнюю revision Brand и требует APPROVED.
Если последняя revision ещё DRAFT, старая APPROVED не подставляется автоматически.
`ApprovedIdentitySource` фиксирует ID, version, lockVersion, content SHA-256, approval
actor/time, original analysis ID и generation actor. Caller scope копируется в immutable
Snapshot; persisted Brand link и parent versions связывают receipt с Workspace.

`EvidenceIdentitySourcePolicy` разрешает original/inherited graph и повторяет существующие
TEXT/PDF/URL/social guards. Ручная approved Identity поддерживается без фиктивного analysis.
`IdentitySourceVerification` читает private PDF/URL bytes вне transaction и затем снова
проверяет committed metadata. Strategy model получает только bounded approved content,
Brand name/industry: raw evidence, IDs, object references и OAuth account IDs не передаются.

После validated model result completion повторяет physical/current checks и под locks
создаёт Strategy DRAFT с immutable generation receipt. Никакой частичной Strategy при отказе
не сохраняется. SQL V42 независимо проверяет approval/source/checksum/actor/lineage и
существующую evidence policy. Старые migration checksums не меняются.

## Архитектурное решение

[ADR0026](adr/0026-approved-identity-strategy-source.md) описывает selection и acceptance contract.
Latest revision + APPROVED выбран вместо latest APPROVED: новый DRAFT явно обозначает
незаконченный review, поэтому незаметное использование старого решения нежелательно.
Цена — creator должен завершить review перед новой generation. Explicit historical selector
добавил бы отдельный product contract и здесь не нужен.

Переиспользованы validated model port, existing evidence policy, physical verifier и Strategy
history lifecycle. Новый параллельный evidence pipeline дублировал бы security checks.
Source receipt отделён от model payload и хранится рядом с original generation provenance.
Historical edit/approval Strategy не означает новый AI egress; existing revisedFrom сохраняет
связь ручной ветки с исходной generation. Legacy source остаётся NULL, не выдумывается.

Новых dependencies, external fetch, OAuth calls или provider решений нет. Общее ограничение
generation2/process и deadline60s дополняется physical verification30s. Это не distributed
budget, не durable AI job и не hard cancellation некооперативного provider.

## Security Review

Защищаем tenant content, consent, approval audit и историю generation. Недоверенные стороны:
API caller, stored user/provider content и model output. Границы доверия (trust boundaries):
HTTP → actual DB membership, approved revision → model input, external result → publication.

| Threat | Protection | Verification |
| --- | --- | --- |
| BOLA/foreign scope, forged actor, stale JWT | OWNER method security + scoped lookup + actual locked active DB account/membership; captured actor/scope repeated | HTTP/direct-bean foreign/role tests; actual account/role/membership revocation; forged snapshot |
| Подмена source во время generation | Latest revision ID/version/lockVersion/checksum/approval equality; parent versions и deadline | New DRAFT/approval during model, source edit/archive, deadline tests |
| Withdrawal/expired/changed scan или provider binding | Original/inherited evidence policy before egress и completion; sourceVersion-wide withdrawal | TEXT inherited withdrawal, mixed URL/PDF/social mutation tests |
| Недоступные/corrupt private bytes | Bounded exact PDF/URL physical verification вне locks и fresh metadata recheck | Mixed corrupted storage и withdrawal during pre-egress read; existing PDF/URL regression suites |
| SQL history forgery | Nullable legacy/all-or-none new source, FK, immutable trigger, checksum, current latest source, actor/membership, evidence guard | Forged checksum insert, audit updates, populated V41 upgrade; actual restricted runtime role |
| Prompt/command injection и excessive exposure | Model port не имеет fetch/tools; bounded approved values; validated output; audit IDs не model input | Provider called without transaction; exact source content assertions; no URL fetch/social refresh; existing malformed output tests |
| Resource exhaustion | Process slots2, bounded Identity content/lineage1000, source graph5, existing output bounds, deadlines | Existing bounded contracts/concurrent generation and full suite; distributed limits остаются отдельными |
| Secrets/error/log disclosure | Existing safe Problem Details/provider exception translation; no credential/config changes | Provider diagnostic suppression and fail-closed adapter tests |

Authentication остаётся Bearer-only; cookie/session auth не добавлена, CSRF configuration
не менялась. SQL/command strings не строятся из user content; SQL queries parameterized.
Deployment storage IAM/retention нужен даже при успешном physical witness: database locks
не предотвращают rogue storage deletion сразу после проверки. Это существующая явная граница.

## Изменённые файлы

Полный перечень и назначение каждого файла: [manifest](issue-121-approved-strategy-files.md).

## Runtime Flow

```text
POST /brands/{brandId}/strategies — authenticated OWNER
  → BuildStrategyUseCase: capacity2, NOT_SUPPORTED
  → StrategyBuildTransactions.snapshot: Account → Workspace → Brand → Identity → evidence
  → captured source/actor/scope/parents + bounded approved content
  → preflight: metadata capture → private bytes outside TX → fresh metadata under locks
  → StrategyGenerationService.generate outside TX/locks
  → complete: fresh capture → physical verification outside TX → fresh capture
  → Strategy + generation/Identity receipt + collections
  → V42 + existing SQL guards / atomic commit
  → read DTO / 201 DRAFT
```

Source/access change до final locked validation блокирует публикацию. Revocation после
успешного commit сохраняется как новое событие и не переписывает прежнюю историю.

## Тесты

Команды выполнялись в ignored `var/prompt06-verification`, кроме root Spotless/renderer/startup.
Source/build manifest фиксирует509 SHA-256; compiled class manifest —329 classes.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dtest=BuildStrategyUseCaseTest,PostgreSqlStrategyBuildTest,PostgreSqlApprovedStrategyMigrationTest,StrategyApiTest,PostgreSqlStrategyApiTest,StrategyApiFailClosedTest,PostgreSqlIdentityEvidenceTest,PostgreSqlUrlAnalysisTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt06-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt06-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\prompt06-startup.ps1
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check
git diff --check
```

Final full: **BUILD SUCCESS**, 2026-10-14T07:02:04+01:00, 6m44s.
131 suites /1309 tests /0 failures /0 errors /3 Windows filesystem skips;
1306 executed Maven tests passed, Windows skips закрыты Linux35/35 без skips.
JaCoCo6916/7288 lines =94.90%; compilation/Spotless/JAR прошли.
Mandatory PostgreSQL/MinIO/ClamAV/renderer/full-provider reports PASS без required skips.
Packaged JAR health200; anonymous create/read/approval/PATCH Strategy401; собственный
hidden process остановлен. 509 source/build SHA-256 совпали root/copy,329 compiled hashes
не менялись в ходе полного run. Docs checks:7 Markdown files/140 relative links,
fences и diff PASS; V1–V41 unchanged.

Существенные suites: PostgreSQL Strategy build22/22, HTTP27/27, new V41→V42 migration1/1,
legacy Strategy migration1/1; H2 Strategy19/19; TEXT Identity H2 19/19 и PG26/26;
PG PDF68/68, URL52/52; full pipeline с restricted runtime10/10; disabled adapter1/1.
Warnings: существующие deprecated/unchecked APIs, три platform skips, Hikari cleanup
закрытых test containers (connection validation/shutdown warnings); tests не завершились
ошибками. SQL exceptions в negative tests ожидаемы и не являются test failures.
Compilation/Spotless прошли. Focused132 tests:115 pass/17 OpenAPI failures,0errors/skips.
Причина — неверное описание nullable `analysisId`: исправлено на project OpenAPI `anyOf`,
assertions не ослаблены. `StrategyApiTest` не существует и ничего отдельно не запустил;
actual H2 Strategy suite включён в full run. Остальные selected suites выполнились.
Python10/10, renderer29/29, Linux35/35 без skips прошли. Первый sandbox Python run
получил Temp permissions errors; повтор с разрешённым host execution прошёл.

## Security Tests

Проверяются anonymous/EDITOR/VIEWER/foreign access, stale account/membership, forged source
и actor, SQL source/audit mutation, missing/DRAFT/LOCKED latest Identity, concurrent new revision,
withdrawal до/во время model, bytes/scan/expiry/provider binding change. Отдельная PostgreSQL
race удерживает Account revocation transaction и доказывает, что completion ждёт её до
Workspace lock и после commit отказывает. Existing Strategy lifecycle и evidence suites
входят в full verification.

## Edge Cases

Ручной source без analysis разрешён только с настоящим approval. Inherited evidence проверяется
по original receipt, даже после ручного изменения Identity. Старая signature может отличаться
от approved content: модель использует Identity. Новый DRAFT блокирует fallback. Legacy generated
Strategy после V42 имеет source=NULL и сохраняет прежние content/provenance/timestamps.
Concurrent generation получает разные Strategy versions; late output и malformed/null provider
result не создают Strategy. Historical read не становится текущим разрешением на использование.

## Непроверенные аспекты

Live AI/Clerk/OAuth #32/#33, provider privacy/retention #120, real deployment IAM/TLS/AppArmor,
distributed capacity/cost/timeouts, свежие dependency/SAST/container vulnerability scans и
remote exact-SHA CI не проверялись этим local run. Production model остаётся fail-closed;
visual inputs остаются REQUIRES_PROVIDER. Для production нужны отдельные approvals и live
qualification; local fake не доказывает provider quality, безопасность его tools или privacy.

## Что мне важно понять

Immutable content не означает вечный допуск. Identity может не меняться, но consent, scan или
membership уже отозваны. Поэтому source фиксируется один раз, а eligibility проверяется снова.
External model call освобождает DB locks; после него обязательно fresh validation, иначе output
мог бы опубликоваться на основании уже недействительного разрешения.

## Что мне стоит изучить

1. Захват состояния (snapshot capture): immutable вход generation, который нельзя молча заменить.
2. Гонка проверки и использования (TOCTOU): между preflight и model completion могут измениться права.
3. Порядок блокировок (lock ordering): Account → parents → revision уменьшает риск deadlock и сериализует revoke.
4. Происхождение данных (provenance): receipt описывает фактический source, а не придумывает approval для legacy.
5. Эшелонированная защита (defense in depth): Java policies, SQL guards и runtime grants защищают разные границы.

## Production considerations

V42 additive: существующая история сохраняется. Старый binary после migration больше не сможет
создавать generation без Identity source; нужен согласованный rollout, а не долгий mixed-version
deployment. Downgrade schema с удалением provenance не допускается; recovery — reviewed forward fix.
Physical storage trust, expiry/retention, real model cancellation/budgets и monitoring квалифицируются
отдельно. User/client обязан показать missing approval/source conflict и не подделывать success.

Подтверждённые факты: полный local suite, PostgreSQL/runtime/migration, independent gates,
startup, source/class hashes и docs checks прошли. Инженерный вывод: source binding
и repeated eligibility закрывают silent replacement/stale publication в принятом contract.
Assumptions: trusted production storage/admin boundary и future provider isolation требуют
отдельной проверки; local tests не дают оснований объявлять весь продукт production-ready.
