ALTER TABLE clients RENAME TO workspaces;
ALTER TABLE workspaces DROP CONSTRAINT clients_status_check;
UPDATE workspaces SET status = 'ACTIVE' WHERE status = 'PROSPECT';
ALTER TABLE workspaces
    ADD CONSTRAINT workspaces_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'));

ALTER TABLE brands RENAME COLUMN client_id TO workspace_id;
ALTER INDEX brands_client_idx RENAME TO brands_workspace_idx;

ALTER TABLE cadence_user_client_access RENAME TO workspace_memberships;
ALTER TABLE workspace_memberships RENAME COLUMN client_id TO workspace_id;
ALTER INDEX cadence_user_client_access_client_idx RENAME TO workspace_memberships_workspace_idx;

ALTER TABLE cadence_users DROP CONSTRAINT cadence_users_role_check;
UPDATE cadence_users SET role = 'OWNER' WHERE role = 'STRATEGIST';
UPDATE cadence_users SET role = 'VIEWER' WHERE role = 'CLIENT';
ALTER TABLE cadence_users
    ADD CONSTRAINT cadence_users_role_check CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'));
