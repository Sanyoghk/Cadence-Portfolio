# Website metadata — #117

## Что сделано

Добавлено внутреннее извлечение title, description и ограниченного текста при новых URL fetch Jobs. Метаданные сохраняются атомарно с fetched provenance и успешным Job. Снимок не меняет исходный checksum регистрации, не переводит источник в READY и не расширяет API. Архитектура, dependency review, threat model и flow: [ADR-0006](adr/0006-bounded-website-metadata.md).

## Изменённые файлы

- `pom.xml`: фиксированная jsoup 1.23.2.
- `src/main/java/com/cadence/application/ports/WebsiteMetadataExtractor.java`: provider-neutral port.
- `src/main/java/com/cadence/application/ports/WebsiteExtractionFailure.java`: постоянная ошибка с безопасным сообщением.
- `src/main/java/com/cadence/domain/WebsiteMetadata.java`: валидированный внутренний результат.
- `src/main/java/com/cadence/infrastructure/ingest/JsoupWebsiteMetadataExtractor.java`: ограниченный разбор UTF-8 HTML/text без сетевых вызовов.
- `src/main/java/com/cadence/domain/BrandEvidence.java`: необязательный неизменяемый снимок и доменные ограничения.
- `src/main/java/com/cadence/application/services/UrlFetchWorker.java`: извлечение вне транзакции и запись под существующим fencing.
- `src/main/resources/db/migration/V10__website_metadata.sql`: nullable-поля, all-or-none constraint, связь с provenance и временем; V1–V9 не менялись.
- `src/test/java/com/cadence/infrastructure/ingest/JsoupWebsiteMetadataExtractorTest.java`: HTML, Unicode, лимиты, кодировки и отрицательные сценарии.
- `src/test/java/com/cadence/domain/WebsiteMetadataTest.java`: валидация и запрет повторной записи/неверного состояния.
- `src/test/java/com/cadence/application/UrlFetchWorkerTest.java`: атомарность, ошибка извлечения, duplicate/stale completion.
- `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java`: mapping, DB constraints и заполненный V9 → V10 upgrade.
- `DEVELOPMENT_PLAN.md`, `DEVELOPMENT_PLAN_RU.md`, `README.md`, `docs/verification.md`: реализованный объём, остающиеся задачи и исправление устаревшего статуса CI.
- `docs/adr/0006-bounded-website-metadata.md`: решение и ограничения.
- `docs/website-metadata-implementation.md`: этот отчёт.

## Проверки

- `.\mvnw.cmd -DskipTests compile`: BUILD SUCCESS.
- `.\mvnw.cmd spotless:apply clean verify`: BUILD SUCCESS после добавления upgrade fixture; 211 тестов, failures/errors 0, 205 успешных, 6 PostgreSQL/Testcontainers skipped без Docker. Компиляция, test-profile startup, Spotless и JaCoCo прошли.
- `git diff --check`: успешно.
- Удалённый CI для конкретного commit SHA отслеживается в #117 и PR #113; локальные skips не являются успешной PostgreSQL-проверкой.

## Security Tests и Edge Cases

Проверены script/style/iframe/form/hidden/embedded content, глубокий и некорректный HTML, отсутствие XML expansion, media/input limits, неверный UTF-8, пустой результат, supplementary Unicode без разрыва surrogate pair, truncation warnings, повторные Jobs, потеря lease и ошибка без частичного снимка. Старые OWNER-only, unauthenticated, cross-Workspace, forged-scope и SSRF tests входят в полный verify.

## Непроверенные аспекты и production

Локально не запускались PostgreSQL, live Clerk и production configuration. Новые PG fixtures требуют CI Docker. UTF-8-only, эвристическое извлечение без CSS/rendering, отсутствие backfill для старых FETCHED и hard CPU sandbox описаны в ADR. Production providers/scanner, screenshots, социальные источники, quotas, egress/retention и scraping approval остаются отдельными требованиями. Текст по-прежнему недоверенный, даже после успешного извлечения.

## Что стоит изучить

- Port/adapter отделяет бизнес-контракт от конкретного HTML-парсера.
- Fencing проверяет, что результат принадлежит текущей попытке Job, а не просроченному worker.
- All-or-none constraint запрещает частично заполненный снимок даже при обходе Java.
- Extracted не означает trusted: будущие UI/AI потребители обязаны отдельно защищать границу HTML и инструкций модели.

## CI correction

CI run 34546525196 executed all 211 tests (no skips) and found one fixture failure: the isolated upgrade schema has a Flyway schema-creation marker in addition to ten SQL migrations. The fixture now asserts the exact SQL migration versions 1 through 10 instead of counting administrative records. No expected migration version or security constraint was relaxed. PostgreSQL mapping and provenance preservation had passed; the corrected run must also complete the remaining upgrade constraint assertions.

## Final remote verification

[CI 34546765430](https://github.com/Sanyoghk/Cadence/actions/runs/34546765430), commit `ea3a5fc`: `sh ./mvnw --batch-mode --no-transfer-progress clean verify` — BUILD SUCCESS, 211 tests, failures/errors/skips 0. All six PostgreSQL tests, populated V9 to V10 upgrade and mandatory report gate passed. Issues #117/#107/#118 and draft PR #113 are synchronized. No merge or deployment.
