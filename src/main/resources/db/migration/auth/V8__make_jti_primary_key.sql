ALTER TABLE refresh_tokens
    DROP COLUMN id;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT pk_refresh_tokens PRIMARY KEY (jti);
