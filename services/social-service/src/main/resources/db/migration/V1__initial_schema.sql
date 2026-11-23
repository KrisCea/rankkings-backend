CREATE TABLE posts (
    id VARCHAR(255) PRIMARY KEY,
    author_id VARCHAR(255) NOT NULL,
    item_id VARCHAR(255) NOT NULL,
    kind VARCHAR(255) NOT NULL,
    caption VARCHAR(2000) NOT NULL,
    review_score DOUBLE PRECISION,
    moderation_status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE comments (
    id VARCHAR(255) PRIMARY KEY,
    post_id VARCHAR(255) NOT NULL,
    author_id VARCHAR(255) NOT NULL,
    kind VARCHAR(20) NOT NULL DEFAULT 'comment',
    parent_id VARCHAR(255),
    reply_to_username VARCHAR(255),
    text VARCHAR(2000) NOT NULL,
    author_score DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE comment_likes (
    id VARCHAR(255) PRIMARY KEY,
    comment_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    CONSTRAINT uk_comment_likes UNIQUE (comment_id, user_id)
);

CREATE TABLE post_ratings (
    id VARCHAR(255) PRIMARY KEY,
    post_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    CONSTRAINT uk_post_ratings UNIQUE (post_id, user_id)
);

CREATE TABLE post_votes (
    id VARCHAR(255) PRIMARY KEY,
    post_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    CONSTRAINT uk_post_votes UNIQUE (post_id, user_id)
);

CREATE TABLE post_shares (
    id VARCHAR(255) PRIMARY KEY,
    post_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE post_media (
    id VARCHAR(255) PRIMARY KEY,
    post_id VARCHAR(255) NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    is_cover BOOLEAN,
    content_type VARCHAR(100),
    size_bytes BIGINT,
    object_key VARCHAR(512),
    external_url VARCHAR(2048),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_post_media_post_created ON post_media(post_id, created_at);
