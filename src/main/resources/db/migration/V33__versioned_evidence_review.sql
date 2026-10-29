-- Preserve immutable prior receipts; each human confirmation binds an exact version context.
ALTER TABLE evidence_admissions DROP CONSTRAINT evidence_admissions_evidence_id_source_version_key;
ALTER TABLE evidence_admissions ADD CONSTRAINT evidence_admission_review_context_unique
 UNIQUE(evidence_id,source_version,brand_version,workspace_version);
-- Updated invoker guard retains every V31 validation and the reviewed parent-first lock order.
CREATE OR REPLACE FUNCTION guard_evidence_admission() RETURNS trigger LANGUAGE plpgsql AS $$
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
  OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals w JOIN evidence_admissions a ON a.id=w.admission_id WHERE a.evidence_id=NEW.evidence_id AND a.source_version=NEW.source_version)
  OR (SELECT count(*) FROM evidence_admissions a WHERE a.evidence_id=NEW.evidence_id)>=1000
  OR NEW.reviewed_at<source.captured_at OR NEW.reviewed_at>clock_timestamp()+interval '5 seconds' THEN
  RAISE EXCEPTION 'Invalid evidence admission source or review' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
