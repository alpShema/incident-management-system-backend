INSERT INTO permissions (code, name, description)
VALUES
    ('incident-category.create', 'Create Incident Category', 'Allows creation of incident categories.'),
    ('incident-category.update', 'Update Incident Category', 'Allows editing incident categories.'),
    ('incident-category.delete', 'Delete Incident Category', 'Allows deletion of incident categories.')
ON CONFLICT (code) DO UPDATE
SET
    name        = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at  = NOW();

INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('ADMIN'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code IN (
    'incident-category.create',
    'incident-category.update',
    'incident-category.delete'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
