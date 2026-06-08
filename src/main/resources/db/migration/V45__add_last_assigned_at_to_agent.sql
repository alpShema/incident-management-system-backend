ALTER TABLE "Agent"
    ADD COLUMN last_assigned_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_agent_last_assigned_at ON "Agent" (last_assigned_at);
