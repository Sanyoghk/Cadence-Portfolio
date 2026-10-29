CREATE TABLE evidence_scan_results (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    evidence_id UUID NOT NULL REFERENCES brand_evidence(id),
    job_id UUID NOT NULL REFERENCES jobs(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    lease_owner VARCHAR(255) NOT NULL CHECK (btrim(lease_owner) <> ''),
    source_version BIGINT NOT NULL CHECK (source_version >= 0),
    checksum VARCHAR(64) NOT NULL CHECK (checksum ~ '^[0-9a-f]{64}$'),
    scanned_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    verdict VARCHAR(32) NOT NULL CHECK (verdict IN ('CLEAN','INFECTED','UNAVAILABLE','ERROR')),
    CONSTRAINT evidence_scan_job_attempt_unique UNIQUE (job_id, attempt_number)
);
CREATE INDEX evidence_scan_evidence_time_idx ON evidence_scan_results(evidence_id, scanned_at);

-- No old evidence is labelled scanned. Validate cross-row provenance even if application checks
-- are bypassed. A result is inserted before the worker updates the job's terminal/retry state.
CREATE FUNCTION validate_evidence_scan_result() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Evidence scan results are immutable' USING ERRCODE = '23514';
    END IF;
    PERFORM 1 FROM brand_evidence e
        JOIN brands b ON b.id = e.brand_id AND b.status = 'ACTIVE'
        JOIN workspaces w ON w.id = b.workspace_id AND w.status = 'ACTIVE'
        JOIN jobs j ON j.id = NEW.job_id
        WHERE e.id = NEW.evidence_id AND e.type = 'FILE' AND e.status = 'PENDING'
          AND e.checksum = NEW.checksum AND e.lock_version = NEW.source_version
          AND NEW.scanned_at >= e.captured_at AND NEW.scanned_at <= clock_timestamp()
          AND j.type = 'BRAND_EVIDENCE_INGEST' AND j.payload_reference = 'evidence:' || e.id::text
          AND j.status = 'RUNNING' AND j.attempts = NEW.attempt_number
          AND j.lease_owner = NEW.lease_owner AND j.lease_expires_at > clock_timestamp()
        FOR SHARE OF j, e, b, w;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid evidence scan provenance' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER evidence_scan_result_guard BEFORE INSERT OR UPDATE ON evidence_scan_results
    FOR EACH ROW EXECUTE FUNCTION validate_evidence_scan_result();
