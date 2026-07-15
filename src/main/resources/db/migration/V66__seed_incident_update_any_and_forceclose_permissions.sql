-- Seed incident.update.any and incident.forceclose permissions.
--
-- Prior to this migration, cross-incident update access (reassigning, changing status/severity
-- of an incident the actor doesn't own) was granted by hardcoded role checks in IncidentService
-- for ADMIN, ADMIN_AGENT, and SUPER_ADMIN. ADMIN_AGENT should not have received this unrestricted
-- access implicitly — access must instead be explicitly permission-gated.
--
-- incident.update.any -> ADMIN, SUPER_ADMIN (bypass ownership checks on assignment/severity updates)
-- incident.forceclose  -> ADMIN, SUPER_ADMIN (force-close an incident regardless of current status)
--
-- ADMIN_AGENT intentionally does NOT receive either permission by default.

INSERT INTO permissions (code, name, description)
VALUES
    ('incident.update.any', 'Update Any Incident',   'Allows updating (reassigning, changing severity) an incident regardless of ownership or assignment.'),
    ('incident.forceclose', 'Force-Close Incident',  'Allows closing an incident regardless of its current status, bypassing the normal status transition rules.')
ON CONFLICT (code) DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        updated_at  = NOW();

INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('ADMIN'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code IN ('incident.update.any', 'incident.forceclose')
ON CONFLICT (role_code, permission_id) DO NOTHING;
