CREATE TABLE IF NOT EXISTS "MessageMedia" (
    id            VARCHAR(255) PRIMARY KEY,
    message_id    VARCHAR(255) NOT NULL REFERENCES "Message"(id) ON DELETE CASCADE,
    incident_id   VARCHAR(255) NOT NULL REFERENCES "Incident"(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    file_key      VARCHAR(255) NOT NULL UNIQUE,
    content_type  VARCHAR(255) NOT NULL,
    file_size     BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_message_media_message_id ON "MessageMedia" (message_id);
CREATE INDEX IF NOT EXISTS idx_message_media_incident_id ON "MessageMedia" (incident_id);

ALTER TABLE "Message"
    ALTER COLUMN content DROP NOT NULL;
