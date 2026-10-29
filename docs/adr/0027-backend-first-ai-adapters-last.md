# ADR0027 — Сначала MVP backend, затем AI adapters

Дата: 2026-10-15. Статус: ACCEPTED — явное решение пользователя в чате.
Baseline: `1000ce0`; docs-only, runtime implementation не меняется.

## Решение и порядок

Сначала завершить provider-independent MVP backend: durable AI Jobs, budgets/idempotency,
полноту Strategy Playbook, Content/calendar/review/CSV, metrics/analytics,
recommendations/reports и соответствующие API, security, persistence и operational gates.
Следующая независимая работа — Task 2.4/2.5 и Task 3.3 в обоих DEVELOPMENT_PLAN;
их отдельные GitHub issue numbers ещё не назначены. Проверять actual prerequisites перед implementation.

После этого выбрать AI API/provider/model и реализовать adapters. #120 уже отслеживает
Brand/Strategy; будущие Content/Recommendation adapters требуют своего явного scope,
не считаются автоматически реализованными через #120. Frontend не является prerequisite
выбора AI API: текущий приоритет пользователя — backend.

#121 остаётся OPEN до выполнения оставшегося visual scope и whole-issue gates.
Этот blocker не останавливает независимые backend задачи. P2 из
[ADR0021](0021-source-kind-ai-eligibility.md) сохраняется: обязательные visual inputs
не удалены из MVP и не объявляются поддержанными. Задание07 не считается полностью завершённым.
Политика push/CI/closure не изменена; это решение не разрешает push незавершённого #121.

## Visual data flow

Утверждён целевой путь без обязательного отдельного AI/OCR preprocessing:

```text
Exact retained image / screenshot
    → bounded file validation + current access/scan/provenance + explicit review/consent
    → AI adapter: approved bytes + bounded server-selected context
    → structured Identity result
    → output validation + current permission/source/withdrawal/deadline rechecks
    → atomic Identity DRAFT publication
    → human review / approval
```

Внешний AI может анализировать изображение и генерировать Identity одним вызовом.
Backend не обязан сначала извлекать visual observations либо OCR text. Это уточняет
[ADR0024](0024-visual-evidence-provider-boundary.md): предложенные
`VisualInterpretationPort`/`VisualOutput` — возможная декомпозиция, не обязательные
отдельные production stages. Byte/checksum/tenant/source/actor/time binding и guards
сохраняются при прямом multimodal generation. OCR нужен только при отдельно обоснованном
сценарии; если создаётся OCR-derived text, его provenance/review rules сохраняются.
Existing PDF text extraction и TEXT/PDF/URL/social textual contracts не меняются.

## Граница доверия и отложенная qualification

Модель не получает arbitrary fetch/tools/private storage references. Source bytes и model
output недоверенные: backend ограничивает input/output, проверяет schema/provenance и
не исполняет текст из изображения как команды. External I/O выполняется вне DB locks;
после него проверяются актуальные права, sources и withdrawal перед публикацией.

До выбора provider нельзя утверждать supported formats/limits, visual citation semantics,
decoder safety или качество результатов. Processor/region/retention/deletion/cost policy,
credentials, adapter negative tests и live qualification остаются обязательными перед
активацией. Решение не разрешает live egress. Production fail-closed; fakes только local/test.
Provider-independent review/admission/lifecycle guards не откладываются автоматически:
их реализуют по утверждённым contracts, оставляя зависящие от API детали явно pending.

## Альтернативы и последствия

Ожидание provider сейчас задерживает независимые модули. Обязательная двухступенчатая
OCR/vision → Identity схема добавляет latency, стоимость и derived history без доказанной
необходимости. Выбран backend-first и прямой multimodal путь; цена — будущая адаптация
contracts к фактическим возможностям API и отдельная end-to-end qualification.
Завершённый backend без adapters не означает работающий production AI MVP.

## Проверка решения

GitHub #107/#120/#121 прочитаны: issues открыты. Remote bodies не синхронизированы этим
docs-only решением; источником нового порядка служит этот ADR и оба локальных плана.
Проверка: Markdown links/fences, parity порядка планов, `git diff --check`, Spotless.
Product tests не запускались: code/configuration/migrations не менялись.
