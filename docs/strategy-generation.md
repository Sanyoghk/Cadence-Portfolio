# Strategy generation — #97

## Актуальный contract13

Нижние разделы описывают исторический slice97; актуальная generation использует captured
APPROVED Identity по ADR0026, full v2 Playbook по ADR0029, durable jobs10–11 и shared budgets12.
[API](strategy-api.md), [async contract](durable-ai-generation.md), [report13](prompt13-playbook-report.md).
Generation result теперь обязан содержать полный typed Playbook; old partial output не публикуется.
Local fake помечен deterministic-v2 / strategy-playbook-v2 / DEMONSTRATION_ONLY.
Production adapter остаётся disabled. Human edits имеют отдельный actor/time и не меняют receipt.

## Исторический Scope97 и architecture

Application generation flow без HTTP endpoints (#102). Реальный AI provider отложен:
production fail-closed; fake доступен только local/test без prod. Production adapters
Brand/Strategy отслеживаются в [#120](https://github.com/Sanyoghk/Cadence/issues/120).

`BuildStrategyUseCase.execute` проверяет OWNER и server-authorized Workspace scope.
`StrategyBuildTransactions.snapshot` в отдельной короткой transaction блокирует
Workspace → Brand, проверяет ACTIVE и валидную signature, захватывает immutable
source snapshot, optimistic versions, server UUID и PostgreSQL clock.
`StrategyGenerationService.generate` выполняется вне transaction. `complete` повторно
блокирует те же ресурсы, проверяет versions, exact signature/name/industry и 60-second
publication deadline, выделяет max(version)+1 и сохраняет только новый DRAFT.
Два concurrent requests при default READ_COMMITTED получают разные versions; previous
strategies сохраняются. Ошибка/null provider output не создаёт partial draft и не
изменяет старые версии. Более строгая transaction isolation может дать безопасный
serialization conflict; automatic retries отсутствуют.

## Generation contract / trust boundary

Command содержит declared name/industry, validated signature snapshot, source versions,
UUID и startedAt. Entity, credentials и raw provider payload в port не передаются.
Result: 1–10 pillars, unique case-insensitive names, sum(weights)=100; postsPerWeek1–21;
known timezone; 1–7 unique publishing times с microsecond precision; bounded text;
validated provider/model/prompt identifiers и enum warnings. Result не задаёт status.
Operation bounds не являются обещанием оптимальной маркетинговой стратегии.
Java zone identifier дополнительно проверяется PostgreSQL timezone catalog при commit;
нестандартный JVM alias, отсутствующий в PostgreSQL, безопасно отвергается.

`cadence.strategy-generation.adapter`: fake разрешён local/test и запрещён prod/mixed;
disabled — default вне development; unknown value отвергает startup без fallback.
Default application profile — local, поэтому deployment обязан явно выбрать prod.
Production adapter обязан сам ограничивать transport I/O и cancellation в #120:
60 seconds здесь — publication fence, а не механизм остановки зависшего provider.
До реализации adapter production generation недоступна. Fake deterministic и
DEMONSTRATION_ONLY; не выполняет external calls.

## Data integrity и migration

V24 добавляет nullable legacy / complete generated provenance, unique generation ID,
source/deadline/version guards и deferred collection constraints. Последние выполняются
при commit, когда JPA уже сохранила все pillars/times. Legacy строки не получают
выдуманную provenance. Generated history нельзя удалять; identity/source immutable.
APPROVED/LOCKED content и обратные state transitions защищены database triggers.
Runtime role получает UPDATE лишь editable draft/status/audit columns, не provenance.
Migration role владеет schema; после upgrade повторно примените runtime-permissions.sql.

Strategy defensive copies не позволяют менять approved cadence/pillars через alias.
`PublishingCadence.bestTimes` использует Hibernate LOCAL_TIME JDBC mapping: время
публикации — local wall time, его нельзя сдвигать UTC conversion для audit timestamps.
API/approval/edit-as-new-draft и их safe HTTP conflict mapping остаются #102.
Current source — valid current BrandSignature, а не approved Brand Identity revision:
editable/versioned identity, citations и human approval остаются отдельным scope #107.

## Threat model / limitations

Защищаем tenant signatures, strategy history и database resources. Caller — OWNER;
trust boundaries: authentication scope, persisted source, provider result, SQL runtime.
BOLA блокируется method guards и scoped repository lookup; archive/source races —
Workspace→Brand locks и repeated snapshot equality; stale output — application + DB
deadline/source guards; provider-controlled approval — отсутствие status в result;
malformed/excessive output — immutable bounded constructors + DB constraints;
provider diagnostic leakage — fixed exception без cause/raw diagnostics.
Defense in depth: guards → domain/port → DB constraints/triggers → runtime privileges.
Новые dependencies/secrets/endpoints отсутствуют. SQL параметризован; model text
хранится как data, не выполняется. Будущий UI обязан безопасно отображать text (#102/UI).
Нет durable AI Job/attempt ledger, rate limiting/cost admission или request idempotency
для generation; это будущий provider/API scope, не production-ready AI product.
Повторный вызов создаёт новую version. Draft edits после generation не переписывают
source provenance; полноценный edit/approval audit и revision lineage проектируются #102.

## Verification

`StrategyGenerationContractTest`, `StrategyGenerationConfigurationTest`,
`BuildStrategyUseCaseTest`, `StrategyTest`, `PostgreSqlStrategyBuildTest`,
`PostgreSqlStrategyMigrationTest` и runtime-role scenario в
`FullEvidenceProviderIntegrationTest` проверяют contracts, authorization, races,
deadlines, source freshness, immutable history, populated V23 upgrade и actual DB grants.
Python report gate требует Strategy PostgreSQL suites и отвергает skipped results.
Фактические команды и итоговые counts — в issue completion report после full qualification.
