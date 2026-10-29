# GitHub PDF review sync draft — approved и опубликован

Точный block ниже предназначен для добавления к descriptions обоих issues:
[Sanyoghk/Cadence #121](https://github.com/Sanyoghk/Cadence/issues/121) и
[Sanyoghk/Cadence #107](https://github.com/Sanyoghk/Cadence/issues/107).
Title/state/labels сохраняются. Push/merge/deployment не входят в это действие.

Предыдущая automatic approval review потребовала отдельное подтверждение internal progress
payload и destinations. Предыдущий ответ «Разрешаю оба updates» покрывал Identity block,
который уже опубликован. Пользователь отдельно ответил «Разрешаю оба PDF updates» для этого
точного block и обоих destinations. Block добавлен в оба descriptions; нового rejection не было.
Secrets/credentials/real user content отсутствуют. Core feature/test work завершена независимо
от approval external записи. Перед записью прочитаны current descriptions; block добавлен
ровно один раз. Readback подтвердил exact body = old body + согласованный block и сохранение
title/state/labels в обоих issues. Оба issues остаются open; push/merge/deployment не выполнялись.

## Точный progress block для обоих descriptions

<!-- pdf-review-2026-10-13:start -->
## #121 — private PDF_TEXT review preview, 2026-10-13

Законченный local slice `53c77de4e95d48280e1d1a63dc2714aea5161f5b` на `mvp`.
Push/remote exact-SHA CI не выполнялись. Whole issue остаётся IN PROGRESS.

- ADR0017: scoped actual active OWNER/member GET document-review для FILE/application/pdf/PENDING.
  Exact current PDF_TEXT artifact + original/latest CLEAN scans + REFERENCED tenant ledger;
  validated bounded text/checksums, exclusive scan expiry, physical size/header/SHA-256 checks.
- Private storage read вне DB transaction; Account-first capture/fresh finalize с access,
  lifecycle/version/scan/ledger rechecks и30s deadline. Два process-local concurrent slots,
  safe503 при busy/corrupted/unavailable bytes; DTO/no-store без storage key/reference.
- Requires human review; admittedToAi=false. No persisted review/admission, READY transition,
  model call или schema changes; V1–V35 unchanged. TEXT consumers остаются TEXT-only.

Final `spotless:apply clean verify` BUILD SUCCESS `2026-10-13T23:48:07+01:00`:
112 suites/982 tests,0 failures/errors,3 Windows filesystem skips;979 executed pass.
Domain7/H2PDF18/PGPDF19/OpenAPI3 PASS. Mandatory PostgreSQL/MinIO/ClamAV/renderer/full provider
pipeline report gates PASS без skips;Python10/10,renderer29/29,Linux35/35 covers Windows skips.
JaCoCo94.67%,Spotless/compilation/JAR PASS;packaged local health200/anonymous PDF GET401.
Cross-tenant/forged actor/revocation, source/archive/scan changes during I/O, latest non-CLEAN,
expired scans, corrupt/missing bytes, bounded concurrent reads/slot release проверены.
Local report: `docs/issue-121-pdf-review-slice.md`;API/OpenAPI/оба plans обновлены.

**#121/#107 остаются open. Next:** persisted PDF review/admission selectors и consumer/approval
fences → URL/images/social eligibility policy → APPROVED Identity→Strategy.
Real adapters#120,AI Jobs/budgets/full Playbook отдельно. Hosted/live providers, distributed
quotas/gateway limits,privacy/retention,fresh vulnerability scans/commercial deployment не
квалифицированы local run. CLEAN/checksum не доказывают truth или полную безопасность PDF.
Merge/deployment отдельно.
<!-- pdf-review-2026-10-13:end -->
