# Architecture Decision Records

- [ADR-0001: Keep the Spring Boot modular monolith](0001-spring-boot-modular-monolith.md)
- [ADR-0002: Use Clerk identity with local Workspace memberships](0002-clerk-identity-and-memberships.md)
- [ADR-0003: Use approval-gated CSV export for the MVP](0003-export-first-publishing.md)
- [ADR-0004: Use bounded public-data ingestion behind a port](0004-bounded-public-data-ingestion.md)
- [ADR-0005: Pin URL evidence connections to validated public IP addresses](0005-pinned-url-evidence-fetching.md)
- [ADR-0006: Bounded website metadata](0006-bounded-website-metadata.md)
- [ADR-0007: Private evidence pipeline, generations and quotas](0007-private-evidence-pipeline.md)
- [ADR-0008: Reviewed API contract, safe errors and UTC](0008-reviewed-api-contract-and-utc.md)

- [ADR-0009: Brand analysis port and fenced publication](0009-brand-analysis-port-and-fenced-publication.md)

- [ADR-0010: Strategy generation and immutable history](0010-strategy-generation-and-immutable-history.md)

- [ADR-0011: Strategy REST lifecycle, lineage and approval audit](0011-strategy-rest-lifecycle.md)

- [ADR-0012: Versioned Brand Identity manual drafts](0012-brand-identity-drafts.md)
- [ADR-0013: Явный review текстового evidence перед AI](0013-evidence-admission.md)
- [ADR-0014: Versioned TEXT re-review](0014-versioned-text-re-review.md)
- [ADR-0015: Bounded TEXT analysis и citations](0015-bounded-text-analysis-citations.md)
- [ADR-0016: Evidence Identity provenance и approval](0016-evidence-identity-provenance.md)
- [ADR-0017: PDF review eligibility](0017-pdf-review-eligibility.md)
- [ADR-0018: PDF admission history](0018-pdf-admission-history.md)
- [ADR-0019: PDF evidence analysis](0019-pdf-evidence-analysis.md)
- [ADR-0020: PDF Identity consumption](0020-pdf-identity-consumption.md)
- [ADR-0021: Source-kind AI eligibility — accepted](0021-source-kind-ai-eligibility.md)
- [ADR-0022: URL textual human admission](0022-url-text-admission.md)

- [ADR-0023: URL textual generation и Identity](0023-url-analysis-identity.md)

- [ADR0024 — Visual provider boundary](0024-visual-evidence-provider-boundary.md): exact contracts, REQUIRES_PROVIDER #120.
- [ADR0025 — Social text review/consumption](0025-social-text-review-consumption.md): typed snapshot, same-account policy, v3 digest и legacy downgrade guards.

- [ADR0026 — APPROVED Identity → Strategy](0026-approved-identity-strategy-source.md).
- [ADR0027 — Backend first, AI adapters last](0027-backend-first-ai-adapters-last.md).
- [ADR0028 — Durable AI workflow, idempotency и budgets](0028-durable-ai-workflow-contracts.md).
- [ADR0029 — Полный Strategy Playbook v2](0029-full-playbook-contract.md).
- [ADR0030 — Generation budget ledger](0030-generation-budget-ledger.md).
- [ADR0031 — Post identity/revision lifecycle](0031-post-revision-lifecycle.md).
- [ADR0032 — Durable Content generation](0032-content-generation-jobs.md).
- [ADR0033 — Calendar scheduling](0033-calendar-scheduling.md).
- [ADR0034 — Private demo media](0034-private-demo-media.md).
- [ADR0035 — Durable private CSV export](0035-durable-csv-export.md).

## Frontend foundation

- [ADR0040 — Static creator frontend, Clerk Bearer and onboarding](0040-frontend-bearer-onboarding.md)

## Расширенное ТЗ2026-10-20

- [ADR0041 — Media production, market intelligence и approved publishing](0041-expanded-creator-production-scope.md):
  CSV-only product boundary заменена новым user scope; safeguards/history сохраняются.
- [ADR0042 — Один Workspace на аккаунт, несколько Brands](0042-single-workspace-per-account.md):
  новая cardinality, автоматический onboarding, миграция legacy memberships и Brand UI;
  решение принято пользователем, implementation pending.
