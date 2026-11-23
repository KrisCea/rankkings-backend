ALTER TABLE posts
    ADD COLUMN moderated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN moderated_by VARCHAR(255),
    ADD COLUMN moderation_reason VARCHAR(1000);

ALTER TABLE comments
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'approved',
    ADD COLUMN moderated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN moderated_by VARCHAR(255),
    ADD COLUMN moderation_reason VARCHAR(1000);

ALTER TABLE post_media
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'approved',
    ADD COLUMN moderated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN moderated_by VARCHAR(255),
    ADD COLUMN moderation_reason VARCHAR(1000);

UPDATE post_media
SET moderation_status = 'pending'
WHERE status = 'pending_upload';

CREATE INDEX idx_comments_moderation ON comments(moderation_status, created_at);
CREATE INDEX idx_post_media_moderation ON post_media(moderation_status, created_at);
CREATE INDEX idx_posts_moderation ON posts(moderation_status, created_at);
