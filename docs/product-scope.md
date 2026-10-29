# Cadence — актуальное описание продукта

Обновлено: **2026-10-20, расширенное ТЗ**. Источник — прямой запрос пользователя и уточнения:
органическое продвижение; £20 на демонстрационные AI-вызовы, остальные расходы оценить
отдельно; **один Workspace на аккаунт, несколько Brands внутри него**.
Это целевой продукт, **не заявление о реализованной готовности**.
[Промпты V2](astra-medium-mvp-prompts-v2.md) · [Сверка реализации и исходного brief](scope-v2-inventory.md).

## Продукт и ценность

Cadence — self-service SaaS для предпринимателей, creators и медиа-персон, который
автоматизирует органическое продвижение в Instagram и TikTok: от материалов бренда и
исследования его медиа-среды до готового фото/видео, согласования, публикации и анализа
результатов. Пользователь управляет решениями через review, правки и явное approval.

Каждый контентный замысел должен иметь объяснимое основание: информация клиента,
утверждённая Brand Identity, конкретная версия Strategy Playbook и доступные актуальные
данные о рынке/результатах. При нехватке данных сервис показывает ограничения и гипотезы,
а не выдумывает популярность, demographics, конкурентов или гарантированный рост.

**Публикация через сервис теперь входит в целевой MVP.** CSV остаётся полезным экспортом,
но не заменяет acceptance Instagram/TikTok publishing. Сохранение поста в БД, экспорт,
отправка файла платформе и подтверждённая публикация — разные события.

## Пользовательский сценарий

1. Открыть сервис, зарегистрироваться/войти через Clerk. При первом onboarding сервис
   автоматически создаёт единственный Workspace аккаунта; повторный вход использует его.
   Создать первый Brand, затем при необходимости добавлять и переключать Brands.
2. Загрузить brand book, logo files, product shots, brand voice notes; указать website,
   Instagram и TikTok. Подключить свои social accounts через разрешённый OAuth flow.
   Ссылка на профиль сама по себе не даёт права читать закрытые данные или публиковать.
3. Проверить извлечённые материалы и разрешить использование конкретных источников AI.
4. Получить Brand Identity, исправить вручную и отдельно утвердить её.
5. На основании approved Identity, материалов клиента и исследования медиа-среды получить
   Strategy Playbook; отредактировать и утвердить конкретную версию.
6. Получить план ближайших четырёх недель: тема, pillar, канал, формат, цель, рекомендуемое
   время, основание рекомендации и сроки подготовки/review.
7. Для слота либо собственного запроса создать фото/готовое видео либо загрузить сырые
   видеоматериалы с заданием на монтаж. Внеплановый запрос также связывается с approved
   Playbook; противоречие стратегии требует review, а не её скрытого изменения.
8. Получить пакет поста: готовый media asset, caption, hashtags, cover/thumbnail где нужно,
   platform-specific параметры, сведения об источниках и статус готовности.
9. Просмотреть sample/preview, исправить доступные поля, запросить доработку конкретных
   частей или regeneration. История и предыдущие версии сохраняются.
10. Утвердить точный финальный пакет и отдельно разрешить публикацию в выбранный account
    сейчас или в согласованное время. Согласие связано с версиями media/caption/hashtags,
    platform/account и настройками публикации.
11. Получать статус публикации/ошибки, напоминания о generation/review/approval и уведомления,
    требующие действия. Неутверждённый или изменённый пакет не публикуется.
12. Получать регулярные отчёты о результатах и изменениях рынка; применять выбранные
    рекомендации в новый Strategy DRAFT. Продолжать четырёхнедельное планирование.

## Один Workspace на аккаунт, несколько Brands

Workspace остаётся внутренним контейнером аккаунта и границей изоляции данных (tenant boundary).
После успешного onboarding аккаунт имеет ровно один Workspace; до его завершения допустимо
состояние без Workspace. Второй Workspace запрещён сервером и ограничениями PostgreSQL,
включая одновременные запросы и альтернативные пути provisioning. Архивирование не освобождает
место для второго Workspace. Повторный запрос onboarding не создаёт дубликат.

В UI нет Workspace switcher, списка Workspaces, Manage workspaces и создания дополнительных
Workspace. Есть настройки аккаунта/единственного Workspace и управление несколькими Brands.
Один Brand не является отдельным аккаунтом: новые Brands создаются в том же Workspace,
с ограничениями ресурсов и общей проверкой расходов аккаунта.

У каждого Brand свои материалы, отрасль/аудитория, Identity, Playbook, calendar, posts,
social connections, reports и настройки персонализации. Данные другого Brand не подмешиваются
в AI context, metrics или publishing даже внутри одного Workspace. Общие расходы учитываются
на уровне аккаунта, а использование дополнительно атрибутируется Brand. Численный лимит Brands
определяется отдельной resource policy; несколько Brands не означает неограниченные ресурсы.

Текущий код поддерживает несколько Workspace. Переход требует inventory существующих
memberships и новой Flyway migration: нельзя молча выбрать первый Workspace, удалить остальные
или перенести Brands между tenants. Неоднозначные старые связи требуют проверенного плана
переноса и сохранения истории до активации нового ограничения. Детали: [ADR0042](adr/0042-single-workspace-per-account.md).

## Brand Identity и материалы

Identity содержит palette, typography guidance, tone/voice, допустимые и нежелательные
формулировки, примеры и происхождение выводов. Product shots также служат references
для production. Текстовые brand voice notes обязательны; аудиозаметки требуют проверки
форматов, транскрипции и согласия на передачу provider, без неявного voice cloning.

Logo files не означают исполнение SVG, загрузку произвольных шрифтов или архивов. Форматы
и пределы описываются в API. Raw video/audio требуют отдельного media ingest. Извлечение
текста из PDF не равно пониманию изображений внутри brand book.

## Исследование медиа-среды

- Профиль индустрии: категория, география, язык, аудитория, выбранные конкуренты.
- Наблюдаемое присутствие/популярность бренда: доступные followers/views/reach/engagement,
  изменения и сравнимая выборка; отсутствие метрики отличается от нуля.
- Competitive landscape: частота, форматы, темы и доступные результаты конкурентов;
  свободные направления развития — выводы с источниками, не доказательство отсутствия конкуренции.
- Hashtag intelligence: релевантность, наблюдаемая активность/динамика, источник, регион,
  окно измерения, давность и полнота данных.
- Регулярное обновление snapshots с quotas, backoff и явным stale/partial статусом.

Собственные insights, публичные сведения о конкурентах и лицензированный market data —
разные права доступа. Нет обещания полного обзора платформ или точного глобального объёма
каждого hashtag. Обязательная функция без источника остаётся BLOCKED/ограниченной, а не
заменяется фиктивным результатом. Достаточность покрытия входит в acceptance.

## Strategy Playbook

Сохраняются pillars/weights, audience assumptions, goals, channel mix, cadence/timezone,
tone spectrum, конкурентные гипотезы и four-week outline. Расширение включает:

| Область | Целевое содержание |
| --- | --- |
| Content pillars | Вес, цель, аудитория, темы, форматы и связь с brand/market evidence |
| Competitive landscape | Конкуренты, сравнимые наблюдения, периоды, ограничения и opportunities |
| Best times to post | Platform/timezone, основание OWN_DATA/CATEGORY_BASELINE/EXPERIMENT, coverage/freshness |
| Tone of voice | Применение approved Identity к captions, scripts, spoken text и визуальному стилю |
| Четырёхнедельный план | Даты/время, тема, формат, pillar, CTA, brief, production method и сроки review |
| Production guidance | References, style rules, монтаж/субтитры/музыка, ограничения и доступность клиента |
| Измерение | Проверяемые цели и эксперименты без обещания гарантированного роста |

Outline стратегии, календарное назначение и команда публикации имеют отдельные состояния.
Редактирование approved Playbook создаёт новый DRAFT. Новый план не переписывает уже
опубликованную историю и не переносит существующие согласия автоматически.

## Content: готовое видео, монтаж и изображения

**Генерация видео:** запрос + slot/brief + approved Identity/Strategy + разрешённые references
→ сценарий/storyboard → генерация сцен → сборка/звук/субтитры при необходимости → проверка
формата и качества → final preview. Короткий clip модели не автоматически готовый пост.

**Монтаж сырого видео:** uploads + запрос → quarantine/scan/изолированное декодирование →
transcript/scene candidates → проверяемый монтажный план → deterministic render → review.
Предусмотреть trim/reorder, переходы, кадрирование, обработку звука, subtitles, brand styling
и разрешённую музыку. Набор операций и ограничения фиксируются перед implementation.
AI не получает shell, произвольные пути или права approval.

**Фото/посты:** generation или преобразование product shots/references, проверка верности
продукта/бренда, caption/hashtags/CTA, carousel где поддерживается. Точные logo/text overlays
предпочтительно собирать контролируемо: модель не гарантирует надписи/геометрию продукта.

Caption, hashtags, media, script и монтажный план можно дорабатывать по отдельности.
Содержательная правка после approval отменяет пригодность прежнего publish consent.
Утверждение storyboard/чернового sample не разрешает отправку неизвестного финального
render: нужен review фактического final asset.

## Персонализация AI

Обязательная основа — versioned brand profile, разрешённые references, approved examples,
запреты/предпочтения и feedback клиента. Это персонализация контекста, а не обучение весов.
Для монтажа сохраняются style presets и примеры edit decisions.

Опционально — fine-tuning/LoRA там, где это поддерживает выбранная модель. Нужны права/
согласие на dataset, tenant isolation, отдельный бюджет, evaluation на отложенных примерах,
model version/rollback/deletion policy. Нет обещания обучить любой proprietary video API.
[Возможности и ограничения](media-personalization-and-platforms.md).

## Публикация, analytics и уведомления

Publishing — через официально доступные API и разрешения аккаунта, с требуемыми
preview/consent/privacy/disclosure controls. Platform review/audit — внешний release gate.

Нужны безопасное хранение/refresh/revoke OAuth tokens, account binding, durable publish
intent (проектируемый `PublishIntent`), scheduler, external ID/status, reconciliation неоднозначных ответов, отмена ещё
не отправленного задания и уведомление об uncertainty. Нельзя обещать exactly-once там,
где platform этого не гарантирует.

Metrics sync использует доступные fields с привязкой к post/account/window. Manual/CSV
сохраняется для дополнительных показателей. Audience/conversion данные требуют своего
источника/разрешения; read API не считается полным analytics API. Reports содержат
performance, market changes, recommendations и ограничения. Расписание/timezone настраиваются.

Обязательны in-app notifications о generation/review/approval, публикации/ошибке и готовности
report. Email delivery — отдельное решение о provider/preferences. Напоминание не запускает
платную генерацию без пользовательской политики и не утверждает контент.

## Архитектурные границы

- Java 21 / Spring Boot modular monolith; PostgreSQL/Flyway; Next.js/React/TypeScript.
- Целевая модель: `CadenceUser → один Workspace → несколько Brands`; текущий OWNER-only
  доступ и server-side проверки конкретного Brand сохраняются. Multi-workspace заменяется
  по ADR0042; наличие записи в плане ещё не изменяет runtime.
- Clerk — authentication; backend — актуальные authorization/ownership.
- DTO/REST `/api/v1`, safe errors, bounded inputs, optimistic locking, immutable approvals.
- Существующие Jobs/recovery/idempotency/audit расширяются новыми operation kinds.
- Отдельные ports для Brand/Strategy/Content, media, research, platforms и delivery.
- Renderer — изолированный worker, не model-generated команды внутри web process.
- External I/O вне длинных DB transactions; права/версии/consent/deadline/lease проверяются
  до передачи данных и перед фиксацией результата. Critical invariants защищены PostgreSQL.

## Scope и открытые решения

**Required:** один Workspace на аккаунт с несколькими Brands, ready video, raw-footage editing,
фото/видео post package, industry/hashtag
intelligence, rich Playbook/calendar, approved publishing, regular reports/reminders,
постоянный review/revision цикл.

**Не включено:** Ads Manager/платная реклама (пользователь выбрал органику), billing,
agency CRM, iframe/ElorForce SSO, white-label и обязательный named pilot.
Неисполненные функции исходного HTML сохранены в backlog, не объявлены принятыми по факту упоминания.

**Pending:** AI/video providers (NanoBanana для видео не утверждён), market-data source,
hosting/region/retention, media limits/quality, usage rights, report channels, обучение.
Claude Code не считается backend API provider. £20 — лимит demo AI calls, не всего продукта.

## Milestones и Definition of Done

1. Сначала backend: complete API workflows, media pipeline, согласованный live AI,
   platform contracts и контролируемая проверка интеграций; external gates отдельно.
2. Затем полностью новый frontend. Финальный platform audit, требующий UX, завершается
   после UI; его нельзя объявить выполненным на основании backend fixtures.
3. Полный MVP: новый пользователь проходит оба video-сценария, утверждает final package,
   публикует в обе платформы с подтверждением, получает реальные доступные analytics/
   market reports/reminders и изменяет Strategy через DRAFT.
4. Negative/concurrency/cost/restart/decoder/OAuth/cross-tenant tests, actual CI,
   reproducible setup и recovery evidence обязательны. Fixtures отдельно от live.
5. Commercial deployment — отдельная infrastructure/privacy/security qualification.
