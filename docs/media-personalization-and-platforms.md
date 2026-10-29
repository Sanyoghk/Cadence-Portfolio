# Media, персонализация и внешние ограничения Cadence

Дата проверки источников:2026-10-20. Это исследование для [плана V2](astra-medium-mvp-prompts-v2.md),
не выбор provider и не разрешение расходов/передачи данных/публикации.
Пользователь подтвердил органику и £20 demo AI calls; остальные затраты оцениваются отдельно.

## Что значит «натренировать адаптер для клиента»

В коде Cadence adapter — компонент интеграции с внешним API. Обучают модель или небольшие
дополнительные веса, а adapter выбирает нужную версию/контекст и проверяет результат.
Эти операции нужно различать, чтобы не обещать обучение там, где передаются лишь references.

| Подход | Что меняется | Для генерации видео | Для монтажа | Рекомендация |
| --- | --- | --- | --- | --- |
| Brand profile + prompts | Versioned правила/примеры, веса модели прежние | Palette, shot guidance, negative examples, wording | Tempo, transitions, subtitles/audio preferences | Обязательная базовая персонализация |
| Reference conditioning | Изображения/видео направляют конкретную generation | Product/talent/style consistency в пределах модели | Reference style для выбора/доработки сцен | Проверить capability выбранной модели |
| Retrieval approved examples | Выбираются релевантные разрешённые примеры клиента | Подходящие примеры к текущему brief | Approved edit decisions для похожего footage | Начать с scoped lookup; vector DB не обязательна |
| Presets + structured edit policy | Настройки монтажа и выбор разрешённых операций | Assembly generated scenes | Повторяемый renderer с индивидуальными style rules | Практичная основа editing MVP |
| Fine-tuning text/planning model | Меняются веса модели, выдающей scripts/edit plans | Лучше следовать формату/голосу при достаточных данных | Учить mapping brief+scene metadata→validated EditPlan | После измерения baseline, если provider поддерживает |
| Video/image LoRA | Небольшие обучаемые добавки к совместимой базовой модели | Узкий visual subject/style/motion; не гарантирует exact product | Возможен style transform; не заменяет timeline editor | Отдельный эксперимент X01 |
| Full fine-tuning | Много весов и сложный training pipeline | Требует данных/compute/qualification | Не нужен только ради trim/reorder/subtitles | Не рекомендован как prerequisite первого MVP |

Runway описывает применение reference media для привязки генераций к product/brand/talent.
Это подтверждает возможность conditioning, но не tenant-specific training любого их API.
[Официальное описание references](https://docs.dev.runwayml.com/recipes/reference-media/).
Форматы, длительности и поддержка video-to-video различаются по моделям, поэтому
capability matrix должна проверяться перед реализацией.
[Официальные input constraints](https://docs.dev.runwayml.com/assets/inputs/).

У Hugging Face finetrainers документированы LoRA и full fine-tuning для определённых Wan
checkpoints и загрузка LoRA при inference. Это пример технической возможности для совместимой
модели, **не выбор Wan для Cadence**, не обещание доступного GPU и не blanket commercial licence.
[Официальный training guide](https://github.com/huggingface/finetrainers/blob/main/docs/models/wan.md).

## Когда training имеет смысл

Предложение: сначала V2-18 context/references/presets, затем V2-19 измерение качества.
Если остаётся повторяемая проблема, которую нельзя исправить contract/reference/rendering,
подготовить эксперимент X01. Нельзя заранее обещать одинаковое качество каждой brand.

Для video dataset нужны разрешённые клипы/изображения с полезным разнообразием сцен,
ракурсов/освещения/движения и annotations. Для editing полезнее пары:
`brief + raw scene metadata → approved edit plan + final corrections`.
Случайные готовые ролики без исходников/решений не объясняют, как пользователь хотел монтировать.
Универсального «достаточно N видео» здесь нет: объём определяется моделью и задачей.

Необходимые gates:

1. Отдельное opt-in на training, права на лица/голоса/products/music, scope использования.
2. Dataset manifest/checksum/version, доступ только tenant, отсутствие чужих примеров.
3. Holdout — независимые примеры для оценки; отсутствие их в training/reference set.
4. Baseline vs trained comparison: style fidelity, product fidelity, temporal consistency,
   edit intent, safety/format, latency и cost per approved asset.
5. Model registry: base model/licence/adapter version/dataset lineage/evaluation/actor.
6. Review перед activation, возможность rollback, explicit retraining/deletion policy.
7. Training не получает OAuth tokens, market credentials или доступ к live publishing.

**Нельзя обещать:** обучение proprietary API без соответствующей функции; точное повторение
логотипа/человека во всех сценах; улучшение без evaluation; удаление learned information простой
очисткой dataset; автоматическое согласие на обучение из факта загрузки файла.
Voice cloning/face replacement не следуют автоматически из запроса о стиле бренда.

## Почему монтаж — отдельная подсистема

Предлагаемый поток:

```text
raw uploads → scoped quarantine/scan/decode → transcript/scenes
  → approved Identity/Strategy + user edit request
  → typed EditPlan → backend validator → isolated renderer
  → technical checks + human preview → final revision
```

AI выбирает содержание/последовательность; renderer выполняет разрешённые операции.
Сгенерированная моделью строка FFmpeg/shell не исполняется. `video-to-video` может быть
одним преобразованием, но не заменяет ingest, timeline, звук, проверку и revisions.
Точные overlays/subtitles/logo лучше собирать отдельно от генерации визуальных сцен.

Ресурсы renderer ограничиваются по CPU/RAM/disk/time/decoded frames; нет network и secrets.
Аргументы строятся сервером по allowlist операций. Конкретные package/container/version
выбираются и проверяются в implementation, не фиксируются на основании случайного snippet.

## Instagram: разные login paths, не универсальный доступ

Meta публикует официальные collections для Instagram Login и Facebook Login. Обе описывают
professional accounts; Facebook Login path требует связанной Page. Instagram Login path
не требует этой связи и использует свой набор scopes. Нельзя смешать permissions двух
flows или обещать работу с любым personal account.
[Instagram Login](https://www.postman.com/meta/instagram/folder/6raa77c/instagram-api-with-instagram-login),
[Facebook Login](https://www.postman.com/meta/instagram/folder/u4g5a2a/instagram-api-with-facebook-login).

Для Reels Meta показывает separate container processing и `media_publish`: готовый container
ещё не опубликованный media ID. Это основание отдельного asynchronous publishing lifecycle.
[Официальный publishing example](https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api?entity=request-23987686-1ff01566-3509-48bd-a0f4-8571a91ccfdf).

Вывод для V2-02/03/21: выбрать один подходящий login path, проверить exact endpoints/
permissions/formats/insights/business discovery/hashtag capabilities и app access level.
Доступность чужих competitor data не выводится из права публиковать в свой account.
Direct developer pages при проверке возвращали tool fetch error; использованы официальные
Meta collections. Полный app-review/insights contract остаётся implementation gate.

## TikTok: Content Posting API, consent и audit

Для organic direct publishing рассматривается **Content Posting API**, а не предположение
старого HTML о Marketing API. Требуются app/user authorization на `video.publish`;
unaudited clients ограничены private visibility.
[Официальный getting started](https://developers.tiktok.com/docs/en/content-posting-api-get-started).

Официальные UX guidelines требуют актуальный creator info, preview, редактируемый текст,
выбор privacy пользователем без default, управление interactions/disclosures и явное согласие
перед upload. Также описаны audit, контроль creator/posting limits и delivery через verified
URL prefix/domain для server-hosted media. Это влияет на дизайн scheduled publishing:
общее OAuth-согласие не заменяет согласие на конкретный пост.
[Content Sharing Guidelines](https://developers.tiktok.com/docs/en/content-sharing-guidelines).

Вывод: backend сохраняет consented immutable package и проверяет capabilities в момент
dispatch. Возможность отправки по расписанию с таким UX нужно квалифицировать на актуальной
policy/application, а не обещать по наличию HTTP endpoint. Если platform требует дополнительный
шаг, это показывается пользователю; обхода через browser automation не предлагается.
Прохождение audit не гарантируется. Приложение рассчитано на широкий self-service сценарий,
а не скрытую internal-only утилиту.

## Откуда брать market/hashtag/analytics data

Display API описывает доступ к разрешённому профилю и metadata его videos. Его наличие не
доказывает доступ к arbitrary competitors, аудитории и полному business analytics.
[TikTok Display overview](https://developers.tiktok.com/docs/en/display-api-overview).
Research Tools прямо исключают creators/advertisers/commercial users из eligibility;
это не бесплатный market-data backend коммерческого Cadence.
[TikTok Research FAQ](https://developers.tiktok.com/docs/en/research-api-faq?enter_method=left_navigation).

Предлагаемые категории источников:

| Источник | Для чего | Ограничение/решение |
| --- | --- | --- |
| Own-account official data | История, performance, timing observations | OAuth/scopes/account type/field availability |
| Official public/business discovery | Разрешённые competitor/hashtag samples | Проверить конкретный API/login path/quotas |
| Licensed market-data provider | Более широкое industry/trend coverage | Отдельно стоимость, commercial rights, retention и API contract |
| Client-provided research/exports | Initial context и дополнение gaps | User-provided provenance; не выдавать за verified live platform snapshot |

Live market source ещё не выбран. Широкий «анализ рынка» без покрытия/свежести должен
оставаться ограничением, а не обещанием знания всей платформы. LLM не измеряет популярность.
Apify из HTML не принят; автоматический scraper не появляется в плане по умолчанию.

## Музыка, promotion и права

Органическое продвижение бренда всё равно может быть commercial content по правилам
платформы. TikTok рекомендует commercial-cleared музыку для такого использования;
общая музыкальная библиотека не даёт автоматических commercial rights.
[Commercial use of music](https://support.tiktok.com/en/business-and-creator/creator-and-business-accounts/commercial-use-of-music-on-tiktok?lang=en).
Права на TikTok track нельзя считать автоматически действующими для Instagram/rendered
download; требуется проверка лицензии и способа API-доставки. Trending audio X03 — отдельная
feasibility задача, не загрузка популярной песни из интернета.

## £20 и стоимость расширенного продукта

£20 — общий demo AI envelope, подтверждённый пользователем, не ежемесячная subscription
и не разрешение незаметно повысить лимит. Разделить смету на:

- input/output/vision tokens, image calls, video seconds/resolution/audio;
- повторные генерации и discarded scenes;
- ASR/video analysis и optional AI quality checks;
- renderer CPU/GPU time, private storage/traffic и backups;
- licensed market data, hosting и optional delivery;
- отдельный training GPU/curation/evaluation/inference budget.

Для quote нужна формула:
`стоимость approved asset = все charged attempts + analysis + rendering + delivery/storage allocation`.
После model selection рассчитать максимум отдельных demo samples и запас retry/unknown billing.
Низкая цена одного вызова не равна низкой цене принятого пользователем ролика.
В этом этапе цены/количество видео не фиксируются, paid calls и training не выполняются.

## Decision log

| Решение | Статус |
| --- | --- |
| Organic promotion, не Ads Manager | Подтверждено пользователем20 октября |
| £20 demo AI calls; другие расходы отдельно | Подтверждено пользователем20 октября |
| Ready video + raw editing + approved direct publishing | Required новое ТЗ |
| Video model/provider | PENDING; NanoBanana не назначен |
| Context/references/presets как первая personalization | Инженерная рекомендация V2-18, не training claim |
| Tenant-specific training | Исследование/evaluation V2-19, optional implementation X01 |
| Market-data source и coverage | PENDING V2-02/07; потенциальный external blocker |
| Media limits/quality/render deployment | PENDING design/qualification |
| Platform audit/public posting | EXTERNAL_GATE, не выполнено |
| PDF/email/audience/trending audio | Сохранены как X02–X04, не приняты автоматически |
