CREATE TABLE media_assets (
 id UUID PRIMARY KEY,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version>=0),brand_id UUID NOT NULL REFERENCES brands(id),
 fixture VARCHAR(255) NOT NULL CHECK(fixture IN ('IMAGE_V1','VIDEO_V1')),
 origin VARCHAR(30) NOT NULL CHECK(origin='DEMO_FIXTURE'),format VARCHAR(255) NOT NULL CHECK(format IN ('PNG','MP4')),
 checksum VARCHAR(64) NOT NULL CHECK(checksum ~ '^[0-9a-f]{64}$'),byte_size INTEGER NOT NULL CHECK(byte_size BETWEEN 1 AND 10485760),
 width INTEGER NOT NULL CHECK(width BETWEEN 1 AND 4096),height INTEGER NOT NULL CHECK(height BETWEEN 1 AND 4096),
 duration_millis INTEGER NOT NULL CHECK(duration_millis BETWEEN 0 AND 60000),
 requested_by UUID NOT NULL REFERENCES cadence_users(id),requested_at TIMESTAMPTZ(6) NOT NULL,deadline TIMESTAMPTZ(6) NOT NULL,
 status VARCHAR(255) NOT NULL CHECK(status IN ('STAGING','AVAILABLE','REJECTED','ARCHIVED')),
 moderation VARCHAR(255) NOT NULL CHECK(moderation IN ('PENDING','APPROVED_DEMO','REJECTED')),
 scan_result VARCHAR(255) CHECK(scan_result IN ('CLEAN','INFECTED','UNAVAILABLE','ERROR')),
 scan_engine VARCHAR(100),scan_signature VARCHAR(100),scanned_at TIMESTAMPTZ(6),completed_at TIMESTAMPTZ(6),
 verification_failure VARCHAR(40) CHECK(verification_failure IN ('DEADLINE_EXCEEDED','PHYSICAL_UNAVAILABLE','SCAN_REJECTED')),
 reviewed_by UUID REFERENCES cadence_users(id),reviewed_at TIMESTAMPTZ(6),archived_by UUID REFERENCES cadence_users(id),archived_at TIMESTAMPTZ(6),
 CHECK(deadline=requested_at+interval '120 seconds'),
 CHECK((fixture='IMAGE_V1' AND format='PNG' AND checksum='6318b17380802c6bf6ecc6b0333af1a064ea70b5944ddbc36da4319d671ffc99' AND byte_size=6688 AND width=512 AND height=512 AND duration_millis=0)
 OR(fixture='VIDEO_V1' AND format='MP4' AND checksum='ac056108384f7da890c0c38f7171ea46447e69d4536b9ef4405a35f7296be9f2' AND byte_size=6288 AND width=360 AND height=640 AND duration_millis=2000)),
 CHECK((scan_result IS NULL AND scan_engine IS NULL AND scan_signature IS NULL AND scanned_at IS NULL)
 OR(scan_result IS NOT NULL AND scan_engine IS NOT NULL AND btrim(scan_engine)<>'' AND scan_signature IS NOT NULL AND btrim(scan_signature)<>'' AND scanned_at IS NOT NULL AND completed_at IS NOT NULL AND scanned_at>=requested_at AND scanned_at<=completed_at)),
 CHECK((moderation='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
 OR(moderation<>'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND completed_at IS NOT NULL AND reviewed_at>=completed_at)),
 CHECK((status='ARCHIVED' AND archived_by IS NOT NULL AND archived_at IS NOT NULL AND archived_at>=requested_at AND (completed_at IS NULL OR archived_at>=completed_at) AND (reviewed_at IS NULL OR archived_at>=reviewed_at))
 OR(status<>'ARCHIVED' AND archived_by IS NULL AND archived_at IS NULL)),
 CHECK(status<>'STAGING' OR (scan_result IS NULL AND completed_at IS NULL AND verification_failure IS NULL AND moderation='PENDING')),
 CHECK(status<>'AVAILABLE' OR (scan_result IS NOT NULL AND scan_result='CLEAN' AND completed_at IS NOT NULL AND completed_at<deadline AND verification_failure IS NULL AND moderation IN ('PENDING','APPROVED_DEMO'))),
 CHECK(status<>'REJECTED' OR (completed_at IS NOT NULL AND (verification_failure IS NOT NULL OR moderation='REJECTED')))
);
CREATE INDEX media_assets_brand_idx ON media_assets(brand_id,created_at,id);
CREATE TABLE post_revision_media(
 revision_id UUID NOT NULL REFERENCES post_revisions(id),asset_order INTEGER NOT NULL CHECK(asset_order BETWEEN 0 AND 9),
 asset_id UUID NOT NULL REFERENCES media_assets(id),PRIMARY KEY(revision_id,asset_order),UNIQUE(revision_id,asset_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX post_revision_media_asset_idx ON post_revision_media(asset_id,revision_id);

CREATE FUNCTION guard_media_asset() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE parent brands%ROWTYPE; workspace workspaces%ROWTYPE; actor UUID; time TIMESTAMPTZ;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Retained media cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN actor=NEW.requested_by;time=NEW.requested_at;
 ELSE
  IF OLD.status='ARCHIVED' OR NEW.lock_version<>OLD.lock_version+1 OR
   ROW(NEW.id,NEW.created_at,NEW.brand_id,NEW.fixture,NEW.origin,NEW.format,NEW.checksum,NEW.byte_size,NEW.width,NEW.height,NEW.duration_millis,NEW.requested_by,NEW.requested_at,NEW.deadline)
   IS DISTINCT FROM ROW(OLD.id,OLD.created_at,OLD.brand_id,OLD.fixture,OLD.origin,OLD.format,OLD.checksum,OLD.byte_size,OLD.width,OLD.height,OLD.duration_millis,OLD.requested_by,OLD.requested_at,OLD.deadline) THEN
   RAISE EXCEPTION 'Media provenance immutable' USING ERRCODE='23514'; END IF;
  IF NEW.status='ARCHIVED' THEN actor=NEW.archived_by;time=NEW.archived_at;
   IF ROW(NEW.moderation,NEW.scan_result,NEW.scan_engine,NEW.scan_signature,NEW.scanned_at,NEW.completed_at,NEW.verification_failure,NEW.reviewed_by,NEW.reviewed_at)
    IS DISTINCT FROM ROW(OLD.moderation,OLD.scan_result,OLD.scan_engine,OLD.scan_signature,OLD.scanned_at,OLD.completed_at,OLD.verification_failure,OLD.reviewed_by,OLD.reviewed_at) THEN
    RAISE EXCEPTION 'Archive preserves media provenance' USING ERRCODE='23514'; END IF;
  ELSIF OLD.status='STAGING' AND NEW.status IN ('AVAILABLE','REJECTED') THEN actor=OLD.requested_by;time=NEW.completed_at;
   IF NEW.moderation<>'PENDING' OR NEW.reviewed_by IS NOT NULL OR NEW.reviewed_at IS NOT NULL THEN
    RAISE EXCEPTION 'Scan is not moderation' USING ERRCODE='23514'; END IF;
  ELSIF OLD.status='AVAILABLE' AND OLD.moderation='PENDING' AND NEW.moderation IN ('APPROVED_DEMO','REJECTED') THEN
   actor=NEW.reviewed_by;time=NEW.reviewed_at;
   IF (NEW.moderation='APPROVED_DEMO' AND NEW.status<>'AVAILABLE') OR (NEW.moderation='REJECTED' AND NEW.status<>'REJECTED') OR
    ROW(NEW.scan_result,NEW.scan_engine,NEW.scan_signature,NEW.scanned_at,NEW.completed_at,NEW.verification_failure)
    IS DISTINCT FROM ROW(OLD.scan_result,OLD.scan_engine,OLD.scan_signature,OLD.scanned_at,OLD.completed_at,OLD.verification_failure) THEN
    RAISE EXCEPTION 'Media review preserves scan receipt' USING ERRCODE='23514'; END IF;
  ELSE RAISE EXCEPTION 'Invalid media transition' USING ERRCODE='23514'; END IF;
 END IF;
 PERFORM 1 FROM cadence_users WHERE id=actor FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=NEW.brand_id;
 SELECT * INTO workspace FROM workspaces WHERE id=parent.workspace_id FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=NEW.brand_id FOR UPDATE;
 PERFORM post_require_actor(actor,workspace.id);
 IF workspace.status IS DISTINCT FROM 'ACTIVE' OR parent.status IS DISTINCT FROM 'ACTIVE' OR time IS NULL
  OR time<transaction_timestamp() OR time>clock_timestamp() THEN RAISE EXCEPTION 'Media actor or lifecycle unavailable' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND (NEW.status<>'STAGING' OR NEW.lock_version<>0 OR NEW.moderation<>'PENDING'
   OR NEW.completed_at IS NOT NULL OR NEW.verification_failure IS NOT NULL OR NEW.reviewed_at IS NOT NULL OR NEW.scan_result IS NOT NULL
   OR (SELECT count(*) FROM media_assets WHERE brand_id=NEW.brand_id)>=1000
   OR (SELECT coalesce(sum(byte_size),0) FROM media_assets WHERE brand_id=NEW.brand_id)+NEW.byte_size>104857600) THEN
  RAISE EXCEPTION 'Media intent or retained quota invalid' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER media_asset_guard BEFORE INSERT OR UPDATE OR DELETE ON media_assets FOR EACH ROW EXECUTE FUNCTION guard_media_asset();

CREATE FUNCTION guard_post_media_link() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE revision UUID; r post_revisions%ROWTYPE;
BEGIN
 revision=CASE WHEN TG_OP='DELETE' THEN OLD.revision_id ELSE NEW.revision_id END;
 IF TG_OP='UPDATE' AND NEW.revision_id IS DISTINCT FROM OLD.revision_id THEN RAISE EXCEPTION 'Media link cannot move revision' USING ERRCODE='23514'; END IF;
 SELECT * INTO r FROM post_revisions WHERE id=revision FOR UPDATE;
 IF r.status IS DISTINCT FROM 'DRAFT' OR NOT EXISTS(SELECT 1 FROM post_revisions p WHERE p.id=revision AND p.xmin::text::bigint=mod(txid_current(),4294967296)) THEN
  RAISE EXCEPTION 'Media change requires current draft version and human audit' USING ERRCODE='23514'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;
CREATE TRIGGER post_media_link_guard BEFORE INSERT OR UPDATE OR DELETE ON post_revision_media FOR EACH ROW EXECUTE FUNCTION guard_post_media_link();

CREATE FUNCTION seal_post_media() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE revision UUID; r post_revisions%ROWTYPE; p content_posts%ROWTYPE; n INTEGER;
BEGIN
 IF TG_TABLE_NAME='post_revisions' THEN revision=NEW.id;
 ELSE revision=CASE WHEN TG_OP='DELETE' THEN OLD.revision_id ELSE NEW.revision_id END; END IF;
 SELECT * INTO r FROM post_revisions WHERE id=revision;
 IF r.status NOT IN ('DRAFT','APPROVED') THEN RETURN NULL; END IF;
 SELECT * INTO p FROM content_posts WHERE id=r.post_id;
 SELECT count(*) INTO n FROM post_revision_media WHERE revision_id=revision;
 IF n=0 THEN RETURN NULL; END IF;
 IF n>10 OR (SELECT min(asset_order) FROM post_revision_media WHERE revision_id=revision)<>0
  OR (SELECT max(asset_order) FROM post_revision_media WHERE revision_id=revision)<>n-1
  OR EXISTS(SELECT 1 FROM post_revision_media l JOIN media_assets a ON a.id=l.asset_id WHERE l.revision_id=revision AND
   (a.brand_id<>p.brand_id OR a.status<>'AVAILABLE' OR a.moderation<>'APPROVED_DEMO' OR a.scan_result IS DISTINCT FROM 'CLEAN'
    OR a.reviewed_by IS NULL OR a.reviewed_at IS NULL OR a.verification_failure IS NOT NULL
    OR (r.format IN ('IMAGE','CAROUSEL') AND a.format<>'PNG') OR (r.format IN ('VIDEO','REEL') AND a.format<>'MP4')))
  OR (r.format IN ('IMAGE','VIDEO','REEL','STORY') AND n<>1) OR (r.format='CAROUSEL' AND n<2) THEN
  RAISE EXCEPTION 'Post media unavailable or incompatible' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER post_media_root_seal AFTER INSERT OR UPDATE ON post_revisions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_post_media();
CREATE CONSTRAINT TRIGGER post_media_links_seal AFTER INSERT OR UPDATE OR DELETE ON post_revision_media
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION seal_post_media();
