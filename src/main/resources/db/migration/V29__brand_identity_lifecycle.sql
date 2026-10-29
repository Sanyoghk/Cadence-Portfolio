-- Preserve existing V28 DRAFT content. No approval actor/time/lineage is invented.
ALTER TABLE brand_identities
 DROP CONSTRAINT brand_identities_status_check,
 ADD CONSTRAINT identity_status CHECK(status IN ('DRAFT','APPROVED','LOCKED')),
 ADD COLUMN revised_from_id UUID REFERENCES brand_identities(id),
 ADD COLUMN approved_by UUID REFERENCES cadence_users(id),
 ADD COLUMN approved_at TIMESTAMPTZ,
 ADD CONSTRAINT identity_revision_not_self CHECK(revised_from_id IS NULL OR revised_from_id<>id),
 ADD CONSTRAINT identity_approval_audit CHECK (
  (status='DRAFT' AND approved_by IS NULL AND approved_at IS NULL) OR
  (status IN ('APPROVED','LOCKED') AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND approved_at>=created_at));
CREATE INDEX identity_revision_source_idx ON brand_identities(revised_from_id) WHERE revised_from_id IS NOT NULL;
CREATE INDEX identity_approval_actor_idx ON brand_identities(approved_by) WHERE approved_by IS NOT NULL;

CREATE OR REPLACE FUNCTION guard_identity_draft() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE ws_id UUID; latest INTEGER; source brand_identities%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Identity history cannot be deleted' USING ERRCODE='23514'; END IF;
 SELECT b.workspace_id INTO ws_id FROM brands b WHERE b.id=NEW.brand_id;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'DRAFT' OR NEW.approved_by IS NOT NULL OR NEW.approved_at IS NOT NULL THEN
   RAISE EXCEPTION 'Identity must start as a draft' USING ERRCODE='23514';
  END IF;
  PERFORM 1 FROM workspaces WHERE id=ws_id AND status='ACTIVE' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Archived identity parent' USING ERRCODE='23514'; END IF;
  PERFORM 1 FROM brands WHERE id=NEW.brand_id AND status='ACTIVE' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Archived identity parent' USING ERRCODE='23514'; END IF;
  SELECT coalesce(max(version),0) INTO latest FROM brand_identities WHERE brand_id=NEW.brand_id;
  IF NEW.version<>latest+1 THEN RAISE EXCEPTION 'Invalid identity revision allocation' USING ERRCODE='23514'; END IF;
  IF NEW.revised_from_id IS NOT NULL THEN
   SELECT * INTO source FROM brand_identities WHERE id=NEW.revised_from_id;
   IF source.id IS NULL OR source.brand_id<>NEW.brand_id OR source.status NOT IN ('APPROVED','LOCKED') OR source.version>=NEW.version THEN
    RAISE EXCEPTION 'Invalid identity lineage' USING ERRCODE='23514';
   END IF;
  END IF;
 ELSE
  IF ROW(NEW.id,NEW.brand_id,NEW.version,NEW.created_at,NEW.revised_from_id)
   IS DISTINCT FROM ROW(OLD.id,OLD.brand_id,OLD.version,OLD.created_at,OLD.revised_from_id) THEN
   RAISE EXCEPTION 'Identity revision metadata is immutable' USING ERRCODE='23514';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM brands b JOIN workspaces w ON w.id=b.workspace_id
   WHERE b.id=NEW.brand_id AND b.status='ACTIVE' AND w.status='ACTIVE') THEN
   RAISE EXCEPTION 'Archived identity parent' USING ERRCODE='23514';
  END IF;
  IF OLD.status<>'DRAFT' AND (
   ROW(NEW.primary_color,NEW.secondary_color,NEW.accent_color,NEW.neutral_color,NEW.background_color,NEW.sample_caption,NEW.approved_by,NEW.approved_at)
    IS DISTINCT FROM ROW(OLD.primary_color,OLD.secondary_color,OLD.accent_color,OLD.neutral_color,OLD.background_color,OLD.sample_caption,OLD.approved_by,OLD.approved_at)
   OR (NEW.status IS DISTINCT FROM OLD.status AND NOT (OLD.status='APPROVED' AND NEW.status='LOCKED'))) THEN
   RAISE EXCEPTION 'Approved identity content and audit are immutable' USING ERRCODE='23514';
  END IF;
  IF OLD.status='DRAFT' AND NEW.status IS DISTINCT FROM OLD.status THEN
   IF NEW.status<>'APPROVED' OR NEW.approved_by IS NULL OR NEW.approved_at IS NULL
    OR NEW.approved_at<NEW.created_at OR NOT EXISTS (
     SELECT 1 FROM cadence_users u JOIN workspace_memberships m ON m.user_id=u.id
      WHERE u.id=NEW.approved_by AND u.active AND u.role='OWNER' AND m.workspace_id=ws_id) THEN
    RAISE EXCEPTION 'Invalid identity approval actor or transition' USING ERRCODE='23514';
   END IF;
   IF ROW(NEW.primary_color,NEW.secondary_color,NEW.accent_color,NEW.neutral_color,NEW.background_color,NEW.sample_caption)
    IS DISTINCT FROM ROW(OLD.primary_color,OLD.secondary_color,OLD.accent_color,OLD.neutral_color,OLD.background_color,OLD.sample_caption) THEN
    RAISE EXCEPTION 'Approval cannot replace identity content' USING ERRCODE='23514';
   END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
-- V28 collection guards and deferred content checks continue to protect every revision.
