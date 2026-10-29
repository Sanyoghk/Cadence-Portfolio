# Изменённые файлы задания04

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Синхронизация actual scope04, qualification и remaining05/#120. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронизация actual scope04, qualification и remaining05/#120. |
| [docs/adr/0021-source-kind-ai-eligibility.md](adr/0021-source-kind-ai-eligibility.md) | Ссылка на реализацию04, approved P1–P3 неизменны. |
| [docs/adr/0023-url-analysis-identity.md](adr/0023-url-analysis-identity.md) | Версии contracts/digests, alternatives, threat model и guards. |
| [docs/adr/README.md](adr/README.md) | Индекс ADR0023. |
| [docs/brand-identity-api.md](brand-identity-api.md) | API documentation URL consumer, citations и Identity/source guards. |
| [docs/evidence-analysis-api.md](evidence-analysis-api.md) | API documentation URL consumer, citations и Identity/source guards. |
| [docs/issue-121-url-consumption-files.md](issue-121-url-consumption-files.md) | Полный manifest данного slice. |
| [docs/issue-121-url-consumption-slice.md](issue-121-url-consumption-slice.md) | Итоговый отчёт: architecture/security/tests/limitations/Git. |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Progress ledger04 и зависимости. |
| [docs/url-evidence-admission-api.md](url-evidence-admission-api.md) | API documentation URL consumer, citations и Identity/source guards. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Missing/skipped reports новых suites не считаются pass. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Обязательные reports новых PostgreSQL consumer/migration suites. |
| [src/main/java/com/cadence/application/ports/AnalysisCitation.java](../src/main/java/com/cadence/application/ports/AnalysisCitation.java) | Nullable fieldPath с allowlist; legacy constructor сохраняется. |
| [src/main/java/com/cadence/application/ports/AnalysisTextSource.java](../src/main/java/com/cadence/application/ports/AnalysisTextSource.java) | Typed URL projection, exact canonical payload/fields и однократная serialization. |
| [src/main/java/com/cadence/application/ports/BrandAnalysisCommand.java](../src/main/java/com/cadence/application/ports/BrandAnalysisCommand.java) | mixed-evidence-v2 только для URL batches; decoded field citations и прежний общий budget. |
| [src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java](../src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java) | Server-selected latest current URL admissions alongside TEXT/PDF. |
| [src/main/java/com/cadence/application/results/IdentityEvidenceProvenance.java](../src/main/java/com/cadence/application/results/IdentityEvidenceProvenance.java) | Nullable fieldPath/urlSnapshotId в исторических citations. |
| [src/main/java/com/cadence/application/results/UrlAdmissionDetails.java](../src/main/java/com/cadence/application/results/UrlAdmissionDetails.java) | Consumer capability flag true; consent/provenance сохраняются. |
| [src/main/java/com/cadence/application/services/BrandAnalysisTransactions.java](../src/main/java/com/cadence/application/services/BrandAnalysisTransactions.java) | URL metadata guards до egress/publication и persisted citation path. |
| [src/main/java/com/cadence/application/services/EvidenceIdentitySourcePolicy.java](../src/main/java/com/cadence/application/services/EvidenceIdentitySourcePolicy.java) | URL copy/approval/inherited provenance и exact citations. |
| [src/main/java/com/cadence/application/services/EvidencePhysicalSource.java](../src/main/java/com/cadence/application/services/EvidencePhysicalSource.java) | Immutable PDF/URL physical witness и bytes/extraction verification. |
| [src/main/java/com/cadence/application/services/IdentitySourceVerification.java](../src/main/java/com/cadence/application/services/IdentitySourceVerification.java) | Общий physical witness; local extractor outside transaction. |
| [src/main/java/com/cadence/application/services/PdfAnalysisSourcePolicy.java](../src/main/java/com/cadence/application/services/PdfAnalysisSourcePolicy.java) | PDF semantics сохраняются через общий witness type. |
| [src/main/java/com/cadence/application/services/UrlAnalysisSourcePolicy.java](../src/main/java/com/cadence/application/services/UrlAnalysisSourcePolicy.java) | Current URL acquisition/job/latest scan/expiry/retained ledger/admission checks. |
| [src/main/java/com/cadence/application/usecases/AnalyzeEvidenceUseCase.java](../src/main/java/com/cadence/application/usecases/AnalyzeEvidenceUseCase.java) | Двойной physical batch URL/PDF и legacy TEXT SUPERSEDED compatibility. |
| [src/main/java/com/cadence/domain/BrandAnalysisCitation.java](../src/main/java/com/cadence/domain/BrandAnalysisCitation.java) | Immutable nullable field_path mapping с validation. |
| [src/main/java/com/cadence/infrastructure/analysis/DeterministicBrandAnalysisAdapter.java](../src/main/java/com/cadence/infrastructure/analysis/DeterministicBrandAnalysisAdapter.java) | Local demo URL quotes по decoded field и versioned prompt label. |
| [src/main/resources/db/migration/V40__url_analysis_identity.sql](../src/main/resources/db/migration/V40__url_analysis_identity.sql) | Additive field_path, URL SQL guards, mixed digest seal; без history rewrite. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI3.1 URL capability и nullable path/snapshot contract. |
| [src/test/java/com/cadence/application/AnalyzeEvidenceCapacityTest.java](../src/test/java/com/cadence/application/AnalyzeEvidenceCapacityTest.java) | Новый mocked URL dependency; прежние capacity assertions сохранены. |
| [src/test/java/com/cadence/application/IdentitySourceVerificationTest.java](../src/test/java/com/cadence/application/IdentitySourceVerificationTest.java) | Общий witness и extractor dependency; deadline/bulkhead tests сохранены. |
| [src/test/java/com/cadence/application/UrlAnalysisContractTest.java](../src/test/java/com/cadence/application/UrlAnalysisContractTest.java) | Decoded quotes, format separation, legacy digest, serialized payload без duplicated fields. |
| [src/test/java/com/cadence/infrastructure/analysis/EvidenceAnalysisFakeTest.java](../src/test/java/com/cadence/infrastructure/analysis/EvidenceAnalysisFakeTest.java) | URL demo citation с fieldPath; warnings/Unicode regression сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlPdfAnalysisMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPdfAnalysisMigrationTest.java) | Старая quote row сравнивается с additive null field_path без изменения old values. |
| [src/test/java/com/cadence/persistence/PostgreSqlPdfIdentityMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPdfIdentityMigrationTest.java) | Legacy provenance preserved с новой nullable column. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Migration inventory V1–V40. |
| [src/test/java/com/cadence/persistence/PostgreSqlUrlAnalysisMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUrlAnalysisMigrationTest.java) | Populated V39→40: original TEXT attempt/source/quote и approved Identity сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlUrlAnalysisTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUrlAnalysisTest.java) | PostgreSQL shared flow, SQL forged paths, runtime grants, обе withdrawal/approval lock races. |
| [src/test/java/com/cadence/security/UrlAnalysisApiIntegrationTest.java](../src/test/java/com/cadence/security/UrlAnalysisApiIntegrationTest.java) | H2 execution shared URL generation/Identity API/security contract. |
| [src/test/java/com/cadence/support/UrlAnalysisApiContract.java](../src/test/java/com/cadence/support/UrlAnalysisApiContract.java) | Mixed TEXT/PDF/URL generation/Identity, malicious data/citations, budget и race tests. |
| [src/test/java/com/cadence/support/UrlReviewApiContract.java](../src/test/java/com/cadence/support/UrlReviewApiContract.java) | Обновлён ожидаемый consumer capability/selection; preview по-прежнему не consent. |
