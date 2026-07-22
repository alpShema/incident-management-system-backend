-- Seed incident.assignee permission.
--
-- Represents the capability of being a valid assignment target (the "assignee" side),
-- as opposed to incident.assign which represents the capability of assigning incidents
-- to others (the "assigner" side). Granted to AGENT and ADMIN_AGENT — the only roles
-- that can currently be assigned an incident (assignment eligibility is still enforced
-- via the Agent shadow table in IncidentService.assignIncident, unchanged by this migration).

INSERT INTO permissions (code, name, description)
VALUES
    ('incident.assignee', 'Assignable to Incidents', 'Marks a user as eligible to be assigned incidents.')
ON CONFLICT (code) DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        updated_at  = NOW();

INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('AGENT'), ('ADMIN_AGENT')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code IN ('incident.assignee')
ON CONFLICT (role_code, permission_id) DO NOTHING;
