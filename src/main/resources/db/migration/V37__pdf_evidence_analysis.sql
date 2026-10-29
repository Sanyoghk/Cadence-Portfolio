-- PDF source metadata for selected immutable receipts. Physical bytes are checked by orchestration.
CREATE FUNCTION validate_pdf_analysis_source(admission UUID) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE r evidence_admissions%ROWTYPE; e brand_evidence%ROWTYPE; b brands%ROWTYPE; ws workspaces%ROWTYPE;
 art evidence_artifacts%ROWTYPE; original evidence_scan_results%ROWTYPE; latest evidence_scan_results%ROWTYPE; obj quarantine_objects%ROWTYPE;
BEGIN
 SELECT * INTO r FROM evidence_admissions WHERE id=admission;
 SELECT * INTO e FROM brand_evidence WHERE id=r.evidence_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=e.brand_id;
 SELECT * INTO ws FROM workspaces WHERE id=b.workspace_id;
  SELECT * INTO art FROM evidence_artifacts WHERE id=r.artifact_id;
  SELECT * INTO original FROM evidence_scan_results WHERE id=r.original_scan_id;
  SELECT * INTO latest FROM evidence_scan_results WHERE evidence_id=r.evidence_id
    ORDER BY scanned_at DESC,created_at DESC,id DESC LIMIT 1;
  SELECT * INTO obj FROM quarantine_objects WHERE evidence_id=e.id AND brand_id=b.id
    AND workspace_id=ws.id AND object_key=substring(e.source_reference from 12)
    AND 'quarantine:'||object_key=e.source_reference FOR UPDATE;
  IF e.type<>'FILE' OR e.media_type IS DISTINCT FROM 'application/pdf'
   OR art.id IS NULL OR original.id IS NULL OR latest.id IS NULL OR obj.id IS NULL
   OR art.evidence_id<>e.id OR art.source_version<>r.source_version OR art.source_checksum<>r.source_checksum
   OR art.kind<>'PDF_TEXT' OR art.scan_result_id<>r.original_scan_id OR art.storage_reference IS NOT NULL
   OR art.extracted_text IS DISTINCT FROM r.reviewed_text OR art.output_checksum IS DISTINCT FROM r.content_checksum
   OR art.renderer_version IS DISTINCT FROM r.renderer_version OR art.produced_at>clock_timestamp()
   OR art.produced_at<original.scanned_at
   OR NOT EXISTS(SELECT 1 FROM jobs WHERE id=art.job_id AND status='SUCCEEDED')
   OR original.evidence_id<>e.id OR original.source_version<>r.source_version
   OR original.checksum<>r.source_checksum OR original.verdict<>'CLEAN' OR original.scanned_at>clock_timestamp()
   OR latest.id<>r.latest_scan_id OR latest.source_version<>r.source_version
   OR latest.checksum<>r.source_checksum OR latest.verdict<>'CLEAN' OR latest.scanned_at>clock_timestamp()
   OR r.scan_expires_at<=clock_timestamp() OR r.reviewed_at>=r.scan_expires_at
   OR r.scan_expires_at<latest.scanned_at+interval '1 second'
   OR r.scan_expires_at>latest.scanned_at+interval '7 days'
   OR obj.state<>'REFERENCED' OR obj.quota_released OR obj.deleted_at IS NOT NULL
   OR obj.retained_bytes NOT BETWEEN 1 AND 10485760
   OR obj.object_key IS DISTINCT FROM 'quarantine/writes/'||ws.id::text||'/'||obj.id::text||'.pdf' THEN
   RAISE EXCEPTION 'Invalid PDF admission' USING ERRCODE='23514';
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
  OR e.status<>'PENDING'
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
 ELSE
  RAISE EXCEPTION 'Analysis admission policy invalid' USING ERRCODE='23514';
 END IF;

END $$;
