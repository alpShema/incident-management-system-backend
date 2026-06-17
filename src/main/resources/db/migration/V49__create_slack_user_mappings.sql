-- Slack user mappings: links Slack user IDs to HILFE user IDs
CREATE TABLE slack_user_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slack_user_id VARCHAR(255) NOT NULL UNIQUE,
    hilfe_user_id VARCHAR(255) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    slack_team_id VARCHAR(255) NOT NULL,
    access_token TEXT,
    bot_token TEXT,
    connected_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_slack_mappings_hilfe_user ON slack_user_mappings(hilfe_user_id);
CREATE INDEX idx_slack_mappings_slack_user ON slack_user_mappings(slack_user_id);
CREATE INDEX idx_slack_mappings_team ON slack_user_mappings(slack_team_id);
