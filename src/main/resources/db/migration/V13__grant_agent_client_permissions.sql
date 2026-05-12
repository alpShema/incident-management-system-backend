INSERT INTO role_permissions (role_code, permission_id)
SELECT 'AGENT', permission.id
FROM permissions permission
WHERE permission.code IN (
    'incident.create',
    'incident.read.own'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
