CREATE TABLE user_roles
(
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    role       VARCHAR(50)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (id)
);

ALTER TABLE user_roles
    ADD CONSTRAINT uc_user_id_role UNIQUE (user_id, role);

ALTER TABLE user_roles
    ADD CONSTRAINT FK_USER_ROLES_USER FOREIGN KEY (user_id) REFERENCES users (id);
