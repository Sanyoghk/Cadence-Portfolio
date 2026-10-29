-- Bounded workspace/hour admission counts include text and pending/failed URL sources.
CREATE INDEX brand_evidence_registration_scope_idx ON brand_evidence (brand_id, captured_at);
