# Cadence

A creator workspace for turning brand material into reviewed brand identity, content
strategy, a content calendar and performance insights.

This repository is a **public engineering portfolio snapshot**. It demonstrates a Java
backend with explicit tenant boundaries, versioned approvals, durable jobs and a React
frontend. Development is ongoing; live AI generation, social publishing and production
deployment are not claimed as complete.

![Cadence landing page](docs/design/ui-rework/implementation/landing-desktop.jpg)

## Review guide

- [Architecture decisions](docs/adr/README.md) — the decisions and their trade-offs.
- [Backend security contract](docs/backend-security-gate.md) — authorization, audit and limits.
- [API contract](docs/api-contract.md) — DTOs, safe errors, pagination and versioning.
- [Generation jobs](docs/prompt10-durable-generation-report.md) — durable execution and recovery.
- [Frontend foundation](docs/frontend-foundation.md) — static frontend, Clerk and Bearer transport.
- [Verification boundaries](docs/verification.md) — what the tests prove and what remains external.
- [Publication notes](docs/publication-release-2026-10-29.md) — snapshot scope and privacy checks.

## Implemented and planned

| Area | Included in this snapshot | Remaining work |
| --- | --- | --- |
| Accounts and tenancy | Clerk JWT validation, backend OWNER authorization, Workspace/Brand APIs | Live account lifecycle qualification; single-Workspace model migration |
| Evidence | Quarantine, bounded URL ingestion, malware scanning, isolated rendering and explicit review | Production storage/egress/provider qualification |
| Brand and strategy | Versioned drafts, human approval, provenance and provider-neutral generation ports | Real AI provider selection and adapters |
| Content | Post revisions, durable jobs, calendar, private demo assets and CSV export | Real photo/video generation and approved social publishing |
| Performance | Manual/CSV metrics, analytics, evidence-linked recommendations and private reports | Live platform synchronization and commercial operations |
| Frontend | Next.js/React application with onboarding and evidence/Identity workflows | Remaining workflow screens and live end-to-end qualification |

The product target is **one Workspace per account with multiple Brands**. The exported
implementation predates that migration and still contains the earlier Workspace model.
The separate, newer frontend and unpushed development changes are not part of this snapshot.

## Architecture

Java 21 · Spring Boot 4 · Spring Security · Spring Data JPA · PostgreSQL · Flyway ·
JUnit/Mockito/Testcontainers · Next.js/React/TypeScript.

```text
HTTP / DTO validation
    → authentication + resource authorization
    → application use cases
    → domain invariants
    → repositories / PostgreSQL constraints

Durable jobs → bounded infrastructure adapters → recheck permissions and source versions
```

The backend is a modular monolith. Database constraints protect critical invariants;
external I/O is kept outside long database transactions. Approved history is immutable:
changes create a new draft. Local fixtures are isolated from production provider activation.

## Build and verify

Prerequisites: Java 21, Python 3, and Docker for the complete integration suite.

```powershell
.\mvnw.cmd clean verify
python -m unittest discover -s scripts -p 'test_*.py'
```

On Linux/macOS use `sh ./mvnw clean verify`. Renderer tests additionally require the
documented Linux sandbox support. Missing/skipped required integration tests are not a full pass.
See [renderer qualification](docs/isolated-evidence-rendering.md).

For the frontend, use the pinned Node/pnpm versions and commands in
[frontend setup](docs/frontend-foundation.md).

[GitHub Actions](https://github.com/Sanyoghk/Cadence-Portfolio/actions) contains the current
results for this repository. Older execution reports describe their original revisions;
they do not establish that this public snapshot has passed every runtime or deployment gate.

## Configuration and security

Use `.env.example` as a template and keep real values in ignored local files or deployment
secrets. The `prod` profile requires PostgreSQL and schema validation. Business operations
require valid authentication and backend resource authorization. Missing live provider
configuration fails closed; demo assets and fixtures are explicitly identified.

See [SECURITY.md](SECURITY.md) for private vulnerability reporting. Please do not put secrets,
personal data or sensitive exploit details in public issues.

## Snapshot history

This repository starts from an independently initialized Git history. Private development
history, issues, logs and credentials were not copied. Historical documents may refer to
private development issues or old commit hashes; those links are archival context, not public
acceptance evidence. The publication audit covers source exposure, not a production security
certification. Third-party font license notices remain alongside their assets.
