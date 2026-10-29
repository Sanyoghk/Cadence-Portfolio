# ADR-0004: Use bounded public-data ingestion behind a port

Status: Accepted for pilot, subject to compliance approval

Date: 2026-09-01

## Context

Brand analysis benefits from recent first-party and competitor public content. Official APIs may not provide timely access during the pilot. The supplied brief proposes Apify, but scraping is operationally fragile and may introduce provider, privacy, and terms-of-service risk.

## Decision

Define a `SocialEvidenceProvider` application port. The initial adapter may use an Apify-maintained or explicitly reviewed Actor for public Instagram/competitor evidence only.

Never send passwords, private-account cookies, authenticated browser state, or unrelated personal data. Bound retrieval by post count, time, input size, run time, and monthly cost. Cache normalized evidence and record source/provenance/warnings. TikTok evidence ingestion is P2.

## Consequences

- Scraping failure cannot block manual onboarding.
- The Apify adapter can be replaced by official APIs without changing application use cases.
- A community Actor requires security/data-handling review before configuration.
- Compliance approval remains a production gate.
- Monitoring must detect schema drift, partial results, rate limits, and cost-cap exhaustion.
