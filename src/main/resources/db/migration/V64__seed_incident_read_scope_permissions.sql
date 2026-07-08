-- Seed incident.read.all and incident.read.department permissions.
-- incident.read.all        -> ADMIN, ADMIN_AGENT, SUPER_ADMIN (view every incident)
-- incident.read.department -> AGENT, ADMIN_AGENT (view incidents in the agent's department)

INSERT INTO permissions (code, name, description)
VALUES
    ('incident.read.all',        'Read All Incidents',        'Allows viewing every incident regardless of assignment or department.'),
    ('incident.read.department', 'Read Department Incidents', 'Allows an agent to view incidents belonging to their department.')
ON CONFLICT (code) DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        updated_at  = NOW();

-- incident.read.all -> ADMIN, ADMIN_AGENT, SUPER_ADMIN
INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('ADMIN'), ('ADMIN_AGENT'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code = 'incident.read.all'
ON CONFLICT (role_code, permission_id) DO NOTHING;

-- incident.read.department -> AGENT, ADMIN_AGENT
INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('AGENT'), ('ADMIN_AGENT')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code = 'incident.read.department'
ON CONFLICT (role_code, permission_id) DO NOTHING;
