CREATE TABLE evidence_artifacts (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    evidence_id UUID NOT NULL REFERENCES brand_evidence(id),
    scan_result_id UUID NOT NULL REFERENCES evidence_scan_results(id),
    job_id UUID NOT NULL REFERENCES jobs(id),
    source_checksum VARCHAR(64) NOT NULL,
    source_version BIGINT NOT NULL,
    attempt_number INTEGER NOT NULL,
    lease_owner VARCHAR(255) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    storage_reference VARCHAR(500),
    extracted_text VARCHAR(16000),
    output_checksum VARCHAR(64) NOT NULL,
    renderer_version VARCHAR(100) NOT NULL,
    produced_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    pixel_width INTEGER,
    pixel_height INTEGER,
    CONSTRAINT evidence_artifacts_unique_source UNIQUE(evidence_id, source_checksum, source_version, kind),
    CONSTRAINT evidence_artifacts_checksums CHECK (source_checksum ~ '^[0-9a-f]{64}$'
        AND output_checksum ~ '^[0-9a-f]{64}$' AND source_version >= 0 AND attempt_number > 0
        AND char_length(lease_owner) BETWEEN 1 AND 255
        AND renderer_version ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'),
    CONSTRAINT evidence_artifacts_kind_check CHECK (
        (kind = 'SCREENSHOT' AND storage_reference IS NOT NULL
            AND storage_reference ~ '^quarantine:quarantine/writes/[0-9a-f-]{36}/[0-9a-f-]{36}[.]png$'
            AND extracted_text IS NULL AND pixel_width IS NOT NULL AND pixel_height IS NOT NULL
            AND pixel_width = 1280 AND pixel_height = 720)
        OR (kind = 'PDF_TEXT' AND storage_reference IS NULL AND extracted_text IS NOT NULL
            AND pixel_width IS NULL AND pixel_height IS NULL
            AND translate(extracted_text, chr(9) || chr(10) || chr(13), '') !~ '[[:cntrl:]]'))
);
CREATE INDEX evidence_artifacts_scan_idx ON evidence_artifacts(scan_result_id);
CREATE INDEX evidence_artifacts_storage_idx ON evidence_artifacts(storage_reference)
    WHERE storage_reference IS NOT NULL;

CREATE FUNCTION validate_evidence_artifact() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    source brand_evidence%ROWTYPE;
    observation evidence_scan_results%ROWTYPE;
    extraction_job jobs%ROWTYPE;
    tracked quarantine_objects%ROWTYPE;
    latest_observation UUID;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Evidence artifact provenance is append-only' USING ERRCODE = '23514';
    END IF;
    SELECT * INTO source FROM brand_evidence WHERE id = NEW.evidence_id FOR SHARE;
    SELECT * INTO observation FROM evidence_scan_results WHERE id = NEW.scan_result_id;
    SELECT id INTO latest_observation FROM evidence_scan_results WHERE evidence_id = NEW.evidence_id
        ORDER BY scanned_at DESC, created_at DESC, id DESC LIMIT 1;
    SELECT * INTO extraction_job FROM jobs WHERE id = NEW.job_id FOR SHARE;
    IF source.id IS NULL OR observation.id IS NULL OR extraction_job.id IS NULL
        OR source.status = 'FAILED' OR source.lock_version <> NEW.source_version
        OR observation.evidence_id <> source.id OR observation.verdict <> 'CLEAN'
        OR observation.id <> latest_observation OR observation.source_version <> NEW.source_version
        OR observation.scanned_at < NEW.produced_at - INTERVAL '7 days'
        OR observation.checksum <> NEW.source_checksum OR observation.scanned_at > NEW.produced_at
        OR NEW.source_checksum <> (CASE WHEN source.type = 'URL' THEN source.fetched_checksum ELSE source.checksum END)
        OR extraction_job.type <> 'EVIDENCE_DOCUMENT_EXTRACT'
        OR extraction_job.payload_reference <> 'evidence:' || source.id::text
        OR extraction_job.status <> 'RUNNING' OR extraction_job.attempts <> NEW.attempt_number
        OR extraction_job.lease_owner <> NEW.lease_owner OR extraction_job.lease_expires_at <= NEW.produced_at
        OR NEW.produced_at > clock_timestamp() THEN
        RAISE EXCEPTION 'Invalid evidence artifact provenance' USING ERRCODE = '23514';
    END IF;
    PERFORM 1 FROM brands b JOIN workspaces w ON w.id = b.workspace_id
        WHERE b.id = source.brand_id AND b.status = 'ACTIVE' AND w.status = 'ACTIVE' FOR SHARE OF b, w;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Inactive evidence artifact ownership' USING ERRCODE = '23514';
    END IF;
    IF NEW.kind = 'PDF_TEXT' AND NEW.output_checksum <> encode(sha256(convert_to(NEW.extracted_text, 'UTF8')), 'hex') THEN
        RAISE EXCEPTION 'Invalid evidence artifact output checksum' USING ERRCODE = '23514';
    END IF;
    IF NEW.kind = 'SCREENSHOT' THEN
        IF source.type <> 'URL' OR source.fetched_media_type <> 'text/html' THEN
            RAISE EXCEPTION 'Invalid screenshot source' USING ERRCODE = '23514';
        END IF;
        SELECT * INTO tracked FROM quarantine_objects
            WHERE object_key = substring(NEW.storage_reference FROM length('quarantine:') + 1) FOR UPDATE;
        IF NOT FOUND OR tracked.brand_id <> source.brand_id OR tracked.state <> 'RESERVED'
            OR clock_timestamp() >= tracked.write_deadline THEN
            RAISE EXCEPTION 'Quarantine artifact publication is not authorized' USING ERRCODE = '23514';
        END IF;
    ELSIF source.type <> 'FILE' OR source.media_type <> 'application/pdf' THEN
        RAISE EXCEPTION 'Invalid PDF text source' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER evidence_artifact_guard BEFORE INSERT OR UPDATE OR DELETE ON evidence_artifacts
    FOR EACH ROW EXECUTE FUNCTION validate_evidence_artifact();

-- A derived screenshot owns a generation whose evidence_id is its parent Evidence. The artifact
-- independently records the exact generation, source bytes and CLEAN observation.
CREATE OR REPLACE FUNCTION validate_quarantine_object() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Quarantine generation tombstones must be retained' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id <> OLD.id OR NEW.workspace_id <> OLD.workspace_id OR NEW.brand_id <> OLD.brand_id
            OR NEW.object_key <> OLD.object_key OR NEW.reserved_bytes <> OLD.reserved_bytes
            OR NEW.reserved_at <> OLD.reserved_at OR NEW.write_deadline <> OLD.write_deadline
            OR NEW.orphan_after <> OLD.orphan_after
            OR NEW.cleanup_attempts < OLD.cleanup_attempts OR NEW.cleanup_attempts > OLD.cleanup_attempts + 1
            OR (OLD.quota_released AND NOT NEW.quota_released) THEN
            RAISE EXCEPTION 'Quarantine generation identity is immutable' USING ERRCODE = '23514';
        END IF;
        IF OLD.state = 'REFERENCED' AND (NEW.state <> OLD.state
            OR NEW.evidence_id IS DISTINCT FROM OLD.evidence_id
            OR NEW.referenced_at IS DISTINCT FROM OLD.referenced_at
            OR NEW.retained_bytes <> OLD.retained_bytes) THEN
            RAISE EXCEPTION 'Referenced quarantine data cannot be reclaimed' USING ERRCODE = '23514';
        END IF;
        IF OLD.state = 'DELETING' AND NEW.state NOT IN ('DELETING', 'TOMBSTONED')
            OR OLD.state = 'TOMBSTONED' AND NEW.state <> 'TOMBSTONED' THEN
            RAISE EXCEPTION 'Quarantine tombstone cannot be reopened' USING ERRCODE = '23514';
        END IF;
    END IF;
    PERFORM 1 FROM brands b WHERE b.id = NEW.brand_id AND b.workspace_id = NEW.workspace_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Quarantine workspace ownership mismatch' USING ERRCODE = '23514';
    END IF;
    IF NEW.state = 'REFERENCED' THEN
        PERFORM 1 FROM brand_evidence e WHERE e.id = NEW.evidence_id AND e.brand_id = NEW.brand_id
            AND (e.source_reference = 'quarantine:' || NEW.object_key OR e.fetched_reference = 'quarantine:' || NEW.object_key
                OR EXISTS (SELECT 1 FROM evidence_artifacts a WHERE a.evidence_id = e.id
                    AND a.storage_reference = 'quarantine:' || NEW.object_key));
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Quarantine evidence ownership mismatch' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.state IN ('DELETING', 'TOMBSTONED') THEN
        PERFORM 1 FROM brand_evidence e WHERE e.source_reference = 'quarantine:' || NEW.object_key
            OR e.fetched_reference = 'quarantine:' || NEW.object_key;
        IF FOUND OR EXISTS (SELECT 1 FROM evidence_artifacts a
            WHERE a.storage_reference = 'quarantine:' || NEW.object_key) THEN
            RAISE EXCEPTION 'Referenced quarantine data cannot be reclaimed' USING ERRCODE = '23514';
        END IF;
        PERFORM 1 FROM quarantine_write_intents i
            WHERE i.object_key = NEW.object_key AND i.registered_at IS NOT NULL;
        IF FOUND THEN
            RAISE EXCEPTION 'Registered quarantine intent cannot be reclaimed' USING ERRCODE = '23514';
        END IF;
        PERFORM 1 FROM jobs j WHERE j.payload_reference IN (NEW.object_key, 'quarantine:' || NEW.object_key)
            AND j.status IN ('PENDING', 'RUNNING', 'FAILED');
        IF FOUND THEN
            RAISE EXCEPTION 'Active quarantine job cannot be reclaimed' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
