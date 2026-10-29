# Backend gaps и будущие решения для UI rework

Живой реестр,2026-10-19, baseline `5a71df6`. Его нужно обновлять по ходу работы над
отдельным прототипом/implementation: evidence → gap → решение → acceptance → status.
UI желание не равно разрешению реализовать новый backend/provider/privacy policy.
Ни один пункт здесь не создаёт автоматически GitHub issue или product commitment.

## Реальные gaps относительно нового brief

| ID / статус | Запрошенная возможность и actual gap | Что сделать позже / acceptance |
| --- | --- | --- |
| BG01 — product/security decision | Настоящий Brand analysis до sign-up. Текущие business APIs OWNER-only и Workspace/Brand-scoped; anonymous equivalent не найден | Решить, нужен ли он вообще. Если да: отдельная временная ownership/session модель, exact consent/privacy/retention, SSRF/file quarantine, bot/rate/cost/resource limits, secure claim-to-account, cleanup и abuse tests. До решения только synthetic demo |
| BG02 — deferred product scope | Standard/Plus, checkout и subscriptions отсутствуют; billing прямо вне MVP | Выбрать provider/цены/валюту/налоги/trial/cancellation/refund/entitlements; server-side authorization, verified signed webhooks, replay/idempotency, audit/reconciliation и negative tests. Сейчас TBD, никаких реальных charge/paid flags |
| BG03 — external prerequisite | Real AI providers/models и qualification ещё не выбраны | Сохранить #120 Brand/Strategy; Content/Recommendations/media adapters отдельно. Data policy/region/retention/cost ceilings, moderation/provenance/failure/recovery и live qualification. Local fake ≠ real AI |
| BG04 — existing incomplete scope | PNG/JPEG/screenshots принимаются как private bytes, но visual consumption REQUIRES_PROVIDER | Не реализовывать ingest заново. Завершить remaining visual #121 с exact admission/current-byte/source guards и qualified provider; APPROVED lineage/withdrawal regression tests |
| BG05 — external integration | Live Instagram/TikTok collection/sync не следует из текстового admission/handle | #32/#33/social policy и #99 sync отдельно: platform permissions/terms, OAuth lifecycle/revocation, scopes, retry/reconciliation, user consent и live tests |
| BG06 — unsupported prototype fields | Competitors auto-benchmark/follower signals/trending audio/demographics из старого HTML не имеют эквивалентного qualified API | Competitor name как input ≠ измеренные данные. Уточнить модель source/provenance/privacy/limits и endpoint/DTO, либо оставить clearly unavailable; не фабриковать metrics |
| BG07 — input contract decision | Единая форма Step1Upload содержит competitors и Brand voice notes, но не единый атомарный onboarding DTO | Website/social profile и source ingest уже существуют. Voice note можно проектировать как явный TEXT source с review/consent, без выдуманного поля Entity. Competitor collection/meaning уточнить до persistence. Один save-all не обещать без orchestration contract |
| BG08 — unsupported formats | Старый demo обещает SVG/ZIP/50MB, actual UI PDF/PNG/JPEG≤10MiB | Сохранить existing bounds; не вводить ZIP/SVG ради картинки. Если бизнесу действительно понадобится — отдельная quarantine/content/zip-bomb policy и bounded parser qualification |
| BG09 — remaining release gate | Backend28 qualified locally19 октября, rework23–24 locally20 октября; live self-service OWNER/device/cross-stack release ещё не qualified | Не повторять delivered backend28; завершить frontend25–27, cross-stack28 и actual whole issues/live/load/production IAM/TLS/backup/restore acceptance |

## Уже есть backend; это frontend integration / qualification

- Workspace/Brand create/read/edit/archive и scoped lists: не missing backend. Новый switcher
  и Workspace Manager используют existing routes и optimistic locking.
- Exact review/consent/history/withdrawal для supported evidence и Identity lifecycle уже
  существуют; новый UX обязан их отобразить, а не заменить одним Analyze.
- Durable generation Jobs, budgets/idempotency и полный Playbook уже delivered в своих
  local scopes. Нельзя повторять историческую фразу recovery о том, что их нет.
- Content/revision/regeneration/Calendar/timezone/CSV/media API09–22 не реализуются заново.
- Manual/CSV metrics, Performance/attribution/recommendations/apply→DRAFT и private reports
  уже существуют. Необходимы новые frontend25–27 и later cross-stack/live verification.
- Brand switcher обязателен для multiple Brands, но новый backend contract для него не нужен.
- CORS extensions25–27 — planned configuration work на existing method/path contract,
  не blanket allow-all и не новое auth решение.

## Как вести реестр дальше

При каждом новом control проверить route/DTO и lifecycle. Если route отсутствует — добавить
новый BG пункт с source evidence, чувствительными данными, caller, trust boundary и required
negative tests. Если route существует — пометить как frontend work, не записывать duplicate
backend feature. Product/provider/data-policy decisions согласовать до реальной интеграции.

Текущий прототип после выбора останется offline-demo по данным: никакой API credentials
сборки, внешнего fetch URLs или настоящей оплаты. Backend28 и AI integration остаются
самостоятельным маршрутом; design artifacts не объявляют их completed.


## Обновления по ходу Cream Orbit prototype

2026-10-19: frontend-only synthetic model собрана, [report](prototype-report.md).
Новых доказанных missing backend endpoints не обнаружено: проверка inventory по23 controllers
и85 route annotations сохраняется. Ни один existing API не реализовывался заново.

| Наблюдение | Классификация / будущая работа |
| --- | --- |
| Account → Standard/Plus | BG02 остаётся product/backend decision; в demo concept без оплаты |
| Public links/PDF → pillars | BG01; только named synthetic fixture, не анонимный ingest |
| Image/screenshot source | BG03/BG04; visible Requires provider, без фиктивного успеха |
| Source kind changes / consent history | Existing backend capability; перенести actual receipt/version/history DTO, не demo counter |
| Media, metrics attribution, Jobs recovery, private report/export result | Existing backend capability; ещё требуется полноценный production UI и cross-stack qualification |
| CSV formula handling / Calendar DST | Existing contract не упрощать до demo serializer и bounded date window |
| Stale Identity после нового consent | Исправлен bug demo model и negative tests; это не заявление о backend vulnerability |

В ходе дальнейшего UI implementation продолжать дополнять эту таблицу с route/DTO evidence.
Durable AI Jobs и full Playbook уже существуют: demo таймер не свидетельствует об их отсутствии.
