# #121 — whole-issue review07, 2026-10-15

## Итог и граница завершения

**#121 остаётся OPEN; задание07 не qualified для closure/push.** Review выполнен на
`c19abc0db35074b0a02b166c5a886d8a2a3d2e28`, ветка `mvp`, clean/ahead31 до изменений.
Текущие GitHub [#121](https://github.com/Sanyoghk/Cadence/issues/121),
[#107](https://github.com/Sanyoghk/Cadence/issues/107) и
[#120](https://github.com/Sanyoghk/Cadence/issues/120) прочитаны 2026-10-15.
Unchecked boxes не использованы как доказательство отсутствия реализации: source06,
Identity lifecycle и supported evidence consumers фактически присутствуют и local qualified.

Но prerequisite «все required #121 scopes delivered» не выполнен. В утверждённом пользователем
[ADR0021/P2](adr/0021-source-kind-ai-eligibility.md) явно сказано:

> PNG/JPEG uploads и URL screenshots остаются обязательными MVP inputs,
> но визуальное понимание имеет состояние REQUIRES_PROVIDER.

Тот же P2 требует «не объявлять #121/MVP полностью завершённым».
[ADR0024](adr/0024-visual-evidence-provider-boundary.md) подтверждает отсутствие visual runtime.
Ссылка на #120 означает dependency, а не разрешение исключить visual gap из closure #121.
Доставленный docs-only visual contract05 и textual consumer06 это условие не снимают.

## Что было сделано

Сопоставлены все девять актуальных acceptance criteria #121 с code/API/ADR/tests и
утверждённым product scope. Проверены source selection, actor/scope binding, physical
verification, immutable history, migration/runtime boundaries и CI workflow.
Исправлены два устаревших утверждения в `brand-identity-api.md`, будто новая Strategy ещё
использует legacy signature и approved Identity consumer отсутствует. Синхронизированы
оба плана и ledger:06 qualified,07 blocked для closure, visual runtime не delivered.
Product code, dependencies, migrations, runtime grants и tests этим review не менялись.

## Acceptance matrix

«Local confirmed» ниже означает inspection неизменённого source и квалификацию06 от
2026-10-14, проверенную по сохранённым reports/hash manifest; это не новый test run15 октября.

| # | Требование GitHub #121 | Code / API / ADR | Tests / evidence | Actual status |
| --- | --- | --- | --- | --- |
| 1 | Reviewed contracts: editable fields, lineage, citations, approval actor/time; visible product gaps | [ADR0012](adr/0012-brand-identity-drafts.md), [0016](adr/0016-evidence-identity-provenance.md), [0021](adr/0021-source-kind-ai-eligibility.md), [0026](adr/0026-approved-identity-strategy-source.md); [Identity API](brand-identity-api.md) | Domain `BrandIdentity`, bounded content; `BrandIdentityApiContract`; visual port inspection | Contracts delivered; mandatory visual implementation BLOCKED |
| 2 | OWNER/membership at Controller/use-case/repository; no Entity/private references/secrets | `BrandIdentityController`, `BrandIdentityUseCase`, `IdentityEvidenceUseCase`, `BrandIdentityRepository`; `CadenceIdentityService` resolves actual account; scoped repository reads; separate DTOs | `BrandIdentityApiContract` anonymous/reserved roles/foreign IDs/scope forgery; actual approval account/membership negatives; `OpenApiContractAssertions` | Local confirmed for implemented operations; no claim of visual endpoint support |
| 3 | expectedVersion, immutable approval, approved edit→new DRAFT, common errors | `BrandIdentity.replaceContent/approve/createDraftVersion`, `BrandIdentityUseCase.update/approve`, V28/V29 | `PostgreSqlBrandIdentityApiTest`, `BrandIdentityApiContract`, migration tests; concurrent edit/approval, immutable collections/audit | Local confirmed |
| 4 | Bounded server-selected citations, tenant/source checks, untrusted model output | `BrandAnalysisTransactions.startEvidence`, `BrandAnalysisCommand.validateCitations`, `AnalysisCitation`, `BrandIngestService`; ADR0015/0016/0023/0025; OpenAPI | `EvidenceAnalysisApiContract`, `IdentityEvidenceApiContract`, `UrlAnalysisApiContract`, `SocialReviewApiContract`; foreign/nonexistent/path/quote/budget negatives | Local confirmed for TEXT/PDF/URL/social text; visual observations/citations absent |
| 5 | Exact APPROVED Identity source for Strategy; fresh eligibility; fail-closed | `StrategyBuildTransactions.snapshot/preflight/current/complete`; `ApprovedIdentitySource`; `EvidenceIdentitySourcePolicy`; `IdentitySourceVerification`; V42; [Strategy API](strategy-api.md) | PG Strategy build22/22, HTTP27/27; mixed source guards; `StrategyApiFailClosedTest`; latest DRAFT/approval/access/archive/deadline/withdrawal races | Local confirmed; manual/declared approved roots supported without invented evidence |
| 6 | Strategy/history/evidence nonregression; V1–V27 unchanged; no fake legacy approval | V28–V42 additive migrations; existing Strategy revision lineage; nullable legacy generation source; preserved text-evidence-v1/url v2/social v3 digests | `PostgreSqlStrategyMigrationTest`, `PostgreSqlApprovedStrategyMigrationTest`, Identity populated migrations; `withdrawalDuringExternalCallSupersedesWithoutPublication` preserves TEXT SUPERSEDED | Local confirmed; `git diff origin/mvp` lists only added V28–V42 migrations |
| 7 | Real PostgreSQL/runtime/deferred/concurrency verification | [runtime grants](../docker/postgres/runtime-permissions.sql); SQL parent/source/audit guards; Account→Workspace→Brand→revision order | Required PG reports; `FullEvidenceProviderIntegrationTest`10/10 restricted runtime; new V41→V42 test1/1; account-revocation wait test; PDF68/68 and URL52/52 PG suites | Local confirmed for implemented paths; visual SQL/runtime/concurrency not implemented |
| 8 | Compilation/Spotless/domain/API/security/full mandatory reports/startup + exact-SHA CI | [workflow](../.github/workflows/verify.yml): jobs `verify` and `dependency-security` (no separate security.yml) | Historical full131 suites/1309 tests,0failures/errors,3Windows skips covered Linux35/35; Python10/10,renderer29/29; startup200/401 | Local06 confirmed; whole-issue final gates/remote exact-SHA CI PENDING |
| 9 | Docs/remaining scopes/#107 synchronized; Jobs/budgets/full Playbook explicit | Both plans, [ledger](mvp-prompt-progress.md), [report06](issue-121-approved-strategy-slice.md), this matrix; [GitHub drafts](issue-121-review-github-drafts.md) | Docs content/links/fences/diff checks; current remote issues inspected | Local docs synchronized; remote synchronization deferred until required gates; #107 remains open |

## Source scope: что именно отсутствует

| Representation | Фактический consumer | Current status |
| --- | --- | --- |
| TEXT | Exact immutable review, sourceVersion-wide withdrawal, generation/citations/Identity/Strategy checks | Implemented/local qualified |
| PDF_TEXT | Reviewed extraction with original artifact/scan/provenance; retained bytes checked | Implemented/local qualified; это не OCR |
| URL_TEXT | Bounded title/description/extracted text; exact acquisition/extraction/scan; private retained HTML verification, no review fetch | Implemented/local qualified |
| SOCIAL_TEXT | Instagram/TikTok reviewed projection, original provider-origin/account binding, expiry and withdrawal | Implemented/local qualified; live onboarding #32/#33 не квалифицирован |
| Uploaded PNG/JPEG | Bytes/scan/retention ingestion exists; no semantic extraction | REQUIRES_PROVIDER; mandatory visual gap |
| URL screenshot | `EvidenceRenderer.Screenshot`: PNG bytes, dimensions, version; no observations/OCR | REQUIRES_PROVIDER; mandatory visual gap |

Для visual отсутствуют выбранный/qualified interpretation provider или OCR engine, executable
review/admission consumer для этой representation, validated interpretation output/citations,
integration с generation/Identity/Strategy и соответствующие positive/forbidden/SQL/concurrency
tests. Отдельно необходимы processor/region/transmission/retention/deletion policy, decoder
isolation/limits и controlled live qualification. CLEAN/FETCHED и screenshot1280×720 не дают
consent на AI и не подтверждают visual understanding.

Самостоятельно выбирать provider/privacy или убирать approved inputs нельзя. Техническая
рекомендация ADR0024 сохраняется: vision для visual style; OCR-only не покрывает palette/layout.
После product/provider решений нужна полноценная implementation/qualification. Альтернатива
изменить именно closure boundary #121 требует явного изменения P2 пользователем; этот review
такого решения не принимает и не считает blocker закрытым.

## Архитектурное решение и Security Review

Существующий flow и guards сохранены. Перенос visual gap в формальный «out of scope» только
ради зелёного issue противоречил бы утверждённому P2. Безопасная граница — показать implemented
textual capabilities отдельно от недоставленного visual consumer и оставить issue открытым.

| Threat | Protection | Verification / limitation |
| --- | --- | --- |
| BOLA/forged scope/actor | Server scope, OWNER method security, actual approval/generation actor checks; parent-first locks | Scoped repositories/use cases и negative tests inspected; historical qualification preserved |
| Stale consent/source publication | Exact immutable receipt, before-egress and completion eligibility, physical checks outside DB transaction, fresh metadata after I/O | Existing TEXT withdrawal/SUPERSEDED regression и mixed URL/PDF/social tests inspected |
| Forged output/citations | Source IDs selected by server, exact quote/field checks, bounded output, no model fetch/tools | `BrandAnalysisCommand`, `AnalysisCitation`, strict output adapter path inspected |
| History rewrite/SQL bypass | Immutable approval/lineage/provenance, FK/check/trigger guards, least-privilege runtime | V29/V35/V42 and runtime grants inspected; PG report gate revalidated |
| Visual privacy leak/fabricated interpretation | No semantic fallback from filename/metadata; no unapproved external egress; REQUIRES_PROVIDER | Current renderer port returns bytes only; future visual runtime is NOT verified |
| False completion evidence | Match qualified source hashes; distinguish historical tests from new checks; no old CI credited to current SHA |509 hashes match; no push/new CI claim; explicit BLOCKED matrix |

Новых endpoints, auth/CSRF/CORS rules, SQL/command construction, logging, dependencies или
secrets не добавлено. Existing process capacity/deadlines не являются distributed budgets.
Inspection не является доказательством отсутствия всех bugs или production security audit.

## Runtime Flow

```text
Reviewed supported evidence → bounded analysis + validated citations
  → Identity DRAFT → explicit approval → immutable APPROVED revision
  → Strategy capture → current/physical preflight → model outside locks
  → current/physical completion → atomic Strategy DRAFT + exact source receipt

Image/screenshot bytes → retained artifact/scan → REQUIRES_PROVIDER (no AI interpretation)
```

## Проверки этого review

Выполнены Git status/log/diff, inspection code/API/ADR/workflow и current GitHub reads.
Python SHA-256 сравнение с `var/prompt06-source-manifest.json`:509/509 совпадений.
Повторно прочитаны131 сохранённых XML reports:1309 tests,0failures/errors,3skips.
Это результаты14 октября, не новый запуск Java tests15 октября.

Команды, реально выполненные15 октября для сохранённых reports из `var/prompt06-verification`:

```powershell
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
```

Оба PASS: mandatory reports присутствуют, required integration suites без skips/failures.
Дополнительно выполнен root `.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check` — PASS.
Docs links/fences, source/migration hashes, plan parity и `git diff --check` проверены
командой `python -X utf8 var/prompt07-review-checks.py` и Git: **PASS**,6 Markdown files,
138 relative links,509 source hashes unchanged,V1–V27 Git blob hashes unchanged,оба plans
согласованы. Проверены Markdown fences и отсутствие product changes.
Новый full Maven/Docker/Python unit/Linux/startup run не запускался: whole-issue prerequisite
не выполнен, product source не менялся. Успешные historical runs не названы свежей qualification.
После доставки visual scope обязательны новый full run и exact-source-SHA CI.

## Git / CI / external payload

- Новый pushed SHA: **нет**, push не выполнялся по whole-issue policy.
- Verify/dependency-security CI URLs/results для нового SHA: **нет**, запуск/успех не заявляются.
- #121 и #107: прочитаны как OPEN; не закрывались и не редактировались этим review.
- Local review commit: `docs(review): record issue 121 acceptance and visual blocker`.
- Remote payload подготовлен в отдельном draft; пользователь требует synchronization после
  actual gates, это условие пока не выполнено. Auto-review rejection не было.
- Merge/deploy и другие номера не начинались.

## Изменённые файлы

1. `docs/issue-121-whole-review-2026-10-15.md` — matrix, evidence, blocker, verification boundary.
2. `docs/issue-121-review-github-drafts.md` — точные append-only status drafts для #121/#107, не отправлены.
3. `docs/brand-identity-api.md` — исправлены obsolete Strategy statements по V42.
4. `docs/mvp-prompt-progress.md` — review07 recorded, closure blocked.
5. `DEVELOPMENT_PLAN.md` — actual current continuation07 и P2 dependency.
6. `DEVELOPMENT_PLAN_RU.md` — идентичные status/dependency changes.

## Непроверенные аспекты / remaining scopes

Visual runtime и связанные security tests не существуют; provider/privacy/decoder decisions
и credentials не выбраны. Real Brand/Strategy adapters #120, OAuth #32/#33, durable AI Jobs,
budgets/idempotency, full Playbook, frontend и commercial deployment остаются незавершёнными.
Jobs/budgets/full Playbook явно выделены #121 как follow-up; P2 visual closure blocker
нельзя приравнять к этим исключениям. Fresh vulnerability/SAST/container scans, hosted
AppArmor/IAM/TLS и remote CI для c19abc0 этим review не выполнялись.

## Что важно понять / что изучить

Доставленная часть может быть корректной, а whole issue — незавершённым. Тест отказа для
image доказывает deny-by-default, но не поддержку image. Review contract описывает будущий
consumer, но не подменяет implementation.

1. Матрица соответствия (requirements traceability): каждому требованию соответствует код и доказательство.
2. Критерий завершения (acceptance boundary): local06 не равен whole #121 и не равен production MVP.
3. Происхождение проверки (verification provenance): passing CI должен относиться к точному pushed SHA.
4. Явный безопасный отказ (fail-closed): отсутствие provider блокирует вызов, а не включает fake.

## Production considerations и достоверность

Факты: current source/ADRs/remote issues inspected,509 qualified hashes совпали, reports
revalidated, obsolete API statements исправлены. Инженерный вывод: реализованная textual
цепочка не снимает explicit visual closure blocker. Assumptions/непроверенное: future provider
и deployment guarantees отсутствуют; безопасности live visual path этот review не доказывает.
Для продолжения нужны product/provider decisions либо явное новое решение пользователя о
closure boundary; молчаливый отказ от images/screenshots не допускается.
