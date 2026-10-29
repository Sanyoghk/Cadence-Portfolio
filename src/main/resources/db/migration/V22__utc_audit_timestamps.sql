-- Legacy timestamps have no recoverable timezone. This project's existing data is
-- local demonstration data only; explicitly interpret legacy wall times as UTC.
-- For any deployment with historical real data, establish its origin timezone
-- before applying this migration. Do not rely on the session TimeZone setting.
DO $$
DECLARE audit_table text;
BEGIN
  FOREACH audit_table IN ARRAY ARRAY[
    'workspaces', 'brands', 'strategies', 'social_posts', 'post_metrics',
    'recommendations', 'jobs', 'cadence_users', 'brand_evidence',
    'evidence_scan_results', 'evidence_artifacts'
  ] LOOP
    EXECUTE format(
      'ALTER TABLE %I ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE ''UTC'', ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE ''UTC''',
      audit_table);
  END LOOP;
END $$;
