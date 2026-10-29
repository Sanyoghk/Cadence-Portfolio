CREATE TABLE evidence_social_origins (
  evidence_id UUID PRIMARY KEY REFERENCES brand_evidence(id),
  provider_call_id UUID NOT NULL UNIQUE REFERENCES evidence_provider_calls(id),
  provider VARCHAR(16) NOT NULL CHECK (provider IN ('INSTAGRAM','TIKTOK')),
  account_id VARCHAR(128) NOT NULL CHECK (account_id ~ '^[A-Za-z0-9_-]{1,128}$'),
  api_version VARCHAR(16) NOT NULL CHECK (api_version ~ '^v([1-9][0-9]*[.]0|2)$'),
  captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  checksum VARCHAR(64) NOT NULL CHECK (checksum ~ '^[a-f0-9]{64}$')
);
CREATE FUNCTION validate_social_evidence_origin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP <> 'INSERT' THEN
    RAISE EXCEPTION 'Social evidence origin is immutable' USING ERRCODE = '23514';
  END IF;
  PERFORM 1 FROM brand_evidence e JOIN brands b ON b.id=e.brand_id
    JOIN evidence_provider_calls c ON c.id=NEW.provider_call_id
    WHERE e.id=NEW.evidence_id AND e.type='TEXT' AND e.checksum=NEW.checksum
      AND c.brand_id=e.brand_id AND c.workspace_id=b.workspace_id AND c.provider=NEW.provider
      AND NEW.captured_at >= c.requested_at AND NEW.captured_at <= clock_timestamp();
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Social evidence origin has invalid provider receipt or ownership' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER social_origin_guard BEFORE INSERT OR UPDATE OR DELETE ON evidence_social_origins
  FOR EACH ROW EXECUTE FUNCTION validate_social_evidence_origin();
-- Later evidence mutation cannot silently change bytes underlying a trusted origin receipt.
CREATE FUNCTION guard_social_evidence_checksum() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM evidence_social_origins o WHERE o.evidence_id=OLD.id) AND
    (NEW.brand_id<>OLD.brand_id OR NEW.type<>OLD.type OR NEW.checksum<>OLD.checksum OR NEW.source_reference<>OLD.source_reference) THEN
    RAISE EXCEPTION 'Social evidence origin content is immutable' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER social_evidence_content_guard BEFORE UPDATE ON brand_evidence
  FOR EACH ROW EXECUTE FUNCTION guard_social_evidence_checksum();
