-- agent_id on IncidentType was originally required when topics had a pre-assigned agent.
-- The createTopic flow does not accept an agentId (topics are assigned to an agent group,
-- not a specific agent), so the NOT NULL constraint caused a 500 on every topic creation.
-- IncidentService already null-checks agentId before using it for auto-assignment, so
-- making this column nullable is safe and does not affect incident routing behaviour.
ALTER TABLE "IncidentType" ALTER COLUMN agent_id DROP NOT NULL;
