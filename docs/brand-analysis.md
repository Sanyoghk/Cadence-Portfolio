# Анализ бренда — issue #114

## Согласованный scope

Пользователь выбрал **port и production fail-closed; реальный adapter отдельным шагом**.
Production отклоняет анализ при отсутствии adapter. Local/test используют явно
изолированный deterministic fake. Это не завершает real AI Brand Identity generation,
citations, approval или весь Task 2.3 из master plan.

## Contract и граница доверия (trust boundary)

`BrandAnalysisPort.analyze` принимает immutable `BrandAnalysisCommand`: UUID попытки,
версии Brand/Workspace, нормализованные declared brand name/industry (до 255 characters).
Entity, credentials, URL fetch и quarantine/evidence content не передаются через port.

`BrandAnalysisResult` проверяет пять RGB colors, fonts (1–5, до 100 characters),
voiceUp/voiceDown (1–10, до 100), caption (до 1000), warnings (до 10 из enum),
provider/model/prompt identifiers (до 100, ограниченный alphabet). Текст не может
быть пустым или содержать ISO control characters. Lists копируются; palette immutable.
`toSignature()` создаёт новую domain representation без mutable aliases.
Raw provider JSON и diagnostics остаются в adapter. Ошибка или null output превращаются
в `BrandAnalysisUnavailableException` без исходного message/cause.

## Configuration

`cadence.brand-analysis.adapter`:

- `fake`: только local/test, без prod даже при mixed profiles.
- `disabled`: default вне local/test; HTTP 503 `brand_analysis_unavailable`.
- неизвестное значение: startup rejected без fallback в fake.

Fake package-private и не является scanned Spring component. Local/test configuration
выбирает его через environment guard. Production deployment обязан явно включать
`prod`; default development profile проекта — `local`.

## Runtime Flow

```text
Bearer + stored OWNER/membership
  → CadencePanelController
  → AnalyzeBrandUseCase (NOT_SUPPORTED)
  → BrandAnalysisTransactions.start (REQUIRES_NEW): scoped locks, RUNNING + source versions
  → BrandIngestService → BrandAnalysisPort: validated immutable command/result, без DB transaction
  → BrandAnalysisTransactions.complete (REQUIRES_NEW): version/archive/deadline recheck
  → signature replacement + SUCCEEDED receipt в одной транзакции
```

При ошибке port отдельная фаза `fail` записывает FAILED и сохраняет прежнюю signature.
Изменение Brand/Workspace, archive или истёкший deadline дают SUPERSEDED и HTTP conflict.
Каждая транзакционная фаза защищена OWNER/scope method guard. Блокировки идут
Workspace → Brand. Даже одинаковая signature publication увеличивает Brand version:
Hibernate может не выполнить обычный UPDATE, поэтому используется force increment.

## Ledger и invariants

V23 создаёт `brand_analysis_attempts`: UUID, source versions/checksum, started/deadline/
completed timestamps, status и только для SUCCEEDED — provider/model/prompt/warnings/
output checksum. Raw inputs и provider responses не сохраняются в receipt.
SHA-256 output использует length-prefixed fields и list counts: строки с запятыми и
скобками не создают неоднозначность. Checksum подтверждает представление данных,
не является криптографическим доказательством доверия к provider.

PostgreSQL защищает FK, неотрицательные версии, статус и обязательные поля,
одну RUNNING на бренд (partial unique index), 60-second publication deadline,
неизменяемый source и terminal receipts. Trigger проверяет actual DB clock и текущие
source versions при success. Runtime не может DELETE ledger или UPDATE source columns.

Истёкший RUNNING после остановки процесса восстанавливается лениво следующим запросом:
старый становится FAILED, новый получает другой UUID. Старый callback не может
завершить новую попытку. Ledger внутренний; новый public status endpoint не добавлен.

## Production considerations

Реальный adapter отложен. До его активации нужны bounded connect/read/overall timeouts,
concurrency/cost budgets, privacy review и ограничения retry. Publication deadline
запрещает запись позднего результата, но не прерывает произвольный код adapter.
Gateway rate controls и reviewed privileged archival/retention policy нужны до
production activation; runtime не получает blanket DELETE permissions.
DB outage во время failure recording может оставить RUNNING до deadline/recovery;
предыдущая signature остаётся целой. Backups/recovery, provider credentials/TLS и load
проверяются отдельно. CLEAN evidence не становится READY только из-за вызова анализа.

## Verification

[ADR-0009](adr/0009-brand-analysis-port-and-fenced-publication.md) объясняет alternatives.
[Completion report](issue-114-completion.md) содержит реальные команды, результаты,
security review, deployment limits и полный manifest файлов.

Отдельный production AI adapter отслеживается в [GitHub #120](https://github.com/Sanyoghk/Cadence/issues/120). Provider/model пока не выбраны.
