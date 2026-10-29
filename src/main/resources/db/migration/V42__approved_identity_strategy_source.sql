-- Historical Strategy provenance is not backfilled.
ALTER TABLE strategies
 ADD generation_identity_id UUID REFERENCES brand_identities(id),
 ADD generation_identity_version INTEGER,
 ADD generation_identity_lock_version BIGINT,
 ADD generation_identity_checksum VARCHAR(255),
 ADD generation_identity_approved_by UUID REFERENCES cadence_users(id),
 ADD generation_identity_approved_at TIMESTAMPTZ,
 ADD generation_identity_analysis_id UUID REFERENCES brand_analysis_attempts(id),
 ADD generation_requested_by UUID REFERENCES cadence_users(id),
 ADD CONSTRAINT strategy_identity_source_complete CHECK (
 (generation_identity_id IS NULL AND generation_identity_version IS NULL AND generation_identity_lock_version IS NULL
  AND generation_identity_checksum IS NULL AND generation_identity_approved_by IS NULL AND generation_identity_approved_at IS NULL
  AND generation_identity_analysis_id IS NULL AND generation_requested_by IS NULL)
 OR (generation_id IS NOT NULL AND generation_identity_id IS NOT NULL AND generation_identity_version IS NOT NULL
  AND generation_identity_version BETWEEN 1 AND 1000 AND generation_identity_lock_version IS NOT NULL AND generation_identity_lock_version>=0
  AND generation_identity_checksum IS NOT NULL AND generation_identity_checksum ~ '^[0-9a-f]{64}$'
  AND generation_identity_approved_by IS NOT NULL AND generation_identity_approved_at IS NOT NULL AND generation_requested_by IS NOT NULL));
CREATE INDEX strategy_identity_source_idx ON strategies(generation_identity_id) WHERE generation_identity_id IS NOT NULL;

CREATE FUNCTION strategy_identity_checksum(source_id UUID) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE identity brand_identities%ROWTYPE; payload BYTEA; items BYTEA;
BEGIN
 SELECT * INTO identity FROM brand_identities WHERE id=source_id;
 payload=identity_content_bytes(identity.primary_color)||identity_content_bytes(identity.secondary_color)
   ||identity_content_bytes(identity.accent_color)||identity_content_bytes(identity.neutral_color)
   ||identity_content_bytes(identity.background_color);
 SELECT int4send(count(*)::INTEGER)||coalesce(string_agg(identity_content_bytes(item_value),decode('','hex') ORDER BY item_order),decode('','hex'))
  INTO items FROM identity_fonts WHERE identity_id=identity.id;
 payload=payload||items;
 SELECT int4send(count(*)::INTEGER)||coalesce(string_agg(identity_content_bytes(item_value),decode('','hex') ORDER BY item_order),decode('','hex'))
  INTO items FROM identity_voice_up WHERE identity_id=identity.id;
 payload=payload||items;
 SELECT int4send(count(*)::INTEGER)||coalesce(string_agg(identity_content_bytes(item_value),decode('','hex') ORDER BY item_order),decode('','hex'))
  INTO items FROM identity_voice_down WHERE identity_id=identity.id;
 payload=payload||items||identity_content_bytes(identity.sample_caption);
 RETURN encode(sha256(payload),'hex');
END $$;

CREATE FUNCTION guard_strategy_identity_source() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source brand_identities%ROWTYPE; ws UUID; original UUID;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.generation_identity_id,NEW.generation_identity_version,NEW.generation_identity_lock_version,
    NEW.generation_identity_checksum,NEW.generation_identity_approved_by,NEW.generation_identity_approved_at,
    NEW.generation_identity_analysis_id,NEW.generation_requested_by)
   IS DISTINCT FROM ROW(OLD.generation_identity_id,OLD.generation_identity_version,OLD.generation_identity_lock_version,
    OLD.generation_identity_checksum,OLD.generation_identity_approved_by,OLD.generation_identity_approved_at,
    OLD.generation_identity_analysis_id,OLD.generation_requested_by)
  THEN RAISE EXCEPTION 'Strategy Identity source is immutable' USING ERRCODE='23514'; END IF;
  RETURN NEW;
 END IF;
 IF NEW.generation_id IS NULL THEN RETURN NEW; END IF;
 IF NEW.generation_identity_id IS NULL OR NEW.generation_requested_by IS NULL
 THEN RAISE EXCEPTION 'Approved Identity source required' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM cadence_users WHERE id=NEW.generation_requested_by AND active AND role='OWNER' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Strategy generation actor invalid' USING ERRCODE='23514'; END IF;
 SELECT workspace_id INTO ws FROM brands WHERE id=NEW.brand_id;
 PERFORM 1 FROM workspaces WHERE id=ws AND status='ACTIVE' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Strategy parent inactive' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM brands WHERE id=NEW.brand_id AND status='ACTIVE' FOR UPDATE;
 IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=NEW.generation_requested_by AND workspace_id=ws)
 THEN RAISE EXCEPTION 'Strategy membership invalid' USING ERRCODE='23514'; END IF;
 SELECT * INTO source FROM brand_identities WHERE id=NEW.generation_identity_id AND brand_id=NEW.brand_id FOR UPDATE;
 IF source.id IS NULL OR source.status<>'APPROVED'
  OR source.version IS DISTINCT FROM NEW.generation_identity_version OR source.lock_version IS DISTINCT FROM NEW.generation_identity_lock_version
  OR source.approved_by IS DISTINCT FROM NEW.generation_identity_approved_by OR source.approved_at IS DISTINCT FROM NEW.generation_identity_approved_at
  OR strategy_identity_checksum(source.id) IS DISTINCT FROM NEW.generation_identity_checksum
  OR EXISTS(SELECT 1 FROM brand_identities WHERE brand_id=NEW.brand_id AND version>source.version)
 THEN RAISE EXCEPTION 'Strategy Identity source changed' USING ERRCODE='23514'; END IF;
 original=resolve_identity_analysis_origin(source.analysis_attempt_id,source.revised_from_id,source.brand_id,source.version);
 IF original IS DISTINCT FROM NEW.generation_identity_analysis_id
 THEN RAISE EXCEPTION 'Strategy analysis origin mismatch' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM brand_analysis_attempts WHERE id=original AND requested_by IS NOT NULL)
 THEN PERFORM validate_identity_evidence_sources(original,NEW.brand_id); END IF;
 RETURN NEW;
END $$;
-- Sort before existing Strategy guards to establish Account -> Workspace -> Brand order.
CREATE TRIGGER strategy_aa_identity_source_guard BEFORE INSERT OR UPDATE ON strategies
 FOR EACH ROW EXECUTE FUNCTION guard_strategy_identity_source();
