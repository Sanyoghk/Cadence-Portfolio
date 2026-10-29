ALTER TABLE clients ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE brands ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE strategies ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE social_posts ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE post_metrics ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE recommendations ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE clients
    ADD CONSTRAINT clients_name_not_blank CHECK (btrim(name) <> ''),
    ADD CONSTRAINT clients_industry_not_blank CHECK (btrim(industry) <> '');

ALTER TABLE brands
    ADD CONSTRAINT brands_name_not_blank CHECK (btrim(name) <> '');

ALTER TABLE strategies
    ADD CONSTRAINT strategies_cadence_required CHECK (
        posts_per_week IS NOT NULL AND posts_per_week > 0 AND publishing_timezone IS NOT NULL
        AND btrim(publishing_timezone) <> ''
    );

ALTER TABLE strategy_content_pillars
    ADD CONSTRAINT strategy_content_pillars_name_not_blank CHECK (
        pillar_name IS NOT NULL AND btrim(pillar_name) <> ''
    ),
    ADD CONSTRAINT strategy_content_pillars_description_not_blank CHECK (
        pillar_description IS NOT NULL AND btrim(pillar_description) <> ''
    );

ALTER TABLE social_posts
    ADD CONSTRAINT social_posts_title_not_blank CHECK (btrim(title) <> ''),
    ADD CONSTRAINT social_posts_caption_not_blank CHECK (btrim(caption) <> '');

ALTER TABLE recommendations
    ADD CONSTRAINT recommendations_text_not_blank CHECK (btrim(text) <> ''),
    ADD CONSTRAINT recommendations_expected_lift_not_blank CHECK (btrim(expected_lift) <> '');
