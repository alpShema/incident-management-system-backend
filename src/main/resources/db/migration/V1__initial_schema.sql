-- V1__initial_schema.sql
-- Base domain schema matching the Prisma-managed database structure.
-- V2+ layer RBAC, activity logs, and app-specific columns on top of this.
-- NOTE: role_code is intentionally omitted (V2 adds it with IF NOT EXISTS).
--       token_version is intentionally omitted (V7 adds it without IF NOT EXISTS guard).
--       category_id on IncidentType is intentionally omitted (V11 adds it with IF NOT EXISTS).

-- ── Session ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Session" (
    id          VARCHAR(255) PRIMARY KEY,
    sid         VARCHAR(255) NOT NULL UNIQUE,
    data        TEXT         NOT NULL,
    "expiresAt" TIMESTAMP    NOT NULL
);

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
    signature   VARCHAR(255),
    location_id VARCHAR(255) REFERENCES "Location"(id) ON DELETE SET NULL,
    permissions TEXT[],
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_location_id ON "User" (location_id);

-- ── Admin ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Admin" (
    id         VARCHAR(255) PRIMARY KEY,
    user_id    VARCHAR(255) NOT NULL UNIQUE REFERENCES "User"(id) ON DELETE CASCADE,
    status     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── AgentGroup ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "AgentGroup" (
    id          VARCHAR(255) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    status      BOOLEAN,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── Agent ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Agent" (
    id             VARCHAR(255) PRIMARY KEY,
    user_id        VARCHAR(255) NOT NULL UNIQUE REFERENCES "User"(id)       ON DELETE CASCADE,
    agent_group_id VARCHAR(255)             REFERENCES "AgentGroup"(id) ON DELETE SET NULL,
    status         BOOLEAN,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_agent_group_id ON "Agent" (agent_group_id);

-- ── Status ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Status" (
    id            VARCHAR(255) PRIMARY KEY,
    name          VARCHAR(255) NOT NULL UNIQUE,
    description   TEXT,
    show_on_reply BOOLEAN,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── Severity ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Severity" (
    id          VARCHAR(255) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── IncidentType ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "IncidentType" (
    id               VARCHAR(255) PRIMARY KEY,
    name             VARCHAR(255) NOT NULL UNIQUE,
    description      TEXT         NOT NULL,
    admin_id         VARCHAR(255) NOT NULL,
    agent_id         VARCHAR(255) NOT NULL REFERENCES "Agent"(id) ON DELETE RESTRICT,
    visible_to_group BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_incident_type_agent_id ON "IncidentType" (agent_id);

-- ── Incident ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Incident" (
    id               VARCHAR(255) PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    incident_no      SERIAL       NOT NULL,
    description      TEXT         NOT NULL,
    user_id          VARCHAR(255) NOT NULL REFERENCES "User"(id)         ON DELETE RESTRICT,
    location_id      VARCHAR(255) NOT NULL REFERENCES "Location"(id)     ON DELETE RESTRICT,
    incident_type_id VARCHAR(255) NOT NULL REFERENCES "IncidentType"(id) ON DELETE RESTRICT,
    status_id        VARCHAR(255)          REFERENCES "Status"(id)        ON DELETE SET NULL,
    severity_id      VARCHAR(255)          REFERENCES "Severity"(id)      ON DELETE SET NULL,
    assigned_to_id   VARCHAR(255)          REFERENCES "Agent"(id)         ON DELETE SET NULL,
    read             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_incident_user_id          ON "Incident" (user_id);
CREATE INDEX IF NOT EXISTS idx_incident_location_id      ON "Incident" (location_id);
CREATE INDEX IF NOT EXISTS idx_incident_incident_type_id ON "Incident" (incident_type_id);
CREATE INDEX IF NOT EXISTS idx_incident_status_id        ON "Incident" (status_id);
CREATE INDEX IF NOT EXISTS idx_incident_severity_id      ON "Incident" (severity_id);
CREATE INDEX IF NOT EXISTS idx_incident_assigned_to_id   ON "Incident" (assigned_to_id);

-- ── IncidentLog ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "IncidentLog" (
    id          VARCHAR(255) PRIMARY KEY,
    incident_id VARCHAR(255) NOT NULL REFERENCES "Incident"(id) ON DELETE CASCADE,
    user_id     VARCHAR(255) NOT NULL REFERENCES "User"(id)     ON DELETE RESTRICT,
    text        TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_incident_log_incident_id ON "IncidentLog" (incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_log_user_id     ON "IncidentLog" (user_id);

-- ── IncidentReportLog ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "IncidentReportLog" (
    id          VARCHAR(255) PRIMARY KEY,
    incident_id VARCHAR(255) NOT NULL REFERENCES "Incident"(id) ON DELETE CASCADE,
    status_id   VARCHAR(255) NOT NULL REFERENCES "Status"(id)   ON DELETE RESTRICT,
    date        TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_incident_report_log_incident_id ON "IncidentReportLog" (incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_report_log_status_id   ON "IncidentReportLog" (status_id);

-- ── Message ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Message" (
    id          VARCHAR(255) PRIMARY KEY,
    sender_id   VARCHAR(255) NOT NULL REFERENCES "User"(id)     ON DELETE RESTRICT,
    incident_id VARCHAR(255) NOT NULL REFERENCES "Incident"(id) ON DELETE CASCADE,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_message_sender_id   ON "Message" (sender_id);
CREATE INDEX IF NOT EXISTS idx_message_incident_id ON "Message" (incident_id);

-- ── Media ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Media" (
    id            VARCHAR(255) PRIMARY KEY,
    incident_id   VARCHAR(255) NOT NULL REFERENCES "Incident"(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    file_key      VARCHAR(255) NOT NULL,
    url           TEXT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_media_incident_id ON "Media" (incident_id);
