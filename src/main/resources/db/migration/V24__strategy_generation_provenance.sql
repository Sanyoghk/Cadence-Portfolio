ALTER TABLE strategies
 ADD COLUMN generation_id UUID UNIQUE,
 ADD COLUMN generation_brand_version BIGINT,
 ADD COLUMN generation_workspace_version BIGINT,
 ADD COLUMN generation_started_at TIMESTAMPTZ,
 ADD COLUMN generation_deadline TIMESTAMPTZ,
 ADD COLUMN generation_provider VARCHAR(100),
 ADD COLUMN generation_model_version VARCHAR(100),
 ADD COLUMN generation_prompt_version VARCHAR(100),
 ADD COLUMN generation_warnings VARCHAR(512),
 ADD CONSTRAINT strategy_generation_complete CHECK (
  (generation_id IS NULL AND generation_brand_version IS NULL AND generation_workspace_version IS NULL
   AND generation_started_at IS NULL AND generation_deadline IS NULL AND generation_provider IS NULL
   AND generation_model_version IS NULL AND generation_prompt_version IS NULL AND generation_warnings IS NULL)
  OR (generation_id IS NOT NULL AND generation_brand_version IS NOT NULL AND generation_brand_version>=0
   AND generation_workspace_version IS NOT NULL AND generation_workspace_version>=0
   AND generation_started_at IS NOT NULL AND generation_deadline IS NOT NULL AND generation_deadline=generation_started_at+INTERVAL '60 seconds'
   AND generation_provider IS NOT NULL AND generation_provider ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
   AND generation_model_version IS NOT NULL AND generation_model_version ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
   AND generation_prompt_version IS NOT NULL AND generation_prompt_version ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
   AND generation_warnings IS NOT NULL AND (generation_warnings='' OR generation_warnings ~ '^(DEMONSTRATION_ONLY|HUMAN_REVIEW_REQUIRED)(,(DEMONSTRATION_ONLY|HUMAN_REVIEW_REQUIRED))*$')
   AND posts_per_week BETWEEN 1 AND 21)
 );
CREATE FUNCTION guard_strategy_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE brand_version BIGINT; workspace_version BIGINT; brand_status TEXT; workspace_status TEXT; latest_version INTEGER;
BEGIN
 IF TG_OP='DELETE' THEN
  IF OLD.status<>'DRAFT' OR OLD.generation_id IS NOT NULL THEN RAISE EXCEPTION 'Strategy history cannot be deleted' USING ERRCODE='23514'; END IF;
  RETURN OLD;
 END IF;
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.id,NEW.brand_id,NEW.version,NEW.created_at,NEW.generation_id,NEW.generation_brand_version,NEW.generation_workspace_version,NEW.generation_started_at,NEW.generation_deadline,NEW.generation_provider,NEW.generation_model_version,NEW.generation_prompt_version,NEW.generation_warnings)
   IS DISTINCT FROM ROW(OLD.id,OLD.brand_id,OLD.version,OLD.created_at,OLD.generation_id,OLD.generation_brand_version,OLD.generation_workspace_version,OLD.generation_started_at,OLD.generation_deadline,OLD.generation_provider,OLD.generation_model_version,OLD.generation_prompt_version,OLD.generation_warnings)
   THEN RAISE EXCEPTION 'Strategy identity and provenance are immutable' USING ERRCODE='23514'; END IF;
  IF (OLD.status='APPROVED' AND NEW.status NOT IN ('APPROVED','LOCKED')) OR (OLD.status='LOCKED' AND NEW.status<>'LOCKED')
     OR (OLD.status='DRAFT' AND NEW.status='LOCKED')
     OR (OLD.status<>'DRAFT' AND ROW(NEW.posts_per_week,NEW.publishing_timezone) IS DISTINCT FROM ROW(OLD.posts_per_week,OLD.publishing_timezone))
   THEN RAISE EXCEPTION 'Invalid strategy transition or approved edit' USING ERRCODE='23514'; END IF;
 ELSIF NEW.generation_id IS NOT NULL THEN
  SELECT b.lock_version,w.lock_version,b.status,w.status INTO brand_version,workspace_version,brand_status,workspace_status
   FROM brands b JOIN workspaces w ON w.id=b.workspace_id WHERE b.id=NEW.brand_id;
  SELECT coalesce(max(version),0) INTO latest_version FROM strategies WHERE brand_id=NEW.brand_id;
  IF NEW.status<>'DRAFT' OR clock_timestamp()>=NEW.generation_deadline OR brand_status IS DISTINCT FROM 'ACTIVE' OR workspace_status IS DISTINCT FROM 'ACTIVE'
   OR brand_version IS DISTINCT FROM NEW.generation_brand_version OR workspace_version IS DISTINCT FROM NEW.generation_workspace_version
   OR NEW.version::BIGINT<>latest_version::BIGINT+1
  THEN RAISE EXCEPTION 'Invalid strategy generation source or version' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER strategy_revision_guard BEFORE INSERT OR UPDATE OR DELETE ON strategies FOR EACH ROW EXECUTE FUNCTION guard_strategy_revision();

CREATE FUNCTION guard_strategy_collection_edit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_id UUID; parent_status TEXT;
BEGIN
 parent_id=CASE WHEN TG_OP='DELETE' THEN OLD.strategy_id ELSE NEW.strategy_id END;
 IF TG_OP='UPDATE' AND ROW(NEW.strategy_id) IS DISTINCT FROM ROW(OLD.strategy_id) THEN RAISE EXCEPTION 'Strategy collection ownership is immutable' USING ERRCODE='23514'; END IF;
 SELECT status INTO parent_status FROM strategies WHERE id=parent_id FOR UPDATE;
 IF parent_status IS NOT NULL AND parent_status<>'DRAFT' THEN RAISE EXCEPTION 'Approved strategy collections are immutable' USING ERRCODE='23514'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER strategy_pillar_edit_guard BEFORE INSERT OR UPDATE OR DELETE ON strategy_content_pillars FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();
CREATE TRIGGER strategy_time_edit_guard BEFORE INSERT OR UPDATE OR DELETE ON strategy_best_publishing_times FOR EACH ROW EXECUTE FUNCTION guard_strategy_collection_edit();

-- Deferred checks see the complete JPA-owned collections, not an intermediate flush.
CREATE FUNCTION validate_generated_strategy_content() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_id UUID; generation UUID; zone TEXT; pillar_count INTEGER; total_weight BIGINT; unique_names INTEGER; time_count INTEGER; unique_times INTEGER;
BEGIN
 IF TG_TABLE_NAME='strategies' THEN parent_id=NEW.id;
 ELSE parent_id=CASE WHEN TG_OP='DELETE' THEN OLD.strategy_id ELSE NEW.strategy_id END; END IF;
 SELECT generation_id,publishing_timezone INTO generation,zone FROM strategies WHERE id=parent_id;
 IF generation IS NULL THEN RETURN NULL; END IF;
 SELECT count(*),coalesce(sum(pillar_weight),0),count(distinct lower(pillar_name)) INTO pillar_count,total_weight,unique_names FROM strategy_content_pillars WHERE strategy_id=parent_id;
 SELECT count(*),count(distinct publishing_time) INTO time_count,unique_times FROM strategy_best_publishing_times WHERE strategy_id=parent_id;
 IF pillar_count NOT BETWEEN 1 AND 10 OR total_weight<>100 OR unique_names<>pillar_count OR time_count NOT BETWEEN 1 AND 7 OR unique_times<>time_count
  OR EXISTS(SELECT 1 FROM strategy_best_publishing_times WHERE strategy_id=parent_id AND publishing_time IS NULL)
  OR NOT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=zone)
 THEN RAISE EXCEPTION 'Invalid generated strategy content' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER generated_strategy_content AFTER INSERT OR UPDATE ON strategies DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_generated_strategy_content();
CREATE CONSTRAINT TRIGGER generated_strategy_pillars AFTER INSERT OR UPDATE OR DELETE ON strategy_content_pillars DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_generated_strategy_content();
CREATE CONSTRAINT TRIGGER generated_strategy_times AFTER INSERT OR UPDATE OR DELETE ON strategy_best_publishing_times DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_generated_strategy_content();
