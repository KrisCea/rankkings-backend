ALTER TABLE rankable_items
    ADD COLUMN moderated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN moderated_by VARCHAR(255),
    ADD COLUMN moderation_reason TEXT;
