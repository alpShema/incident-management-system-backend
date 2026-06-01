INSERT INTO permissions (code, name, description)
VALUES
    ('agent.availability.update.any', 'Agent Availability Update (Any)', 'Update availability status for any agent'),
    ('admin.availability.update', 'Admin Availability Update', 'Update own availability status')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'ADMIN', id FROM permissions WHERE code IN ('agent.availability.update.any', 'admin.availability.update')
ON CONFLICT (role_code, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'SUPER_ADMIN', id FROM permissions WHERE code IN ('agent.availability.update.any', 'admin.availability.update')
ON CONFLICT (role_code, permission_id) DO NOTHING;
