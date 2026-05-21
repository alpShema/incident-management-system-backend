INSERT INTO permissions (code, name, description)
VALUES
    ('agent-group.read',   'Read Departments',   'View departments'),
    ('agent-group.create', 'Create Departments', 'Create departments'),
    ('agent-group.update', 'Update Departments', 'Update departments'),
    ('agent-group.delete', 'Delete Departments', 'Delete departments')
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO role_permissions (role_code, permission_id)
SELECT role_code, permission.id
FROM (VALUES ('ADMIN'), ('SUPER_ADMIN')) AS roles(role_code)
CROSS JOIN permissions permission
WHERE permission.code IN (
    'agent-group.read',
    'agent-group.create',
    'agent-group.update',
    'agent-group.delete'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
