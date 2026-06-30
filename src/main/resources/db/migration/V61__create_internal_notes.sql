CREATE TABLE IF NOT EXISTS "InternalNote" (
    id          VARCHAR(255) PRIMARY KEY,
    incident_id VARCHAR(255) NOT NULL REFERENCES "Incident"(id) ON DELETE CASCADE,
    author_id   VARCHAR(255) NOT NULL REFERENCES "User"(id) ON DELETE RESTRICT,
    body        TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_internal_note_incident_id ON "InternalNote" (incident_id);
