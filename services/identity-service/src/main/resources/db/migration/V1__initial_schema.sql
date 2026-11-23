CREATE TABLE accounts (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(30) NOT NULL,
    email VARCHAR(150) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    email_verified BOOLEAN NOT NULL,
    user_role VARCHAR(255) NOT NULL,
    account_type VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(512),
    bio VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_accounts_username UNIQUE (username),
    CONSTRAINT uk_accounts_email UNIQUE (email)
);

CREATE TABLE user_follows (
    id VARCHAR(255) PRIMARY KEY,
    follower_id VARCHAR(255) NOT NULL,
    followed_id VARCHAR(255) NOT NULL,
    CONSTRAINT uk_user_follows UNIQUE (follower_id, followed_id)
);

CREATE TABLE email_verifications (
    token_hash VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE login_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
