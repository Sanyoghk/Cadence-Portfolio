
-- Typed social projection. No origin/admission/Identity history is rewritten.
CREATE TABLE social_text_snapshots (
 id UUID PRIMARY KEY, evidence_id UUID NOT NULL REFERENCES evidence_social_origins(evidence_id),
 source_version BIGINT NOT NULL CHECK(source_version>=0), provider_call_id UUID NOT NULL REFERENCES evidence_provider_calls(id),
 source_checksum VARCHAR(64) NOT NULL CHECK(source_checksum ~ '^[0-9a-f]{64}$'),
 captured_at TIMESTAMPTZ NOT NULL, source_captured_at TIMESTAMPTZ NOT NULL,
 payload VARCHAR(20000) NOT NULL CHECK(octet_length(payload)<=20000),
 content_checksum VARCHAR(64) NOT NULL CHECK(content_checksum=encode(sha256(convert_to(payload,'UTF8')),'hex')),
 post_ids VARCHAR(1000) NOT NULL, projection_version VARCHAR(32) NOT NULL CHECK(projection_version='social-text-v1'),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version=0), UNIQUE(evidence_id,source_version)
);
-- Strict canonical official payload: reconstructing original also rejects duplicate/unknown fields.
CREATE FUNCTION social_text_projection(raw TEXT, provider TEXT, account TEXT, api TEXT, captured TIMESTAMPTZ)
 RETURNS TEXT LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE j JSONB; p JSONB; original TEXT; projected TEXT; originals TEXT=''; projections TEXT='';
 previous TEXT; pid TEXT; at_time TIMESTAMPTZ; n INTEGER=0; meaningful BOOLEAN;
BEGIN
 j=raw::jsonb;
 IF jsonb_typeof(j)<>'object' OR j->>'format'<>'approved-social-v1' OR j->>'provider' IS DISTINCT FROM provider
  OR j->>'accountId' IS DISTINCT FROM account OR j->>'apiVersion' IS DISTINCT FROM api
  OR jsonb_typeof(j->'accountName') IS DISTINCT FROM 'string' OR length(j->>'accountName')*2-length(regexp_replace(j->>'accountName',U&'[\+010000-\+10FFFF]','','g'))>128
  OR jsonb_typeof(j->'biography') IS DISTINCT FROM 'string' OR length(j->>'biography')>300
  OR jsonb_typeof(j->'posts') IS DISTINCT FROM 'array'
  OR provider NOT IN ('INSTAGRAM','TIKTOK')
  OR (provider='INSTAGRAM' AND (account !~ '^[0-9]{1,32}$' OR api !~ '^v[1-9][0-9]*\.0$'))
  OR (provider='TIKTOK' AND (api<>'v2' OR j->>'biography'<>''))
 THEN RAISE EXCEPTION 'Invalid social projection' USING ERRCODE='23514'; END IF;
 IF ((j->>'accountName')||(j->>'biography')) ~ U&'[\0001-\0008\000B\000C\000E-\001F\007F-\009F]' THEN RAISE EXCEPTION 'Invalid social text controls' USING ERRCODE='23514'; END IF;
 meaningful=btrim((j->>'accountName')||(j->>'biography'))<>'';
 FOR p IN SELECT value FROM jsonb_array_elements(j->'posts') LOOP
  n=n+1; pid=p->>'id';
  IF n>20 OR jsonb_typeof(p->'id') IS DISTINCT FROM 'string' OR pid !~ '^[0-9]{1,32}$'
   OR (previous IS NOT NULL AND previous COLLATE "C">=pid COLLATE "C")
   OR jsonb_typeof(p->'publishedAt') IS DISTINCT FROM 'string'
   OR jsonb_typeof(p->'text') IS DISTINCT FROM 'string' OR length(p->>'text')>300
   OR (provider='TIKTOK' AND (jsonb_typeof(p->'title') IS DISTINCT FROM 'string' OR length(p->>'title')>100))
  THEN RAISE EXCEPTION 'Invalid social post' USING ERRCODE='23514'; END IF;
  at_time=(p->>'publishedAt')::timestamptz;
  IF at_time<'2000-01-01T00:00:00Z'::timestamptz OR at_time>captured+interval '1 day'
   THEN RAISE EXCEPTION 'Invalid social post time' USING ERRCODE='23514'; END IF;
  previous=pid;
  projected='{"publishedAt":'||to_json(p->>'publishedAt')::text||',"text":'||to_json(p->>'text')::text
   ||CASE WHEN provider='TIKTOK' THEN ',"title":'||to_json(p->>'title')::text ELSE '' END||'}';
  original='{"id":'||to_json(pid)::text||','||substring(projected from 2);
  IF n>1 THEN originals=originals||',';projections=projections||','; END IF;
  originals=originals||original;projections=projections||projected;
  IF ((p->>'text')||coalesce(p->>'title','')) ~ U&'[\0001-\0008\000B\000C\000E-\001F\007F-\009F]' THEN RAISE EXCEPTION 'Invalid social text controls' USING ERRCODE='23514'; END IF;
  meaningful=meaningful OR btrim((p->>'text')||coalesce(p->>'title',''))<>'';
 END LOOP;
 original='{"format":"approved-social-v1","provider":'||to_json(provider)::text||',"accountId":'||to_json(account)::text
  ||',"accountName":'||to_json(j->>'accountName')::text||',"biography":'||to_json(j->>'biography')::text
  ||',"apiVersion":'||to_json(api)::text||',"posts":['||originals||']}';
 projected='{"format":"social-text-v1","accountName":'||to_json(j->>'accountName')::text
  ||',"biography":'||to_json(j->>'biography')::text||',"posts":['||projections||']}';
 IF original<>raw OR NOT meaningful OR octet_length(projected)>20000
  THEN RAISE EXCEPTION 'Noncanonical social snapshot' USING ERRCODE='23514'; END IF;
 RETURN projected;
END $$;
CREATE FUNCTION guard_social_text_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE e brand_evidence%ROWTYPE; o evidence_social_origins%ROWTYPE; ids TEXT;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Social snapshot immutable' USING ERRCODE='23514'; END IF;
 SELECT * INTO e FROM brand_evidence WHERE id=NEW.evidence_id FOR UPDATE;
 SELECT * INTO o FROM evidence_social_origins WHERE evidence_id=e.id;
 SELECT '['||coalesce(string_agg(to_json(p.value->>'id')::text,',' ORDER BY p.ordinality),'')||']' INTO ids
  FROM jsonb_array_elements(e.source_reference::jsonb->'posts') WITH ORDINALITY p;
 IF e.id IS NULL OR o.evidence_id IS NULL OR e.type<>'TEXT' OR e.status<>'PENDING'
  OR e.lock_version<>NEW.source_version OR e.checksum IS DISTINCT FROM NEW.source_checksum
  OR e.checksum IS DISTINCT FROM o.checksum OR e.checksum<>encode(sha256(convert_to(E'TEXT\n'||e.source_reference,'UTF8')),'hex')
  OR NEW.provider_call_id<>o.provider_call_id OR NEW.captured_at<>o.captured_at
  OR NEW.source_captured_at<>e.captured_at OR o.captured_at>clock_timestamp()
  OR NEW.payload IS DISTINCT FROM social_text_projection(e.source_reference,o.provider,o.account_id,o.api_version,o.captured_at)
  OR NEW.post_ids IS DISTINCT FROM ids
  OR (SELECT count(*) FROM social_text_snapshots WHERE evidence_id=e.id)>=1000
  OR NOT EXISTS(SELECT 1 FROM brands b JOIN workspaces w ON w.id=b.workspace_id WHERE b.id=e.brand_id AND b.status='ACTIVE' AND w.status='ACTIVE')
 THEN RAISE EXCEPTION 'Invalid social snapshot' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER social_text_snapshot_guard BEFORE INSERT OR UPDATE OR DELETE ON social_text_snapshots
 FOR EACH ROW EXECUTE FUNCTION guard_social_text_snapshot();
ALTER TABLE evidence_admissions ADD social_snapshot_id UUID REFERENCES social_text_snapshots(id);
ALTER TABLE evidence_admissions ADD binding_expires_at TIMESTAMPTZ;
ALTER TABLE evidence_admissions DROP CONSTRAINT admission_policy_shape;
ALTER TABLE evidence_admissions ADD CONSTRAINT admission_policy_shape CHECK((social_snapshot_id IS NULL AND binding_expires_at IS NULL AND (
 (policy_version='text-review-v1' AND artifact_id IS NULL AND original_scan_id IS NULL
  AND latest_scan_id IS NULL AND renderer_version IS NULL AND scan_expires_at IS NULL AND url_snapshot_id IS NULL)
 OR (policy_version='pdf-text-review-v1' AND artifact_id IS NOT NULL AND original_scan_id IS NOT NULL
  AND latest_scan_id IS NOT NULL AND renderer_version IS NOT NULL AND scan_expires_at IS NOT NULL AND url_snapshot_id IS NULL
  AND renderer_version ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$'
  AND char_length(reviewed_text)*2-char_length(regexp_replace(reviewed_text,U&'[\+010000-\+10FFFF]','','g'))<=16000)
 OR (policy_version='url-text-review-v1' AND url_snapshot_id IS NOT NULL AND artifact_id IS NULL
  AND renderer_version IS NULL AND original_scan_id IS NOT NULL AND latest_scan_id IS NOT NULL
  AND original_scan_id=latest_scan_id AND scan_expires_at IS NOT NULL AND octet_length(reviewed_text)<=20000))) OR (policy_version='social-text-review-v1' AND social_snapshot_id IS NOT NULL AND binding_expires_at IS NOT NULL
 AND artifact_id IS NULL AND original_scan_id IS NULL AND latest_scan_id IS NULL AND renderer_version IS NULL
 AND scan_expires_at IS NULL AND url_snapshot_id IS NULL AND octet_length(reviewed_text)<=20000));
CREATE UNIQUE INDEX admission_social_context_unique ON evidence_admissions(evidence_id,source_version,brand_version,workspace_version,social_snapshot_id,binding_expires_at) WHERE policy_version='social-text-review-v1';

CREATE FUNCTION validate_social_admission(r evidence_admissions) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE s social_text_snapshots%ROWTYPE; e brand_evidence%ROWTYPE; o evidence_social_origins%ROWTYPE;
BEGIN
 SELECT * INTO e FROM brand_evidence WHERE id=r.evidence_id FOR UPDATE;
 SELECT * INTO o FROM evidence_social_origins WHERE evidence_id=e.id;
 SELECT * INTO s FROM social_text_snapshots WHERE id=r.social_snapshot_id;
 IF s.id IS NULL OR o.evidence_id IS NULL OR e.type<>'TEXT' OR e.status<>'PENDING'
  OR s.evidence_id<>e.id OR s.source_version<>e.lock_version OR r.source_version<>e.lock_version
  OR s.provider_call_id<>o.provider_call_id OR s.source_checksum<>e.checksum OR o.checksum<>e.checksum
  OR s.captured_at<>o.captured_at OR s.source_captured_at<>e.captured_at
  OR s.payload IS DISTINCT FROM social_text_projection(e.source_reference,o.provider,o.account_id,o.api_version,o.captured_at)
  OR r.reviewed_text IS DISTINCT FROM s.payload OR r.content_checksum IS DISTINCT FROM s.content_checksum
  OR r.source_checksum IS DISTINCT FROM e.checksum OR r.reviewed_at<s.captured_at
  OR r.binding_expires_at IS NULL OR r.binding_expires_at<=clock_timestamp()+interval '30 seconds'
  OR r.binding_expires_at<=r.reviewed_at+interval '30 seconds'
 THEN RAISE EXCEPTION 'Invalid social admission' USING ERRCODE='23514'; END IF;
 -- Current configuration/scopes/account are additionally checked by the local application port.
END $$;
CREATE OR REPLACE FUNCTION guard_evidence_admission() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE source brand_evidence%ROWTYPE; parent brands%ROWTYPE; ws workspaces%ROWTYPE;
 url url_text_snapshots%ROWTYPE;
 art evidence_artifacts%ROWTYPE; original evidence_scan_results%ROWTYPE; latest evidence_scan_results%ROWTYPE; obj quarantine_objects%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN
  RAISE EXCEPTION 'Evidence admission history is immutable' USING ERRCODE='23514';
 END IF;
 -- Match application Account -> Workspace -> Brand -> Evidence order.
 PERFORM 1 FROM cadence_users WHERE id=NEW.reviewed_by AND active AND role='OWNER' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Invalid evidence reviewer' USING ERRCODE='23514'; END IF;
 SELECT * INTO source FROM brand_evidence WHERE id=NEW.evidence_id;
 SELECT * INTO parent FROM brands WHERE id=source.brand_id;
 SELECT * INTO ws FROM workspaces WHERE id=parent.workspace_id FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=source.brand_id FOR UPDATE;
 SELECT * INTO source FROM brand_evidence WHERE id=NEW.evidence_id FOR UPDATE;
 IF source.id IS NULL OR parent.id IS NULL OR ws.id IS NULL OR source.brand_id<>parent.id
  OR parent.workspace_id<>ws.id
  OR (CASE WHEN NEW.policy_version='url-text-review-v1' THEN source.status<>'FETCHED' ELSE source.status<>'PENDING' END) OR parent.status<>'ACTIVE' OR ws.status<>'ACTIVE'
  OR source.lock_version<>NEW.source_version OR parent.lock_version<>NEW.brand_version
  OR ws.lock_version<>NEW.workspace_version
  OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=NEW.reviewed_by AND workspace_id=ws.id)
  OR NEW.source_checksum IS DISTINCT FROM source.checksum
  OR NEW.content_checksum IS DISTINCT FROM encode(sha256(convert_to(NEW.reviewed_text,'UTF8')),'hex')
  OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals w JOIN evidence_admissions a ON a.id=w.admission_id WHERE a.evidence_id=NEW.evidence_id AND a.source_version=NEW.source_version)
  OR (SELECT count(*) FROM evidence_admissions a WHERE a.evidence_id=NEW.evidence_id)>=1000
  OR NEW.reviewed_at<source.captured_at OR NEW.reviewed_at>clock_timestamp()+interval '5 seconds' THEN
  RAISE EXCEPTION 'Invalid evidence admission source or review' USING ERRCODE='23514';
 END IF;

 IF NEW.policy_version='text-review-v1' THEN
  IF EXISTS(SELECT 1 FROM evidence_social_origins WHERE evidence_id=source.id) OR source.type<>'TEXT' OR NEW.reviewed_text IS DISTINCT FROM source.source_reference
   OR NEW.source_checksum IS DISTINCT FROM encode(sha256(convert_to(E'TEXT\n'||NEW.reviewed_text,'UTF8')),'hex') THEN
   RAISE EXCEPTION 'Invalid text admission' USING ERRCODE='23514';
  END IF;

 ELSIF NEW.policy_version='social-text-review-v1' THEN
  PERFORM validate_social_admission(NEW);
 ELSIF NEW.policy_version='url-text-review-v1' THEN
  SELECT * INTO url FROM url_text_snapshots WHERE id=NEW.url_snapshot_id;
  SELECT * INTO latest FROM evidence_scan_results WHERE evidence_id=source.id ORDER BY scanned_at DESC,created_at DESC,id DESC LIMIT 1;
  SELECT * INTO obj FROM quarantine_objects WHERE evidence_id=source.id AND brand_id=parent.id AND workspace_id=ws.id
   AND 'quarantine:'||object_key=source.fetched_reference FOR UPDATE;
  IF source.type<>'URL' OR url.id IS NULL OR latest.id IS NULL OR obj.id IS NULL
   OR url.evidence_id<>source.id OR url.source_version<>source.lock_version
   OR url.source_checksum IS DISTINCT FROM source.checksum OR url.fetched_checksum IS DISTINCT FROM source.fetched_checksum
   OR url.storage_reference IS DISTINCT FROM source.fetched_reference
   OR url.fetched_at IS DISTINCT FROM source.fetched_at OR url.extracted_at IS DISTINCT FROM source.extracted_at
   OR url.captured_at IS DISTINCT FROM source.captured_at
   OR url.media_type IS DISTINCT FROM source.fetched_media_type OR url.extraction_version IS DISTINCT FROM source.extraction_version
   OR url.warnings IS DISTINCT FROM source.extraction_warnings
   OR url.payload IS DISTINCT FROM url_text_projection(source.extracted_title,source.extracted_description,source.extracted_text)
   OR NEW.reviewed_text IS DISTINCT FROM url.payload OR NEW.content_checksum IS DISTINCT FROM url.content_checksum
   OR btrim(source.extracted_title||source.extracted_description||source.extracted_text)=''
   OR source.checksum IS DISTINCT FROM encode(sha256(convert_to(E'URL\n'||source.source_reference,'UTF8')),'hex')
   OR NOT EXISTS(SELECT 1 FROM jobs WHERE id=url.fetch_job_id AND status='SUCCEEDED' AND attempts=url.fetch_attempt)
   OR NEW.original_scan_id IS DISTINCT FROM latest.id OR NEW.latest_scan_id IS DISTINCT FROM latest.id
   OR latest.source_version<>source.lock_version OR latest.checksum IS DISTINCT FROM source.fetched_checksum
   OR latest.verdict<>'CLEAN' OR latest.scanned_at>clock_timestamp() OR latest.scanned_at<source.fetched_at
   OR latest.scanner_engine='legacy-unknown' OR latest.signature_version='legacy-unknown'
   OR NEW.scan_expires_at<=clock_timestamp() OR NEW.reviewed_at>=NEW.scan_expires_at
   OR NEW.scan_expires_at<latest.scanned_at+interval '1 second' OR NEW.scan_expires_at>latest.scanned_at+interval '7 days'
   OR obj.state<>'REFERENCED' OR obj.quota_released OR obj.deleted_at IS NOT NULL OR obj.retained_bytes NOT BETWEEN 1 AND 2097152
   OR obj.object_key<>'quarantine/writes/'||ws.id::text||'/'||obj.id::text||'.html' THEN
   RAISE EXCEPTION 'Invalid URL admission' USING ERRCODE='23514';
  END IF;
 ELSE
  SELECT * INTO art FROM evidence_artifacts WHERE id=NEW.artifact_id;
  SELECT * INTO original FROM evidence_scan_results WHERE id=NEW.original_scan_id;
  SELECT * INTO latest FROM evidence_scan_results WHERE evidence_id=NEW.evidence_id
    ORDER BY scanned_at DESC,created_at DESC,id DESC LIMIT 1;
  SELECT * INTO obj FROM quarantine_objects WHERE evidence_id=source.id AND brand_id=parent.id
    AND workspace_id=ws.id AND object_key=substring(source.source_reference from 12)
    AND 'quarantine:'||object_key=source.source_reference FOR UPDATE;
  IF source.type<>'FILE' OR source.media_type IS DISTINCT FROM 'application/pdf'
   OR art.id IS NULL OR original.id IS NULL OR latest.id IS NULL OR obj.id IS NULL
   OR art.evidence_id<>source.id OR art.source_version<>NEW.source_version OR art.source_checksum<>NEW.source_checksum
   OR art.kind<>'PDF_TEXT' OR art.scan_result_id<>NEW.original_scan_id OR art.storage_reference IS NOT NULL
   OR art.extracted_text IS DISTINCT FROM NEW.reviewed_text OR art.output_checksum IS DISTINCT FROM NEW.content_checksum
   OR art.renderer_version IS DISTINCT FROM NEW.renderer_version OR art.produced_at>clock_timestamp()
   OR art.produced_at<original.scanned_at
   OR NOT EXISTS(SELECT 1 FROM jobs WHERE id=art.job_id AND status='SUCCEEDED')
   OR original.evidence_id<>source.id OR original.source_version<>NEW.source_version
   OR original.checksum<>NEW.source_checksum OR original.verdict<>'CLEAN' OR original.scanned_at>clock_timestamp()
   OR latest.id<>NEW.latest_scan_id OR latest.source_version<>NEW.source_version
   OR latest.checksum<>NEW.source_checksum OR latest.verdict<>'CLEAN' OR latest.scanned_at>clock_timestamp()
   OR NEW.scan_expires_at<=clock_timestamp() OR NEW.reviewed_at>=NEW.scan_expires_at
   OR NEW.scan_expires_at<latest.scanned_at+interval '1 second'
   OR NEW.scan_expires_at>latest.scanned_at+interval '7 days'
   OR obj.state<>'REFERENCED' OR obj.quota_released OR obj.deleted_at IS NOT NULL
   OR obj.retained_bytes NOT BETWEEN 1 AND 10485760
   OR obj.object_key IS DISTINCT FROM 'quarantine/writes/'||ws.id::text||'/'||obj.id::text||'.pdf' THEN
   RAISE EXCEPTION 'Invalid PDF admission' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
ALTER TABLE brand_analysis_citations DROP CONSTRAINT citation_field_path;
ALTER TABLE brand_analysis_citations ADD CONSTRAINT citation_field_path CHECK(field_path IS NULL OR field_path IN ('title','description','text','accountName','biography') OR field_path ~ '^posts\[([0-9]|1[0-9])\]\.(text|title)$');
CREATE OR REPLACE FUNCTION validate_analysis_admission(attempt UUID, admission UUID, publication BOOLEAN)
 RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE a brand_analysis_attempts%ROWTYPE; r evidence_admissions%ROWTYPE;
 b brands%ROWTYPE; w workspaces%ROWTYPE; e brand_evidence%ROWTYPE;
BEGIN
 SELECT * INTO a FROM brand_analysis_attempts WHERE id=attempt;
 PERFORM 1 FROM cadence_users WHERE id=a.requested_by AND active AND role='OWNER' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Analysis owner invalid' USING ERRCODE='23514'; END IF;
 SELECT * INTO b FROM brands WHERE id=a.brand_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=a.brand_id FOR UPDATE;
 SELECT * INTO r FROM evidence_admissions WHERE id=admission;
 SELECT * INTO e FROM brand_evidence WHERE id=r.evidence_id FOR UPDATE;
 IF a.status<>'RUNNING' OR clock_timestamp()>=a.deadline OR r.id IS NULL OR e.id IS NULL
  OR e.brand_id<>b.id OR b.status<>'ACTIVE' OR w.status<>'ACTIVE'
  OR b.lock_version<>(a.brand_version+CASE WHEN publication THEN 1 ELSE 0 END)
  OR w.lock_version<>a.workspace_version OR r.brand_version<>a.brand_version
  OR r.workspace_version<>a.workspace_version OR r.source_version<>e.lock_version
  OR (CASE WHEN r.policy_version='url-text-review-v1' THEN e.status<>'FETCHED' ELSE e.status<>'PENDING' END)
  OR r.source_checksum IS DISTINCT FROM e.checksum
  OR r.content_checksum<>encode(sha256(convert_to(r.reviewed_text,'UTF8')),'hex')
  OR NOT EXISTS(SELECT 1 FROM workspace_memberships WHERE user_id=a.requested_by AND workspace_id=w.id)
  OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals x JOIN evidence_admissions y ON y.id=x.admission_id
    WHERE y.evidence_id=e.id AND y.source_version=r.source_version)
 THEN RAISE EXCEPTION 'Analysis admission invalid' USING ERRCODE='23514'; END IF;
 IF r.policy_version='text-review-v1' THEN
  IF EXISTS(SELECT 1 FROM evidence_social_origins WHERE evidence_id=e.id) OR e.type<>'TEXT' OR r.reviewed_text IS DISTINCT FROM e.source_reference
   OR r.source_checksum<>encode(sha256(convert_to(E'TEXT\n'||r.reviewed_text,'UTF8')),'hex') THEN
   RAISE EXCEPTION 'Text analysis admission invalid' USING ERRCODE='23514';
  END IF;
 ELSIF r.policy_version='social-text-review-v1' THEN
  PERFORM validate_social_admission(r);
 ELSIF r.policy_version='pdf-text-review-v1' THEN
  PERFORM validate_pdf_analysis_source(r.id);
 ELSIF r.policy_version='url-text-review-v1' THEN
  PERFORM validate_url_analysis_source(r.id);
 ELSE
  RAISE EXCEPTION 'Analysis admission policy invalid' USING ERRCODE='23514';
 END IF;

END $$;
-- Extend metadata guards only; external bytes remain application-verified. No history rewrite.
CREATE OR REPLACE FUNCTION validate_identity_evidence_sources(analysis UUID,parent_id UUID)
 RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE a brand_analysis_attempts%ROWTYPE; b brands%ROWTYPE; w workspaces%ROWTYPE;
 r RECORD; e brand_evidence%ROWTYPE; n INTEGER; bytes BIGINT;
BEGIN
 SELECT * INTO a FROM brand_analysis_attempts WHERE id=analysis;
 SELECT * INTO b FROM brands WHERE id=parent_id;
 SELECT * INTO w FROM workspaces WHERE id=b.workspace_id FOR UPDATE;
 SELECT * INTO b FROM brands WHERE id=parent_id FOR UPDATE;
 IF a.id IS NULL OR a.requested_by IS NULL OR a.status<>'SUCCEEDED' OR a.brand_id<>b.id
  OR b.status<>'ACTIVE' OR w.status<>'ACTIVE' OR b.lock_version<>a.brand_version+1
  OR w.lock_version<>a.workspace_version
 THEN RAISE EXCEPTION 'Evidence identity source changed' USING ERRCODE='23514'; END IF;
 SELECT count(*),coalesce(sum(octet_length(ad.reviewed_text)),0) INTO n,bytes
  FROM brand_analysis_sources s JOIN evidence_admissions ad ON ad.id=s.admission_id WHERE s.attempt_id=analysis;
 IF n NOT BETWEEN 1 AND 5 OR bytes>20000
  OR (SELECT count(*) FROM brand_analysis_citations WHERE attempt_id=analysis) NOT BETWEEN 1 AND 10
 THEN RAISE EXCEPTION 'Invalid identity evidence graph' USING ERRCODE='23514'; END IF;
 FOR r IN SELECT x.* FROM brand_analysis_sources s JOIN evidence_admissions x ON x.id=s.admission_id
  WHERE s.attempt_id=analysis ORDER BY x.evidence_id LOOP
  SELECT * INTO e FROM brand_evidence WHERE id=r.evidence_id FOR UPDATE;
  IF e.id IS NULL OR e.brand_id<>b.id OR (CASE WHEN r.policy_version='url-text-review-v1' THEN e.status<>'FETCHED' ELSE e.status<>'PENDING' END)
   OR e.lock_version<>r.source_version OR r.brand_version<>a.brand_version
   OR r.workspace_version<>a.workspace_version
   OR e.checksum IS DISTINCT FROM r.source_checksum
   OR r.content_checksum<>encode(sha256(convert_to(r.reviewed_text,'UTF8')),'hex')
   OR EXISTS(SELECT 1 FROM evidence_admission_withdrawals x JOIN evidence_admissions y ON y.id=x.admission_id
    WHERE y.evidence_id=e.id AND y.source_version=r.source_version)
  THEN RAISE EXCEPTION 'Evidence identity source changed' USING ERRCODE='23514'; END IF;
  IF r.policy_version='text-review-v1' THEN
   IF EXISTS(SELECT 1 FROM evidence_social_origins WHERE evidence_id=e.id) OR e.type<>'TEXT' OR e.source_reference IS DISTINCT FROM r.reviewed_text
    OR r.source_checksum<>encode(sha256(convert_to(E'TEXT\n'||r.reviewed_text,'UTF8')),'hex')
   THEN RAISE EXCEPTION 'Invalid text identity source' USING ERRCODE='23514'; END IF;
  ELSIF r.policy_version='social-text-review-v1' THEN
   PERFORM validate_social_admission(ad) FROM evidence_admissions ad WHERE ad.id=r.id;
  ELSIF r.policy_version='pdf-text-review-v1' THEN
   PERFORM validate_pdf_analysis_source(r.id);
  ELSIF r.policy_version='url-text-review-v1' THEN
   PERFORM validate_url_analysis_source(r.id);
  ELSE RAISE EXCEPTION 'Invalid identity source policy' USING ERRCODE='23514';
  END IF;
 END LOOP;
END $$;

CREATE OR REPLACE FUNCTION guard_analysis_evidence_child() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r evidence_admissions%ROWTYPE; value TEXT;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Analysis evidence history immutable' USING ERRCODE='23514'; END IF;
 PERFORM validate_analysis_admission(NEW.attempt_id,NEW.admission_id,FALSE);
 IF TG_TABLE_NAME='brand_analysis_citations' THEN
  SELECT * INTO r FROM evidence_admissions WHERE id=NEW.admission_id;
  IF r.policy_version='url-text-review-v1' THEN
   IF NEW.field_path IS NULL OR NEW.field_path NOT IN ('title','description','text') THEN RAISE EXCEPTION 'Citation path required' USING ERRCODE='23514'; END IF;
   value=r.reviewed_text::jsonb->>NEW.field_path;
  ELSIF r.policy_version='social-text-review-v1' THEN
   IF NEW.field_path IS NULL THEN RAISE EXCEPTION 'Social citation path required' USING ERRCODE='23514'; END IF;
   IF NEW.field_path IN ('accountName','biography') THEN value=r.reviewed_text::jsonb->>NEW.field_path;
   ELSIF NEW.field_path ~ '^posts\[([0-9]|1[0-9])\]\.(text|title)$' THEN
    value=r.reviewed_text::jsonb->'posts'->(substring(NEW.field_path from '\[([0-9]+)\]')::integer)->>split_part(NEW.field_path,'.',2);
   ELSE RAISE EXCEPTION 'Invalid social citation path' USING ERRCODE='23514'; END IF;
  ELSE
   IF NEW.field_path IS NOT NULL THEN RAISE EXCEPTION 'Legacy citation path forbidden' USING ERRCODE='23514'; END IF;
   value=r.reviewed_text;
  END IF;
  IF value IS NULL OR strpos(value,NEW.quote)=0 THEN RAISE EXCEPTION 'Analysis quote invalid' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION seal_analysis_evidence_input() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a brand_analysis_attempts%ROWTYPE; n INTEGER; bytes BIGINT; digest TEXT; parent UUID;
BEGIN
 IF TG_TABLE_NAME='brand_analysis_attempts' THEN parent=NEW.id; ELSE parent=NEW.attempt_id; END IF;
 SELECT * INTO a FROM brand_analysis_attempts WHERE id=parent;
 SELECT count(*),coalesce(sum(octet_length(r.reviewed_text)),0),
  encode(sha256(convert_to((CASE WHEN bool_or(r.policy_version='social-text-review-v1') THEN E'social-evidence-v3\n' WHEN bool_or(r.policy_version='url-text-review-v1') THEN E'mixed-evidence-v2\n' ELSE E'text-evidence-v1\n' END)||a.input_brand_name||E'\n'||a.input_industry||
   string_agg(E'\n'||r.id::TEXT||E'\n'||r.evidence_id::TEXT||E'\n'||r.source_version::TEXT||E'\n'||r.content_checksum,'' ORDER BY s.position),'UTF8')),'hex')
 INTO n,bytes,digest FROM brand_analysis_sources s JOIN evidence_admissions r ON r.id=s.admission_id WHERE s.attempt_id=parent;
 IF a.requested_by IS NULL THEN
  IF n<>0 THEN RAISE EXCEPTION 'Legacy analysis cannot acquire sources' USING ERRCODE='23514'; END IF;
 ELSE
  IF n NOT BETWEEN 1 AND 5 OR bytes>20000 OR digest IS DISTINCT FROM a.source_checksum
   OR (SELECT count(DISTINCT r.evidence_id) FROM brand_analysis_sources s JOIN evidence_admissions r ON r.id=s.admission_id WHERE s.attempt_id=parent)<>n
  THEN RAISE EXCEPTION 'Invalid bounded analysis input' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NULL;
END $$;

CREATE OR REPLACE FUNCTION validate_social_evidence_origin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP <> 'INSERT' THEN
    RAISE EXCEPTION 'Social evidence origin is immutable' USING ERRCODE = '23514';
  END IF;
  -- Serialize late trusted-origin attachment with review/generation/copy/approval.
  PERFORM 1 FROM workspaces WHERE id=(SELECT b.workspace_id FROM brands b JOIN brand_evidence e ON e.brand_id=b.id WHERE e.id=NEW.evidence_id) FOR UPDATE;
  PERFORM 1 FROM brands WHERE id=(SELECT brand_id FROM brand_evidence WHERE id=NEW.evidence_id) FOR UPDATE;
  PERFORM 1 FROM brand_evidence WHERE id=NEW.evidence_id FOR UPDATE;
  PERFORM 1 FROM brand_evidence e JOIN brands b ON b.id=e.brand_id
    JOIN evidence_provider_calls c ON c.id=NEW.provider_call_id
    WHERE e.id=NEW.evidence_id AND e.type='TEXT' AND e.checksum=NEW.checksum
      AND c.brand_id=e.brand_id AND c.workspace_id=b.workspace_id AND c.provider=NEW.provider
      AND NEW.captured_at >= c.requested_at AND NEW.captured_at <= clock_timestamp();
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Social evidence origin has invalid provider receipt or ownership' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;
