ALTER TABLE jobs ADD CONSTRAINT csv_job_bounds CHECK(type<>'CSV_EXPORT' OR (max_attempts=3 AND attempts BETWEEN 0 AND 3));
CREATE TABLE csv_exports (
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version>=0),brand_id UUID NOT NULL REFERENCES brands(id),
 job_id UUID NOT NULL UNIQUE REFERENCES jobs(id),actor_id UUID NOT NULL REFERENCES cadence_users(id),
 client_key VARCHAR(128) NOT NULL CHECK(client_key ~ '^[A-Za-z0-9_-]{16,128}$'),
 request_digest VARCHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
 from_at TIMESTAMPTZ(6) NOT NULL,until_at TIMESTAMPTZ(6) NOT NULL,timezone VARCHAR(100) NOT NULL,
 captured_at TIMESTAMPTZ(6) NOT NULL,deadline TIMESTAMPTZ(6) NOT NULL,expires_at TIMESTAMPTZ(6) NOT NULL,
 snapshot_checksum VARCHAR(64) NOT NULL CHECK(snapshot_checksum ~ '^[0-9a-f]{64}$'),
 artifact BYTEA,artifact_checksum VARCHAR(64),published_attempt INTEGER,published_worker VARCHAR(255),completed_at TIMESTAMPTZ(6),
 UNIQUE(brand_id,actor_id,client_key),
 CHECK(deadline=captured_at+interval '300 seconds' AND expires_at=captured_at+interval '86400 seconds'),
 CHECK(from_at<until_at AND from_at>='1900-01-01Z'::timestamptz AND until_at<='2200-01-01Z'::timestamptz),
 CHECK((artifact IS NULL AND artifact_checksum IS NULL AND published_attempt IS NULL AND published_worker IS NULL AND completed_at IS NULL)
 OR(artifact IS NOT NULL AND artifact_checksum IS NOT NULL AND published_attempt IS NOT NULL AND published_attempt BETWEEN 1 AND 3 AND published_worker IS NOT NULL
 AND btrim(published_worker)<>'' AND completed_at IS NOT NULL AND completed_at>=captured_at AND completed_at<deadline
 AND octet_length(artifact) BETWEEN 1 AND 2097152 AND artifact_checksum=encode(sha256(artifact),'hex')))
);
CREATE INDEX csv_exports_brand_idx ON csv_exports(brand_id,captured_at,id);
CREATE TABLE csv_export_items (
 export_id UUID NOT NULL REFERENCES csv_exports(id),item_order INTEGER NOT NULL CHECK(item_order BETWEEN 0 AND 83),
 revision_id UUID NOT NULL REFERENCES post_revisions(id),schedule_id UUID NOT NULL REFERENCES post_schedule_events(id),
 revision_version BIGINT NOT NULL CHECK(revision_version>=0),content_checksum VARCHAR(64) NOT NULL CHECK(content_checksum ~ '^[0-9a-f]{64}$'),
 PRIMARY KEY(export_id,item_order),UNIQUE(export_id,revision_id),UNIQUE(export_id,schedule_id)
);
CREATE INDEX csv_export_items_revision_idx ON csv_export_items(revision_id,export_id);

CREATE FUNCTION csv_require_sources(export UUID) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE e csv_exports%ROWTYPE;n INTEGER;
BEGIN
 SELECT * INTO e FROM csv_exports WHERE id=export;
 SELECT count(*) INTO n FROM csv_export_items WHERE export_id=export;
 IF n<1 OR n>84 OR (SELECT max(item_order) FROM csv_export_items WHERE export_id=export)<>n-1 THEN
 RAISE EXCEPTION 'Invalid export selection bounds' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM csv_export_items i JOIN post_revisions r ON r.id=i.revision_id
 JOIN content_posts p ON p.id=r.post_id JOIN strategies s ON s.id=p.strategy_id JOIN post_schedule_events h ON h.id=i.schedule_id
 WHERE i.export_id=export AND (p.brand_id<>e.brand_id OR p.legacy OR r.status<>'APPROVED' OR r.approved_by IS NULL OR r.approved_at IS NULL
 OR r.lock_version<>i.revision_version OR i.content_checksum<>post_schedule_checksum(r.id)
 OR EXISTS(SELECT 1 FROM post_revisions newer WHERE newer.post_id=p.id AND newer.revision_number>r.revision_number)
 OR h.post_id<>p.id OR h.revision_id<>r.id OR h.revision_lock_version<>r.lock_version OR h.content_checksum<>i.content_checksum
 OR h.action<>'SCHEDULE' OR h.scheduled_at<e.from_at OR h.scheduled_at>=e.until_at
 OR EXISTS(SELECT 1 FROM post_schedule_events newer WHERE newer.post_id=p.id AND newer.event_number>h.event_number)
 OR s.status<>'APPROVED' OR s.content_format_version<>2 OR s.lock_version<>p.source_lock_version OR s.version<>p.source_revision
 OR s.approved_by IS DISTINCT FROM p.source_approved_by OR s.approved_at IS DISTINCT FROM p.source_approved_at))
 OR EXISTS(SELECT 1 FROM csv_export_items i JOIN post_revision_media l ON l.revision_id=i.revision_id JOIN media_assets a ON a.id=l.asset_id
 WHERE i.export_id=export AND (a.brand_id<>e.brand_id OR a.status<>'AVAILABLE' OR a.moderation<>'APPROVED_DEMO' OR a.scan_result IS DISTINCT FROM 'CLEAN')) THEN
 RAISE EXCEPTION 'Export source unavailable' USING ERRCODE='23514'; END IF;
END $$;

CREATE FUNCTION guard_csv_export() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE b brands%ROWTYPE;w workspaces%ROWTYPE;j jobs%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Export history retained' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM cadence_users WHERE id=NEW.actor_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=NEW.brand_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=NEW.brand_id FOR UPDATE;
 PERFORM post_require_actor(NEW.actor_id,w.id);
 IF b.status IS DISTINCT FROM 'ACTIVE' OR w.status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Export parent unavailable' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.artifact IS NOT NULL OR NEW.lock_version<>0 OR NEW.captured_at>clock_timestamp() OR NEW.captured_at<clock_timestamp()-interval '30 seconds'
   OR (SELECT count(*) FROM csv_exports WHERE brand_id=NEW.brand_id)>=100
   OR NOT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=NEW.timezone)
   OR (NEW.until_at AT TIME ZONE NEW.timezone)>(NEW.from_at AT TIME ZONE NEW.timezone)+interval '28 days' THEN
   RAISE EXCEPTION 'Invalid export capture or quota' USING ERRCODE='23514'; END IF;
 ELSE
  IF OLD.artifact IS NOT NULL OR NEW.artifact IS NULL OR NEW.lock_version<>OLD.lock_version+1 OR
   ROW(NEW.id,NEW.created_at,NEW.brand_id,NEW.job_id,NEW.actor_id,NEW.client_key,NEW.request_digest,NEW.from_at,NEW.until_at,NEW.timezone,NEW.captured_at,NEW.deadline,NEW.expires_at,NEW.snapshot_checksum)
   IS DISTINCT FROM ROW(OLD.id,OLD.created_at,OLD.brand_id,OLD.job_id,OLD.actor_id,OLD.client_key,OLD.request_digest,OLD.from_at,OLD.until_at,OLD.timezone,OLD.captured_at,OLD.deadline,OLD.expires_at,OLD.snapshot_checksum) THEN
   RAISE EXCEPTION 'Export capture or artifact immutable' USING ERRCODE='23514'; END IF;
  SELECT * INTO j FROM jobs WHERE id=NEW.job_id FOR UPDATE;
  IF j.status IS DISTINCT FROM 'RUNNING' OR j.attempts<>NEW.published_attempt OR j.lease_owner IS DISTINCT FROM NEW.published_worker
   OR j.lease_expires_at IS NULL OR j.lease_expires_at<=clock_timestamp() OR NEW.deadline<=clock_timestamp()
   OR NEW.completed_at<transaction_timestamp() OR NEW.completed_at>clock_timestamp() THEN
   RAISE EXCEPTION 'Export worker fenced' USING ERRCODE='23514'; END IF;
  PERFORM csv_require_sources(NEW.id);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER csv_export_guard BEFORE INSERT OR UPDATE OR DELETE ON csv_exports FOR EACH ROW EXECUTE FUNCTION guard_csv_export();

CREATE FUNCTION guard_csv_item() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'INSERT' OR NOT EXISTS(SELECT 1 FROM csv_exports e WHERE e.id=NEW.export_id AND e.artifact IS NULL
 AND e.xmin::text::bigint=mod(txid_current(),4294967296) AND e.lock_version=0) THEN
 RAISE EXCEPTION 'Export selection immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER csv_item_guard BEFORE INSERT OR UPDATE OR DELETE ON csv_export_items FOR EACH ROW EXECUTE FUNCTION guard_csv_item();

CREATE FUNCTION seal_csv_export() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE e csv_exports%ROWTYPE;j jobs%ROWTYPE;
BEGIN
 IF TG_TABLE_NAME='jobs' THEN
  IF TG_OP='UPDATE' AND OLD.type='CSV_EXPORT' AND NEW.type<>'CSV_EXPORT' THEN
   RAISE EXCEPTION 'Export Job kind immutable' USING ERRCODE='23514'; END IF;
  IF NEW.type<>'CSV_EXPORT' THEN RETURN NULL; END IF;
  SELECT * INTO e FROM csv_exports WHERE job_id=NEW.id;
 ELSE SELECT * INTO e FROM csv_exports WHERE id=NEW.id; END IF;
 SELECT * INTO j FROM jobs WHERE id=e.job_id;
 IF e.id IS NULL OR j.type IS DISTINCT FROM 'CSV_EXPORT' OR j.payload_reference IS DISTINCT FROM 'csv-export:'||e.id::text
  OR j.idempotency_key IS DISTINCT FROM 'csv:'||e.actor_id::text||':'||e.brand_id::text||':'||e.client_key OR j.max_attempts<>3
  OR ((j.status='SUCCEEDED') IS DISTINCT FROM (e.artifact IS NOT NULL))
  OR (e.artifact IS NOT NULL AND (e.published_attempt<>j.attempts OR j.completed_at<e.completed_at)) THEN
  RAISE EXCEPTION 'Export Job publication incoherent' USING ERRCODE='23514'; END IF;
 -- Source checks only at capture/publication. Historical terminal rows survive later revocation.
 IF TG_TABLE_NAME='csv_exports' THEN PERFORM csv_require_sources(e.id); END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER csv_export_seal AFTER INSERT OR UPDATE ON csv_exports DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_csv_export();
CREATE CONSTRAINT TRIGGER csv_job_seal AFTER INSERT OR UPDATE ON jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_csv_export();
