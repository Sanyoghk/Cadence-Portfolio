# ADR-0001: Keep the Spring Boot modular monolith

Status: Accepted; amended 2026-09-11 to match the approved creator-first scope

Date: 2026-09-01

Amendment 2026-10-15: framework migrated to Boot4.0.8/Spring7.0.9 on Java21;
the modular-monolith decision is unchanged. See [migration qualification](../spring4-migration-report.md).
The context below describes the original decision baseline.

## Context

The repository contains a Spring Boot 3.5/Java 21 backend, JPA domain model, controllers, use cases, and tests. Keeping this stack preserves the existing implementation while developing the creator workflow defined in product-scope.md. Earlier prototype-specific pilot and agency framing does not define the product.

## Decision

Keep a Spring Boot modular monolith. Add capabilities as domain/application/presentation vertical slices. Introduce provider-neutral ports at external boundaries: identity claims, brand evidence, AI generation, object storage, jobs, analytics, and future publishing.

The planned frontend is a separate Next.js/React/TypeScript application. There is no iframe requirement and no implemented frontend yet. Long-running work is handled by durable jobs; it does not require a microservice split.

## Consequences

- Existing Java code and tests remain valuable.
- Transactions and relational integrity stay in one deployable backend initially.
- Node-specific recommendations such as BullMQ are translated to equivalent semantics rather than copied directly.
- A later worker deployment may reuse the same codebase/artifact with a different runtime profile.
- Microservices require a future ADR supported by measured scaling or organizational needs.
