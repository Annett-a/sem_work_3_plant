CREATE TABLE oauth_accounts
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    provider          VARCHAR(40)  NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    email_at_provider VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_oauth_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT uq_oauth_provider_subject
        UNIQUE (provider, provider_user_id),

    CONSTRAINT uq_oauth_user_provider
        UNIQUE (user_id, provider)
);

CREATE INDEX idx_oauth_accounts_user_id ON oauth_accounts (user_id);