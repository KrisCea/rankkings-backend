CREATE TABLE rankable_items (
    id VARCHAR(255) PRIMARY KEY,
    category VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    creator JSONB NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    release_year INTEGER,
    cover_url VARCHAR(2048) NOT NULL,
    identifiers JSONB NOT NULL,
    details JSONB,
    verification_status VARCHAR(20) NOT NULL,
    post_id VARCHAR(255) NOT NULL,
    average_rating DOUBLE PRECISION NOT NULL DEFAULT 0,
    ratings_count INTEGER NOT NULL DEFAULT 0,
    parent_id VARCHAR(255),
    position INTEGER,
    children_count INTEGER NOT NULL DEFAULT 0,
    children_average_rating DOUBLE PRECISION,
    saves_count INTEGER NOT NULL DEFAULT 0,
    external_links JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_items_category ON rankable_items(category);
CREATE INDEX idx_items_parent ON rankable_items(parent_id, position);

CREATE TABLE saved_items (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    item_id VARCHAR(255) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_saved_user_item UNIQUE (user_id, item_id)
);
