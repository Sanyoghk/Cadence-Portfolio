ALTER TABLE brands ADD CONSTRAINT brands_identity_workspace_unique UNIQUE (id, workspace_id);
CREATE TABLE evidence_provider_calls (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id),
  brand_id UUID NOT NULL,
  provider VARCHAR(16) NOT NULL CHECK (provider IN ('INSTAGRAM','TIKTOK')),
  requested_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  CONSTRAINT provider_call_brand_scope FOREIGN KEY (brand_id, workspace_id) REFERENCES brands(id, workspace_id)
);
CREATE INDEX provider_calls_quota_idx ON evidence_provider_calls(workspace_id, provider, requested_at);
CREATE INDEX provider_calls_retention_idx ON evidence_provider_calls(requested_at, id);
CREATE FUNCTION guard_evidence_provider_call() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN
    RAISE EXCEPTION 'Provider call quota ledger is append only' USING ERRCODE = '23514';
  END IF;
  IF TG_OP = 'DELETE' AND OLD.requested_at >= clock_timestamp() - INTERVAL '90 days' THEN
    RAISE EXCEPTION 'Provider call quota ledger retention has not elapsed' USING ERRCODE = '23514';
  END IF;
  RETURN OLD;
END;
$$;
CREATE TRIGGER provider_call_immutability BEFORE UPDATE OR DELETE ON evidence_provider_calls
  FOR EACH ROW EXECUTE FUNCTION guard_evidence_provider_call();
