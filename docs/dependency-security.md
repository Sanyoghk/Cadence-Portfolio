# Dependency security — Task 0.2 / #8 / #87

## Backend28 — fresh review 2026-10-19

Current Maven runtime/test inventory:194 components; fresh OSV2.5.1 scan and independent
inventory comparison:0 findings. No Maven dependency was added. Full actual commands,
qualification state and limitations are in [report28](prompt28-backend-report.md).

New `source-security.yml` adds checksum/digest-pinned Gitleaks8.30.1, Semgrep1.181.0
and Trivy0.76.0 with contents:read, no persisted Git credentials, no privileged PR trigger,
bounded execution and seven-day full artifacts. Semgrep runs locally with telemetry off;
rule download does not upload source. SAST audit findings remain visible and require human
triage; this is not an automatic zero-findings gate or a remote CI success claim.

- Full Git history:156 commits,1 redacted generic-api-key false positive in ordinary ADR0040
  wording about a private session subtree and cancellation/completion guards, not a credential. History retained;
  current wording clarified. No ignore file/baseline or history rewrite.
- Java SAST:60rules/429files/one audit finding/zero parse errors. Intentional raw socket for approved public HTTP fetch remains a visible audit
  finding. HTTPS wraps the pinned socket in verified JSSE with hostname/SNI/handshake checks.
  ADR0005 HTTP support is not silently removed to satisfy a pattern scanner.
- Renderer: original11HIGH addressed with OS security updates and removal of unused codec,
  build-tool and cached installer bytes. Complete final image scan:0HIGH/CRITICAL,
  564MEDIUM/83LOW, vendor-unfixed. Runtime Python distributions remain the original four pins.
- Application OS/JRE base: security upgrades address2OpenSSL HIGH; complete base scan:
  0HIGH/CRITICAL,27MEDIUM/32LOW, vendor-unfixed. Java dependencies are checked independently
  through the complete Maven SBOM; an OS-base scan alone does not qualify the application JAR.
- Additional infrastructure scan found PostgreSQL1CRITICAL/24HIGH, MinIO5CRITICAL/71HIGH,
  ClamAV1HIGH and gateway5HIGH. Candidates now: PostgreSQL/ClamAV/gateway0findings after
  distribution fixes and native su-exec replacing the old Go helper. Unchanged MinIO/mc
  upstream sources now build with pinned Go1.27.2 and exact patched module manifests/sums;
 0HIGH/CRITICAL,2UNKNOWN retained. Actual provider/runtime qualification24/24PASS.
- Those2UNKNOWN are the [official OpenPGP advisory](https://vuln.go.dev/ID/GO-2026-5932.json).
  Seven affected package paths are absent from exact-image compiler dependency inventories,
 1359server/605client packages. Strict independent applicability verification binds image ID,
  fixed advisory/module/binary and bounded inventories. It preserves raw findings and rejects
  any unproven UNKNOWN or HIGH/CRITICAL; counterexample fixtures pass. No general waiver.

All severities remain in raw reports. Container verification rejects HIGH/CRITICAL and every
unreviewed UNKNOWN, including unfixed findings; incomplete/missing inventory is a failure. Lower severities
are retained risks, not a production-readiness certificate. No severity-filtered report,
disabled analyzer, `nosemgrep`, allow-empty or edited raw scanner output was added.
The OpenPGP check is reproducible absence-of-compiled-code evidence, not a reachable-risk waiver.

Actual read-only GitHub checks: user admin; private rulesets403 due repository-plan limitation,
branch protection403 due integration administration permission. No enforcement/settings change,
remote run or pushed-SHA qualification is claimed. Whole28 needs future frontend/adapters review.

## Актуализация 2026-10-15 — Spring major migration

Текущий stack переведён на Boot4.0.8/Spring7.0.9/Tomcat11.0.27 по coherent BOM с security patch overrides Jackson3.1.7/Tomcat11.0.27.
Jackson2 compatibility сохраняет reviewed wire/snapshot contract; deprecated module требует
отдельной последующей migration. [Фактические gates, scan и ограничения](spring4-migration-report.md).
Раздел findings ниже описывает прежнюю remediation Boot3.5, а не текущий dependency inventory.
Исторический CI baseline09 выявил два новых Spring advisories; они не suppress и не считаются
устранёнными старым успешным scan. Новый local scan и remote CI учитываются отдельно.

## Architecture and policy

The separate `dependency-security` GitHub Actions job runs for PRs, pushes to mvp/main and manual workflow runs. Maven `test-compile` must resolve all artifacts successfully before CycloneDX Maven plugin 2.9.3 generates the inventory, including test scope. The plugin alone can warn about unresolved artifacts yet produce an incomplete SBOM; compilation closes that gap.

OSV-Scanner 2.5.1 scans the generated CycloneDX file. Its Linux binary SHA-256 is pinned in the workflow and checked before execution. Python independently checks that the complete report matches the Maven inventory (artifact name/version multiplicities), includes representative runtime and test dependencies and has no findings. Native scanner failure, missing packages, invalid reports, download/API errors or checksum mismatch fail the job. There are no suppressions, allow-empty options, severity exclusions or continue-on-error shortcuts. All reported vulnerability severities require triage.

SBOM and JSON findings are retained as repository artifacts for seven days. The job has contents:read only, no deployment credentials or security-events write access, and uses pull_request rather than pull_request_target. It sends dependency coordinates to OSV; it does not upload source files or application secrets. OSV availability is required; failure is not treated as a clean scan.

CycloneDX is a build-time plugin, not an application dependency. OSV works without GitHub Advanced Security or an NVD API key. Direct OSV pom resolution currently excludes Maven test dependencies, which is why the Maven-generated inventory is used. Sources: [OSV supported inputs](https://google.github.io/osv-scanner/supported-languages-and-lockfiles/), [CycloneDX options](https://cyclonedx.github.io/cyclonedx-maven-plugin/makeAggregateBom-mojo.html), [OSV release](https://github.com/google/osv-scanner/releases/tag/v2.5.1).

## Findings and remediation

The initial scan identified 47 unique advisories across 14 components. These are dependency-version matches, not proof that all vulnerable paths are reachable in Cadence. All were remediated without suppressions. Updating Commons Compress also introduced a vulnerable managed Commons Lang version, which was then patched.

| Component | Before | After | Reason |
| --- | --- | --- | --- |
| Spring Boot BOM | 3.5.14 | 3.5.16 | Coherent maintenance update; fixes Boot autoconfiguration finding and updates managed libraries |
| Spring Framework | 6.2.18 | 6.2.19 | Reported URI parsing, resource handling, SpEL and DoS issues |
| Spring Security | 6.5.10 | 6.5.11 | X.509 impersonation / redirect findings; Cadence still uses JWT |
| Spring Data | 3.5.11 | 3.5.13 | Path/property/projection allocation and sorting DoS findings |
| Jackson BOM | 2.21.2 | 2.21.6 | Parser limits and deserialization restrictions |
| Tomcat | 10.1.54 | 10.1.59 | Authentication/access-control and protocol findings |
| PostgreSQL JDBC | 42.7.10 | 42.7.13 | SCRAM cost bounds and channel-binding downgrade |
| Logback | 1.5.32 | 1.5.34 | Deserialization findings in optional input modules |
| Log4j API | 2.24.3 | 2.25.5 | MapMessage JSON serialization |
| Commons Compress | 1.24.0 | 1.28.0 | Testcontainers transitive archive DoS issues |
| Commons Lang | newly resolved 3.17.0 | 3.18.0 | Recursive input processing DoS |

Boot remains on 3.5; no major framework migration was introduced. Explicit overrides use the Boot parent properties where available and dependencyManagement for Commons Compress. Revisit overrides against future Boot BOM updates. Tomcat 10.1.58 is mentioned as fixed in advisory data but was not released; Apache directs users to 10.1.59 ([security notes](https://tomcat.apache.org/security-10.html)).

## Security Review

- Threat: known dependency vulnerabilities become reachable through HTTP/JSON/URI or DB protocol input. Protection: patched versions, automated scan on changes, existing validation and authorization. Verification: initial positive findings, subsequent zero-findings scan plus application/security tests.
- Threat: missing test/transitive dependencies create a misleading clean result. Protection: actual Maven resolution, includeTestScope and report-to-SBOM comparison. Verification: negative fixtures for missing packages/test scope/coordinates and incomplete reports.
- Threat: compromised downloaded scanner or privileged PR execution. Protection: pinned version/checksum, pinned Actions, read-only token, no secrets, bounded job/download time, no privileged trigger. Verification: checksum executed locally for Windows binary and enforced in Linux CI.
- Threat: scan outages silently bypass the gate. Protection: nonzero exit fails; missing report or inventory also fails. Verification: empty/partial report fixtures and real scanner rejection of the original vulnerable inventory.

Runtime request flow remains JWT → local OWNER/membership → controller/use case → scoped repository → PostgreSQL. API contracts, domain rules and migrations are unchanged.

## Historical local commands and results — pre-#117

```powershell
.\mvnw.cmd spotless:apply verify
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\target\security\osv-scanner.exe scan source -L target/bom.cdx.json --all-packages --format=json --output-file=target/security/osv-final.json
python scripts/verify_dependency_scan.py target/bom.cdx.json target/security/osv-final.json
python -m unittest discover -s scripts -p 'test_*.py'
```

Maven verify: BUILD SUCCESS; 211 tests, 205 passed, six PostgreSQL tests skipped without Docker, no failures/errors. Spotless and JaCoCo passed. SBOM generation succeeded; OSV exit 0; all 127 resolved dependencies accounted for and no vulnerabilities reported. Four Python tests passed, including existing PostgreSQL-report gate fixtures.

Startup smoke: `java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18089`. Embedded Tomcat started; GET `/api/v1/health` returned 200 and unauthenticated `/api/v1/workspaces` returned 401. The owned test process was stopped afterward.

The initial OSV run exited 1 for findings. One inventory generation attempt used a PowerShell argument without quoting and was corrected. Attempting advisory-mentioned Tomcat 10.1.58 failed dependency resolution; only published 10.1.59 is committed. No scan of unresolved artifacts is treated as verification.

## Files and scope

- `pom.xml`: maintenance BOM update and security overrides.
- `.github/workflows/verify.yml`: dependency-security job, verified scanner and restricted report artifacts.
- `scripts/verify_dependency_scan.py`: completeness and clean-report checks.
- `scripts/test_verify_dependency_scan.py`: positive and negative gate fixtures.
- `README.md`, `DEVELOPMENT_PLAN.md`, `DEVELOPMENT_PLAN_RU.md`, `docs/verification.md`: delivered scope and remaining enforcement.
- `docs/dependency-security.md`: this report and operational contract.

## Remaining gates

A clean OSV result is time-specific and covers known Maven dependency advisories, not application SAST, all Maven plugin/Action dependencies, OS/JDK/container vulnerabilities, malicious packages or production readiness. Build tools themselves remain supply-chain review work. Report absence is not proof of safety. No recurring scan is configured; new PR/push/manual runs query fresh advisory data.

Task 0.2 remains partially complete until required checks are technically enforced before merge. The GitHub integration cannot read administration-protected branch settings; the repository rulesets endpoint reported a private-repository plan restriction. No paid upgrade, visibility change, merge or deployment is performed. Until enforcement is available, review both `verify` and `dependency-security` before a manual merge; this procedure is not equivalent to branch protection. Deployment artifacts in #8 remain a separate release task.

Local PostgreSQL and production/Clerk configuration were not verified by the historical commands above. The #117 full Docker/PostgreSQL result is recorded below; live production/Clerk qualification remains external. Remote CI results for the exact committed revision are tracked in #8/#107/#87 and PR #113.

## Concepts to study

- SBOM records the versions actually selected by dependency resolution.
- Transitive dependencies can introduce vulnerabilities even when direct dependencies are unchanged.
- Fail-closed gates distinguish a clean result from an unavailable/incomplete scan.
- BOM alignment keeps related framework modules compatible; patch overrides still require regression testing.

## Remote verification

[CI 34650200603](https://github.com/Sanyoghk/Cadence/actions/runs/34650200603), commit `146316c`: both `verify` and `dependency-security` passed. `sh ./mvnw --batch-mode --no-transfer-progress clean verify` completed with 211 Java tests, failures/errors/skips 0, including six PostgreSQL tests. OSV reported no vulnerabilities across all 127 SBOM dependencies. Scanner SHA-256 verification, four Python gate tests, inventory match and artifact upload passed. Issues #8/#87/#107 and draft PR #113 reflect these results. Task 0.2 remains partial for branch-check enforcement only; deployment and broader scanners remain separate.

## Issue #117: повторная проверка 2026-10-01

Новый полный SBOM включает 165 runtime/test components. Первый scan обнаружил
5 advisories в четырёх packages: HttpClient5 5.5.2 (connection leak/DoS),
HttpCore5 и HttpCore5-h2 5.3.6 (header/HPACK memory exhaustion),
Jackson Databind 2.21.6 (forward references/raw type IDs DoS). Никаких suppressions
не добавлено. Boot property overrides обновлены до HttpClient5 5.6.3,
HttpCore5 5.4.3 и Jackson BOM 2.21.7. AWS SDK 2.55.7 pinned отдельно;
он использует собственный проверенный S3 signing через SDK, не custom crypto.

Primary release records: [HttpClient 5.6.3](https://raw.githubusercontent.com/apache/httpcomponents-client/rel/v5.6.3/RELEASE_NOTES.txt),
[HttpCore 5.4.3](https://raw.githubusercontent.com/apache/httpcomponents-core/rel/v5.4.3/RELEASE_NOTES.txt),
[Jackson 2.21.7](https://github.com/FasterXML/jackson-databind/releases/tag/jackson-databind-2.21.7).
Фактически выполненные final commands:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean spotless:apply verify
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\tools\osv-scanner.exe scan source -L target/bom.cdx.json --all-packages --format=json --output-file=target/osv-results.json
python scripts/verify_dependency_scan.py target/bom.cdx.json target/osv-results.json
.\var\tools\osv-scanner.exe scan source -L docker/evidence-renderer/requirements.txt --all-packages --format=json --output-file=var/issue117-python-osv.json
python -m unittest discover -s scripts -p 'test_*.py'
```

Final Maven: BUILD SUCCESS, **593 tests**, failures/errors **0**, **3 Windows-only filesystem
skips**. Все три реально выполнены в Linux suite **35/35, 0 skipped**; required PostgreSQL,
MinIO, ClamAV и renderer suites не пропущены. Python report/diagnostics scripts **10/10 passed**;
renderer boundary suite в Linux **29/29 passed, 0 skipped**. Spotless и JaCoCo checks passed,
line coverage **93.26%**. Подробнее — [verification](verification.md) и
[completion report](issue-117-completion.md).

Remote [CI 36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248),
source commit `ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab`: `verify` и
`dependency-security` **passed**. Linux Maven **593/593**, failures/errors/skips **0**;
scripts Python **10/10**, renderer Python **29/29**. Fresh remote dependency inventory
check подтвердил все **165 Maven components**, OSV findings **0**; pinned scanner hash,
mandatory report gates и enforcing renderer AppArmor/preflight/sandbox прошли.

Final CycloneDX inventory содержит **165 resolved Maven runtime/test components**;
OSV scan exit 0, findings 0, complete inventory/report comparison passed. Separate requirements
scan exit 0, findings 0 для **4 unique PyPI packages**: Playwright 1.63.0, greenlet 3.5.6,
pyee 13.0.1 и typing-extensions 4.16.0. OSV включил typing-extensions также в дополнительный
source record; уникальных packages четыре. Это анализ requirements, а не scan Debian/Chromium
OS packages или MinIO/container images.

Сохранены local artifacts `var/issue117-bom.cdx.json`, `var/issue117-osv-results.json`,
`var/issue117-python-osv.json` и соответствующие scan logs. No suppression, allow-empty,
severity exclusion или bypass не добавлены. Clean report ограничен advisory database и
проверенными resolved versions на момент scan; SAST/container scans и production qualification
остаются отдельными release gates. Ранние counts и CI links выше — historical snapshots.
