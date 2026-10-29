-- Scheduling metadata never changes approved content or invents a legacy timezone/actor.
ALTER TABLE content_posts ADD CONSTRAINT content_posts_brand_identity_unique UNIQUE(brand_id,id);
CREATE TABLE post_schedule_events (
 id UUID PRIMARY KEY, created_at TIMESTAMPTZ(6) NOT NULL, updated_at TIMESTAMPTZ(6) NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 brand_id UUID NOT NULL REFERENCES brands(id), post_id UUID NOT NULL REFERENCES content_posts(id), revision_id UUID NOT NULL,
 event_number INTEGER NOT NULL CHECK(event_number BETWEEN 1 AND 1000),
 action VARCHAR(255) NOT NULL CHECK(action IN ('SCHEDULE','CANCEL')),
 revision_lock_version BIGINT NOT NULL CHECK(revision_lock_version>=0),
 content_checksum VARCHAR(64) NOT NULL CHECK(content_checksum ~ '^[0-9a-f]{64}$'),
 timezone VARCHAR(100) NOT NULL, local_time TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
 offset_seconds INTEGER NOT NULL CHECK(offset_seconds BETWEEN -64800 AND 64800),
 scheduled_at TIMESTAMPTZ(6) NOT NULL, rules_version VARCHAR(40) NOT NULL CHECK(btrim(rules_version)<>''),
 actor_id UUID NOT NULL REFERENCES cadence_users(id), occurred_at TIMESTAMPTZ(6) NOT NULL,
 FOREIGN KEY(brand_id,post_id) REFERENCES content_posts(brand_id,id),
 FOREIGN KEY(post_id,revision_id) REFERENCES post_revisions(post_id,id),
 CONSTRAINT post_schedule_version_unique UNIQUE(post_id,event_number),
 CHECK(date_trunc('minute',local_time)=local_time)
);
-- Composite FK binds the denormalized Brand for a tenant-leading calendar index.
CREATE INDEX post_schedule_time_idx ON post_schedule_events(brand_id,scheduled_at,id) WHERE action='SCHEDULE';

CREATE FUNCTION schedule_checksum_part(value TEXT) RETURNS TEXT LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE WHEN value IS NULL THEN '-1:' ELSE octet_length(value)::text||':'||value END
$$;
CREATE FUNCTION post_schedule_checksum(revision UUID) RETURNS TEXT LANGUAGE SQL STABLE AS $$
 SELECT encode(sha256(convert_to('post-schedule-content-v1:'||schedule_checksum_part(r.title)||
   schedule_checksum_part(r.caption)||schedule_checksum_part(r.format)||schedule_checksum_part(r.media_prompt)||
   schedule_checksum_part(r.video_script)||schedule_checksum_part((SELECT count(*)::text FROM post_revision_hashtags WHERE revision_id=r.id))||
   coalesce((SELECT string_agg(schedule_checksum_part(hashtag),'' ORDER BY hashtag_order)
     FROM post_revision_hashtags WHERE revision_id=r.id),''),'UTF8')),'hex')
 FROM post_revisions r WHERE r.id=revision
$$;

CREATE FUNCTION guard_post_schedule() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p content_posts%ROWTYPE; parent brands%ROWTYPE; workspace workspaces%ROWTYPE;
 source strategies%ROWTYPE; r post_revisions%ROWTYPE; prior post_schedule_events%ROWTYPE; n INTEGER;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Schedule history immutable' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM cadence_users WHERE id=NEW.actor_id FOR UPDATE;
 SELECT * INTO p FROM content_posts WHERE id=NEW.post_id;
 SELECT * INTO parent FROM brands WHERE id=p.brand_id;
 SELECT * INTO workspace FROM workspaces WHERE id=parent.workspace_id FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=p.brand_id FOR UPDATE;
 SELECT * INTO source FROM strategies WHERE id=p.strategy_id FOR UPDATE;
 PERFORM 1 FROM content_posts WHERE id=p.id FOR UPDATE;
 PERFORM post_require_actor(NEW.actor_id,workspace.id);
 IF parent.status IS DISTINCT FROM 'ACTIVE' OR workspace.status IS DISTINCT FROM 'ACTIVE' OR p.legacy
   OR NEW.occurred_at<transaction_timestamp() OR NEW.occurred_at>clock_timestamp() THEN
   RAISE EXCEPTION 'Schedule lifecycle unavailable' USING ERRCODE='23514'; END IF;
 SELECT * INTO prior FROM post_schedule_events WHERE post_id=p.id ORDER BY event_number DESC LIMIT 1;
 n=coalesce(prior.event_number,0);
 IF NEW.event_number<>n+1 OR (n>0 AND NEW.occurred_at<prior.occurred_at) THEN
   RAISE EXCEPTION 'Schedule version changed' USING ERRCODE='23514'; END IF;
 IF NEW.action='CANCEL' THEN
   IF n=0 OR prior.action<>'SCHEDULE' OR
     ROW(NEW.revision_id,NEW.revision_lock_version,NEW.content_checksum,NEW.timezone,NEW.local_time,
         NEW.offset_seconds,NEW.scheduled_at,NEW.rules_version)
     IS DISTINCT FROM ROW(prior.revision_id,prior.revision_lock_version,prior.content_checksum,prior.timezone,
         prior.local_time,prior.offset_seconds,prior.scheduled_at,prior.rules_version) THEN
     RAISE EXCEPTION 'Invalid schedule cancellation' USING ERRCODE='23514'; END IF;
 ELSE
   IF NEW.event_number=1000 THEN RAISE EXCEPTION 'Final schedule event reserved for cancellation' USING ERRCODE='23514'; END IF;
   SELECT * INTO r FROM post_revisions WHERE post_id=p.id ORDER BY revision_number DESC LIMIT 1;
   IF NEW.content_checksum IS DISTINCT FROM post_schedule_checksum(NEW.revision_id) THEN
     RAISE EXCEPTION 'Schedule content checksum invalid' USING ERRCODE='23514'; END IF;
   IF r.id IS DISTINCT FROM NEW.revision_id OR r.status IS DISTINCT FROM 'APPROVED' OR r.approved_by IS NULL
     OR r.lock_version<>NEW.revision_lock_version THEN
     RAISE EXCEPTION 'Schedule revision unavailable' USING ERRCODE='23514'; END IF;
   IF source.status IS DISTINCT FROM 'APPROVED' OR source.content_format_version<>2
     OR source.lock_version<>p.source_lock_version OR source.version<>p.source_revision
     OR source.approved_by IS DISTINCT FROM p.source_approved_by OR source.approved_at IS DISTINCT FROM p.source_approved_at THEN
     RAISE EXCEPTION 'Schedule source unavailable' USING ERRCODE='23514'; END IF;
   IF NEW.occurred_at<r.approved_at OR NEW.scheduled_at<=NEW.occurred_at
     OR NEW.scheduled_at>NEW.occurred_at+interval '31622400 seconds' THEN
     RAISE EXCEPTION 'Schedule deadline invalid' USING ERRCODE='23514'; END IF;
   IF NOT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=NEW.timezone)
     OR (NEW.scheduled_at AT TIME ZONE NEW.timezone) IS DISTINCT FROM NEW.local_time
     OR extract(epoch FROM (NEW.local_time-(NEW.scheduled_at AT TIME ZONE 'UTC')))<>NEW.offset_seconds THEN
     RAISE EXCEPTION 'Schedule timezone binding invalid' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER post_schedule_guard BEFORE INSERT OR UPDATE OR DELETE ON post_schedule_events
 FOR EACH ROW EXECUTE FUNCTION guard_post_schedule();

-- Deferred so an atomic batch can swap slots. Parent Brand lock serializes concurrent writers.
CREATE FUNCTION seal_post_schedule_slots() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE brand UUID;
BEGIN
 SELECT brand_id INTO brand FROM content_posts WHERE id=NEW.post_id;
 IF EXISTS(
   SELECT p.platform,e.scheduled_at FROM post_schedule_events e
   JOIN content_posts p ON p.id=e.post_id JOIN brands b ON b.id=p.brand_id
   JOIN workspaces w ON w.id=b.workspace_id JOIN strategies s ON s.id=p.strategy_id
   JOIN post_revisions r ON r.id=e.revision_id
   WHERE e.brand_id=brand AND b.status='ACTIVE' AND w.status='ACTIVE' AND e.action='SCHEDULE'
     AND s.status='APPROVED' AND s.lock_version=p.source_lock_version AND s.version=p.source_revision
     AND s.approved_by=p.source_approved_by AND s.approved_at=p.source_approved_at
     AND r.status='APPROVED' AND r.lock_version=e.revision_lock_version
     AND NOT EXISTS(SELECT 1 FROM post_schedule_events n WHERE n.post_id=p.id AND n.event_number>e.event_number)
     AND NOT EXISTS(SELECT 1 FROM post_revisions n WHERE n.post_id=p.id AND n.revision_number>r.revision_number)
   GROUP BY p.platform,e.scheduled_at HAVING count(*)>1
 ) THEN RAISE EXCEPTION 'Calendar slot occupied' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER post_schedule_slots_seal AFTER INSERT ON post_schedule_events
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_post_schedule_slots();
