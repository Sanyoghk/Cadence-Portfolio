-- Rejection retains the exact extraction provenance; it never grants READY approval.
ALTER TABLE brand_evidence DROP CONSTRAINT brand_evidence_extraction_check;
ALTER TABLE brand_evidence ADD CONSTRAINT brand_evidence_extraction_check CHECK (
    (extracted_title IS NULL AND extracted_description IS NULL AND extracted_text IS NULL
        AND extraction_warnings IS NULL AND extraction_version IS NULL AND extracted_at IS NULL)
    OR (type = 'URL' AND status IN ('FETCHED', 'READY', 'FAILED') AND fetched_at IS NOT NULL
        AND extracted_title IS NOT NULL AND extracted_description IS NOT NULL
        AND extracted_text IS NOT NULL AND extraction_warnings IS NOT NULL
        AND extraction_version IS NOT NULL AND length(trim(extraction_version)) > 0
        AND extracted_at IS NOT NULL AND extracted_at >= fetched_at)
);
-- PostgreSQL forbids changing the type of a column named in UPDATE OF. Recreate the same
-- guard under this migration's exclusive table lock; concurrent writes cannot bypass it.
DROP TRIGGER managed_quarantine_reference_guard ON brand_evidence;
ALTER TABLE brand_evidence ALTER COLUMN source_reference TYPE VARCHAR(20000);
CREATE TRIGGER managed_quarantine_reference_guard
    BEFORE INSERT OR UPDATE OF source_reference, fetched_reference ON brand_evidence
    FOR EACH ROW EXECUTE FUNCTION validate_managed_quarantine_reference();
ALTER TABLE brand_evidence ADD CONSTRAINT brand_evidence_source_size CHECK (
    char_length(source_reference) BETWEEN 1 AND
        CASE WHEN type = 'TEXT' THEN 20000 ELSE 4096 END
);
