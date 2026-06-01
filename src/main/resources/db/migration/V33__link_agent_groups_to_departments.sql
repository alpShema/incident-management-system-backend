ALTER TABLE "AgentGroup"
    ADD COLUMN IF NOT EXISTS department_id VARCHAR(255);

ALTER TABLE "AgentGroup"
    ADD CONSTRAINT fk_agent_group_department
        FOREIGN KEY (department_id) REFERENCES "Department"(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_agent_group_department_id
    ON "AgentGroup" (department_id);
