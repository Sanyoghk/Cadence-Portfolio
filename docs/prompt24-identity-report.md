# Frontend24 — Evidence и Brand Identity

Дата: 2026-10-18. Baseline `5dbd15f6f145adce51ca1d048b568a82bae99024`, ветка `mvp`.
**Status: QUALIFIED LOCAL repository scope.** Следующий frontend scope25 не начат. Live Clerk/OAuth/AI adapters — external blockers.
Foundation23 и visual follow-up23 сохранены. #3/#107 сверены read-only; issue3 шире этого шага.
Reference SHA256 `2f15d3a196af190b0d9732a32cb6223e1d05cf285a9d01b6a33a4d20a016a068` совпал.

## Что было сделано

Рабочий Brand Identity UI подключён к существующим API: TEXT/URL/file/social registration,
acquisition Job, exact review/consent, current read, admission/withdrawal history, durable
EVIDENCE analysis, copy successful receipt в Identity DRAFT, human edit, separate approval,
version history и direct/inherited citations. Manual draft явно отделён от provider output.
PNG/JPEG/screenshots показывают REQUIRES_PROVIDER; browser не выдумывает OCR/vision.
Live AI и social OAuth prerequisites не обходятся, production adapters остаются disabled.

## Как это работает

Пользователь открывает Workspace → Brand → Open Brand Identity. `IdentityWorkbench`
создаётся внутри существующего keyed tenant/session subtree; browser не хранит общий cache.
`Step1Upload` регистрирует материал, `AnalyzingPanel` читает фактические Job states,
`SourceReview` загружает exact textual preview и проверяет SHA256 UTF8. Confirmation отправляет
только tokens/checksum/immutable selectors именно этого preview. Любая ошибка consent
инвалидирует preview и checkbox;409 требует нового server read и нового human review.

Успешный ingest/CLEAN не является AI admission. История содержит immutable metadata;
кнопка current snapshot повторяет physical/access/scan/expiry/withdrawal checks на backend.
Withdrawal требует отдельного явного подтверждения и отзывает весь sourceVersion.

Generation capture использует свежий Brand lockVersion + один Idempotency-Key. При потере
ответа в открытом экране explicit retry сохраняет тот же body/key. Автоматических повторов
mutation нет. Job poll: каждые2s, максимум30 чтений за окно; после ошибки/окна нужен manual
refresh. OUTCOME_UNKNOWN/EXPIRED/SUPERSEDED/FAILED не становятся success. Browser не открывает
resultUrl: только фиксированный `/analysis-results/{resultId}` после coherent receipt.

`ExtractedPanel` показывает provider/model/prompt, warnings и exact citations. Copy выполняет
fresh Brand read и отдельный guarded backend command; successful copy блокирует повторную
кнопку в текущем компоненте, uncertain copy требует сверить history. Edit использует
`identity.lockVersion`; APPROVED/LOCKED edit создаёт новый DRAFT с lineage. Approval доступно
только для committed DRAFT, с отдельным checkbox точной revision/token. Pending edit скрывает
approval. Provider receipt остаётся original, human edit не выдаётся за новый AI output.

## Архитектурное решение

Сохранён ADR0040: static Next.js browser → Clerk SDK Bearer → Java API. Cookie/BFF не добавлены.
`ApiTransport` переиспользует существующие timeout15s/credentials omit/cache no-store/redirect
error/controlled Problem Details. Default response cap512KiB; только source list допускает
4MiB, чтобы вместить20 существующих TEXT references по20000 UTF16 символов, включая Unicode.

`generate-identity-contract.mjs` выводит44 reachable schemas/types из actual OpenAPI и полный
source fingerprint. `contracts:check` включён в lint. `contract.ts` валидирует используемый
subset schema: required, enum/const, nullable union, bounds, UUID, UTC/date-time, valid UTF8,
object/array/depth limits; безопасные integers, без потери точности. Это не универсальный
OpenAPI client generator. Fixed builders дополнительно связывают response IDs/tenant,
policy/kind, checksums и captured selectors. Unknown response fields отбрасываются;
request DTO выбираются whitelist. Новые dependencies/lockfile/fonts/media/CDN не добавлены.

Отсутствовавший history read добавлен как узкий bounded metadata endpoint, а не получение
старого текста через обход current eligibility. Endpoint использует существующий reviewed
Account → Workspace → Brand → Evidence lock order и actual active OWNER/membership.
Историческая withdrawal проецируется на sourceVersion, включая несколько context receipts.
SQL schema/grants/history неизменны; новые migrations не нужны. Existing PostgreSQL history
cap1000 ограничивает withdrawal metadata scan; page≤20 receipts сортируется reviewedAt/id DESC.
Внутри JPA грузится максимум20 существующих bounded receipt records; наружу text не выходит.

Alternative: перенести prototype seed/timers целиком — не даёт реальные review/audit guards.
Alternative: универсальная новая schema/state/form library — дополнительная dependency без
необходимости для этого bounded scope. Trade-off: небольшой scoped validator поддерживается
вместе с generator/check/tests; сервер остаётся единственной authorization boundary.

## Security Review

Threat: BOLA, stale tenant/session/late responses, revoked current membership.
Protection: backend scoped authorization + actual DB membership; keyed private tree, abort
and completion guard;401/403/404 unmounts evidence/Identity and requires fresh access read.
Verification: existing backend API/PostgreSQL negatives; browser foreign response, revocation,
session switch/late snapshot tests. Frontend guard не заменяет backend.

Threat: consent на изменившийся snapshot, kind confusion, withdrawn/expired/unclean artifacts.
Protection: exact SHA256 preview + separate unchecked confirmation + server immutable selectors;
separate TEXT/PDF/URL/SOCIAL routes, no fallback; current rechecks before egress/copy/approval.
Verification: corrupt/hash/Unicode/selectors tests, stale409 and withdrawal browser tests,
backend source/scan/expiry/lifecycle/concurrency qualification.

Threat: XSS, model/webpage instruction execution, arbitrary egress.
Protection: React text nodes/pre/blockquote, no innerHTML/markdown execution; only validated
six-digit palette color is used as CSS; provider fonts remain text. Fixed API paths, ignored
response URLs, no browser fetch of source URLs/private references. No script/provider added.
Verification: malicious markup shown literally, no DOM image and no external browser requests;
malformed result/IDs rejected; existing backend prompt-injection/source selection tests.

Threat: CSRF/CORS bypass, audit mass assignment, token disclosure.
Protection: unchanged explicit Bearer and omitted cookies; exact configured origin and route
methods; Idempotency-Key only analysis submission; backend role/resource checks unchanged.
No role/actor/provider/ownership fields in writable UI DTO. No token/content logging/storage.
Verification: CORS preflight/default-deny/cookie-only tests, DTO whitelist assertions, static
export test-fixture isolation. Hosting CSP/TLS remains deployment qualification.

Threat: duplicate uncertain writes, fake successful/zero-cost outcome, unbounded polling/input.
Protection: single in-flight action, bounded abortable transport/pagination/poll windows,
explicit terminal states, same-key submission retry, no automatic copy/edit retry; existing
server budgets/lease fencing/limits unchanged. No inferred cost/moderation/live observation.
Verification: unknown Job, malformed receipt, retry key/body, duplicate UI action, SQL/runtime
backend concurrency/recovery tests. Reload does not reconstruct an unknown submission key;
user must inspect server state before starting another operation.

## Runtime Flow

```text
Clerk session → scoped keyed Onboarding/IdentityWorkbench
    → EvidenceIdentityApi fixed route + whitelist + Bearer ApiTransport
    → Spring Security / Controller DTO
    → existing review / durable generation / Identity use cases
    → actual membership + lifecycle + immutable receipt + optimistic versions
    → PostgreSQL; storage/scan/model ports under existing policy
    → bounded validated response → escaped reviewable UI
```

Новый history flow: GET → EvidenceAdmissionController.history → ReviewEvidenceUseCase.history
→ parent locks/current OWNER → scoped bounded repositories → metadata PageResponse/no-store.

## Тесты и фактические результаты

Frontend, из `frontend/` (Node24.19.0 / pnpm11.25.0; dependencies/lockfile не менялись):

```powershell
pnpm contracts:generate
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm verify:export
pnpm test:e2e
pnpm peers check
pnpm audit --json
pnpm test:live
```

PASS:44 schemas синхронизированы; ESLint/Prettier без warnings, TypeScript; **58unit/3files**,
**56Chromium/0failed/skipped**, включая23 новых Identity browser scenarios,23 новых API
trust-boundary cases,3 themes/mobile/axe, editor/approval desktop/keyboard, no external request.
Static export:21HTML/JS/CSS artifacts,3 bounded local WOFF2, fixture/loader/CDN/stack markers
отсутствуют. Build static routes unchanged. Peers0issues; npm audit263entries/0vulnerabilities.
`test:live` — **BLOCKED, exit1 до network**: missing development Clerk keys/test user/config;
это не passing live signup/login.

Backend focused final50/50PASS,0failures/errors/skips:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=EvidenceAdmissionApiIntegrationTest,OnboardingCorsIntegrationTest,PostgreSqlEvidenceAdmissionTest' test
```

Первый full run:2275reported/0failures/errors/4Windows filesystem skips, BUILD SUCCESS,
21:14min. Второй full после CORS narrowing/archive regression:2275/0failures/errors/4Windows skips, BUILD SUCCESS19:11min.
Следующая auth correction найдена actual HTTP E2E и квалифицируется новым clean full без объединения XML runs:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dspring.test.context.cache.maxSize=8' spotless:check clean verify
```

Final isolated clean full: **2286 reported / 2282 executed PASS / 0failures/errors**, 4Windows filesystem skips covered Linux37/37;182suites, BUILD SUCCESS20:48:53+01:00,19:06min. Spotless/JaCoCo PASS: line94.22%,branch71.37%. Новый auth regression входит в этот запуск, старые XML не объединялись.

Применимые независимые проверки, команды реально запускались:

```powershell
python -m unittest discover -s scripts -p 'test_*.py'
.\scripts/run_linux_storage_tests.ps1
docker build --tag cadence-renderer-prompt24 docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-prompt24 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\mvnw.cmd --batch-mode --no-transfer-progress org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=prompt24-current-bom.cdx' '-DoutputDirectory=var'
.\var/osv-scanner-2.5.1.exe scan source -L var/prompt24-current-bom.cdx.json --all-packages --format=json --output-file=var/prompt24-osv-results.json
python scripts/verify_dependency_scan.py var/prompt24-current-bom.cdx.json var/prompt24-osv-results.json
```

Python фактически через bundled executable
`C:/Users/<user>/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`.
PASS: scripts10/10, Linux filesystem37/37 без skips, renderer29/29. SBOM194/OSV0findings,
independent inventory verifier PASS; scanner2.5.1 SHA256
`25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6` сверён.
SBOM goal не выполнял compilation и не менял classes текущего full run.

Финальные дополнительные команды (после successful full, на новом JAR):

```powershell
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts/run_linux_storage_tests.ps1
.\var/prompt24-startup.ps1
# из frontend/
node scripts/qualify-identity-stack.mjs --isolated-local
```

Все mandatory PostgreSQL/MinIO/ClamAV/renderer/full pipeline reports без required skips PASS.
Linux37/37PASS. Packaged health200;45 anonymous protected operations401, fixed Bearer challenge,
cookie-only401/foreign origin403; exact-origin PATCH/key/history preflights200 без credentials.
Owned startup process stopped/port18100 released.

Actual HTTP Chromium → unchanged backend responses → real JwtDecoder/JPA/H2/local fake:
Workspace/Brand→TEXT registration→stale409→fresh exact consent→current read→durable generation
→DRAFT→human edit→approval→new revision→immutable old history→withdrawal→denied approval/current
read→foreign denial and private UI removal PASS. Bridged statuses200×43/201×6/202×2/403×1/409×1;
**0unexpected401/5xx**. Direct guarded stale/withdrawal/foreign requests also asserted.
RSA signing key only in memory; fixed loopback issuer/JWKS and fresh owned backend/fixture;
all3 owned ports released. This uses synthetic verified JWT, not actual Clerk login.
Provider output is backend local fake and visibly DEMONSTRATION; no live provider inference.
`var/prompt24-identity-approved-http.png` captures actual approved card from this run.

Fresh SBOM/OSV rerun after accepted JAR:194 dependencies/0findings, independent verifier PASS.
745 backend/build hashes match the captured source; no product changes after qualification.

Значимые intermediate failures и их исправления:

- Первый focused backend имел fixture SecurityContext ошибки после MockMvc; context handling
  исправлен, assertions role/scope не ослаблялись. Повтор50/50PASS.
- Первый browser lifecycle locator не находил prefilled textarea через exact label;
  использован доступный role textbox/name. Реальный edit/approval затем прошёл.
- Axe нашёл scrollable generated JSON без keyboard focus. Добавлен tabIndex и label;
  исключений axe rule нет, повтор21/21 и полный54/54PASS.
- Один intermediate browser run совпал с редактированием/Vite parse error и не считается pass;
  финальные gates выполнены на стабильных files, timeout regression реально завершился примерно15s.
- Interim lint обнаружил оставшийся unused ref: удалён, финальный lint0warnings.
- Внешний resultUrl в новом unit negative ожидаемо отвергается по actual OpenAPI;
  проверка усилена coherent terminal/result/policy/draft receipts, а не разрешением URL.

Warnings: existing Mockito/JVM/Hibernate/JSON/S3 notices, Hikari reconnect после закрытых
containers и ожидаемые SQL/security-negative renderer denials; Node NO_COLOR/FORCE_COLOR
notice. Они не заменяют pass/failure results. Production Linux kernel/AppArmor/deployment
qualification не приписывается Windows local runner.

## Security Tests и Edge Cases

TEXT/PDF/URL/social kind-specific positive paths; corrupted checksum/Unicode/foreign IDs;
selectors/actor whitelist; absent/unavailable model/storage/scan; stale409 fresh consent;
sourceVersion withdrawal и inherited approval rejection; terminal unknown Job; edit conflict;
uncertain same-key submission; no automatic approval; tenant/session unmount/late response;
image/provider gap; keyboard focus, mobile overflow, three themes/axe; no external egress.
Existing backend full suite проверяет scoped API/SQL/runtime, physical and lease/race guards.

## Изменённые файлы

См. [полный manifest](prompt24-changed-files.md). Manifest включает каждый tracked/new
product/test/doc файл slice; generated artifacts/logs/screenshots лежат в ignored var/target.

## Непроверенные аспекты

Live Clerk login/signup и actual cross-origin deployed JWT flow требуют development keys,
test user и deployed origin/TLS. Live social binding #32/#33 и AI adapter #120 не qualified.
Chromium tests используют технически отдельный Vite fixture + API responses и исполняют
production components; отдельный actual HTTP harness исполняет real JwtDecoder/backend/H2 с временным test issuer, а PostgreSQL suite отдельно проверяет SQL/runtime guards. Это не
полный live browser-to-Clerk-to-provider demo. Не заявляется production AI readiness.
Визуальные inputs вне supported textual contracts остаются gap; backend не заменяется
browser fake. Multi-browser/WebKit/Firefox и коммерческий load/pilot здесь не qualified.

## Что мне важно понять

Согласие относится к конкретным данным и версиям, а не к названию файла или успешной загрузке.
Поэтому UI не может после409 молча повторить consent. История объясняет прошлое; current
eligibility определяется новым guarded read. Frontend делает ошибку заметной, backend
проверяет право и целостность независимо от поведения браузера.

## Что мне стоит изучить

1. Optimistic locking — expectedVersion предотвращает запись поверх чужого изменения.
2. Immutable provenance — исходный receipt/citation сохраняется после human edits.
3. Idempotency — один key/body повторяет одну generation operation, не новую оплату/работу.
4. Trust boundary — внешние JSON/model/source данные валидируются перед использованием.
5. Race condition — поздний ответ или отзыв доступа не должен вернуть старый private UI.

## Production considerations

Нужны live auth/provider qualification, deployed origin/TLS/CSP/security headers, current
membership provisioning, production load/rate and operations gates. Existing provider-disabled
configuration сохранена; никаких credentials/CDN/privacy/cost decisions этим шагом не принято.
Deployment/merge/push/remote issue closure не выполняются. #3 остаётся open для25–27/live gates.

Подтверждённые факты: код/contracts и явно перечисленные ниже фактически выполненные checks.
Инженерные выводы: scoped immutable workflow сохраняет существующие backend boundaries.
Assumptions/remaining: live credentials/provider/hosting behavior требуют отдельной проверки.

### Уточнение history projection

withdrawnBy/withdrawnAt — последний фактически записанный withdrawal всего sourceVersion,
включая withdrawal другого context receipt той же версии; сортировка withdrawnAt/id DESC.
Это effective status projection, а не выдуманное событие для каждой строки admission.
Original review/withdrawal records не переписываются. Endpoint отдаёт paged admission history
с effective withdrawal, а не неограниченный экспорт всех audit events.


## Дополнительный auth regression из actual HTTP E2E

Первый actual HTTP run прошёл Identity lifecycle, но зафиксировал один лишний401 при
параллельном first login: два запроса одновременно не находили CadenceUser и пытались
вставить одинаковый externalSubject. Unique constraint сохранял одну запись, проигравший
запрос падал при commit. Это реальный найденный reliability gap, не provider blocker.

`CadenceIdentityService.resolve` теперь использует TransactionTemplate: principal возвращается
только после завершения transaction. DataIntegrityViolation при first provision допускает
один fresh lookup ПОСЛЕ rollback. Возвращается только фактически committed active account,
его stored role и current memberships. Losing JWT claims не объединяются с winner permissions;
повторного insert/retry loop нет. При ambient rollback-only transaction recovery запрещён,
missing/inactive winner даёт controlled authentication failure. Subject bounded255, без
silent trim/ISO controls; не создаются алиасы разных opaque JWT subject values.

Threat: ложный401 при конкурентном bootstrap либо повышение прав через losing claims.
Protection: DB unique key + completed transaction + bounded fresh current-account read;
active/role/membership из DB, не из проигравшего JWT. Existing signature/issuer/audience/azp/
expiry validation и business resource checks не меняются. Production auth bypass не добавлен.
Verification: deterministic PostgreSQL test блокирует оба INSERT через test-only table SHARE
lock после successful absent reads, затем выпускает их. До fix:1test ERROR с unique collision;
после:оба principal равны одной committed DB identity/role/единственному workspace из двух
конкурирующих claim sets. Unit10 проверяют inactive/missing winner, failed ambient transaction,
noncanonical/control/oversized subjects. Existing security/JWT/CORS также проходят.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostgreSqlPersistenceTest#concurrentFirstAuthenticationRecoversCommittedIdentityWithoutMergingClaims' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=CadenceIdentityServiceTest,PostgreSqlPersistenceTest,SecurityIntegrationTest,JwtDecoderTest,OnboardingCorsIntegrationTest' test
```

Первый command RED/exit1 до fix; второй GREEN46/46,0failures/errors/skips, включая real
PostgreSQL9/9 и10unit. Original history/CORS full2275PASS был выполнен до auth correction;
после неё выполняется отдельный final clean full run. Actual HTTP harness усилен assertion
об отсутствии лишних401/5xx и повторяется на новом qualified JAR. SQL23505 при детерминированном
competing insert допустим в server diagnostic log; клиент получает coherent principal без
SQL/subject/JWT disclosure. Auth протокол и принятая ADR0040 архитектура сохранены.


### Почему recovery находится снаружи transaction

При PostgreSQL unique violation transaction больше не пригодна для следующего SELECT.
Кроме того, `@Transactional` proxy может выполнить commit после возврата тела метода —
внутренний try/catch не поймает такой commit failure. TransactionTemplate завершает commit
или rollback до возврата из execute; только затем wrapper делает один fresh lookup.

JVM-only mutex не защищает несколько backend instances. Vendor-specific upsert потребовал
бы отдельного PostgreSQL/H2 SQL contract для user и memberships. Здесь уже существующий DB
unique key остаётся источником serialization; bounded recovery сохраняет portable JPA и
принятые first-committed account permissions. Последующие resource operations по-прежнему
проверяют current DB authorization; восстановление principal не отменяет revocation guards.

### History остаётся доступной при unsupported current file

Отсутствующий/unknown current mediaType не становится выдуманным PDF или visual provenance.
UI показывает UNSUPPORTED, PNG/JPEG показывают REQUIRES_PROVIDER. При этом metadata history
и withdrawal не зависят от поддержки нового preview: original admission может существовать
до изменения/недоступности текущего source. History-only SourceReview скрывает новый preview/
consent; current reads используют kind из immutable receipt и backend guards; withdrawal
остаётся доступным по актуальной авторизации. Browser regression проверяет legacy FILE с
NULL mediaType и существующим PDF receipt: нет fabricated preview, отзыв версии работает.


### Source list byte bound

Source list cap4MiB учитывает не только UTF8 Unicode, но и worst-case JSON escaping:
20×20000 control characters сериализуются примерно в2.4MB (`\u0000` etc). Допустимая исходная
регистрация не обязана уже быть AI-eligible; metadata list должна оставаться доступной.
Regression первоначально падал на прежнем2MiB cap, затем проходит; over4MiB response явно
отвергается. Page20/field20000/default512KiB/15s bounds сохранены. Это только ограничение
browser response buffer; server model input budget и source admission policy не меняются.


## Acceptance mapping24

| Требование | Реализация / evidence |
| --- | --- |
| Registration / actual Job status | Step1Upload + AnalyzingPanel, actual API/typed validation; TEXT/PDF/URL/social positive browser paths; unsupported visual/legacy states |
| Exact review → consent → current read | SourceReview + SHA256/selectors; stale409 resets confirmation; backend original scan/provenance/current eligibility remains authoritative |
| Withdrawal / history | Bounded metadata endpoint, sourceVersion-wide status, actor/time; current unavailable/unsupported file does not hide historical withdrawal |
| Generate → DRAFT → edit → separate approval | Existing durable EVIDENCE Jobs + fresh Brand copy + expectedVersion; actual HTTP local backend lifecycle and browser contract tests |
| Citations / immutable revisions / demo | Original/inherited provenance and scoped origin receipt; approved edit → new DRAFT; original approved content unchanged; no automatic approval |
| Denials / races | Scoped responses, revoked/foreign data removal, late response guards; withdrawn/expiry/storage/model/unknown output errors; PostgreSQL/backend tests + Chromium negatives |
| Accessible prototype UI | Shared Cream/Dark/Sage shell, local licensed fonts, responsive/keyboard/axe; actual HTTP approved-card screenshot in local var |
| Security/reliability regression | Concurrent first authentication red→green, stored winner permissions,10unit + deterministic PostgreSQL; no auth protocol/bypass change |
| Qualification / documentation / commit | Final commands/results below and full file manifest; local only; whole#3/live/provider acceptance not claimed |

## Visual adaptation

Узнаваемые typography, paper/cards, border/focus и Cream/Dark/Sage переиспользуют qualified23.
Step1Upload теперь показывает реальные типы регистрации и supported boundaries.
AnalyzingPanel показывает server status вместо prototype timers/progress; ExtractedPanel
показывает actual result и citations до создания DRAFT. Identity palette/voice/caption,
manual review/edit и explicit approval следуют actual contract, без seed content.

Deliberate differences: provider-dependent image interpretation недоступна; URL/social имеют
отдельные typed snapshots; history/current eligibility и optimistic conflicts видимы.
Technical provenance собрана в details/history; main action copy сокращён до понятных creator
действий. Agency/iframe ownership, old React/Babel/loaders и external font requests не перенесены.
Local evidence: `var/prompt24-{cream,dark,sage}-mobile.png`,
`var/prompt24-identity-desktop.png`, `var/prompt24-identity-approved-http.png`.
Artifacts/logs ignored и не являются опубликованными remote CI attachments.

### Recovery deadline verification

Один промежуточный full run после auth fix имел2 failures в
PostgreSqlAiRecoveryProcessTest.crashAfterCommitKeepsOneResult: child/restarted JVM не завершились
в прежние90s. Зафиксированы почти599s для этого suite и замедление host во время параллельной
Next build. Точная причина не доказана; resource contention — инженерная гипотеза, не факт.
Собственный уже failed run остановлен после сохранения отчёта/child logs; он не pass даже
если shell после forced stop вернул0. Assertions, deadlines и source не менялись для retry.

Изолированный `-Dtest=PostgreSqlAiRecoveryProcessTest test`:8/8PASS,198.5s (whole Maven3:25).
Probe `-DskipTests package` использовался только для сборки executable JAR под actual HTTP
qualification, не как доказательство full tests. HTTP probe нового auth source: coherent
lifecycle,0extra401/5xx; один реальный H2 unique collision recovered. После всех frontend gates
запущен новый clean full без параллельного build; его recovery suite8/8PASS,198.6s.
Последний полный результат и повтор JAR smoke приведены в основной секции tests.

## Git / remote status

Finished slice сохраняется одним local commit `feat(frontend): qualify evidence and Identity workflow`.
Commit включает соседний обнаруженный bootstrap reliability fix, необходимый для actual
onboarding/evidence workflow. Source baseline5dbd15f;745 backend/build hashes verified stable.
Нет push/new remote CI/issue edits/closure/merge/deployment. Read-only #3/#107 и remote `mvp`
перепроверены; remote mvp остаётся `a283382c8191bfa4020d0fb3c141493cabb9f821`, #3/#121 open.
Факт local qualification не подменяет whole-issue acceptance и exact-pushed-SHA CI.

### Cross-platform line endings

`.gitattributes` фиксирует LF для frontend text и OpenAPI; WOFF2 явно binary. При текущем
`core.autocrlf=true` это предотвращает изменение raw OpenAPI fingerprint/generated file и
Prettier drift после Windows checkout. Текущие qualified source bytes не менялись; это
Git metadata, а не изменение review snapshot/checksum normalization. Source review text и
original uploaded bytes не нормализуются этим правилом. `git check-attr`/contracts/export
проверены после правила; все3 font artifacts сохраняют binary handling.
