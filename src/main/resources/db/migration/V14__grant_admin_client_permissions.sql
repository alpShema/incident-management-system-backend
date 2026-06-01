INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('ADMIN'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code IN (
    'incident.create',
    'incident.read.own'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
