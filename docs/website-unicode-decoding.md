# Unicode decoding для website metadata — #117

Новые website snapshots используют `website-v2`. Extractor распознаёт явный UTF-8, UTF-16LE или UTF-16BE BOM и удаляет его перед разбором. Без BOM применяется строгий UTF-8. Исторические `website-v1` snapshots сохраняются без backfill.

## Архитектура и Runtime Flow

OWNER registration → durable URL Job → SSRF-safe bounded fetch → strict BOM decoding → bounded HTML/plain-text metadata → original-byte quarantine storage → atomic fenced completion и malware scan Job.

`JsoupWebsiteMetadataExtractor` использует стандартные JDK CharsetDecoder. Новой dependency нет. HTTP charset headers и HTML meta declarations не выбирают decoder. Поддержка этих деклараций и legacy encodings остаётся отдельной задачей. Исходные bytes и их SHA-256 не меняются; decoder работает только с metadata. Version и фиксированный warning сохраняют способ интерпретации. Схема БД и публичные DTO не меняются.

## Security Review

Защищаем целостность metadata, исходных bytes и ресурсы worker. Пользователь OWNER регистрирует URL, но remote body пересекает отдельную границу доверия (trust boundary). Возможные атаки: неоднозначные кодировки, malformed surrogates, oversized input, scripts, embedded internal URLs и ложная успешная обработка.

- Threat: malformed bytes интерпретируются с replacement characters. Protection: CharsetDecoder сообщает ошибку; fixed WebsiteExtractionFailure; worker не сохраняет partial metadata. Verification: malformed UTF-8/UTF-16 regression fixtures и существующий worker failure test.
- Threat: UTF-32LE ошибочно распознаётся как UTF-16LE. Protection: UTF-32 BOM проверяется раньше UTF-16 и отклоняется. Verification: оба UTF-32 BOM fixtures.
- Threat: ресурсное истощение или выполнение активного контента. Protection: общий лимит 2 MiB включает BOM; parser depth 128; bounded text fields; parsing не делает network calls и не выполняет scripts. Verification: oversized BOM input, deep markup, inactive script и Unicode truncation tests.
- Threat: decoded bytes подменяют объект, который затем сканируется. Protection: storage/checksum получают original body; malware scan Job остаётся отдельным. Verification: worker integration test проверяет исходный SHA-256 и exact stored bytes.

Authentication/authorization остаются на существующих OWNER/Workspace boundaries. SQL и shell не используются decoder. Metadata по-прежнему является недоверенным текстом: будущему frontend требуется output encoding, AI adapter — separation of data/instructions. Успешное извлечение не переводит evidence в READY. Ошибки и warnings не включают remote content, paths или secrets.

## Проверки и ограничения

Extractor fixtures покрывают UTF-8 BOM, оба UTF-16 BOM, кириллицу/emoji, malformed sequences, UTF-32, literal plain text, пустой BOM-only документ, output truncation и input bounds. Spring worker fixture проверяет сохранение website-v2 metadata, checksum original bytes и pending scan Job.

Проверки 2026-10-01:

- `.\mvnw.cmd spotless:apply '-Dtest=JsoupWebsiteMetadataExtractorTest' test`: BUILD SUCCESS, 8 tests, 0 failures/errors/skips.
- `.\mvnw.cmd spotless:apply clean verify`: BUILD FAILURE, 359 tests, 1 failure, 9 errors, 3 skips. Все failures/errors относятся к существующему PostgreSqlFileScanWorkerTest: scan provenance guard отклонил результаты; новый Unicode worker test прошёл.
- `.\mvnw.cmd '-Dtest=PostgreSqlFileScanWorkerTest' test`: BUILD SUCCESS, 19 tests, 0 failures/errors/skips, без изменения scan implementation/tests/constraints.
- `.\mvnw.cmd verify`: BUILD SUCCESS, 359 tests, 0 failures/errors, 3 skips; 356 выполненных tests прошли. PostgreSQL suites без skips; Spotless/JaCoCo gates прошли. Application contexts успешно стартовали в test/postgres-test.
- `python scripts/verify_postgres_report.py`: PostgreSQL suite ran without skips or failures.

Три пропуска — Linux-only symlink tests на Windows. JVM сообщает CDS warning при загрузке Mockito agent. Первоначальный PostgreSQL provenance failure непостоянен; точная причина не установлена. Сравнение часов Windows/Docker на уровне секунд не подтвердило существенного расхождения, но не исключает subsecond clock skew. Повторный успешный запуск не доказывает устранение flakiness. Перед production требуется диагностировать этот timing-sensitive путь на синхронизированных application/database clocks и в CI, сохраняя строгие provenance/lease guards.

Live websites, HTTP charset negotiation, legacy encodings и production activation этим изменением не проверяются. Real storage/scanner, quotas, retention, egress policy, screenshots и provider approval остаются production requirements.

## Что важно понять

BOM — явный префикс bytes, задающий Unicode encoding. Строгое декодирование (strict decoding) отклоняет повреждённые последовательности вместо молчаливого исправления. Provenance version объясняет, каким алгоритмом создан snapshot. Quarantine отделяет сохранение/извлечение от разрешения использовать контент. Fenced completion не даёт worker с истёкшим lease перезаписать актуальный результат.
