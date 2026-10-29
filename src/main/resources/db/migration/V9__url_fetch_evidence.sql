ALTER TABLE brand_evidence DROP CONSTRAINT brand_evidence_status_check;
ALTER TABLE brand_evidence ADD CONSTRAINT brand_evidence_status_check
    CHECK (status IN ('PENDING', 'FETCHED', 'READY', 'FAILED'));

ALTER TABLE brand_evidence ADD COLUMN fetched_reference VARCHAR(500);
ALTER TABLE brand_evidence ADD COLUMN fetched_checksum VARCHAR(64);
ALTER TABLE brand_evidence ADD COLUMN fetched_media_type VARCHAR(100);
ALTER TABLE brand_evidence ADD COLUMN fetched_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE brand_evidence ADD CONSTRAINT brand_evidence_fetched_metadata_check CHECK (
    (fetched_reference IS NULL AND fetched_checksum IS NULL
        AND fetched_media_type IS NULL AND fetched_at IS NULL AND status <> 'FETCHED')
    OR (type = 'URL' AND status IN ('FETCHED', 'READY')
        AND fetched_reference IS NOT NULL AND fetched_reference LIKE 'quarantine:%'
        AND fetched_checksum IS NOT NULL AND fetched_checksum ~ '^[0-9a-f]{64}$'
        AND fetched_media_type IS NOT NULL AND fetched_media_type IN ('text/html', 'text/plain')
        AND fetched_at IS NOT NULL)
);
