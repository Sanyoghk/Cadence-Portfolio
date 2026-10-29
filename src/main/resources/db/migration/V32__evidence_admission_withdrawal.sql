-- Withdrawal is append-only and never erases the original reviewed input.
CREATE TABLE evidence_admission_withdrawals (
 id UUID PRIMARY KEY,
 admission_id UUID NOT NULL UNIQUE REFERENCES evidence_admissions(id),
 withdrawn_by UUID NOT NULL REFERENCES cadence_users(id),
 withdrawn_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0)
);
CREATE INDEX evidence_withdrawal_actor_idx ON evidence_admission_withdrawals(withdrawn_by);

CREATE FUNCTION guard_evidence_admission_withdrawal() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE admission evidence_admissions%ROWTYPE; source brand_evidence%ROWTYPE;
 parent brands%ROWTYPE; ws workspaces%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN
  RAISE EXCEPTION 'Evidence withdrawal history is immutable' USING ERRCODE='23514';
 END IF;
 PERFORM 1 FROM cadence_users WHERE id=NEW.withdrawn_by AND active AND role='OWNER' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Invalid evidence withdrawal actor' USING ERRCODE='23514'; END IF;
 SELECT * INTO admission FROM evidence_admissions WHERE id=NEW.admission_id;
 SELECT * INTO source FROM brand_evidence WHERE id=admission.evidence_id;
 SELECT * INTO parent FROM brands WHERE id=source.brand_id;
 SELECT * INTO ws FROM workspaces WHERE id=parent.workspace_id FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=source.brand_id FOR UPDATE;
 SELECT * INTO source FROM brand_evidence WHERE id=admission.evidence_id FOR UPDATE;
 -- Archived/stale inputs may still be withdrawn by a current authorized owner.
 IF admission.id IS NULL OR source.id IS NULL OR parent.id IS NULL OR ws.id IS NULL
  OR source.brand_id<>parent.id OR parent.workspace_id<>ws.id
  OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=NEW.withdrawn_by AND workspace_id=ws.id)
  OR NEW.withdrawn_at<admission.reviewed_at OR NEW.withdrawn_at>clock_timestamp()+interval '5 seconds' THEN
  RAISE EXCEPTION 'Invalid evidence admission withdrawal' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER evidence_withdrawal_guard BEFORE INSERT OR UPDATE OR DELETE ON evidence_admission_withdrawals
 FOR EACH ROW EXECUTE FUNCTION guard_evidence_admission_withdrawal();

-- Parent versions alone cannot detect SQL reparenting to another version-zero tenant.
-- Reparenting is already forbidden by domain/API contracts; enforce it independently in DB.
CREATE FUNCTION guard_evidence_parent_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.brand_id IS DISTINCT FROM OLD.brand_id THEN
  RAISE EXCEPTION 'Evidence parent is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER evidence_parent_identity_guard BEFORE UPDATE OF brand_id ON brand_evidence
 FOR EACH ROW EXECUTE FUNCTION guard_evidence_parent_identity();

CREATE FUNCTION guard_brand_workspace_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.workspace_id IS DISTINCT FROM OLD.workspace_id THEN
  RAISE EXCEPTION 'Brand workspace is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER brand_workspace_identity_guard BEFORE UPDATE OF workspace_id ON brands
 FOR EACH ROW EXECUTE FUNCTION guard_brand_workspace_identity();
