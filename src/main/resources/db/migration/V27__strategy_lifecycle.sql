-- Existing approved history has no reconstructed actor/time. New approvals must have both.
ALTER TABLE strategies
 ADD COLUMN revised_from_id UUID REFERENCES strategies(id),
 ADD COLUMN approved_by UUID REFERENCES cadence_users(id),
 ADD COLUMN approved_at TIMESTAMPTZ,
 ADD CONSTRAINT strategy_approval_audit CHECK (
   (approved_by IS NULL AND approved_at IS NULL) OR
   (approved_by IS NOT NULL AND approved_at IS NOT NULL AND status IN ('APPROVED','LOCKED'))),
 ADD CONSTRAINT strategy_revision_not_self CHECK (revised_from_id IS NULL OR revised_from_id<>id);
CREATE INDEX strategy_revision_source_idx ON strategies(revised_from_id) WHERE revised_from_id IS NOT NULL;
CREATE INDEX strategy_approval_actor_idx ON strategies(approved_by) WHERE approved_by IS NOT NULL;

CREATE FUNCTION guard_strategy_lifecycle() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent strategies%ROWTYPE; ws_id UUID; latest INTEGER;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF NEW.revised_from_id IS DISTINCT FROM OLD.revised_from_id THEN
   RAISE EXCEPTION 'Strategy lineage is immutable' USING ERRCODE='23514';
  END IF;
  IF OLD.status<>'DRAFT' AND ROW(NEW.approved_by,NEW.approved_at) IS DISTINCT FROM ROW(OLD.approved_by,OLD.approved_at) THEN
   RAISE EXCEPTION 'Approval audit is immutable' USING ERRCODE='23514';
  END IF;
 ELSIF NEW.status<>'DRAFT' OR NEW.approved_by IS NOT NULL OR NEW.approved_at IS NOT NULL THEN
  RAISE EXCEPTION 'Strategy must start as a draft' USING ERRCODE='23514';
 END IF;
 IF TG_OP='INSERT' AND NEW.revised_from_id IS NOT NULL THEN
  -- New roots can acquire parents first, including direct SQL callers.
  SELECT b.workspace_id INTO ws_id FROM brands b WHERE b.id=NEW.brand_id;
  PERFORM 1 FROM workspaces WHERE id=ws_id AND status='ACTIVE' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Archived strategy parent' USING ERRCODE='23514'; END IF;
  -- Brand lock serializes revision allocation with generation finalization.
  PERFORM 1 FROM brands WHERE id=NEW.brand_id AND status='ACTIVE' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Archived strategy parent' USING ERRCODE='23514'; END IF;
  SELECT * INTO parent FROM strategies WHERE id=NEW.revised_from_id;
  SELECT coalesce(max(version),0) INTO latest FROM strategies WHERE brand_id=NEW.brand_id;
  IF parent.id IS NULL OR parent.brand_id<>NEW.brand_id OR parent.status='DRAFT'
   OR NEW.version::BIGINT<>latest::BIGINT+1 OR NEW.generation_id IS NOT NULL THEN
   RAISE EXCEPTION 'Invalid strategy lineage' USING ERRCODE='23514';
  END IF;
 END IF;
 IF TG_OP='UPDATE' AND OLD.status='DRAFT' AND NEW.status='APPROVED' THEN
  SELECT b.workspace_id INTO ws_id FROM brands b JOIN workspaces w ON w.id=b.workspace_id
   WHERE b.id=NEW.brand_id AND b.status='ACTIVE' AND w.status='ACTIVE';
  IF NEW.approved_by IS NULL OR NEW.approved_at IS NULL OR NEW.approved_at<NEW.created_at
   OR ws_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM cadence_users u JOIN workspace_memberships m ON m.user_id=u.id
     WHERE u.id=NEW.approved_by AND u.active AND u.role='OWNER' AND m.workspace_id=ws_id) THEN
   RAISE EXCEPTION 'Invalid strategy approval actor or parent' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER strategy_lifecycle_guard BEFORE INSERT OR UPDATE ON strategies
 FOR EACH ROW EXECUTE FUNCTION guard_strategy_lifecycle();

-- Keep legacy history readable. Generation, revision and new approval require complete content.
CREATE OR REPLACE FUNCTION validate_generated_strategy_content() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_id UUID; managed BOOLEAN; zone TEXT; posts INTEGER; pillar_count INTEGER;
 total_weight BIGINT; unique_names INTEGER; time_count INTEGER; unique_times INTEGER;
BEGIN
 IF TG_TABLE_NAME='strategies' THEN parent_id=NEW.id;
 ELSE parent_id=CASE WHEN TG_OP='DELETE' THEN OLD.strategy_id ELSE NEW.strategy_id END; END IF;
 SELECT generation_id IS NOT NULL OR revised_from_id IS NOT NULL OR approved_by IS NOT NULL,
  publishing_timezone,posts_per_week INTO managed,zone,posts FROM strategies WHERE id=parent_id;
 IF managed IS DISTINCT FROM TRUE THEN RETURN NULL; END IF;
 SELECT count(*),coalesce(sum(pillar_weight),0),count(distinct lower(pillar_name))
  INTO pillar_count,total_weight,unique_names FROM strategy_content_pillars WHERE strategy_id=parent_id;
 SELECT count(*),count(distinct publishing_time) INTO time_count,unique_times
  FROM strategy_best_publishing_times WHERE strategy_id=parent_id;
 IF posts NOT BETWEEN 1 AND 21 OR pillar_count NOT BETWEEN 1 AND 10 OR total_weight<>100
  OR unique_names<>pillar_count OR time_count NOT BETWEEN 1 AND 7 OR unique_times<>time_count
  OR EXISTS(SELECT 1 FROM strategy_best_publishing_times WHERE strategy_id=parent_id AND publishing_time IS NULL)
  OR EXISTS(SELECT 1 FROM strategy_content_pillars WHERE strategy_id=parent_id AND (
    pillar_name IS NULL OR btrim(pillar_name)='' OR pillar_name<>btrim(pillar_name)
    OR pillar_description IS NULL OR btrim(pillar_description)='' OR pillar_description<>btrim(pillar_description)
    OR pillar_name ~ '[[:cntrl:]]' OR pillar_description ~ '[[:cntrl:]]'
    OR pillar_weight NOT BETWEEN 0 AND 100))
  OR NOT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=zone) THEN
  RAISE EXCEPTION 'Invalid strategy content' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END $$;
