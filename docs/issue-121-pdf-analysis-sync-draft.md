# Точный progress block для GitHub #121 и #107

Destinations: descriptions https://github.com/Sanyoghk/Cadence/issues/121 и
https://github.com/Sanyoghk/Cadence/issues/107. Добавляется только block ниже к прежнему body;
title/state/labels сохраняются. Push/merge/deployment не входят в этот payload.
Статус: пользователь подтвердил «Разрешаю оба analysis updates»; block опубликован в обоих
descriptions. Readback подтвердил exact old body + block, title/state/labels сохранены.

<!-- pdf-analysis-2026-10-14:start -->
## #121 — reviewed PDF text analysis, 2026-10-14

Законченный local slice `d43af8ca01c81097b52e5c041bc0560f5b080f08` на `mvp`.
Push/remote exact-SHA CI не выполнялись; whole issue остаётся IN PROGRESS.

- ADR0019/V37: server-selected latest current TEXT/PDF_TEXT admissions,1–5 sources/20000 UTF-8
  bytes, existing explicit analyze-evidence action, immutable source links и exact citations.
  AnalysisTextSource/digest format text-evidence-v1 сохранены; old receipts не backfilled.
- PDF physical size/header/SHA-256 checks до модели и перед publication, exact artifact/
  original/latest CLEAN/expiry/ledger metadata; private storage I/O и model вне DB transactions.
  Fresh actual active OWNER/membership и entire-batch source/version/withdrawal checks.
  Internal PDF admission set сверяется с persisted links, чтобы не пропустить verification.
- Atomic signature/Brand version/citations/SUCCEEDED publication; stale/withdrawn/changed bytes
  или revoked access не заменяют signature. Process capacity2,physical batch30s,attempt60s.
  aiConsumptionEnabled=true означает отдельный backend consumer, не live provider.
  PDF Identity copy/approval остаётся fail-closed; source FILE/PENDING, production model disabled.
  V1–V36/runtime grants unchanged; V37 SQL independent metadata guard сохраняет TEXT behavior.

Final `spotless:apply clean verify` BUILD SUCCESS `2026-10-14T02:41:05+01:00`:
116 suites/1053 tests,0 failures/errors,3 Windows skips;1050 executed pass.
H2PDF47/47,PGPDF56/56,V36 upgrade1/1,capacity1/1,OpenAPI3/3;TEXT H2API15/PG21 PASS.
Mandatory PostgreSQL/MinIO/ClamAV/renderer/full pipeline reports PASS без required skips.
Python10/10,renderer29/29,Linux35/35 covers Windows skips;JaCoCo94.82%,Spotless/JAR PASS.
Packaged local health200,anonymous generation/PDF operations401. Mixed batch withdrawal during
PDF read stops model; scan/bytes/access/withdrawal during model stops publication; SQL latest
ERROR bypass rejected. Populated V36 TEXT attempt/source/citation rows preserved exactly.
Initial fixture/Mockito setup failures fixed without weakening assertions; final full covers fixes.
Local report: `docs/issue-121-pdf-analysis-slice.md`;API/OpenAPI/оба plans updated.

**#121/#107 remain open. Next:** PDF Identity copy/approval physical/lineage fences → other
non-TEXT eligibility → APPROVED Identity→Strategy. Real adapters#120,AI Jobs/budgets/full
Playbook separate. Live/hosted providers,distributed budgets/timeouts,privacy/retention,
fresh vulnerability scans и production rollout/recovery не qualified by local run.
Exact quote не доказывает truth/relevance; DB locks не защищают rogue storage deletion после
physical witness. Push/exact-source-SHA CI after whole issue; merge/deployment отдельно.
<!-- pdf-analysis-2026-10-14:end -->
