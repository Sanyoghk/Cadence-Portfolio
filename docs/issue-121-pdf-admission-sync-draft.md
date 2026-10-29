# GitHub PDF admission sync draft — approved и опубликован

Точный block ниже предназначен для добавления к descriptions обоих issues:
[Sanyoghk/Cadence #121](https://github.com/Sanyoghk/Cadence/issues/121) и
[Sanyoghk/Cadence #107](https://github.com/Sanyoghk/Cadence/issues/107).
Title/state/labels сохраняются; push/merge/deployment не входят в это действие.

Automatic approval review ранее потребовала отдельное подтверждение internal progress payload
и destinations. Прежнее «Разрешаю оба PDF updates» покрывало уже опубликованный PDF preview
block. Пользователь отдельно ответил «Разрешаю оба admission updates» для этого точного
admission block и обоих destinations; block добавлен в descriptions #121/#107.
Payload содержит local architecture/test status и feature SHA, без secrets/real user content.
Перед записью прочитаны current descriptions; согласованный block добавлен ровно один раз.
Readback подтвердил exact old body + block и сохранение title/state/labels в обоих issues.
Оба issues остаются open; push/merge/deployment не выполнялись.

## Точный progress block для обоих descriptions

<!-- pdf-admission-2026-10-14:start -->
## #121 — explicit PDF admission history, 2026-10-14

Законченный local slice `730a491c7cb0dcb03cff4cf9efbf42af9166a2ce` на `mvp`.
Push/remote exact-SHA CI не выполнялись. Whole issue остаётся IN PROGRESS.

- ADR0018/V36: explicit reviewed PDF selectors, immutable shared consent/audit/history,
  artifact/original/latest scan IDs, source/parent versions, checksums, renderer/expiry/policy.
  POST document-review/admissions и GET current physical snapshot; actor/time/text server-owned.
  Unknown/missing fields400; actual active OWNER/member, scoped graph и exact selectors enforced.
- Shared bounded two-slot capture → private physical read вне DB transaction → fresh finalize;
  original/latest CLEAN, ledger/key/bytes, size/header/SHA-256, access/source/archive/expiry checks.
  Fresh scan/expiry context требует нового explicit consent; old receipts не переписываются.
- Existing append-only withdrawal блокирует sourceVersion во всех contexts/scans, доступен
  для stale/archived PDF; cap1000, replay409, policy/FK/partial UNIQUE/history lookup/SQL guards.
  Runtime INSERT/withdrawal allowed, UPDATE/DELETE denied. TEXT history preserved, new PDF fields
  NULL у legacy rows; V1–V35 unchanged. Source FILE/PENDING, aiConsumptionEnabled=false;
  current model/analysis/Identity guards остаются TEXT-only, provider не вызывается.

Final `spotless:apply clean verify` BUILD SUCCESS `2026-10-14T01:24:19+01:00`:
114 suites/1024 tests,0 failures/errors,3 Windows filesystem skips;1021 executed pass.
H2PDF34/34,PGPDF42/42,domain2/2,populated V35 upgrade1/1,OpenAPI3/3 PASS.
Mandatory PostgreSQL/MinIO/ClamAV/renderer/full pipeline report gates PASS без required skips;
Python10/10,renderer29/29,Linux35/35 covers Windows skips. JaCoCo94.78%,Spotless/JAR PASS.
Packaged local health200,anonymous PDF previewGET/admissionPOST/snapshotGET401.
SQL/runtime forgery/history denials,cap1000/1001,parallel same-consent exactly1,both held
admission/withdrawal orderings,expiry-aware policy re-review,stale/access/scan/I/O races checked.
Earlier targeted contract/fixture failures fixed; two exploratory full runs interrupted for
review fixes, не считаются qualification pass. Final full run above uses corrected source.
Local report: `docs/issue-121-pdf-admission-slice.md`;API/OpenAPI/оба plans updated.

**#121/#107 остаются open. Next:** PDF-aware model input/publication/Identity approval fences →
other non-TEXT eligibility → APPROVED Identity→Strategy. Real adapters#120,AI Jobs/budgets/full
Playbook отдельно. Live/hosted providers,distributed/user quotas,privacy/retention,fresh scans
и production-size migration lock/rollout/recovery qualification не подтверждены local run.
CLEAN/checksum не доказывают truth/полную безопасность PDF. Merge/deployment отдельно.
<!-- pdf-admission-2026-10-14:end -->
