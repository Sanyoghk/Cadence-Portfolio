# Задание14 — изменённые файлы

| Файл | Назначение |
| --- | --- |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Actual14 scope/qualification, prerequisites и remaining boundary. |
| [docs/issue-sync.md](issue-sync.md) | Mapping14→actual #115/#103, wider acceptance и baseline13 push/CI. |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Current milestone14, baseline13 push/CI и subsequent boundaries. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронная русская точка продолжения. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Restricted Post column grants и retired legacy writer privileges. |
| [docs/adr/0031-post-revision-lifecycle.md](adr/0031-post-revision-lifecycle.md) | Pre-migration contract/invariants, threat model и consumer eligibility. |
| [docs/adr/README.md](adr/README.md) | ADR index: существующий0030 и новый0031. |
| [docs/post-revisions-api.md](post-revisions-api.md) | API flow/examples, identity/version semantics, migration/recovery и remaining scope. |
| [docs/prompt14-changed-files.md](prompt14-changed-files.md) | Этот полный file manifest. |
| [docs/prompt14-post-revisions-report.md](prompt14-post-revisions-report.md) | Detailed qualification/security/architecture report. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Regression для missing/skipped новых mandatory reports. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Обязательные Post API/migration reports без skipped tests. |
| [src/main/java/com/cadence/application/repositories/CadenceUserRepository.java](../src/main/java/com/cadence/application/repositories/CadenceUserRepository.java) | Scalar membership query без ранней загрузки Workspace. |
| [src/main/java/com/cadence/application/repositories/ContentPostRepository.java](../src/main/java/com/cadence/application/repositories/ContentPostRepository.java) | Tenant-scoped identity lookup, lock и bounded pagination/count. |
| [src/main/java/com/cadence/application/repositories/PostRevisionRepository.java](../src/main/java/com/cadence/application/repositories/PostRevisionRepository.java) | Latest revision и paginated immutable history. |
| [src/main/java/com/cadence/application/results/PostDetails.java](../src/main/java/com/cadence/application/results/PostDetails.java) | Immutable application/read DTO с source и audit. |
| [src/main/java/com/cadence/application/usecases/PostLifecycleUseCase.java](../src/main/java/com/cadence/application/usecases/PostLifecycleUseCase.java) | Authorization, parent-first locks, create/read/list/edit/approve/history. |
| [src/main/java/com/cadence/domain/ContentPost.java](../src/main/java/com/cadence/domain/ContentPost.java) | Stable identity, platform и immutable approved Strategy receipt. |
| [src/main/java/com/cadence/domain/PostContent.java](../src/main/java/com/cadence/domain/PostContent.java) | Editorial fields, platform/format matrix, bounds и encoding validation. |
| [src/main/java/com/cadence/domain/PostRevision.java](../src/main/java/com/cadence/domain/PostRevision.java) | DRAFT/edit/approval/approved branch и immutable lineage/audit. |
| [src/main/java/com/cadence/domain/SocialPost.java](../src/main/java/com/cadence/domain/SocialPost.java) | Пометка legacy-only model; existing behaviour сохранено. |
| [src/main/java/com/cadence/presentation/PostController.java](../src/main/java/com/cadence/presentation/PostController.java) | OWNER-only REST API и Location semantics. |
| [src/main/java/com/cadence/presentation/dto/CreatePostRequest.java](../src/main/java/com/cadence/presentation/dto/CreatePostRequest.java) | Allowlisted create fields и exact Strategy version. |
| [src/main/java/com/cadence/presentation/dto/EditPostRequest.java](../src/main/java/com/cadence/presentation/dto/EditPostRequest.java) | Expected version и complete content replacement. |
| [src/main/java/com/cadence/presentation/dto/PostApprovalRequest.java](../src/main/java/com/cadence/presentation/dto/PostApprovalRequest.java) | Separate approval token без writable audit. |
| [src/main/java/com/cadence/presentation/dto/PostContentRequest.java](../src/main/java/com/cadence/presentation/dto/PostContentRequest.java) | Bounded typed nested editorial input. |
| [src/main/java/com/cadence/presentation/dto/StrictPostRequest.java](../src/main/java/com/cadence/presentation/dto/StrictPostRequest.java) | Unknown fields rejected на всех Post request levels. |
| [src/main/resources/db/migration/V47__post_revision_lifecycle.sql](../src/main/resources/db/migration/V47__post_revision_lifecycle.sql) | Normalized tables, populated legacy import, SQL source/audit/history/lineage/format/tag guards. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | Reviewed OpenAPI endpoints/request/response/page/source schemas. |
| [src/test/java/com/cadence/application/PostApiIntegrationTest.java](../src/test/java/com/cadence/application/PostApiIntegrationTest.java) | H2 execution общего API contract. |
| [src/test/java/com/cadence/domain/PostRevisionTest.java](../src/test/java/com/cadence/domain/PostRevisionTest.java) | Domain transition matrix, bounds, encoding, checksum mismatch и cap. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Latest migration inventory47 без ослабления mapping assertions. |
| [src/test/java/com/cadence/persistence/PostgreSqlPostApiTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPostApiTest.java) | Real PostgreSQL API, direct SQL/runtime bypasses, revocation и concurrency/archive races. |
| [src/test/java/com/cadence/persistence/PostgreSqlPostMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPostMigrationTest.java) | Populated V46→V47 exact legacy preservation и immutable history. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Exact48 UTC audit columns после двух новых timestamped tables. |
| [src/test/java/com/cadence/support/PostApiContract.java](../src/test/java/com/cadence/support/PostApiContract.java) | Shared real HTTP/bean flow, platform/source/role/foreign/mass-assignment/stale/replay negatives. |
