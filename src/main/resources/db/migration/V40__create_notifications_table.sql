CREATE TABLE IF NOT EXISTS notifications (
    id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id     VARCHAR(255) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    incident_id VARCHAR(255) REFERENCES "Incident"(id) ON DELETE SET NULL,
    type        VARCHAR(100) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    message     TEXT         NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;
