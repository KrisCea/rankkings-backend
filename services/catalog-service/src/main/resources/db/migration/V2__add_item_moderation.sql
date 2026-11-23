ALTER TABLE rankable_items
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'approved';

CREATE INDEX idx_items_moderation ON rankable_items(moderation_status);
