-- Versioned structured citations; existing receipts and digest semantics are unchanged.
ALTER TABLE brand_analysis_citations ADD field_path VARCHAR(32);
ALTER TABLE brand_analysis_citations ADD CONSTRAINT citation_field_path CHECK(field_path IS NULL OR field_path IN ('title','description','text'));
ALTER TABLE brand_analysis_citations DROP CONSTRAINT brand_analysis_citations_attempt_id_admission_id_quote_key;
CREATE UNIQUE INDEX analysis_citation_exact_unique ON brand_analysis_citations(attempt_id,admission_id,coalesce(field_path,''),quote);
CREATE FUNCTION validate_url_analysis_source(admission UUID) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE r evidence_admissions%ROWTYPE; source brand_evidence%ROWTYPE; b brands%ROWTYPE; w workspaces%ROWTYPE;
 url url_text_snapshots%ROWTYPE; latest evidence_scan_results%ROWTYPE; obj quarantine_objects%ROWTYPE;
BEGIN
 SELECT * INTO r FROM evidence_admissions WHERE id=admission;
 SELECT * INTO source FROM brand_evidence WHERE id=r.evidence_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=source.brand_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id;
  SELECT * INTO url FROM url_text_snapshots WHERE id=r.url_snapshot_id;
  SELECT * INTO latest FROM evidence_scan_results WHERE evidence_id=source.id ORDER BY scanned_at DESC,created_at DESC,id DESC LIMIT 1;
  SELECT * INTO obj FROM quarantine_objects WHERE evidence_id=source.id AND brand_id=b.id AND workspace_id=w.id
   AND 'quarantine:'||object_key=source.fetched_reference FOR UPDATE;
  IF source.type<>'URL' OR url.id IS NULL OR latest.id IS NULL OR obj.id IS NULL
   OR url.evidence_id<>source.id OR url.source_version<>source.lock_version
   OR url.source_checksum IS DISTINCT FROM source.checksum OR url.fetched_checksum IS DISTINCT FROM source.fetched_checksum
   OR url.storage_reference IS DISTINCT FROM source.fetched_reference
   OR url.fetched_at IS DISTINCT FROM source.fetched_at OR url.extracted_at IS DISTINCT FROM source.extracted_at
   OR url.captured_at IS DISTINCT FROM source.captured_at
   OR url.media_type IS DISTINCT FROM source.fetched_media_type OR url.extraction_version IS DISTINCT FROM source.extraction_version
   OR url.warnings IS DISTINCT FROM source.extraction_warnings
   OR url.payload IS DISTINCT FROM url_text_projection(source.extracted_title,source.extracted_description,source.extracted_text)
   OR r.reviewed_text IS DISTINCT FROM url.payload OR r.content_checksum IS DISTINCT FROM url.content_checksum
   OR btrim(source.extracted_title||source.extracted_description||source.extracted_text)=''
   OR source.checksum IS DISTINCT FROM encode(sha256(convert_to(E'URL\n'||source.source_reference,'UTF8')),'hex')
   OR NOT EXISTS(SELECT 1 FROM jobs WHERE id=url.fetch_job_id AND status='SUCCEEDED' AND attempts=url.fetch_attempt)
   OR r.original_scan_id IS DISTINCT FROM latest.id OR r.latest_scan_id IS DISTINCT FROM latest.id
   OR latest.source_version<>source.lock_version OR latest.checksum IS DISTINCT FROM source.fetched_checksum
   OR latest.verdict<>'CLEAN' OR latest.scanned_at>clock_timestamp() OR latest.scanned_at<source.fetched_at
   OR latest.scanner_engine='legacy-unknown' OR latest.signature_version='legacy-unknown'
   OR r.scan_expires_at<=clock_timestamp() OR r.reviewed_at>=r.scan_expires_at
   OR r.scan_expires_at<latest.scanned_at+interval '1 second' OR r.scan_expires_at>latest.scanned_at+interval '7 days'
   OR obj.state<>'REFERENCED' OR obj.quota_released OR obj.deleted_at IS NOT NULL OR obj.retained_bytes NOT BETWEEN 1 AND 2097152
   OR obj.object_key<>'quarantine/writes/'||w.id::text||'/'||obj.id::text||'.html' THEN
   RAISE EXCEPTION 'Invalid URL admission' USING ERRCODE='23514';
  END IF;
END $$;
CREATE OR REPLACE FUNCTION validate_analysis_admission(attempt UUID, admission UUID, publication BOOLEAN)
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
  OR (CASE WHEN r.policy_version='url-text-review-v1' THEN e.status<>'FETCHED' ELSE e.status<>'PENDING' END)
  OR r.source_checksum IS DISTINCT FROM e.checksum
  OR r.content_checksum<>encode(sha256(convert_to(r.reviewed_text,'UTF8')),'hex')
  OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=a.requested_by AND workspace_id=w.id)
  OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals x JOIN evidence_admissions y ON y.id=x.admission_id
    WHERE y.evidence_id=e.id AND y.source_version=r.source_version)
 THEN RAISE EXCEPTION 'Analysis admission invalid' USING ERRCODE='23514'; END IF;
 IF r.policy_version='text-review-v1' THEN
  IF e.type<>'TEXT' OR r.reviewed_text IS DISTINCT FROM e.source_reference
   OR r.source_checksum<>encode(sha256(convert_to(E'TEXT\n'||r.reviewed_text,'UTF8')),'hex') THEN
   RAISE EXCEPTION 'Text analysis admission invalid' USING ERRCODE='23514';
  END IF;
 ELSIF r.policy_version='pdf-text-review-v1' THEN
  PERFORM validate_pdf_analysis_source(r.id);
 ELSIF r.policy_version='url-text-review-v1' THEN
  PERFORM validate_url_analysis_source(r.id);
 ELSE
  RAISE EXCEPTION 'Analysis admission policy invalid' USING ERRCODE='23514';
 END IF;

END $$;
-- Extend metadata guards only; external bytes remain application-verified. No history rewrite.
CREATE OR REPLACE FUNCTION validate_identity_evidence_sources(analysis UUID,parent_id UUID)
 RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE a brand_analysis_attempts%ROWTYPE; b brands%ROWTYPE; w workspaces%ROWTYPE;
 r RECORD; e brand_evidence%ROWTYPE; n INTEGER; bytes BIGINT;
BEGIN
 SELECT * INTO a FROM brand_analysis_attempts WHERE id=analysis;
 SELECT * INTO b FROM brands WHERE id=parent_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=parent_id FOR UPDATE;
 IF a.id IS NULL OR a.requested_by IS NULL OR a.status<>'SUCCEEDED' OR a.brand_id<>b.id
  OR b.status<>'ACTIVE' OR w.status<>'ACTIVE' OR b.lock_version<>a.brand_version+1
  OR w.lock_version<>a.workspace_version
 THEN RAISE EXCEPTION 'Evidence identity source changed' USING ERRCODE='23514'; END IF;
 SELECT count(*),coalesce(sum(octet_length(ad.reviewed_text)),0) INTO n,bytes
  FROM brand_analysis_sources s JOIN evidence_admissions ad ON ad.id=s.admission_id WHERE s.attempt_id=analysis;
 IF n NOT BETWEEN 1 AND 5 OR bytes>20000
  OR (SELECT count(*) FROM brand_analysis_citations WHERE attempt_id=analysis) NOT BETWEEN 1 AND 10
 THEN RAISE EXCEPTION 'Invalid identity evidence graph' USING ERRCODE='23514'; END IF;
 FOR r IN SELECT x.* FROM brand_analysis_sources s JOIN evidence_admissions x ON x.id=s.admission_id
  WHERE s.attempt_id=analysis ORDER BY x.evidence_id LOOP
  SELECT * INTO e FROM brand_evidence WHERE id=r.evidence_id FOR UPDATE;
  IF e.id IS NULL OR e.brand_id<>b.id OR (CASE WHEN r.policy_version='url-text-review-v1' THEN e.status<>'FETCHED' ELSE e.status<>'PENDING' END)
   OR e.lock_version<>r.source_version OR r.brand_version<>a.brand_version
   OR r.workspace_version<>a.workspace_version
   OR e.checksum IS DISTINCT FROM r.source_checksum
   OR r.content_checksum<>encode(sha256(convert_to(r.reviewed_text,'UTF8')),'hex')
   OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals x JOIN evidence_admissions y ON y.id=x.admission_id
    WHERE y.evidence_id=e.id AND y.source_version=r.source_version)
  THEN RAISE EXCEPTION 'Evidence identity source changed' USING ERRCODE='23514'; END IF;
  IF r.policy_version='text-review-v1' THEN
   IF e.type<>'TEXT' OR e.source_reference IS DISTINCT FROM r.reviewed_text
    OR r.source_checksum<>encode(sha256(convert_to(E'TEXT\n'||r.reviewed_text,'UTF8')),'hex')
   THEN RAISE EXCEPTION 'Invalid text identity source' USING ERRCODE='23514'; END IF;
  ELSIF r.policy_version='pdf-text-review-v1' THEN
   PERFORM validate_pdf_analysis_source(r.id);
  ELSIF r.policy_version='url-text-review-v1' THEN
   PERFORM validate_url_analysis_source(r.id);
  ELSE RAISE EXCEPTION 'Invalid identity source policy' USING ERRCODE='23514';
  END IF;
 END LOOP;
END $$;

CREATE OR REPLACE FUNCTION guard_analysis_evidence_child() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r evidence_admissions%ROWTYPE; value TEXT;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Analysis evidence history immutable' USING ERRCODE='23514'; END IF;
 PERFORM validate_analysis_admission(NEW.attempt_id,NEW.admission_id,FALSE);
 IF TG_TABLE_NAME='brand_analysis_citations' THEN
  SELECT * INTO r FROM evidence_admissions WHERE id=NEW.admission_id;
  IF r.policy_version='url-text-review-v1' THEN
   IF NEW.field_path IS NULL OR NEW.field_path NOT IN ('title','description','text') THEN RAISE EXCEPTION 'Citation path required' USING ERRCODE='23514'; END IF;
   value=r.reviewed_text::jsonb->>NEW.field_path;
  ELSE
   IF NEW.field_path IS NOT NULL THEN RAISE EXCEPTION 'Legacy citation path forbidden' USING ERRCODE='23514'; END IF;
   value=r.reviewed_text;
  END IF;
  IF value IS NULL OR strpos(value,NEW.quote)=0 THEN RAISE EXCEPTION 'Analysis quote invalid' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION seal_analysis_evidence_input() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a brand_analysis_attempts%ROWTYPE; n INTEGER; bytes BIGINT; digest TEXT; parent UUID;
BEGIN
 IF TG_TABLE_NAME='brand_analysis_attempts' THEN parent=NEW.id; ELSE parent=NEW.attempt_id; END IF;
 SELECT * INTO a FROM brand_analysis_attempts WHERE id=parent;
 SELECT count(*),coalesce(sum(octet_length(r.reviewed_text)),0),
  encode(sha256(convert_to((CASE WHEN bool_or(r.policy_version='url-text-review-v1') THEN E'mixed-evidence-v2\n' ELSE E'text-evidence-v1\n' END)||a.input_brand_name||E'\n'||a.input_industry||
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
