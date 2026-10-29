# Изменённые файлы social implementation05

Отдельный visual docs commit `be6a03e`: ADR0024, оба DEVELOPMENT_PLAN и progress ledger.
Ниже полный manifest следующего social slice относительно `be6a03e`.

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Actual scope05, qualification и открытые visual/provider gaps. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронный русский план scope05 и remaining #121. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Запрет UPDATE/DELETE immutable social snapshots для cadence_runtime. |
| [docs/adr/0021-source-kind-ai-eligibility.md](adr/0021-source-kind-ai-eligibility.md) | Ссылка на реализацию05 без изменения approved P1–P3 или исторического baseline. |
| [docs/adr/0025-social-text-review-consumption.md](adr/0025-social-text-review-consumption.md) | Архитектура, alternatives, versioned contracts и security boundaries05. |
| [docs/adr/README.md](adr/README.md) | Индекс visual/social ADR0024–0025. |
| [docs/approved-social-evidence.md](approved-social-evidence.md) | Ссылка на typed social consumer и запрет generic fallback. |
| [docs/brand-identity-api.md](brand-identity-api.md) | Social provenance/direct/inherited approval contract. |
| [docs/evidence-admission-api.md](evidence-admission-api.md) | Typed social review и общий withdrawal contract. |
| [docs/evidence-analysis-api.md](evidence-analysis-api.md) | Mixed social generation, v3 digest и preserved older receipts. |
| [docs/issue-121-social-consumption-files.md](issue-121-social-consumption-files.md) | Полный per-file manifest implementation slice. |
| [docs/issue-121-social-consumption-slice.md](issue-121-social-consumption-slice.md) | Архитектурный/security/qualification отчёт05. |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Ledger05, separate visual commit и actual acceptance boundary. |
| [docs/social-evidence-admission-api.md](social-evidence-admission-api.md) | HTTP examples, guards, privacy/provider limitations и consumer flow. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Regression missing/skipped required PostgreSQL reports. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Обязательные social runtime/migration reports без пропусков. |
| [src/main/java/com/cadence/application/ports/AnalysisCitation.java](../src/main/java/com/cadence/application/ports/AnalysisCitation.java) | Allowlist social field paths; прежний nullable plain citation contract. |
| [src/main/java/com/cadence/application/ports/AnalysisTextSource.java](../src/main/java/com/cadence/application/ports/AnalysisTextSource.java) | Typed social canonical payload и decoded exact citation map; identifiers не сериализуются. |
| [src/main/java/com/cadence/application/ports/ApprovedSocialEvidenceSource.java](../src/main/java/com/cadence/application/ports/ApprovedSocialEvidenceSource.java) | Local same-account policy port; default fail-closed, без HTTP/credentials. |
| [src/main/java/com/cadence/application/ports/BrandAnalysisCommand.java](../src/main/java/com/cadence/application/ports/BrandAnalysisCommand.java) | Versioned social-evidence-v3 digest при сохранении v1/v2 и total budget. |
| [src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java](../src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java) | Server-selected latest social admissions и bounded duplicate-context query. |
| [src/main/java/com/cadence/application/repositories/SocialTextSnapshotRepository.java](../src/main/java/com/cadence/application/repositories/SocialTextSnapshotRepository.java) | Scoped immutable snapshot reads и bounded history count. |
| [src/main/java/com/cadence/application/results/IdentityEvidenceProvenance.java](../src/main/java/com/cadence/application/results/IdentityEvidenceProvenance.java) | Nullable socialSnapshotId для сохранённой citation provenance. |
| [src/main/java/com/cadence/application/results/SocialAdmissionDetails.java](../src/main/java/com/cadence/application/results/SocialAdmissionDetails.java) | Review receipt, snapshot ID, capture и binding expiry DTO. |
| [src/main/java/com/cadence/application/results/SocialAdmissionSnapshot.java](../src/main/java/com/cadence/application/results/SocialAdmissionSnapshot.java) | Current admitted social text DTO. |
| [src/main/java/com/cadence/application/results/SocialReviewPreview.java](../src/main/java/com/cadence/application/results/SocialReviewPreview.java) | Exact projection/selectors/capture/provider/warnings DTO без secrets. |
| [src/main/java/com/cadence/application/services/BrandAnalysisTransactions.java](../src/main/java/com/cadence/application/services/BrandAnalysisTransactions.java) | Social current checks до egress/publication; запрет generic origin downgrade. |
| [src/main/java/com/cadence/application/services/EvidenceIdentitySourcePolicy.java](../src/main/java/com/cadence/application/services/EvidenceIdentitySourcePolicy.java) | Social copy/direct/inherited approval и historical provenance guards. |
| [src/main/java/com/cadence/application/services/SocialAnalysisSourcePolicy.java](../src/main/java/com/cadence/application/services/SocialAnalysisSourcePolicy.java) | Central origin/snapshot/hash/version/same-account/current expiry policy. |
| [src/main/java/com/cadence/application/usecases/AnalyzeEvidenceUseCase.java](../src/main/java/com/cadence/application/usecases/AnalyzeEvidenceUseCase.java) | Social structured input не направляется в URL physical resolver; preflight сохраняется. |
| [src/main/java/com/cadence/application/usecases/ReviewEvidenceUseCase.java](../src/main/java/com/cadence/application/usecases/ReviewEvidenceUseCase.java) | Запрет originated social в generic preview/admit/current read, shared withdrawal сохраняется. |
| [src/main/java/com/cadence/application/usecases/ReviewSocialEvidenceUseCase.java](../src/main/java/com/cadence/application/usecases/ReviewSocialEvidenceUseCase.java) | Scoped parent locks, immutable preview, explicit consent/current read, history/withdrawal limits. |
| [src/main/java/com/cadence/domain/BrandAnalysisCitation.java](../src/main/java/com/cadence/domain/BrandAnalysisCitation.java) | Persisted field-path allowlist для social decoded quote. |
| [src/main/java/com/cadence/domain/EvidenceAdmission.java](../src/main/java/com/cadence/domain/EvidenceAdmission.java) | Immutable social snapshot/expiry selectors и actor/time receipt. |
| [src/main/java/com/cadence/domain/SocialReviewToken.java](../src/main/java/com/cadence/domain/SocialReviewToken.java) | Validated exact review selectors. |
| [src/main/java/com/cadence/domain/SocialTextProjection.java](../src/main/java/com/cadence/domain/SocialTextProjection.java) | Strict parser, canonical bounded projection, IDs-local mapping и citation fields. |
| [src/main/java/com/cadence/domain/SocialTextSnapshot.java](../src/main/java/com/cadence/domain/SocialTextSnapshot.java) | Immutable source/capture/projection/hash/post mapping с current verification. |
| [src/main/java/com/cadence/infrastructure/analysis/DeterministicBrandAnalysisAdapter.java](../src/main/java/com/cadence/infrastructure/analysis/DeterministicBrandAnalysisAdapter.java) | Явно local/test fake выбирает substantive social citation; URL order сохранён. |
| [src/main/java/com/cadence/infrastructure/ingest/social/OfficialSocialEvidenceSource.java](../src/main/java/com/cadence/infrastructure/ingest/social/OfficialSocialEvidenceSource.java) | Проверка того же Brand/provider/account и current approval/scopes/expiry без HTTP. |
| [src/main/java/com/cadence/presentation/SocialEvidenceReviewController.java](../src/main/java/com/cadence/presentation/SocialEvidenceReviewController.java) | OWNER/no-store social preview/admission/current snapshot API. |
| [src/main/java/com/cadence/presentation/dto/AdmitSocialEvidenceRequest.java](../src/main/java/com/cadence/presentation/dto/AdmitSocialEvidenceRequest.java) | Validated selectors DTO и отказ unknown fields. |
| [src/main/resources/db/migration/V41__social_text_review_consumption.sql](../src/main/resources/db/migration/V41__social_text_review_consumption.sql) | Append-only snapshots/admissions, canonical projection, origin locks, typed citations/digest/Identity guards. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI3.1 social routes/DTOs/citation fields и nullable provenance. |
| [src/test/java/com/cadence/domain/SocialTextProjectionTest.java](../src/test/java/com/cadence/domain/SocialTextProjectionTest.java) | Strict shape/Unicode/budget/order/origin/citation/default-denial unit tests. |
| [src/test/java/com/cadence/infrastructure/ingest/social/OfficialSocialEvidenceSourceTest.java](../src/test/java/com/cadence/infrastructure/ingest/social/OfficialSocialEvidenceSourceTest.java) | Actual same-account local policy tests без external calls. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | V41 migration inventory. |
| [src/test/java/com/cadence/persistence/PostgreSqlSocialReviewMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlSocialReviewMigrationTest.java) | V40→V41 unchanged receipts/approved history; late-origin generic use denial. |
| [src/test/java/com/cadence/persistence/PostgreSqlSocialReviewTest.java](../src/test/java/com/cadence/persistence/PostgreSqlSocialReviewTest.java) | Real PostgreSQL API plus SQL/runtime/history/canonicalization/concurrency tests. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | UTC audit columns social snapshot. |
| [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | New DTO fields/enum/time schemas and routes match implementation. |
| [src/test/java/com/cadence/security/SocialReviewApiIntegrationTest.java](../src/test/java/com/cadence/security/SocialReviewApiIntegrationTest.java) | Shared social use-case/API contracts on H2. |
| [src/test/java/com/cadence/support/SocialReviewApiContract.java](../src/test/java/com/cadence/support/SocialReviewApiContract.java) | Both-provider positive flows and forbidden access/binding/withdrawal/corruption/legacy scenarios. |
| [src/test/java/com/cadence/support/UrlAnalysisApiContract.java](../src/test/java/com/cadence/support/UrlAnalysisApiContract.java) | Mixed URL/PDF/social and no bypass of existing physical guards. |
