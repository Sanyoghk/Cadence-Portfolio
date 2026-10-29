-- No legacy admission/READY/citation is fabricated. Human review is independent of ingest.
CREATE TABLE evidence_admissions (
 id UUID PRIMARY KEY,
 evidence_id UUID NOT NULL REFERENCES brand_evidence(id),
 source_version BIGINT NOT NULL CHECK(source_version>=0),
 brand_version BIGINT NOT NULL CHECK(brand_version>=0),
 workspace_version BIGINT NOT NULL CHECK(workspace_version>=0),
 source_checksum VARCHAR(64) NOT NULL CHECK(source_checksum ~ '^[0-9a-f]{64}$'),
 content_checksum VARCHAR(64) NOT NULL CHECK(content_checksum ~ '^[0-9a-f]{64}$'),
 reviewed_text VARCHAR(20000) NOT NULL CHECK(char_length(reviewed_text) BETWEEN 1 AND 20000
  AND btrim(reviewed_text,U&'\0009\000A\000D\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')<>''
  AND reviewed_text !~ U&'[\0001-\0008\000B\000C\000E-\001F\007F-\009F]'),
 reviewed_by UUID NOT NULL REFERENCES cadence_users(id),
 reviewed_at TIMESTAMPTZ NOT NULL,
 policy_version VARCHAR(32) NOT NULL CHECK(policy_version='text-review-v1'),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 UNIQUE(evidence_id,source_version)
);
CREATE INDEX evidence_admission_actor_idx ON evidence_admissions(reviewed_by);

CREATE FUNCTION guard_evidence_admission() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source brand_evidence%ROWTYPE; parent brands%ROWTYPE; ws workspaces%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN
  RAISE EXCEPTION 'Evidence admission history is immutable' USING ERRCODE='23514';
 END IF;
 -- Match application Account -> Workspace -> Brand -> Evidence order.
 PERFORM 1 FROM cadence_users WHERE id=NEW.reviewed_by AND active AND role='OWNER' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Invalid evidence reviewer' USING ERRCODE='23514'; END IF;
 SELECT * INTO source FROM brand_evidence WHERE id=NEW.evidence_id;
 SELECT * INTO parent FROM brands WHERE id=source.brand_id;
 SELECT * INTO ws FROM workspaces WHERE id=parent.workspace_id FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=source.brand_id FOR UPDATE;
 SELECT * INTO source FROM brand_evidence WHERE id=NEW.evidence_id FOR UPDATE;
 IF source.id IS NULL OR parent.id IS NULL OR ws.id IS NULL OR source.brand_id<>parent.id
  OR parent.workspace_id<>ws.id OR source.type<>'TEXT'
  OR source.status<>'PENDING' OR parent.status<>'ACTIVE' OR ws.status<>'ACTIVE'
  OR source.lock_version<>NEW.source_version OR parent.lock_version<>NEW.brand_version
  OR ws.lock_version<>NEW.workspace_version
  OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=NEW.reviewed_by AND workspace_id=ws.id)
  OR NEW.reviewed_text IS DISTINCT FROM source.source_reference
  OR NEW.source_checksum IS DISTINCT FROM source.checksum
  OR NEW.source_checksum IS DISTINCT FROM encode(sha256(convert_to(E'TEXT\n'||NEW.reviewed_text,'UTF8')),'hex')
  OR NEW.content_checksum IS DISTINCT FROM encode(sha256(convert_to(NEW.reviewed_text,'UTF8')),'hex')
  OR NEW.reviewed_at<source.captured_at OR NEW.reviewed_at>clock_timestamp()+interval '5 seconds' THEN
  RAISE EXCEPTION 'Invalid evidence admission source or review' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER evidence_admission_guard BEFORE INSERT OR UPDATE OR DELETE ON evidence_admissions
 FOR EACH ROW EXECUTE FUNCTION guard_evidence_admission();
