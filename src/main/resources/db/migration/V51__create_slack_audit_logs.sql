-- Slack audit logs: tracks all Slack bot interactions for compliance and debugging
CREATE TABLE slack_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    slack_user_id VARCHAR(255),
    hilfe_user_id VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(255),
    metadata JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_slack_audit_user ON slack_audit_logs(hilfe_user_id);
CREATE INDEX idx_slack_audit_action ON slack_audit_logs(action);
CREATE INDEX idx_slack_audit_created ON slack_audit_logs(created_at);
CREATE INDEX idx_slack_audit_resource ON slack_audit_logs(resource_type, resource_id);
