ALTER TABLE brand_evidence ADD COLUMN original_file_name VARCHAR(255);
ALTER TABLE brand_evidence ADD COLUMN media_type VARCHAR(100);

ALTER TABLE brand_evidence
    ADD CONSTRAINT brand_evidence_file_metadata_check CHECK (
        (type = 'FILE' AND original_file_name IS NOT NULL AND media_type IS NOT NULL)
        OR (type <> 'FILE' AND original_file_name IS NULL AND media_type IS NULL)
    );
