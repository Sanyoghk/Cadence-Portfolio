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
  IF e.id IS NULL OR e.brand_id<>b.id OR e.status<>'PENDING'
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
  ELSE RAISE EXCEPTION 'Invalid identity source policy' USING ERRCODE='23514';
  END IF;
 END LOOP;
END $$;
