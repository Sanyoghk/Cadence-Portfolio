-- Nullable on existing/manual identities: no historical provenance is invented.
ALTER TABLE brand_identities ADD COLUMN analysis_attempt_id UUID REFERENCES brand_analysis_attempts(id),
 ADD CONSTRAINT identity_single_origin CHECK(analysis_attempt_id IS NULL OR revised_from_id IS NULL);
CREATE INDEX identity_analysis_source_idx ON brand_identities(analysis_attempt_id) WHERE analysis_attempt_id IS NOT NULL;
CREATE INDEX brand_analysis_identity_selection_idx ON brand_analysis_attempts
 (brand_id,brand_version,workspace_version,status,started_at DESC,id DESC);

CREATE FUNCTION guard_identity_analysis_source() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE receipt brand_analysis_attempts%ROWTYPE;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF NEW.analysis_attempt_id IS DISTINCT FROM OLD.analysis_attempt_id THEN
   RAISE EXCEPTION 'Identity analysis origin is immutable' USING ERRCODE='23514';
  END IF;
 ELSIF NEW.analysis_attempt_id IS NOT NULL THEN
  SELECT * INTO receipt FROM brand_analysis_attempts WHERE id=NEW.analysis_attempt_id;
  IF receipt.id IS NULL OR receipt.brand_id<>NEW.brand_id OR receipt.status<>'SUCCEEDED'
   OR NOT EXISTS(SELECT 1 FROM brands b JOIN workspaces w ON w.id=b.workspace_id
    WHERE b.id=NEW.brand_id AND b.lock_version=receipt.brand_version+1
      AND w.lock_version=receipt.workspace_version AND b.status='ACTIVE' AND w.status='ACTIVE') THEN
   RAISE EXCEPTION 'Invalid identity analysis source' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
-- Alphabetical trigger order: the existing draft guard locks Workspace/Brand first.
CREATE TRIGGER identity_generation_guard BEFORE INSERT OR UPDATE ON brand_identities
 FOR EACH ROW EXECUTE FUNCTION guard_identity_analysis_source();

CREATE FUNCTION identity_content_bytes(value TEXT) RETURNS BYTEA LANGUAGE SQL IMMUTABLE STRICT AS $$
 SELECT int4send(octet_length(convert_to(value,'UTF8'))) || convert_to(value,'UTF8')
$$;

CREATE FUNCTION validate_identity_analysis_content() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE identity brand_identities%ROWTYPE; payload BYTEA; items BYTEA; expected TEXT;
BEGIN
 SELECT * INTO identity FROM brand_identities WHERE id=NEW.id;
 IF identity.analysis_attempt_id IS NULL THEN RETURN NULL; END IF;
 SELECT output_checksum INTO expected FROM brand_analysis_attempts WHERE id=identity.analysis_attempt_id;
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
 IF encode(sha256(payload),'hex') IS DISTINCT FROM expected THEN
  RAISE EXCEPTION 'Identity content does not match analysis receipt' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END $$;
-- Only initial insertion is receipt-bound. Later explicit manual draft edits keep their original
-- generation receipt, not a false claim that the new content came from that provider.
CREATE CONSTRAINT TRIGGER identity_analysis_content AFTER INSERT ON brand_identities
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_identity_analysis_content();
