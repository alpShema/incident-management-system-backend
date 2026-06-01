ALTER TABLE "AgentGroup" DROP CONSTRAINT IF EXISTS fk_agent_group_primary_agent;
DROP INDEX IF EXISTS idx_agent_group_primary_agent_id;
ALTER TABLE "AgentGroup" DROP COLUMN IF EXISTS primary_agent_id;
