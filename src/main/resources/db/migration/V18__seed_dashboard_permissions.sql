INSERT INTO permissions (code, name, description)
VALUES
    ('dashboard.agent', 'Agent Dashboard', 'Allows an agent to view their own dashboard summary.'),
    ('dashboard.admin', 'Admin Dashboard', 'Allows an admin to view the platform-wide dashboard summary.')
ON CONFLICT (code) DO UPDATE
SET
    name        = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at  = NOW();

-- Grant dashboard.agent to AGENT only
INSERT INTO role_permissions (role_code, permission_id)
SELECT 'AGENT', p.id FROM permissions p WHERE p.code = 'dashboard.agent'
ON CONFLICT (role_code, permission_id) DO NOTHING;

-- Grant dashboard.admin + missing incident permissions to ADMIN
INSERT INTO role_permissions (role_code, permission_id)
SELECT 'ADMIN', p.id FROM permissions p
WHERE p.code IN (
    'dashboard.admin',
    'incident.read.assigned',
    'incident.assign',
    'incident.status.change',
    'incident.severity.change',
    'rbac.role.read',
    'rbac.user-role.update'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;

-- Grant dashboard.admin + missing incident permissions to SUPER_ADMIN
INSERT INTO role_permissions (role_code, permission_id)
SELECT 'SUPER_ADMIN', p.id FROM permissions p
WHERE p.code IN (
    'dashboard.admin',
    'incident.read.assigned',
    'incident.assign',
    'incident.status.change',
    'incident.severity.change'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
