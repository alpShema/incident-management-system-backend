INSERT INTO role_permissions (role_code, permission_id)
SELECT 'ADMIN', p.id
FROM permissions p
WHERE p.code = 'rbac.role.update'
ON CONFLICT (role_code, permission_id) DO NOTHING;
