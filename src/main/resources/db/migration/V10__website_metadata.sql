ALTER TABLE brand_evidence ADD COLUMN extracted_title VARCHAR(300);
ALTER TABLE brand_evidence ADD COLUMN extracted_description VARCHAR(2000);
ALTER TABLE brand_evidence ADD COLUMN extracted_text VARCHAR(16000);
ALTER TABLE brand_evidence ADD COLUMN extraction_warnings VARCHAR(500);
ALTER TABLE brand_evidence ADD COLUMN extraction_version VARCHAR(32);
ALTER TABLE brand_evidence ADD COLUMN extracted_at TIMESTAMP(6) WITH TIME ZONE;

-- Existing FETCHED rows remain valid without a metadata snapshot; no implicit refetch/backfill.
ALTER TABLE brand_evidence ADD CONSTRAINT brand_evidence_extraction_check CHECK (
    (extracted_title IS NULL AND extracted_description IS NULL AND extracted_text IS NULL
        AND extraction_warnings IS NULL AND extraction_version IS NULL AND extracted_at IS NULL)
    OR (type = 'URL' AND status IN ('FETCHED', 'READY') AND fetched_at IS NOT NULL
        AND extracted_title IS NOT NULL AND extracted_description IS NOT NULL
        AND extracted_text IS NOT NULL AND extraction_warnings IS NOT NULL
        AND extraction_version IS NOT NULL AND length(trim(extraction_version)) > 0
        AND extracted_at IS NOT NULL AND extracted_at >= fetched_at)
);
