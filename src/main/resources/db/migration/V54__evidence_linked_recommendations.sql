-- Legacy recommendations retain their original unverified provenance. No backfill.
CREATE TABLE recommendation_keys(
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 actor_id UUID NOT NULL REFERENCES cadence_users(id),workspace_id UUID NOT NULL REFERENCES workspaces(id),brand_id UUID NOT NULL REFERENCES brands(id),operation VARCHAR(20) NOT NULL CHECK(operation IN('GENERATE','APPLY','DISMISS')),
 key_digest VARCHAR(64) NOT NULL CHECK(key_digest ~ '^[0-9a-f]{64}$'),request_digest VARCHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),UNIQUE(actor_id,workspace_id,operation,key_digest)
);
CREATE INDEX recommendation_key_brand_idx ON recommendation_keys(brand_id);
CREATE TABLE recommendation_insights(
 request_id UUID NOT NULL UNIQUE REFERENCES recommendation_keys(id),
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 brand_id UUID NOT NULL REFERENCES brands(id),strategy_id UUID NOT NULL REFERENCES strategies(id),actor_id UUID NOT NULL REFERENCES cadence_users(id),workspace_id UUID NOT NULL REFERENCES workspaces(id),
 key_digest VARCHAR(64) NOT NULL CHECK(key_digest ~ '^[0-9a-f]{64}$'),request_digest VARCHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
 source_version BIGINT NOT NULL CHECK(source_version>=0),source_checksum VARCHAR(64) NOT NULL CHECK(source_checksum ~ '^[0-9a-f]{64}$'),
 snapshot VARCHAR(262144) NOT NULL CHECK(octet_length(snapshot)<=262144),snapshot_checksum VARCHAR(64) NOT NULL CHECK(snapshot_checksum ~ '^[0-9a-f]{64}$'),
 from_pillar VARCHAR(64) NOT NULL,to_pillar VARCHAR(64) NOT NULL,CHECK(from_pillar<>to_pillar),
 window_from TIMESTAMPTZ(6) NOT NULL,window_until TIMESTAMPTZ(6) NOT NULL,CHECK(window_from<window_until AND window_until-window_from<=interval '29 days'),
 platform VARCHAR(20) NOT NULL CHECK(platform IN('INSTAGRAM','TIKTOK')),captured_at TIMESTAMPTZ(6) NOT NULL,
 origin VARCHAR(20) NOT NULL CHECK(origin='HEURISTIC'),rule_version VARCHAR(64) NOT NULL CHECK(rule_version='pillar-shift-v1'),provider VARCHAR(64) NOT NULL CHECK(provider='cadence-rules'),model_version VARCHAR(100),prompt_version VARCHAR(100),confidence VARCHAR(30) NOT NULL CHECK(confidence='UNCALIBRATED'),
 CHECK(model_version IS NULL AND prompt_version IS NULL),explanation VARCHAR(2000) NOT NULL,
 rules_version VARCHAR(30) NOT NULL CHECK(btrim(rules_version)<>''),
 CHECK(explanation='Consider testing a 5-point pillar-weight transfer from '||from_pillar||' to '||to_pillar||'. Observed engagement/reach differs; causal improvement is unknown.'),
 UNIQUE(actor_id,workspace_id,key_digest),
 FOREIGN KEY(strategy_id,from_pillar) REFERENCES strategy_playbook_pillar_ids(strategy_id,local_id),
 FOREIGN KEY(strategy_id,to_pillar) REFERENCES strategy_playbook_pillar_ids(strategy_id,local_id)
);
CREATE INDEX recommendation_brand_page_idx ON recommendation_insights(brand_id,created_at DESC,id);
CREATE TABLE recommendation_metric_links(
 recommendation_id UUID NOT NULL REFERENCES recommendation_insights(id),item_order INTEGER NOT NULL CHECK(item_order BETWEEN 0 AND 99),
 observation_id UUID NOT NULL REFERENCES metric_observations(id),attribution_id UUID NOT NULL REFERENCES metric_attributions(id),
 value_checksum VARCHAR(64) NOT NULL CHECK(value_checksum ~ '^[0-9a-f]{64}$'),
 PRIMARY KEY(recommendation_id,item_order),UNIQUE(recommendation_id,observation_id)
);
CREATE INDEX recommendation_observation_idx ON recommendation_metric_links(observation_id);
CREATE TABLE recommendation_resolutions(
 request_id UUID NOT NULL UNIQUE REFERENCES recommendation_keys(id),
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 recommendation_id UUID NOT NULL UNIQUE REFERENCES recommendation_insights(id),actor_id UUID NOT NULL REFERENCES cadence_users(id),workspace_id UUID NOT NULL REFERENCES workspaces(id),
 action VARCHAR(20) NOT NULL CHECK(action IN('APPLY','DISMISS')),key_digest VARCHAR(64) NOT NULL CHECK(key_digest ~ '^[0-9a-f]{64}$'),request_digest VARCHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
 draft_id UUID UNIQUE REFERENCES strategies(id),draft_checksum VARCHAR(64),resolved_at TIMESTAMPTZ(6) NOT NULL,
 CHECK((action='APPLY' AND draft_id IS NOT NULL AND draft_checksum IS NOT NULL AND draft_checksum ~ '^[0-9a-f]{64}$') OR(action='DISMISS' AND draft_id IS NULL AND draft_checksum IS NULL)),
 UNIQUE(actor_id,workspace_id,action,key_digest)
);
CREATE FUNCTION recommendation_lock_actor(actor UUID,brand UUID) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE ws UUID;
BEGIN
 PERFORM 1 FROM cadence_users WHERE id=actor FOR UPDATE;
 SELECT workspace_id INTO ws FROM brands WHERE id=brand;
 PERFORM 1 FROM workspaces WHERE id=ws AND status='ACTIVE' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Recommendation parent unavailable' USING ERRCODE='23514';END IF;
 PERFORM 1 FROM brands WHERE id=brand AND status='ACTIVE' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Recommendation parent unavailable' USING ERRCODE='23514';END IF;
 PERFORM post_require_actor(actor,ws);
END $$;
CREATE FUNCTION guard_recommendation_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE r recommendation_insights%ROWTYPE; b brands%ROWTYPE; s strategies%ROWTYPE; c JSONB;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Recommendation history immutable' USING ERRCODE='23514';END IF;
 IF TG_TABLE_NAME='recommendation_keys' THEN
  PERFORM recommendation_lock_actor(NEW.actor_id,NEW.brand_id);
  IF NEW.workspace_id IS DISTINCT FROM(SELECT workspace_id FROM brands WHERE id=NEW.brand_id) OR(SELECT count(*) FROM recommendation_keys WHERE brand_id=NEW.brand_id)>=10000 THEN RAISE EXCEPTION 'Invalid recommendation admission' USING ERRCODE='23514';END IF;
 ELSIF TG_TABLE_NAME='recommendation_insights' THEN
  PERFORM recommendation_lock_actor(NEW.actor_id,NEW.brand_id);
  SELECT * INTO b FROM brands WHERE id=NEW.brand_id;
  SELECT * INTO s FROM strategies WHERE id=NEW.strategy_id FOR UPDATE;
  c=NEW.snapshot::jsonb;
  IF NOT EXISTS(SELECT 1 FROM recommendation_keys WHERE id=NEW.request_id AND actor_id=NEW.actor_id AND workspace_id=NEW.workspace_id AND brand_id=NEW.brand_id AND operation='GENERATE' AND key_digest=NEW.key_digest AND request_digest=NEW.request_digest)
   OR NEW.workspace_id IS DISTINCT FROM b.workspace_id OR s.brand_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM 'APPROVED'
   OR s.content_format_version IS DISTINCT FROM 2 OR s.lock_version IS DISTINCT FROM NEW.source_version
   OR NEW.captured_at<transaction_timestamp() OR NEW.captured_at>clock_timestamp() OR NEW.created_at<transaction_timestamp() OR NEW.created_at>clock_timestamp()
   OR NEW.snapshot_checksum IS DISTINCT FROM encode(sha256(convert_to(NEW.snapshot,'UTF8')),'hex')
   OR c->>'strategyId' IS DISTINCT FROM s.id::text OR(c->>'strategyVersion')::bigint IS DISTINCT FROM s.lock_version
   OR(c->>'strategyRevision')::integer IS DISTINCT FROM s.version OR c->>'strategyChecksum' IS DISTINCT FROM NEW.source_checksum
   OR c->>'brandId' IS DISTINCT FROM b.id::text OR(c->>'brandVersion')::bigint IS DISTINCT FROM b.lock_version
   OR(c->>'workspaceVersion')::bigint IS DISTINCT FROM(SELECT lock_version FROM workspaces WHERE id=b.workspace_id)
   OR c->'expected'->>'fromPillar' IS DISTINCT FROM NEW.from_pillar OR c->'expected'->>'toPillar' IS DISTINCT FROM NEW.to_pillar
   OR(c->'expected'->>'points')::integer IS DISTINCT FROM 5 OR c->'query'->>'platform' IS DISTINCT FROM NEW.platform
   OR c->'query'->>'dimension' IS DISTINCT FROM 'PILLAR' OR c->'query'->>'bucket' IS DISTINCT FROM 'PERIOD'
   OR EXISTS(SELECT 1 FROM strategies WHERE brand_id=b.id AND version>s.version)
   OR(SELECT count(*) FROM recommendation_insights WHERE brand_id=b.id)>=1000 THEN
   RAISE EXCEPTION 'Invalid recommendation source capture' USING ERRCODE='23514';END IF;
 ELSIF TG_TABLE_NAME='recommendation_metric_links' THEN
  SELECT * INTO r FROM recommendation_insights WHERE id=NEW.recommendation_id;
  PERFORM recommendation_lock_actor(r.actor_id,r.brand_id);
  IF r.created_at<transaction_timestamp() OR EXISTS(SELECT 1 FROM recommendation_resolutions WHERE recommendation_id=r.id) THEN RAISE EXCEPTION 'Published recommendation immutable' USING ERRCODE='23514';END IF;
 ELSE
  SELECT * INTO r FROM recommendation_insights WHERE id=NEW.recommendation_id;
  PERFORM recommendation_lock_actor(NEW.actor_id,r.brand_id);
  IF NOT EXISTS(SELECT 1 FROM recommendation_keys WHERE id=NEW.request_id AND actor_id=NEW.actor_id AND workspace_id=NEW.workspace_id AND brand_id=r.brand_id AND operation=NEW.action AND key_digest=NEW.key_digest AND request_digest=NEW.request_digest)
   OR NEW.workspace_id IS DISTINCT FROM r.workspace_id OR NEW.resolved_at<transaction_timestamp() OR NEW.resolved_at>clock_timestamp() THEN
   RAISE EXCEPTION 'Invalid recommendation resolution actor' USING ERRCODE='23514';END IF;
 END IF;RETURN NEW;
END $$;
CREATE TRIGGER recommendation_insight_guard BEFORE INSERT OR UPDATE OR DELETE ON recommendation_insights FOR EACH ROW EXECUTE FUNCTION guard_recommendation_history();
CREATE TRIGGER recommendation_key_guard BEFORE INSERT OR UPDATE OR DELETE ON recommendation_keys FOR EACH ROW EXECUTE FUNCTION guard_recommendation_history();
CREATE TRIGGER recommendation_link_guard BEFORE INSERT OR UPDATE OR DELETE ON recommendation_metric_links FOR EACH ROW EXECUTE FUNCTION guard_recommendation_history();
CREATE TRIGGER recommendation_resolution_guard BEFORE INSERT OR UPDATE OR DELETE ON recommendation_resolutions FOR EACH ROW EXECUTE FUNCTION guard_recommendation_history();

CREATE FUNCTION validate_recommendation_evidence(rec UUID,allowed_draft UUID DEFAULT NULL) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE r recommendation_insights%ROWTYPE;s strategies%ROWTYPE;b brands%ROWTYPE;c JSONB;item RECORD;n INTEGER;lo_n NUMERIC;lo_d NUMERIC;hi_n NUMERIC;hi_d NUMERIC;lo_count INTEGER;hi_count INTEGER;from_weight INTEGER;to_weight INTEGER;origin strategies%ROWTYPE;ident brand_identities%ROWTYPE;hops INTEGER=0;analysis UUID;
BEGIN
 SELECT * INTO r FROM recommendation_insights WHERE id=rec;SELECT * INTO s FROM strategies WHERE id=r.strategy_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=r.brand_id;c=r.snapshot::jsonb;
 IF s.status IS DISTINCT FROM 'APPROVED' OR s.lock_version IS DISTINCT FROM r.source_version OR b.status IS DISTINCT FROM 'ACTIVE'
 OR b.lock_version IS DISTINCT FROM(c->>'brandVersion')::bigint
 OR NOT EXISTS(SELECT 1 FROM workspaces WHERE id=r.workspace_id AND status='ACTIVE' AND lock_version=(c->>'workspaceVersion')::bigint)
 OR EXISTS(SELECT 1 FROM strategies WHERE brand_id=b.id AND version>s.version AND id IS DISTINCT FROM allowed_draft) THEN
 RAISE EXCEPTION 'Recommendation source changed' USING ERRCODE='23514';END IF;
 origin=s;
 WHILE origin.generation_identity_id IS NULL AND origin.revised_from_id IS NOT NULL LOOP
  hops=hops+1;IF hops>1000 THEN RAISE EXCEPTION 'Recommendation lineage limit' USING ERRCODE='23514';END IF;
  SELECT * INTO origin FROM strategies WHERE id=origin.revised_from_id AND brand_id=b.id AND version<origin.version;
  IF origin.id IS NULL THEN RAISE EXCEPTION 'Invalid recommendation lineage' USING ERRCODE='23514';END IF;
 END LOOP;
 IF origin.generation_identity_id IS NOT NULL THEN
  SELECT * INTO ident FROM brand_identities WHERE id=origin.generation_identity_id AND brand_id=b.id FOR UPDATE;
  IF ident.status IS DISTINCT FROM 'APPROVED' OR ident.version IS DISTINCT FROM origin.generation_identity_version
  OR ident.lock_version IS DISTINCT FROM origin.generation_identity_lock_version
  OR ident.approved_by IS DISTINCT FROM origin.generation_identity_approved_by OR ident.approved_at IS DISTINCT FROM origin.generation_identity_approved_at
  OR strategy_identity_checksum(ident.id) IS DISTINCT FROM origin.generation_identity_checksum
  OR EXISTS(SELECT 1 FROM brand_identities WHERE brand_id=b.id AND version>ident.version)
  OR c->'identitySource'->>'identityId' IS DISTINCT FROM ident.id::text THEN RAISE EXCEPTION 'Recommendation Identity changed' USING ERRCODE='23514';END IF;
  analysis=resolve_identity_analysis_origin(ident.analysis_attempt_id,ident.revised_from_id,b.id,ident.version);
  IF analysis IS DISTINCT FROM origin.generation_identity_analysis_id THEN RAISE EXCEPTION 'Recommendation Identity lineage changed' USING ERRCODE='23514';END IF;
  IF EXISTS(SELECT 1 FROM brand_analysis_attempts WHERE id=analysis AND requested_by IS NOT NULL) THEN PERFORM validate_identity_evidence_sources(analysis,b.id);END IF;
 ELSIF origin.generation_id IS NOT NULL OR c->'identitySource' IS DISTINCT FROM 'null'::jsonb THEN RAISE EXCEPTION 'Unverified recommendation lineage' USING ERRCODE='23514';END IF;
 SELECT count(*) INTO n FROM recommendation_metric_links WHERE recommendation_id=r.id;
 IF n NOT BETWEEN 10 AND 100 OR jsonb_array_length(c->'sources') IS DISTINCT FROM n OR jsonb_array_length(c->'expected'->'citations') IS DISTINCT FROM n THEN RAISE EXCEPTION 'Invalid recommendation sample count' USING ERRCODE='23514';END IF;
 FOR item IN SELECT l.*,o.post_id,o.observed_at,o.window_from,o.window_until,o.revision_number,o.reach,o.engagement,o.value_checksum AS actual_checksum,
 p.brand_id,p.strategy_id,p.platform,p.source_checksum,p.source_lock_version,a.observation_id AS classified_observation,a.pillar_id
 FROM recommendation_metric_links l JOIN metric_observations o ON o.id=l.observation_id JOIN content_posts p ON p.id=o.post_id JOIN metric_attributions a ON a.id=l.attribution_id WHERE l.recommendation_id=r.id LOOP
  IF item.brand_id IS DISTINCT FROM r.brand_id OR item.strategy_id IS DISTINCT FROM r.strategy_id OR item.platform IS DISTINCT FROM r.platform
   OR item.source_checksum IS DISTINCT FROM r.source_checksum OR item.source_lock_version IS DISTINCT FROM r.source_version
   OR item.window_from IS DISTINCT FROM r.window_from OR item.window_until IS DISTINCT FROM r.window_until
   OR item.reach IS NULL OR item.reach<=0 OR item.engagement IS NULL OR item.observed_at<clock_timestamp()-interval '7 days' OR item.observed_at>clock_timestamp()
   OR item.value_checksum IS DISTINCT FROM item.actual_checksum OR item.classified_observation IS DISTINCT FROM item.observation_id OR item.pillar_id IS NULL OR item.pillar_id NOT IN(r.from_pillar,r.to_pillar)
   OR EXISTS(SELECT 1 FROM metric_observations newer WHERE newer.post_id=item.post_id AND newer.observed_at=item.observed_at AND newer.revision_number>item.revision_number)
   OR EXISTS(SELECT 1 FROM metric_attributions newer WHERE newer.observation_id=item.observation_id AND newer.attribution_number>(SELECT attribution_number FROM metric_attributions WHERE id=item.attribution_id))
   OR EXISTS(SELECT 1 FROM metric_observations newer WHERE newer.post_id=item.post_id AND newer.window_from=item.window_from AND newer.window_until=item.window_until AND newer.observed_at>item.observed_at
       AND NOT EXISTS(SELECT 1 FROM metric_observations correction WHERE correction.post_id=newer.post_id AND correction.observed_at=newer.observed_at AND correction.revision_number>newer.revision_number))
   OR NOT(c->'expected'->'citations' @> to_jsonb(item.observation_id::text))
   OR NOT EXISTS(SELECT 1 FROM jsonb_array_elements(c->'sources') e WHERE e->>'observationId'=item.observation_id::text AND e->>'attributionId'=item.attribution_id::text AND e->>'valueChecksum'=item.value_checksum
      AND(e->>'reach')::bigint=item.reach AND(e->>'engagement')::bigint=item.engagement AND(e->>'observedAt')::timestamptz=item.observed_at) THEN
   RAISE EXCEPTION 'Invalid or stale recommendation evidence' USING ERRCODE='23514';END IF;
 END LOOP;
 IF(SELECT count(distinct o.post_id) FROM recommendation_metric_links l JOIN metric_observations o ON o.id=l.observation_id WHERE l.recommendation_id=r.id)<>n THEN RAISE EXCEPTION 'Duplicate recommendation Posts' USING ERRCODE='23514';END IF;
 SELECT count(*),sum(o.engagement),sum(o.reach) INTO lo_count,lo_n,lo_d FROM recommendation_metric_links l JOIN metric_observations o ON o.id=l.observation_id JOIN metric_attributions a ON a.id=l.attribution_id WHERE l.recommendation_id=r.id AND a.pillar_id=r.from_pillar;
 SELECT count(*),sum(o.engagement),sum(o.reach) INTO hi_count,hi_n,hi_d FROM recommendation_metric_links l JOIN metric_observations o ON o.id=l.observation_id JOIN metric_attributions a ON a.id=l.attribution_id WHERE l.recommendation_id=r.id AND a.pillar_id=r.to_pillar;
 SELECT p.pillar_weight INTO from_weight FROM strategy_content_pillars p JOIN strategy_playbook_pillar_ids i ON i.strategy_id=p.strategy_id AND i.entry_order=p.pillar_order WHERE p.strategy_id=s.id AND i.local_id=r.from_pillar;
 SELECT p.pillar_weight INTO to_weight FROM strategy_content_pillars p JOIN strategy_playbook_pillar_ids i ON i.strategy_id=p.strategy_id AND i.entry_order=p.pillar_order WHERE p.strategy_id=s.id AND i.local_id=r.to_pillar;
 IF lo_count<5 OR hi_count<5 OR lo_d<100 OR hi_d<100 OR from_weight<5 OR to_weight>95 OR round(100*hi_n/hi_d,4)-round(100*lo_n/lo_d,4)<2 THEN RAISE EXCEPTION 'Insufficient recommendation sample or difference' USING ERRCODE='23514';END IF;
END $$;

CREATE FUNCTION seal_recommendation_capture() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE rec UUID;d strategies%ROWTYPE;r recommendation_insights%ROWTYPE;source strategies%ROWTYPE;tab TEXT;different BOOLEAN;
BEGIN
 IF TG_TABLE_NAME='recommendation_insights' THEN rec=NEW.id;ELSE rec=NEW.recommendation_id;END IF;
 IF TG_TABLE_NAME='recommendation_resolutions' AND to_jsonb(NEW)->>'action'='APPLY' THEN
  SELECT * INTO r FROM recommendation_insights WHERE id=rec;SELECT * INTO source FROM strategies WHERE id=r.strategy_id;SELECT * INTO d FROM strategies WHERE id=NEW.draft_id;
  IF d.brand_id IS DISTINCT FROM r.brand_id OR d.revised_from_id IS DISTINCT FROM r.strategy_id OR d.status IS DISTINCT FROM 'DRAFT'
   OR d.version<>source.version+1 OR d.generation_id IS NOT NULL OR d.edited_by IS DISTINCT FROM NEW.actor_id OR d.edited_at>NEW.resolved_at
   OR ROW(d.posts_per_week,d.publishing_timezone,d.content_format_version,d.whitespace_status) IS DISTINCT FROM ROW(source.posts_per_week,source.publishing_timezone,source.content_format_version,source.whitespace_status) THEN
   RAISE EXCEPTION 'Invalid recommendation draft lineage' USING ERRCODE='23514';END IF;
  PERFORM validate_recommendation_evidence(rec,d.id);
  FOR tab IN SELECT unnest(ARRAY['strategy_best_publishing_times','strategy_playbook_pillar_ids','strategy_playbook_audience','strategy_playbook_channels','strategy_playbook_goals','strategy_playbook_tones','strategy_playbook_hypotheses','strategy_playbook_slots']) LOOP
   EXECUTE format('SELECT EXISTS((SELECT to_jsonb(t)-''strategy_id'' FROM %I t WHERE strategy_id=$1 EXCEPT SELECT to_jsonb(t)-''strategy_id'' FROM %I t WHERE strategy_id=$2) UNION ALL(SELECT to_jsonb(t)-''strategy_id'' FROM %I t WHERE strategy_id=$2 EXCEPT SELECT to_jsonb(t)-''strategy_id'' FROM %I t WHERE strategy_id=$1))',tab,tab,tab,tab) INTO different USING source.id,d.id;
   IF different THEN RAISE EXCEPTION 'Unexpected recommendation draft content change' USING ERRCODE='23514';END IF;
  END LOOP;
  IF(SELECT count(*) FROM strategy_content_pillars WHERE strategy_id=d.id)<>(SELECT count(*) FROM strategy_content_pillars WHERE strategy_id=source.id)
   OR EXISTS(SELECT 1 FROM strategy_content_pillars p JOIN strategy_playbook_pillar_ids i ON i.strategy_id=p.strategy_id AND i.entry_order=p.pillar_order LEFT JOIN strategy_content_pillars q ON q.strategy_id=d.id AND q.pillar_order=p.pillar_order
     WHERE p.strategy_id=source.id AND(ROW(q.pillar_name,q.pillar_description) IS DISTINCT FROM ROW(p.pillar_name,p.pillar_description) OR q.pillar_weight IS DISTINCT FROM p.pillar_weight+CASE i.local_id WHEN r.from_pillar THEN -5 WHEN r.to_pillar THEN 5 ELSE 0 END)) THEN
   RAISE EXCEPTION 'Invalid recommendation pillar patch' USING ERRCODE='23514';END IF;
 ELSE PERFORM validate_recommendation_evidence(rec);END IF;RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER recommendation_capture_seal AFTER INSERT ON recommendation_insights DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_recommendation_capture();
CREATE CONSTRAINT TRIGGER recommendation_resolution_seal AFTER INSERT ON recommendation_resolutions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_recommendation_capture();
