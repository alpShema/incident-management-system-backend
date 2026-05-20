INSERT INTO role_permissions (role_code, permission_id)
SELECT 'CLIENT', p.id
FROM permissions p
WHERE p.code = 'incident.status.change'
ON CONFLICT (role_code, permission_id) DO NOTHING;
