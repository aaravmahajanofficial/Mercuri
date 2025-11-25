ALTER TABLE users
    DROP CONSTRAINT uc_users_username;

ALTER TABLE users
    DROP COLUMN username;
