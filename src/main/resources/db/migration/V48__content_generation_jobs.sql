-- Existing snapshots/history are never rewritten or assigned invented generation provenance.
ALTER TABLE ai_generation_operations DROP CONSTRAINT ai_generation_operations_kind_check;
ALTER TABLE ai_generation_operations ADD CHECK(kind IN ('DECLARED','EVIDENCE','STRATEGY','CONTENT'));
ALTER TABLE generation_reservations DROP CONSTRAINT generation_reservations_kind_check;
ALTER TABLE generation_reservations ADD CHECK(kind IN ('DECLARED','EVIDENCE','STRATEGY','CONTENT'));
CREATE TABLE content_generation_batches (
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 operation_id UUID NOT NULL UNIQUE REFERENCES ai_generation_operations(id),attempt_number INTEGER NOT NULL,
 output_checksum VARCHAR(64) NOT NULL CHECK(output_checksum ~ '^[0-9a-f]{64}$'),
 FOREIGN KEY(operation_id,attempt_number) REFERENCES ai_generation_attempts(operation_id,attempt_number)
);
ALTER TABLE content_posts ADD outline_slot_id VARCHAR(64) CHECK(outline_slot_id ~ '^[A-Za-z0-9_-]{1,64}$');
ALTER TABLE content_posts ADD FOREIGN KEY(strategy_id,outline_slot_id) REFERENCES strategy_playbook_slots(strategy_id,slot_id);
ALTER TABLE post_revisions ADD generation_batch_id UUID REFERENCES content_generation_batches(id), ADD generation_slot_id VARCHAR(64);
ALTER TABLE post_revisions ADD UNIQUE(generation_batch_id,generation_slot_id);
ALTER TABLE post_revisions ADD CHECK((generation_batch_id IS NULL AND generation_slot_id IS NULL) OR
 (generation_batch_id IS NOT NULL AND generation_slot_id IS NOT NULL AND generation_slot_id ~ '^[A-Za-z0-9_-]{1,64}$'));
DO $$ DECLARE c RECORD; BEGIN FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='post_revisions'::regclass
 AND contype='c' AND pg_get_constraintdef(oid) LIKE '%status%' LOOP
 EXECUTE format('ALTER TABLE post_revisions DROP CONSTRAINT %I',c.conname); END LOOP;END $$;
ALTER TABLE post_revisions ADD CHECK(status IN ('DRAFT','APPROVED','SUPERSEDED','LEGACY'));
ALTER TABLE post_revisions ADD CHECK(
 (status='LEGACY' AND revision_number=1 AND revised_from_id IS NULL AND authored_by IS NULL AND authored_at IS NULL
 AND edited_by IS NULL AND edited_at IS NULL AND approved_by IS NULL AND approved_at IS NULL AND media_prompt IS NULL
 AND video_script IS NULL AND legacy_status IS NOT NULL AND generation_batch_id IS NULL)
 OR(status IN ('DRAFT','APPROVED','SUPERSEDED') AND authored_by IS NOT NULL AND authored_at IS NOT NULL
 AND ((edited_by IS NOT NULL AND edited_at IS NOT NULL AND edited_at>=authored_at)
 OR (generation_batch_id IS NOT NULL AND edited_by IS NULL AND edited_at IS NULL))
 AND media_prompt IS NOT NULL AND btrim(media_prompt)<>'' AND (video_script IS NULL OR btrim(video_script)<>'')
 AND legacy_status IS NULL AND legacy_scheduled_at IS NULL
 AND ((status IN ('DRAFT','SUPERSEDED') AND approved_by IS NULL AND approved_at IS NULL)
 OR(status='APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND approved_at>=coalesce(edited_at,authored_at)))));

CREATE OR REPLACE FUNCTION guard_ai_operation() RETURNS trigger LANGUAGE plpgsql AS $$
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
  IF NEW.kind='CONTENT' THEN payload=payload->'content'->'identityContext';ELSIF NEW.kind='STRATEGY' THEN payload=payload->'strategy';ELSE payload=payload->'analysis';END IF;
  IF payload IS NULL OR payload='null'::jsonb OR (payload->>'brandVersion')::BIGINT IS DISTINCT FROM b.lock_version
   OR (payload->>'workspaceVersion')::BIGINT IS DISTINCT FROM w.lock_version OR payload->>'brandName' IS DISTINCT FROM b.name
   OR payload->>'industry' IS DISTINCT FROM w.industry THEN RAISE EXCEPTION 'Invalid generation capture' USING ERRCODE='23514';END IF;
 END IF;
 SELECT * INTO j FROM jobs WHERE id=NEW.job_id;
 IF j.type<>'AI_GENERATION' OR j.max_attempts<>3 THEN RAISE EXCEPTION 'Invalid generation job' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
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
   OR o.kind IN ('STRATEGY','CONTENT') THEN RAISE EXCEPTION 'Invalid attempt result binding' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION seal_ai_operation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o ai_generation_operations%ROWTYPE;j jobs%ROWTYPE;a ai_generation_attempts%ROWTYPE;sid UUID;payload JSONB;cnt INTEGER;
BEGIN
 SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.id;SELECT * INTO j FROM jobs WHERE id=o.job_id;
 IF (o.status='SUCCEEDED' AND j.status<>'SUCCEEDED') OR (o.status='RUNNING' AND j.status<>'RUNNING')
 OR (o.status='QUEUED' AND j.status<>'PENDING') OR (o.status='RETRY_WAIT' AND j.status<>'FAILED')
 OR (o.status IN ('FAILED','SUPERSEDED','EXPIRED','OUTCOME_UNKNOWN') AND j.status<>'DEAD')
 THEN RAISE EXCEPTION 'Generation job state mismatch' USING ERRCODE='23514';END IF;
 payload=o.snapshot::JSONB;
 IF j.payload_reference IS DISTINCT FROM 'generation:'||o.id::text OR ((payload->>'schemaVersion')::integer IS DISTINCT FROM 1 AND NOT(o.kind='STRATEGY' AND (payload->>'schemaVersion')::integer=2) AND NOT(o.kind='CONTENT' AND (payload->>'schemaVersion')::integer=3))
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
  IF o.kind='CONTENT' THEN
   IF NOT EXISTS(SELECT 1 FROM content_generation_batches WHERE id=o.result_id AND operation_id=o.id AND attempt_number=j.attempts AND output_checksum=encode(sha256(convert_to(o.result_json,'UTF8')),'hex')) THEN RAISE EXCEPTION 'Content receipt binding invalid' USING ERRCODE='23514';END IF;
  ELSIF o.kind='STRATEGY' THEN
   IF (payload->>'schemaVersion')::integer=2 AND NOT EXISTS(SELECT 1 FROM strategies WHERE id=o.result_id AND content_format_version=2 AND generation_content_format=2 AND generation_output_checksum IS NOT NULL) THEN RAISE EXCEPTION 'Full Playbook receipt required' USING ERRCODE='23514'; END IF;
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
CREATE OR REPLACE FUNCTION guard_post_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p content_posts%ROWTYPE; parent brands%ROWTYPE; workspace workspaces%ROWTYPE; source strategies%ROWTYPE;
 previous post_revisions%ROWTYPE; n INTEGER; actor UUID;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Post history cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (OLD.status<>'DRAFT' OR
   ROW(NEW.id,NEW.post_id,NEW.revision_number,NEW.revised_from_id,NEW.authored_by,NEW.authored_at,NEW.created_at,NEW.legacy_status,NEW.legacy_scheduled_at,NEW.generation_batch_id,NEW.generation_slot_id)
   IS DISTINCT FROM ROW(OLD.id,OLD.post_id,OLD.revision_number,OLD.revised_from_id,OLD.authored_by,OLD.authored_at,OLD.created_at,OLD.legacy_status,OLD.legacy_scheduled_at,OLD.generation_batch_id,OLD.generation_slot_id)) THEN
   RAISE EXCEPTION 'Post history immutable' USING ERRCODE='23514'; END IF;
 actor=CASE WHEN NEW.status='APPROVED' THEN NEW.approved_by ELSE coalesce(NEW.edited_by,NEW.authored_by) END;
 IF NEW.status='SUPERSEDED' THEN SELECT o.actor_id INTO actor FROM ai_generation_operations o JOIN jobs j ON j.id=o.job_id JOIN content_generation_batches g ON g.operation_id=o.id AND g.attempt_number=j.attempts WHERE g.xmin::text::bigint=mod(txid_current(),4294967296) AND o.kind='CONTENT' AND o.status='RUNNING' AND j.status='RUNNING' AND o.snapshot::jsonb->'content'->'request'->>'revisionId'=NEW.id::text;END IF;
 PERFORM 1 FROM cadence_users WHERE id=actor FOR UPDATE;
 SELECT * INTO p FROM content_posts WHERE id=NEW.post_id;
 SELECT * INTO parent FROM brands WHERE id=p.brand_id;
 SELECT * INTO workspace FROM workspaces WHERE id=parent.workspace_id FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=p.brand_id FOR UPDATE;
 SELECT * INTO source FROM strategies WHERE id=p.strategy_id FOR UPDATE;
 PERFORM 1 FROM content_posts WHERE id=p.id FOR UPDATE;
 PERFORM post_require_actor(actor,workspace.id);
 IF p.legacy OR NEW.status='LEGACY' OR parent.status<>'ACTIVE' OR workspace.status<>'ACTIVE'
   OR source.status<>'APPROVED' OR source.content_format_version<>2 OR source.lock_version<>p.source_lock_version
   OR source.version<>p.source_revision OR source.approved_by IS DISTINCT FROM p.source_approved_by
   OR source.approved_at IS DISTINCT FROM p.source_approved_at THEN
   RAISE EXCEPTION 'Post lifecycle unavailable' USING ERRCODE='23514'; END IF;
 IF (p.platform='INSTAGRAM' AND (NEW.format NOT IN ('IMAGE','CAROUSEL','REEL','STORY') OR length(NEW.caption)>2200))
   OR (p.platform='TIKTOK' AND (NEW.format NOT IN ('VIDEO','CAROUSEL') OR length(NEW.caption)>4000))
   OR (NEW.video_script IS NOT NULL AND NEW.format NOT IN ('VIDEO','REEL')) THEN
   RAISE EXCEPTION 'Invalid platform content' USING ERRCODE='23514'; END IF;
 SELECT coalesce(max(revision_number),0) INTO n FROM post_revisions WHERE post_id=p.id;
 IF TG_OP='INSERT' THEN
   PERFORM post_require_actor(NEW.authored_by,workspace.id);
   IF NEW.status<>'DRAFT' OR NEW.revision_number<>n+1 OR (NEW.generation_batch_id IS NULL AND (NEW.authored_by IS DISTINCT FROM NEW.edited_by OR NEW.authored_at IS DISTINCT FROM NEW.edited_at)) OR (NEW.generation_batch_id IS NOT NULL AND (NEW.edited_by IS NOT NULL OR NEW.edited_at IS NOT NULL)) THEN RAISE EXCEPTION 'Invalid new Post revision' USING ERRCODE='23514'; END IF;
   IF n=0 THEN
     IF NEW.revised_from_id IS NOT NULL THEN RAISE EXCEPTION 'Invalid root' USING ERRCODE='23514'; END IF;
   ELSE
     SELECT * INTO previous FROM post_revisions WHERE id=NEW.revised_from_id;
     IF previous.id IS NULL OR previous.post_id<>p.id OR previous.revision_number<>n OR (previous.status<>'APPROVED' AND NOT(NEW.generation_batch_id IS NOT NULL AND previous.status='SUPERSEDED')) THEN
       RAISE EXCEPTION 'Invalid Post lineage' USING ERRCODE='23514'; END IF;
   END IF;
 ELSE
   IF NEW.revision_number<>n OR NEW.lock_version<>OLD.lock_version+1 THEN
     RAISE EXCEPTION 'Stale Post revision' USING ERRCODE='23514'; END IF;
   IF NEW.status='DRAFT' AND (NEW.edited_at IS NOT DISTINCT FROM OLD.edited_at OR NEW.edited_at>clock_timestamp()) THEN
     RAISE EXCEPTION 'Post edit requires fresh attribution' USING ERRCODE='23514'; END IF;
   IF NEW.status='SUPERSEDED' THEN
     IF ROW(NEW.title,NEW.caption,NEW.format,NEW.media_prompt,NEW.video_script,NEW.edited_by,NEW.edited_at) IS DISTINCT FROM ROW(OLD.title,OLD.caption,OLD.format,OLD.media_prompt,OLD.video_script,OLD.edited_by,OLD.edited_at) OR NOT EXISTS(SELECT 1 FROM ai_generation_operations o JOIN jobs j ON j.id=o.job_id JOIN ai_generation_attempts a ON a.operation_id=o.id AND a.attempt_number=j.attempts JOIN content_generation_batches g ON g.operation_id=o.id AND g.attempt_number=a.attempt_number WHERE g.xmin::text::bigint=mod(txid_current(),4294967296) AND o.kind='CONTENT' AND o.status='RUNNING' AND j.status='RUNNING' AND a.worker_id=j.lease_owner AND a.dispatched_at IS NOT NULL AND clock_timestamp()<a.lease_expires_at AND clock_timestamp()<o.deadline AND o.snapshot::jsonb->'content'->'request'->>'revisionId'=OLD.id::text AND (o.snapshot::jsonb->'content'->'request'->>'revisionVersion')::bigint=OLD.lock_version) THEN RAISE EXCEPTION 'Supersession requires current regeneration' USING ERRCODE='23514';END IF;
   END IF;
   IF NEW.status='APPROVED' AND ROW(NEW.title,NEW.caption,NEW.format,NEW.media_prompt,NEW.video_script,NEW.edited_by,NEW.edited_at)
     IS DISTINCT FROM ROW(OLD.title,OLD.caption,OLD.format,OLD.media_prompt,OLD.video_script,OLD.edited_by,OLD.edited_at) THEN
     RAISE EXCEPTION 'Approval cannot change content' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;

-- Current eligibility is checked independently of Java at capture, dispatch and publication.
CREATE FUNCTION validate_content_source(o ai_generation_operations) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE c JSONB; i JSONB; s strategies%ROWTYPE; origin strategies%ROWTYPE; ident brand_identities%ROWTYPE;
 b brands%ROWTYPE; w workspaces%ROWTYPE; analysis UUID; n INTEGER; r post_revisions%ROWTYPE; p content_posts%ROWTYPE;
BEGIN
 c=o.snapshot::jsonb->'content';i=o.snapshot::jsonb->'identity';
 PERFORM 1 FROM cadence_users WHERE id=o.actor_id FOR UPDATE;
 SELECT * INTO w FROM workspaces WHERE id=o.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=o.brand_id FOR UPDATE;
 PERFORM post_require_actor(o.actor_id,w.id);
 IF b.workspace_id IS DISTINCT FROM w.id OR b.status<>'ACTIVE' OR w.status<>'ACTIVE'
 OR (c->'identityContext'->>'brandVersion')::bigint IS DISTINCT FROM b.lock_version
 OR (c->'identityContext'->>'workspaceVersion')::bigint IS DISTINCT FROM w.lock_version
 OR (i->>'requestedBy')::uuid IS DISTINCT FROM o.actor_id
 OR (o.snapshot::jsonb->>'schemaVersion')::integer IS DISTINCT FROM 3 THEN RAISE EXCEPTION 'Content scope changed' USING ERRCODE='23514';END IF;
 SELECT * INTO ident FROM brand_identities WHERE id=(i->>'identityId')::uuid FOR UPDATE;
 IF ident.id IS NULL OR ident.brand_id<>b.id OR ident.status<>'APPROVED'
 OR ident.version IS DISTINCT FROM (i->>'version')::integer OR ident.lock_version IS DISTINCT FROM (i->>'lockVersion')::bigint
 OR ident.approved_by IS DISTINCT FROM (i->>'approvedBy')::uuid OR ident.approved_at IS DISTINCT FROM (i->>'approvedAt')::timestamptz
 OR strategy_identity_checksum(ident.id) IS DISTINCT FROM i->>'checksum'
 OR EXISTS(SELECT 1 FROM brand_identities WHERE brand_id=b.id AND version>ident.version)
 THEN RAISE EXCEPTION 'Content Identity changed' USING ERRCODE='23514';END IF;
 analysis=resolve_identity_analysis_origin(ident.analysis_attempt_id,ident.revised_from_id,b.id,ident.version);
 IF analysis IS DISTINCT FROM (i->>'analysisId')::uuid THEN RAISE EXCEPTION 'Content analysis lineage changed' USING ERRCODE='23514';END IF;
 IF EXISTS(SELECT 1 FROM brand_analysis_attempts WHERE id=analysis AND requested_by IS NOT NULL)
 THEN PERFORM validate_identity_evidence_sources(analysis,b.id);END IF;
 SELECT * INTO s FROM strategies WHERE id=(c->>'strategyId')::uuid FOR UPDATE;
 IF s.id IS NULL OR s.brand_id<>b.id OR s.status<>'APPROVED' OR s.content_format_version<>2
 OR s.version IS DISTINCT FROM (c->>'strategyRevision')::integer OR s.lock_version IS DISTINCT FROM (c->>'strategyLockVersion')::bigint
 OR s.approved_by IS DISTINCT FROM (c->>'strategyApprovedBy')::uuid OR s.approved_at IS DISTINCT FROM (c->>'strategyApprovedAt')::timestamptz
 OR s.id IS DISTINCT FROM (c->'request'->>'strategyId')::uuid OR s.lock_version IS DISTINCT FROM (c->'request'->>'strategyVersion')::bigint
 THEN RAISE EXCEPTION 'Content Strategy changed' USING ERRCODE='23514';END IF;
 origin=s;n=0;
 WHILE origin.generation_identity_id IS NULL LOOP
  n=n+1;IF n>1000 OR origin.revised_from_id IS NULL THEN RAISE EXCEPTION 'Missing Strategy lineage' USING ERRCODE='23514';END IF;
  SELECT * INTO origin FROM strategies WHERE id=origin.revised_from_id AND brand_id=b.id AND version<origin.version;
  IF NOT FOUND THEN RAISE EXCEPTION 'Invalid Strategy lineage' USING ERRCODE='23514';END IF;
 END LOOP;
 IF origin.id IS DISTINCT FROM (c->>'lineageOrigin')::uuid OR origin.generation_identity_id<>ident.id
 OR origin.generation_identity_version<>ident.version OR origin.generation_identity_lock_version<>ident.lock_version
 OR origin.generation_identity_checksum IS DISTINCT FROM i->>'checksum'
 THEN RAISE EXCEPTION 'Content inherited Identity mismatch' USING ERRCODE='23514';END IF;
 IF jsonb_typeof(c->'request'->'slotIds') IS DISTINCT FROM 'array' OR jsonb_array_length(c->'request'->'slotIds') NOT BETWEEN 1 AND 84
 OR EXISTS(SELECT 1 FROM jsonb_array_elements_text(c->'request'->'slotIds') v WHERE NOT EXISTS(SELECT 1 FROM strategy_playbook_slots WHERE strategy_id=s.id AND slot_id=v))
 OR (SELECT count(DISTINCT v) FROM jsonb_array_elements_text(c->'request'->'slotIds') v)<>jsonb_array_length(c->'request'->'slotIds')
 THEN RAISE EXCEPTION 'Invalid requested slots' USING ERRCODE='23514';END IF;
 IF c->'request'->>'postId' IS NOT NULL THEN
  SELECT * INTO p FROM content_posts WHERE id=(c->'request'->>'postId')::uuid FOR UPDATE;
  SELECT * INTO r FROM post_revisions WHERE id=(c->'request'->>'revisionId')::uuid;
  IF p.brand_id IS DISTINCT FROM b.id OR p.strategy_id<>s.id OR r.post_id IS DISTINCT FROM p.id
   OR r.lock_version IS DISTINCT FROM (c->'request'->>'revisionVersion')::bigint
   OR r.status NOT IN ('DRAFT','APPROVED') OR EXISTS(SELECT 1 FROM post_revisions WHERE post_id=p.id AND revision_number>r.revision_number)
   THEN RAISE EXCEPTION 'Content regeneration changed' USING ERRCODE='23514';END IF;
 END IF;
END $$;
CREATE FUNCTION guard_content_capture() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.kind='CONTENT' AND (TG_OP='INSERT') THEN PERFORM validate_content_source(NEW);END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER content_capture_guard BEFORE INSERT ON ai_generation_operations FOR EACH ROW EXECUTE FUNCTION guard_content_capture();
CREATE FUNCTION guard_content_dispatch() RETURNS TRIGGER LANGUAGE plpgsql AS $$ DECLARE o ai_generation_operations%ROWTYPE; BEGIN
 SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.operation_id;
 IF o.kind='CONTENT' AND NEW.dispatched_at IS NOT NULL AND NEW.finished_at IS NULL THEN PERFORM validate_content_source(o);END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER content_dispatch_guard BEFORE INSERT OR UPDATE ON ai_generation_attempts FOR EACH ROW EXECUTE FUNCTION guard_content_dispatch();
CREATE FUNCTION guard_content_batch() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE o ai_generation_operations%ROWTYPE;j jobs%ROWTYPE;a ai_generation_attempts%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Content receipt immutable' USING ERRCODE='23514';END IF;
 SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.operation_id;
 PERFORM validate_content_source(o);
 SELECT * INTO j FROM jobs WHERE id=o.job_id;SELECT * INTO a FROM ai_generation_attempts WHERE operation_id=o.id AND attempt_number=NEW.attempt_number;
 IF o.kind<>'CONTENT' OR o.status<>'RUNNING' OR j.status<>'RUNNING' OR j.attempts<>NEW.attempt_number
 OR a.dispatched_at IS NULL OR a.finished_at IS NOT NULL OR a.worker_id IS DISTINCT FROM j.lease_owner OR clock_timestamp()>=a.lease_expires_at OR clock_timestamp()>=o.deadline
 THEN RAISE EXCEPTION 'Stale Content publication' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER content_batch_guard BEFORE INSERT OR UPDATE OR DELETE ON content_generation_batches FOR EACH ROW EXECUTE FUNCTION guard_content_batch();
CREATE FUNCTION seal_content_batch() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE o ai_generation_operations%ROWTYPE; c JSONB; output JSONB;n INTEGER;item JSONB;r RECORD;expected JSONB;field TEXT;
BEGIN
 SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.operation_id;c=o.snapshot::jsonb->'content';output=o.result_json::jsonb;
 IF o.kind<>'CONTENT' OR o.status<>'SUCCEEDED' OR o.result_id IS DISTINCT FROM NEW.id
 OR NEW.output_checksum IS DISTINCT FROM encode(sha256(convert_to(o.result_json,'UTF8')),'hex') OR (output->>'schemaVersion')::integer IS DISTINCT FROM 1
 OR NOT(output->'warnings' @> '["HUMAN_REVIEW_REQUIRED"]'::jsonb)
 THEN RAISE EXCEPTION 'Content receipt incomplete' USING ERRCODE='23514';END IF;
 SELECT count(*) INTO n FROM post_revisions WHERE generation_batch_id=NEW.id;
 IF n<>jsonb_array_length(c->'request'->'slotIds') OR n<>jsonb_array_length(output->'items')
 THEN RAISE EXCEPTION 'Content batch incomplete' USING ERRCODE='23514';END IF;
 FOR r IN SELECT v.*,p.brand_id,p.strategy_id,p.platform,p.outline_slot_id FROM post_revisions v JOIN content_posts p ON p.id=v.post_id WHERE generation_batch_id=NEW.id LOOP
  SELECT value INTO item FROM jsonb_array_elements(output->'items') WHERE value->>'slotId'=r.generation_slot_id;
  IF item IS NULL OR r.status<>'DRAFT' OR r.authored_by<>o.actor_id OR r.edited_by IS NOT NULL
   OR r.brand_id<>o.brand_id OR r.strategy_id<>(c->>'strategyId')::uuid OR item->>'channel' IS DISTINCT FROM r.platform
   OR NOT(c->'request'->'slotIds' @> to_jsonb(r.generation_slot_id))
   OR NOT EXISTS(SELECT 1 FROM strategy_playbook_slots WHERE strategy_id=r.strategy_id AND slot_id=r.generation_slot_id AND channel=r.platform)
  THEN RAISE EXCEPTION 'Content result binding invalid' USING ERRCODE='23514';END IF;
  IF c->'request'->>'postId' IS NULL THEN
   IF r.revision_number<>1 OR r.outline_slot_id IS DISTINCT FROM r.generation_slot_id THEN RAISE EXCEPTION 'Invalid generated Post' USING ERRCODE='23514';END IF;
  ELSE
   IF r.post_id IS DISTINCT FROM (c->'request'->>'postId')::uuid OR r.revised_from_id IS DISTINCT FROM (c->'request'->>'revisionId')::uuid THEN RAISE EXCEPTION 'Invalid regenerated Post' USING ERRCODE='23514';END IF;
  END IF;
  field=c->'request'->>'field';expected=item->'content';
  IF c->'request'->>'postId' IS NOT NULL AND field<>'ALL' THEN
   expected=c->'command'->'baseline';
   expected=jsonb_set(expected,ARRAY[CASE field WHEN 'TITLE' THEN 'title' WHEN 'CAPTION' THEN 'caption' WHEN 'HASHTAGS' THEN 'hashtags' WHEN 'FORMAT' THEN 'format' WHEN 'MEDIA_PROMPT' THEN 'mediaPrompt' WHEN 'VIDEO_SCRIPT' THEN 'videoScript' ELSE 'invalid' END],item->'content'->(CASE field WHEN 'TITLE' THEN 'title' WHEN 'CAPTION' THEN 'caption' WHEN 'HASHTAGS' THEN 'hashtags' WHEN 'FORMAT' THEN 'format' WHEN 'MEDIA_PROMPT' THEN 'mediaPrompt' WHEN 'VIDEO_SCRIPT' THEN 'videoScript' ELSE 'invalid' END));
  END IF;
  IF ROW(r.title,r.caption,r.format,r.media_prompt,r.video_script) IS DISTINCT FROM ROW(expected->>'title',expected->>'caption',expected->>'format',expected->>'mediaPrompt',expected->>'videoScript')
   OR coalesce((SELECT jsonb_agg(hashtag ORDER BY hashtag_order) FROM post_revision_hashtags WHERE revision_id=r.id),'[]'::jsonb) IS DISTINCT FROM expected->'hashtags'
  THEN RAISE EXCEPTION 'Content output differs from receipt' USING ERRCODE='23514';END IF;
 END LOOP;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER content_batch_seal AFTER INSERT ON content_generation_batches DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_content_batch();
-- A generated revision can be inserted only in the receipt's publication transaction.
CREATE FUNCTION guard_generated_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='INSERT' AND NEW.generation_batch_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM content_generation_batches WHERE id=NEW.generation_batch_id AND xmin::text::bigint=mod(txid_current(),4294967296))
 THEN RAISE EXCEPTION 'Generated revision requires current receipt' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER generated_revision_guard BEFORE INSERT ON post_revisions FOR EACH ROW EXECUTE FUNCTION guard_generated_revision();
-- DRAFT supersession cannot be committed without the corresponding new generated revision.
CREATE FUNCTION seal_post_supersession() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.status='SUPERSEDED' AND NOT EXISTS(SELECT 1 FROM post_revisions WHERE revised_from_id=NEW.id AND generation_batch_id IS NOT NULL AND revision_number=NEW.revision_number+1)
 THEN RAISE EXCEPTION 'Incomplete Post supersession' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER post_supersession_seal AFTER UPDATE ON post_revisions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_post_supersession();

CREATE FUNCTION guard_post_outline_binding() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.outline_slot_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM strategy_playbook_slots WHERE strategy_id=NEW.strategy_id AND slot_id=NEW.outline_slot_id AND channel=NEW.platform)
 THEN RAISE EXCEPTION 'Post outline platform mismatch' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER post_outline_binding_guard BEFORE INSERT ON content_posts FOR EACH ROW EXECUTE FUNCTION guard_post_outline_binding();
