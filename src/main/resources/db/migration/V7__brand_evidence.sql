CREATE TABLE brand_evidence (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    brand_id UUID NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_reference VARCHAR(4096) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    warning VARCHAR(1000),
    CONSTRAINT brand_evidence_brand_fk FOREIGN KEY (brand_id) REFERENCES brands (id),
    CONSTRAINT brand_evidence_brand_checksum_unique UNIQUE (brand_id, checksum),
    CONSTRAINT brand_evidence_type_check CHECK (type IN ('URL', 'TEXT', 'FILE')),
    CONSTRAINT brand_evidence_status_check CHECK (status IN ('PENDING', 'READY', 'FAILED'))
);

CREATE INDEX brand_evidence_brand_captured_idx
    ON brand_evidence (brand_id, captured_at DESC);
