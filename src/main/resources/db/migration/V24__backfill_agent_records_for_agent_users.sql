INSERT INTO "Agent" (id, user_id, status, created_at, updated_at)
SELECT gen_random_uuid()::text, user_record.id, TRUE, NOW(), NOW()
FROM "User" user_record
LEFT JOIN "Agent" agent_record ON agent_record.user_id = user_record.id
WHERE user_record.role_code = 'AGENT'
  AND agent_record.id IS NULL;
