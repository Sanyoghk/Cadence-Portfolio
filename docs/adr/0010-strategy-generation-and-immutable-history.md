# ADR-0010: Strategy generation with fenced publication

Status: accepted for #97 repository scope. Date: 2026-10-04.

## Context

Generation may run while Brand changes or Workspace is archived. Holding DB locks
across model I/O would retain scarce connections and block unrelated lifecycle work.
Model output must not approve content or overwrite approved history.

## Decision

Use immutable StrategyGenerationService input/output, short snapshot/completion
transactions and external I/O between them. Lock Workspace→Brand consistently;
compare versions and exact source content before creating next DRAFT version.
Store immutable generation provenance; enforce source/deadline and generated content
in PostgreSQL V24 with deferred collection checks. Keep legacy metadata NULL.
Default production disabled; fake restricted to local/test. Use defensive copies for
nested Strategy values and direct JDBC LOCAL_TIME mapping for publishing wall times.

## Alternatives / consequences

A single DB transaction during I/O consumes connections/locks for provider latency.
Unconditional replace loses history and permits stale output. Optimistic insert alone
cannot serialize next-version allocation. Chosen locks are brief; Brand is the serial
publication boundary. Concurrent valid requests can create two versions. No automatic
provider retry/idempotency/AI Job/budget is claimed; #120 owns real provider bounds,
#102 owns HTTP/approval/versioned edits, #107 tracks approved Brand Identity work.
No new dependency. Runtime privileges and DB guards add migration/operational cost;
full populated migration and real runtime-role tests are required.
