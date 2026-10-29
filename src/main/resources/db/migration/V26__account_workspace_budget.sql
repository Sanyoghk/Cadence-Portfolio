-- Atomic counter provides a hard ceiling even for concurrent membership inserts.
-- Existing accounts keep all memberships; accounts already over the ceiling cannot add more.
ALTER TABLE cadence_users ADD COLUMN workspace_membership_count INTEGER NOT NULL DEFAULT 0
    CHECK (workspace_membership_count >= 0);
UPDATE cadence_users u SET workspace_membership_count =
    (SELECT count(*) FROM workspace_memberships m WHERE m.user_id = u.id);

CREATE FUNCTION enforce_workspace_membership_budget() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE cadence_users SET workspace_membership_count = workspace_membership_count + 1
        WHERE id = NEW.user_id AND workspace_membership_count < 10;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Workspace membership budget exceeded' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE cadence_users SET workspace_membership_count = workspace_membership_count - 1
        WHERE id = OLD.user_id;
        RETURN OLD;
    ELSIF NEW.user_id IS DISTINCT FROM OLD.user_id
        OR NEW.workspace_id IS DISTINCT FROM OLD.workspace_id THEN
        RAISE EXCEPTION 'Workspace membership identity is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER workspace_membership_budget
BEFORE INSERT OR UPDATE OR DELETE ON workspace_memberships
FOR EACH ROW EXECUTE FUNCTION enforce_workspace_membership_budget();

-- The counter is maintained only by the membership trigger, not by ordinary user updates.
CREATE FUNCTION guard_workspace_membership_count() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (TG_OP = 'INSERT' AND NEW.workspace_membership_count <> 0)
        OR (TG_OP = 'UPDATE' AND NEW.workspace_membership_count <> OLD.workspace_membership_count
            AND pg_trigger_depth() < 2) THEN
        RAISE EXCEPTION 'Workspace membership count is managed by memberships' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER workspace_membership_count_guard
BEFORE INSERT OR UPDATE OF workspace_membership_count ON cadence_users
FOR EACH ROW EXECUTE FUNCTION guard_workspace_membership_count();
