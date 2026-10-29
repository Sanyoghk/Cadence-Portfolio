# Verification boundaries

## Backend28 — current qualification

[Actual report28](prompt28-backend-report.md) отделяет backend09–22 gate от pending frontend/
cross-stack28 и production/provider qualification. Проверять именно текущие source/image
fingerprints и actual report completeness; historical green run не квалифицирует новый HEAD.
Новый mandatory PostgreSQL audit migration report, secret/SAST/container validators и runtime
startup входят в этот gate. Failures/skips/incomplete scan не выдаются за pass.

## #121 reviewed PDF text analysis — 2026-10-14

ADR0019/V37: latest current TEXT/PDF_TEXT server selection,1–5/20000 UTF-8 bytes,
physical PDF checks до model и publication, exact immutable sources/quotes и fresh all-source
access/version/withdrawal preflight. PDF Identity copy/approval остаётся fail-closed.
Final clean verify116 suites/1053 tests,0 failures/errors,3 Windows skips;1050 executed pass.
H2PDF47/PGPDF56,V36 upgrade1,capacity1,OpenAPI3 passed; mandatory PG/provider reports PASS
без required skips. Python10/10,renderer29/29,Linux35/35 covers Windows skips;JaCoCo94.82%.
Spotless/compilation/JAR/startup health200;anonymous generation/PDF operations401.
[Report](issue-121-pdf-analysis-slice.md). Local only; no remote exact-SHA CI/live qualification.
Next: PDF Identity fences,other non-TEXT policy,APPROVED Identity→Strategy.

## #121 explicit PDF admission history — 2026-10-14

ADR0018/V36: exact reviewed PDF selectors, immutable shared consent history, current physical
snapshot и existing sourceVersion withdrawal. No PDF model consumption; legacy TEXT filters/guards
unchanged. Final full114 suites/1024 tests,0 failures/errors,3 Windows skips;1021 executed pass.
H2PDF34/34,PGPDF42/42,domain2/2,populated V35 upgrade1/1,OpenAPI3/3;mandatory reports PASS без skips.
Runtime INSERT/withdrawal + no rewrite/delete,SQL forged metadata,cap1000/1001,expiry-aware re-review
и обе race orderings checked. Python10/10,renderer29/29,Linux35/35;JaCoCo94.78%,Spotless/JAR PASS.
Packaged local health200,anonymous PDF previewGET/admissionPOST/snapshotGET401.
[Report](issue-121-pdf-admission-slice.md). Next: PDF consumer/publication/Identity fences,
other non-TEXT policy,APPROVED Identity→Strategy;real/live/hosted qualification отдельно.

## #121 private PDF review preview — 2026-10-13

ADR0017: actual OWNER/member scoped PDF_TEXT preview, original/latest CLEAN/source/ledger checks,
bounded private bytes read вне transaction, fresh finalize/access/version/deadline fence.
No AI admission или persisted review; source остаётся FILE/PENDING, model consumers TEXT-only.
Final full112 suites/982 tests,0 failures/errors,3 Windows skips;979 executed pass.
Domain7,H2PDF18,PGPDF19,OpenAPI3 — all PASS; mandatory PostgreSQL/provider reports PASS без skips.
Python10/10,renderer29/29,Linux35/35;JaCoCo94.67%,Spotless/JAR PASS.
Packaged local health200,anonymous PDF GET401. [Report](issue-121-pdf-review-slice.md).
Remaining: PDF admission/consumer fences, URL/images/social policy, APPROVED Identity→Strategy,
real adapter/hosted qualification, AI Jobs/budgets/full Playbook.

## #121 TEXT Identity provenance — 2026-10-13

ADR0016/V35: explicit evidence receipt copy, scoped quote read и inherited source/withdrawal
approval fence. Current copier audit immutable; manual/legacy contracts/history сохранены.
Final full109 suites/938 tests,0 failures/errors,3 Windows skips;935 executed pass.
H2API17/17,PG24/24,migration1/1,domain2/2. Mandatory PostgreSQL/MinIO/ClamAV/renderer/full pipeline
gates PASS;Python10/10,renderer29/29,Linux35/35 закрывает Windows skips. JaCoCo94.55%,Spotless/JAR PASS.
Packaged local health200,anonymous new copy POST401/provenance GET401.
[Detailed report](issue-121-identity-evidence-slice.md). Remaining: non-TEXT eligibility,
APPROVED Identity→Strategy, AI Jobs/budgets/Playbook, real provider/hosted qualification.


## #121 bounded TEXT analysis/citations — 2026-10-12

ADR0015/V34: отдельный `analyze-evidence` action, 1–5 current TEXT admissions / 20000 UTF-8
bytes, immutable sources/exact citations, actual OWNER/membership и publication fencing.
Legacy analysis unchanged; half-surrogate quote regression покрыт encoder validation.
Final full105 suites/894 tests,0 failures/errors,3 Windows skips,891 executed pass;
H2API15/15,PG21/21,port5/5,fake1/1. Mandatory Docker gates PASS;Spotless/JAR/JaCoCo94.52%.
Evidence receipt → legacy Identity copy fail-closed проверен use-case и SQL regression tests.
[Detailed qualification](issue-121-evidence-analysis-slice.md) содержит фактические final counts
и команды. Linux35/35,renderer29/29,Python10/10; packaged health200/new anonymous POST401.
Остаются evidence receipt/citations → Identity integration, non-TEXT policy, approved
Identity→Strategy, AI Jobs/budgets, real adapter/hosted commercial qualification.


## #121 versioned TEXT re-review — 2026-10-12

ADR0014/V33 qualified locally: full101 suites/852 tests,0 failures/errors,3Windows skips,
849 executed pass;Linux35/35 закрывает symlink scenarios. H2HTTP14/14,PG27/27,migrations4/4.
Python10/10,renderer29/29; mandatory PostgreSQL/MinIO/ClamAV/renderer/full pipeline gates PASS.
Spotless/JAR/JaCoCo94.41%,packaged health200 и anonymous admission routes401.
Новый exact version context требует explicit review; replay409,withdrawn sourceVersion запрещена
во всех contexts,1000 reviews/evidence. Runtime grants и V1–V32 не менялись.
Fixture capturedAt теперь использует production-consistent DB Clock; time assertions сохранены.
[Отчёт](issue-121-re-review-slice.md). Regrant withdrawn version,AI/citations/non-TEXT/approved
Identity→Strategy и remote exact-SHA/live production qualification остаются отдельными scopes.

## #121 withdrawal/current TEXT snapshot — 2026-10-12

V32 append-only withdrawal и DB parent identity guards qualified locally. Final full Maven:
101 suites,840 tests,0 failures/errors,3Windows skips;837 executed pass, Linux35/35 закрывает
skipped filesystem scenarios. H2HTTP11,PostgreSQL20,migrations2,withdrawal domain1 прошли.
Python10/10,renderer29/29; mandatory PG/MinIO/ClamAV/renderer/full pipeline report gates PASS.
Spotless/JAR/JaCoCo94.41% passed; packaged health200,anonymous preview/admit/snapshot/withdrawal401.
[Полный отчёт](issue-121-withdrawal-slice.md). First full failure был ошибочным DELETE predicate
в новом test; исправлен без ослабления assertion и повторён весь suite.
Snapshot current только при capture. Future generation selection/citations/publication fencing,
versioned re-review, URL/PDF/image eligibility и approved Identity→Strategy остаются незавершёнными.
Whole #121 не закрыт; no push/merge/deployment,remote exact-SHA CI/live providers отдельно.

## #121 TEXT admission — 2026-10-11

Explicit OWNER source review/snapshot и V31 qualified locally: full827,0 failures/errors,
3Windows skips покрыты Linux35/35 без skips;824 executed tests прошли. Domain4,H2HTTP7,
PostgreSQL13,migration1; H2 isolation regression и прежний method-security10 прошли.
Python10/10,renderer29/29; mandatory PostgreSQL/provider report gates PASS. JAR/Spotless/JaCoCo
94.36%, packaged health200/anonymous new GET+POST401. [Отчёт](issue-121-text-admission-slice.md).
URL/PDF/image admission, withdrawal/consumption/citations/approved Strategy source и real AI
не completed; exact-source remote CI/production qualification остаются отдельными gates.

## #121 declared-analysis provenance — 2026-10-08

V30/explicit Identity copy from current successful analysis qualified locally:
full801,0 failures/errors,3Windows filesystem skips покрыты Linux35/35 без skips.
Identity PG30/30,migrations3/3,H2HTTP16/16,domain5/5; full provider/runtime10/10,
scripts10/10,renderer29/29,mandatory PostgreSQL/provider gates passed.
Spotless/JAR/JaCoCo94.27% passed; packaged health200/new anonymous route401.
[Полный отчёт](issue-121-provenance-slice.md). Evidence citations/approved Strategy source,
real adapters и remote exact-SHA CI не completed; #121 остаётся IN PROGRESS.

## Запуск PostgreSQL tests без Docker

По умолчанию PostgreSQL suites используют Testcontainers. При отсутствии Docker
можно явно передать отдельный временный локальный PostgreSQL:

```powershell
$env:CADENCE_TEST_POSTGRES_URL='jdbc:postgresql://127.0.0.1:55439/postgres'
$env:CADENCE_TEST_POSTGRES_USER='test_admin'
$env:CADENCE_TEST_POSTGRES_PASSWORD='<temporary test password>'
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=PostgreSql*Test' test
python -B scripts/verify_postgres_report.py
```

Используйте disposable server. Test identity нужна возможность CREATE/DROP DATABASE:
каждый suite создаёт отдельную БД со случайным именем `cadence_test_*` и удаляет
только её после тестов. URL ограничен loopback и явным port; existing application
databases не используются. Это не заменяет MinIO/ClamAV/renderer tests: для них
по-прежнему нужен Docker, а для renderer — проверенный Linux sandbox.
CI сохраняет обязательный Docker preflight; skips не принимаются report gates.

`PostgreSqlReviewMigrationTest` и `PostgreSqlReviewConcurrencyTest` входят в
обязательный PostgreSQL report gate. Актуальные результаты исправлений review:
[отчёт](review-remediation-90686e5.md).

## Current API contract gate #111 — 2026-10-01

Source `7ee9efa`: `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify`
реально прошёл с Docker: **615 tests, 0 failures/errors, 3 Windows filesystem skips**,
77 XML suites. Все **35/35 filesystem cases** прошли отдельно через
`.\scripts\run_linux_storage_tests.ps1` на Linux без skips. JaCoCo line coverage:
3800 covered / 262 missed (~93.55%), configured gate passed; JAR/Spotless passed.

```powershell
& 'python' -m unittest discover -s scripts -p 'test_*.py'
& 'python' scripts/verify_postgres_report.py
& 'python' scripts/verify_evidence_reports.py
docker build --tag cadence-renderer-preflight docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-preflight -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\scripts\start_local_evidence.ps1 -ProjectName cadence-issue117-validation -UpdateSignatures
```

Python scripts **10/10**, freshly built Linux renderer **29/29**; PostgreSQL/evidence
report gates passed without missing/skipped providers. V21 → V22 Compose upgrade,
Hibernate validate, runtime startup и real HTTP health200/unauthenticated401 problem
contract прошли. UTC migration suite теперь mandatory в report gate. Remote CI для
pushed SHA и production deployment квалифицируются отдельно. Historical counts
ниже относятся к предыдущим revisions.

Details, threat model, warnings, exact focused commands и limits:
[completion report](issue-111-completion.md), [API contract](api-contract.md).

### Remote result для #111

[CI 36893518886](https://github.com/Sanyoghk/Cadence/actions/runs/36893518886),
SHA `1e5716d82f7cfca13289fa30ef6c98e157b0d572`: оба jobs success. Full Linux Maven
**615 tests, failures/errors/skips0**, BUILD SUCCESS11m10s, JaCoCo passed; scripts
Python10/10, renderer29/29, all required reports passed. Enforcing scoped AppArmor,
kernel boundary probe и real screenshot1280×720 passed. Full provider7/7;
OSV verified165 Maven components без findings. Последующий docs-only report commit
не изменяет product source и не квалифицирует external deployment.

## Authorization

The #74 reserved-role discrepancy is corrected: user-facing controllers and Spring use cases require OWNER, including reads and analysis. EDITOR/VIEWER remain stored reserved roles but grant no business operations. HTTP and direct-bean tests verify denial within assigned Workspaces; local roles remain authoritative over token claims.

HTTP controllers enforce roles and object membership. Business use cases receive permitted Workspace IDs from the authenticated server-side principal and use scoped lookups. These IDs are an internal authorization context, never a request DTO field. Direct-call regression tests cover analysis, Brand creation/listing and evidence registration/listing outside the permitted Workspace. Archived Workspaces reject new Brands, Brand updates, analysis and evidence registration; existing data remains readable by members.

Role enforcement also applies to user-facing use-case Spring beans, including GetJobUseCase. Their class-level default is denyAll; each exposed operation has an explicit rule. The supplied Workspace scope must be a subset of the current authenticated CadencePrincipal's memberships. Workspace creation requires the current user's ID as well as OWNER. UseCaseMethodSecurityTest and JobApiIntegrationTest call the real proxied beans directly and check role denial, forged scopes, impersonation and allowed operations. This does not introduce collaboration or an administrative permissions API. See [use-case authorization](use-case-authorization.md) for the policy and proxy boundary.

## Authentication

SecurityIntegrationTest uses a mocked decoder to test HTTP authorization. JwtDecoderTest separately exercises the configured real decoder with generated RSA signatures and a loopback JWKS fixture: accepted token, wrong signature, issuer, audience, authorized party, expired/future token and missing expiration. The fixture makes no request to Clerk. Live Clerk account configuration remains an external verification step.

CSRF is disabled because this API authenticates explicit Authorization Bearer headers and does not use session/cookie authentication. Adding cookie authentication requires revisiting this decision.

## Persistence and CI

Local H2 tests use Hibernate create-drop and do not verify Flyway SQL or PostgreSQL constraints. PostgreSqlPersistenceTest uses PostgreSQL/Testcontainers and applies migrations on an empty database. The populated V9 to V10 upgrade is covered; other historical upgrade paths and backup/restore procedures are not yet verified.

The GitHub Actions workflow requires Docker, runs clean verify and checks both PostgreSQL and
required evidence-provider reports. Missing, skipped or failed required integration suites fail
the report gates. Historical [CI run 34544158729](https://github.com/Sanyoghk/Cadence/actions/runs/34544158729)
passed for `b50c69a`: Docker, clean verify and the then-current PostgreSQL report gate all succeeded.
That historical run does not verify the later #117 adapter changes. Required branch checks remain
an operational follow-up; no branch protection settings are changed here.

Dependabot configuration provides update proposals for Maven and Actions. Maven dependency vulnerability scanning is now implemented separately with OSV and complete runtime/test SBOM checks; see [dependency security](dependency-security.md). SAST and container scanning remain release work.

## Production gates

S3-compatible object storage, private clamd scanning, signature freshness, durable quotas/lifecycle
and isolated screenshot/PDF extraction are implemented. Bare `prod` keeps disabled evidence
providers. Activating real providers/workers requires `s3,clamd`, operational acknowledgements and
`renderer` for artifacts; mixing `prod` with development/test profiles is rejected. Production S3
requires HTTPS, strict private policy/owner ACL/versioning checks, and forbids HTTP/MinIO-fixture
shortcuts. Acknowledgement properties are an activation contract, not infrastructure verification.

Before real user data, qualify the selected provider's TLS/IAM/encryption/region, private daemon
transport, current signatures and continuous updater, ingress/resource controls, deployed sandbox,
monitoring and backup/restore. Apply migrations with a separate release identity and run the app
with a non-owner runtime role. Clamd has no protocol TLS/authentication; multi-host deployment needs
an authenticated encrypted channel. The local Compose network intentionally has no public app
egress: live Clerk/social API calls and public URL fetching are not validated by its healthcheck.
See [operations](evidence-operations.md) and [storage/scanner architecture](storage-scanner-design.md).

Real AI, versioned Identity approvals, export and analytics remain separate project work. A CLEAN
scan or extracted artifact does not promote source evidence to public/approved content.

## Real evidence-provider gate (#117) — historical targeted slice

Executed command:

```powershell
.\mvnw.cmd '-Dtest=RendererEvidenceAdapterTest,RendererContainerIntegrationTest,ClamdContainerIntegrationTest,FullEvidenceProviderIntegrationTest' test
```

Result: **20 tests, 0 failures, 0 errors, 0 skips**. Real PostgreSQL/Flyway V1–V21, restricted MinIO
credentials, ClamAV and Chromium/Poppler verified PDF text, private website PNG, canonical EICAR
PDF attachment and encrypted-PDF rejection. Fake private HTTP tests covered malformed framing,
deadlines and invalid PNG zlib streams. The renderer container additionally ran all **7 Python
boundary tests without skips**, including Linux symlink/ancestor/FIFO/hardlink/output bounds.

The website fetch is an offline byte fixture; all subsequent provider bytes and persistence are
real. Only scanner freshness Clock is anchored to the loaded signature VERSION for reproducibility;
durable timestamps and leases use PostgreSQL time. Production scanner uses system UTC.

On Windows, the standalone renderer Python invocation ran seven tests with two Linux-descriptor
skips. Those two scenarios were actually executed without skips inside the Linux container above.
This targeted result preceded the runtime PostgreSQL privilege regressions and the complete suite.
Their final verification is recorded below; the historical count of 20 is preserved for that slice.

## Historical full local verification (#117) — 2026-10-01

Этот завершённый gate относится к revision до inherited-proc/parent-secret/readiness/
ephemeral-cleanup renderer correction. Ниже сохранены historical counts. Замороженная текущая
revision после timeout/PDEATHSIG исправления прошла Linux Python 29 и real Chromium screenshot;
новый hardened full Maven593 также прошёл. Enforcing Linux/AppArmor CI прошёл отдельно ниже.
Текущие Linux/full/remote результаты приведены отдельно;592 здесь остаётся historical.

Фактически выполнено:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean spotless:apply verify
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence/evidence-renderer:local docker/evidence-renderer
```

Maven: **BUILD SUCCESS, 592 tests, 0 failures, 0 errors**, 72 Surefire XML reports.
На Windows прошли 589 tests, пропущены только три Linux filesystem checks:
`rejectsLinkedFileForReadAndWrite`, `rejectsLinkedStorageRoot`, `rejectsLinkedParentForReadAndWrite`.
Linux storage launcher реально выполнил **35/35 с нулём skips**, включая эти три scenarios.
Required PostgreSQL и evidence-provider report gates passed: Docker integration suites не
пропущены. Python scripts **10/10 passed** (7 report-gate и 3 kernel-diagnostics tests);
Linux renderer Python **7/7 passed, 0 skipped**.
Spotless, compilation, packaging и JaCoCo прошли; line coverage **93.26%** (3736/4006).

`FullEvidenceProviderIntegrationTest` выполнил **7/7 без failures/errors/skips** с реальными
PostgreSQL/Flyway V1–V21, restricted runtime role, MinIO application key, ClamAV и isolated
Chromium/Poppler. Canonical EICAR PDF attachment, encrypted PDF, artifact/provenance integrity,
role restrictions и private PNG проверены. Current-clock private ClamAV smoke отдельно
подтвердил CLEAN, EICAR INFECTED и signature freshness ≤48h после actual signature update.
Это дополняет reproducible integration Clock, привязанный к signature VERSION.

Local Compose startup с `start_local_evidence.ps1 -UpdateSignatures` и повторным запуском
через gateway прошёл: app healthy, host loopback health 200, unauthenticated brands 401,
MinIO health 200, PostgreSQL TCP connect. У provider/app services нет прямых host bindings;
loopback ports принадлежат только gateway. External provider OAuth, Clerk и public egress
этим smoke не проверяются.

OSV clean: **165 Maven runtime/test components**, complete inventory comparison, и **4 unique
renderer PyPI packages**; см. [dependency security](dependency-security.md).
Локальные reports/logs: `target/surefire-reports`, `target/site/jacoco/jacoco.xml`,
`var/issue117-corrected-final-verify.log`, `var/issue117-corrected-linux-filesystem.log`,
`var/issue117-live-clamd.log`. [Completion report](issue-117-completion.md) отделяет эти факты
от deployment qualification. Исторические CI links относятся к указанным прежним commits;
они не заменяют remote CI результата финального revision. Первый remote run
36821456124 выявил DNS handoff и Ubuntu renderer sandbox несовместимость; corrected
local gate выше включает два новых regressions и namespace preflight. Scoped AppArmor
profile compiled locally; на том этапе actual enforcing Ubuntu gate ещё не прошёл.
Исторические Ubuntu24/26 CI загрузили enforcing renderer profile, но прежняя fresh-proc
configuration отказала на `proc-mount`; эти runs завершились ошибкой. Commit `b4708c5`
добавил bounded, allowlisted и redacted kernel diagnostics. Local diagnostics tests не доказывают
successful AppArmor sandbox. Latest contract сохраняет inherited Docker masks без fresh proc,
sole non-dumpable server PID1, mandatory startup negative parent-access/proc preflight и отдельный
token-free readiness container. Trusted child Python PID1 устанавливает/проверяет dumpable0 и CORE0
до input/parser. Normal completion допускает bounded `/tmp`+`/dev/shm` purge до/после job.
Worker timeout, signalled supervisor (`returncode < 0`) либо killpg/reap failure poisons перед
cleanup; postpurge и следующий tenant запрещены. Mutable child PDEATHSIG не позволяет считать
outer-wrapper reap proof namespace death. Kill/reap best effort; recreate целого container с fresh
tmpfs обязателен, slow/hostile document может требовать operator recovery и alert на 503.
Startup filesystem guard
требует read-only root, `/tmp`≤128MiB и `/dev/shm`≤256MiB private rw,noexec,nosuid,nodev tmpfs,
mount root `/`, и `/dev/mqueue` ro,noexec,nosuid,nodev,size=4k,mode=000 mask. Trusted launcher
запрещает host binds; mountinfo не различает bind целого host tmpfs с root `/`. Outer CapBnd=0;
child CapEff/Prm/Inh/Amb=0 после exec, namespace-scoped bounding set не даёт parent privileges.
HTTP watchdog 20s, worker 10s, каждый pre/post purge 2s, kill/reap 2s.

## Renderer verification after timeout review (#117) — 2026-10-01

Фактически выполнены:

```powershell
docker build --tag cadence-renderer-preflight docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-preflight -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

**29/29 Linux Python passed, 0 failures/errors/skips**: server 12, probe 8, ephemeral 9.
Probe tests включают actual denied parent proc/ptrace/process-VM access и invalid filesystem/mqueue
configuration. Ephemeral tests проверяют fixed roots, mode 000/no-follow traversal, combined budget
и poisoning; новые server regressions подтверждают poison before cleanup/reuse после timeout,
signalled supervisor и killpg/reap failure. Actual mandatory namespace preflight ранее passed;
real Chromium screenshot 1280×720 повторно passed после trusted child PID1 protection.
Scoped corrected AppArmor profile compiled locally; AppArmor enforcement Docker Desktop не проверяет.

Локальные logs: `var/issue117-final-renderer-build.log`, `var/issue117-final-renderer-python.log`,
`var/issue117-final-renderer-smoke.log`.
Historical Python 26 относился к revision до timeout/PDEATHSIG review; current 29 включает её исправление.

## Final hardened full verification (#117) — 2026-10-01

Фактически выполнено:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean spotless:apply verify
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
```

**BUILD SUCCESS:593 tests,0 failures/errors,590 passed,3 Windows filesystem skips;72 Surefire XML**.
Linux filesystem suite **35/35 passed,0 skips**, включая все3 Windows scenarios.
Required PostgreSQL/evidence report gates passed без required skips. Real full provider **7/7** и
`RendererContainerIntegrationTest` **6/6** passed; startup-negative fixtures завершились примерно6.7s.
Linux renderer Python **29/29**, scripts Python **10/10** passed. JaCoCo **93.26%**:3736/4006 lines.
Spotless/compilation/packaging passed. Logs: `var/issue117-final-hardened-verify.log`,
`var/issue117-final-linux-filesystem.log`, renderer logs выше. Fresh JAR Compose startup passed:

```powershell
.\scripts\start_local_evidence.ps1 -ProjectName cadence-issue117-validation
```

App/gateway/renderer-ready healthy; `/api/v1/health`200 и unauthenticated `/api/v1/workspaces`401.
Renderer без init/Docker exec healthcheck; readiness environment не содержит token.
Log: `var/issue117-final-compose-application.log`. Historical full592 не заменяет этот593 result.
Remote enforcing CI результат приведён далее; внешний production deployment не выполнялся.

## Final remote Linux/AppArmor verification (#117) — 2026-10-01

[CI36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248) завершился success
для source commit [ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab](https://github.com/Sanyoghk/Cadence/commit/ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab).
[Verify job110276029645](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248/job/110276029645)
и [dependency-security job110276029478](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248/job/110276029478)
оба завершились success.

Actual enforcing AppArmor profile загрузился; kernel boundary preflight вывел `namespace-probe ok`,
real Chromium sandbox создал screenshot1280×720. Это устранило прежний CI `proc-mount` blocker.
Фактически выполненные final verify commands:

```sh
sh ./mvnw --batch-mode --no-transfer-progress clean verify
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/verify_postgres_report.py
python3 scripts/verify_evidence_reports.py
docker build --tag cadence-renderer-verification docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-verification -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

| Remote gate | Фактический результат |
|---|---|
| Полный Maven | BUILD SUCCESS;593 tests,0 failures/errors/skips; JaCoCo checks passed |
| Renderer integration / full provider | 6/6 и7/7 passed |
| Python scripts / renderer | 10/10 и29/29 passed,0 skips |
| PostgreSQL/evidence report gates | Passed;0 missing/skipped required suites |
| Dependency-security | Success;165 Maven components verified, OSV0 findings |

Результат привязан к указанному source SHA. Последующий docs-only commit фиксирует этот отчёт
при неизменном product source и не является отдельным тестовым запуском нового SHA.
CI подтверждает Linux/AppArmor fixture; selected-provider IAM/TLS/region/network и внешний
production deployment остаются отдельной qualification.

## Website metadata (#117)

Bounded internal extraction and its security/verification boundaries are described in [ADR-0006](adr/0006-bounded-website-metadata.md). V10 adds optional snapshots without backfilling old sources. New PostgreSQL fixtures cover a populated V9 to V10 upgrade; previous broad statements about absent upgrade tests refer to earlier history, not this new fixture. This does not verify every historical upgrade or backup/restore procedure.

Historical CI run [34546765430](https://github.com/Sanyoghk/Cadence/actions/runs/34546765430) passed for
`ea3a5fc`: 211 tests with no failures/errors/skips, including all six then-current PostgreSQL fixtures
and the mandatory report gate. It verifies that website-metadata/V10 revision and populated V9 to
V10 preservation; it does not attest to the subsequent #117 production adapters or final suite.

## Review handoff remote gate — 2026-10-05

Review source `9f6f530ac1bf580aa04406bbf1f9322bfa025f24` pushed в mvp;
[CI37257337832](https://github.com/Sanyoghk/Cadence/actions/runs/37257337832) оба jobs success.
Linux Maven695/695, failures/errors/skips0, scripts10/10, renderer29/29, all required
PostgreSQL/provider reports и enforcing sandbox/preflight passed. OSV verified165
components без findings. Local independent handoff full695 с3Windows skips дополнен
Linux35/35 без skips. [Details](review-remediation-90686e5.md).
Новый локальный Strategy API slice квалифицируется отдельно: [report](issue-102-api-slice.md).
## Strategy REST lifecycle #102 — 2026-10-05

Актуальные local/remote результаты и точные команды: [completion report](issue-102-completion.md).
PostgreSqlStrategyApiTest остаётся mandatory в report gate; теперь содержит HTTP lifecycle,
raw SQL approval/lineage/content negatives и competing edit/approve/generation/archive tests.
FullEvidenceProviderIntegrationTest проверяет эти commands под actual non-owner runtime role.
Предыдущие CI/test counts ниже относятся к своим revisions, не к новому V27 source.

### #102 remote result

Source e400acb8eadbaa5b186b61fa8549e9ed0e70247b, [CI37261511659](https://github.com/Sanyoghk/Cadence/actions/runs/37261511659): both jobs success,
Linux746/746 без failures/errors/skips, Python10/10+renderer29/29, all required reports
и enforcing AppArmor/kernel/sandbox/screenshot1280×720 passed. OSV165 dependencies без
findings на момент scan. Local746 с3Windows skips дополнен Linux35/35 без skips;
packaged JAR health200 и5 Strategy operations401 проверены. [Report](issue-102-completion.md).
Subsequent docs-only qualification update не является отдельным полным test run нового SHA.

## #121 manual Identity drafts — 2026-10-05

Новый V28/manual Identity source qualified локально согласно [slice report](issue-121-draft-slice.md).
PostgreSqlBrandIdentityApiTest и PostgreSqlBrandIdentityMigrationTest добавлены в обязательный
report gate: missing/skipped/failed report отвергает CI. UTC inventory учитывает26 columns,
включая явно проверенные created_at/updated_at brand_identities. Legacy migrations не изменены.
Exact-SHA remote CI нового source ещё нет: push после завершения всего #121. Baseline results
#102 не используются как доказательство нового product source.

Local qualification #121 slice1: full770,0failures/errors,3Windows filesystem skips;
Linux35/35 без skips; Python10/10+renderer29/29, all mandatory PostgreSQL/provider reports,
Spotless/JaCoCo/JAR, packaged startup health200 и4Identity operations401 passed.
Новый feature commit local only; issue остаётся IN PROGRESS.

## #121 Identity approval / revision lifecycle — 2026-10-05

Второй slice реализует separate committed draft approval, actual DB OWNER/membership,
server actor/time, approved/locked PATCH→independent DRAFT с immutable lineage и201/Location.
V29 сохраняет V28 content/version/token/NULL legacy audit; защищает approved root/collections,
audit/lineage и root history, runtime grants расширены только status/audit columns.

Local qualification: full786,0failures/errors,3Windows filesystem skips закрыты Linux35/35;
focused42/42, scripts10/10,renderer29/29, all mandatory PostgreSQL/provider reports и actual
restricted runtime lifecycle tests прошли. [Report](issue-121-lifecycle-slice.md).
#121/#107 Task2.3 остаются IN PROGRESS: citations и approved Identity→Strategy source ещё не
реализованы. Push/exact-SHA CI после всего issue; merge/deployment не выполнялись.

## 2026-10-14 — задание01 / PDF Identity consumer V38

Scope01 qualified locally: PDF analysis→Identity DRAFT/read/direct+inherited approval,
original publication lineage Brand=attempt+1, immutable artifact/original/latest scan IDs,
physical I/O вне transactions и fresh locked source/access/withdrawal/expiry/ledger checks.
V1–V37 unchanged, V38 metadata SQL guard; legacy/manual/TEXT сохранены. [Отчёт](issue-121-pdf-identity-slice.md),
[ADR0020](adr/0020-pdf-identity-consumption.md), [ledger](mvp-prompt-progress.md).

Final isolated `mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify`:
BUILD SUCCESS2026-10-14T04:01:26+01:00;118suites/1079tests/0failures/errors,3Windows skips
covered Linux35/35. PDF H2API57/PG68,migration2,capacity2,OpenAPI3 passed. Mandatory PostgreSQL/
MinIO/ClamAV/renderer/full pipeline reports PASS;Python10/10,rebuilt renderer29/29,
JaCoCo94.85%,packaged health200/Identity copy/approval/provenance401.
464 source/build hashes идентичны workspace,302 class hashes stable. Первый root full run
имел1079tests/0failures/errors, но failed JaCoCo из-за изменившихся class IDs; он не pass.
Snapshot qualification не меняет POM/gates/IDE settings. Production/live provider gates отдельно.

Local commit only; no push/remote CI/closure/merge/deploy. #121/#107 остаются IN PROGRESS.
Remaining: other source eligibility и approved Identity→Strategy; задания02+ не начаты.
