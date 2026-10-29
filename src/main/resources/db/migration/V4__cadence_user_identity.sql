CREATE TABLE cadence_users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    external_subject VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT cadence_users_external_subject_unique UNIQUE (external_subject),
    CONSTRAINT cadence_users_external_subject_not_blank CHECK (btrim(external_subject) <> ''),
    CONSTRAINT cadence_users_role_check CHECK (role IN ('STRATEGIST', 'CLIENT'))
);

CREATE TABLE cadence_user_client_access (
    user_id UUID NOT NULL,
    client_id UUID NOT NULL,
    PRIMARY KEY (user_id, client_id),
    CONSTRAINT cadence_user_client_access_user_fk
        FOREIGN KEY (user_id) REFERENCES cadence_users (id) ON DELETE CASCADE,
    CONSTRAINT cadence_user_client_access_client_fk
        FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE
);

CREATE INDEX cadence_user_client_access_client_idx
    ON cadence_user_client_access (client_id, user_id);
