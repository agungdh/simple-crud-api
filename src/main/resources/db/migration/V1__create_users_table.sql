-- Enable trigram extension for full-text search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Users table
CREATE TABLE users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid        UUID         NOT NULL DEFAULT gen_random_uuid(),
    username    VARCHAR(100) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,

    -- Generated column for multi-column text search
    search_text TEXT GENERATED ALWAYS AS (
        coalesce(username, '') || ' ' || coalesce(name, '')
    ) STORED,

    -- Audit columns (nullable, no DB default)
    created_at  TIMESTAMPTZ,
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ,
    updated_by  BIGINT,
    deleted_at  TIMESTAMPTZ,
    deleted_by  BIGINT
);

-- Hash index on uuid for O(1) equality lookup
CREATE INDEX idx_users_uuid_hash ON users USING HASH (uuid);

-- Partial unique index on username (only for active/non-deleted records)
CREATE UNIQUE INDEX idx_users_username_unique_active
    ON users (username)
    WHERE deleted_at IS NULL;

-- GIN trigram index on search_text for fast case-insensitive substring search
CREATE INDEX idx_users_search_text_trgm
    ON users USING gin (search_text gin_trgm_ops)
    WHERE deleted_at IS NULL;
