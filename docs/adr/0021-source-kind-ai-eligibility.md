# ADR0021 — Допуск URL, images и social snapshots к AI

Актуализация 2026-10-15: [ADR0027](0027-backend-first-ai-adapters-last.md) утверждает backend-first, AI adapters last
и прямой image → AI → Identity без обязательного отдельного OCR/interpretation stage.
#121 остаётся OPEN; visual gap не отменён и не блокирует независимые backend scopes.


Дата: 2026-10-14. Scope: задание 02, #121. Baseline: `4f4faab9d1cd477f95d075072e7810ee9f70a3d8`.
Статус: **ACCEPTED — пользователь явно утвердил P1–P3 в этом чате 2026-10-14**.
Технические contracts ниже подготовлены; это не runtime implementation и не разрешение egress.
Prerequisite 01 qualified: [PDF Identity report](../issue-121-pdf-identity-slice.md).

## Утверждённое product решение

- **P1, утверждено:** URL title/description/extracted text и текстовая проекция social snapshot
  поддерживаются policy после exact human review. Social projection содержит accountName,
  biography и posts: publishedAt, text, TikTok title; accountId/postId/API version остаются в
  локальном provenance, не отправляются модели. Пустые поля не дополняются выдуманными данными.
  Изменение набора полей требует новой projection policy и нового review.
- **P2, утверждено:** PNG/JPEG uploads и URL screenshots остаются обязательными MVP inputs,
  но визуальное понимание имеет состояние REQUIRES_PROVIDER. Сейчас не выбирать vision/OCR,
  не подменять изображение filename/metadata и не объявлять #121/MVP полностью завершённым.
  Выбор vision versus OCR и privacy qualification остаётся отдельным решением #120.
- **P3, утверждено:** snapshot считается историческим материалом «на дату получения»;
  возраст текста сам по себе не задаёт произвольный TTL. Доступ, withdrawal, binding expiry,
  scan freshness и физическая retention проверяются независимо. Никакой новый external AI
  egress не разрешён этим ADR. Срок хранения пользовательских данных, регион, provider retention,
  deletion/backups и право передачи конкретному processor должны быть утверждены до live activation.
  Если продукту нужен максимальный возраст marketing evidence, его следует явно выбрать;
  нельзя выдавать 24-hour malware freshness за свежесть бренда или retention consent.

Утверждение P1–P3 разрешает contracts/implementation 03–05 в указанных границах;
оно не выбирает provider/model и не утверждает передачу реальных данных третьей стороне.

## Фактический current HEAD

Website metadata (`WebsiteMetadata`, `BrandEvidence.recordWebsiteMetadata`) извлекаются локально
из bounded fetched bytes: `website-v2`, title ≤300, description ≤2000, text ≤16000 UTF-16 units,
warnings ≤500, version ≤32; HTML/plaintext ≤2 MiB. Скрипты не выполняются. Inline extraction
не является immutable `EvidenceArtifact` с original scan ID: extraction происходит до допуска.
Legacy snapshots нельзя молча переименовать в v2 или переизвлечь через network.

URL registration checksum = SHA-256(UTF-8(`URL\n` + normalized URL)); это **не** checksum HTML.
Физический fetched checksum хранится отдельно, вместе с fetchedAt/media type/reference.
Inline metadata имеют extractedAt/version/warnings. Persisted evidence не доказывает final
redirect URL, HTTP ETag или remote revision; не добавлять их в provenance задним числом.

`EvidenceArtifact` URL screenshot — private PNG 1280×720, отдельный output checksum/generation,
renderer version, source version/checksum, original CLEAN scan, Job/attempt/lease provenance.
PNG header/IHDR verification не доказывает полноценную безопасность image decoder.
FILE PNG/JPEG uploads проходят file/scan pipeline, но не OCR и не semantic image analysis.
PDF_TEXT уже имеет admission → analysis → Identity contracts ADR0017–0020; image-only PDF
не получает текст посредством придуманного OCR.

Official Instagram/TikTok adapter сохраняет normalized `approved-social-v1`, не raw HTTP body.
Одна страница ≤20 posts, canonical JSON ≤20000 Java chars, сортировка по строковому post ID.
Instagram biography/caption ≤300 codepoints; TikTok description ≤300, title ≤100;
TikTok biography отсутствует и остаётся пустой. Account name ≤128 Java chars.
Media bytes, linked media, comments и visual observations не получаются.
`EvidenceSocialOrigin` связывает provider/account/API version/capturedAt/checksum/providerCallId
с server-issued receipt. Это отдельная trust boundary (граница доверия), не поля JSON клиента.
При dedup сохраняются исходные Evidence/origin/capture time; новый запрос не омолаживает snapshot.

**Обнаруженный gap:** `RegisterSocialBrandEvidenceUseCase` регистрирует canonical JSON как TEXT.
`EvidenceAdmission.reviewableText` и `EvidenceAdmissionRepository` не исключают social origin.
Поэтому generic TEXT review/analysis путь может принять social JSON по обычным TEXT правилам;
typed social projection и повторная проверка binding там не реализованы. Это inspection finding,
не новый исполненный regression test. Production generator остаётся fail-closed, но gap должен
быть закрыт в 05 до объявления social consumer qualified. Похожий JSON ручного TEXT никогда
не становится trusted social source. Старые receipts сохраняют фактическую generic attribution.

## Матрица policy и реализации

SUPPORTED означает допустимость по утверждённой policy, а не наличие feature на current HEAD.
P1–P3 утверждены. REQUIRES_PROVIDER сохраняет MVP gap; future guards ещё не реализованы.

| Source / представление | Approved policy | Current runtime / model input | Зависимость |
| --- | --- | --- | --- |
| Manual TEXT | SUPPORTED | bounded reviewed text, существует | сохранить ADR0013–0016 |
| PDF_TEXT | SUPPORTED | exact reviewed artifact text, существует | 01 qualified |
| URL extracted title/description/text | SUPPORTED | consumer отсутствует; только bounded text, без HTML | 03 admission, 04 consumers |
| URL original HTML/plain bytes | UNSUPPORTED как прямой AI input | private source для проверки extraction; FETCHED не consent | 03/04 physical guards |
| URL screenshot | REQUIRES_PROVIDER | private PNG artifact, text-only port не принимает | 05 + #120 |
| Uploaded PNG/JPEG | REQUIRES_PROVIDER | original bytes + typed metadata, semantic consumer отсутствует | 05 + #120 |
| Image OCR text | REQUIRES_PROVIDER | OCR отсутствует; отдельная derived text policy/review | #120, затем 05 |
| Official social text projection | SUPPORTED | сейчас generic TEXT loophole; typed consumer отсутствует | 05 |
| Social validated origin metadata | SUPPORTED только для local provenance | IDs/receipt не являются brand insights/model instructions | 05 |
| Social photos/video/screenshots | REQUIRES_PROVIDER и ingestion contract | bytes не ingestятся текущим social adapter | 05 scope gap + #120; #32/#33 при OAuth |
| Client-claimed provider provenance, raw remote JSON, arbitrary model URL fetch/tools | UNSUPPORTED | нельзя повышать доверие по содержимому JSON или загружать links из prompt | 03–05 negative gates |

Unsupported representation не отменяет поддерживаемый product input: URL по-прежнему даёт
text + потенциальный screenshot; image остаётся visual MVP gap, а не переименовывается в metadata AI.
Новый upload/format за пределами PDF/PNG/JPEG не получает поддержку этим ADR.

## Общий immutable snapshot contract

Каждый новый admission выбирает server-issued snapshot, а не client-provided content/hash.
Хранить tenant IDs, evidenceId/sourceVersion/source checksum/capturedAt, representation kind,
projection/extraction policy version, exact model-visible payload и SHA-256 его UTF-8/bytes,
все warnings, createdAt и immutable provenance links. Отдельно хранить review actor/time,
review policy/version и exact selectors; отдельное append-only withdrawal отзывает будущий допуск.
SourceVersion и payload checksum решают разные задачи: version ловит изменения состояния,
checksum связывает конкретные bytes. Совпадение одного не заменяет другое.

URL дополнительно связывает fetched checksum/time/media/private generation ledger, extractedAt,
website extraction version и exact title/description/text/warnings. Проектируемый 03 snapshot
должен быть immutable и иметь собственный ID; нынешние inline fields сами по себе не такой ID.
Проверка private bytes + reproducible extraction той же версии вне долгой DB transaction
подтверждает текст. Если exact extractor version недоступна, отказ, а не повторный network fetch.
Для legacy без достаточного происхождения нужен новый явный acquisition/review, без backfill consent.
Capture-time CLEAN scan связывается при создании review snapshot; его нельзя называть scan,
который якобы предшествовал уже существовавшей inline extraction. Job/attempt links сохраняются,
только если действительно есть соответствующая immutable receipt; недостающие поля не выдумываются.

Text projection (`url-text-v1` / `social-text-v1` в поле format) сериализуется versioned
deterministic JSON с фиксированным порядком полей:
URL: `format,title,description,text`; social: `format,accountName,biography,posts`,
post: `publishedAt,text,title` (title только для TikTok). UTF-8 без BOM, без whitespace вне строк;
escaping и round-trip fixtures фиксируются реализацией и policy version. Quote citations
ссылаются на exact field path + exact substring reviewed value; presentation может экранировать,
но не менять цитируемый оригинал. Snapshot hash покрывает весь payload, provenance хранится отдельно.
Server mapping post index → original post ID остаётся локальным и immutable.
Это расширение нынешнего substring-only citation contract: 04/05 должны version новые field-path
selectors, не менять смысл legacy citations или `text-evidence-v1` digest задним числом.
Полный selected batch сохраняет текущие 1–5 sources / 20000 UTF-8 bytes; JSON overhead учитывается.
Oversize отклоняется до model call; нет скрытого дополнительного truncation после review.
Payload без непустых содержательных text/title/description/name/biography полей не допускается:
одни timestamps/format/IDs не являются материалом для brand analysis. NO_TEXT warning не скрывается;
при непустом title/description reviewer видит, что body text отсутствует.
Сам extractor уже bounded: предупреждения и факт неполноты должны быть видимы reviewer.

Visual snapshot обязан связывать original checksum/generation/scan с exact derivative checksum,
dimensions/media type/decoder or renderer version/Job/attempt и отдельным human preview/review.
До выбора decoder/provider/OCR не фиксируются выдуманные preprocessing version, model image
limits или output quality. OCR создаёт новый derived text snapshot с engine/version, warnings,
исходным image hash и новым review; он не переносит consent с raw image автоматически.

Social snapshot связывает EvidenceSocialOrigin/providerCallId/account/provider/API version,
original canonical checksum (TEXT registration prefix учитывается), capture time и schema version
с новым projection ID/hash/version. На use проверяется current approved binding для **того же**
account/provider/brand, scopes и expiry; provider-only lookup недостаточен при смене account.
Необходимый local policy port не должен делать HTTP. Сетевая повторная выгрузка — отдельный acquisition.
Parsing strict: unknown format/invalid shape/duplicate keys/trailing data отвергаются.
No origin → ordinary untrusted TEXT, никогда trusted social. Origin present → typed route,
никакого fallback в generic TEXT при broken/expired origin. Migration не переписывает старые receipts;
новые generation/copy/approval обязаны учитывать этот запрет, включая inherited sources.

## Freshness, withdrawal, expiry и retention

- FILE/URL: physical read/hash нужных original/derivative bytes, retained ledger и отсутствие
  tombstone; exact original scan receipt плюс **latest** scan matching source/version/checksum.
  Latest ERROR/INFECTED перекрывает прежний CLEAN. Scan policy default PT24H, configurable
  1 second–7 days, SQL upper bound 7 days; expiry во время операции отменяет publication.
- Social/manual text: malware scan не выдумывается для normalized DB text. Social требует
  validated immutable origin, active same-account binding и explicit review; current fetch
  binding требует approved/scopes и expiresAt > now+30s. Consumer должен соблюдать не менее
  строгий запас, повторить проверку перед egress/publication. Age текста показывается пользователю.
- Review: actual active OWNER/member видит exact bounded payload, source kind, capture time,
  warnings, destination scope и gaps. Действие explicit; FETCHED/CLEAN/renderer success и
  provider approval не дают AI consent. Privacy approval для external processor — отдельный gate.
- Withdrawal: новые egress/generation/copy/approval запрещены для отозванного snapshot/source
  scope по receipt. Historical authorized provenance read сохраняется; withdrawal не стирает
  immutable APPROVED history и не «отзывает» уже переданные provider bytes. Re-review должен
  создавать новый receipt и не реактивировать старый автоматически. Текущий withdrawal scope
  охватывает весь evidence/sourceVersion; новый receipt той же версии не обходит отзыв.
  Возобновление требует предусмотренного versioned re-review/new snapshot transition.
- `QuarantineLifecyclePolicy.orphanRetention=PT24H` относится к orphan objects; referenced
  evidence сохраняется до отдельной deletion policy. Это **не** privacy retention срок.
  Social quota ledger удаляет только unreferenced receipts старше 90 days, batch ≤500;
  referenced origin остаётся. Эти operational rules не заменяют user data deletion policy.
- Expired/tombstoned/missing bytes запрещают новое использование даже при сохранённом consent.
  Требуется явный новый snapshot/review; missing bytes нельзя восстановить незаметным refetch.
  Live activation блокируется до утверждения retention/deletion/backup policy и provider terms.

## Flow и defense in depth

OWNER request → actual account/membership/tenant checks → typed source dispatch → current
review/withdrawal/provenance/scan capture → bounded private I/O вне DB transaction → fresh
locked validation → server-selected bounded model input → provider port (production disabled)
→ output/citation validation → fresh all-source publication fencing → immutable receipt
→ explicit Identity copy → direct/inherited approval с повторными source guards.

Соблюдать существующий parent-first lock order, deadlines/lease fencing для применимых jobs.
Новые DB constraints и append-only receipts защищают metadata при обходе application layer;
SQL не может проверить storage bytes. Storage IAM/immutability и ограниченное окно между physical
witness и final transaction — отдельная operational qualification, не обещание distributed atomicity.

## Threat model

Защищаем tenant content, privacy consent, approval history и generation provenance. Caller —
OWNER, включая злоумышленника с действующей сессией; external site/provider/model и storage
content недоверенные. Trust boundaries: HTTP→membership, DB→private bytes, provider→normalized
origin, reviewed payload→AI processor, model output→published Identity.

| Threat | Protection | Verification в 03–05 (ещё не выполнена) |
| --- | --- | --- |
| BOLA, revoked membership, account replacement | scoped queries + actual OWNER + fresh same-account binding | foreign IDs, deactivated account/member, switched binding, concurrent revoke |
| Prompt injection в HTML/caption/image | data-only inputs, no model tools/fetch, exact output schema/citations, human approval | hostile instructions не меняют selectors/permissions; invalid quotes rejected |
| SSRF через links/redirect/media URL | existing pinned fetch only at acquisition; renderer bytes-only; consumers no HTTP refetch | private IP/redirect tests сохраняются; review/consumer network call count=0 |
| Forged CLEAN/provenance или stale snapshot | source/payload hashes + versions + immutable origin + latest scan + physical witness | stale/error scan, forged JSON origin, corrupt/missing generation, expiry during I/O |
| Social generic TEXT downgrade | origin-first dispatch, no fallback, inherited receipt guard | generic route rejects originated social without typed admission; manual JSON not elevated |
| Privacy leak, excessive model input | explicit projection/review, local-only identifiers, external gate #120 | serialized payload lacks account/post IDs, token/storage key; disabled live adapter |
| Resource exhaustion/image decoder abuse | bounds/quotas/deadlines/concurrency; visual path blocked until qualified decoder | UTF-8 batch overflow, huge dimensions, malformed formats, deadline/resource tests |
| Withdrawal race/history forgery | append-only review/withdrawal, recheck before commit, immutable approved history | both race orders, SQL bypass, old receipt never reactivated |

Malware scan не предотвращает prompt injection; input validation не доказывает ownership;
human review не заменяет processor privacy policy. Обход одного слоя встречает остальные gates.
Safe errors/logging не раскрывают content, tokens, private refs; новых dependencies в этом ADR нет.

## Alternatives и trade-offs

| Вариант | Security / correctness | Complexity | User value / решение |
| --- | --- | --- | --- |
| Send original HTML/JSON после CLEAN | лишние IDs/content, prompt surface; scan не consent | малая сейчас, дорогая remediation | отвергнут: слабая provenance/контроль |
| Reviewed bounded text projection | минимизация payload, exact citations; не видит visual style | умеренная, использует существующие ports | принят для URL/social text |
| Vision adapter | распознаёт palette/layout; новые decoder/privacy/cost риски, неверные visual claims | высокая, требует qualification | нужен отдельный provider decision; не выбран |
| OCR-only | текст на image, но не palette/layout; ошибки распознавания | новый engine и derived provenance | не эквивалент vision/MVP visual analysis; не выбран |
| Отказаться от images/screenshots | меньше attack surface | низкая | противоречит утверждённым MVP inputs; не принят |

## Границы 03–05 и qualification

03: URL policy утверждена — следующий scope: immutable preview/admission/withdrawal contract, physical/source
verification и safe legacy handling; без consumer. 04: после03 — mixed bounded URL analysis,
citations, Identity copy/read/direct/inherited approval и fresh guards. 05: после02 и01/04 —
typed social route + downgrade regression; visual contracts/blockers по выбранной policy,
без выдуманного live vision/OCR/OAuth. #32/#33 onboarding не обходятся server fixtures.

Для 03–05 понадобятся Java/PostgreSQL/concurrency/security/full gates. В 02 только inspection,
docs links/content/diff; исторический passing suite01 не является тестом предложенных contracts.
Все перечисленные новые guards являются acceptance criteria, пока не реализованы и проверены.
Whole #121 остаётся open, включая visual/provider gaps и approved Identity→Strategy.

## Implementation03 — 2026-10-14

URL acquisition snapshot/review/admission/current read/withdrawal реализованы и qualified:
[ADR0022](0022-url-text-admission.md). Историческое описание baseline выше сохранено;
URL consumers04 и social/visual05 этим slice не реализованы. P1–P3 не изменены.

## Implementation04 — 2026-10-14

URL textual consumers implemented по [ADR0023](0023-url-analysis-identity.md): mixed generation,
field-specific citations и Identity provenance/approval. Scope04 qualified; qualification в
[отчёте04](../issue-121-url-consumption-slice.md). Historical baseline выше не переписывается.

## Implementation05 — 2026-10-14

Typed social implementation и current consumer guards: [ADR0025](0025-social-text-review-consumption.md).
Visual contracts/blocker: [ADR0024](0024-visual-evidence-provider-boundary.md).
Actual qualification: [отчёт05](../issue-121-social-consumption-slice.md).
Исторический baseline и approved P1–P3 выше не изменены; visual MVP gap остаётся.
