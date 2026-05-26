CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    system_defined BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO roles (id, code, name, description, system_defined)
VALUES
    ('role-client', 'CLIENT', 'Client', 'Built-in client role', TRUE),
    ('role-agent', 'AGENT', 'Agent', 'Built-in agent role', TRUE),
    ('role-admin', 'ADMIN', 'Admin', 'Built-in admin role', TRUE),
    ('role-super-admin', 'SUPER_ADMIN', 'Super Admin', 'Built-in super admin role', TRUE)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE role_permissions
    ALTER COLUMN role_code TYPE VARCHAR(64);

UPDATE "User"
SET role_code = UPPER(role_code::text)
WHERE role_code IS NOT NULL;

INSERT INTO roles (id, code, name, description, system_defined)
SELECT
    'role-' || LOWER(REPLACE(rp.role_code, ' ', '-')),
    rp.role_code,
    INITCAP(REPLACE(LOWER(rp.role_code), '_', ' ')),
    'Auto-imported role from existing role_permissions',
    FALSE
FROM role_permissions rp
LEFT JOIN roles r ON r.code = rp.role_code
WHERE r.code IS NULL
GROUP BY rp.role_code;
