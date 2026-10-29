-- No legacy usage, cost, reservation or approval backfill. Stop pre-V45 writers before upgrade.
CREATE TABLE generation_budget_mutex(id INTEGER PRIMARY KEY CHECK(id=1));
INSERT INTO generation_budget_mutex VALUES(1);
CREATE TABLE generation_reservations(
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0,
 request_id UUID NOT NULL UNIQUE, operation_id UUID UNIQUE REFERENCES ai_generation_operations(id),
 actor_id UUID NOT NULL REFERENCES cadence_users(id),workspace_id UUID NOT NULL REFERENCES workspaces(id),brand_id UUID NOT NULL REFERENCES brands(id),
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('DECLARED','EVIDENCE','STRATEGY')),
 request_digest VARCHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
 namespace VARCHAR(24) NOT NULL CHECK(namespace IN ('SYNTHETIC','LIVE')),
 policy_version VARCHAR(64) NOT NULL CHECK(policy_version ~ '^[A-Za-z0-9._:-]{1,64}$'),
 policy_snapshot VARCHAR(1024) NOT NULL CHECK(length(policy_snapshot) BETWEEN 1 AND 1024 AND policy_snapshot ~ '^[a-z0-9=;:-]+$'),
 quote_version VARCHAR(64) NOT NULL CHECK(quote_version ~ '^[A-Za-z0-9._:-]{1,64}$'),
 unit VARCHAR(24) NOT NULL CHECK(unit ~ '^[A-Z_]{1,24}$'),scale INTEGER NOT NULL CHECK(scale BETWEEN 0 AND 9),
 period_start TIMESTAMPTZ NOT NULL,period_end TIMESTAMPTZ NOT NULL,deadline TIMESTAMPTZ NOT NULL,
 reserved BIGINT NOT NULL CHECK(reserved BETWEEN 1 AND 1000000000),state VARCHAR(16) NOT NULL CHECK(state IN ('RESERVED','HELD_UNKNOWN','SETTLED','RELEASED')),
 dispatched_at TIMESTAMPTZ,attempt_number INTEGER CHECK(attempt_number BETWEEN 1 AND 3),settled_at TIMESTAMPTZ,
 actual BIGINT CHECK(actual>=0),usage_origin VARCHAR(16) CHECK(usage_origin IN ('SYNTHETIC','MEASURED')),usage_evidence VARCHAR(64) CHECK(usage_evidence ~ '^[0-9a-f]{64}$'),
 CHECK((namespace='SYNTHETIC' AND unit='SYNTHETIC_CREDIT' AND scale=0 AND quote_version='synthetic-fixed-v1' AND (usage_origin IS NULL OR usage_origin='SYNTHETIC'))
    OR (namespace='LIVE' AND unit<>'SYNTHETIC_CREDIT' AND (usage_origin IS NULL OR usage_origin='MEASURED'))),
 CHECK(period_start=date_trunc('day',period_start AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AND period_end=period_start+interval '24 hours'),
 CHECK(deadline>period_start AND deadline<=period_end+interval '60 seconds'),
 CHECK((state IN ('RESERVED','RELEASED') AND dispatched_at IS NULL AND attempt_number IS NULL) OR
       (state IN ('HELD_UNKNOWN','SETTLED') AND dispatched_at IS NOT NULL AND attempt_number IS NOT NULL AND dispatched_at>=period_start AND dispatched_at<deadline)),
 CHECK((state='SETTLED' AND actual IS NOT NULL AND usage_origin IS NOT NULL AND usage_evidence IS NOT NULL AND settled_at IS NOT NULL AND settled_at>=dispatched_at) OR
       (state<>'SETTLED' AND actual IS NULL AND usage_origin IS NULL AND usage_evidence IS NULL AND settled_at IS NULL))
);
CREATE INDEX generation_budget_account_idx ON generation_reservations(actor_id,state,period_end);
CREATE INDEX generation_budget_workspace_idx ON generation_reservations(workspace_id,state,period_end);
CREATE INDEX generation_budget_global_idx ON generation_reservations(state,period_end);
CREATE FUNCTION guard_generation_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o ai_generation_operations%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Accounting history immutable' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM generation_budget_mutex WHERE id=1 FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Accounting mutex missing' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.state<>'RESERVED' THEN RAISE EXCEPTION 'Reservation initial state invalid' USING ERRCODE='23514';END IF;
  IF NOT EXISTS(SELECT 1 FROM brands WHERE id=NEW.brand_id AND workspace_id=NEW.workspace_id)
    OR NOT EXISTS(SELECT 1 FROM cadence_users u JOIN workspace_memberships m ON m.user_id=u.id
      WHERE u.id=NEW.actor_id AND u.active AND u.role='OWNER' AND m.workspace_id=NEW.workspace_id)
    THEN RAISE EXCEPTION 'Reservation scope invalid' USING ERRCODE='23514';END IF;
  IF NEW.operation_id IS NOT NULL THEN
    SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.operation_id;
    IF o.id IS NULL OR NEW.request_id<>o.id OR NEW.actor_id<>o.actor_id OR NEW.workspace_id<>o.workspace_id
      OR NEW.brand_id<>o.brand_id OR NEW.kind<>o.kind OR NEW.request_digest<>o.request_digest OR NEW.deadline<>o.deadline
      OR o.status<>'QUEUED' THEN RAISE EXCEPTION 'Reservation operation mismatch' USING ERRCODE='23514';END IF;
  ELSIF NEW.kind IN ('DECLARED','EVIDENCE') THEN
    IF NOT EXISTS(SELECT 1 FROM brand_analysis_attempts a WHERE a.id=NEW.request_id AND a.brand_id=NEW.brand_id
      AND a.source_checksum=NEW.request_digest AND a.deadline=NEW.deadline AND a.status='RUNNING')
    THEN RAISE EXCEPTION 'Synchronous reservation source mismatch' USING ERRCODE='23514';END IF;
  END IF;
 ELSE
  IF OLD.state='RESERVED' AND NEW.state='HELD_UNKNOWN' AND clock_timestamp()>=NEW.deadline THEN RAISE EXCEPTION 'Expired budget dispatch' USING ERRCODE='23514';END IF;
  IF (to_jsonb(NEW)-ARRAY['state','dispatched_at','attempt_number','settled_at','actual','usage_origin','usage_evidence','updated_at','lock_version'])
      IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['state','dispatched_at','attempt_number','settled_at','actual','usage_origin','usage_evidence','updated_at','lock_version'])
    OR NOT ((OLD.state='RESERVED' AND NEW.state IN ('HELD_UNKNOWN','RELEASED')) OR (OLD.state='HELD_UNKNOWN' AND NEW.state='SETTLED'))
    OR (OLD.dispatched_at IS NOT NULL AND (NEW.dispatched_at IS DISTINCT FROM OLD.dispatched_at OR NEW.attempt_number IS DISTINCT FROM OLD.attempt_number))
    THEN RAISE EXCEPTION 'Accounting history immutable' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER generation_reservation_guard BEFORE INSERT OR UPDATE OR DELETE ON generation_reservations
 FOR EACH ROW EXECUTE FUNCTION guard_generation_reservation();
-- Dispatch must have the same immutable accounting witness at transaction commit.
CREATE FUNCTION seal_generation_budget_dispatch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.dispatched_at IS NOT NULL THEN RETURN NULL;END IF;
 IF NEW.dispatched_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM generation_reservations r
   WHERE r.operation_id=NEW.operation_id AND r.attempt_number=NEW.attempt_number
     AND r.state IN ('HELD_UNKNOWN','SETTLED') AND r.dispatched_at=NEW.dispatched_at)
 THEN RAISE EXCEPTION 'Missing generation accounting dispatch' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER generation_budget_dispatch AFTER INSERT OR UPDATE ON ai_generation_attempts
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_generation_budget_dispatch();

CREATE FUNCTION guard_generation_budget_mutex() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Accounting mutex immutable' USING ERRCODE='23514';END $$;
CREATE TRIGGER generation_budget_mutex_guard BEFORE UPDATE OR DELETE ON generation_budget_mutex
 FOR EACH ROW EXECUTE FUNCTION guard_generation_budget_mutex();
ALTER TABLE ai_generation_attempts DROP CONSTRAINT ai_attempt_completion;
ALTER TABLE ai_generation_attempts ADD CONSTRAINT ai_attempt_completion CHECK (
 (finished_at IS NULL AND outcome IS NULL AND reason IS NULL) OR
 (finished_at IS NOT NULL AND outcome IS NOT NULL AND reason IS NOT NULL
  AND finished_at>=started_at AND (dispatched_at IS NULL OR finished_at>=dispatched_at)
  AND outcome IN ('SUCCEEDED','RETRY_SAFE','SUPERSEDED','FAILED','EXPIRED','OUTCOME_UNKNOWN')
  AND reason IN ('COMPLETED','LEASE_EXPIRED_BEFORE_DISPATCH','INPUT_UNAVAILABLE','SOURCE_OR_ACCESS_CHANGED','INVALID_OUTPUT','DEADLINE_EXPIRED','PROVIDER_OUTCOME_UNKNOWN','CAPABILITY_UNAVAILABLE','PUBLICATION_REJECTED','BUDGET_EXCEEDED')
  AND (outcome<>'RETRY_SAFE' OR dispatched_at IS NULL)
  AND (outcome<>'SUCCEEDED' OR (dispatched_at IS NOT NULL AND reason='COMPLETED'))
  AND (reason<>'COMPLETED' OR outcome='SUCCEEDED')));

CREATE FUNCTION seal_generation_reservation_dispatch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.operation_id IS NOT NULL AND NEW.state IN ('HELD_UNKNOWN','SETTLED') AND NOT EXISTS(
   SELECT 1 FROM ai_generation_attempts a WHERE a.operation_id=NEW.operation_id AND a.attempt_number=NEW.attempt_number AND a.dispatched_at=NEW.dispatched_at)
 THEN RAISE EXCEPTION 'Unbound accounting dispatch' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER generation_reservation_dispatch AFTER INSERT OR UPDATE ON generation_reservations
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_generation_reservation_dispatch();
