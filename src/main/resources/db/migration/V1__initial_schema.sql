-- V1__initial_schema.sql
-- Creates the foundational tables that mirror the original Prisma schema.
-- All subsequent migrations depend on these tables existing first.

-- ── Location ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Location" (
    id          VARCHAR(255) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── User ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "User" (
    id          VARCHAR(255) PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    full_name   VARCHAR(255) NOT NULL,
    contact     VARCHAR(255),
    status      BOOLEAN,
    profile_img VARCHAR(255),
    position    VARCHAR(255),
    signature   TEXT,
    role_code   VARCHAR(32),
    location_id VARCHAR(255) REFERENCES "Location"(id) ON DELETE SET NULL,
    permissions TEXT[]       NOT NULL DEFAULT '{}',
    token_version INTEGER    NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_email       ON "User" (email);
CREATE INDEX IF NOT EXISTS idx_user_location_id ON "User" (location_id);

-- ── AgentGroup ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "AgentGroup" (
    id          VARCHAR(255) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    status      BOOLEAN,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── Admin ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Admin" (
    id         VARCHAR(255) PRIMARY KEY,
    user_id    VARCHAR(255) NOT NULL UNIQUE REFERENCES "User"(id) ON DELETE CASCADE,
    status     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_user_id ON "Admin" (user_id);

-- ── Agent ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Agent" (
    id             VARCHAR(255) PRIMARY KEY,
    user_id        VARCHAR(255) NOT NULL UNIQUE REFERENCES "User"(id) ON DELETE CASCADE,
    agent_group_id VARCHAR(255) REFERENCES "AgentGroup"(id) ON DELETE SET NULL,
    status         BOOLEAN,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_user_id        ON "Agent" (user_id);
CREATE INDEX IF NOT EXISTS idx_agent_agent_group_id ON "Agent" (agent_group_id);

-- ── Session ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Session" (
    id         VARCHAR(255) PRIMARY KEY,
    sid        VARCHAR(255) NOT NULL UNIQUE,
    data       TEXT         NOT NULL,
    "expiresAt" TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_session_sid        ON "Session" (sid);
CREATE INDEX IF NOT EXISTS idx_session_expires_at ON "Session" ("expiresAt");
