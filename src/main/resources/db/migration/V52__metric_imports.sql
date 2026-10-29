-- Human-asserted metrics only. Legacy post_metrics remain untouched and unverified.
ALTER TABLE jobs ADD CONSTRAINT metrics_job_bounds CHECK(type<>'METRICS_IMPORT' OR(max_attempts=3 AND attempts BETWEEN 0 AND 3));
CREATE TABLE metric_imports(
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version>=0),
 brand_id UUID NOT NULL REFERENCES brands(id),job_id UUID NOT NULL UNIQUE REFERENCES jobs(id),actor_id UUID NOT NULL REFERENCES cadence_users(id),
 client_key VARCHAR(128) NOT NULL CHECK(client_key ~ '^[A-Za-z0-9_-]{16,128}$'),origin VARCHAR(16) NOT NULL CHECK(origin IN('MANUAL_JSON','CSV')),
 raw_input BYTEA NOT NULL CHECK(octet_length(raw_input) BETWEEN 1 AND 131072),raw_checksum VARCHAR(64) NOT NULL CHECK(raw_checksum=encode(sha256(raw_input),'hex')),
 request_digest VARCHAR(64) NOT NULL CHECK(request_digest=encode(sha256(convert_to('metric-import-v1|'||origin||'|'||raw_checksum,'UTF8')),'hex')),
 row_count INTEGER NOT NULL CHECK(row_count BETWEEN 1 AND 200),submitted_at TIMESTAMPTZ(6) NOT NULL,deadline TIMESTAMPTZ(6) NOT NULL,
 completed_at TIMESTAMPTZ(6),published_attempt INTEGER,published_worker VARCHAR(255),UNIQUE(brand_id,actor_id,client_key),
 CHECK(deadline=submitted_at+interval '300 seconds'),
 CHECK((completed_at IS NULL AND published_attempt IS NULL AND published_worker IS NULL) OR
 (completed_at IS NOT NULL AND published_attempt IS NOT NULL AND published_attempt BETWEEN 1 AND 3 AND published_worker IS NOT NULL AND btrim(published_worker)<>'' AND completed_at>=submitted_at AND completed_at<deadline))
);
CREATE INDEX metric_imports_brand_idx ON metric_imports(brand_id,submitted_at,id);
CREATE TABLE metric_observations(
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 post_id UUID NOT NULL REFERENCES content_posts(id),import_id UUID NOT NULL REFERENCES metric_imports(id),previous_id UUID UNIQUE REFERENCES metric_observations(id),
 revision_number INTEGER NOT NULL CHECK(revision_number BETWEEN 1 AND 100),
 window_from TIMESTAMPTZ(6) NOT NULL,window_until TIMESTAMPTZ(6) NOT NULL,observed_at TIMESTAMPTZ(6) NOT NULL,ingested_at TIMESTAMPTZ(6) NOT NULL,
 value_checksum VARCHAR(64) NOT NULL CHECK(value_checksum ~ '^[0-9a-f]{64}$'),
 reach BIGINT CHECK(reach BETWEEN 0 AND 1000000000000),engagement BIGINT CHECK(engagement BETWEEN 0 AND 1000000000000),saves BIGINT CHECK(saves BETWEEN 0 AND 1000000000000),
 signups BIGINT CHECK(signups BETWEEN 0 AND 1000000000000),profile_to_site_clicks BIGINT CHECK(profile_to_site_clicks BETWEEN 0 AND 1000000000000),
 CHECK(num_nonnulls(reach,engagement,saves,signups,profile_to_site_clicks)>0),
 CHECK(window_from>='2000-01-01Z'::timestamptz AND window_from<window_until AND window_until<=observed_at AND observed_at<='2200-01-01Z'::timestamptz AND observed_at<=ingested_at AND window_until-window_from<=interval '366 days'),
 CHECK((revision_number=1 AND previous_id IS NULL) OR(revision_number>1 AND previous_id IS NOT NULL)),UNIQUE(post_id,observed_at,revision_number)
);
CREATE INDEX metric_observations_import_idx ON metric_observations(import_id,id);
CREATE TABLE metric_import_results(
 import_id UUID NOT NULL REFERENCES metric_imports(id),row_number INTEGER NOT NULL CHECK(row_number BETWEEN 0 AND 199),observation_id UUID NOT NULL REFERENCES metric_observations(id),
 PRIMARY KEY(import_id,row_number),UNIQUE(import_id,observation_id)
);
CREATE INDEX metric_import_results_observation_idx ON metric_import_results(observation_id,import_id);

CREATE FUNCTION metrics_parent(import UUID) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE e metric_imports%ROWTYPE;b brands%ROWTYPE;w workspaces%ROWTYPE;
BEGIN
 SELECT * INTO e FROM metric_imports WHERE id=import;
 PERFORM 1 FROM cadence_users WHERE id=e.actor_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=e.brand_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=e.brand_id FOR UPDATE;
 PERFORM post_require_actor(e.actor_id,w.id);
 IF b.status IS DISTINCT FROM 'ACTIVE' OR w.status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Metrics parent unavailable' USING ERRCODE='23514';END IF;
END $$;
CREATE FUNCTION guard_metric_import() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE b brands%ROWTYPE;w workspaces%ROWTYPE;j jobs%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Metrics history retained' USING ERRCODE='23514';END IF;
 PERFORM 1 FROM cadence_users WHERE id=NEW.actor_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=NEW.brand_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=NEW.brand_id FOR UPDATE;
 PERFORM post_require_actor(NEW.actor_id,w.id);
 IF b.status IS DISTINCT FROM 'ACTIVE' OR w.status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Metrics parent unavailable' USING ERRCODE='23514';END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.lock_version<>0 OR NEW.completed_at IS NOT NULL OR NEW.submitted_at>clock_timestamp() OR NEW.submitted_at<clock_timestamp()-interval '30 seconds'
   OR(SELECT count(*) FROM metric_imports WHERE brand_id=NEW.brand_id)>=1000 THEN RAISE EXCEPTION 'Invalid metrics capture or quota' USING ERRCODE='23514';END IF;
 ELSE
  IF OLD.completed_at IS NOT NULL OR NEW.completed_at IS NULL OR NEW.lock_version<>OLD.lock_version+1 OR
   ROW(NEW.id,NEW.created_at,NEW.brand_id,NEW.job_id,NEW.actor_id,NEW.client_key,NEW.origin,NEW.raw_input,NEW.raw_checksum,NEW.request_digest,NEW.row_count,NEW.submitted_at,NEW.deadline)
   IS DISTINCT FROM ROW(OLD.id,OLD.created_at,OLD.brand_id,OLD.job_id,OLD.actor_id,OLD.client_key,OLD.origin,OLD.raw_input,OLD.raw_checksum,OLD.request_digest,OLD.row_count,OLD.submitted_at,OLD.deadline) THEN
   RAISE EXCEPTION 'Metrics receipt immutable' USING ERRCODE='23514';END IF;
  SELECT * INTO j FROM jobs WHERE id=NEW.job_id FOR UPDATE;
  IF j.status IS DISTINCT FROM 'RUNNING' OR j.attempts<>NEW.published_attempt OR j.lease_owner IS DISTINCT FROM NEW.published_worker OR j.lease_expires_at IS NULL
   OR j.lease_expires_at<=clock_timestamp() OR NEW.deadline<=clock_timestamp() OR NEW.completed_at<transaction_timestamp() OR NEW.completed_at>clock_timestamp() THEN
   RAISE EXCEPTION 'Metrics worker fenced' USING ERRCODE='23514';END IF;
 END IF;RETURN NEW;
END $$;
CREATE TRIGGER metric_import_guard BEFORE INSERT OR UPDATE OR DELETE ON metric_imports FOR EACH ROW EXECUTE FUNCTION guard_metric_import();

CREATE FUNCTION guard_metric_observation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE e metric_imports%ROWTYPE;p content_posts%ROWTYPE;prior metric_observations%ROWTYPE;j jobs%ROWTYPE;digest TEXT;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Metrics observation immutable' USING ERRCODE='23514';END IF;
 PERFORM metrics_parent(NEW.import_id);
 SELECT * INTO e FROM metric_imports WHERE id=NEW.import_id;
 SELECT * INTO p FROM content_posts WHERE id=NEW.post_id FOR UPDATE;
 SELECT * INTO j FROM jobs WHERE id=e.job_id;
 IF p.brand_id IS DISTINCT FROM e.brand_id OR p.legacy OR p.platform IS NULL OR e.completed_at IS NOT NULL OR j.status IS DISTINCT FROM 'RUNNING'
  OR j.lease_expires_at IS NULL OR j.lease_expires_at<=clock_timestamp() OR e.deadline<=clock_timestamp()
  OR NEW.ingested_at<transaction_timestamp() OR NEW.ingested_at>clock_timestamp() OR NEW.observed_at>e.submitted_at
  OR(SELECT count(*) FROM metric_observations o JOIN content_posts cp ON cp.id=o.post_id WHERE cp.brand_id=e.brand_id)>=100000 THEN
  RAISE EXCEPTION 'Metrics source or lease unavailable' USING ERRCODE='23514';END IF;
 SELECT * INTO prior FROM metric_observations WHERE post_id=NEW.post_id AND observed_at=NEW.observed_at ORDER BY revision_number DESC LIMIT 1;
 IF (prior.id IS NULL AND (NEW.previous_id IS NOT NULL OR NEW.revision_number<>1)) OR
  (prior.id IS NOT NULL AND (NEW.previous_id IS DISTINCT FROM prior.id OR NEW.revision_number<>prior.revision_number+1 OR NEW.value_checksum=prior.value_checksum)) THEN
  RAISE EXCEPTION 'Metrics correction conflict' USING ERRCODE='23514';END IF;
 digest='metric-interval-v1|'||NEW.post_id::text||'|'||p.platform||'|'||(extract(epoch FROM NEW.window_from)*1000000)::bigint::text||'|'||
 (extract(epoch FROM NEW.window_until)*1000000)::bigint::text||'|'||(extract(epoch FROM NEW.observed_at)*1000000)::bigint::text||'|'||
 coalesce(NEW.reach::text,'null')||'|'||coalesce(NEW.engagement::text,'null')||'|'||coalesce(NEW.saves::text,'null')||'|'||coalesce(NEW.signups::text,'null')||'|'||coalesce(NEW.profile_to_site_clicks::text,'null');
 IF NEW.value_checksum<>encode(sha256(convert_to(digest,'UTF8')),'hex') THEN RAISE EXCEPTION 'Invalid metric checksum' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER metric_observation_guard BEFORE INSERT OR UPDATE OR DELETE ON metric_observations FOR EACH ROW EXECUTE FUNCTION guard_metric_observation();
CREATE FUNCTION guard_metric_result() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'INSERT' OR NOT EXISTS(SELECT 1 FROM metric_imports e WHERE e.id=NEW.import_id AND e.completed_at IS NOT NULL
 AND e.xmin::text::bigint=mod(txid_current(),4294967296)) THEN RAISE EXCEPTION 'Metrics result immutable or unfenced' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER metric_result_guard BEFORE INSERT OR UPDATE OR DELETE ON metric_import_results FOR EACH ROW EXECUTE FUNCTION guard_metric_result();
CREATE FUNCTION seal_metric_import() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE e metric_imports%ROWTYPE;j jobs%ROWTYPE;n INTEGER;
BEGIN
 IF TG_TABLE_NAME='jobs' THEN
  IF TG_OP='UPDATE' AND OLD.type='METRICS_IMPORT' AND NEW.type<>'METRICS_IMPORT' THEN RAISE EXCEPTION 'Metrics Job kind immutable' USING ERRCODE='23514';END IF;
  IF NEW.type<>'METRICS_IMPORT' THEN RETURN NULL;END IF;SELECT * INTO e FROM metric_imports WHERE job_id=NEW.id;
 ELSIF TG_TABLE_NAME='metric_imports' THEN SELECT * INTO e FROM metric_imports WHERE id=NEW.id;
 ELSE SELECT * INTO e FROM metric_imports WHERE id=NEW.import_id; END IF;
 SELECT * INTO j FROM jobs WHERE id=e.job_id;
 SELECT count(*) INTO n FROM metric_import_results WHERE import_id=e.id;
 IF e.id IS NULL OR j.type IS DISTINCT FROM 'METRICS_IMPORT' OR j.payload_reference IS DISTINCT FROM 'metrics:'||e.id::text
  OR j.idempotency_key IS DISTINCT FROM 'metrics:'||e.actor_id::text||':'||e.brand_id::text||':'||e.client_key
  OR ((j.status='SUCCEEDED') IS DISTINCT FROM(e.completed_at IS NOT NULL))
  OR(e.completed_at IS NULL AND (n<>0 OR EXISTS(SELECT 1 FROM metric_observations WHERE import_id=e.id)))
  OR(e.completed_at IS NOT NULL AND(n<>e.row_count OR(SELECT max(row_number) FROM metric_import_results WHERE import_id=e.id)<>n-1
   OR e.published_attempt<>j.attempts OR j.completed_at<e.completed_at
   OR EXISTS(SELECT 1 FROM metric_import_results r JOIN metric_observations o ON o.id=r.observation_id JOIN content_posts p ON p.id=o.post_id WHERE r.import_id=e.id AND p.brand_id<>e.brand_id)
   OR EXISTS(SELECT 1 FROM metric_observations o WHERE o.import_id=e.id AND NOT EXISTS(SELECT 1 FROM metric_import_results r WHERE r.import_id=e.id AND r.observation_id=o.id)))) THEN
  RAISE EXCEPTION 'Metrics publication incoherent' USING ERRCODE='23514';END IF;
 -- Only new publication rows must belong to this transaction; later history survives revocation.
 IF TG_TABLE_NAME='metric_observations' AND NOT EXISTS(SELECT 1 FROM metric_imports WHERE id=e.id AND completed_at IS NOT NULL AND xmin::text::bigint=mod(txid_current(),4294967296)) THEN
  RAISE EXCEPTION 'Metrics observation without publication' USING ERRCODE='23514';END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER metric_import_seal AFTER INSERT OR UPDATE ON metric_imports DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_metric_import();
CREATE CONSTRAINT TRIGGER metric_observation_seal AFTER INSERT ON metric_observations DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_metric_import();
CREATE CONSTRAINT TRIGGER metric_result_seal AFTER INSERT ON metric_import_results DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_metric_import();
CREATE CONSTRAINT TRIGGER metric_job_seal AFTER INSERT OR UPDATE ON jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_metric_import();
