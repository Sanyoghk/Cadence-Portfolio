-- Provider-wide attempt admission serializes across workspaces and application replicas.
CREATE TABLE provider_capacity_slots (
  provider VARCHAR(16) PRIMARY KEY CHECK (provider IN ('INSTAGRAM', 'TIKTOK'))
);
INSERT INTO provider_capacity_slots(provider) VALUES ('INSTAGRAM'), ('TIKTOK');
ALTER TABLE evidence_provider_calls ADD CONSTRAINT provider_call_capacity_identity
  FOREIGN KEY (provider) REFERENCES provider_capacity_slots(provider);
CREATE INDEX provider_calls_global_quota_idx ON evidence_provider_calls(provider, requested_at);

CREATE FUNCTION guard_social_provider_capacity_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Social provider capacity identities are immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER provider_capacity_immutability BEFORE UPDATE OR DELETE ON provider_capacity_slots
  FOR EACH ROW EXECUTE FUNCTION guard_social_provider_capacity_identity();
