CREATE TABLE content_posts (
 id UUID PRIMARY KEY, created_at TIMESTAMPTZ(6) NOT NULL, updated_at TIMESTAMPTZ(6) NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version>=0),
 brand_id UUID NOT NULL REFERENCES brands(id), strategy_id UUID NOT NULL REFERENCES strategies(id),
 platform VARCHAR(255), legacy BOOLEAN NOT NULL,
 source_revision INTEGER, source_lock_version BIGINT, source_approved_by UUID REFERENCES cadence_users(id),
 source_approved_at TIMESTAMPTZ(6), source_checksum VARCHAR(64),
 authored_by UUID REFERENCES cadence_users(id), authored_at TIMESTAMPTZ(6),
 CHECK ((legacy AND platform IS NULL AND source_revision IS NULL AND source_lock_version IS NULL
   AND source_approved_by IS NULL AND source_approved_at IS NULL AND source_checksum IS NULL
   AND authored_by IS NULL AND authored_at IS NULL)
 OR (NOT legacy AND platform IS NOT NULL AND platform IN ('INSTAGRAM','TIKTOK') AND source_revision IS NOT NULL AND source_revision>0
   AND source_lock_version IS NOT NULL AND source_lock_version>=0 AND source_approved_by IS NOT NULL
   AND source_approved_at IS NOT NULL AND source_checksum IS NOT NULL AND source_checksum ~ '^[0-9a-f]{64}$'
   AND authored_by IS NOT NULL AND authored_at IS NOT NULL))
);
CREATE INDEX content_posts_brand_idx ON content_posts(brand_id,created_at,id);
CREATE TABLE post_revisions (
 id UUID PRIMARY KEY, created_at TIMESTAMPTZ(6) NOT NULL, updated_at TIMESTAMPTZ(6) NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version>=0),
 post_id UUID NOT NULL REFERENCES content_posts(id), revision_number INTEGER NOT NULL CHECK(revision_number BETWEEN 1 AND 1000),
 revised_from_id UUID REFERENCES post_revisions(id), status VARCHAR(255) NOT NULL CHECK(status IN ('DRAFT','APPROVED','LEGACY')),
 title VARCHAR(255) NOT NULL CHECK(btrim(title)<>''),caption VARCHAR(5000) NOT NULL CHECK(btrim(caption)<>''),
 format VARCHAR(255) NOT NULL CHECK(format IN ('IMAGE','CAROUSEL','REEL','VIDEO','STORY','TEXT')),
 media_prompt VARCHAR(2000),video_script VARCHAR(5000),
 authored_by UUID REFERENCES cadence_users(id),authored_at TIMESTAMPTZ(6),
 edited_by UUID REFERENCES cadence_users(id),edited_at TIMESTAMPTZ(6),
 approved_by UUID REFERENCES cadence_users(id),approved_at TIMESTAMPTZ(6),
 legacy_status VARCHAR(255),legacy_scheduled_at TIMESTAMPTZ(6),
 UNIQUE(post_id,revision_number),UNIQUE(post_id,id),
 FOREIGN KEY(post_id,revised_from_id) REFERENCES post_revisions(post_id,id),
 CHECK((status='LEGACY' AND revision_number=1 AND revised_from_id IS NULL AND authored_by IS NULL
   AND authored_at IS NULL AND edited_by IS NULL AND edited_at IS NULL AND approved_by IS NULL AND approved_at IS NULL
   AND media_prompt IS NULL AND video_script IS NULL AND legacy_status IS NOT NULL)
 OR(status IN ('DRAFT','APPROVED') AND authored_by IS NOT NULL AND authored_at IS NOT NULL
   AND edited_by IS NOT NULL AND edited_at IS NOT NULL AND edited_at>=authored_at
   AND media_prompt IS NOT NULL AND btrim(media_prompt)<>'' AND (video_script IS NULL OR btrim(video_script)<>'')
   AND legacy_status IS NULL AND legacy_scheduled_at IS NULL
   AND ((status='DRAFT' AND approved_by IS NULL AND approved_at IS NULL)
     OR(status='APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND approved_at>=edited_at))))
);
CREATE UNIQUE INDEX post_one_draft_idx ON post_revisions(post_id) WHERE status='DRAFT';
CREATE TABLE post_revision_hashtags (
 revision_id UUID NOT NULL REFERENCES post_revisions(id),hashtag_order INTEGER NOT NULL CHECK(hashtag_order>=0),
 hashtag VARCHAR(100) NOT NULL,PRIMARY KEY(revision_id,hashtag_order)
);

-- Preserve exactly the legacy data that exists; no invented platform or approval provenance.
INSERT INTO content_posts(id,created_at,updated_at,lock_version,brand_id,strategy_id,legacy)
 SELECT p.id,p.created_at,p.updated_at,p.lock_version,s.brand_id,p.strategy_id,true FROM social_posts p JOIN strategies s ON s.id=p.strategy_id;
INSERT INTO post_revisions(id,created_at,updated_at,lock_version,post_id,revision_number,status,title,caption,format,legacy_status,legacy_scheduled_at)
 SELECT id,created_at,updated_at,lock_version,id,1,'LEGACY',title,caption,format,status,scheduled_at FROM social_posts;
INSERT INTO post_revision_hashtags SELECT post_id,hashtag_order,hashtag FROM social_post_hashtags;

CREATE FUNCTION post_require_actor(actor UUID,workspace UUID) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM cadence_users u JOIN workspace_memberships m ON m.user_id=u.id
   WHERE u.id=actor AND u.active AND u.role='OWNER' AND m.workspace_id=workspace) THEN
   RAISE EXCEPTION 'Post actor denied' USING ERRCODE='23514';
 END IF;
END $$;

CREATE FUNCTION guard_content_post() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE source strategies%ROWTYPE; parent brands%ROWTYPE; workspace workspaces%ROWTYPE;
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Post identity immutable' USING ERRCODE='23514'; END IF;
 IF NEW.legacy THEN RAISE EXCEPTION 'Legacy Post import is migration only' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM cadence_users WHERE id=NEW.authored_by FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=NEW.brand_id;
 SELECT * INTO workspace FROM workspaces WHERE id=parent.workspace_id FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=NEW.brand_id FOR UPDATE;
 SELECT * INTO source FROM strategies WHERE id=NEW.strategy_id FOR UPDATE;
 PERFORM post_require_actor(NEW.authored_by,workspace.id);
 IF source.brand_id IS DISTINCT FROM NEW.brand_id OR source.status<>'APPROVED' OR source.content_format_version<>2
   OR source.version IS DISTINCT FROM NEW.source_revision OR source.lock_version IS DISTINCT FROM NEW.source_lock_version
   OR source.approved_by IS DISTINCT FROM NEW.source_approved_by OR source.approved_at IS DISTINCT FROM NEW.source_approved_at
   OR source.approved_by IS NULL OR workspace.status<>'ACTIVE' OR parent.status<>'ACTIVE'
   OR NOT EXISTS(SELECT 1 FROM strategy_playbook_channels WHERE strategy_id=source.id AND channel=NEW.platform)
   OR (SELECT count(*) FROM content_posts WHERE brand_id=NEW.brand_id)>=10000 THEN
   RAISE EXCEPTION 'Post source unavailable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER content_post_guard BEFORE INSERT OR UPDATE OR DELETE ON content_posts FOR EACH ROW EXECUTE FUNCTION guard_content_post();

CREATE FUNCTION guard_post_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p content_posts%ROWTYPE; parent brands%ROWTYPE; workspace workspaces%ROWTYPE; source strategies%ROWTYPE;
 previous post_revisions%ROWTYPE; n INTEGER; actor UUID;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Post history cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (OLD.status<>'DRAFT' OR
   ROW(NEW.id,NEW.post_id,NEW.revision_number,NEW.revised_from_id,NEW.authored_by,NEW.authored_at,NEW.created_at,NEW.legacy_status,NEW.legacy_scheduled_at)
   IS DISTINCT FROM ROW(OLD.id,OLD.post_id,OLD.revision_number,OLD.revised_from_id,OLD.authored_by,OLD.authored_at,OLD.created_at,OLD.legacy_status,OLD.legacy_scheduled_at)) THEN
   RAISE EXCEPTION 'Post history immutable' USING ERRCODE='23514'; END IF;
 actor=CASE WHEN NEW.status='APPROVED' THEN NEW.approved_by ELSE NEW.edited_by END;
 PERFORM 1 FROM cadence_users WHERE id=actor FOR UPDATE;
 SELECT * INTO p FROM content_posts WHERE id=NEW.post_id;
 SELECT * INTO parent FROM brands WHERE id=p.brand_id;
 SELECT * INTO workspace FROM workspaces WHERE id=parent.workspace_id FOR UPDATE;
 SELECT * INTO parent FROM brands WHERE id=p.brand_id FOR UPDATE;
 SELECT * INTO source FROM strategies WHERE id=p.strategy_id FOR UPDATE;
 PERFORM 1 FROM content_posts WHERE id=p.id FOR UPDATE;
 PERFORM post_require_actor(actor,workspace.id);
 IF p.legacy OR NEW.status='LEGACY' OR parent.status<>'ACTIVE' OR workspace.status<>'ACTIVE'
   OR source.status<>'APPROVED' OR source.content_format_version<>2 OR source.lock_version<>p.source_lock_version
   OR source.version<>p.source_revision OR source.approved_by IS DISTINCT FROM p.source_approved_by
   OR source.approved_at IS DISTINCT FROM p.source_approved_at THEN
   RAISE EXCEPTION 'Post lifecycle unavailable' USING ERRCODE='23514'; END IF;
 IF (p.platform='INSTAGRAM' AND (NEW.format NOT IN ('IMAGE','CAROUSEL','REEL','STORY') OR length(NEW.caption)>2200))
   OR (p.platform='TIKTOK' AND (NEW.format NOT IN ('VIDEO','CAROUSEL') OR length(NEW.caption)>4000))
   OR (NEW.video_script IS NOT NULL AND NEW.format NOT IN ('VIDEO','REEL')) THEN
   RAISE EXCEPTION 'Invalid platform content' USING ERRCODE='23514'; END IF;
 SELECT coalesce(max(revision_number),0) INTO n FROM post_revisions WHERE post_id=p.id;
 IF TG_OP='INSERT' THEN
   PERFORM post_require_actor(NEW.authored_by,workspace.id);
   IF NEW.status<>'DRAFT' OR NEW.revision_number<>n+1 OR NEW.authored_by<>NEW.edited_by
      OR NEW.authored_at<>NEW.edited_at THEN RAISE EXCEPTION 'Invalid new Post revision' USING ERRCODE='23514'; END IF;
   IF n=0 THEN
     IF NEW.revised_from_id IS NOT NULL THEN RAISE EXCEPTION 'Invalid root' USING ERRCODE='23514'; END IF;
   ELSE
     SELECT * INTO previous FROM post_revisions WHERE id=NEW.revised_from_id;
     IF previous.id IS NULL OR previous.post_id<>p.id OR previous.revision_number<>n OR previous.status<>'APPROVED' THEN
       RAISE EXCEPTION 'Invalid Post lineage' USING ERRCODE='23514'; END IF;
   END IF;
 ELSE
   IF NEW.revision_number<>n OR NEW.lock_version<>OLD.lock_version+1 THEN
     RAISE EXCEPTION 'Stale Post revision' USING ERRCODE='23514'; END IF;
   IF NEW.status='DRAFT' AND (NEW.edited_at IS NOT DISTINCT FROM OLD.edited_at OR NEW.edited_at>clock_timestamp()) THEN
     RAISE EXCEPTION 'Post edit requires fresh attribution' USING ERRCODE='23514'; END IF;
   IF NEW.status='APPROVED' AND ROW(NEW.title,NEW.caption,NEW.format,NEW.media_prompt,NEW.video_script,NEW.edited_by,NEW.edited_at)
     IS DISTINCT FROM ROW(OLD.title,OLD.caption,OLD.format,OLD.media_prompt,OLD.video_script,OLD.edited_by,OLD.edited_at) THEN
     RAISE EXCEPTION 'Approval cannot change content' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER post_revision_guard BEFORE INSERT OR UPDATE OR DELETE ON post_revisions FOR EACH ROW EXECUTE FUNCTION guard_post_revision();

CREATE FUNCTION guard_post_hashtags() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE r post_revisions%ROWTYPE; rid UUID;
BEGIN
 rid=CASE WHEN TG_OP='DELETE' THEN OLD.revision_id ELSE NEW.revision_id END;
 SELECT * INTO r FROM post_revisions WHERE id=rid FOR UPDATE;
 IF r.status<>'DRAFT' OR (TG_OP='UPDATE' AND NEW.revision_id<>OLD.revision_id) THEN
   RAISE EXCEPTION 'Post tags history immutable' USING ERRCODE='23514'; END IF;
 -- The root must have been inserted/updated by this transaction, not merely row-locked.
 -- xmin is the physical row's creating transaction; txid_current includes an epoch.
 IF NOT EXISTS(SELECT 1 FROM post_revisions WHERE id=rid AND xmin::text::bigint=mod(txid_current(),4294967296)) THEN
   RAISE EXCEPTION 'Post tags require versioned parent edit' USING ERRCODE='23514'; END IF;
 IF TG_OP<>'DELETE' AND (NEW.hashtag_order>=30 OR NEW.hashtag !~ '^#[[:alnum:]_]+$') THEN
   RAISE EXCEPTION 'Invalid Post hashtag' USING ERRCODE='23514'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;
CREATE TRIGGER post_hashtag_guard BEFORE INSERT OR UPDATE OR DELETE ON post_revision_hashtags FOR EACH ROW EXECUTE FUNCTION guard_post_hashtags();
CREATE FUNCTION check_post_tags_complete() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE rid UUID; n INTEGER; d INTEGER; hi INTEGER;
BEGIN
 rid=CASE WHEN TG_OP='DELETE' THEN OLD.revision_id ELSE NEW.revision_id END;
 SELECT count(*),count(DISTINCT hashtag),max(hashtag_order) INTO n,d,hi FROM post_revision_hashtags WHERE revision_id=rid;
 IF n<>d OR (n>0 AND hi<>n-1) THEN RAISE EXCEPTION 'Invalid Post tags' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER post_tags_complete AFTER INSERT OR UPDATE OR DELETE ON post_revision_hashtags
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_post_tags_complete();

CREATE FUNCTION check_post_root_complete() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM post_revisions WHERE post_id=NEW.id AND revision_number=1) THEN
   RAISE EXCEPTION 'Post root revision missing' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER post_root_complete AFTER INSERT ON content_posts
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_post_root_complete();
