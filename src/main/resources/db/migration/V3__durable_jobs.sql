CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    type VARCHAR(100) NOT NULL,
    payload_reference VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lease_owner VARCHAR(255),
    lease_expires_at TIMESTAMP(6) WITH TIME ZONE,
    last_error VARCHAR(1000),
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT jobs_idempotency_key_unique UNIQUE (idempotency_key),
    CONSTRAINT jobs_type_not_blank CHECK (btrim(type) <> ''),
    CONSTRAINT jobs_payload_reference_not_blank CHECK (btrim(payload_reference) <> ''),
    CONSTRAINT jobs_idempotency_key_not_blank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT jobs_status_check CHECK (status IN ('PENDING', 'RUNNING', 'FAILED', 'SUCCEEDED', 'DEAD')),
    CONSTRAINT jobs_attempts_check CHECK (attempts >= 0),
    CONSTRAINT jobs_max_attempts_check CHECK (max_attempts > 0),
    CONSTRAINT jobs_lease_check CHECK (
        (status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'RUNNING' AND lease_owner IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT jobs_completion_check CHECK (
        (status IN ('SUCCEEDED', 'DEAD') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'DEAD') AND completed_at IS NULL)
    )
);

CREATE INDEX jobs_ready_idx ON jobs (status, next_attempt_at);
CREATE INDEX jobs_expired_lease_idx ON jobs (status, lease_expires_at);
