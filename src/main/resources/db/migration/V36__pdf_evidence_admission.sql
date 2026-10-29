-- Existing TEXT receipts keep content/audit/IDs and NULL PDF fields; no invented consent.
ALTER TABLE evidence_admissions
 ADD artifact_id UUID REFERENCES evidence_artifacts(id),
 ADD original_scan_id UUID REFERENCES evidence_scan_results(id),
 ADD latest_scan_id UUID REFERENCES evidence_scan_results(id),
 ADD renderer_version VARCHAR(100),
 ADD scan_expires_at TIMESTAMPTZ;
ALTER TABLE evidence_admissions DROP CONSTRAINT evidence_admissions_policy_version_check;
ALTER TABLE evidence_admissions ADD CONSTRAINT admission_policy_shape CHECK(
 (policy_version='text-review-v1' AND artifact_id IS NULL AND original_scan_id IS NULL
  AND latest_scan_id IS NULL AND renderer_version IS NULL AND scan_expires_at IS NULL)
 OR (policy_version='pdf-text-review-v1' AND artifact_id IS NOT NULL AND original_scan_id IS NOT NULL
  AND latest_scan_id IS NOT NULL AND renderer_version IS NOT NULL AND scan_expires_at IS NOT NULL
  AND renderer_version ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
  AND char_length(reviewed_text)*2-char_length(regexp_replace(reviewed_text,U&'[\+010000-\+10FFFF]','','g'))<=16000));
ALTER TABLE evidence_admissions DROP CONSTRAINT evidence_admission_review_context_unique;
CREATE UNIQUE INDEX admission_text_context_unique ON evidence_admissions(evidence_id,source_version,brand_version,workspace_version)
 WHERE policy_version='text-review-v1';
CREATE UNIQUE INDEX admission_pdf_context_unique ON evidence_admissions(evidence_id,source_version,brand_version,workspace_version,artifact_id,latest_scan_id,scan_expires_at)
 WHERE policy_version='pdf-text-review-v1';
-- Shared cap/withdrawal queries do not filter policy; retain an unrestricted leading lookup.
CREATE INDEX evidence_admission_source_history_idx ON evidence_admissions(evidence_id,source_version);
CREATE OR REPLACE FUNCTION guard_evidence_admission() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source brand_evidence%ROWTYPE; parent brands%ROWTYPE; ws workspaces%ROWTYPE;
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
  OR source.status<>'PENDING' OR parent.status<>'ACTIVE' OR ws.status<>'ACTIVE'
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
