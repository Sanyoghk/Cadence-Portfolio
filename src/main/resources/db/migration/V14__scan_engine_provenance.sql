ALTER TABLE evidence_scan_results ADD COLUMN scanner_engine VARCHAR(100);
ALTER TABLE evidence_scan_results ADD COLUMN signature_version VARCHAR(100);

-- Existing V11-V13 rows were produced before the scanner contract exposed identity. Keep their
-- history honest instead of inventing a provider/version.
UPDATE evidence_scan_results
SET scanner_engine = 'legacy-unknown', signature_version = 'legacy-unknown';

ALTER TABLE evidence_scan_results ALTER COLUMN scanner_engine SET NOT NULL;
ALTER TABLE evidence_scan_results ALTER COLUMN signature_version SET NOT NULL;
ALTER TABLE evidence_scan_results ADD CONSTRAINT evidence_scan_engine_check CHECK (
    char_length(scanner_engine) BETWEEN 1 AND 100
    AND scanner_engine ~ '^[A-Za-z0-9]'
    AND scanner_engine !~ '[^A-Za-z0-9._:+/@ -]'
    AND char_length(signature_version) BETWEEN 1 AND 100
    AND signature_version ~ '^[A-Za-z0-9]'
    AND signature_version !~ '[^A-Za-z0-9._:+/@ -]'
);
