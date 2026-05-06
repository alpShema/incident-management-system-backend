CREATE TABLE IF NOT EXISTS activity_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id VARCHAR(255) REFERENCES "User"(id) ON DELETE SET NULL,
    target_user_id VARCHAR(255) REFERENCES "User"(id) ON DELETE SET NULL,
    action VARCHAR(128) NOT NULL,
    subject_type VARCHAR(128) NOT NULL,
    subject_id VARCHAR(255),
    description TEXT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_activity_logs_actor_user_id ON activity_logs (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_activity_logs_target_user_id ON activity_logs (target_user_id);
CREATE INDEX IF NOT EXISTS idx_activity_logs_action ON activity_logs (action);
CREATE INDEX IF NOT EXISTS idx_activity_logs_subject_type_subject_id ON activity_logs (subject_type, subject_id);
