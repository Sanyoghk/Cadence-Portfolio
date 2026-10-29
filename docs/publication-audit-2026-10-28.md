# Проверка Cadence перед публичным открытием — 2026-10-28

## Что было сделано

**Существующий repository пока нельзя рекомендовать к переключению в public.**
В его истории подтверждены личные email авторов commits, домашние пути и внутренние
идентификаторы чатов/проектов. Удаление из последнего commit не стирает предыдущие версии.
В старом `ClientTest` также используется фамилия автора и phone-shaped значение. Принадлежность
номера не подтверждена; как потенциально личные данные его нельзя без проверки включать
в public history. В очищенном snapshot этого legacy test уже нет.
Подтверждённых действующих API keys, private keys или production passwords в проверенных
материалах не найдено. Это результат ограниченных детекторов и inspection, не гарантия
отсутствия любых секретов или уязвимостей.

Исправления подготовлены отдельно от незавершённых features: `codex/publication-audit`
от remote `mvp`. Видимость repository не менялась; история не переписывалась.
Локальные изменения исходного checkout сохранены. Новые commits используют GitHub noreply
email; local repository Git configuration обновлена для предотвращения повторной утечки.
Имена авторов старых commits также остаются видимыми в истории.

## Что именно проверено

Remote refs получены свежим `git fetch`, включая все advertised branches/tags и PR heads.
GitHub REST inventory дополнительно подтвердил три branches, отсутствие tags/releases/forks,
отключённые wiki/Pages/discussions и текущую visibility **private**.

| Remote ref | Проверенный SHA |
| --- | --- |
| `main` (default branch) | `e64fc5c403754e9b41edc1f28a95a347a2cafff8` |
| `domain` | `f6d6e097817cfeeaf07526b9aab69ed7904709f4` |
| `mvp` | `8b7f881b48aeb196afe7b1ec0eed4ac44bab4f34` |
| PR #113 head | `8b7f881b48aeb196afe7b1ec0eed4ac44bab4f34` |

- 166 reachable commits, 2823 уникальных blobs; отдельный raw-object поиск охватывал
  текст и доступные строки бинарных файлов. Gitleaks обработал 164 commits с анализируемыми
  diff, около 12.17 MB; различие с количеством Git objects не означает потерянные refs.
- 119 issue/PR bodies и 2 issue comments, без review/commit comments и releases.
- Все 70 возвращённых Actions runs: logs скачаны успешно; все 6 artifacts скачаны успешно.
  Gitleaks проверил примерно 41.53 MB распакованных logs/artifacts: 0 findings.
  Тексты GitHub discussions проверены отдельно: 0 Gitleaks findings.
- Privacy-поиск в этих GitHub текстах/logs не подтвердил персональных данных. Совпадение
  UUID тестовой H2 database было проверено и исключено как false positive.
- 91 изображение remote snapshot просмотрено в обзорных листах. Это обзор содержания,
  не полный OCR каждой надписи; мелкий текст и скрытое содержимое не сертифицированы.
  Найденный в PNG email относится к сертификату поставщика provenance, а не пользователю;
  эта информация не удалялась. PDF и MP4 имеют документированное происхождение test/demo
  fixtures. Fonts сохраняют существующие OFL notices. Дополнительно у 80 изображений
  очищенного snapshot проверены EXIF GPS/Artist/CameraOwnerName/BodySerialNumber/LensSerialNumber:
  этих полей не обнаружено. Это не исключает все другие виды metadata.
- Дополнительно Gitleaks проверил все доступные local refs: 179 commits с diff, 13.15 MB.
  `feature/frontend-v2` отсутствовала на remote; local `mvp` была впереди remote на 8 commits.
  Эти features не включены в audit-ветку и не отправлены вместе с ней.

Сырые API responses, найденные личные значения, scanner binaries и архивы сохранены только
в игнорируемом локальном `security-reports/`. Их нельзя включать в public export.

## Находки и исправления

| Находка | Исправление / оставшийся предел |
| --- | --- |
| Личные email в author/committer metadata | Новые commits используют noreply; старые требуют отдельного решения об истории |
| Домашние пути, private chat/project/attachment IDs | Обезличены 21 документ/JSON; исходные команды сохранены с `<user>` placeholders |
| 11 third-party research screenshots с фотографиями и именами | Убраны из snapshot, приватные локальные копии сохранены; ссылки ведут на источники. История всё ещё содержит изображения |
| Secret scan только текущего checkout | Новый workflow проверяет reachable history на всех push/PR, fetch-depth 0, redacted output |
| Повторное попадание личных данных | Новый privacy scanner и 13 regression tests; CI проверяет snapshot, отдельный manual режим проверяет историю |
| Неигнорируемые local assistant/config exports | Расширен `.gitignore` для local state, private key containers и DB dumps |
| Source-security CI упал при записи `/reports/semgrep.json` | Вывод JSON перенесён в stdout, файл создаёт host shell; container не получает root или writable source mount |
| Default branch `main` показывает практически начальный scaffold | Для работодателей нужно отдельно выбрать актуальный очищенный snapshot/default branch; автоматически не менялось |

Первая Gitleaks-проверка remote дала одно совпадение: текст строки ADR0040 про tenant/session
races, без credential. Добавлено **одно точное историческое fingerprint-исключение** с причиной,
а не исключение каталога или типа ключа. Повторный remote scan: 0 findings.
Два дополнительных local-only совпадения — синтетическая hexadecimal строка в mock transport
test и заведомо неверный secret-looking key в frontend negative test. Они не являются
подтверждёнными утечками. В audit-ветку исключения для этих ещё не отправленных features
не добавлялись; их будущий push может потребовать отдельного узкого triage.

Исторический privacy scan дал **416 locations**: 331 personal-email, 79 personal-home-path,
4 private-chat-id, 2 international-phone-candidate. Это число повторений по objects/metadata, а не 416 разных личных значений.
Exit code 1 здесь — ожидаемый запрет публикации исторического repository.

## Как это работает

`scripts/audit_publication.py` получает tracked файлы через Git. В snapshot-режиме он читает
blobs выбранного commit; `--worktree` читает только tracked рабочие файлы; `--history` читает
все reachable commits, author/committer/messages и исторические имена файлов. Поэтому rename
или удаление чувствительного файла не маскирует его прежнюю версию. Report содержит только
rule/path/object/line, без matched values и строк исходника.

Gitleaks отдельно проверяет форматы секретов, включая ограниченное recursive decoding
и архивы. Ни source, ни найденные credentials не отправляются scanner service.
GitHub authentication использовалась только в памяти read-only audit helper; credential
не пересылался на redirect host при скачивании archive.

## Архитектурное решение

Application layers, REST contracts, authentication, authorization, migrations и dependencies
не меняются. Изменены publication tooling, CI и документация. Это минимизирует runtime
regressions и не смешивает публичность исходников с готовностью реального SaaS.

Рассмотрены три варианта:

1. Просто удалить сведения последним commit — **недостаточно**: Git хранит старые blobs.
2. Переписать историю существующего repository — возможно, но меняет SHA, затрагивает PR #113,
   другие worktrees/clones и требует согласованного force push и очистки cached references.
3. Отдельный public repository из очищенного snapshot — рекомендуемый путь для портфолио:
   private development history сохраняется, новый public repo не наследует старые commits,
   discussions и Actions logs. Недостаток: история разработки не показывается автоматически.

Ни destructive rewrite, ни создание public repository этой проверкой не выполняются.
Для следующего шага нужен выбор пользователя: это разные, существенно влияющие на историю
и публичность действия. Подготовленный source archive позволяет сначала проверить результат.

## Security Review

**Threat:** утечка credentials через файлы, удалённые версии или CI artifacts.
**Protection:** full-history Gitleaks, отдельное чтение GitHub surfaces, redacted reports,
игнорирование private local outputs. **Verification:** remote scan после точечного prose
triage — 0; GitHub discussions и все доступные logs/artifacts — 0.

**Threat:** раскрытие личного email/домашнего пути и private context после visibility change.
**Protection:** обезличенный snapshot, noreply для новых commits, fail-closed historical gate;
public visibility сохранена выключенной. **Verification:** historical gate реально нашёл
historical locations; tests доказывают обнаружение email metadata, phone candidates,
удалённого и переименованного файла.

**Threat:** ошибочное игнорирование реального секрета после false positive.
**Protection:** только точный fingerprint конкретного prose finding; нет allowlist всех tests,
docs или token patterns. **Verification:** raw findings вручную сопоставлены с исходными commits.

**Threat:** scanner обходится silent error или выводит сам секрет.
**Protection:** ненулевой exit при невозможности чтения, invalid refs и findings; report не
содержит matched values. **Verification:** tests invalid ref/redaction/history/rename и
regression UUID-v4 vs chat identifier. Бинарные и неизвестные PII formats остаются ограничением.

**Threat:** CI исправляют чрезмерными правами ради записи report.
**Protection:** Semgrep пишет stdout, host перенаправляет его в файл; `contents: read`,
`persist-credentials: false`, existing container limits сохраняются. **Verification:**
inspection и YAML parsing выполнены; фактический Semgrep container run в этой среде не выполнен.

Authentication/authorization, IDOR/BOLA, SQL/command injection, CORS/CSRF и API resource limits:
новых runtime endpoints нет. Inspection существующего SecurityConfiguration подтвердил
explicit Bearer/stateless, guarded business routes, `allowCredentials=false`; prod configuration
использует DB password из environment и Hibernate `validate`. Это не заменяет pentest и новый
полный security test suite. Новых application dependencies нет.

## Runtime Flow

```text
Remote refs + GitHub surfaces
    → local read-only inventory
    → redacted secret scan + privacy rules + manual triage
    → isolated audit branch
    → anonymized tracked snapshot + regression tests
    → commit / private branch push / exact-SHA exposure CI
    → explicit choice of public snapshot or coordinated history rewrite
```

## Тесты и команды

Команды выполнялись из корня соответствующего checkout. `gitleaks` ниже — проверенный
официальный Gitleaks 8.30.1; Windows archive SHA256:
`d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e`.
В локальных командах использовался `security-reports/publication-audit/tools/gitleaks.exe`.

| Команда / проверка | Фактический результат |
| --- | --- |
| `git fetch origin '+refs/heads/*:refs/remotes/origin/*' '+refs/tags/*:refs/audit/tags/*' '+refs/pull/*/head:refs/audit/pull/*'` | PASS; PR #113 получен |
| `gitleaks git --redact --no-banner --log-opts='--remotes=origin --glob=refs/audit/*' --max-decode-depth=3 --max-archive-depth=3 --report-format=json --report-path=security-reports/gitleaks-remote-reviewed.json .` | PASS после единственного prose fingerprint; 164 commits |
| `gitleaks git --redact --no-banner --log-opts='--all' --max-decode-depth=3 --max-archive-depth=3 --report-format=json --report-path=security-reports/publication-audit/gitleaks-all.json .` | 3 reviewed false positives, 179 commits; raw run exit 1 |
| `gitleaks dir --redact --no-banner --max-decode-depth=3 --max-archive-depth=3 --report-format=json --report-path=security-reports/publication-audit/gitleaks-logs.json security-reports/publication-audit/github-archives` | PASS, 0 findings |
| `gitleaks dir --redact --no-banner --max-decode-depth=3 --max-archive-depth=3 --report-format=json --report-path=security-reports/publication-audit/gitleaks-discussions.json security-reports/publication-audit/github-text` | PASS, 0 findings |
| `python scripts/audit_publication.py --history --ref origin/main --ref origin/domain --ref origin/mvp --ref refs/audit/pull/113 --output security-reports/privacy-remote-history.json` | EXPECTED BLOCK: 3006 items, 416 findings, exit 1 |
| `python scripts/audit_publication.py --worktree --output security-reports/privacy-snapshot.json` | PASS, tracked cleaned snapshot; staged/final snapshot повторно проверяется перед export |
| `python -m unittest discover -s scripts -p 'test_*.py'` | PASS, 26 tests, 0 failures/errors, включая 13 новых privacy tests |
| `.\mvnw.cmd --batch-mode --no-transfer-progress spotless:check` | BUILD SUCCESS; 645 Java + 1 POM clean |
| `git diff --check` | PASS |
| YAML load через существующий `js-yaml@4.3.2`, assertions `on`, `jobs`, `permissions.contents == read` | Оба изменённых workflows PASS |
| `docker info --format '{{.ServerVersion}}'` | Docker daemon недоступен |

Выводы `HIGH`/`UNKNOWN` в Python unit tests относятся к намеренным fixtures report-gate tests,
а не к новому vulnerability scan dependencies. Полный `clean verify`, runtime startup,
container SAST/Trivy и новый dependency scan не запускались: application code не менялся,
Docker недоступен. Прежние Java/integration results не выдаются за новые.

## Security Tests и Edge Cases

- Personal email, home paths и international-phone candidates детектируются, но не отражаются в JSON values.
- Windows paths, JSON-escaped backslashes и WSL/macOS/Linux paths.
- Portable placeholders, CI runner paths и `example.com` не объявляются личными данными.
- Private chat-like IDs; отдельная regression для обычного H2 UUID-v4.
- `.env`/key containers запрещены, `.env.example` допустим.
- Удалённый файл обнаруживается в history при чистом latest snapshot.
- Rename blob не скрывает чувствительное historical filename.
- Email author/committer обнаруживается даже при чистом source.
- Untracked `.env` не попадает в snapshot scan/export; invalid ref не даёт clean result.

Дополнительно проверены отсутствие сломанных local links в изменённых Markdown files,
полнота списка всех 42 изменённых файлов, SHA256 всех 68 оставшихся manifest assets и отсутствие
tracked private env/audit/assistant-state файлов. Готовый archive без `.git` повторно прошёл
Gitleaks directory scan; committed snapshot — privacy scan (1189 files, 0 findings).

Первый implementation commit `04e9b564886f2ac156076dd28e79b116a594878c` отправлен только
в private `codex/publication-audit`; [exposure CI 40731541306](https://github.com/Sanyoghk/Cadence/actions/runs/40731541306)
завершился SUCCESS. Optional historical-privacy job step штатно skipped: historical privacy
проверена отдельно локально с ожидаемым BLOCK. Последующее расширение phone detector и этот
отчёт сохраняются отдельным commit; его exact-SHA CI проверяется после push, а результат
сообщается в итоговом ответе. Merge, visibility change и updates issues не выполнялись.

## Непроверенные аспекты

- GitHub Dependabot alerts API вернул 403, secret-scanning alerts API — 404. Это отсутствие
  доступа/доступности, **не** доказательство нулевого числа alerts. Secret values в GitHub
  Settings не читались и не экспортировались.
- Server-unreachable/dangling Git objects, inaccessible cached views, сторонние clones,
  private integrations и данные вне repository не могут быть исчерпывающе проверены.
- Текущий audit не выполнял exhaustive OCR/steganography analysis всех исторических media,
  полный line-by-line application security review, penetration test или live provider check.
- Последний pre-audit remote SHA имел failed source-security и cancelled verification runs;
  исторический green CI другого SHA не подтверждает текущую версию.
- Semgrep stdout fix требует выполнения соответствующего workflow с реальным container;
  только новый exposure workflow не доказывает исправность всех SAST/container gates.
- В snapshot сохранены product plans и engineering reports. Для публичного портфолио полезен
  отдельный editorial review коммерческих планов; наличие этих файлов не означает согласие
  на public publication. Лицензия на собственный проект автоматически не назначалась.

## Что мне важно понять

Зелёный secret scan не равен отсутствию личных данных: email автора лежит в Git metadata,
а не обязательно в исходниках. Чистый последний commit не делает чистыми предыдущие commits.
Публичность затрагивает весь repository, включая старые ветки и Actions history.
Поэтому безопаснее подготовить проверяемый snapshot и только затем выбирать публикацию.

## Что мне стоит изучить

1. **Достижимость объектов (Git reachability):** ветки, tags и PR refs удерживают старые blobs.
2. **Обезличивание (redaction):** отчёт показывает место проблемы, не копируя личное значение.
3. **Ложное срабатывание (false positive):** совпадение scanner нужно проверить по контексту;
   это не повод отключать класс проверок целиком.
4. **Закрытый отказ (fail closed):** ошибка scan не должна превращаться в разрешение publication.
5. **Происхождение материала (provenance):** собственный generated artwork и third-party
   research screenshots имеют разные условия дальнейшего использования.

## Production considerations и источники

Публикация source для работодателей не активирует production. Перед commercial deployment
остаются существующие live-auth/provider/security/operations/backup/recovery gates.
Для public portfolio нужен согласованный snapshot, свежие применимые CI checks, понятный
README о реализованном и pending scope, выбранная default branch и канал private disclosure.

GitHub прямо указывает, что при переходе private → public видимыми становятся Actions logs:
[Setting repository visibility](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility).
Удаление sensitive data требует учитывать переписывание SHA, PR references, caches и clones:
[Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

**Подтверждённые факты:** remote inventory, исторические privacy findings, scanner results,
26 Python tests, Spotless, содержимое diff и сохранённая private visibility.
**Инженерный вывод:** существующую историю пока не открывать; отдельный очищенный public
snapshot меньше затрагивает текущую разработку, чем массовый force push.
**Assumptions / дополнительная проверка:** completeness недоступных GitHub surfaces,
неизвестные secret/PII formats, полный runtime security и право на дальнейшую публикацию.

## Изменённые файлы

Полный список и назначение приведены ниже; application source и migrations отсутствуют в diff.

| File | Назначение |
| --- | --- |
| `.github/workflows/publication-safety.yml` | Secret-history и snapshot-privacy CI на всех push/PR; manual historical privacy gate. |
| `.github/workflows/source-security.yml` | Semgrep JSON через stdout без writable report mount. |
| `.gitignore` | Не включать local assistant state, private keys и data exports. |
| `.gitleaksignore` | Одно точное исключение исторического prose false positive. |
| `README.md` | Видимый publication blocker и ссылка на audit. |
| `SECURITY.md` | Private disclosure guidance и границы публикации. |
| `docs/design/ui-rework/craft/app-inspector-output.json` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/design/ui-rework/craft/inspect-app/report.json` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/design/ui-rework/craft/inspect-landing/report.json` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/design/ui-rework/craft/landing-inspector-output.json` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/design/ui-rework/generation-prompts.json` | Обезличенная provenance; references отмечены как private research. |
| `docs/design/ui-rework/implementation/error-inspector.json` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/design/ui-rework/implementation/landing-inspector.json` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/design/ui-rework/motion-report.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/design/ui-rework/prototype-report.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/design/ui-rework/reference-review.md` | Обезличивание, ссылки на внешние источники вместо сохранённых чужих снимков. |
| `docs/design/ui-rework/references/01-wedding-hero.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/01b-wedding-desktop-hero.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/02-wedding-transition.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/03-wedding-message.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/03b-wedding-desktop-message.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/04-wedding-about-motion.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/05-wedding-desktop-about.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/05b-wedding-about-line.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/06-wedding-story.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/06b-wedding-footer.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/design/ui-rework/references/07-loader-entry.jpg` | Убран сторонний research screenshot; приватная копия сохранена. |
| `docs/frontend-v2-claude-handoff.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/issue-111-completion.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/issue-121-pdf-identity-slice.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/issue-121-url-admission-slice.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/issue-121-url-consumption-slice.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/project-recovery-2026-10-08.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/prompt23-frontend-report.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/prompt24-identity-report.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/prototype-ui-adaptation-plan.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/publication-audit-2026-10-28.md` | Этот отчёт, evidence, ограничения и план publication. |
| `docs/scope-v2-inventory.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `docs/verification.md` | Домашние пути и/или private context IDs заменены placeholders; исторические test результаты не изменены. |
| `scripts/audit_publication.py` | Redacted privacy scanner snapshot/history/worktree. |
| `scripts/test_audit_publication.py` | 13 privacy/security regression tests. |
| `docs/design/ui-rework/artifact-manifest.json` | Исключены private reference captures; проверены существование и SHA256 оставшихся assets. |
