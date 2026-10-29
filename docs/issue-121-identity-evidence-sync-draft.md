# GitHub sync draft — approved и опубликован

Дата slice: 2026-10-13. Первоначальная automatic approval review потребовала отдельное
подтверждение verification/architecture payload и destinations. Пользователь явно ответил
«Разрешаю оба updates». Ниже точный approved progress block, добавленный к обоим descriptions.
Readback подтвердил previous body + этот block, title/state сохранены; patch менял только body.
Оба issues остаются open. Push/merge/deployment не выполнялись.

Destinations:
- [Sanyoghk/Cadence #121](https://github.com/Sanyoghk/Cadence/issues/121)
- [Sanyoghk/Cadence #107](https://github.com/Sanyoghk/Cadence/issues/107)

Внешняя запись содержит внутренний architecture/test status и local commit SHA. Secrets,
credentials, реальные user content или source texts в progress block отсутствуют.
Push, merge и deployment не входят в approval этого draft.

## Точный progress block для добавления в оба descriptions

<!-- identity-evidence-2026-10-13:start -->
## #121 — reviewed TEXT Identity provenance, 2026-10-13

Законченный local slice `ea2117f40e3d90b7423dfa5f6ecb08d16ab66fcb` на `mvp`; clean tree.
Push/remote exact-SHA CI не выполнялись. Whole issue остаётся IN PROGRESS.

- ADR0016/V35: explicit evidence receipt → Identity copy/read, immutable current copier audit,
  original input digest/admission links/quotations сохраняются через receipt.
- Actual active OWNER/membership, current source/version/text/checksum/withdrawal checks на
  copy и direct/inherited approval; reviewed Account-first locks. Approved-edit branch
  сохраняет same-Brand decreasing-version lineage и не позволяет обойти withdrawal.
- Legacy declared-only copy/manual contracts и history сохранены, audit не backfilled.
  Runtime UPDATE copiedBy не выдаётся. Withdrawal после approval сохраняет immutable history;
  future Strategy consumer обязан recheck eligibility. Exact quotes не доказывают truth/relevance
  или происхождение последующих manual edits.

Final `spotless:apply clean verify` BUILD SUCCESS 2026-10-13T22:49:34+01:00:
109 suites/938 tests,0 failures/errors,3 Windows skips;935 executed pass.
H2API17/17,PG24/24,migration1/1,domain2/2. Mandatory PostgreSQL/MinIO/ClamAV/renderer/full pipeline
gates PASS без skips. Python10/10,renderer29/29,Linux35/35 закрывает skipped symlink cases.
JaCoCo94.55%,Spotless/JAR PASS;packaged local health200,anonymous new POST/GET401.
SQL/runtime guards,actual Account revocation и обе approval/withdrawal race orderings проверены.
Local report: `docs/issue-121-identity-evidence-slice.md`; API/OpenAPI/оба plans обновлены.

**#121/#107 остаются open. Next:** non-TEXT eligibility → APPROVED Identity→Strategy.
Real adapters#120,AI Jobs(Task2.4),budgets/idempotency(Task2.5),full Playbook(Task3.3) отдельно.
Live AI/Clerk,privacy/retention/1000-hop lineage performance,свежие OSV/SAST/container scans
и hosted commercial qualification не подтверждены local run. Merge/deployment отдельно.
<!-- identity-evidence-2026-10-13:end -->
