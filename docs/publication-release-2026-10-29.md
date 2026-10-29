# Публичный portfolio snapshot — 2026-10-29

## Что было сделано

Подготовлен отдельный repository `Sanyoghk/Cadence-Portfolio` для оценки проекта работодателями.
Он получает только очищенные tracked-файлы ранее проверенной версии, с новой Git-историей.
Исходный development repository остаётся private. Это source publication, а не запуск SaaS.

Snapshot основан на audit commit `02b9f1d31ba9cf6bdfd46464b4f0707219a8e3b2`.
Новые локальные backend/features и отдельный frontend-v2 не включены. Исторический
[аудит](publication-audit-2026-10-28.md) описывает original repository; его запрет открытия
старой истории не относится к новой истории этого отдельного portfolio repository.

## Как это работает / Runtime Flow

```text
Проверенный source commit
    → git archive только tracked-файлов
    → отдельный каталог без старого .git
    → public README + настройки CI для main
    → новый root commit с GitHub noreply identity
    → secret scan + privacy scan всей новой истории
    → отдельный GitHub repository / только main
    → anonymous read-back + exact-SHA CI
```

## Архитектурное решение

Выбран независимый repository, не fork и не mirror. Ни старые commits/tags, ни issues,
Actions logs, settings secrets или untracked файлы не переносятся. Это сохраняет приватную
историю разработки без destructive rewrite. Trade-off: публичная история начинается со
snapshot и не демонстрирует все прежние этапы разработки.

Application code, миграции, API contracts и зависимости не меняются. Для новых обновлений
нужно проверять source diff и импортировать только выбранные файлы: нельзя делать merge
приватной Git-истории в публичный repository.

## Security Review

Threat: попадание старых личных данных через parent commits или refs.
Protection: `git init` в export-каталоге, новый root commit, push только `main`; отсутствует
remote на private repository. Verification: перед push проверяются отсутствие родителей,
единственный commit и full-history privacy/Gitleaks scans.

Threat: повторная публикация credentials или личных данных.
Protection: существующий privacy detector и Gitleaks; historical privacy gate теперь
обязателен на каждом push/PR, author использует noreply. Verification: 26 Python tests
прошли; snapshot Gitleaks — 0 findings. Проверки эвристические и не гарантируют абсолютную полноту.

Threat: маскировка scanner finding старым исключением.
Protection: `.gitleaksignore` старой истории не перенесён. Формулировка ADR0040 уточнена
без изменения архитектурного смысла, чтобы не провоцировать ранее проверенный prose false
positive. Verification: новый snapshot проверяется без этого исключения.

Threat: раскрытие GitHub credential при создании repository.
Protection: официальный API по HTTPS; существующий Git credential хранится только в памяти
локального helper, login/id владельца проверяется до mutation; helper и outputs не tracked.
Verification: scan export и проверка tracked-file inventory; credential не включён в URLs.

Новых runtime endpoints, данных пользователей или external provider calls нет. Tenant
authorization, validation, database constraints и application runtime не изменены.
Security settings нового repository настраиваются отдельно; они не подтверждают production readiness.

## Изменённые файлы относительно очищенного snapshot

| Файл | Назначение |
| --- | --- |
| `README.md` | Обзор для работодателей, implemented/planned scope, архитектура, актуальные Actions links |
| `SECURITY.md` | Разграничение private history и public snapshot, private disclosure guidance |
| `AGENTS.md` | Рабочая ветка main для public copy и запрет переноса private history |
| `.github/dependabot.yml` | Target branch main вместо отсутствующей mvp |
| `.github/workflows/publication-safety.yml` | Historical privacy gate обязателен на всех событиях |
| `.gitleaksignore` | Удалено исключение, относящееся только к старому commit |
| `docs/adr/0040-frontend-bearer-onboarding.md` | Переформулирован prose false positive, смысл guards сохранён |
| `docs/publication-release-2026-10-29.md` | Этот отчёт и границы публикации |

## Тесты

- `python -m unittest discover -s scripts -p 'test_*.py'` — 26 tests, 0 failures/errors
  при запуске вне sandbox. Первый запуск ограниченного Windows sandbox завершился errors
  при создании/удалении временных каталогов; это не было объявлено pass.
- `.\mvnw.cmd --batch-mode --no-transfer-progress spotless:check` — BUILD SUCCESS.
- `gitleaks dir --redact --no-banner --max-decode-depth=3 --max-archive-depth=3 --report-format=json --report-path=../gitleaks-portfolio-snapshot.json .`
  — 0 findings; официальный Gitleaks 8.30.1 из предыдущего checksum-verified audit.
- После root commit выполняются `python scripts/audit_publication.py --history` и
  `gitleaks git --redact --no-banner --log-opts=--all --max-decode-depth=3 --max-archive-depth=3 .`.
  Publication допускается только при нулевом exit code обеих команд.
- До push проверяются YAML, local Markdown links и совпадение application файлов с исходным snapshot.

Выводы HIGH/UNKNOWN в report-gate unit tests относятся к synthetic fixtures. Они не являются
результатами нового vulnerability scan. Полный runtime/integration suite и dependency/container
scans выполняются отдельными GitHub workflows; фактический статус доступен в
[Actions](https://github.com/Sanyoghk/Cadence-Portfolio/actions). Старый CI не заменяет новый.

## Security Tests и Edge Cases

Tests покрывают email metadata, phone candidates, Windows/WSL paths, deleted/renamed files,
private filenames, redaction, invalid refs и UUID false positives. Snapshot содержит только
reviewed source: `.env`, raw audit outputs и assistant state не переносятся. Fonts сохраняют
license notices; сторонние research screenshots были исключены ранее.

## Непроверенные аспекты

Publication не является полным application pentest, доказательством отсутствия всех PII
форматов или qualification live Clerk/AI/social providers. Новая full CI может выявить
environment/dependency/runtime проблемы, которые не относятся к чистоте Git-истории.
Подробные historical reports могут ссылаться на недоступные private issues/old SHA.

## Что мне важно понять

Публичность разрешена для конкретного проверенного набора файлов. Новый public repository
не должен наследовать старую историю. Последующий merge из private development branch
может вернуть сведения, которые отсутствуют в текущем snapshot.

## Что мне стоит изучить

1. **Корневой commit (root commit):** commit без родителей начинает независимую историю.
2. **Экспорт snapshot (git archive):** переносит tracked содержимое без Git database и untracked файлов.
3. **Изоляция публикации (publication boundary):** public copy имеет свои refs, CI и security settings.

## Production considerations

Repository можно использовать для source review; production activation требует прежних
auth/provider/infrastructure/recovery gates. Новая лицензия на собственный код не назначалась;
существующие third-party notices сохранены.

Подтверждённые факты до publication: чистый source export, результаты локальных tests/scans,
обновлённые public docs и отсутствие application changes.
Инженерный вывод: independent history предотвращает наследование personal metadata старых commits.
Ограничения: полнота heuristic detectors и runtime readiness требуют отдельных проверок.
Факт создания, public visibility и точные remote CI results проверяются после push.
