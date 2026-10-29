# Задание02 — source-kind eligibility, #121

Дата: 2026-10-14. Baseline `4f4faab9d1cd477f95d075072e7810ee9f70a3d8`, branch `mvp`.
Исходный worktree clean; prerequisite01 действительно qualified, local commit существует.
Статус: **02 QUALIFIED (architecture/product decision); P1–P3 явно утверждены пользователем
2026-10-14; ADR0021 ACCEPTED**.
Это docs-only scope. Номера03–05 не выполнялись.

## Что было сделано

[ADR0021](adr/0021-source-kind-ai-eligibility.md) разделяет факт получения данных, malware scan,
human review и разрешение передать конкретный payload модели. Сопоставлены фактические URL
metadata/bytes/screenshot, uploaded images и official social snapshots с предлагаемыми contracts.
URL/social text приняты как SUPPORTED по policy после review; visual inputs сохраняются
как REQUIRES_PROVIDER. Эти статусы пока не означают реализованного consumer.

Свежие #121/#107/#120 прочитаны: удалённый progress отстаёт от local01 (PDF Identity); issues
остаются open. Provider/model/privacy решения #120 не приняты. Remote updates не выполнялись.

## Как это работает

Production код не менялся. Сейчас TEXT/PDF имеют reviewed consumers; URL metadata и screenshots
получаются через ingest. PNG/JPEG не дают visual insights. Social canonical JSON хранится как TEXT:
это позволяет пройти обычный TEXT contract без typed origin/binding revalidation. ADR открыто
фиксирует gap; реализация typed dispatch и regression tests относится к05, не маскируется docs.

Предлагаемый admission привязывает согласие к exact representation/hash/version/provenance.
URL URL-registration hash не совпадает по смыслу с hash fetched bytes. Social validated origin
подтверждается server receipt, а не JSON полем. Human review не разрешает автоматически external AI.

## Архитектурное решение

Выбрано разделение source identity, immutable representation и permission receipt. Повторное
использование существующих bounded text ports уменьшает complexity; новые field-path citations
требуют versioned contract. Отправка raw HTML/JSON после CLEAN отвергнута: раскрывает лишние поля
и не доказывает consent. Vision/OCR сравниваются отдельно; OCR не заменяет visual style analysis.
Provider и коммерческая privacy policy не выбирались. Trade-off: images пока остаются явным gap.

## Security Review

- Threat: BOLA/revoked access. Protection: scoped OWNER + fresh guards на всех use boundaries.
  Verification: inspected current architecture; будущие negative/race tests перечислены в ADR.
- Threat: forged provider provenance и generic TEXT downgrade. Protection: proposed origin-first
  dispatch/no fallback + same-account binding. Verification: inspection RegisterSocialBrandEvidenceUseCase,
  EvidenceAdmission.reviewableText и repository подтвердил отсутствие typed guard; runtime fix ещё нет.
- Threat: altered bytes/stale scan/withdrawal. Protection: proposed exact physical witness,
  immutable receipt и latest scan/final transaction checks. Verification: ADR mapping к текущему PDF
  pattern; tests новых URL/social consumers пока не выполнены.
- Threat: SSRF/prompt injection/privacy leak. Protection: no consumer refetch/tools, bounded data,
  exact citations/human approval, local provenance identifiers, production disabled. Verification:
  inspected WebsiteMetadata/OfficialSocialEvidenceSource/BrandAnalysisConfiguration; docs-only scope.
- Threat: resource exhaustion. Protection: bounded source count/UTF-8 payload/private I/O; visual
  decoder qualification required. Verification: inspected current limits, future cases в ADR.

Новые dependencies, auth configuration, logs и DB schema не изменены. Эти proposed protections
не объявляются действующими runtime security guarantees.

## Изменённые файлы

1. [ADR0021](adr/0021-source-kind-ai-eligibility.md): decision matrix, exact contracts, retention,
   threat model, alternatives и acceptance boundaries03–05.
2. [ADR index](adr/README.md): ссылки17–21, включая ранее отсутствовавшие PDF ADR.
3. [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md): source policy status/gaps/dependencies.
4. [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md): синхронный русский статус.
5. [Ledger](mvp-prompt-progress.md): prerequisite01 и qualification02 отдельно от implementation03–05.
6. Этот отчёт: evidence, проверки, ограничения и объяснение решения.

## Runtime Flow

Проектируемый: OWNER → typed snapshot → explicit review → current eligibility → physical checks
вне transaction → fresh preflight → bounded model port → validated output/citations → fresh
publication → explicit Identity copy/approval. Для social вместо выдуманного malware scan —
origin/hash/binding checks. Real model port остаётся disabled до #120.

## Тесты

Фактически выполнены:

```powershell
git status --short --branch
git rev-parse HEAD
python var/prompt02-docs-check.py
git diff --check
git diff -- DEVELOPMENT_PLAN.md DEVELOPMENT_PLAN_RU.md docs/mvp-prompt-progress.md docs/adr/README.md
```

Локальный validation helper в ignored `var/` проверил **6 documentation files,110 local file
links**, UTF-8 без replacement/NUL, accepted decision markers, синхронные plan markers и
отсутствие source/build changes: PASS. Remote URLs и Markdown anchors этим script не проверяются.
Diff check: PASS, whitespace errors отсутствуют. Content/security review выполнен по кодовым
источникам, указанным в ADR; оба новых документа прочитаны до commit. Helper не часть production
или committed test suite. Staged diff/scope отдельно проверены перед local commit.
Full Maven/Spotless/runtime suites в02 не запускались: production/build/config files не менялись.
1079 tests из01 — историческое prerequisite evidence, не новый тест ADR.

## Security Tests

Новых runtime tests нет. Для03–05 заданы обязательные negative cases: foreign IDs, current
membership/account revoke, switched social account, forged origin JSON, generic fallback,
latest ERROR после CLEAN, expiry в середине операции, tampered/missing bytes, withdrawal race,
UTF-8 overflow, invalid citations и отсутствие network calls при review/consumption.

## Edge Cases

Inspection учёл legacy extraction без доказанного receipt, одинаковый social snapshot после
повторного fetch (не обновляет capture time), пустые captions/biography, TikTok title,
image-only PDF, truncated website text, expired scans, orphan versus referenced retention,
withdrawal sourceVersion scope и inherited Identity provenance. Это contract analysis,
не результаты runtime исполнения этих сценариев в02.

## Непроверенные аспекты

URL/social typed admission и visual consumers ещё не реализованы. Vision/OCR качество, external
privacy/retention/region/cost и live provider qualification не проверены, потому что не выбраны.
P1–P3 утверждены; это разрешение contracts, а не external data transmission. Реализация проверяется
в03–05 и#120 PostgreSQL/concurrency/security/full gates и реальным deployment qualification.

## Что мне важно понять

Checksum доказывает идентичность bytes, но не их безопасность или разрешение использовать.
CLEAN относится к malware; provider receipt — к происхождению; OWNER review — к конкретному
snapshot. Все три нужны по своей причине и не заменяют друг друга. Data age, scan expiry,
binding expiry и storage retention — разные часы. Их смешивание приводит либо к stale use,
либо к обещанию удаления данных, которое система не выполняет.

## Что мне стоит изучить

1. Происхождение данных (provenance): immutable links отличают официальный snapshot от похожего JSON.
2. Минимизация данных (data minimization): модели нужны captions, но не все provider identifiers.
3. Гонка проверки и использования (TOCTOU): после I/O access/scan/withdrawal могли измениться.
4. Версионирование contracts (contract versioning): новые projections не меняют старые receipts.
5. Запрет при неопределённости (fail-closed): отсутствие provider/consent не заменяется fake success.

## Production considerations

Утвердить privacy/region/retention/deletion policy и qualified providers; реализовать typed social
guard, URL consumers и visual gaps; проверить storage IAM, decoder isolation, cost limits и
actual membership/binding races. Не обещать, что withdrawal удаляет ранее переданные данные.
Push/CI/issue closure отсутствуют: whole #121 не завершён. Local commit
`docs(evidence): approve source-kind AI eligibility policy`; точный SHA определяется через
`git log -1 -- docs/issue-121-source-policy-decision.md`, чтобы не создавать self-referential hash.

Факты: current source inspected,01 commit/prerequisite подтверждены; source/build не изменены.
Инженерные выводы: separate representations/receipts и no generic social fallback нужны для
сохранения consent/provenance. Assumptions: future implementation guarantees пока
не подтверждены выполненными runtime tests; provider/privacy решения остаются отдельными gates.
