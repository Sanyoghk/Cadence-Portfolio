-- Manual draft revisions only: no legacy approval/provenance is inferred.
CREATE TABLE brand_identities (
 id UUID PRIMARY KEY, brand_id UUID NOT NULL REFERENCES brands(id),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 lock_version BIGINT NOT NULL DEFAULT 0 CHECK(lock_version>=0),
 version INTEGER NOT NULL CHECK(version BETWEEN 1 AND 1000),
 status VARCHAR(16) NOT NULL CHECK(status='DRAFT'),
 primary_color VARCHAR(7) NOT NULL CHECK(primary_color ~ '^#[0-9A-F]{6}$'),
 secondary_color VARCHAR(7) NOT NULL CHECK(secondary_color ~ '^#[0-9A-F]{6}$'),
 accent_color VARCHAR(7) NOT NULL CHECK(accent_color ~ '^#[0-9A-F]{6}$'),
 neutral_color VARCHAR(7) NOT NULL CHECK(neutral_color ~ '^#[0-9A-F]{6}$'),
 background_color VARCHAR(7) NOT NULL CHECK(background_color ~ '^#[0-9A-F]{6}$'),
 sample_caption VARCHAR(1000) NOT NULL CHECK(btrim(sample_caption,U&'\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')<>'' AND sample_caption=btrim(sample_caption,U&'\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')
   AND sample_caption !~ U&'[\0001-\001F\007F-\009F]'),
 UNIQUE(brand_id,version)
);
CREATE TABLE identity_fonts (
 identity_id UUID NOT NULL REFERENCES brand_identities(id),item_order INTEGER NOT NULL CHECK(item_order BETWEEN 0 AND 4),
 item_value VARCHAR(100) NOT NULL CHECK(btrim(item_value,U&'\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')<>'' AND item_value=btrim(item_value,U&'\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000') AND item_value !~ U&'[\0001-\001F\007F-\009F]'),
 PRIMARY KEY(identity_id,item_order)
);
CREATE TABLE identity_voice_up (
 identity_id UUID NOT NULL REFERENCES brand_identities(id),item_order INTEGER NOT NULL CHECK(item_order BETWEEN 0 AND 9),
 item_value VARCHAR(100) NOT NULL CHECK(btrim(item_value,U&'\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')<>'' AND item_value=btrim(item_value,U&'\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000') AND item_value !~ U&'[\0001-\001F\007F-\009F]'),
 PRIMARY KEY(identity_id,item_order)
);
CREATE TABLE identity_voice_down (
 identity_id UUID NOT NULL REFERENCES brand_identities(id),item_order INTEGER NOT NULL CHECK(item_order BETWEEN 0 AND 9),
 item_value VARCHAR(100) NOT NULL CHECK(btrim(item_value,U&'\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')<>'' AND item_value=btrim(item_value,U&'\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000') AND item_value !~ U&'[\0001-\001F\007F-\009F]'),
 PRIMARY KEY(identity_id,item_order)
);
CREATE FUNCTION guard_identity_draft() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE ws_id UUID; latest INTEGER;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Identity history cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND ROW(NEW.id,NEW.brand_id,NEW.version,NEW.created_at,NEW.status)
   IS DISTINCT FROM ROW(OLD.id,OLD.brand_id,OLD.version,OLD.created_at,OLD.status) THEN
  RAISE EXCEPTION 'Identity revision metadata is immutable' USING ERRCODE='23514';
 END IF;
 SELECT b.workspace_id INTO ws_id FROM brands b WHERE b.id=NEW.brand_id;
 IF TG_OP='INSERT' THEN
  PERFORM 1 FROM workspaces WHERE id=ws_id AND status='ACTIVE' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Archived identity parent' USING ERRCODE='23514'; END IF;
  PERFORM 1 FROM brands WHERE id=NEW.brand_id AND status='ACTIVE' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Archived identity parent' USING ERRCODE='23514'; END IF;
  SELECT coalesce(max(version),0) INTO latest FROM brand_identities WHERE brand_id=NEW.brand_id;
  IF NEW.version<>latest+1 THEN RAISE EXCEPTION 'Invalid identity revision allocation' USING ERRCODE='23514'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM brands b JOIN workspaces w ON w.id=b.workspace_id
    WHERE b.id=NEW.brand_id AND b.status='ACTIVE' AND w.status='ACTIVE') THEN
   RAISE EXCEPTION 'Archived identity parent' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER identity_draft_guard BEFORE INSERT OR UPDATE OR DELETE ON brand_identities
 FOR EACH ROW EXECUTE FUNCTION guard_identity_draft();

CREATE FUNCTION guard_identity_collection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_id UUID;
BEGIN
 IF TG_OP='UPDATE' AND NEW.identity_id IS DISTINCT FROM OLD.identity_id THEN
  RAISE EXCEPTION 'Identity collection ownership is immutable' USING ERRCODE='23514';
 END IF;
 parent_id=CASE WHEN TG_OP='DELETE' THEN OLD.identity_id ELSE NEW.identity_id END;
 PERFORM 1 FROM brand_identities i JOIN brands b ON b.id=i.brand_id JOIN workspaces w ON w.id=b.workspace_id
  WHERE i.id=parent_id AND i.status='DRAFT' AND b.status='ACTIVE' AND w.status='ACTIVE' FOR UPDATE OF i;
 IF NOT FOUND THEN RAISE EXCEPTION 'Identity collection cannot be edited' USING ERRCODE='23514'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER identity_fonts_guard BEFORE INSERT OR UPDATE OR DELETE ON identity_fonts FOR EACH ROW EXECUTE FUNCTION guard_identity_collection();
CREATE TRIGGER identity_voice_up_guard BEFORE INSERT OR UPDATE OR DELETE ON identity_voice_up FOR EACH ROW EXECUTE FUNCTION guard_identity_collection();
CREATE TRIGGER identity_voice_down_guard BEFORE INSERT OR UPDATE OR DELETE ON identity_voice_down FOR EACH ROW EXECUTE FUNCTION guard_identity_collection();

CREATE FUNCTION validate_identity_content() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_id UUID; fonts_valid BOOLEAN; up_valid BOOLEAN; down_valid BOOLEAN;
BEGIN
 IF TG_TABLE_NAME='brand_identities' THEN parent_id=NEW.id;
 ELSE parent_id=CASE WHEN TG_OP='DELETE' THEN OLD.identity_id ELSE NEW.identity_id END; END IF;
 SELECT count(*) BETWEEN 1 AND 5 AND count(*)=count(distinct lower(item_value)) AND min(item_order)=0 AND max(item_order)=count(*)-1
  INTO fonts_valid FROM identity_fonts WHERE identity_id=parent_id;
 SELECT count(*) BETWEEN 1 AND 10 AND count(*)=count(distinct lower(item_value)) AND min(item_order)=0 AND max(item_order)=count(*)-1
  INTO up_valid FROM identity_voice_up WHERE identity_id=parent_id;
 SELECT count(*) BETWEEN 1 AND 10 AND count(*)=count(distinct lower(item_value)) AND min(item_order)=0 AND max(item_order)=count(*)-1
  INTO down_valid FROM identity_voice_down WHERE identity_id=parent_id;
 IF fonts_valid IS DISTINCT FROM TRUE OR up_valid IS DISTINCT FROM TRUE OR down_valid IS DISTINCT FROM TRUE THEN
  RAISE EXCEPTION 'Invalid identity content' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER identity_content AFTER INSERT OR UPDATE ON brand_identities DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_identity_content();
CREATE CONSTRAINT TRIGGER identity_font_content AFTER INSERT OR UPDATE OR DELETE ON identity_fonts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_identity_content();
CREATE CONSTRAINT TRIGGER identity_up_content AFTER INSERT OR UPDATE OR DELETE ON identity_voice_up DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_identity_content();
CREATE CONSTRAINT TRIGGER identity_down_content AFTER INSERT OR UPDATE OR DELETE ON identity_voice_down DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_identity_content();
