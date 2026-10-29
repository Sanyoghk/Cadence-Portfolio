# ADR-0002: Use Clerk identity with local Workspace memberships

Status: Accepted; amended 2026-09-03

## Context

Cadence is a self-service product. Creators need secure registration, sessions, and account recovery, while the Spring Boot API must enforce tenant ownership independently of frontend claims.

## Decision

Use Clerk for authentication and session issuance. Spring Security validates Clerk JWTs as an OAuth2 resource server. Cadence stores the immutable provider subject, local role, and Workspace memberships.

On first authenticated access, server-controlled claims may bootstrap a local user. The local record is authoritative afterward. `OWNER` is the active MVP role; `EDITOR` and `VIEWER` are reserved for future collaboration. All object access follows Brand → Workspace membership.

## Consequences

- Clerk-specific parsing stays at the security adapter boundary.
- A valid token authenticates but does not by itself authorize a Workspace object.
- Issuer, audience/authorized party, expiry, and signature are validated.
- Another OIDC-compatible provider can later replace Clerk without changing domain ownership.
- Account, domain, key, and billing configuration remains owner-operated external work.
