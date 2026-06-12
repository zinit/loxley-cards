CREATE TABLE users (
    id                    UUID         PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    highest_unlocked_stage INT         NOT NULL DEFAULT 1,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL
);
