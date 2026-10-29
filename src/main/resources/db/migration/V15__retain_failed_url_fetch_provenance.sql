-- A URL that fails malware scanning remains quarantined and must retain the exact fetched-object
-- provenance needed for audit and cleanup. A URL that failed before fetching still has no metadata.
ALTER TABLE brand_evidence DROP CONSTRAINT brand_evidence_fetched_metadata_check;
ALTER TABLE brand_evidence ADD CONSTRAINT brand_evidence_fetched_metadata_check CHECK (
    (fetched_reference IS NULL AND fetched_checksum IS NULL
        AND fetched_media_type IS NULL AND fetched_at IS NULL AND status <> 'FETCHED')
    OR (type = 'URL' AND status IN ('FETCHED', 'READY', 'FAILED')
        AND fetched_reference IS NOT NULL AND fetched_reference LIKE 'quarantine:%'
        AND fetched_checksum IS NOT NULL AND fetched_checksum ~ '^[0-9a-f]{64}$'
        AND fetched_media_type IS NOT NULL AND fetched_media_type IN ('text/html', 'text/plain')
        AND fetched_at IS NOT NULL)
);
