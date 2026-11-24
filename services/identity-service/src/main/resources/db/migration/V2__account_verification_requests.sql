ALTER TABLE accounts
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'unverified',
    ADD COLUMN verification_link VARCHAR(2048),
    ADD COLUMN verification_explanation VARCHAR(1000),
    ADD COLUMN verification_requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN verification_reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN verification_reviewed_by VARCHAR(255),
    ADD COLUMN verification_review_reason VARCHAR(1000);

CREATE INDEX idx_accounts_verification_queue
    ON accounts (verification_status, verification_requested_at DESC);
