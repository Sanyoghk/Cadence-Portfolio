-- New acquisitions only. Never backfill legacy metadata as verified provenance/consent.
CREATE TABLE url_text_snapshots (
 id UUID PRIMARY KEY, evidence_id UUID NOT NULL REFERENCES brand_evidence(id),
 source_version BIGINT NOT NULL CHECK(source_version>=0),
 fetch_job_id UUID NOT NULL REFERENCES jobs(id), fetch_attempt INTEGER NOT NULL CHECK(fetch_attempt>0),
 lease_owner VARCHAR(255) NOT NULL CHECK(char_length(lease_owner)>0),
 source_checksum VARCHAR(64) NOT NULL CHECK(source_checksum ~ '^[0-9a-f]{64}$'),
 fetched_checksum VARCHAR(64) NOT NULL CHECK(fetched_checksum ~ '^[0-9a-f]{64}$'),
 storage_reference VARCHAR(500) NOT NULL,
 captured_at TIMESTAMPTZ NOT NULL, fetched_at TIMESTAMPTZ NOT NULL, extracted_at TIMESTAMPTZ NOT NULL,
 media_type VARCHAR(100) NOT NULL CHECK(media_type IN ('text/html','text/plain')),
 extraction_version VARCHAR(32) NOT NULL CHECK(extraction_version='website-v2'),
 warnings VARCHAR(500) NOT NULL, payload VARCHAR(120000) NOT NULL,
 content_checksum VARCHAR(64) NOT NULL CHECK(content_checksum=encode(sha256(convert_to(payload,'UTF8')),'hex')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0), UNIQUE(evidence_id,source_version)
);
CREATE FUNCTION url_text_projection(title TEXT, description TEXT, body TEXT) RETURNS TEXT
 LANGUAGE sql IMMUTABLE STRICT AS $$
 SELECT '{"format":"url-text-v1","title":'||to_json(title)::text||',"description":'||to_json(description)::text||',"text":'||to_json(body)::text||'}'
$$;
CREATE FUNCTION guard_url_text_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source brand_evidence%ROWTYPE; job jobs%ROWTYPE; obj quarantine_objects%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'URL snapshot is immutable' USING ERRCODE='23514'; END IF;
 SELECT * INTO job FROM jobs WHERE id=NEW.fetch_job_id FOR UPDATE;
 SELECT * INTO source FROM brand_evidence WHERE id=NEW.evidence_id FOR UPDATE;
 SELECT * INTO obj FROM quarantine_objects WHERE evidence_id=source.id
  AND 'quarantine:'||object_key=NEW.storage_reference FOR UPDATE;
 IF source.id IS NULL OR job.id IS NULL OR obj.id IS NULL
  OR source.type<>'URL' OR source.status<>'FETCHED' OR source.lock_version<>NEW.source_version
  OR source.checksum IS DISTINCT FROM NEW.source_checksum
  OR source.checksum IS DISTINCT FROM encode(sha256(convert_to(E'URL\n'||source.source_reference,'UTF8')),'hex')
  OR source.fetched_checksum IS DISTINCT FROM NEW.fetched_checksum
  OR source.fetched_reference IS DISTINCT FROM NEW.storage_reference
  OR source.fetched_at IS DISTINCT FROM NEW.fetched_at OR source.extracted_at IS DISTINCT FROM NEW.extracted_at
  OR source.captured_at IS DISTINCT FROM NEW.captured_at
  OR source.fetched_media_type IS DISTINCT FROM NEW.media_type OR source.extraction_version IS DISTINCT FROM NEW.extraction_version
  OR source.extraction_warnings IS DISTINCT FROM NEW.warnings
  OR NEW.payload IS DISTINCT FROM url_text_projection(source.extracted_title,source.extracted_description,source.extracted_text)
  -- UrlFetchWorker uses a two-minute lease from the database-authoritative clock.
  OR NEW.extracted_at<>NEW.fetched_at OR NEW.fetched_at<job.lease_expires_at-interval '2 minutes'
  OR NEW.fetched_at<NEW.captured_at
  OR NEW.extracted_at>clock_timestamp()+interval '1 second'
  OR job.type<>'BRAND_EVIDENCE_INGEST' OR job.payload_reference<>'evidence:'||source.id::text
  OR job.status<>'RUNNING' OR job.attempts<>NEW.fetch_attempt OR job.lease_owner IS DISTINCT FROM NEW.lease_owner
  OR job.lease_expires_at IS NULL OR job.lease_expires_at<=clock_timestamp()
  OR obj.state<>'REFERENCED' OR obj.quota_released OR obj.deleted_at IS NOT NULL
  OR obj.brand_id<>source.brand_id OR obj.retained_bytes NOT BETWEEN 1 AND 2097152
  OR obj.object_key<>'quarantine/writes/'||obj.workspace_id::text||'/'||obj.id::text||'.html'
  OR NOT EXISTS(SELECT 1 FROM brands b JOIN workspaces w ON w.id=b.workspace_id
    WHERE b.id=source.brand_id AND w.id=obj.workspace_id AND b.status='ACTIVE' AND w.status='ACTIVE') THEN
  RAISE EXCEPTION 'Invalid URL acquisition snapshot' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER url_text_snapshot_guard BEFORE INSERT OR UPDATE OR DELETE ON url_text_snapshots
 FOR EACH ROW EXECUTE FUNCTION guard_url_text_snapshot();

ALTER TABLE evidence_admissions ADD url_snapshot_id UUID REFERENCES url_text_snapshots(id);
ALTER TABLE evidence_admissions DROP CONSTRAINT admission_policy_shape;
ALTER TABLE evidence_admissions ADD CONSTRAINT admission_policy_shape CHECK(
 (policy_version='text-review-v1' AND artifact_id IS NULL AND original_scan_id IS NULL
  AND latest_scan_id IS NULL AND renderer_version IS NULL AND scan_expires_at IS NULL AND url_snapshot_id IS NULL)
 OR (policy_version='pdf-text-review-v1' AND artifact_id IS NOT NULL AND original_scan_id IS NOT NULL
  AND latest_scan_id IS NOT NULL AND renderer_version IS NOT NULL AND scan_expires_at IS NOT NULL AND url_snapshot_id IS NULL
  AND renderer_version ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
  AND char_length(reviewed_text)*2-char_length(regexp_replace(reviewed_text,U&'[\+010000-\+10FFFF]','','g'))<=16000)
 OR (policy_version='url-text-review-v1' AND url_snapshot_id IS NOT NULL AND artifact_id IS NULL
  AND renderer_version IS NULL AND original_scan_id IS NOT NULL AND latest_scan_id IS NOT NULL
  AND original_scan_id=latest_scan_id AND scan_expires_at IS NOT NULL AND octet_length(reviewed_text)<=20000));
CREATE UNIQUE INDEX admission_url_context_unique ON evidence_admissions(evidence_id,source_version,brand_version,workspace_version,url_snapshot_id,latest_scan_id,scan_expires_at)
 WHERE policy_version='url-text-review-v1';

CREATE OR REPLACE FUNCTION guard_evidence_admission() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source brand_evidence%ROWTYPE; parent brands%ROWTYPE; ws workspaces%ROWTYPE;
 url url_text_snapshots%ROWTYPE;
 art evidence_artifacts%ROWTYPE; original evidence_scan_results%ROWTYPE; latest evidence_scan_results%ROWTYPE; obj quarantine_objects%ROWTYPE;
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
  OR parent.workspace_id<>ws.id
  OR (CASE WHEN NEW.policy_version='url-text-review-v1' THEN source.status<>'FETCHED' ELSE source.status<>'PENDING' END) OR parent.status<>'ACTIVE' OR ws.status<>'ACTIVE'
  OR source.lock_version<>NEW.source_version OR parent.lock_version<>NEW.brand_version
  OR ws.lock_version<>NEW.workspace_version
  OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=NEW.reviewed_by AND workspace_id=ws.id)
  OR NEW.source_checksum IS DISTINCT FROM source.checksum
  OR NEW.content_checksum IS DISTINCT FROM encode(sha256(convert_to(NEW.reviewed_text,'UTF8')),'hex')
  OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals w JOIN evidence_admissions a ON a.id=w.admission_id WHERE a.evidence_id=NEW.evidence_id AND a.source_version=NEW.source_version)
  OR (SELECT count(*) FROM evidence_admissions a WHERE a.evidence_id=NEW.evidence_id)>=1000
  OR NEW.reviewed_at<source.captured_at OR NEW.reviewed_at>clock_timestamp()+interval '5 seconds' THEN
  RAISE EXCEPTION 'Invalid evidence admission source or review' USING ERRCODE='23514';
 END IF;

 IF NEW.policy_version='text-review-v1' THEN
  IF source.type<>'TEXT' OR NEW.reviewed_text IS DISTINCT FROM source.source_reference
   OR NEW.source_checksum IS DISTINCT FROM encode(sha256(convert_to(E'TEXT\n'||NEW.reviewed_text,'UTF8')),'hex') THEN
   RAISE EXCEPTION 'Invalid text admission' USING ERRCODE='23514';
  END IF;

 ELSIF NEW.policy_version='url-text-review-v1' THEN
  SELECT * INTO url FROM url_text_snapshots WHERE id=NEW.url_snapshot_id;
  SELECT * INTO latest FROM evidence_scan_results WHERE evidence_id=source.id ORDER BY scanned_at DESC,created_at DESC,id DESC LIMIT 1;
  SELECT * INTO obj FROM quarantine_objects WHERE evidence_id=source.id AND brand_id=parent.id AND workspace_id=ws.id
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
   OR NEW.reviewed_text IS DISTINCT FROM url.payload OR NEW.content_checksum IS DISTINCT FROM url.content_checksum
   OR btrim(source.extracted_title||source.extracted_description||source.extracted_text)=''
   OR source.checksum IS DISTINCT FROM encode(sha256(convert_to(E'URL\n'||source.source_reference,'UTF8')),'hex')
   OR NOT EXISTS(SELECT 1 FROM jobs WHERE id=url.fetch_job_id AND status='SUCCEEDED' AND attempts=url.fetch_attempt)
   OR NEW.original_scan_id IS DISTINCT FROM latest.id OR NEW.latest_scan_id IS DISTINCT FROM latest.id
   OR latest.source_version<>source.lock_version OR latest.checksum IS DISTINCT FROM source.fetched_checksum
   OR latest.verdict<>'CLEAN' OR latest.scanned_at>clock_timestamp() OR latest.scanned_at<source.fetched_at
   OR latest.scanner_engine='legacy-unknown' OR latest.signature_version='legacy-unknown'
   OR NEW.scan_expires_at<=clock_timestamp() OR NEW.reviewed_at>=NEW.scan_expires_at
   OR NEW.scan_expires_at<latest.scanned_at+interval '1 second' OR NEW.scan_expires_at>latest.scanned_at+interval '7 days'
   OR obj.state<>'REFERENCED' OR obj.quota_released OR obj.deleted_at IS NOT NULL OR obj.retained_bytes NOT BETWEEN 1 AND 2097152
   OR obj.object_key<>'quarantine/writes/'||ws.id::text||'/'||obj.id::text||'.html' THEN
   RAISE EXCEPTION 'Invalid URL admission' USING ERRCODE='23514';
  END IF;
 ELSE
  SELECT * INTO art FROM evidence_artifacts WHERE id=NEW.artifact_id;
  SELECT * INTO original FROM evidence_scan_results WHERE id=NEW.original_scan_id;
  SELECT * INTO latest FROM evidence_scan_results WHERE evidence_id=NEW.evidence_id
    ORDER BY scanned_at DESC,created_at DESC,id DESC LIMIT 1;
  SELECT * INTO obj FROM quarantine_objects WHERE evidence_id=source.id AND brand_id=parent.id
    AND workspace_id=ws.id AND object_key=substring(source.source_reference from 12)
    AND 'quarantine:'||object_key=source.source_reference FOR UPDATE;
  IF source.type<>'FILE' OR source.media_type IS DISTINCT FROM 'application/pdf'
   OR art.id IS NULL OR original.id IS NULL OR latest.id IS NULL OR obj.id IS NULL
   OR art.evidence_id<>source.id OR art.source_version<>NEW.source_version OR art.source_checksum<>NEW.source_checksum
   OR art.kind<>'PDF_TEXT' OR art.scan_result_id<>NEW.original_scan_id OR art.storage_reference IS NOT NULL
   OR art.extracted_text IS DISTINCT FROM NEW.reviewed_text OR art.output_checksum IS DISTINCT FROM NEW.content_checksum
   OR art.renderer_version IS DISTINCT FROM NEW.renderer_version OR art.produced_at>clock_timestamp()
   OR art.produced_at<original.scanned_at
   OR NOT EXISTS(SELECT 1 FROM jobs WHERE id=art.job_id AND status='SUCCEEDED')
   OR original.evidence_id<>source.id OR original.source_version<>NEW.source_version
   OR original.checksum<>NEW.source_checksum OR original.verdict<>'CLEAN' OR original.scanned_at>clock_timestamp()
   OR latest.id<>NEW.latest_scan_id OR latest.source_version<>NEW.source_version
   OR latest.checksum<>NEW.source_checksum OR latest.verdict<>'CLEAN' OR latest.scanned_at>clock_timestamp()
   OR NEW.scan_expires_at<=clock_timestamp() OR NEW.reviewed_at>=NEW.scan_expires_at
   OR NEW.scan_expires_at<latest.scanned_at+interval '1 second'
   OR NEW.scan_expires_at>latest.scanned_at+interval '7 days'
   OR obj.state<>'REFERENCED' OR obj.quota_released OR obj.deleted_at IS NOT NULL
   OR obj.retained_bytes NOT BETWEEN 1 AND 10485760
   OR obj.object_key IS DISTINCT FROM 'quarantine/writes/'||ws.id::text||'/'||obj.id::text||'.pdf' THEN
   RAISE EXCEPTION 'Invalid PDF admission' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
