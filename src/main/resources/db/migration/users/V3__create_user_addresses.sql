CREATE TABLE user_addresses
(
    id            UUID                     NOT NULL,
    address_type  VARCHAR(20)              NOT NULL,
    full_name     VARCHAR(200)             NOT NULL,
    phone_number  VARCHAR(20)              NOT NULL,
    address_line1 VARCHAR(255)             NOT NULL,
    address_line2 VARCHAR(255),
    city          VARCHAR(100)             NOT NULL,
    state         VARCHAR(100)             NOT NULL,
    postal_code   VARCHAR(20)              NOT NULL,
    country       VARCHAR(100)             NOT NULL,
    is_default    BOOLEAN                  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id       UUID                     NOT NULL,
    CONSTRAINT pk_user_addresses PRIMARY KEY (id)
);

ALTER TABLE user_addresses
    ADD CONSTRAINT FK_USER_ADDRESSES_USER_ID FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
