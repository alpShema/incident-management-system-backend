-- V8__domain_tables.sql
-- Creates the domain tables that Hibernate expects but were missing from
-- the original migration set (Status, Severity, IncidentType, Incident,
-- IncidentLog, IncidentReportLog, Message, Media).

-- ── Status ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS "Status" (
    id             VARCHAR(255) PRIMARY KEY,
    name           VARCHAR(255) NOT NULL UNIQUE,
    description    TEXT,
    show_on_reply  BOOLEAN,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
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
    status_id        VARCHAR(255)          REFERENCES "Status"(id)       ON DELETE SET NULL,
    severity_id      VARCHAR(255)          REFERENCES "Severity"(id)     ON DELETE SET NULL,
    assigned_to_id   VARCHAR(255)          REFERENCES "Agent"(id)        ON DELETE SET NULL,
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
