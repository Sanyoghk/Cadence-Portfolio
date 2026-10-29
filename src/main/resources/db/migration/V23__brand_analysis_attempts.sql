CREATE TABLE brand_analysis_attempts (
 id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK (lock_version >= 0),
 brand_id UUID NOT NULL REFERENCES brands(id),
 brand_version BIGINT NOT NULL CHECK (brand_version >= 0),
 workspace_version BIGINT NOT NULL CHECK (workspace_version >= 0),
 source_checksum VARCHAR(64) NOT NULL CHECK (source_checksum ~ '^[0-9a-f]{64}$'),
 started_at TIMESTAMPTZ NOT NULL, deadline TIMESTAMPTZ NOT NULL,
 completed_at TIMESTAMPTZ, status VARCHAR(16) NOT NULL,
 provider VARCHAR(100), model_version VARCHAR(100), prompt_version VARCHAR(100),
 warnings VARCHAR(512), output_checksum VARCHAR(64),
 CHECK (deadline = started_at + INTERVAL '60 seconds'),
 CHECK ((status = 'RUNNING' AND completed_at IS NULL) OR
        (status IN ('SUCCEEDED','FAILED','SUPERSEDED') AND completed_at IS NOT NULL AND completed_at >= started_at)),
 CHECK ((status = 'SUCCEEDED' AND completed_at < deadline
         AND provider IS NOT NULL AND provider ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
         AND model_version IS NOT NULL AND model_version ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
         AND prompt_version IS NOT NULL AND prompt_version ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
         AND warnings IS NOT NULL AND (warnings = '' OR warnings ~ '^(DEMONSTRATION_ONLY|DECLARED_FIELDS_ONLY|HUMAN_REVIEW_REQUIRED)(,(DEMONSTRATION_ONLY|DECLARED_FIELDS_ONLY|HUMAN_REVIEW_REQUIRED))*$')
         AND output_checksum IS NOT NULL AND output_checksum ~ '^[0-9a-f]{64}$')
        OR (status <> 'SUCCEEDED' AND provider IS NULL AND model_version IS NULL
            AND prompt_version IS NULL AND warnings IS NULL AND output_checksum IS NULL))
);
CREATE UNIQUE INDEX brand_analysis_one_running ON brand_analysis_attempts(brand_id) WHERE status='RUNNING';
CREATE INDEX brand_analysis_history ON brand_analysis_attempts(brand_id,started_at DESC);
CREATE FUNCTION guard_brand_analysis_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE current_brand_version BIGINT; current_workspace_version BIGINT; brand_status TEXT; workspace_status TEXT;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Analysis receipts are immutable' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF OLD.status <> 'RUNNING' OR NEW.status='RUNNING' OR
     ROW(NEW.id,NEW.brand_id,NEW.brand_version,NEW.workspace_version,NEW.source_checksum,NEW.started_at,NEW.deadline,NEW.created_at)
     IS DISTINCT FROM ROW(OLD.id,OLD.brand_id,OLD.brand_version,OLD.workspace_version,OLD.source_checksum,OLD.started_at,OLD.deadline,OLD.created_at)
  THEN RAISE EXCEPTION 'Invalid analysis transition' USING ERRCODE='23514'; END IF;
 ELSE
  IF NEW.status <> 'RUNNING' THEN RAISE EXCEPTION 'Analysis must start running' USING ERRCODE='23514'; END IF;
 END IF;
 IF NEW.status='SUCCEEDED' AND clock_timestamp() >= NEW.deadline THEN
  RAISE EXCEPTION 'Analysis deadline expired' USING ERRCODE='23514';
 END IF;
 IF TG_OP='INSERT' OR NEW.status='SUCCEEDED' THEN
  SELECT b.lock_version,w.lock_version,b.status,w.status INTO current_brand_version,current_workspace_version,brand_status,workspace_status
    FROM brands b JOIN workspaces w ON w.id=b.workspace_id WHERE b.id=NEW.brand_id;
  IF brand_status IS DISTINCT FROM 'ACTIVE' OR workspace_status IS DISTINCT FROM 'ACTIVE'
     OR current_workspace_version IS DISTINCT FROM NEW.workspace_version
     OR current_brand_version IS DISTINCT FROM (NEW.brand_version + CASE WHEN NEW.status='SUCCEEDED' THEN 1 ELSE 0 END)
  THEN RAISE EXCEPTION 'Analysis source changed' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER brand_analysis_attempt_guard BEFORE INSERT OR UPDATE OR DELETE ON brand_analysis_attempts
 FOR EACH ROW EXECUTE FUNCTION guard_brand_analysis_attempt();
