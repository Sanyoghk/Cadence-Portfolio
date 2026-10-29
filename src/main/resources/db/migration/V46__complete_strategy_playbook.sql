-- v1 remains explicitly partial; no invented output, human audit or defaults for missing fields.
ALTER TABLE strategies ADD COLUMN content_format_version INTEGER NOT NULL DEFAULT 1 CHECK(content_format_version IN(1,2)),
 ADD COLUMN whitespace_status VARCHAR(30), ADD COLUMN edited_by UUID REFERENCES cadence_users(id), ADD COLUMN edited_at TIMESTAMPTZ,
 ADD COLUMN generation_content_format INTEGER, ADD COLUMN generation_output_checksum VARCHAR(64),
 ADD CONSTRAINT playbook_format CHECK((content_format_version=1 AND whitespace_status IS NULL) OR (content_format_version=2 AND whitespace_status IS NOT NULL AND whitespace_status IN('HYPOTHESES','INSUFFICIENT_EVIDENCE'))),
 ADD CONSTRAINT playbook_manual_attribution CHECK(content_format_version=1 OR generation_id IS NOT NULL OR edited_by IS NOT NULL),
 ADD CONSTRAINT playbook_edit_audit CHECK((edited_by IS NULL AND edited_at IS NULL) OR (edited_by IS NOT NULL AND edited_at IS NOT NULL)),
 ADD CONSTRAINT playbook_output_receipt CHECK((generation_content_format IS NULL AND generation_output_checksum IS NULL) OR (generation_id IS NOT NULL AND generation_content_format=2 AND generation_output_checksum IS NOT NULL AND generation_output_checksum ~ '^[0-9a-f]{64}$'));

CREATE TABLE strategy_playbook_pillar_ids (strategy_id UUID NOT NULL REFERENCES strategies(id), entry_order INTEGER NOT NULL CHECK(entry_order>=0 AND entry_order<84),
local_id VARCHAR(64) NOT NULL CHECK(local_id ~ '^[A-Za-z0-9_-]{1,64}$'),
 PRIMARY KEY(strategy_id,entry_order), UNIQUE(strategy_id,local_id)
);

CREATE TABLE strategy_playbook_audience (strategy_id UUID NOT NULL REFERENCES strategies(id), entry_order INTEGER NOT NULL CHECK(entry_order>=0 AND entry_order<84),
local_id VARCHAR(64) NOT NULL CHECK(local_id ~ '^[A-Za-z0-9_-]{1,64}$'),
segment VARCHAR(200) NOT NULL CHECK(btrim(segment)<>'' AND segment=btrim(segment) AND segment !~ '[[:cntrl:]]'),
needs VARCHAR(1000) NOT NULL CHECK(btrim(needs)<>'' AND needs=btrim(needs) AND needs !~ '[[:cntrl:]]'),
rationale VARCHAR(1000) NOT NULL CHECK(btrim(rationale)<>'' AND rationale=btrim(rationale) AND rationale !~ '[[:cntrl:]]'),
 PRIMARY KEY(strategy_id,entry_order), UNIQUE(strategy_id,local_id)
);

CREATE TABLE strategy_playbook_channels (strategy_id UUID NOT NULL REFERENCES strategies(id), entry_order INTEGER NOT NULL CHECK(entry_order>=0 AND entry_order<84),
channel VARCHAR(20) NOT NULL CHECK(channel IN('INSTAGRAM','TIKTOK')),
share_percent INTEGER NOT NULL CHECK(share_percent BETWEEN 1 AND 100),
 PRIMARY KEY(strategy_id,entry_order), UNIQUE(strategy_id,channel)
);

CREATE TABLE strategy_playbook_goals (strategy_id UUID NOT NULL REFERENCES strategies(id), entry_order INTEGER NOT NULL CHECK(entry_order>=0 AND entry_order<84),
channel VARCHAR(20) NOT NULL CHECK(channel IN('INSTAGRAM','TIKTOK')),
goal_order INTEGER NOT NULL CHECK(goal_order BETWEEN 0 AND 2),
objective VARCHAR(500) NOT NULL CHECK(btrim(objective)<>'' AND objective=btrim(objective) AND objective !~ '[[:cntrl:]]'),
metric_hint VARCHAR(100) CHECK(btrim(metric_hint)<>'' AND metric_hint=btrim(metric_hint) AND metric_hint !~ '[[:cntrl:]]'),
rationale VARCHAR(1000) NOT NULL CHECK(btrim(rationale)<>'' AND rationale=btrim(rationale) AND rationale !~ '[[:cntrl:]]'),
 PRIMARY KEY(strategy_id,entry_order), UNIQUE(strategy_id,channel,goal_order)
);

CREATE TABLE strategy_playbook_tones (strategy_id UUID NOT NULL REFERENCES strategies(id), entry_order INTEGER NOT NULL CHECK(entry_order>=0 AND entry_order<84),
local_id VARCHAR(64) NOT NULL CHECK(local_id ~ '^[A-Za-z0-9_-]{1,64}$'),
left_label VARCHAR(100) NOT NULL CHECK(btrim(left_label)<>'' AND left_label=btrim(left_label) AND left_label !~ '[[:cntrl:]]'),
right_label VARCHAR(100) NOT NULL CHECK(btrim(right_label)<>'' AND right_label=btrim(right_label) AND right_label !~ '[[:cntrl:]]'),
position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 100),
rationale VARCHAR(1000) NOT NULL CHECK(btrim(rationale)<>'' AND rationale=btrim(rationale) AND rationale !~ '[[:cntrl:]]'),
 PRIMARY KEY(strategy_id,entry_order), UNIQUE(strategy_id,local_id)
);

CREATE TABLE strategy_playbook_hypotheses (strategy_id UUID NOT NULL REFERENCES strategies(id), entry_order INTEGER NOT NULL CHECK(entry_order>=0 AND entry_order<84),
statement VARCHAR(1000) NOT NULL CHECK(btrim(statement)<>'' AND statement=btrim(statement) AND statement !~ '[[:cntrl:]]'),
rationale VARCHAR(1000) NOT NULL CHECK(btrim(rationale)<>'' AND rationale=btrim(rationale) AND rationale !~ '[[:cntrl:]]'),
 PRIMARY KEY(strategy_id,entry_order)
);

CREATE TABLE strategy_playbook_slots (strategy_id UUID NOT NULL REFERENCES strategies(id), entry_order INTEGER NOT NULL CHECK(entry_order>=0 AND entry_order<84),
week_number INTEGER NOT NULL CHECK(week_number BETWEEN 1 AND 4),
slot_id VARCHAR(64) NOT NULL CHECK(slot_id ~ '^[A-Za-z0-9_-]{1,64}$'),
channel VARCHAR(20) NOT NULL CHECK(channel IN('INSTAGRAM','TIKTOK')),
pillar_id VARCHAR(64) NOT NULL CHECK(pillar_id ~ '^[A-Za-z0-9_-]{1,64}$'),
audience_id VARCHAR(64) NOT NULL CHECK(audience_id ~ '^[A-Za-z0-9_-]{1,64}$'),
topic VARCHAR(500) NOT NULL CHECK(btrim(topic)<>'' AND topic=btrim(topic) AND topic !~ '[[:cntrl:]]'),
intent VARCHAR(500) NOT NULL CHECK(btrim(intent)<>'' AND intent=btrim(intent) AND intent !~ '[[:cntrl:]]'),
day_offset INTEGER NOT NULL CHECK(day_offset BETWEEN 0 AND 6),
 PRIMARY KEY(strategy_id,entry_order), UNIQUE(strategy_id,slot_id)
);

ALTER TABLE strategy_playbook_goals ADD FOREIGN KEY(strategy_id,channel) REFERENCES strategy_playbook_channels(strategy_id,channel) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE strategy_playbook_slots ADD FOREIGN KEY(strategy_id,channel) REFERENCES strategy_playbook_channels(strategy_id,channel) DEFERRABLE INITIALLY DEFERRED,
 ADD FOREIGN KEY(strategy_id,pillar_id) REFERENCES strategy_playbook_pillar_ids(strategy_id,local_id) DEFERRABLE INITIALLY DEFERRED,
 ADD FOREIGN KEY(strategy_id,audience_id) REFERENCES strategy_playbook_audience(strategy_id,local_id) DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION guard_playbook_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE ws UUID;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF OLD.edited_by IS NOT NULL AND NEW.edited_by IS NULL THEN RAISE EXCEPTION 'Human attribution cannot be erased' USING ERRCODE='23514'; END IF;
  IF ROW(NEW.content_format_version,NEW.whitespace_status,NEW.posts_per_week,NEW.publishing_timezone) IS DISTINCT FROM ROW(OLD.content_format_version,OLD.whitespace_status,OLD.posts_per_week,OLD.publishing_timezone)
   AND NEW.content_format_version=2 AND (NEW.edited_by IS NULL OR NEW.edited_at IS NULL OR NEW.edited_at IS NOT DISTINCT FROM OLD.edited_at) THEN
   RAISE EXCEPTION 'Playbook edit requires fresh human attribution' USING ERRCODE='23514'; END IF;
  IF ROW(NEW.generation_content_format,NEW.generation_output_checksum) IS DISTINCT FROM ROW(OLD.generation_content_format,OLD.generation_output_checksum)
   OR (OLD.content_format_version=2 AND NEW.content_format_version<>2)
   OR (OLD.status<>'DRAFT' AND ROW(NEW.content_format_version,NEW.whitespace_status,NEW.edited_by,NEW.edited_at) IS DISTINCT FROM ROW(OLD.content_format_version,OLD.whitespace_status,OLD.edited_by,OLD.edited_at)) THEN
   RAISE EXCEPTION 'Immutable Playbook history' USING ERRCODE='23514'; END IF;
 END IF;
 IF NEW.edited_by IS NOT NULL AND (TG_OP='INSERT' OR ROW(NEW.edited_by,NEW.edited_at) IS DISTINCT FROM ROW(OLD.edited_by,OLD.edited_at)) THEN
  SELECT b.workspace_id INTO ws FROM brands b JOIN workspaces w ON w.id=b.workspace_id WHERE b.id=NEW.brand_id AND b.status='ACTIVE' AND w.status='ACTIVE';
  IF NEW.status<>'DRAFT' OR ws IS NULL OR (TG_OP='UPDATE' AND NEW.edited_at<NEW.created_at) OR NEW.edited_at>clock_timestamp() OR NOT EXISTS(SELECT 1 FROM cadence_users u JOIN workspace_memberships m ON m.user_id=u.id WHERE u.id=NEW.edited_by AND u.active AND u.role='OWNER' AND m.workspace_id=ws) THEN
   RAISE EXCEPTION 'Invalid human edit attribution' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER playbook_revision_guard BEFORE INSERT OR UPDATE ON strategies FOR EACH ROW EXECUTE FUNCTION guard_playbook_revision();

CREATE FUNCTION validate_playbook_content() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_id UUID; parent strategies%ROWTYPE; n INTEGER; total INTEGER; payload_size BIGINT;
BEGIN
 IF TG_TABLE_NAME='strategies' THEN parent_id=NEW.id; ELSE parent_id=CASE WHEN TG_OP='DELETE' THEN OLD.strategy_id ELSE NEW.strategy_id END; END IF;
 SELECT * INTO parent FROM strategies WHERE id=parent_id;
 IF parent.id IS NULL THEN RETURN NULL; END IF;
 IF parent.content_format_version=1 THEN
  IF EXISTS(SELECT 1 FROM strategy_playbook_pillar_ids WHERE strategy_id=parent_id) OR EXISTS(SELECT 1 FROM strategy_playbook_audience WHERE strategy_id=parent_id) OR EXISTS(SELECT 1 FROM strategy_playbook_channels WHERE strategy_id=parent_id) OR EXISTS(SELECT 1 FROM strategy_playbook_goals WHERE strategy_id=parent_id) OR EXISTS(SELECT 1 FROM strategy_playbook_tones WHERE strategy_id=parent_id) OR EXISTS(SELECT 1 FROM strategy_playbook_hypotheses WHERE strategy_id=parent_id) OR EXISTS(SELECT 1 FROM strategy_playbook_slots WHERE strategy_id=parent_id) THEN RAISE EXCEPTION 'Legacy Playbook cannot contain v2 values' USING ERRCODE='23514'; END IF;
  RETURN NULL;
 END IF;

 IF EXISTS(SELECT 1 FROM strategy_playbook_pillar_ids WHERE strategy_id=parent_id HAVING min(entry_order)<>0 OR max(entry_order)<>count(*)-1) OR EXISTS(SELECT 1 FROM strategy_playbook_audience WHERE strategy_id=parent_id HAVING min(entry_order)<>0 OR max(entry_order)<>count(*)-1) OR EXISTS(SELECT 1 FROM strategy_playbook_channels WHERE strategy_id=parent_id HAVING min(entry_order)<>0 OR max(entry_order)<>count(*)-1) OR EXISTS(SELECT 1 FROM strategy_playbook_goals WHERE strategy_id=parent_id HAVING min(entry_order)<>0 OR max(entry_order)<>count(*)-1) OR EXISTS(SELECT 1 FROM strategy_playbook_tones WHERE strategy_id=parent_id HAVING min(entry_order)<>0 OR max(entry_order)<>count(*)-1) OR EXISTS(SELECT 1 FROM strategy_playbook_hypotheses WHERE strategy_id=parent_id HAVING min(entry_order)<>0 OR max(entry_order)<>count(*)-1) OR EXISTS(SELECT 1 FROM strategy_playbook_slots WHERE strategy_id=parent_id HAVING min(entry_order)<>0 OR max(entry_order)<>count(*)-1) THEN RAISE EXCEPTION 'Invalid Playbook collection order' USING ERRCODE='23514'; END IF;
 total=4*parent.posts_per_week;
 IF parent.posts_per_week IS NULL OR parent.posts_per_week NOT BETWEEN 1 AND 21
 OR parent.publishing_timezone IS NULL OR NOT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=parent.publishing_timezone)
 OR (SELECT coalesce(sum(pillar_weight),0) FROM strategy_content_pillars WHERE strategy_id=parent_id)<>100
 OR EXISTS(SELECT 1 FROM strategy_content_pillars WHERE strategy_id=parent_id AND (pillar_name IS NULL OR btrim(pillar_name)='' OR pillar_description IS NULL OR btrim(pillar_description)='' OR pillar_name ~ '[[:cntrl:]]' OR pillar_description ~ '[[:cntrl:]]' OR pillar_weight IS NULL OR pillar_weight NOT BETWEEN 0 AND 100))
 OR (SELECT count(distinct lower(pillar_name)) FROM strategy_content_pillars WHERE strategy_id=parent_id)<>(SELECT count(*) FROM strategy_content_pillars WHERE strategy_id=parent_id)
 OR (SELECT count(*) FROM strategy_best_publishing_times WHERE strategy_id=parent_id) NOT BETWEEN 1 AND 7
 OR (SELECT count(distinct publishing_time) FROM strategy_best_publishing_times WHERE strategy_id=parent_id)<>(SELECT count(*) FROM strategy_best_publishing_times WHERE strategy_id=parent_id)
 OR (SELECT count(*) FROM strategy_playbook_pillar_ids WHERE strategy_id=parent_id) NOT BETWEEN 1 AND 10
 OR (SELECT count(*) FROM strategy_playbook_pillar_ids WHERE strategy_id=parent_id)<>(SELECT count(*) FROM strategy_content_pillars WHERE strategy_id=parent_id)
 OR (SELECT count(*) FROM strategy_playbook_audience WHERE strategy_id=parent_id) NOT BETWEEN 1 AND 5
 OR (SELECT count(*) FROM strategy_playbook_channels WHERE strategy_id=parent_id) NOT BETWEEN 1 AND 2
 OR (SELECT coalesce(sum(share_percent),0) FROM strategy_playbook_channels WHERE strategy_id=parent_id)<>100
 OR (SELECT count(*) FROM strategy_playbook_tones WHERE strategy_id=parent_id) NOT BETWEEN 1 AND 5
 OR EXISTS(SELECT 1 FROM strategy_playbook_tones WHERE strategy_id=parent_id AND lower(left_label)=lower(right_label))
 OR (parent.whitespace_status='INSUFFICIENT_EVIDENCE' AND EXISTS(SELECT 1 FROM strategy_playbook_hypotheses WHERE strategy_id=parent_id))
 OR (parent.whitespace_status='HYPOTHESES' AND (SELECT count(*) FROM strategy_playbook_hypotheses WHERE strategy_id=parent_id) NOT BETWEEN 1 AND 5)
 OR EXISTS(SELECT 1 FROM strategy_playbook_channels c WHERE strategy_id=parent_id AND (SELECT count(*) FROM strategy_playbook_goals g WHERE g.strategy_id=c.strategy_id AND g.channel=c.channel) NOT BETWEEN 1 AND 3)
 OR (SELECT count(*) FROM strategy_playbook_slots WHERE strategy_id=parent_id)<>total
 OR EXISTS(SELECT 1 FROM generate_series(1,4) w WHERE (SELECT count(*) FROM strategy_playbook_slots WHERE strategy_id=parent_id AND week_number=w)<>parent.posts_per_week)
 THEN RAISE EXCEPTION 'Incomplete Playbook' USING ERRCODE='23514'; END IF;
 -- Largest remainder allocation; enum tie order INSTAGRAM before TIKTOK.
 IF EXISTS(WITH allocation AS (
 SELECT channel,total*share_percent/100 AS floor_count,
 row_number() OVER(ORDER BY total*share_percent%100 DESC,channel) AS rank,
 total-sum(total*share_percent/100) OVER() AS remaining
 FROM strategy_playbook_channels WHERE strategy_id=parent_id)
 SELECT 1 FROM allocation a WHERE (SELECT count(*) FROM strategy_playbook_slots s WHERE s.strategy_id=parent_id AND s.channel=a.channel)<>a.floor_count+CASE WHEN a.rank<=a.remaining THEN 1 ELSE 0 END)
 THEN RAISE EXCEPTION 'Invalid channel allocation' USING ERRCODE='23514'; END IF;
 payload_size=0;
 SELECT payload_size+coalesce(sum(octet_length((to_jsonb(v)-'strategy_id'-'entry_order')::text)),0) INTO payload_size FROM strategy_playbook_pillar_ids v WHERE strategy_id=parent_id;
 SELECT payload_size+coalesce(sum(octet_length((to_jsonb(v)-'strategy_id'-'entry_order')::text)),0) INTO payload_size FROM strategy_playbook_audience v WHERE strategy_id=parent_id;
 SELECT payload_size+coalesce(sum(octet_length((to_jsonb(v)-'strategy_id'-'entry_order')::text)),0) INTO payload_size FROM strategy_playbook_channels v WHERE strategy_id=parent_id;
 SELECT payload_size+coalesce(sum(octet_length((to_jsonb(v)-'strategy_id'-'entry_order')::text)),0) INTO payload_size FROM strategy_playbook_goals v WHERE strategy_id=parent_id;
 SELECT payload_size+coalesce(sum(octet_length((to_jsonb(v)-'strategy_id'-'entry_order')::text)),0) INTO payload_size FROM strategy_playbook_tones v WHERE strategy_id=parent_id;
 SELECT payload_size+coalesce(sum(octet_length((to_jsonb(v)-'strategy_id'-'entry_order')::text)),0) INTO payload_size FROM strategy_playbook_hypotheses v WHERE strategy_id=parent_id;
 SELECT payload_size+coalesce(sum(octet_length((to_jsonb(v)-'strategy_id'-'entry_order')::text)),0) INTO payload_size FROM strategy_playbook_slots v WHERE strategy_id=parent_id;
 -- Independent physical-row envelope includes repeated normalized keys; API canonical cap is 64 KiB.
 IF payload_size>131072 THEN RAISE EXCEPTION 'Playbook payload too large' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER strategy_playbook_pillar_ids_edit BEFORE INSERT OR UPDATE OR DELETE ON strategy_playbook_pillar_ids FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();
CREATE CONSTRAINT TRIGGER strategy_playbook_pillar_ids_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_playbook_pillar_ids DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE TRIGGER strategy_playbook_audience_edit BEFORE INSERT OR UPDATE OR DELETE ON strategy_playbook_audience FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();
CREATE CONSTRAINT TRIGGER strategy_playbook_audience_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_playbook_audience DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE TRIGGER strategy_playbook_channels_edit BEFORE INSERT OR UPDATE OR DELETE ON strategy_playbook_channels FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();
CREATE CONSTRAINT TRIGGER strategy_playbook_channels_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_playbook_channels DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE TRIGGER strategy_playbook_goals_edit BEFORE INSERT OR UPDATE OR DELETE ON strategy_playbook_goals FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();
CREATE CONSTRAINT TRIGGER strategy_playbook_goals_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_playbook_goals DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE TRIGGER strategy_playbook_tones_edit BEFORE INSERT OR UPDATE OR DELETE ON strategy_playbook_tones FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();
CREATE CONSTRAINT TRIGGER strategy_playbook_tones_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_playbook_tones DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE TRIGGER strategy_playbook_hypotheses_edit BEFORE INSERT OR UPDATE OR DELETE ON strategy_playbook_hypotheses FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();
CREATE CONSTRAINT TRIGGER strategy_playbook_hypotheses_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_playbook_hypotheses DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE TRIGGER strategy_playbook_slots_edit BEFORE INSERT OR UPDATE OR DELETE ON strategy_playbook_slots FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();
CREATE CONSTRAINT TRIGGER strategy_playbook_slots_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_playbook_slots DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE CONSTRAINT TRIGGER strategies_playbook_complete AFTER INSERT OR UPDATE ON strategies DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE CONSTRAINT TRIGGER strategy_content_pillars_playbook_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_content_pillars DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();
CREATE CONSTRAINT TRIGGER strategy_best_publishing_times_playbook_complete AFTER INSERT OR UPDATE OR DELETE ON strategy_best_publishing_times DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_playbook_content();

-- Preserve old schema1 terminal history; new Strategy capture is explicitly v2.
CREATE OR REPLACE FUNCTION seal_ai_operation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o ai_generation_operations%ROWTYPE;j jobs%ROWTYPE;a ai_generation_attempts%ROWTYPE;sid UUID;payload JSONB;cnt INTEGER;
BEGIN
 SELECT * INTO o FROM ai_generation_operations WHERE id=NEW.id;SELECT * INTO j FROM jobs WHERE id=o.job_id;
 IF (o.status='SUCCEEDED' AND j.status<>'SUCCEEDED') OR (o.status='RUNNING' AND j.status<>'RUNNING')
 OR (o.status='QUEUED' AND j.status<>'PENDING') OR (o.status='RETRY_WAIT' AND j.status<>'FAILED')
 OR (o.status IN ('FAILED','SUPERSEDED','EXPIRED','OUTCOME_UNKNOWN') AND j.status<>'DEAD')
 THEN RAISE EXCEPTION 'Generation job state mismatch' USING ERRCODE='23514';END IF;
 payload=o.snapshot::JSONB;
 IF j.payload_reference IS DISTINCT FROM 'generation:'||o.id::text OR ((payload->>'schemaVersion')::integer IS DISTINCT FROM 1 AND NOT(o.kind='STRATEGY' AND (payload->>'schemaVersion')::integer=2))
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

CREATE FUNCTION guard_playbook_capture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.kind='STRATEGY' AND (NEW.snapshot::jsonb->>'schemaVersion')::integer IS DISTINCT FROM 2 THEN RAISE EXCEPTION 'New Strategy capture requires v2' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER playbook_capture_guard BEFORE INSERT ON ai_generation_operations FOR EACH ROW EXECUTE FUNCTION guard_playbook_capture();

CREATE FUNCTION guard_playbook_edit_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p strategies%ROWTYPE;
BEGIN
 SELECT * INTO p FROM strategies WHERE id=OLD.strategy_id FOR UPDATE;
 IF p.content_format_version=2 AND (p.edited_by IS NULL OR p.edited_at IS NULL OR p.edited_at<transaction_timestamp()) THEN
  RAISE EXCEPTION 'Playbook value edit requires fresh human attribution' USING ERRCODE='23514'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER strategy_playbook_pillar_ids_audit BEFORE UPDATE OR DELETE ON strategy_playbook_pillar_ids FOR EACH ROW EXECUTE FUNCTION guard_playbook_edit_audit();
CREATE TRIGGER strategy_playbook_audience_audit BEFORE UPDATE OR DELETE ON strategy_playbook_audience FOR EACH ROW EXECUTE FUNCTION guard_playbook_edit_audit();
CREATE TRIGGER strategy_playbook_channels_audit BEFORE UPDATE OR DELETE ON strategy_playbook_channels FOR EACH ROW EXECUTE FUNCTION guard_playbook_edit_audit();
CREATE TRIGGER strategy_playbook_goals_audit BEFORE UPDATE OR DELETE ON strategy_playbook_goals FOR EACH ROW EXECUTE FUNCTION guard_playbook_edit_audit();
CREATE TRIGGER strategy_playbook_tones_audit BEFORE UPDATE OR DELETE ON strategy_playbook_tones FOR EACH ROW EXECUTE FUNCTION guard_playbook_edit_audit();
CREATE TRIGGER strategy_playbook_hypotheses_audit BEFORE UPDATE OR DELETE ON strategy_playbook_hypotheses FOR EACH ROW EXECUTE FUNCTION guard_playbook_edit_audit();
CREATE TRIGGER strategy_playbook_slots_audit BEFORE UPDATE OR DELETE ON strategy_playbook_slots FOR EACH ROW EXECUTE FUNCTION guard_playbook_edit_audit();
