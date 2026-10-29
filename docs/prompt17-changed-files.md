# Изменённые файлы — задание17

[Полный отчёт](prompt17-media-report.md). Каждый файл текущего slice; source/build632 hashes
зафиксированы до full gate. Old Flyway V1–49 checksums не изменялись.

| Файл | Назначение |
| --- | --- |
| `DEVELOPMENT_PLAN.md` | Точка продолжения17, local qualification и границы18/live scope. |
| `DEVELOPMENT_PLAN_RU.md` | Синхронная актуализация русского плана. |
| `docker/minio/runtime-policy.json` | IAM Put/Get для media без Delete; quarantine scope сохранён. |
| `docker/postgres/runtime-permissions.sql` | Column-level media updates и retained history privileges. |
| `docs/adr/0034-private-demo-media.md` | Решения lifecycle, demo scope, retention, delivery, locks и threat model. |
| `docs/astra-medium-mvp-prompts.md` | Execution note17: delivered boundaries без повторения prerequisite14–16. |
| `docs/evidence-operations.md` | Операционная граница IAM quarantine/media. |
| `docs/issue-sync.md` | Actual #98/#107 mapping, local status и no remote writes/closure. |
| `docs/media-assets-api.md` | Фактические routes/DTO/statuses, bounds, preview/review/replacement/retention. |
| `docs/mvp-prompt-progress.md` | Qualification ledger17 и следующий18. |
| `docs/prompt17-changed-files.md` | Каждый изменённый файл и его назначение. |
| `docs/prompt17-media-report.md` | Acceptance matrix, security review, executed qualification/results/remaining scope. |
| `docs/s3-storage.md` | Sample IAM с private media Put/Get и запретом удаления. |
| `scripts/test_verify_postgres_report.py` | Regression missing/skipped/failed media PostgreSQL report. |
| `scripts/verify_postgres_report.py` | Обязательная PostgreSqlMediaTest без skips/failures. |
| `src/main/java/com/cadence/application/exceptions/MediaUnavailableException.java` | Controlled unavailable signal без provider message/cause. |
| `src/main/java/com/cadence/application/ports/DemoMediaSource.java` | Точный local/test producer contract, не live AI adapter. |
| `src/main/java/com/cadence/application/ports/PrivateMediaStorage.java` | Typed retained store/read capability без delete/URL/path. |
| `src/main/java/com/cadence/application/repositories/MediaAssetRepository.java` | Scoped locks, indexed list и durable retained quota queries. |
| `src/main/java/com/cadence/application/results/MediaDetails.java` | Safe metadata/audit/provenance/warnings DTO; no private reference. |
| `src/main/java/com/cadence/application/results/PostDetails.java` | Optional nonempty mediaAssetIds сохраняют прежний empty-media JSON shape. |
| `src/main/java/com/cadence/application/services/MediaVerification.java` | Capture/private I/O/recapture, deadline и bounded capacity; empty paths не throttled. |
| `src/main/java/com/cadence/application/services/PostMediaPolicy.java` | Same-Brand readiness/format/order/cap/duplicate checks и asset locks. |
| `src/main/java/com/cadence/application/usecases/MediaAssetUseCase.java` | Scoped staged admission, human review/archive/list/read/delivery и held quota. |
| `src/main/java/com/cadence/application/usecases/PostLifecycleUseCase.java` | Physical verification в edit/approval/replacement, approved branch с единым audit instant. |
| `src/main/java/com/cadence/domain/DemoMediaFixture.java` | Immutable exact golden byte descriptors/checksums. |
| `src/main/java/com/cadence/domain/MediaAsset.java` | One-way domain lifecycle, immutable receipt и explicit human moderation/archive audit. |
| `src/main/java/com/cadence/domain/MediaFormat.java` | Supported PNG/MP4 contentType/extensions без произвольных MIME. |
| `src/main/java/com/cadence/domain/MediaObjectKey.java` | Server-owned Brand/asset/checksum key, digest и bytes bounds. |
| `src/main/java/com/cadence/domain/PostRevision.java` | Ordered asset refs, DRAFT media edit и human branch inheritance; generated refs остаются новыми. |
| `src/main/java/com/cadence/infrastructure/content/DemoMediaConfiguration.java` | Packaged fixed fixtures, prod/mixed-profile fail-closed. |
| `src/main/java/com/cadence/infrastructure/storage/FileSystemEvidenceObjectStorage.java` | Shared reviewed path/read/write guards с отдельным media namespace. |
| `src/main/java/com/cadence/infrastructure/storage/PrivateMediaStorageAdapter.java` | Отдельная capability делегирует только reviewed FS/S3 implementations. |
| `src/main/java/com/cadence/infrastructure/storage/S3EvidenceObjectStorage.java` | Shared private immutable bounded S3 machinery для typed media keys. |
| `src/main/java/com/cadence/presentation/ApiExceptionHandler.java` | Safe media_unavailable503 без details upstream. |
| `src/main/java/com/cadence/presentation/MediaController.java` | OWNER routes, no-store/private bounded bytes, demo headers и reference replacement. |
| `src/main/java/com/cadence/presentation/dto/CreateMediaRequest.java` | Fixture-only strict request allowlist. |
| `src/main/java/com/cadence/presentation/dto/ReplacePostMediaRequest.java` | Expected revision version и bounded unique IDs domain checks. |
| `src/main/java/com/cadence/presentation/dto/ReviewMediaRequest.java` | Отдельное human decision с expectedVersion. |
| `src/main/resources/db/migration/V50__private_demo_media.sql` | Assets/refs schema, metadata/state/actor/quota guards, deferred final compatibility/uniqueness. |
| `src/main/resources/demo-media/README.md` | Golden provenance, actual encoding tool/descriptor и versioning policy. |
| `src/main/resources/demo-media/demo-image-v1.png` | Authored watermarked DEMO PNG, не AI output. |
| `src/main/resources/demo-media/demo-video-v1.mp4` | Authored watermarked DEMO MP4/H.264, не AI output. |
| `src/main/resources/openapi/cadence.json` | 8 media operations и dedicated DTO schemas; existing paths unchanged. |
| `src/test/java/com/cadence/application/MediaApiIntegrationTest.java` | H2 shared actual HTTP/lifecycle contract. |
| `src/test/java/com/cadence/domain/MediaAssetTest.java` | State/moderation/deadline/bytes-key invariants. |
| `src/test/java/com/cadence/domain/PostRevisionTest.java` | Human inheritance vs regenerated media refs, immutable provenance. |
| `src/test/java/com/cadence/infrastructure/content/DemoMediaConfigurationTest.java` | Prod/mixed-profile rejection и exact packaged fixture bytes. |
| `src/test/java/com/cadence/infrastructure/storage/ClamdContainerIntegrationTest.java` | Actual PNG/MP4 scans real clamd/official signatures, no moderation claim. |
| `src/test/java/com/cadence/infrastructure/storage/FileSystemEvidenceObjectStorageTest.java` | Media prefix/size/hash boundaries и Linux linked-parent rejection. |
| `src/test/java/com/cadence/infrastructure/storage/MinioEvidenceObjectStorageTest.java` | Real private media roundtrip/immutability/bounds, denied anonymous/Delete/cross namespace. |
| `src/test/java/com/cadence/persistence/PostgreSqlMediaTest.java` | SQL/runtime quota/history/receipt/format, failure injection и stale store/delivery/approval races. |
| `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java` | Exact current migration inventory1–50. |
| `src/test/java/com/cadence/persistence/PostgreSqlPostMigrationTest.java` | Legacy upgrade сохраняет audit и не фабрикует media. |
| `src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java` | Точная UTC timestamp inventory54. |
| `src/test/java/com/cadence/presentation/OpenApiContractTest.java` | New DTO field/enum checks и exact routes/OpenAPI consistency. |
| `src/test/java/com/cadence/support/MediaApiContract.java` | Shared IG/TikTok/reference/review/delivery/retention/reorder/security scenarios. |
