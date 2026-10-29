-- Run after Flyway migrations. The runtime identity never owns the schema.
-- Audit writer is a fixed SECURITY DEFINER trigger; the business identity cannot forge/read it.
REVOKE ALL ON security_audit_events FROM cadence_runtime;
REVOKE ALL ON SEQUENCE security_audit_events_id_seq FROM cadence_runtime;
REVOKE ALL ON FUNCTION security_capture_audit(),security_audit_immutable() FROM cadence_runtime;
REVOKE ALL ON flyway_schema_history FROM cadence_runtime;
REVOKE UPDATE ON evidence_scan_results, evidence_artifacts, evidence_provider_calls,
    evidence_social_origins FROM cadence_runtime;
-- Only expired unbound provider-attempt records are removable; V18/V19 independently guard them.
GRANT DELETE ON evidence_provider_calls TO cadence_runtime;
-- Hibernate replaces these owned value collections when Brand analysis is updated.
GRANT DELETE ON brand_signature_fonts, brand_signature_voice_up,
    brand_signature_voice_down TO cadence_runtime;

-- Runtime can finish attempts but cannot rewrite their immutable source or erase receipts.
REVOKE UPDATE, DELETE ON brand_analysis_attempts FROM cadence_runtime;
GRANT UPDATE (status,completed_at,provider,model_version,prompt_version,warnings,output_checksum,updated_at,lock_version)
 ON brand_analysis_attempts TO cadence_runtime;

-- Strategy identity/provenance are INSERT-only. Draft value collections are guarded by V24.
REVOKE UPDATE, DELETE ON strategies FROM cadence_runtime;
GRANT UPDATE (status,posts_per_week,publishing_timezone,approved_by,approved_at,updated_at,lock_version) ON strategies TO cadence_runtime;
GRANT DELETE ON strategy_content_pillars,strategy_best_publishing_times TO cadence_runtime;

-- Identity content/lifecycle commands only; identity/lineage/version and root history are immutable.
-- V30 analysis_attempt_id is also INSERT-only; no UPDATE privilege is added for provenance.
-- V35 analysis_copied_by is likewise INSERT-only; source/approval guards include lineage.
REVOKE UPDATE,DELETE ON brand_identities FROM cadence_runtime;
GRANT UPDATE(primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption,status,approved_by,approved_at,updated_at,lock_version)
 ON brand_identities TO cadence_runtime;
GRANT DELETE ON identity_fonts,identity_voice_up,identity_voice_down TO cadence_runtime;

-- Human-reviewed input snapshots are append-only. No runtime rewrite or deletion.
REVOKE UPDATE,DELETE ON evidence_admissions FROM cadence_runtime;
REVOKE UPDATE,DELETE ON evidence_admission_withdrawals FROM cadence_runtime;
REVOKE UPDATE,DELETE ON brand_analysis_sources,brand_analysis_citations FROM cadence_runtime;

REVOKE UPDATE,DELETE ON url_text_snapshots FROM cadence_runtime;

REVOKE UPDATE, DELETE ON social_text_snapshots FROM cadence_runtime;

-- Generation input/history immutable; only current operation/dispatch transitions are writable.
REVOKE UPDATE,DELETE ON ai_generation_operations,ai_generation_sources,ai_generation_attempts FROM cadence_runtime;
GRANT UPDATE(status,result_id,result_json,completed_at,updated_at,lock_version) ON ai_generation_operations TO cadence_runtime;
GRANT UPDATE(dispatched_at,analysis_id,finished_at,outcome,reason,updated_at,lock_version) ON ai_generation_attempts TO cadence_runtime;

-- Ledger history and mutex identity are immutable to the application.
REVOKE INSERT,UPDATE,DELETE ON generation_budget_mutex FROM cadence_runtime;
GRANT UPDATE(id) ON generation_budget_mutex TO cadence_runtime;
REVOKE UPDATE,DELETE ON generation_reservations FROM cadence_runtime;
GRANT UPDATE(state,dispatched_at,attempt_number,settled_at,actual,usage_origin,usage_evidence,updated_at,lock_version)
 ON generation_reservations TO cadence_runtime;

-- Full Playbook value replacements only; generation receipt remains INSERT-only.
GRANT UPDATE(content_format_version,whitespace_status,edited_by,edited_at) ON strategies TO cadence_runtime;
GRANT DELETE ON strategy_playbook_pillar_ids,strategy_playbook_audience,strategy_playbook_channels,strategy_playbook_goals,strategy_playbook_tones,strategy_playbook_hypotheses,strategy_playbook_slots TO cadence_runtime;

-- Post identity/source and revision history: only a draft's content/audit can change.
REVOKE UPDATE,DELETE ON content_posts,post_revisions FROM cadence_runtime;
-- SELECT FOR UPDATE requires an UPDATE column privilege; the immutable trigger rejects writes.
GRANT UPDATE(lock_version) ON content_posts TO cadence_runtime;
GRANT UPDATE(title,caption,format,media_prompt,video_script,edited_by,edited_at,status,approved_by,approved_at,updated_at,lock_version)
 ON post_revisions TO cadence_runtime;
GRANT DELETE ON post_revision_hashtags TO cadence_runtime;
-- Legacy writer is retired; reads/metrics FK remain available.
REVOKE INSERT,UPDATE,DELETE ON social_posts,social_post_hashtags FROM cadence_runtime;

-- Generated Content receipts are insert-only; revision provenance cannot be updated.
REVOKE UPDATE,DELETE ON content_generation_batches FROM cadence_runtime;
-- Append-only editorial scheduling ledger: no runtime rewrite/delete capability.
REVOKE UPDATE, DELETE ON post_schedule_events FROM cadence_runtime;
-- Retained media provenance and historical references are not removable.
REVOKE UPDATE, DELETE ON media_assets FROM cadence_runtime;
GRANT UPDATE (status,moderation,scan_result,scan_engine,scan_signature,scanned_at,completed_at,verification_failure,reviewed_by,reviewed_at,archived_by,archived_at,updated_at,lock_version) ON media_assets TO cadence_runtime;
GRANT DELETE ON post_revision_media TO cadence_runtime;

-- Captured CSV source links/history retained; only a fenced first publication can change a batch.
REVOKE UPDATE,DELETE ON csv_exports,csv_export_items FROM cadence_runtime;
GRANT UPDATE(artifact,artifact_checksum,published_attempt,published_worker,completed_at,updated_at,lock_version) ON csv_exports TO cadence_runtime;
-- Raw receipts and normalized/correction history retained. Only fenced publication is mutable.
REVOKE UPDATE,DELETE ON metric_imports,metric_observations,metric_import_results FROM cadence_runtime;
GRANT UPDATE(completed_at,published_attempt,published_worker,updated_at,lock_version) ON metric_imports TO cadence_runtime;
REVOKE UPDATE,DELETE ON metric_attributions FROM cadence_runtime;
REVOKE UPDATE,DELETE ON recommendation_keys,recommendation_insights,recommendation_metric_links,recommendation_resolutions FROM cadence_runtime;
REVOKE UPDATE,DELETE ON creator_reports FROM cadence_runtime;
GRANT UPDATE(artifact,artifact_checksum,published_attempt,published_worker,completed_at,updated_at,lock_version) ON creator_reports TO cadence_runtime;
