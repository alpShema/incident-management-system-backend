INSERT INTO role_permissions (role_code, permission_id)
SELECT 'ADMIN', permission.id
FROM permissions permission
WHERE permission.code IN (
    'rbac.role.read',
    'rbac.user-role.update'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
