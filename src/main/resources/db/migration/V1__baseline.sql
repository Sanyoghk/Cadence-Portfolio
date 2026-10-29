CREATE TABLE clients (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    name VARCHAR(255) NOT NULL,
    industry VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT clients_status_check CHECK (status IN ('ACTIVE', 'PROSPECT', 'ARCHIVED'))
);

CREATE TABLE brands (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    name VARCHAR(255) NOT NULL,
    website_url VARCHAR(2048),
    instagram_handle VARCHAR(255),
    tiktok_handle VARCHAR(255),
    primary_color VARCHAR(255),
    secondary_color VARCHAR(255),
    accent_color VARCHAR(255),
    neutral_color VARCHAR(255),
    background_color VARCHAR(255),
    sample_caption VARCHAR(1000),
    client_id UUID NOT NULL,
    CONSTRAINT brands_client_fk FOREIGN KEY (client_id) REFERENCES clients (id)
);

CREATE TABLE brand_signature_fonts (
    brand_id UUID NOT NULL,
    font_order INTEGER NOT NULL,
    font VARCHAR(255),
    PRIMARY KEY (brand_id, font_order),
    CONSTRAINT brand_signature_fonts_brand_fk FOREIGN KEY (brand_id) REFERENCES brands (id) ON DELETE CASCADE
);

CREATE TABLE brand_signature_voice_up (
    brand_id UUID NOT NULL,
    voice_up_order INTEGER NOT NULL,
    voice_up VARCHAR(255),
    PRIMARY KEY (brand_id, voice_up_order),
    CONSTRAINT brand_signature_voice_up_brand_fk FOREIGN KEY (brand_id) REFERENCES brands (id) ON DELETE CASCADE
);

CREATE TABLE brand_signature_voice_down (
    brand_id UUID NOT NULL,
    voice_down_order INTEGER NOT NULL,
    voice_down VARCHAR(255),
    PRIMARY KEY (brand_id, voice_down_order),
    CONSTRAINT brand_signature_voice_down_brand_fk FOREIGN KEY (brand_id) REFERENCES brands (id) ON DELETE CASCADE
);

CREATE TABLE strategies (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    posts_per_week INTEGER,
    publishing_timezone VARCHAR(255),
    brand_id UUID NOT NULL,
    CONSTRAINT strategies_version_check CHECK (version > 0),
    CONSTRAINT strategies_status_check CHECK (status IN ('DRAFT', 'APPROVED', 'LOCKED')),
    CONSTRAINT strategies_brand_version_unique UNIQUE (brand_id, version),
    CONSTRAINT strategies_brand_fk FOREIGN KEY (brand_id) REFERENCES brands (id)
);

CREATE TABLE strategy_content_pillars (
    strategy_id UUID NOT NULL,
    pillar_order INTEGER NOT NULL,
    pillar_name VARCHAR(255),
    pillar_weight INTEGER NOT NULL,
    pillar_description VARCHAR(1000),
    PRIMARY KEY (strategy_id, pillar_order),
    CONSTRAINT strategy_content_pillars_weight_check CHECK (pillar_weight BETWEEN 0 AND 100),
    CONSTRAINT strategy_content_pillars_strategy_fk FOREIGN KEY (strategy_id) REFERENCES strategies (id) ON DELETE CASCADE
);

CREATE TABLE strategy_best_publishing_times (
    strategy_id UUID NOT NULL,
    publishing_time_order INTEGER NOT NULL,
    publishing_time TIME(6) WITHOUT TIME ZONE,
    PRIMARY KEY (strategy_id, publishing_time_order),
    CONSTRAINT strategy_best_times_strategy_fk FOREIGN KEY (strategy_id) REFERENCES strategies (id) ON DELETE CASCADE
);

CREATE TABLE social_posts (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    title VARCHAR(255) NOT NULL,
    caption VARCHAR(5000) NOT NULL,
    format VARCHAR(255) NOT NULL,
    scheduled_at TIMESTAMP(6) WITH TIME ZONE,
    status VARCHAR(255) NOT NULL,
    strategy_id UUID NOT NULL,
    CONSTRAINT social_posts_format_check CHECK (format IN ('IMAGE', 'CAROUSEL', 'REEL', 'VIDEO', 'STORY', 'TEXT')),
    CONSTRAINT social_posts_status_check CHECK (status IN ('DRAFT', 'APPROVED', 'SCHEDULED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT social_posts_strategy_fk FOREIGN KEY (strategy_id) REFERENCES strategies (id)
);

CREATE TABLE social_post_hashtags (
    post_id UUID NOT NULL,
    hashtag_order INTEGER NOT NULL,
    hashtag VARCHAR(100) NOT NULL,
    PRIMARY KEY (post_id, hashtag_order),
    CONSTRAINT social_post_hashtags_post_fk FOREIGN KEY (post_id) REFERENCES social_posts (id) ON DELETE CASCADE
);

CREATE TABLE post_metrics (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    post_id UUID NOT NULL,
    reach INTEGER NOT NULL,
    engagement INTEGER NOT NULL,
    saves INTEGER NOT NULL,
    signups INTEGER NOT NULL,
    profile_to_site_clicks INTEGER NOT NULL,
    CONSTRAINT post_metrics_non_negative_check CHECK (
        reach >= 0 AND engagement >= 0 AND saves >= 0 AND signups >= 0 AND profile_to_site_clicks >= 0
    ),
    CONSTRAINT post_metrics_post_fk FOREIGN KEY (post_id) REFERENCES social_posts (id)
);

CREATE TABLE recommendations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    strategy_id UUID NOT NULL,
    text VARCHAR(2000) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    expected_lift VARCHAR(500) NOT NULL,
    CONSTRAINT recommendations_confidence_check CHECK (confidence BETWEEN 0.0 AND 1.0),
    CONSTRAINT recommendations_strategy_fk FOREIGN KEY (strategy_id) REFERENCES strategies (id)
);

CREATE INDEX brands_client_idx ON brands (client_id);
CREATE INDEX strategies_brand_idx ON strategies (brand_id);
CREATE INDEX social_posts_strategy_idx ON social_posts (strategy_id);
CREATE INDEX social_posts_status_scheduled_idx ON social_posts (status, scheduled_at);
CREATE INDEX post_metrics_post_idx ON post_metrics (post_id);
CREATE INDEX recommendations_strategy_idx ON recommendations (strategy_id);
