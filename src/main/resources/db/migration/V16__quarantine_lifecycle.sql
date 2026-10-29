-- New writes use never-reused generations. Historical shared keys remain readable and are
-- deliberately excluded from automatic deletion until an operator migrates their ownership.
CREATE TABLE quarantine_objects (
    id UUID PRIMARY KEY,
    lock_version BIGINT NOT NULL DEFAULT 0,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    brand_id UUID NOT NULL REFERENCES brands(id),
    object_key VARCHAR(400) NOT NULL UNIQUE,
    reserved_bytes BIGINT NOT NULL,
    retained_bytes BIGINT NOT NULL,
    reserved_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    write_deadline TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    orphan_after TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    state VARCHAR(16) NOT NULL,
    evidence_id UUID REFERENCES brand_evidence(id),
    referenced_at TIMESTAMP(6) WITH TIME ZONE,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    release_after TIMESTAMP(6) WITH TIME ZONE,
    next_cleanup_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    quota_released BOOLEAN NOT NULL DEFAULT FALSE,
    cleanup_attempts BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT quarantine_objects_key_check CHECK (
        object_key ~ ('^quarantine/writes/' || workspace_id::text || '/' || id::text
            || '[.](pdf|png|jpg|html|txt)$')),
    CONSTRAINT quarantine_objects_size_check CHECK (
        reserved_bytes BETWEEN 1 AND 10485760 AND retained_bytes BETWEEN 1 AND reserved_bytes),
    CONSTRAINT quarantine_objects_time_check CHECK (
        write_deadline > reserved_at AND orphan_after > write_deadline
        AND next_cleanup_at >= orphan_after AND cleanup_attempts >= 0),
    CONSTRAINT quarantine_objects_state_check CHECK (
        (state = 'RESERVED' AND evidence_id IS NULL AND referenced_at IS NULL
            AND deleted_at IS NULL AND release_after IS NULL AND NOT quota_released
            AND retained_bytes = reserved_bytes AND cleanup_attempts = 0)
        OR (state = 'REFERENCED' AND evidence_id IS NOT NULL AND referenced_at IS NOT NULL
            AND referenced_at >= reserved_at AND referenced_at < write_deadline
            AND deleted_at IS NULL AND release_after IS NULL AND NOT quota_released
            AND cleanup_attempts = 0)
        OR (state = 'DELETING' AND evidence_id IS NULL AND referenced_at IS NULL
            AND deleted_at IS NULL AND release_after IS NULL AND NOT quota_released
            AND cleanup_attempts > 0)
        OR (state = 'TOMBSTONED' AND evidence_id IS NULL AND referenced_at IS NULL
            AND deleted_at IS NOT NULL AND deleted_at >= orphan_after
            AND release_after > deleted_at AND cleanup_attempts > 0))
);
CREATE INDEX quarantine_objects_quota_idx ON quarantine_objects(workspace_id)
    WHERE NOT quota_released;
CREATE INDEX quarantine_objects_hourly_writes_idx ON quarantine_objects(workspace_id, reserved_at);
CREATE INDEX quarantine_objects_cleanup_idx ON quarantine_objects(next_cleanup_at, id)
    WHERE state <> 'REFERENCED';

CREATE FUNCTION validate_quarantine_object() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Quarantine generation tombstones must be retained' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id <> OLD.id OR NEW.workspace_id <> OLD.workspace_id OR NEW.brand_id <> OLD.brand_id
            OR NEW.object_key <> OLD.object_key OR NEW.reserved_bytes <> OLD.reserved_bytes
            OR NEW.reserved_at <> OLD.reserved_at OR NEW.write_deadline <> OLD.write_deadline
            OR NEW.orphan_after <> OLD.orphan_after
            OR NEW.cleanup_attempts < OLD.cleanup_attempts
            OR NEW.cleanup_attempts > OLD.cleanup_attempts + 1
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
        PERFORM 1 FROM brand_evidence e WHERE e.id = NEW.evidence_id
            AND e.brand_id = NEW.brand_id AND (e.source_reference = 'quarantine:' || NEW.object_key
                OR e.fetched_reference = 'quarantine:' || NEW.object_key);
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Quarantine evidence ownership mismatch' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.state IN ('DELETING', 'TOMBSTONED') THEN
        PERFORM 1 FROM brand_evidence e WHERE e.source_reference = 'quarantine:' || NEW.object_key
            OR e.fetched_reference = 'quarantine:' || NEW.object_key;
        IF FOUND THEN
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
CREATE TRIGGER quarantine_object_guard BEFORE INSERT OR UPDATE OR DELETE ON quarantine_objects
    FOR EACH ROW EXECUTE FUNCTION validate_quarantine_object();

-- Publication acquires the same object lock used by cleanup, including raw SQL callers. A
-- deletion claim and publication can never both succeed for the same generation.
CREATE FUNCTION validate_managed_quarantine_reference() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    ref TEXT;
    tracked quarantine_objects%ROWTYPE;
BEGIN
    FOREACH ref IN ARRAY ARRAY[NEW.source_reference, NEW.fetched_reference] LOOP
        IF ref LIKE 'quarantine:quarantine/writes/%' THEN
            SELECT * INTO tracked FROM quarantine_objects
                WHERE object_key = substring(ref FROM length('quarantine:') + 1) FOR UPDATE;
            IF NOT FOUND OR tracked.brand_id <> NEW.brand_id
                OR tracked.state NOT IN ('RESERVED', 'REFERENCED')
                OR (tracked.state = 'RESERVED' AND clock_timestamp() >= tracked.write_deadline)
                OR (tracked.state = 'REFERENCED' AND tracked.evidence_id <> NEW.id) THEN
                RAISE EXCEPTION 'Quarantine publication is not authorized' USING ERRCODE = '23514';
            END IF;
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER managed_quarantine_reference_guard
    BEFORE INSERT OR UPDATE OF source_reference, fetched_reference ON brand_evidence
    FOR EACH ROW EXECUTE FUNCTION validate_managed_quarantine_reference();

CREATE FUNCTION validate_managed_quarantine_job() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    tracked quarantine_objects%ROWTYPE;
    key TEXT;
BEGIN
    IF NEW.status IN ('PENDING', 'RUNNING', 'FAILED') AND (
        NEW.payload_reference LIKE 'quarantine:quarantine/writes/%'
        OR NEW.payload_reference LIKE 'quarantine/writes/%') THEN
        key := replace(NEW.payload_reference, 'quarantine:quarantine/', 'quarantine/');
        SELECT * INTO tracked FROM quarantine_objects WHERE object_key = key FOR UPDATE;
        IF NOT FOUND OR tracked.state NOT IN ('RESERVED', 'REFERENCED')
            OR (tracked.state = 'RESERVED' AND clock_timestamp() >= tracked.write_deadline) THEN
            RAISE EXCEPTION 'Quarantine job publication is not authorized' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER managed_quarantine_job_guard
    BEFORE INSERT OR UPDATE OF payload_reference, status ON jobs
    FOR EACH ROW EXECUTE FUNCTION validate_managed_quarantine_job();

ALTER TABLE quarantine_write_intents DROP CONSTRAINT quarantine_write_intents_object_key_check;
ALTER TABLE quarantine_write_intents ADD CONSTRAINT quarantine_write_intents_object_key_check
    CHECK (object_key ~ ('^quarantine/' || brand_id::text || '/[0-9a-f]{64}[.](pdf|png|jpg)$')
        OR object_key ~ '^quarantine/writes/[0-9a-f-]{36}/[0-9a-f-]{36}[.](pdf|png|jpg)$');

-- Keep the historical intent API for recovery inventories while accepting managed generations.
CREATE OR REPLACE FUNCTION validate_quarantine_write_intent() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.registered_at IS NOT NULL OR NEW.brand_id <> OLD.brand_id
            OR NEW.object_key <> OLD.object_key OR NEW.started_at <> OLD.started_at THEN
            RAISE EXCEPTION 'Quarantine write intent is immutable' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.object_key LIKE 'quarantine/writes/%' THEN
        PERFORM 1 FROM quarantine_objects q WHERE q.object_key = NEW.object_key
            AND q.brand_id = NEW.brand_id AND q.state IN ('RESERVED', 'REFERENCED')
            AND (q.state = 'REFERENCED' OR clock_timestamp() < q.write_deadline) FOR UPDATE OF q;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Invalid managed quarantine write intent' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.evidence_id IS NOT NULL THEN
        PERFORM 1 FROM brand_evidence e WHERE e.id = NEW.evidence_id AND e.brand_id = NEW.brand_id
            AND e.type = 'FILE' AND e.source_reference = 'quarantine:' || NEW.object_key FOR SHARE OF e;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Invalid quarantine write provenance' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
