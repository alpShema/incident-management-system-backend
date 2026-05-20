CREATE TABLE IF NOT EXISTS "AgentGroupMember" (
    agent_id VARCHAR(255) NOT NULL,
    agent_group_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (agent_id, agent_group_id),
    CONSTRAINT fk_agent_group_member_agent
        FOREIGN KEY (agent_id) REFERENCES "Agent"(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_group_member_agent_group
        FOREIGN KEY (agent_group_id) REFERENCES "AgentGroup"(id) ON DELETE CASCADE
);

INSERT INTO "AgentGroupMember" (agent_id, agent_group_id)
SELECT id, agent_group_id
FROM "Agent"
WHERE agent_group_id IS NOT NULL
ON CONFLICT (agent_id, agent_group_id) DO NOTHING;

INSERT INTO "AgentGroupMember" (agent_id, agent_group_id)
SELECT primary_agent_id, id
FROM "AgentGroup"
WHERE primary_agent_id IS NOT NULL
ON CONFLICT (agent_id, agent_group_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_agent_group_member_agent_id
    ON "AgentGroupMember" (agent_id);

CREATE INDEX IF NOT EXISTS idx_agent_group_member_agent_group_id
    ON "AgentGroupMember" (agent_group_id);
