CREATE TABLE refresh_tokens
(
    id          UUID                     NOT NULL,
    jti         UUID                     NOT NULL,
    user_id     UUID                     NOT NULL,
    token_hash  VARCHAR(255)             NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked     BOOLEAN                  NOT NULL,
    device_info VARCHAR(255),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id)
);

ALTER TABLE refresh_tokens
    ADD CONSTRAINT uc_refresh_tokens_jti UNIQUE (jti);

ALTER TABLE refresh_tokens
    ADD CONSTRAINT uc_refresh_tokens_token_hash UNIQUE (token_hash);

ALTER TABLE refresh_tokens
    ADD CONSTRAINT FK_REFRESH_TOKENS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
