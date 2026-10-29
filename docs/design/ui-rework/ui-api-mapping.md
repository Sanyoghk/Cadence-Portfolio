# UI → API / contracts / state mapping

## Working integration2026-10-20

Rework23–24 qualified locally: [implementation report](../../ui-rework23-24-report.md).
`frontend/` сохраняет `CadenceApi`/`IdentityApi`, generated44-schema contract и existing API paths.
Manager → explicit Workspace/Brand → `#identity`; route содержит только view name, не resource IDs.
В24 работают registration/Jobs/exact review/consent/current/withdrawal/history и Identity/edit/
separate approval/citations. Browser fixtures отдельно от export.25–27 остаются pending.
Public upload/demo pillars заменены informational panel→Clerk/Workspace; BG01/BG02 не реализованы.
Ниже — исходный contract inventory; historical review runtime status не заменяет новый report.

Baseline `5a71df6`,2026-10-19. Все маршруты ниже имеют префикс `/api/v1`.
Это source inspection актуальных controllers/DTO и текущих frontend guards, **не новые HTTP
tests**. [api-inventory.json](api-inventory.json) содержит полный извлечённый route inventory
для рассмотренных business controllers; отсутствие UI в23–24 не означает отсутствие backend.

## Общий контракт кабинета

Clerk SDK → configured short-lived Bearer → typed `CadenceApi` → current OWNER/membership
authorization. ADR0040 сохраняется: `credentials: omit`, `cache: no-store`, redirect error,
bounded response/deadline, безопасные Problem Details. Frontend не хранит JWT или private
business content в localStorage/URL. Session/Workspace/Brand change отменяет запросы,
сбрасывает private subtree и отвергает late replies старого scope.

Для новых экранов25–27 использовать actual DTO из OpenAPI/contracts; явно расширить
method/path CORS allowlist и проверить negatives. Нельзя ради навигации разрешать весь API.
Prototype после выбора использует synthetic fixtures вместо этих endpoints.

Основные проверенные sources:
[WorkspaceController](../../../src/main/java/com/cadence/presentation/WorkspaceController.java),
[BrandIdentityController](../../../src/main/java/com/cadence/presentation/BrandIdentityController.java),
[EvidenceAdmissionController](../../../src/main/java/com/cadence/presentation/EvidenceAdmissionController.java),
[AiGenerationController](../../../src/main/java/com/cadence/presentation/AiGenerationController.java),
[StrategyController](../../../src/main/java/com/cadence/presentation/StrategyController.java),
[ContentGenerationController](../../../src/main/java/com/cadence/presentation/ContentGenerationController.java),
[CalendarController](../../../src/main/java/com/cadence/presentation/CalendarController.java),
[PerformanceController](../../../src/main/java/com/cadence/presentation/PerformanceController.java).

| UI / действие | Actual route | Payload/source/version contract | State/recovery |
| --- | --- | --- | --- |
| Account | Clerk SignIn/SignUp/UserButton, не custom `/login` | Официальный SDK, configured template; backend не принимает SDK cookie как API auth | Signed-out → auth; configuration missing → Setup required; не рисовать signed-in по demo flag |
| Workspace list/create | GET/POST `/workspaces` | CreateWorkspaceRequest: name/industry; PageResponse; id/status/lockVersion в WorkspaceResponse | Page20; loading/empty; uncertain POST → refresh list перед повтором |
| Workspace read/edit/archive | GET/PATCH `/workspaces/{workspaceId}`, POST `/workspaces/{workspaceId}/archive` | UpdateWorkspaceRequest: expectedVersion/name/industry; archive VersionRequest |409 → fresh read и review, не silent overwrite; архивирование не hard delete |
| Brand list/create | GET/POST `/workspaces/{workspaceId}/brands` | Отдельные CreateBrandRequest и Workspace operation; это не atomic wizard | Явный selection; нельзя выдавать созданный Workspace за успешно созданный Brand |
| Brand read/edit/archive | GET/PATCH `/brands/{brandId}`, POST `/brands/{brandId}/archive` | Actual BrandResponse/UpdateBrandRequest/VersionRequest | Fresh scoped read; archived read/history; mutation/foreign scope denied сервером |
| Register text/URL | POST `/brands/{brandId}/evidence` JSON | RegisterEvidenceRequest: type,value; max20000 characters;202 registration+Job | URL сохранён ≠ fetch/analyse/consent; unsafe/unavailable states контролируются API |
| Register file | POST `/brands/{brandId}/evidence` multipart | `file`; current UI PDF/PNG/JPEG≤10MiB; server byte/format/scan validation | SVG/ZIP unsupported; images REQUIRES_PROVIDER; scan/CLEAN ≠ admission |
| Social source | POST `/brands/{brandId}/evidence/social` | Actual social DTO и supported provider policy; не произвольный social scraper | Handle сам по себе не разрешает live fetch; unavailable503 остаётся явным |
| Sources list | GET `/brands/{brandId}/evidence` | PageResponse<BrandEvidenceResponse>; separate frontend4MiB list cap | Source/Job/scan/expiry показывать отдельно; pagination |
| TEXT exact review | GET `/evidence/{evidenceId}/ai-admission` | EvidenceReview snapshot, source/Brand/Workspace versions + contentChecksum | Review current bytes/text;409 требует нового review и снимает локальное согласие |
| TEXT consent | POST `/evidence/{evidenceId}/ai-admission` | AdmitEvidenceRequest: sourceVersion,brandVersion,workspaceVersion,contentChecksum | Unchecked explicit control привязан к показанному snapshot; не consent при upload |
| PDF / URL / social review | GET `/evidence/{evidenceId}/document-review`, `/url-review`, `/social-review` | Отдельные exact selectors: artifact/scan/snapshot/expiry/checksum/context, actual kind DTO | Не универсальный checkbox на имя файла; expiry/source changes требуют re-review |
| PDF / URL / social consent | POST соответствующего review path + `/admissions` | Actual AdmitPdf/Url/Social request DTO, не DTO прототипа | No auto consent; unavailable/changed selectors блокируют action |
| Current snapshot | GET TEXT admission path + `/{admissionId}/snapshot`; для других kinds review path + `/admissions/{admissionId}/snapshot` | Current authorized snapshot, distinct from immutable audit metadata | Withdrawal/stale/expiry/scan guards остаются на сервере |
| Consent history/withdrawal | GET `/evidence/{evidenceId}/ai-admission/history`; POST `/evidence/{evidenceId}/ai-admission/{admissionId}/withdrawals` | Page0..10000/size1..20; append-only receipt/withdrawal, no client actor/time | История не утверждает текущую eligibility; withdrawal виден в lineage |
| Generate Identity analysis | POST `/brands/{brandId}/analysis-jobs` | expectedVersion, mode DECLARED/EVIDENCE; Idempotency-Key |202/200 + Location; status/result отдельно; повтор с новым key не автоматический retry |
| Job status/result | GET `/jobs/{jobId}`, GET `/analysis-results/{id}` | Actual returned IDs/status; bounded polling и cancellation | Terminal failure/unavailable/expired ≠ success; no fake percent |
| Identity DRAFT from evidence | POST `/brands/{brandId}/identities/from-evidence-analysis` | Actual evidence-analysis receipt/source + expectedVersion; original provenance preserved | Copy — отдельное действие; source/withdrawal recheck; не approval |
| Identity manual/from-analysis | POST `/brands/{brandId}/identities`, POST `/brands/{brandId}/identities/from-analysis` | CreateBrandIdentityRequest / IdentityFromAnalysisRequest; explicit origin mode | Не переименовывать DECLARED в evidence-grounded; demo attribution inherited |
| Identity read/history/edit | GET `/brands/{brandId}/identities`, GET/PATCH `/identities/{identityId}` | UpdateBrandIdentityRequest: expectedVersion,content | APPROVED edit→new DRAFT; response may201; исходная approved history immutable |
| Identity approval/provenance | POST `/identities/{identityId}/approval`, GET `/identities/{identityId}/evidence-provenance` | VersionRequest expectedVersion; current source eligibility | Отдельное human approval после edit; sources/withdrawal/lineage не скрыты |
| Strategy generation | POST `/brands/{brandId}/strategy-jobs` | expectedVersion + Idempotency-Key; exact latest APPROVED Identity | Blocked prerequisite vs503 provider unavailable различаются; no public anonymous generation |
| Strategy list/read/edit | GET `/brands/{brandId}/strategies`, GET/PATCH `/strategies/{strategyId}` | UpdateStrategyRequest: expectedVersion,content,playbook; actual PlaybookContent | Full field validation; approved edit→new DRAFT; не sample только pillars |
| Strategy approve | POST `/strategies/{strategyId}/approval` | VersionRequest, actual source and lifecycle guards | Отдельная кнопка; history source/version preserved |
| Content generation | POST `/brands/{brandId}/content-jobs` | expectedBrandVersion,strategyId,expectedStrategyVersion,slotIds1..84; Idempotency-Key | Сначала approved source;202 Job, никакого autonomous approval |
| Regenerate content | POST `/posts/{postId}/regeneration-jobs` | revisionId,expectedVersion,slotId,field + Idempotency-Key | Только нужный field/new revision; preserve provenance/history |
| Content result/posts | GET `/content-results/{id}`, GET/POST `/brands/{brandId}/posts`, GET `/posts/{postId}` | Actual post/create/result DTOs; no raw entities | Result receipt не заменяет post approval; partial/expired outcomes show explicitly |
| Revision history/read/edit | GET `/posts/{postId}/revisions`, GET/PATCH `/posts/{postId}/revisions/{revisionId}` | Actual revision DTO/expectedVersion | Approved/LOCKED immutable; returned new revision становится selected draft |
| Revision approval | POST `/posts/{postId}/revisions/{revisionId}/approval` | Actual approval request and expected revision | Сохранение caption не approval; private media qualification отдельно |
| Calendar | GET `/brands/{brandId}/calendar` | from/until Instant,timezone,page,size; default100, use bounded API contract | Display local zone; range bounded; mobile agenda |
| Schedule change/history | POST `/brands/{brandId}/calendar/schedules`, GET `/posts/{postId}/schedule-history` | CalendarScheduleRequest.changes; per-item revision/version/timezone/offset | Batch atomicity; DST ambiguous/missing time требует явного решения;409 refresh |
| Media list/register/review | GET/POST `/brands/{brandId}/media-assets`; GET `/media-assets/{assetId}`; POST `/media-assets/{assetId}/review` и `/archive` | Existing private demo media contract, actual review/version DTO | Не выдавать seed images за generated media; archive сохраняет audit |
| Media preview/bytes/bind | GET `/media-assets/{assetId}/preview` и `/bytes`; PUT `/posts/{postId}/revisions/{revisionId}/media` | Scoped private response, reviewed asset/revision contract | Не public URL; auth recheck на bytes; stale asset не скрывается |
| CSV export | POST `/brands/{brandId}/exports`; GET `/exports/{exportId}`; GET `/exports/{exportId}/download` | Actual export request + Idempotency-Key, approved revisions snapshot | Durable Job/private download, expiry; CSV first, no direct publishing |
| Metrics import | POST `/brands/{brandId}/metric-imports`; GET `/metric-imports/{importId}` | JSON or UTF-8 text/csv + Idempotency-Key; import status/Job | File validation/duplicates/partial coverage, no live platform sync claim |
| Per-post metrics | GET `/posts/{postId}/metrics` | Actual observation details | Missing is unknown, not zero; observation provenance visible |
| Performance | GET `/brands/{brandId}/performance` | fromDate,untilDate,timezone,dimension,bucket,platform,page,size; default50 | Units/windows/coverage/staleness explicit; no fabricated charts for empty response |
| Metric attribution | GET/PUT `/metric-observations/{observationId}/attribution` | MetricAttributionRequest + exact observation/source | Непривязанные observations видны; не silently attribute to foreign revision |
| Recommendations | GET/POST `/brands/{brandId}/recommendations`, GET `/recommendations/{id}` | Existing request/version/window/evidence/idempotency contracts | HEURISTIC/UNCALIBRATED label; source observations доступны |
| Apply/dismiss | POST `/recommendations/{id}/apply` и `/dismiss` | Actual review decision DTO; server evaluates current source/version | Apply создаёт новый Strategy DRAFT, не изменяет APPROVED автоматически |
| Reports | POST `/brands/{brandId}/reports`; GET `/reports/{id}`, `/result`, `/download` | User-approved snapshot, durable Job/private HTML+JSON,24h result lifetime | Expired requires new operation; не обещать PDF/email delivery |

Legacy synchronous POST `/brands/{brandId}/strategies` существует; для нового долгого
workflow выбран уже имеющийся durable `/strategy-jobs`. Наличие обоих routes не повод
создавать третий AI protocol. Полные field contracts и numeric limits нужно генерировать
из актуальной OpenAPI при implementation; таблица не заменяет validation schemas.

## Публичный demo не имеет backend mapping

`Preview demo pillars`, демонстрационный Account/checkout и plan selection не имеют
production API в текущем scope. Public controls не получают Bearer/secret, не отправляют
URLs/files и не создают текущий server Brand. Нужен отдельный consent/privacy/cost/product
design, если пользователь позже захочет настоящий anonymous analysis. См. [backlog](backend-backlog.md).

## State matrix и будущие negative tests

| Состояние/сценарий | Требуемая реакция нового UI | Текущий review evidence |
| --- | --- | --- |
|401/signed-out | Удалить private state, auth recovery | Inspection ADR0040/current SessionGate; runtime не запускался |
|403/revoked membership | Закрыть subtree/действия, не сохранять старый result | Same boundary reuse; будущий cross-stack test |
|404/foreign ID | Без раскрытия чужого имени/существования; refresh own list | Server authority в contracts; новый UI ещё не проверен |
|409/version/source/consent stale | Обновить snapshot, сбросить consent; review before resubmit | Current Identity pattern inspected |
| Network uncertain after POST | Не показывать saved; reconcile/read status, no blind retry | Current transport/rework requirements |
| Workspace/session/Brand switch while pending | Abort + response scope guard; очистить previous forms/result | ADR0040/current scoped pattern |
| Source withdrawn/scan expired | Запрет generation/approval, сохранить historical receipt | DTO/ADR contracts, не проверка runtime этим review |
| Provider unavailable | Controlled503/REQUIRES_PROVIDER; no seeded success | Current fail-closed product requirement |
| APPROVED edit | Новый DRAFT и отдельное approval | Identity/Strategy/Revision contract inspection |
| Empty metrics/denominator0 | No data/coverage и defined units; не выдумывать growth | Backend Performance scope inspection |
| Malicious text/URL/file | React escaped text; validated URLs/bytes, no raw HTML | Требования последующей implementation/E2E |

На первом этапе review execution security tests не запускались. Теперь есть отдельные
[synthetic domain/browser tests](prototype-report.md), которые не проверяют эту server matrix.
При production implementation потребуются actual DTO tests,
tenant/session late-response negatives, exact consent/approval и browser+axe/keyboard QA.


## Исполняемые demo controls и будущая интеграция

| Demo implementation | Настоящий contract при переносе |
| --- | --- |
| `App.selectBrand` / scope key | Existing Workspace/Brand list + server membership/ownership; session cancellation и late-response guard |
| `Identity` source select / Allow / Withdraw | Actual source-specific review DTO, exact checksum/receipt, admission/current bytes, retention/withdrawal |
| `prepareStrategy` / `approveStrategy` | Durable strategy Job, full Playbook contract, explicit approval/version; demo goal не полный DTO |
| `prepareContent` / revision editor | Content Job/revision endpoints с idempotency, provenance и server current-source checks |
| `schedulePost` / local CSV | Actual Calendar atomic changes, timezone/offset/DST и durable approved snapshot/private export |
| `metricsFor` / `Performance` | Metric import/status, scoped observations/attribution и paginated Performance response |
| Recommendation / report modal | Existing current-source apply→new DRAFT; report creation/approval/Job/result отдельно |

Все85 route annotations инвентаризированы; prototype исполняет representative сценарии,
не каждый backend endpoint. Media bytes/preview/bind, detailed ingestion Jobs/recovery,
source-specific PDF/URL/social receipts, metrics attribution, real report/export downloads и
optimistic409 представлены в mapping/plan для следующего frontend slice, без fake API adapters.
