-- Add resolved_at to Incident
ALTER TABLE "Incident" ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;

-- System configuration table (key-value store for admin-managed settings)
CREATE TABLE IF NOT EXISTS system_config (
    key        VARCHAR(255) PRIMARY KEY,
    value      VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO system_config (key, value, updated_at)
VALUES ('auto_close_hours', '72', NOW())
ON CONFLICT (key) DO NOTHING;

-- Permissions for reading and updating system config
INSERT INTO permissions (code, name, description)
VALUES
    ('system.config.read',   'Read System Config',   'Allows viewing global system configuration.'),
    ('system.config.update', 'Update System Config', 'Allows updating global system configuration.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT r.role_code, p.id
FROM (VALUES ('ADMIN'), ('SUPER_ADMIN')) AS r(role_code)
CROSS JOIN permissions p
WHERE p.code IN ('system.config.read', 'system.config.update')
ON CONFLICT (role_code, permission_id) DO NOTHING;
