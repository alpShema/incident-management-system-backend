-- Grant incident.update.any and incident.forceclose to ADMIN_AGENT.
--
-- V66 deliberately withheld these permissions from ADMIN_AGENT while replacing the
-- hardcoded role checks in IncidentService. ADMIN_AGENT now needs the same cross-incident
-- update and force-close capability as ADMIN/SUPER_ADMIN, granted explicitly here rather
-- than by widening V66's original seed.

INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('ADMIN_AGENT')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code IN ('incident.update.any', 'incident.forceclose')
ON CONFLICT (role_code, permission_id) DO NOTHING;
