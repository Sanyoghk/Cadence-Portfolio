-- Existing completion/usage is unknown: never fabricate historical outcomes.
ALTER TABLE ai_generation_attempts ADD COLUMN finished_at TIMESTAMPTZ;
ALTER TABLE ai_generation_attempts ADD COLUMN outcome VARCHAR(24);
ALTER TABLE ai_generation_attempts ADD COLUMN reason VARCHAR(40);
ALTER TABLE ai_generation_attempts ADD COLUMN completion_schema SMALLINT NOT NULL DEFAULT 0 CHECK(completion_schema IN (0,1));
ALTER TABLE ai_generation_attempts ALTER COLUMN completion_schema SET DEFAULT 1;
ALTER TABLE ai_generation_attempts ADD CONSTRAINT ai_attempt_completion CHECK (
 (finished_at IS NULL AND outcome IS NULL AND reason IS NULL) OR
 (finished_at IS NOT NULL AND outcome IS NOT NULL AND reason IS NOT NULL
  AND finished_at>=started_at AND (dispatched_at IS NULL OR finished_at>=dispatched_at)
  AND outcome IN ('SUCCEEDED','RETRY_SAFE','SUPERSEDED','FAILED','EXPIRED','OUTCOME_UNKNOWN')
  AND reason IN ('COMPLETED','LEASE_EXPIRED_BEFORE_DISPATCH','INPUT_UNAVAILABLE','SOURCE_OR_ACCESS_CHANGED','INVALID_OUTPUT','DEADLINE_EXPIRED','PROVIDER_OUTCOME_UNKNOWN','CAPABILITY_UNAVAILABLE','PUBLICATION_REJECTED')
  AND (outcome<>'RETRY_SAFE' OR dispatched_at IS NULL)
  AND (outcome<>'SUCCEEDED' OR (dispatched_at IS NOT NULL AND reason='COMPLETED'))
  AND (reason<>'COMPLETED' OR outcome='SUCCEEDED')));
CREATE OR REPLACE FUNCTION guard_ai_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o ai_generation_operations%ROWTYPE;j jobs%ROWTYPE;a brand_analysis_attempts%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Attempt history immutable' USING ERRCODE='23514';END IF;
 SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.operation_id; SELECT * INTO j FROM jobs WHERE id=o.job_id;
 IF TG_OP='UPDATE' THEN
  IF (to_jsonb(NEW)-ARRAY['dispatched_at','analysis_id','finished_at','outcome','reason','updated_at','lock_version']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['dispatched_at','analysis_id','finished_at','outcome','reason','updated_at','lock_version'])
     OR OLD.finished_at IS NOT NULL OR o.status<>'RUNNING'
  THEN RAISE EXCEPTION 'Attempt history immutable' USING ERRCODE='23514';END IF;
  IF NEW.finished_at IS NOT NULL THEN
   IF NEW.dispatched_at IS DISTINCT FROM OLD.dispatched_at OR NEW.analysis_id IS DISTINCT FROM OLD.analysis_id
    OR j.status<>'RUNNING' OR j.attempts<>NEW.attempt_number OR j.lease_owner IS DISTINCT FROM NEW.worker_id
   THEN RAISE EXCEPTION 'Invalid attempt completion' USING ERRCODE='23514';END IF;
   -- Completion after lease expiry is cleanup only, never publication authority.
   IF NEW.outcome='SUCCEEDED' AND (clock_timestamp()>=NEW.lease_expires_at OR clock_timestamp()>=o.deadline)
   THEN RAISE EXCEPTION 'Expired attempt completion' USING ERRCODE='23514';END IF;
   RETURN NEW;
  END IF;
  IF OLD.dispatched_at IS NOT NULL THEN RAISE EXCEPTION 'Dispatch immutable' USING ERRCODE='23514';END IF;
 ELSE
  IF NEW.finished_at IS NOT NULL OR NEW.completion_schema<>1 THEN RAISE EXCEPTION 'Invalid initial attempt' USING ERRCODE='23514';END IF;
 END IF;
 IF o.status<>'RUNNING' OR j.status<>'RUNNING' OR j.attempts<>NEW.attempt_number OR j.lease_owner IS DISTINCT FROM NEW.worker_id
 OR NEW.lease_expires_at IS DISTINCT FROM j.lease_expires_at OR NEW.lease_expires_at>o.deadline OR clock_timestamp()>=j.lease_expires_at OR clock_timestamp()>=o.deadline
 THEN RAISE EXCEPTION 'Stale generation attempt' USING ERRCODE='23514';END IF;
 IF NEW.analysis_id IS NOT NULL THEN
  SELECT * INTO a FROM brand_analysis_attempts WHERE id=NEW.analysis_id;
  IF a.brand_id IS DISTINCT FROM o.brand_id OR a.status IS DISTINCT FROM 'RUNNING' OR (o.kind='EVIDENCE' AND a.requested_by IS DISTINCT FROM o.actor_id)
   OR o.kind='STRATEGY' THEN RAISE EXCEPTION 'Invalid attempt result binding' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION seal_ai_attempt_completion() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a ai_generation_attempts%ROWTYPE;o ai_generation_operations%ROWTYPE;j jobs%ROWTYPE;
BEGIN
 SELECT * INTO a FROM ai_generation_attempts WHERE id=NEW.id;
 SELECT * INTO o FROM ai_generation_operations WHERE id=a.operation_id;
 SELECT * INTO j FROM jobs WHERE id=o.job_id;
 IF a.finished_at IS NOT NULL THEN
  IF (a.outcome='RETRY_SAFE' AND (a.dispatched_at IS NOT NULL OR (j.attempts=a.attempt_number AND o.status NOT IN ('RETRY_WAIT','FAILED','EXPIRED'))))
   OR (a.outcome<>'RETRY_SAFE' AND (o.status IS DISTINCT FROM a.outcome OR j.attempts<>a.attempt_number))
  THEN RAISE EXCEPTION 'Attempt completion state mismatch' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ai_attempt_completion_seal AFTER INSERT OR UPDATE ON ai_generation_attempts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_ai_attempt_completion();

-- Distinguish legacy rows without inventing completion evidence.
CREATE FUNCTION seal_ai_completion_job() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a ai_generation_attempts%ROWTYPE;o ai_generation_operations%ROWTYPE;j jobs%ROWTYPE;
BEGIN
 IF NEW.type<>'AI_GENERATION' THEN RETURN NULL;END IF;
 SELECT * INTO j FROM jobs WHERE id=NEW.id;
 SELECT * INTO o FROM ai_generation_operations WHERE job_id=j.id;
 SELECT * INTO a FROM ai_generation_attempts WHERE operation_id=o.id AND attempt_number=j.attempts;
 IF a.completion_schema=1 AND (
  (o.status='RUNNING' AND a.finished_at IS NOT NULL) OR
  (o.status NOT IN ('RUNNING','QUEUED') AND a.finished_at IS NULL))
 THEN RAISE EXCEPTION 'Missing or inconsistent attempt completion' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ai_completion_job_seal AFTER INSERT OR UPDATE ON jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_ai_completion_job();
