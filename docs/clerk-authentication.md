# Clerk authentication

Frontend task23 uses the official React SDK in a static Next.js application and the same
explicit Bearer API contract. See [frontend setup and live qualification](frontend-foundation.md)
and [ADR0040](adr/0040-frontend-bearer-onboarding.md). Backend browser onboarding CORS is
opt-in via `CADENCE_CORS_ALLOWED_ORIGINS` (empty by default); origins must match deployed
Clerk authorized parties. API cookies remain unauthoritative; no CSRF/session contract change.

Cadence is an OAuth 2.0 resource server. The frontend sends a short-lived Clerk JWT in the `Authorization: Bearer <token>` header. Spring Security validates the signature, issuer, expiry/not-before timestamps, audience, and authorized party before Cadence resolves a local user.

## Required environment variables

Configure these values from the Clerk Dashboard:

- `CLERK_ISSUER_URI`: the exact Clerk instance issuer, such as `https://example.clerk.accounts.dev`
- `CLERK_JWKS_URI`: the issuer plus `/.well-known/jwks.json`
- `CLERK_AUDIENCE`: the API audience configured in the token template, normally `cadence-api`
- `CLERK_AUTHORIZED_PARTIES`: comma-separated frontend origins allowed by the token `azp` claim

Production must replace every placeholder from `.env.example`.

## Server-controlled claims

The token used for the Cadence API must contain:

```json
{
  "aud": "cadence-api",
  "cadence_role": "OWNER",
  "cadence_workspace_ids": ["00000000-0000-0000-0000-000000000000"]
}
```

`cadence_role` accepts `OWNER`, `EDITOR`, or `VIEWER`. `cadence_workspace_ids` contains existing Cadence workspace UUIDs. These values must come from Clerk private/server-controlled metadata; never allow users to edit them through profile fields. A new creator starts as `OWNER`; additional collaboration roles are reserved for a later release.

The first valid request creates a local `cadence_users` record keyed by the immutable Clerk `sub`. Later requests resolve that existing record, so the local role and memberships remain the authorization authority. Permission changes must eventually use an administrator-controlled workflow; editing Clerk claims does not mutate an already-provisioned user.

## Endpoint rules

- `/api/v1/health` is public.
- All other endpoints require a valid JWT.
- `OWNER` controls their workspaces and brands.
- `EDITOR` may edit assigned workspace content in a future collaboration release.
- `VIEWER` is read-only and reserved for future collaborators.
- All business operations, including reads and Brand analysis, require `OWNER`. Reserved `EDITOR`/`VIEWER` identities receive 403 even within assigned Workspaces. Stored local roles remain authoritative over JWT role claims.
- Object UUIDs are checked against local workspace membership before the use case runs.
- User-facing use-case Spring beans independently enforce operation roles and validate the supplied Workspace scope against the authenticated principal. Workspace creation cannot name another user as its owner.

Invalid or expired authentication returns `401`. A valid identity without the required role or object membership returns `403`.

## Verification

The real decoder rejects missing audience, authorized party (when configured), and expiration with a controlled authentication failure. JwtDecoderTest verifies signatures and claims using a local JWKS fixture; SecurityIntegrationTest uses a mocked decoder for HTTP policy tests. Neither test proves live Clerk account configuration. See [verification boundaries](verification.md).
