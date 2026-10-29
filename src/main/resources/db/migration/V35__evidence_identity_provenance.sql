-- Existing manual/declared revisions remain unchanged; no actor/citation backfill.
ALTER TABLE brand_identities ADD analysis_copied_by UUID REFERENCES cadence_users(id);
DROP TRIGGER identity_evidence_analysis_mode_guard ON brand_identities;

CREATE FUNCTION resolve_identity_analysis_origin(analysis UUID,revision UUID,parent UUID,current_version INTEGER)
 RETURNS UUID LANGUAGE plpgsql AS $$
DECLARE prior brand_identities%ROWTYPE; hops INTEGER=0;
BEGIN
 WHILE analysis IS NULL AND revision IS NOT NULL LOOP
  hops=hops+1;
  IF hops>1000 THEN RAISE EXCEPTION 'Identity lineage limit exceeded' USING ERRCODE='23514'; END IF;
  SELECT * INTO prior FROM brand_identities WHERE id=revision;
  IF prior.id IS NULL OR prior.brand_id<>parent OR prior.version>=current_version
  THEN RAISE EXCEPTION 'Invalid identity lineage' USING ERRCODE='23514'; END IF;
  analysis=prior.analysis_attempt_id; revision=prior.revised_from_id; current_version=prior.version;
 END LOOP;
 RETURN analysis;
END $$;

CREATE FUNCTION validate_identity_evidence_sources(analysis UUID,parent_id UUID)
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
  IF e.id IS NULL OR e.brand_id<>b.id OR e.type<>'TEXT' OR e.status<>'PENDING'
   OR e.lock_version<>r.source_version OR r.brand_version<>a.brand_version
   OR r.workspace_version<>a.workspace_version OR r.policy_version<>'text-review-v1'
   OR e.source_reference IS DISTINCT FROM r.reviewed_text OR e.checksum IS DISTINCT FROM r.source_checksum
   OR r.source_checksum<>encode(sha256(convert_to(E'TEXT\n'||r.reviewed_text,'UTF8')),'hex')
   OR r.content_checksum<>encode(sha256(convert_to(r.reviewed_text,'UTF8')),'hex')
   OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals x JOIN evidence_admissions y ON y.id=x.admission_id
    WHERE y.evidence_id=e.id AND y.source_version=r.source_version)
  THEN RAISE EXCEPTION 'Evidence identity source changed' USING ERRCODE='23514'; END IF;
 END LOOP;
END $$;

CREATE FUNCTION guard_identity_evidence_provenance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original UUID; receipt brand_analysis_attempts%ROWTYPE; actor UUID; ws UUID;
BEGIN
 IF TG_OP='UPDATE' AND NEW.analysis_copied_by IS DISTINCT FROM OLD.analysis_copied_by
 THEN RAISE EXCEPTION 'Identity copy actor immutable' USING ERRCODE='23514'; END IF;
 IF NEW.analysis_copied_by IS NOT NULL AND NEW.analysis_attempt_id IS NULL
 THEN RAISE EXCEPTION 'Identity copy actor requires evidence origin' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND NEW.analysis_attempt_id IS NOT NULL THEN
  SELECT * INTO receipt FROM brand_analysis_attempts WHERE id=NEW.analysis_attempt_id;
  IF (receipt.requested_by IS NOT NULL)<>(NEW.analysis_copied_by IS NOT NULL)
  THEN RAISE EXCEPTION 'Evidence identity source integration unavailable' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_OP='INSERT' AND NEW.analysis_copied_by IS NOT NULL THEN
  original=NEW.analysis_attempt_id; actor=NEW.analysis_copied_by;
 ELSIF TG_OP='UPDATE' AND OLD.status='DRAFT' AND NEW.status='APPROVED' THEN
  original=resolve_identity_analysis_origin(NEW.analysis_attempt_id,NEW.revised_from_id,NEW.brand_id,NEW.version);
  actor=NEW.approved_by;
 ELSE RETURN NEW; END IF;
 SELECT * INTO receipt FROM brand_analysis_attempts WHERE id=original;
 IF receipt.requested_by IS NULL THEN RETURN NEW; END IF;
 -- This trigger sorts before identity_draft_guard; app follows the same Account-first order.
 PERFORM 1 FROM cadence_users WHERE id=actor AND active AND role='OWNER' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Evidence identity actor invalid' USING ERRCODE='23514'; END IF;
 SELECT workspace_id INTO ws FROM brands WHERE id=NEW.brand_id;
 PERFORM 1 FROM workspaces WHERE id=ws FOR UPDATE;
 PERFORM 1 FROM brands WHERE id=NEW.brand_id FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=actor AND workspace_id=ws)
 THEN RAISE EXCEPTION 'Evidence identity membership invalid' USING ERRCODE='23514'; END IF;
 PERFORM validate_identity_evidence_sources(original,NEW.brand_id);
 RETURN NEW;
END $$;
CREATE TRIGGER identity_aa_evidence_provenance_guard BEFORE INSERT OR UPDATE ON brand_identities
 FOR EACH ROW EXECUTE FUNCTION guard_identity_evidence_provenance();
