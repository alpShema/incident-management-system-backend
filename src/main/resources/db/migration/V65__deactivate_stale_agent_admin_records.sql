-- Deactivate Agent/Admin shadow records left active after a user's role was
-- changed away from AGENT/ADMIN_AGENT or ADMIN/SUPER_ADMIN by prior buggy role-change paths.
DELETE FROM "AgentGroupMember" agm
USING "Agent" a
JOIN "User" u ON u.id = a.user_id
WHERE agm.agent_id = a.id
  AND (u.role_code IS NULL OR u.role_code NOT IN ('AGENT', 'ADMIN_AGENT'));

UPDATE "Agent" a
SET status = FALSE, updated_at = NOW()
FROM "User" u
WHERE u.id = a.user_id
  AND (u.role_code IS NULL OR u.role_code NOT IN ('AGENT', 'ADMIN_AGENT'))
  AND a.status = TRUE;

UPDATE "Admin" ad
SET status = FALSE, updated_at = NOW()
FROM "User" u
WHERE u.id = ad.user_id
  AND (u.role_code IS NULL OR u.role_code NOT IN ('ADMIN', 'SUPER_ADMIN'))
  AND ad.status = TRUE;
