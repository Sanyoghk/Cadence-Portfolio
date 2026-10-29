# Задание04 — admitted URL generation и Identity

Baseline: `cbf585b8a57edeb41169ffc64594130fcf5ee7b5`, `mvp`, clean tree на старте.
Prerequisite03 qualified, ADR0021 P1–P3 accepted; GitHub #107/#121 прочитаны read-only.
Статус: **QUALIFIED**, 2026-10-14. Local commit: `feat(evidence): qualify URL generation and Identity provenance`.
Точный SHA: `git log -1 -- docs/issue-121-url-consumption-slice.md`. Push/remote CI не выполнялись.

## Что было сделано

URL human admission подключён к существующему bounded generation и Identity lifecycle.
Смешанный TEXT/PDF/URL набор выбирает backend. Exact citations сохраняют поле URL projection,
source receipt и snapshot ID. Review/bytes/scan не заменяют друг друга.

## Как это работает

`EvidenceAdmissionRepository.findCurrentReviewed` выбирает latest receipt на evidence/context.
`BrandAnalysisTransactions` проверяет каждый kind и сохраняет immutable selected source graph.
`AnalyzeEvidenceUseCase` повторно читает PDF/URL private bytes до egress и перед publication.
`UrlAnalysisSourcePolicy` связывает receipt с acquisition/Job/scan/ledger; `UrlReviewContent`
и local `WebsiteMetadataExtractor` доказывают exact fetched bytes→reviewed projection.
`EvidenceIdentitySourcePolicy` повторяет kind-specific checks при copy/approval и находит
исходный analysis через bounded inherited lineage. `IdentitySourceVerification` держит I/O
вне DB transaction и требует fresh equality перед commit. Historical read не меняет историю.

## Архитектурное решение

[ADR0023](adr/0023-url-analysis-identity.md) содержит contracts, alternatives и trade-offs.
Переиспользованы ports/orchestration, новый общий physical witness расширяет PDF путь URL
проверкой. URL quote — точная decoded substring в title/description/text, а не JSON substring.
V40 добавляет nullable field_path и SQL validation; legacy rows не переписываются. URL batch
получает mixed-evidence-v2 digest prefix; TEXT/PDF сохраняют text-evidence-v1 и status semantics.
Internal decoded fields исключены из JSON serialization, payload не дублируется для модели.
Новых libraries/provider adapters нет. Адмиссия поддерживает consumer (`aiConsumptionEnabled=true`),
но preview остаётся `admittedToAi=false`: показ текста не создаёт разрешения.

## Security Review

- Threat: BOLA/foreign source или устаревший principal. Protection: server-selected scoped query,
  actual OWNER/account/membership и fresh parent locks. Verification: existing full security suite,
  URL review/API contract и membership revocation during model.
- Threat: malicious webpage instructions/SSRF. Protection: untrusted data-only projection,
  no tools/commands/fetch в model port, exact selected citations; original pinned fetch неизменён.
  Verification: hostile text fixture, no consumer fetcher/renderer interactions, foreign/pathless/
  wrong-field/format quotes rejected; signature не публикуется.
- Threat: stale/corrupt source и consent race. Protection: exact physical hash/size/extraction,
  latest scan/expiry/source/parents/withdrawal повторяются до egress/final transaction.
  Verification: corrupt/missing bytes до/после модели, scan/access/context/source/expiry changes,
  withdrawal during physical read/model/copy, PostgreSQL blocking race в обеих очередностях.
- Threat: forged provenance/history. Protection: immutable acquisition/admission/source/citation,
  SQL field checks/digest/budget и runtime least privilege. Verification: raw SQL forged paths,
  forbidden UPDATE receipts, V39→40 populated migration, inherited withdrawal denial.
- Threat: resource exhaustion. Protection:1–5 sources/20000 UTF-8 bytes including projection JSON,
  max10 quotes,2 concurrent analyses/physical operations,30s verification/60s attempt.
  Verification: actual combined-budget overflow, existing capacity/deadline/full tests.
- Threat: extra data/secrets. Protection: no private keys/raw bytes/lease tokens in command or API,
  safe errors; no new secrets/logging/dependencies. Verification: serialized command and response
  assertions, inspection. User text can literally contain URLs; они не исполняются backend.
- CSRF: existing stateless explicit Bearer auth, no automatic session-cookie credential. Security
  configuration не менялась; CORS/production activation policies остаются прежними.

## Изменённые файлы

Каждый изменённый файл и назначение перечислены в [manifest](issue-121-url-consumption-files.md).

## Runtime Flow

```text
Authenticated OWNER request
 → scoped controller/use case
 → Account/Workspace/Brand locks + server-selected admission graph
 → kind-specific source/scan/withdrawal/ledger checks
 → commit capture → bounded private storage/extraction outside transaction
 → fresh preflight → validated BrandAnalysisPort (no tools/fetch)
 → fresh physical/source checks → atomic citations/signature/SUCCEEDED
 → explicit Identity copy → physical/fresh source verification → DRAFT
 → human approval (including inherited origin) → physical/fresh checks → APPROVED
```

Failure оставляет FAILED/SUPERSEDED attempt и исходную signature; selected source audit receipts
уже существуют, но partial signature/citations publication не допускается. Existing TEXT withdrawal
во время model call сохраняет SUPERSEDED; PDF/URL failure до physical recheck сохраняет FAILED.

## Тесты

Final full gate: **BUILD SUCCESS**, 2026-10-14 05:54:09 Europe/London,6m52s.
126 suites /1226 tests /0failures /0errors /3Windows filesystem skips;1223 фактически
исполненных Maven tests passed. Linux filesystem35/35 без skips/failures покрывает все3 skips.
PostgreSQL и evidence mandatory report gates PASS без required integration skips.
JaCoCo LINE6480/6835 =94.81%; formatting/compile/package/coverage checks PASS.
489 source/build hashes совпали с workspace и verification copy;316 compiled hashes стабильны.

Targeted96/96, failures/errors/skips0:
```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=UrlAnalysisApiIntegrationTest,PostgreSqlUrlAnalysisTest,PostgreSqlUrlAnalysisMigrationTest,UrlAnalysisContractTest,EvidenceAnalysisContractTest,IdentitySourceVerificationTest,EvidenceAnalysisFakeTest,OpenApiContractTest'
```
Этот targeted run предшествует последним двум race regressions и compatibility fix;
их финальное доказательство — full run ниже. Qualification copy `var/prompt04-verification`:
489 source/build hashes matched workspace; исключает известное IDE вмешательство в root target.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
```

Фактически выполнено: Python10/10, renderer29/29:
```powershell
python -B -m unittest discover -s scripts -p 'test_*.py'
docker build --tag cadence-prompt04-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt04-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

Промежуточные ошибки не скрыты: initial testCompile потребовал новый mock dependency;
sandbox запрещал Docker pipe/loopback, поэтому тот run не qualifies; elevated rerun дал85tests
с2failures/2errors test fixtures (Mockito restub и safe exception contract). Следующий compile
нашёл отсутствующий test import. Run95 имел4failures: insufficient overflow fixture и ошибочный
preview consent flag; оба исправлены. Run96 затем passed. Первый full прерван после review
обнаружил TEXT status regression; full повторён после исправления implementation, не теста.
Этот full1226 завершился с10failures/0errors/3Windows skips: nullable citation fields были
ошибочно описаны OpenAPI3.0 `nullable` в OpenAPI3.1 document. Все failures — strict contract
assertions старых TEXT/PDF responses. Исправлена schema на anyOf(string/null), assertions
не изменены. Финальный full1226/0failures/0errors прошёл на исправленном contract.

Packaged startup (из verification copy):
```powershell
& 'C:\Users\<user>\eclipse-workspace\cadence\var\prompt04-startup.ps1'
```
Фактический запуск `java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
--server.address=127.0.0.1 --server.port=18097`: health200; anonymous URL review GET,
analyze-evidence POST, from-evidence-analysis POST и evidence-provenance GET —401.
Script останавливает только созданный им процесс. PostgreSQL Spring/Flyway/JPA startup
дополнительно прошёл в full integration suite. Local H2 startup не доказывает production deployment.

Docs checks:42-file manifest,182 local links, `git diff --check`, V1–V39 immutable —PASS.
Ожидаемые warnings: existing S3 deprecated API/unchecked test code, commons-logging discovery,
Hikari закрытых disposable PostgreSQL pools и negative sandbox test diagnostics; failures0.
Локальные ignored logs: `var/prompt04-focused5.log`, `var/prompt04-full3.log`,
`var/prompt04-linux.log`, `var/prompt04-renderer.log`; предыдущие failed runs сохранены отдельно.

## Security Tests / Edge Cases

Exact quote с Unicode/quotes проверяется по decoded field, null path остаётся legacy.
Same JSON manual TEXT не повышается до URL. Original V39 receipts/policy не меняются.
Mixed TEXT/PDF/URL проходят generation/copy/direct+inherited approval. Corrupt/missing bytes,
latest ERROR/new CLEAN, exact expiry, access revoke, changed source/context и withdrawal
отвергаются; history сохраняется. SQL trigger не позволяет pathless/wrong/unknown field citations.
Runtime role не может переписать history. Concurrent withdrawal-first запрещает approval;
approval-first сохраняет APPROVED history, subsequent inherited approval после withdrawal запрещён.

## Непроверенные аспекты

Live AI/model quality/prompt-injection resistance, actual external processor/privacy/retention,
production IAM/TLS/immutable storage, live Clerk/social OAuth, backup/restore/load не qualified.
Нет atomic DB/storage transaction: rogue deletion после physical witness остаётся operational
risk и требует storage policy. Обязательные local provider tests не заменяют deployment gates.
Свежий OSV/SAST scan не выполнялся; новые dependencies не добавлены. #120, AI Jobs/budgets,
social/visual05 и approved Identity→Strategy остаются отдельными scopes;05 здесь не выполнялся.

## Что мне важно понять

CLEAN отвечает за malware verdict, admission — за явный human consent, version/hash — за точный
источник, access — за право текущего пользователя. Ни один из них не заменяет другие.
Проверка до модели защищает отправку данных, повторная проверка защищает публикацию результата.
Withdrawal не может вернуть уже переданные bytes; он запрещает последующие действия.

## Что мне стоит изучить

1. TOCTOU — состояние меняется между проверкой и использованием; fresh guarded transaction
   и physical witness ограничивают окно гонки.
2. Provenance — проверяемое происхождение: immutable acquisition→admission→analysis→Identity.
3. Versioned contract — новый смысл требует новой версии; mixed-evidence-v2 сохраняет старый digest.
4. Transaction boundary — external I/O вне locks, publication атомарна внутри БД.
5. Defense in depth — application, database constraints и runtime grants закрывают разные обходы.

## Production considerations / Git

Production AI remains fail-closed; reviewed URL support не активирует live provider. Отдельно
нужны approved provider/privacy/retention, IAM, distributed quotas/jobs/cost/timeouts и release tests.
Qualified slice сохранён local commit `feat(evidence): qualify URL generation and Identity provenance`.
Push/remote CI/issue closure не выполнялись: whole #121 ещё partial,05 не начат.

Факты: HEAD/prerequisites, full1226, Linux35, Python10, renderer29, report gates, packaged startup,
source/class hashes и code/SQL inspection подтверждены выполненными проверками.
Инженерный вывод: typed citations + fresh physical/access/source guards сохраняют единый trust boundary.
Assumptions: корректная изоляция storage/operator access и будущий adapter, который соблюдает port;
их production qualification отдельно. Scope04 qualified; это не whole #121 или production readiness.
