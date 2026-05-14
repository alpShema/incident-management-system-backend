INSERT INTO role_permissions (role_code, permission_id)
SELECT 'AGENT', permission.id
FROM permissions permission
WHERE permission.code = 'incident-type.create'
ON CONFLICT (role_code, permission_id) DO NOTHING;
