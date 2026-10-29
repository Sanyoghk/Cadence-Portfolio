-- Additive report history. Legacy recommendations/metrics and earlier migration checksums stay intact.
ALTER TABLE jobs ADD CONSTRAINT creator_report_job_bounds CHECK(type<>'CREATOR_REPORT' OR(max_attempts=3 AND attempts BETWEEN 0 AND 3));
CREATE TABLE creator_reports(
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version>=0),
 brand_id UUID NOT NULL REFERENCES brands(id),workspace_id UUID NOT NULL REFERENCES workspaces(id),actor_id UUID NOT NULL REFERENCES cadence_users(id),
 strategy_id UUID NOT NULL REFERENCES strategies(id),job_id UUID NOT NULL UNIQUE REFERENCES jobs(id),source_version BIGINT NOT NULL CHECK(source_version>=0),
 source_checksum VARCHAR(64) NOT NULL CHECK(source_checksum ~ '^[0-9a-f]{64}$'),key_digest VARCHAR(64) NOT NULL CHECK(key_digest ~ '^[0-9a-f]{64}$'),
 request_digest VARCHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),snapshot VARCHAR(1048576) NOT NULL CHECK(octet_length(snapshot) BETWEEN 1 AND 1048576),
 snapshot_checksum VARCHAR(64) NOT NULL CHECK(snapshot_checksum=encode(sha256(convert_to(snapshot,'UTF8')),'hex')),
 captured_at TIMESTAMPTZ(6) NOT NULL,deadline TIMESTAMPTZ(6) NOT NULL,expires_at TIMESTAMPTZ(6) NOT NULL,
 artifact BYTEA,artifact_checksum VARCHAR(64),published_attempt INTEGER,published_worker VARCHAR(255),completed_at TIMESTAMPTZ(6),
 UNIQUE(actor_id,workspace_id,key_digest),CHECK(deadline=captured_at+interval '300 seconds' AND expires_at=captured_at+interval '86400 seconds'),
 CHECK((artifact IS NULL AND artifact_checksum IS NULL AND published_attempt IS NULL AND published_worker IS NULL AND completed_at IS NULL)
 OR(artifact IS NOT NULL AND artifact_checksum IS NOT NULL AND published_attempt IS NOT NULL AND published_attempt BETWEEN 1 AND 3 AND published_worker IS NOT NULL AND btrim(published_worker)<>''
 AND completed_at IS NOT NULL AND completed_at>=captured_at AND completed_at<deadline AND octet_length(artifact)>0
 AND octet_length(artifact)+octet_length(snapshot)<=2097152 AND artifact_checksum=encode(sha256(artifact),'hex')))
);
CREATE INDEX creator_reports_brand_idx ON creator_reports(brand_id,captured_at,id);

CREATE FUNCTION report_require_capture(report UUID) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE r creator_reports%ROWTYPE;s strategies%ROWTYPE;c JSONB;g JSONB;v JSONB;a JSONB;o metric_observations%ROWTYPE;p content_posts%ROWTYPE;ctx metric_attributions%ROWTYPE;advice recommendation_insights%ROWTYPE;n INTEGER=0;
BEGIN
 SELECT * INTO r FROM creator_reports WHERE id=report;SELECT * INTO s FROM strategies WHERE id=r.strategy_id FOR UPDATE;c=r.snapshot::jsonb;
 IF s.brand_id IS DISTINCT FROM r.brand_id OR s.status NOT IN('APPROVED','LOCKED') OR s.approved_by IS NULL OR s.approved_at IS NULL
 OR s.content_format_version IS DISTINCT FROM 2 OR s.lock_version IS DISTINCT FROM r.source_version
 OR NOT EXISTS(SELECT 1 FROM brands b JOIN workspaces w ON w.id=b.workspace_id WHERE b.id=r.brand_id AND b.workspace_id=r.workspace_id AND b.status='ACTIVE' AND w.status='ACTIVE')
 OR c->>'format' IS DISTINCT FROM 'creator-report-v1' OR c->>'brandId' IS DISTINCT FROM r.brand_id::text OR c->>'workspaceId' IS DISTINCT FROM r.workspace_id::text
 OR c->'strategy'->>'id' IS DISTINCT FROM s.id::text OR(c->'strategy'->>'revision')::integer IS DISTINCT FROM s.version
 OR(c->'strategy'->>'version')::bigint IS DISTINCT FROM r.source_version OR c->'strategy'->>'checksum' IS DISTINCT FROM r.source_checksum
 OR(c->>'capturedAt')::timestamptz IS DISTINCT FROM r.captured_at OR c->'request'->>'strategyId' IS DISTINCT FROM s.id::text
 OR(c->'request'->>'expectedStrategyVersion')::bigint IS DISTINCT FROM r.source_version OR c->>'formulaVersion' IS DISTINCT FROM 'performance-v1'
 OR c->'request'->>'platform' NOT IN('INSTAGRAM','TIKTOK') OR c->'request'->>'bucket' NOT IN('DAY','PERIOD')
 OR NOT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=c->'request'->>'timezone')
 OR(c->'request'->>'untilDate')::date<=(c->'request'->>'fromDate')::date OR(c->'request'->>'untilDate')::date-(c->'request'->>'fromDate')::date>28
 OR(c->>'candidateRows')::integer NOT BETWEEN 0 AND 1000 OR(c->>'selectedRows')::integer NOT BETWEEN 0 AND 1000
 OR jsonb_array_length(c->'groups')>1000 OR jsonb_array_length(c->'recommendations')>10
 OR jsonb_array_length(c->'request'->'recommendationIds') IS DISTINCT FROM jsonb_array_length(c->'recommendations') THEN
 RAISE EXCEPTION 'Invalid report source capture' USING ERRCODE='23514';END IF;
 FOR g IN SELECT * FROM jsonb_array_elements(c->'groups') LOOP
  FOR v IN SELECT * FROM jsonb_array_elements(g->'sources') LOOP
   n=n+1;SELECT * INTO o FROM metric_observations WHERE id=(v->>'id')::uuid;SELECT * INTO p FROM content_posts WHERE id=o.post_id;
   IF o.id IS NULL OR p.brand_id IS DISTINCT FROM r.brand_id OR p.strategy_id IS DISTINCT FROM s.id OR p.platform IS DISTINCT FROM c->'request'->>'platform'
   OR p.source_checksum IS DISTINCT FROM r.source_checksum OR v->>'postId' IS DISTINCT FROM o.post_id::text OR(v->>'revision')::integer IS DISTINCT FROM o.revision_number
   OR v->>'checksum' IS DISTINCT FROM o.value_checksum OR(v->>'observedAt')::timestamptz IS DISTINCT FROM o.observed_at
   OR(v->>'reach')::bigint IS DISTINCT FROM o.reach OR(v->>'engagement')::bigint IS DISTINCT FROM o.engagement OR(v->>'saves')::bigint IS DISTINCT FROM o.saves
   OR(v->>'signups')::bigint IS DISTINCT FROM o.signups OR(v->>'profileToSiteClicks')::bigint IS DISTINCT FROM o.profile_to_site_clicks
   OR(g->>'windowFrom')::timestamptz IS DISTINCT FROM o.window_from OR(g->>'windowUntil')::timestamptz IS DISTINCT FROM o.window_until THEN
   RAISE EXCEPTION 'Invalid report observation binding' USING ERRCODE='23514';END IF;
   IF v->>'attributionId' IS NOT NULL THEN
    SELECT * INTO ctx FROM metric_attributions WHERE id=(v->>'attributionId')::uuid;
    IF ctx.observation_id IS DISTINCT FROM o.id OR(v->>'attributionVersion')::integer IS DISTINCT FROM ctx.attribution_number
    OR v->>'pillarId' IS DISTINCT FROM ctx.pillar_id OR v->>'contentChecksum' IS DISTINCT FROM ctx.content_checksum THEN
     RAISE EXCEPTION 'Invalid report attribution binding' USING ERRCODE='23514';END IF;
   END IF;
  END LOOP;
 END LOOP;
 IF n IS DISTINCT FROM(c->>'selectedRows')::integer OR n>1000
 OR(SELECT count(distinct obs.item->>'id') FROM jsonb_array_elements(c->'groups') AS grp(item) CROSS JOIN LATERAL jsonb_array_elements(grp.item->'sources') AS obs(item))<>n THEN
 RAISE EXCEPTION 'Invalid report selection bounds' USING ERRCODE='23514';END IF;
 FOR a IN SELECT * FROM jsonb_array_elements(c->'recommendations') LOOP
  SELECT * INTO advice FROM recommendation_insights WHERE id=(a->>'id')::uuid;
  IF advice.id IS NULL OR advice.brand_id IS DISTINCT FROM r.brand_id OR advice.strategy_id IS DISTINCT FROM s.id
  OR advice.source_checksum IS DISTINCT FROM r.source_checksum OR a->>'snapshotChecksum' IS DISTINCT FROM advice.snapshot_checksum
  OR a->>'confidence' IS DISTINCT FROM advice.confidence OR a->'attribution'->>'origin' IS DISTINCT FROM advice.origin
  OR a->'attribution'->>'ruleVersion' IS DISTINCT FROM advice.rule_version OR a->>'explanation' IS DISTINCT FROM advice.explanation
  OR NOT(c->'request'->'recommendationIds' @> to_jsonb(advice.id::text))
  OR jsonb_array_length(a->'supportingObservations') IS DISTINCT FROM jsonb_array_length(advice.snapshot::jsonb->'sources') THEN
  RAISE EXCEPTION 'Invalid report recommendation binding' USING ERRCODE='23514';END IF;
  FOR v IN SELECT * FROM jsonb_array_elements(a->'supportingObservations') LOOP
   IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(advice.snapshot::jsonb->'sources') x
      WHERE x->>'observationId'=v->>'id' AND x->>'valueChecksum'=v->>'checksum' AND x->>'attributionId' IS NOT DISTINCT FROM v->>'attributionId'
      AND x->>'reach' IS NOT DISTINCT FROM v->>'reach' AND x->>'engagement' IS NOT DISTINCT FROM v->>'engagement') THEN
    RAISE EXCEPTION 'Forged report supporting observation' USING ERRCODE='23514';END IF;
  END LOOP;
 END LOOP;
END $$;

CREATE FUNCTION guard_creator_report() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE j jobs%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Report history immutable' USING ERRCODE='23514';END IF;
 PERFORM recommendation_lock_actor(NEW.actor_id,NEW.brand_id);
 IF TG_OP='INSERT' THEN
  IF NEW.artifact IS NOT NULL OR NEW.lock_version<>0 OR NEW.captured_at<transaction_timestamp() OR NEW.captured_at>clock_timestamp()
  OR(SELECT count(*) FROM creator_reports WHERE brand_id=NEW.brand_id)>=100 THEN RAISE EXCEPTION 'Invalid report admission' USING ERRCODE='23514';END IF;
 ELSE
  IF OLD.artifact IS NOT NULL OR NEW.artifact IS NULL OR NEW.lock_version<>OLD.lock_version+1
  OR ROW(NEW.id,NEW.created_at,NEW.brand_id,NEW.workspace_id,NEW.actor_id,NEW.strategy_id,NEW.job_id,NEW.source_version,NEW.source_checksum,NEW.key_digest,NEW.request_digest,NEW.snapshot,NEW.snapshot_checksum,NEW.captured_at,NEW.deadline,NEW.expires_at)
  IS DISTINCT FROM ROW(OLD.id,OLD.created_at,OLD.brand_id,OLD.workspace_id,OLD.actor_id,OLD.strategy_id,OLD.job_id,OLD.source_version,OLD.source_checksum,OLD.key_digest,OLD.request_digest,OLD.snapshot,OLD.snapshot_checksum,OLD.captured_at,OLD.deadline,OLD.expires_at) THEN
   RAISE EXCEPTION 'Report capture or artifact immutable' USING ERRCODE='23514';END IF;
  SELECT * INTO j FROM jobs WHERE id=NEW.job_id FOR UPDATE;
  IF j.status IS DISTINCT FROM 'RUNNING' OR j.attempts IS DISTINCT FROM NEW.published_attempt OR j.lease_owner IS DISTINCT FROM NEW.published_worker
  OR j.lease_expires_at IS NULL OR j.lease_expires_at<=clock_timestamp() OR NEW.deadline<=clock_timestamp()
  OR NEW.completed_at<transaction_timestamp() OR NEW.completed_at>clock_timestamp() THEN
   RAISE EXCEPTION 'Report worker fenced' USING ERRCODE='23514';END IF;
 END IF;RETURN NEW;
END $$;
CREATE TRIGGER creator_report_guard BEFORE INSERT OR UPDATE OR DELETE ON creator_reports FOR EACH ROW EXECUTE FUNCTION guard_creator_report();
CREATE FUNCTION seal_creator_report() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE r creator_reports%ROWTYPE;j jobs%ROWTYPE;
BEGIN
 IF TG_TABLE_NAME='jobs' THEN
  IF TG_OP='UPDATE' AND OLD.type='CREATOR_REPORT' AND NEW.type<>'CREATOR_REPORT' THEN RAISE EXCEPTION 'Report Job kind immutable' USING ERRCODE='23514';END IF;
  IF NEW.type<>'CREATOR_REPORT' THEN RETURN NULL;END IF;
  SELECT * INTO r FROM creator_reports WHERE job_id=NEW.id;
 ELSE SELECT * INTO r FROM creator_reports WHERE id=NEW.id;END IF;
 SELECT * INTO j FROM jobs WHERE id=r.job_id;
 IF r.id IS NULL OR j.type IS DISTINCT FROM 'CREATOR_REPORT' OR j.payload_reference IS DISTINCT FROM 'report:'||r.id::text
 OR j.idempotency_key IS DISTINCT FROM 'report:'||r.actor_id::text||':'||r.workspace_id::text||':'||r.key_digest OR j.max_attempts<>3
 OR((j.status='SUCCEEDED') IS DISTINCT FROM(r.artifact IS NOT NULL))
 OR(r.artifact IS NOT NULL AND(r.published_attempt<>j.attempts OR j.completed_at<r.completed_at)) THEN
 RAISE EXCEPTION 'Report Job publication incoherent' USING ERRCODE='23514';END IF;
 IF TG_TABLE_NAME='creator_reports' THEN PERFORM report_require_capture(r.id);END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER creator_report_seal AFTER INSERT OR UPDATE ON creator_reports DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_creator_report();
CREATE CONSTRAINT TRIGGER creator_report_job_seal AFTER INSERT OR UPDATE ON jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_creator_report();
