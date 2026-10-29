CREATE TABLE quarantine_write_intents (
    id UUID PRIMARY KEY,
    brand_id UUID NOT NULL REFERENCES brands (id),
    object_key VARCHAR(400) NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    registered_at TIMESTAMP(6) WITH TIME ZONE,
    evidence_id UUID REFERENCES brand_evidence (id),
    CONSTRAINT quarantine_write_intents_object_key_check
        CHECK (object_key ~ ('^quarantine/' || brand_id::text
            || '/[0-9a-f]{64}[.](pdf|png|jpg)$')),
    CONSTRAINT quarantine_write_intents_registration_check
        CHECK ((registered_at IS NULL AND evidence_id IS NULL)
            OR (registered_at IS NOT NULL AND evidence_id IS NOT NULL
                AND registered_at >= started_at))
);

CREATE INDEX quarantine_write_intents_unresolved_idx
    ON quarantine_write_intents (started_at, id) WHERE registered_at IS NULL;
CREATE INDEX quarantine_write_intents_object_key_idx
    ON quarantine_write_intents (object_key);

-- A completed intent must point to the FILE evidence that references its exact key.
-- Completed records and pre-write identity fields are immutable audit data.
CREATE FUNCTION validate_quarantine_write_intent() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.registered_at IS NOT NULL OR NEW.brand_id <> OLD.brand_id
            OR NEW.object_key <> OLD.object_key OR NEW.started_at <> OLD.started_at THEN
            RAISE EXCEPTION 'Quarantine write intent is immutable' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.evidence_id IS NOT NULL THEN
        PERFORM 1 FROM brand_evidence e
            WHERE e.id = NEW.evidence_id AND e.brand_id = NEW.brand_id
              AND e.type = 'FILE'
              AND e.source_reference = 'quarantine:' || NEW.object_key
            FOR SHARE OF e;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Invalid quarantine write provenance' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER quarantine_write_intent_guard
    BEFORE INSERT OR UPDATE ON quarantine_write_intents
    FOR EACH ROW EXECUTE FUNCTION validate_quarantine_write_intent();
