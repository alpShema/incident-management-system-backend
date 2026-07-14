-- Slack notification preferences: per-user, per-notification-type toggles
CREATE TABLE slack_notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    notification_type VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, notification_type)
);

CREATE INDEX idx_slack_prefs_user ON slack_notification_preferences(user_id);
