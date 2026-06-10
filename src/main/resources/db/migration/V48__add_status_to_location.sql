ALTER TABLE "Location" ADD COLUMN IF NOT EXISTS status BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO permissions (code, name, description)
VALUES
    ('location.create', 'Create Location', 'Allows creation of locations.'),
    ('location.update', 'Update Location', 'Allows editing locations.'),
    ('location.delete', 'Delete Location', 'Allows deactivation of locations.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('ADMIN'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code IN ('location.create', 'location.update', 'location.delete')
ON CONFLICT (role_code, permission_id) DO NOTHING;
