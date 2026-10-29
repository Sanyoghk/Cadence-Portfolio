-- No backfill of context for existing observations. Human classification is a new audited action.
CREATE TABLE metric_attributions(
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 observation_id UUID NOT NULL REFERENCES metric_observations(id),revision_id UUID NOT NULL REFERENCES post_revisions(id),
 attribution_number INTEGER NOT NULL CHECK(attribution_number BETWEEN 1 AND 100),previous_id UUID UNIQUE REFERENCES metric_attributions(id),
 revision_version BIGINT NOT NULL CHECK(revision_version>=0),content_checksum VARCHAR(64) NOT NULL CHECK(content_checksum ~ '^[0-9a-f]{64}$'),
 pillar_id VARCHAR(64),pillar_name VARCHAR(255),pillar_origin VARCHAR(16) NOT NULL CHECK(pillar_origin IN('UNKNOWN','HUMAN','OUTLINE')),
 actor_id UUID NOT NULL REFERENCES cadence_users(id),attributed_at TIMESTAMPTZ(6) NOT NULL,
 CHECK((pillar_origin='UNKNOWN' AND pillar_id IS NULL AND pillar_name IS NULL) OR(pillar_origin IN('HUMAN','OUTLINE') AND pillar_id IS NOT NULL AND pillar_name IS NOT NULL)),
 CHECK((attribution_number=1 AND previous_id IS NULL) OR(attribution_number>1 AND previous_id IS NOT NULL)),UNIQUE(observation_id,attribution_number)
);
CREATE INDEX metric_attribution_revision_idx ON metric_attributions(revision_id,observation_id);
CREATE INDEX metric_observation_window_idx ON metric_observations(post_id,window_from,window_until,observed_at,revision_number);
CREATE FUNCTION guard_metric_attribution() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE o metric_observations%ROWTYPE;p content_posts%ROWTYPE;r post_revisions%ROWTYPE;b brands%ROWTYPE;w workspaces%ROWTYPE;derived TEXT;label TEXT;prior metric_attributions%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Metrics attribution history immutable' USING ERRCODE='23514';END IF;
 SELECT * INTO o FROM metric_observations WHERE id=NEW.observation_id;
 SELECT * INTO p FROM content_posts WHERE id=o.post_id;
 PERFORM 1 FROM cadence_users WHERE id=NEW.actor_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=p.brand_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=p.brand_id FOR UPDATE;
 SELECT * INTO p FROM content_posts WHERE id=o.post_id FOR UPDATE;
 PERFORM post_require_actor(NEW.actor_id,w.id);
 SELECT * INTO r FROM post_revisions WHERE id=NEW.revision_id;
 SELECT * INTO prior FROM metric_attributions WHERE observation_id=NEW.observation_id ORDER BY attribution_number DESC LIMIT 1;
 IF(prior.id IS NULL AND(NEW.attribution_number<>1 OR NEW.previous_id IS NOT NULL))
 OR(prior.id IS NOT NULL AND(NEW.previous_id IS DISTINCT FROM prior.id OR NEW.attribution_number<>prior.attribution_number+1 OR NEW.attributed_at<prior.attributed_at))
 OR(SELECT count(*) FROM metric_attributions a JOIN metric_observations m ON m.id=a.observation_id JOIN content_posts cp ON cp.id=m.post_id WHERE cp.brand_id=p.brand_id)>=100000 THEN
 RAISE EXCEPTION 'Invalid attribution lineage or quota' USING ERRCODE='23514';END IF;
 IF b.status IS DISTINCT FROM 'ACTIVE' OR w.status IS DISTINCT FROM 'ACTIVE' OR p.legacy OR NEW.lock_version<>0
 OR r.post_id IS DISTINCT FROM o.post_id OR r.status IS DISTINCT FROM 'APPROVED' OR r.approved_at>o.observed_at
 OR r.lock_version IS DISTINCT FROM NEW.revision_version OR NEW.content_checksum IS DISTINCT FROM post_schedule_checksum(r.id)
 OR NEW.attributed_at<transaction_timestamp() OR NEW.attributed_at>clock_timestamp()
 OR EXISTS(SELECT 1 FROM metric_observations n WHERE n.post_id=o.post_id AND n.observed_at=o.observed_at AND n.revision_number>o.revision_number) THEN
 RAISE EXCEPTION 'Invalid metrics attribution source or actor' USING ERRCODE='23514';END IF;
 SELECT pillar_id INTO derived FROM strategy_playbook_slots WHERE strategy_id=p.strategy_id AND slot_id=p.outline_slot_id;
 IF p.outline_slot_id IS NOT NULL THEN
  IF derived IS NULL OR NEW.pillar_id IS DISTINCT FROM derived OR NEW.pillar_origin<>'OUTLINE' THEN RAISE EXCEPTION 'Metrics outline mismatch' USING ERRCODE='23514';END IF;
 ELSIF (NEW.pillar_id IS NULL AND NEW.pillar_origin<>'UNKNOWN') OR(NEW.pillar_id IS NOT NULL AND NEW.pillar_origin<>'HUMAN') THEN
 RAISE EXCEPTION 'Invalid human pillar origin' USING ERRCODE='23514';END IF;
 IF NEW.pillar_id IS NOT NULL THEN
  SELECT c.pillar_name INTO label FROM strategy_playbook_pillar_ids i JOIN strategy_content_pillars c ON c.strategy_id=i.strategy_id AND c.pillar_order=i.entry_order WHERE i.strategy_id=p.strategy_id AND i.local_id=NEW.pillar_id;
  IF label IS NULL OR NEW.pillar_name IS DISTINCT FROM label THEN RAISE EXCEPTION 'Foreign metrics pillar' USING ERRCODE='23514';END IF;
 END IF;RETURN NEW;
END $$;
CREATE TRIGGER metric_attribution_guard BEFORE INSERT OR UPDATE OR DELETE ON metric_attributions FOR EACH ROW EXECUTE FUNCTION guard_metric_attribution();
