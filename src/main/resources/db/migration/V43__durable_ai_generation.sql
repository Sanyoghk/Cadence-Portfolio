-- No legacy receipt/approval/usage backfill.
CREATE TABLE ai_generation_operations (
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0,
 actor_id UUID NOT NULL REFERENCES cadence_users(id),workspace_id UUID NOT NULL REFERENCES workspaces(id),brand_id UUID NOT NULL REFERENCES brands(id),
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('DECLARED','EVIDENCE','STRATEGY')),
 key_digest VARCHAR(64) NOT NULL CHECK(key_digest ~ '^[0-9a-f]{64}$'),request_digest VARCHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
 snapshot VARCHAR(65536) NOT NULL CHECK(octet_length(snapshot)<=65536),snapshot_checksum VARCHAR(64) NOT NULL CHECK(snapshot_checksum=encode(sha256(convert_to(snapshot,'UTF8')),'hex')),
 submitted_at TIMESTAMPTZ NOT NULL,deadline TIMESTAMPTZ NOT NULL CHECK(deadline=submitted_at+interval '60 seconds'),
 job_id UUID NOT NULL UNIQUE REFERENCES jobs(id),status VARCHAR(24) NOT NULL CHECK(status IN ('QUEUED','RUNNING','RETRY_WAIT','SUCCEEDED','SUPERSEDED','FAILED','EXPIRED','OUTCOME_UNKNOWN')),
 result_id UUID UNIQUE,result_json VARCHAR(65536) CHECK(octet_length(result_json)<=65536),completed_at TIMESTAMPTZ,
 UNIQUE(actor_id,workspace_id,kind,key_digest),
 CHECK((status IN ('QUEUED','RUNNING','RETRY_WAIT') AND completed_at IS NULL) OR (status NOT IN ('QUEUED','RUNNING','RETRY_WAIT') AND completed_at IS NOT NULL)),
 CHECK((status='SUCCEEDED' AND result_id IS NOT NULL AND result_json IS NOT NULL) OR (status<>'SUCCEEDED' AND result_id IS NULL AND result_json IS NULL))
);
CREATE INDEX ai_generation_pending_idx ON ai_generation_operations(status,submitted_at,id);
CREATE INDEX ai_generation_workspace_idx ON ai_generation_operations(workspace_id);
CREATE TABLE ai_generation_sources(operation_id UUID NOT NULL REFERENCES ai_generation_operations(id),admission_id UUID NOT NULL REFERENCES evidence_admissions(id),PRIMARY KEY(operation_id,admission_id));
CREATE TABLE ai_generation_attempts(
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0,
 operation_id UUID NOT NULL REFERENCES ai_generation_operations(id),attempt_number INTEGER NOT NULL CHECK(attempt_number BETWEEN 1 AND 3),
 worker_id VARCHAR(255) NOT NULL CHECK(length(worker_id)>0),started_at TIMESTAMPTZ NOT NULL,lease_expires_at TIMESTAMPTZ NOT NULL CHECK(lease_expires_at>started_at),dispatched_at TIMESTAMPTZ,
 analysis_id UUID UNIQUE REFERENCES brand_analysis_attempts(id),UNIQUE(operation_id,attempt_number),
 CHECK(dispatched_at IS NULL OR dispatched_at>=started_at),CHECK(analysis_id IS NULL OR dispatched_at IS NOT NULL)
);
CREATE FUNCTION guard_ai_operation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j jobs%ROWTYPE;b brands%ROWTYPE;w workspaces%ROWTYPE;payload JSONB;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Generation history immutable' USING ERRCODE='23514';END IF;
 IF TG_OP='UPDATE' THEN
  IF (to_jsonb(NEW)-ARRAY['status','result_id','result_json','completed_at','updated_at','lock_version']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['status','result_id','result_json','completed_at','updated_at','lock_version'])
   OR OLD.status NOT IN ('QUEUED','RUNNING','RETRY_WAIT') THEN RAISE EXCEPTION 'Generation history immutable' USING ERRCODE='23514';END IF;
  IF NEW.status='QUEUED' OR (NEW.status='SUCCEEDED' AND OLD.status<>'RUNNING') OR (NEW.status='RETRY_WAIT' AND OLD.status<>'RUNNING')
   THEN RAISE EXCEPTION 'Invalid generation transition' USING ERRCODE='23514';END IF;
 ELSE
  IF NEW.status<>'QUEUED' OR NEW.result_id IS NOT NULL OR NEW.completed_at IS NOT NULL THEN RAISE EXCEPTION 'Invalid generation initial state' USING ERRCODE='23514';END IF;
  PERFORM 1 FROM cadence_users WHERE id=NEW.actor_id AND active AND role='OWNER' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Invalid generation actor' USING ERRCODE='23514';END IF;
  SELECT * INTO w FROM workspaces WHERE id=NEW.workspace_id AND status='ACTIVE' FOR UPDATE;
  SELECT * INTO b FROM brands WHERE id=NEW.brand_id AND workspace_id=NEW.workspace_id AND status='ACTIVE' FOR UPDATE;
  IF w.id IS NULL OR b.id IS NULL OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=NEW.actor_id AND workspace_id=w.id)
   THEN RAISE EXCEPTION 'Invalid generation scope' USING ERRCODE='23514';END IF;
  payload=NEW.snapshot::JSONB;
  IF NEW.kind='STRATEGY' THEN payload=payload->'strategy';ELSE payload=payload->'analysis';END IF;
  IF payload IS NULL OR payload='null'::jsonb OR (payload->>'brandVersion')::BIGINT IS DISTINCT FROM b.lock_version
   OR (payload->>'workspaceVersion')::BIGINT IS DISTINCT FROM w.lock_version OR payload->>'brandName' IS DISTINCT FROM b.name
   OR payload->>'industry' IS DISTINCT FROM w.industry THEN RAISE EXCEPTION 'Invalid generation capture' USING ERRCODE='23514';END IF;
 END IF;
 SELECT * INTO j FROM jobs WHERE id=NEW.job_id;
 IF j.type<>'AI_GENERATION' OR j.max_attempts<>3 THEN RAISE EXCEPTION 'Invalid generation job' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ai_operation_guard BEFORE INSERT OR UPDATE OR DELETE ON ai_generation_operations FOR EACH ROW EXECUTE FUNCTION guard_ai_operation();
CREATE FUNCTION guard_ai_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o ai_generation_operations%ROWTYPE;j jobs%ROWTYPE;a brand_analysis_attempts%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Attempt history immutable' USING ERRCODE='23514';END IF;
 SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.operation_id; SELECT * INTO j FROM jobs WHERE id=o.job_id;
 IF TG_OP='UPDATE' AND ((to_jsonb(NEW)-ARRAY['dispatched_at','analysis_id','updated_at','lock_version']) IS DISTINCT FROM
 (to_jsonb(OLD)-ARRAY['dispatched_at','analysis_id','updated_at','lock_version']) OR OLD.dispatched_at IS NOT NULL)
 THEN RAISE EXCEPTION 'Attempt history immutable' USING ERRCODE='23514';END IF;
 IF o.status<>'RUNNING' OR j.status<>'RUNNING' OR j.attempts<>NEW.attempt_number OR j.lease_owner<>NEW.worker_id
 OR NEW.lease_expires_at IS DISTINCT FROM j.lease_expires_at OR NEW.lease_expires_at>o.deadline OR clock_timestamp()>=j.lease_expires_at OR clock_timestamp()>=o.deadline
 THEN RAISE EXCEPTION 'Stale generation attempt' USING ERRCODE='23514';END IF;
 IF NEW.analysis_id IS NOT NULL THEN
  SELECT * INTO a FROM brand_analysis_attempts WHERE id=NEW.analysis_id;
  IF a.brand_id<>o.brand_id OR a.status<>'RUNNING' OR (o.kind='EVIDENCE' AND a.requested_by IS DISTINCT FROM o.actor_id)
   OR o.kind='STRATEGY' THEN RAISE EXCEPTION 'Invalid attempt result binding' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ai_attempt_guard BEFORE INSERT OR UPDATE OR DELETE ON ai_generation_attempts FOR EACH ROW EXECUTE FUNCTION guard_ai_attempt();
CREATE FUNCTION seal_ai_operation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o ai_generation_operations%ROWTYPE;j jobs%ROWTYPE;a ai_generation_attempts%ROWTYPE;sid UUID;payload JSONB;cnt INTEGER;
BEGIN
 SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.id;SELECT * INTO j FROM jobs WHERE id=o.job_id;
 IF (o.status='SUCCEEDED' AND j.status<>'SUCCEEDED') OR (o.status='RUNNING' AND j.status<>'RUNNING')
 OR (o.status='QUEUED' AND j.status<>'PENDING') OR (o.status='RETRY_WAIT' AND j.status<>'FAILED')
 OR (o.status IN ('FAILED','SUPERSEDED','EXPIRED','OUTCOME_UNKNOWN') AND j.status<>'DEAD')
 THEN RAISE EXCEPTION 'Generation job state mismatch' USING ERRCODE='23514';END IF;
 payload=o.snapshot::JSONB;
 IF j.payload_reference IS DISTINCT FROM 'generation:'||o.id::text OR (payload->>'schemaVersion')::integer IS DISTINCT FROM 1
 THEN RAISE EXCEPTION 'Invalid generation snapshot/job binding' USING ERRCODE='23514';END IF;
 SELECT count(*) INTO cnt FROM ai_generation_sources WHERE operation_id=o.id;
 IF (o.kind='EVIDENCE' AND (cnt NOT BETWEEN 1 AND 5 OR cnt<>jsonb_array_length(payload->'analysis'->'sources')))
 OR (o.kind<>'EVIDENCE' AND cnt<>0) THEN RAISE EXCEPTION 'Generation source set invalid' USING ERRCODE='23514';END IF;
 IF EXISTS(SELECT 1 FROM ai_generation_sources x JOIN evidence_admissions r ON r.id=x.admission_id JOIN brand_evidence e ON e.id=r.evidence_id
 WHERE x.operation_id=o.id AND (e.brand_id<>o.brand_id OR NOT EXISTS(SELECT 1 FROM jsonb_array_elements(payload->'analysis'->'sources') v
 WHERE (v->>'admissionId')::UUID=r.id AND (v->>'evidenceId')::UUID=e.id AND v->>'contentChecksum'=r.content_checksum AND v->>'text'=r.reviewed_text AND (v->>'sourceVersion')::BIGINT=r.source_version)))
 THEN RAISE EXCEPTION 'Generation source binding invalid' USING ERRCODE='23514';END IF;
 IF o.status='SUCCEEDED' THEN
  IF NOT EXISTS(SELECT 1 FROM cadence_users u JOIN workspace_memberships m ON m.user_id=u.id
   JOIN workspaces w ON w.id=m.workspace_id JOIN brands b ON b.workspace_id=w.id
   WHERE u.id=o.actor_id AND u.active AND u.role='OWNER' AND w.id=o.workspace_id AND w.status='ACTIVE' AND b.id=o.brand_id AND b.status='ACTIVE')
  THEN RAISE EXCEPTION 'Generation publication access denied' USING ERRCODE='23514';END IF;
  IF EXISTS(SELECT 1 FROM ai_generation_sources x JOIN evidence_admissions r ON r.id=x.admission_id JOIN brand_evidence e ON e.id=r.evidence_id
   WHERE x.operation_id=o.id AND (e.lock_version<>r.source_version OR e.checksum<>r.source_checksum
    OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals wd JOIN evidence_admissions oldr ON oldr.id=wd.admission_id WHERE oldr.evidence_id=e.id AND oldr.source_version=r.source_version)))
  THEN RAISE EXCEPTION 'Generation publication source withdrawn' USING ERRCODE='23514';END IF;
  SELECT * INTO a FROM ai_generation_attempts WHERE operation_id=o.id AND attempt_number=j.attempts;
  IF a.dispatched_at IS NULL OR clock_timestamp()>=a.lease_expires_at OR clock_timestamp()>=o.deadline THEN RAISE EXCEPTION 'Generation publication expired' USING ERRCODE='23514';END IF;
  IF o.kind='STRATEGY' THEN
   IF NOT EXISTS(SELECT 1 FROM strategies WHERE id=o.result_id AND brand_id=o.brand_id AND generation_id=(payload->'strategy'->>'generationId')::UUID
     AND generation_requested_by=o.actor_id AND generation_identity_id=(payload->'identity'->>'identityId')::UUID)
   THEN RAISE EXCEPTION 'Strategy result binding invalid' USING ERRCODE='23514';END IF;
  ELSE
   IF o.result_id IS DISTINCT FROM a.analysis_id OR NOT EXISTS(SELECT 1 FROM brand_analysis_attempts WHERE id=o.result_id AND brand_id=o.brand_id AND status='SUCCEEDED')
   THEN RAISE EXCEPTION 'Analysis result binding invalid' USING ERRCODE='23514';END IF;
  END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ai_operation_seal AFTER INSERT OR UPDATE ON ai_generation_operations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_ai_operation();
CREATE FUNCTION guard_ai_source() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP<>'INSERT' OR NOT EXISTS(SELECT 1 FROM ai_generation_operations o
 JOIN evidence_admissions r ON r.id=NEW.admission_id JOIN brand_evidence e ON e.id=r.evidence_id
 WHERE o.id=NEW.operation_id AND o.status='QUEUED' AND o.kind='EVIDENCE' AND e.brand_id=o.brand_id
 AND EXISTS(SELECT 1 FROM jsonb_array_elements(o.snapshot::jsonb->'analysis'->'sources') s WHERE (s->>'admissionId')::uuid=r.id))
 THEN RAISE EXCEPTION 'Generation sources immutable' USING ERRCODE='23514';END IF;RETURN NEW;END $$;
CREATE TRIGGER ai_source_guard BEFORE INSERT OR UPDATE OR DELETE ON ai_generation_sources FOR EACH ROW EXECUTE FUNCTION guard_ai_source();

-- A queue-only write cannot detach or complete a generation operation.
CREATE FUNCTION seal_ai_job() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j jobs%ROWTYPE;o ai_generation_operations%ROWTYPE;
BEGIN
 IF NEW.type<>'AI_GENERATION' AND (TG_OP='INSERT' OR OLD.type<>'AI_GENERATION') THEN RETURN NULL;END IF;
 SELECT * INTO j FROM jobs WHERE id=NEW.id;SELECT * INTO o FROM ai_generation_operations WHERE job_id=j.id;
 IF o.id IS NULL OR j.type<>'AI_GENERATION' OR j.max_attempts<>3 OR j.payload_reference IS DISTINCT FROM 'generation:'||o.id::text
 OR (o.status='SUCCEEDED' AND j.status<>'SUCCEEDED') OR (o.status='RUNNING' AND j.status<>'RUNNING')
 OR (o.status='QUEUED' AND j.status<>'PENDING') OR (o.status='RETRY_WAIT' AND j.status<>'FAILED')
 OR (o.status IN ('FAILED','SUPERSEDED','EXPIRED','OUTCOME_UNKNOWN') AND j.status<>'DEAD')
 THEN RAISE EXCEPTION 'Generation queue binding invalid' USING ERRCODE='23514';END IF;
 IF o.status='RUNNING' AND NOT EXISTS(SELECT 1 FROM ai_generation_attempts a WHERE a.operation_id=o.id AND a.attempt_number=j.attempts AND a.worker_id=j.lease_owner AND a.lease_expires_at=j.lease_expires_at)
 THEN RAISE EXCEPTION 'Generation lease binding invalid' USING ERRCODE='23514';END IF;
 IF j.attempts<>(SELECT count(*) FROM ai_generation_attempts WHERE operation_id=o.id)
 THEN RAISE EXCEPTION 'Generation attempt history invalid' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER ai_job_seal AFTER INSERT OR UPDATE ON jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_ai_job();
CREATE FUNCTION guard_ai_terminal_job() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF OLD.type='AI_GENERATION' AND OLD.status IN ('SUCCEEDED','DEAD')
 THEN RAISE EXCEPTION 'Generation terminal queue history immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER ai_terminal_job_guard BEFORE UPDATE ON jobs FOR EACH ROW EXECUTE FUNCTION guard_ai_terminal_job();
