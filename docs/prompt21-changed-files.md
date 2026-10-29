# Задание21 — изменённые файлы

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Task6.3: фактический scope recommendations и граница report22/live adapter. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Task6.3: синхронный статус и зависимости на русском. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Запрет runtime UPDATE/DELETE четырёх таблиц immutable recommendation history. |
| [docs/adr/0038-evidence-linked-recommendations.md](../docs/adr/0038-evidence-linked-recommendations.md) | ADR: evidence/sample/rule/uncertainty/capture/apply/idempotency/security contract. |
| [docs/astra-medium-mvp-prompts.md](../docs/astra-medium-mvp-prompts.md) | Execution21: prerequisites, ADR/API/report и фактическая qualification. |
| [docs/issue-sync.md](../docs/issue-sync.md) | Mapping #100/#105/#107; report22 и внешнее закрытие не заявлены. |
| [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) | Ledger задания21 и честные remaining scopes. |
| [docs/prompt21-changed-files.md](../docs/prompt21-changed-files.md) | Полный перечень изменённых файлов с назначением каждого. |
| [docs/prompt21-recommendations-report.md](../docs/prompt21-recommendations-report.md) | Подробный отчёт: архитектура, security review, результаты qualification и обучение. |
| [docs/recommendations-api.md](../docs/recommendations-api.md) | Публичный API, bounds, recovery, provenance и fail-closed правила. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Regression required report gate для новых PostgreSQL suites. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Required PostgreSQL recommendation/runtime/migration reports; skipped не считается pass. |
| [src/main/java/com/cadence/application/exceptions/RecommendationUnavailableException.java](../src/main/java/com/cadence/application/exceptions/RecommendationUnavailableException.java) | Безопасный outcome unavailable/capacity/timeout. |
| [src/main/java/com/cadence/application/ports/RecommendationCommand.java](../src/main/java/com/cadence/application/ports/RecommendationCommand.java) | Immutable server capture и проверка exact proposed action/citations. |
| [src/main/java/com/cadence/application/ports/RecommendationEngine.java](../src/main/java/com/cadence/application/ports/RecommendationEngine.java) | Typed output port и explicit origin/rule/provider/model/prompt attribution. |
| [src/main/java/com/cadence/application/ports/RecommendationInput.java](../src/main/java/com/cadence/application/ports/RecommendationInput.java) | Минимальный bounded engine input без tenant/actor/private references/raw receipts. |
| [src/main/java/com/cadence/application/repositories/RecommendationInsightRepository.java](../src/main/java/com/cadence/application/repositories/RecommendationInsightRepository.java) | Scoped immutable result lookup, bounded paging и retained counts. |
| [src/main/java/com/cadence/application/repositories/RecommendationKeyRepository.java](../src/main/java/com/cadence/application/repositories/RecommendationKeyRepository.java) | Durable actor/Workspace/operation/key/request binding и retained cap. |
| [src/main/java/com/cadence/application/repositories/RecommendationResolutionRepository.java](../src/main/java/com/cadence/application/repositories/RecommendationResolutionRepository.java) | Immutable apply/dismiss receipt lookup и replay. |
| [src/main/java/com/cadence/application/results/RecommendationDetails.java](../src/main/java/com/cadence/application/results/RecommendationDetails.java) | Detail DTO: source/observations/checksums/confidence/uncertainty/resolution. |
| [src/main/java/com/cadence/application/results/RecommendationSummary.java](../src/main/java/com/cadence/application/results/RecommendationSummary.java) | Короткий bounded list DTO без повторения full snapshot. |
| [src/main/java/com/cadence/application/services/RecommendationService.java](../src/main/java/com/cadence/application/services/RecommendationService.java) | Admission, bounded rule call вне locks, fresh publication/apply checks и atomic new DRAFT. |
| [src/main/java/com/cadence/application/services/RecommendationSources.java](../src/main/java/com/cadence/application/services/RecommendationSources.java) | Parent authorization, exact latest approved source/Identity/evidence и comparable metric capture. |
| [src/main/java/com/cadence/application/usecases/GenerateRecommendationsUseCase.java](../src/main/java/com/cadence/application/usecases/GenerateRecommendationsUseCase.java) | OWNER/scope method-security boundary и actual authenticated actor. |
| [src/main/java/com/cadence/domain/RecommendationInsight.java](../src/main/java/com/cadence/domain/RecommendationInsight.java) | Новая immutable recommendation history; legacy Recommendation не backfill. |
| [src/main/java/com/cadence/domain/RecommendationKey.java](../src/main/java/com/cadence/domain/RecommendationKey.java) | Неизменяемая привязка ключа даже при failure/timeout/restart. |
| [src/main/java/com/cadence/domain/RecommendationMetricLink.java](../src/main/java/com/cadence/domain/RecommendationMetricLink.java) | Exact observation/context/value-checksum supporting link. |
| [src/main/java/com/cadence/domain/RecommendationProposal.java](../src/main/java/com/cadence/domain/RecommendationProposal.java) | Validated five-point pillar patch, bounded exact citations, copy-only content. |
| [src/main/java/com/cadence/domain/RecommendationResolution.java](../src/main/java/com/cadence/domain/RecommendationResolution.java) | Mutually exclusive apply/dismiss и initial new-draft checksum/actor/time. |
| [src/main/java/com/cadence/infrastructure/recommendations/RuleRecommendationEngine.java](../src/main/java/com/cadence/infrastructure/recommendations/RuleRecommendationEngine.java) | Честная HEURISTIC: independent weighted arithmetic и minimum sample. |
| [src/main/java/com/cadence/presentation/ApiExceptionHandler.java](../src/main/java/com/cadence/presentation/ApiExceptionHandler.java) | Контролируемая recommendation_unavailable503 без внутренних подробностей. |
| [src/main/java/com/cadence/presentation/RecommendationController.java](../src/main/java/com/cadence/presentation/RecommendationController.java) | Create/list/read/apply/dismiss HTTP API, Location/replay и no-store. |
| [src/main/java/com/cadence/presentation/dto/RecommendationRequest.java](../src/main/java/com/cadence/presentation/dto/RecommendationRequest.java) | Strict request DTO с source version/date window/timezone/platform. |
| [src/main/java/com/cadence/presentation/dto/RecommendationResolutionRequest.java](../src/main/java/com/cadence/presentation/dto/RecommendationResolutionRequest.java) | Strict expectedVersion DTO без writable audit/approval fields. |
| [src/main/resources/db/migration/V54__evidence_linked_recommendations.sql](../src/main/resources/db/migration/V54__evidence_linked_recommendations.sql) | Additive tables/FK/checks/indexes/immutability и deferred evidence/draft guards. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI5 routes/12 schemas; прежние operations/schemas семантически сохранены. |
| [src/test/java/com/cadence/application/RecommendationApiIntegrationTest.java](../src/test/java/com/cadence/application/RecommendationApiIntegrationTest.java) | H2 запуск shared API/application contract. |
| [src/test/java/com/cadence/domain/RecommendationProposalTest.java](../src/test/java/com/cadence/domain/RecommendationProposalTest.java) | Domain bounds, citations и copy-only patch invariants. |
| [src/test/java/com/cadence/infrastructure/recommendations/RuleRecommendationEngineTest.java](../src/test/java/com/cadence/infrastructure/recommendations/RuleRecommendationEngineTest.java) | Independently calculated weighted fixture, per-cohort sample и exact threshold. |
| [src/test/java/com/cadence/persistence/PostgreSqlPerformanceMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPerformanceMigrationTest.java) | Исторический V52→V53 тест явно закреплён на target53. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Actual migration inventory54. |
| [src/test/java/com/cadence/persistence/PostgreSqlRecommendationMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlRecommendationMigrationTest.java) | Populated V53→V54 без fabricated legacy provenance. |
| [src/test/java/com/cadence/persistence/PostgreSqlRecommendationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlRecommendationTest.java) | PostgreSQL/runtime/concurrent replay/revocation/direct SQL evidence tampering tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Actual UTC audit inventory68 created/updated columns. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | Новые DTO зарегистрированы в executable OpenAPI contract checks. |
| [src/test/java/com/cadence/support/RecommendationApiContract.java](../src/test/java/com/cadence/support/RecommendationApiContract.java) | Shared workflow/HTTP/security/source/timeout/idempotency regression tests. |
