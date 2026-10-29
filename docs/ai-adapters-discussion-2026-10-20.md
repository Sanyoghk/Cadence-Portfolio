# AI adapters для Cadence: рекомендации к обсуждению

> **Дополнение после расширения ТЗ20 октября:** эта сравнительная записка относится к
> прежнему demo basket и не оценивает весь ready-video/raw-editing/market/publishing scope.
> Video provider остаётся не выбранным; кандидат ниже не утверждён. £20 подтверждены
> для demo AI calls; прочие расходы оцениваются отдельно.
> [Новые требования и personalization](media-personalization-and-platforms.md),
> [следующий research gate V2-02](astra-medium-mvp-prompts-v2.md).

Дата проверки official docs:2026-10-20. **DRAFT / НЕ УТВЕРЖДЕНО.**
Это исследование по запросу пользователя, не решение о provider, расходах, privacy или
implementation. Цель backend MVP с реальным AI подтверждена; конкретная интеграция обсуждается.
Бюджет £20 консервативно трактуется как общий бюджет демо. Hosting/domain/storage и подписки
разработчика в расчёт API usage не включены; если £20 покрывает их тоже, доступная сумма ниже.

## Краткая рекомендация

Кандидат для первого демо: Anthropic API / Claude Sonnet5.5 для Identity, Strategy,
Content и AI recommendations; Google Nano Banana2.1 для изображений.
Haiku5.5 — кандидат для экономии на частых коротких rewrites после сравнения качества.
OpenAI GPT-6.1 Sol/GPT-6 Luna — альтернативы для контрольного сравнения; третий production
provider не нужен только ради небольшой разницы в цене текста.

Для видео выбор пока отсутствует: пользователь назвал NanoBanana, но официальная Google
модель генерирует изображения. Требуется уточнение, не имеется ли в виду отдельный
сторонний сервис. Если речь о Google, текущий кандидат — Gemini Omni Flash stable.
Это условная рекомендация, не замена выбора пользователя.

При указанном ниже примерном объёме текст и images укладываются в £20 с запасом;
видео и многократные regeneration быстро сокращают число доступных демонстраций.
Это расчёт по тарифам и предположенному usage, не измеренный результат Cadence.

## Два важных различия

**Claude Code** — coding agent: читает repository, изменяет files и запускает commands.
Для функций Cadence предлагается прямой **Claude API**: ограниченный request →
структурированный response. Запуск coding agent внутри обработки источников дал бы
лишние полномочия и более сложный учёт tool calls/расходов. Это архитектурная рекомендация,
а не утверждение, что agent SDK невозможно использовать.
[Claude Code overview](https://code.claude.com/docs/en/overview).

Claude Code subscription и API billing — разные способы использования. Нельзя считать,
что личная подписка автоматически оплачивает requests Cadence. В Console API расходует
API credits; условия возможных subscriber credits зависят от аккаунта и не включены в
наш бюджет. [API billing](https://support.claude.com/en/articles/8977456-how-do-i-pay-for-my-claude-api-usage),
[Code subscription/API distinction](https://support.claude.com/en/articles/11145838-use-claude-code-with-your-pro-or-max-plan).

**Nano Banana2.1** (`gemini-nano-banana-2.1`) — image generation/editing.
Google video model **Gemini Omni Flash** (`gemini-omni-1.1-flash`) принимает text/images/video
и выдаёт video; stable specification допускает3–10s. Если пользователь имел в виду
NanoBanana-сайт, его underlying provider/API/terms/prices нельзя выводить из Google docs.
[Nano Banana model](https://ai.google.dev/gemini-api/docs/models/gemini-nano-banana-2.1),
[Omni model](https://ai.google.dev/gemini-api/docs/models/gemini-omni-flash).

## Все AI области и привязка к коду

| Задача | Кандидат и причина | Current code / необходимая работа |
| --- | --- | --- |
| Brand analysis по declared fields/text/PDF/URL/social snapshot | Sonnet5.5: один multimodal model для synthesis и structured result; выбор согласуется с интересом пользователя к Claude | `BrandAnalysisPort`/`BrandAnalysisCommand`, existing citations. Live adapter отсутствует |
| Анализ изображений/screenshot → Identity | Sonnet5.5 vision с exact approved bytes + context; отдельный OCR не нужен для всего pipeline | Current `AnalysisTextSource` текстовый; новый visual contract/admission/consumer и #121 qualification, не отправка arbitrary URL |
| Strategy Playbook | Sonnet5.5; использовать immutable APPROVED Identity и выдавать JSON DRAFT | `StrategyGenerationService`; полноценный Playbook уже существует |
| Content captions/hashtags/scripts/media prompts | Сначала Sonnet5.5 для целого пакета; Haiku5.5 для коротких повторных edits только после eval | `ContentGenerationService`; model не назначает approval и не выдумывает готовые asset IDs |
| Evidence-linked recommendations | Sonnet5.5 получает рассчитанные сервером показатели и допустимые source IDs | `RecommendationEngine` сейчас heuristic; SQL/math/sample eligibility остаются deterministic. AI предлагает проверяемое изменение, apply→DRAFT |
| Image assets | Nano Banana2.1,1K для demo; Pro/high resolution сравнивать при конкретном дефекте качества | Новый real media producer. `DemoMediaSource` нельзя просто переименовать: необходимы bounded decode/normalization/scan/private delivery/review |
| Video assets | Pending clarification; при Google рассмотреть Omni stable, короткие720p клипы | Отдельный producer/job/poll/recovery/usage + безопасная обработка media. Сейчас только fixed demo MP4 |
| Content safety | Provider safety/refusal + server validation + обязательный human review; отдельный classifier при реальной необходимости | Malware scan и moderation — разные checks. Ни model self-rating, ни CLEAN не дают права публиковать автоматически |
| CSV, metrics, aggregation, report rendering | Дополнительная AI модель не нужна | `PerformanceCalculator`, CSV/report renderers сохраняют точные formulas/escaping. AI summary можно сделать из проверенных aggregates |
| URL/PDF extraction, screenshots | Сохранить existing bounded fetch/extractor/renderer | Не оплачивать LLM повторно за то, что корректно делает deterministic pipeline |
| Search, embeddings, RAG, fine-tuning | Не добавлять в demo по умолчанию | Existing bounded evidence достаточно для текущих contracts. Новые sources/retention/tools/costs требуют отдельного обоснования |

Sonnet поддерживает text/image input и text output, а structured outputs задают JSON
contract. Это делает его подходящим кандидатом, но не доказывает превосходство именно на
брендах Cadence. [Sonnet specification](https://platform.claude.com/docs/en/models/sonnet-5-5/overview),
[Structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs).

## Alternatives и trade-offs

| Вариант | Плюсы для Cadence | Цена решения / когда выбрать |
| --- | --- | --- |
| Claude для текста/vision + Google media | Соответствует предпочтению пользователя;2 provider accounts, shared text transport, отдельные domain contracts | Две data policies и billing systems; мой первый кандидат для обсуждения |
| Claude + Haiku для простых задач + Google media | Снижает цену частых rewrites без третьего provider | Ещё один model profile и eval; на небольшом demo экономия абсолютной суммы невелика |
| OpenAI Sol/Luna + Google media | Аналогичный balanced/low-cost выбор; Luna поддерживает structured outputs | Полезен для сравнения качества. Замена Claude не имеет доказанного преимущества без project eval |
| Google text/vision + Google media | Один vendor/billing; Gemini3.8 Flash принимает text/images/PDF/video | Всё равно разные model contracts. Качество Brand/Playbook нужно сравнить; единый vendor не устраняет privacy/source checks |
| Opus/Fable/тяжёлая agent chain для всего | Возможный запас для сложных reasoning задач | Больше expense/latency; для небольшого validated marketing workflow необходимость пока не доказана |

[OpenAI model catalog](https://developers.openai.com/api/docs/models),
[GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna),
[Gemini3.8 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash).
Оценка architecture/maintenance — инженерный вывод, не provider benchmark.

## Проверенные тарифы

USD, standard processing, без cache/batch discounts и дополнительных tools/налогов.
1M tokens — миллион единиц обработки текста/других входов; это не миллион слов.
Разные модели/языки используют разное число tokens для того же материала.

| Модель | Input /1M | Output /1M | Использование |
| --- | --- | --- | --- |
| Claude Sonnet5.5 | $2 | $10 | Основной text/vision кандидат |
| Claude Haiku5.5 | $0.10 | $0.50 | Только prompts≤100000 tokens; выше другой тариф |
| Claude Opus5.5 | $4 | $20 | Более дорогая альтернатива |
| GPT-6.1 Sol | $2 | $10 | Standard short-context альтернатива |
| GPT-6 Luna | $0.10 | $0.50 | Standard short-context budget альтернатива |

Источники: [Claude pricing](https://platform.claude.com/docs/en/about-claude/pricing),
[OpenAI pricing](https://developers.openai.com/api/docs/pricing).

Nano Banana2.1: image output1K — **$0.0336**,2K — **$0.0504**,4K — около **$0.113**,
плюс input/text/thinking. Omni stable720p:5792 output tokens/second × $17.50/1M,
то есть **$0.10136/second**,8s≈**$0.81088** только video output.
Veo3.1 preview и Omni preview обозначены deprecated/shutdown22 октября2026:
для новой integration их выбирать не предлагаю; GA Veo на другой Google платформе —
отдельный продукт с отдельной qualification.
[Google pricing](https://ai.google.dev/gemini-api/docs/pricing).

## Расчёт стоимости демо

Формула text usage:

```text
cost USD = billable input tokens × input rate / 1 000 000
         + billable output tokens × output rate / 1 000 000
```

В billable output учитывается оплачиваемый thinking, а не только видимый JSON.
Следующие counts — иллюстрация объёма, не результат замера. Предполагаются ограниченные
источники,1 model call на этап,12 публикаций на четыре недели, без search/tools/cache.
Image input tokens при vision должны войти в actual usage, а не исчезнуть из ledger.

| Этап | Input tokens | Всего billable output tokens | Sonnet USD |
| --- | --- | --- | --- |
| Identity | 10000 | 3000 | $0.050 |
| Strategy | 8000 | 6000 | $0.076 |
| Content,12 публикаций | 6000 | 8000 | $0.092 |
| Recommendations | 4000 | 2000 | $0.028 |
| Текстовый цикл | 28000 | 19000 | **$0.246** |

Если только Content перевести на Haiku при тех же token counts, весь цикл≈$0.1586.
Разница≈$0.0874 за цикл: для малого demo сначала стоит проверить Sonnet quality, а не
усложнять routing ради этой суммы. При многократных rewrites/большой аудитории экономия растёт.

12 image outputs1K≈$0.4032. Text+12images≈$0.6492.
Добавить3video outputs по8s720p: ещё≈$2.43264, итого≈$3.08184.
Это не гарантированная цена готового пакета: media inputs/thinking, retries, edits,
отказы после оплаченной обработки, налоги/конвертация и hosting могут увеличить итог.

Пример умеренного live test/demo usage:

```text
10 текстовых циклов на Sonnet      $2.46000
60 outputs изображений1K           $2.01600
6 outputs видео8s720p              $4.86528
Итого по указанным компонентам     $9.34128
```

Для понятного сопоставления используем **условный**, не проверенный рыночный курс
£1=$1.25: £20≈$25, приведённые $9.34≈£7.47. Это arithmetic assumption, не валютная
котировка и не обещание суммы списания с карты. Разумно планировать API usage до£16,
оставляя£4 на billing/FX/непредвиденный расход; actual provider balance и курс проверяются
перед оплатой. Удвоение указанного usage даст≈$18.68 до дополнительных расходов.

Возможное распределение£20 для обсуждения:£5 на сравнение/проверки,£4 на text workflow,
£3 на images,£4 на несколько video runs,£4 reserve. Это лимиты расходов, не обязательные
top-ups. Google paid setup предусматривает minimum$5; доступность models/quotas зависит
от account. [Google billing](https://ai.google.dev/gemini-api/docs/billing).

## На что влияет бюджет

- **Количество:** больше пользователей, brands, вариантов и regeneration означает больше calls.
  Сохранённый результат не нужно генерировать снова при каждом открытии страницы.
- **Модель:** можно платить за более сильный candidate на Strategy и дешёвый на простой edit.
  Цена не является доказательством качества; это проверяется на одинаковых inputs.
- **Контекст и длина:** длинные документы, много images, большой Playbook/JSON и thinking
  увеличивают billable tokens. Слишком маленький output limit даёт обрезанный ответ.
- **Media:** разрешение/длительность/число вариантов и edits сильно меняют расход.
  Просмотр сохранённого видео не требует повторной generation, но storage/traffic имеют свои расходы.
- **Скорость/параллелизм:** premium fast modes могут стоить дороже; queue позволяет ограничить
  concurrent spending. Малый баланс сам по себе не делает ответ одной и той же модели хуже.
- **Проверки:** live eval/smoke тоже расходуют бюджет. Unit/integration tests используют controlled
  fixtures; real calls выполняются отдельно в ограниченном количестве.
- **Account limits:** credits, spending caps и rate limits — разные механизмы.
  Наличие£20 не гарантирует нужный throughput или доступ к конкретному API.

Это не бюджет «на безопасность»: source validation, tenant isolation, explicit consent,
human approval и cost reservations не отключаются для экономии.

## Архитектура и Security Review

Proposed runtime: reviewed scoped sources → budget reservation → existing durable Job →
fixed provider transport → typed validated output → current source/access/lease recheck →
atomic DRAFT publication → отдельный human approval. Provider I/O вне DB transactions.

Threat: overspend после timeout/retry или параллельных calls.
Protection: резерв верхней стоимости перед dispatch, model/price/version snapshot,
durable measured usage и hold при неизвестном результате; отдельные quotas на media.
Verification: future parallel/replay/unknown-outcome/restart tests. У Google project spend caps
могут отставать из-за обработки billing; server ledger нужен независимо от dashboard.
[Billing cap caveats](https://ai.google.dev/gemini-api/docs/billing).

Threat: prompt injection/fabricated citations/model output меняет чужие данные.
Protection: никаких shell/tools/arbitrary fetch; server-selected source IDs, typed JSON,
domain/ownership/SQL invariants и DRAFT-only. Vision interpretation хранить как interpretation,
не как якобы точную текстовую цитату. Неизвестный font на screenshot нельзя выдавать за факт.
Verification: future malicious-input/foreign-ID/fabricated-quote/withdrawal/expiry tests.

Есть конкретная compatibility деталь Claude: native citations несовместимы с JSON
`output_config.format`; это не повод удалять Cadence citations. Сохраняем наши bounded
citation fields/source IDs/exact-quote validation. HTTP200 с `stop_reason=refusal` или
`max_tokens` не является успешной domain generation; нельзя автоматически отправлять запрос
другому provider для обхода refusal.
[Structured outputs limitations](https://platform.claude.com/docs/en/build-with-claude/structured-outputs).

Threat: private source data уходят лишним processors или остаются у provider.
Protection: минимальный approved context; provider allowlist и отдельная policy; no automatic
cross-provider fallback, no raw prompts/keys в logs, no optional data sharing.
Verification: review actual account/endpoint/retention settings до egress и contract/log tests.
No-training не означает no-retention:

- Anthropic commercial retention page описывает удаление API inputs/outputs в пределах30days
  с исключениями. Отдельные ZDR/feature/model arrangements нельзя считать активными автоматически.
  [Commercial retention](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data),
  [API feature retention](https://platform.claude.com/docs/en/manage-claude/api-and-data-retention).
- Google Paid Services не используют prompts/responses для product improvement; abuse-monitoring
  policy описывает55days retention. `store=false`/отказ от files/cache не равен автоматическому
  ZDR всей системы. Не включать optional sharing/grounding без необходимости.
  [ZDR guide](https://ai.google.dev/gemini-api/docs/zdr),
  [Abuse monitoring](https://ai.google.dev/gemini-api/docs/usage-policies).
- OpenAI API по умолчанию не использует данные для обучения без opt-in; abuse logs могут
  храниться до30days, state retention зависит от endpoint/настроек.
  [Data controls](https://developers.openai.com/api/docs/guides/your-data).

Для live demo предлагаются synthetic/явно разрешённые материалы, без ненужных личных данных.
Это предложение не является согласием пользователя на передачу его текущих files.
Google terms отдельно требуют Paid Services для API clients в UK/EEA/Switzerland;
account/region policy нужно проверить до использования.
[Current Google terms](https://ai.google.dev/gemini-api/terms).

## Как проверить выбор перед утверждением

Предлагаемый небольшой eval:3 synthetic brand briefs, разные tone/audience, один смешанный
text/image case и один intentionally insufficient-source case. Сравнить Sonnet с одной
альтернативой на одинаковых разрешённых данных. Для short rewrite сравнить Haiku с Sonnet.
Критерии: валидность schema/domain, реальные citations, соблюдение brand voice/constraints,
полнота Playbook, отсутствие fabricated facts, latency, actual tokens/cost, пригодность
результата после human review. Images оценить по prompt/brand consistency/readability;
video — motion/content consistency и проценту пригодных takes.

Не объявлять «лучший marketing model» по vendor benchmark. Ни paid eval, ни API accounts
в этом исследовании не запускались; предлагаемые проверки требуют отдельного обсуждения.

## Что выполнено / непроверенное

Выполнены repository inspection, чтение official model/pricing/privacy docs и проверка
арифметики: `python var/backend-mvp-planning/check_costs.py` — PASS,4 assertions.
Скрипт использует Decimal; никаких provider calls, оплаты, SDK installation или secrets.
Backend implementation/tests не изменены. Documentation checks записаны в
[backend completion discussion](backend-mvp-completion.md).

Не проверены account availability, actual latency/output quality/token usage, текущий FX,
налоги и точный video service пользователя. £20 — заявленный budget, не разрешение его потратить.

**Факты:** capabilities/prices/limitations опубликованы в процитированных official docs.
**Инженерные выводы:** two-provider candidate, Sonnet baseline, deferred Haiku routing,
server-owned numbers/citations/budgets и ограниченное video usage.
**Assumptions:** приведённые token counts, условный FX, число повторов и качество результатов.
**Ожидают обсуждения:** video service, model selection, data policy и integration stages.
