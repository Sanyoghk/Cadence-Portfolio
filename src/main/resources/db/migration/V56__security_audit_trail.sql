-- No retrospective events: legacy provenance is not reconstructed.
-- A failed/rolled-back command also rolls back its audit entry. Denials are HTTP security events.
CREATE TABLE security_audit_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY (MAXVALUE 1000000 NO CYCLE) PRIMARY KEY,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 transaction_id BIGINT NOT NULL DEFAULT txid_current(),
 action VARCHAR(64) NOT NULL CHECK(action ~ '^[A-Z_]{1,64}$'),
 resource_kind VARCHAR(64) NOT NULL,
 resource_id UUID NOT NULL,
 workspace_id UUID,
 actor_id UUID,
 actor_origin VARCHAR(24) NOT NULL CHECK(actor_origin IN ('DOMAIN_ACTOR','DATABASE_PRINCIPAL')),
 database_principal NAME NOT NULL,
 result VARCHAR(16) NOT NULL DEFAULT 'COMMITTED' CHECK(result='COMMITTED'),
 CHECK((actor_origin='DOMAIN_ACTOR')=(actor_id IS NOT NULL))
);
CREATE INDEX security_audit_workspace_time_idx ON security_audit_events(workspace_id,occurred_at,id);
CREATE INDEX security_audit_resource_idx ON security_audit_events(resource_kind,resource_id,id);

CREATE FUNCTION security_audit_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Security audit is append only' USING ERRCODE='23514';END $$;
CREATE TRIGGER security_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON security_audit_events
 FOR EACH STATEMENT EXECUTE FUNCTION security_audit_immutable();

-- Only the fixed triggers write; no callable function takes client-selected audit values.
-- Fully qualified relations and a fixed search_path prevent SECURITY DEFINER name hijacking.
CREATE FUNCTION security_capture_audit() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE row_data JSONB; previous JSONB; actor UUID; resource UUID; workspace UUID; brand UUID; event VARCHAR(64);
BEGIN
 IF TG_OP='DELETE' THEN row_data=to_jsonb(OLD);ELSE row_data=to_jsonb(NEW);END IF;
 IF TG_OP='UPDATE' THEN previous=to_jsonb(OLD);END IF;
 resource=(row_data->>'id')::uuid;
 IF TG_TABLE_NAME IN('brand_identities','strategies','post_revisions') THEN
  IF TG_OP<>'UPDATE' OR row_data->>'status'<>'APPROVED' OR previous->>'status'='APPROVED' THEN RETURN NULL;END IF;
  actor=(row_data->>'approved_by')::uuid;event='APPROVE';
  brand=(row_data->>'brand_id')::uuid;
  IF TG_TABLE_NAME='post_revisions' THEN EXECUTE format('SELECT brand_id FROM %I.content_posts WHERE id=$1',TG_TABLE_SCHEMA) INTO brand USING (row_data->>'post_id')::uuid;END IF;
 ELSIF TG_TABLE_NAME='post_schedule_events' THEN
  actor=(row_data->>'actor_id')::uuid;brand=(row_data->>'brand_id')::uuid;event='SCHEDULE_'||(row_data->>'action');
 ELSIF TG_TABLE_NAME IN('csv_exports','creator_reports') THEN
  actor=(row_data->>'actor_id')::uuid;brand=(row_data->>'brand_id')::uuid;
  IF TG_OP='INSERT' THEN event='CAPTURE';
  ELSIF previous->>'artifact' IS NULL AND row_data->>'artifact' IS NOT NULL THEN event='PUBLISH';
  ELSE RETURN NULL;END IF;
 ELSIF TG_TABLE_NAME='evidence_admissions' THEN
  actor=(row_data->>'reviewed_by')::uuid;event='EVIDENCE_ADMIT';
  EXECUTE format('SELECT brand_id FROM %I.brand_evidence WHERE id=$1',TG_TABLE_SCHEMA) INTO brand USING (row_data->>'evidence_id')::uuid;
 ELSIF TG_TABLE_NAME='evidence_admission_withdrawals' THEN
  actor=(row_data->>'withdrawn_by')::uuid;event='EVIDENCE_WITHDRAW';
  EXECUTE format('SELECT e.brand_id FROM %I.evidence_admissions a JOIN %I.brand_evidence e ON e.id=a.evidence_id WHERE a.id=$1',TG_TABLE_SCHEMA,TG_TABLE_SCHEMA) INTO brand USING (row_data->>'admission_id')::uuid;
 ELSIF TG_TABLE_NAME='workspace_memberships' THEN
  resource=(row_data->>'user_id')::uuid;workspace=(row_data->>'workspace_id')::uuid;
  event=CASE TG_OP WHEN 'INSERT' THEN 'MEMBERSHIP_GRANT' ELSE 'MEMBERSHIP_REVOKE' END;
 ELSIF TG_TABLE_NAME='cadence_users' THEN
  IF row_data->>'role' IS NOT DISTINCT FROM previous->>'role' AND row_data->>'active' IS NOT DISTINCT FROM previous->>'active' THEN RETURN NULL;END IF;
  event='ACCOUNT_PERMISSIONS_CHANGE';
 ELSE RAISE EXCEPTION 'Unsupported audit origin' USING ERRCODE='23514';END IF;
 IF brand IS NOT NULL THEN EXECUTE format('SELECT workspace_id FROM %I.brands WHERE id=$1',TG_TABLE_SCHEMA) INTO workspace USING brand;END IF;
 EXECUTE format('INSERT INTO %I.security_audit_events(action,resource_kind,resource_id,workspace_id,actor_id,actor_origin,database_principal) VALUES($1,$2,$3,$4,$5,$6,$7)',TG_TABLE_SCHEMA)
 USING event,TG_TABLE_NAME,resource,workspace,actor,CASE WHEN actor IS NULL THEN 'DATABASE_PRINCIPAL' ELSE 'DOMAIN_ACTOR' END,session_user;
 RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION security_capture_audit() FROM PUBLIC;
REVOKE ALL ON FUNCTION security_audit_immutable() FROM PUBLIC;
CREATE TRIGGER security_identity_approval AFTER UPDATE ON brand_identities FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_strategy_approval AFTER UPDATE ON strategies FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_post_approval AFTER UPDATE ON post_revisions FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_schedule AFTER INSERT ON post_schedule_events FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_export AFTER INSERT OR UPDATE ON csv_exports FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_report AFTER INSERT OR UPDATE ON creator_reports FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_admit AFTER INSERT ON evidence_admissions FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_withdraw AFTER INSERT ON evidence_admission_withdrawals FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_membership AFTER INSERT OR DELETE ON workspace_memberships FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
CREATE TRIGGER security_account AFTER UPDATE OF role,active ON cadence_users FOR EACH ROW EXECUTE FUNCTION security_capture_audit();
REVOKE ALL ON security_audit_events FROM PUBLIC;
REVOKE ALL ON SEQUENCE security_audit_events_id_seq FROM PUBLIC;
-- Close the default-privilege window inside the migration itself, before the separate grants job.
-- Disposable migrations need not have a runtime role; deployed databases normally already do.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='cadence_runtime') THEN
  REVOKE ALL ON security_audit_events FROM cadence_runtime;
  REVOKE ALL ON SEQUENCE security_audit_events_id_seq FROM cadence_runtime;
  REVOKE ALL ON FUNCTION security_capture_audit(),security_audit_immutable() FROM cadence_runtime;
 END IF;
END $$;
