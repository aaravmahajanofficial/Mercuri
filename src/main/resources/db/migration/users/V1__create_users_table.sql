CREATE TABLE users
(
    id             UUID                     NOT NULL,
    email          VARCHAR(255)             NOT NULL,
    username       VARCHAR(100)             NOT NULL,
    password_hash  VARCHAR(255)             NOT NULL,
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    phone_number   VARCHAR(20),
    email_verified BOOLEAN                  NOT NULL,
    phone_verified BOOLEAN                  NOT NULL,
    status         VARCHAR(20)              NOT NULL,
    last_login_at  TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE users
    ADD CONSTRAINT uc_users_email UNIQUE (email);

ALTER TABLE users
    ADD CONSTRAINT uc_users_username UNIQUE (username);
