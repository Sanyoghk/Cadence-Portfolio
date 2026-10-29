-- Legacy receipts remain unchanged; no actor, source or citation is invented.
ALTER TABLE brand_analysis_attempts
 ADD requested_by UUID REFERENCES cadence_users(id),
 ADD input_brand_name VARCHAR(255), ADD input_industry VARCHAR(255),
 ADD CONSTRAINT analysis_input_mode CHECK (
  (requested_by IS NULL AND input_brand_name IS NULL AND input_industry IS NULL) OR
  (requested_by IS NOT NULL AND input_brand_name IS NOT NULL AND input_industry IS NOT NULL
   AND length(input_brand_name)>0 AND length(input_industry)>0
   AND input_brand_name !~ '[[:cntrl:]]' AND input_industry !~ '[[:cntrl:]]'));

CREATE TABLE brand_analysis_sources (
 id UUID PRIMARY KEY, attempt_id UUID NOT NULL REFERENCES brand_analysis_attempts(id),
 admission_id UUID NOT NULL REFERENCES evidence_admissions(id),
 position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 4),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 UNIQUE(attempt_id,position), UNIQUE(attempt_id,admission_id)
);
CREATE TABLE brand_analysis_citations (
 id UUID PRIMARY KEY, attempt_id UUID NOT NULL, admission_id UUID NOT NULL,
 position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 9),
 quote VARCHAR(500) NOT NULL CHECK(length(quote)>0 AND octet_length(quote)<=1000
  AND btrim(quote,U&'\0009\000A\000D\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')<>''),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0),
 FOREIGN KEY(attempt_id,admission_id) REFERENCES brand_analysis_sources(attempt_id,admission_id),
 UNIQUE(attempt_id,position), UNIQUE(attempt_id,admission_id,quote)
);

-- Invoker function: acquire reviewed Account -> Workspace -> Brand -> Evidence order.
CREATE FUNCTION validate_analysis_admission(attempt UUID, admission UUID, publication BOOLEAN)
 RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE a brand_analysis_attempts%ROWTYPE; r evidence_admissions%ROWTYPE;
 b brands%ROWTYPE; w workspaces%ROWTYPE; e brand_evidence%ROWTYPE;
BEGIN
 SELECT * INTO a FROM brand_analysis_attempts WHERE id=attempt;
 PERFORM 1 FROM cadence_users WHERE id=a.requested_by AND active AND role='OWNER' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Analysis owner invalid' USING ERRCODE='23514'; END IF;
 SELECT * INTO b FROM brands WHERE id=a.brand_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=a.brand_id FOR UPDATE;
 SELECT * INTO r FROM evidence_admissions WHERE id=admission;
 SELECT * INTO e FROM brand_evidence WHERE id=r.evidence_id FOR UPDATE;
 IF a.status<>'RUNNING' OR clock_timestamp()>=a.deadline OR r.id IS NULL OR e.id IS NULL
  OR e.brand_id<>b.id OR b.status<>'ACTIVE' OR w.status<>'ACTIVE'
  OR b.lock_version<>(a.brand_version+CASE WHEN publication THEN 1 ELSE 0 END)
  OR w.lock_version<>a.workspace_version OR r.brand_version<>a.brand_version
  OR r.workspace_version<>a.workspace_version OR r.source_version<>e.lock_version
  OR e.type<>'TEXT' OR e.status<>'PENDING' OR r.policy_version<>'text-review-v1'
  OR r.reviewed_text IS DISTINCT FROM e.source_reference OR r.source_checksum IS DISTINCT FROM e.checksum
  OR r.source_checksum<>encode(sha256(convert_to(E'TEXT\n'||r.reviewed_text,'UTF8')),'hex')
  OR r.content_checksum<>encode(sha256(convert_to(r.reviewed_text,'UTF8')),'hex')
  OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=a.requested_by AND workspace_id=w.id)
  OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals x JOIN evidence_admissions y ON y.id=x.admission_id
    WHERE y.evidence_id=e.id AND y.source_version=r.source_version)
 THEN RAISE EXCEPTION 'Analysis admission invalid' USING ERRCODE='23514'; END IF;
END $$;

CREATE FUNCTION guard_analysis_evidence_child() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Analysis evidence history immutable' USING ERRCODE='23514'; END IF;
 PERFORM validate_analysis_admission(NEW.attempt_id,NEW.admission_id,FALSE);
 IF TG_TABLE_NAME='brand_analysis_citations' THEN
  IF NOT EXISTS(SELECT 1 FROM evidence_admissions a WHERE a.id=NEW.admission_id AND strpos(a.reviewed_text,NEW.quote)>0)
  THEN RAISE EXCEPTION 'Analysis quote invalid' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER analysis_source_guard BEFORE INSERT OR UPDATE OR DELETE ON brand_analysis_sources
 FOR EACH ROW EXECUTE FUNCTION guard_analysis_evidence_child();
CREATE TRIGGER analysis_citation_guard BEFORE INSERT OR UPDATE OR DELETE ON brand_analysis_citations
 FOR EACH ROW EXECUTE FUNCTION guard_analysis_evidence_child();

CREATE FUNCTION guard_analysis_evidence_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r RECORD;
BEGIN
 IF TG_OP='UPDATE' AND ROW(NEW.requested_by,NEW.input_brand_name,NEW.input_industry)
  IS DISTINCT FROM ROW(OLD.requested_by,OLD.input_brand_name,OLD.input_industry)
 THEN RAISE EXCEPTION 'Analysis input immutable' USING ERRCODE='23514'; END IF;
 IF NEW.requested_by IS NOT NULL AND (TG_OP='INSERT' OR NEW.status='SUCCEEDED') THEN
  PERFORM 1 FROM cadence_users WHERE id=NEW.requested_by AND active AND role='OWNER' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Analysis owner invalid' USING ERRCODE='23514'; END IF;
  IF NOT EXISTS(SELECT 1 FROM brands b JOIN workspace_memberships m ON m.workspace_id=b.workspace_id
   WHERE b.id=NEW.brand_id AND m.user_id=NEW.requested_by)
  THEN RAISE EXCEPTION 'Analysis membership invalid' USING ERRCODE='23514'; END IF;
  IF NEW.status='SUCCEEDED' THEN
   FOR r IN SELECT admission_id FROM brand_analysis_sources WHERE attempt_id=NEW.id ORDER BY position LOOP
    PERFORM validate_analysis_admission(NEW.id,r.admission_id,TRUE);
   END LOOP;
   IF NOT EXISTS(SELECT 1 FROM brand_analysis_citations WHERE attempt_id=NEW.id)
    OR NEW.warnings LIKE '%DECLARED_FIELDS_ONLY%' OR NEW.warnings NOT LIKE '%HUMAN_REVIEW_REQUIRED%'
   THEN RAISE EXCEPTION 'Analysis citations required' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER analysis_evidence_attempt_guard BEFORE INSERT OR UPDATE ON brand_analysis_attempts
 FOR EACH ROW EXECUTE FUNCTION guard_analysis_evidence_attempt();

-- Deferred validation observes the entire input after the JPA child inserts.
CREATE FUNCTION seal_analysis_evidence_input() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a brand_analysis_attempts%ROWTYPE; n INTEGER; bytes BIGINT; digest TEXT; parent UUID;
BEGIN
 IF TG_TABLE_NAME='brand_analysis_attempts' THEN parent=NEW.id; ELSE parent=NEW.attempt_id; END IF;
 SELECT * INTO a FROM brand_analysis_attempts WHERE id=parent;
 SELECT count(*),coalesce(sum(octet_length(r.reviewed_text)),0),
  encode(sha256(convert_to(E'text-evidence-v1\n'||a.input_brand_name||E'\n'||a.input_industry||
   string_agg(E'\n'||r.id::TEXT||E'\n'||r.evidence_id::TEXT||E'\n'||r.source_version::TEXT||E'\n'||r.content_checksum,'' ORDER BY s.position),'UTF8')),'hex')
 INTO n,bytes,digest FROM brand_analysis_sources s JOIN evidence_admissions r ON r.id=s.admission_id WHERE s.attempt_id=parent;
 IF a.requested_by IS NULL THEN
  IF n<>0 THEN RAISE EXCEPTION 'Legacy analysis cannot acquire sources' USING ERRCODE='23514'; END IF;
 ELSE
  IF n NOT BETWEEN 1 AND 5 OR bytes>20000 OR digest IS DISTINCT FROM a.source_checksum
   OR (SELECT count(DISTINCT r.evidence_id) FROM brand_analysis_sources s JOIN evidence_admissions r ON r.id=s.admission_id WHERE s.attempt_id=parent)<>n
  THEN RAISE EXCEPTION 'Invalid bounded analysis input' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER analysis_input_seal AFTER INSERT OR UPDATE ON brand_analysis_attempts
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_analysis_evidence_input();
CREATE CONSTRAINT TRIGGER analysis_sources_seal AFTER INSERT ON brand_analysis_sources
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_analysis_evidence_input();

-- Evidence -> Identity provenance/withdrawal integration is a separate qualification.
-- Fail closed rather than let the legacy copy action lose the source review boundary.
CREATE FUNCTION guard_identity_evidence_analysis_mode() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.analysis_attempt_id IS NOT NULL AND EXISTS(
  SELECT 1 FROM brand_analysis_attempts WHERE id=NEW.analysis_attempt_id AND requested_by IS NOT NULL)
 THEN RAISE EXCEPTION 'Evidence identity source integration unavailable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER identity_evidence_analysis_mode_guard BEFORE INSERT ON brand_identities
 FOR EACH ROW EXECUTE FUNCTION guard_identity_evidence_analysis_mode();
