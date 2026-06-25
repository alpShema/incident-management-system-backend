-- Seed chatbot and FAQ permissions that are defined in RbacPermissions.java
-- but were never inserted into the database.

-- ── Chatbot Permissions ────────────────────────────────────────────────────────

INSERT INTO permissions (code, name, description)
VALUES
    ('chatbot.query',             'Chatbot Query',             'Submit queries to the AI chatbot assistant'),
    ('chatbot.interactions.read', 'Read Chatbot Interactions', 'View and audit chatbot interaction history')
ON CONFLICT (code) DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        updated_at  = NOW();

-- chatbot.query → CLIENT, AGENT, ADMIN, SUPER_ADMIN
INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('CLIENT'), ('AGENT'), ('ADMIN'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code = 'chatbot.query'
ON CONFLICT (role_code, permission_id) DO NOTHING;

-- chatbot.interactions.read → ADMIN, SUPER_ADMIN only
INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('ADMIN'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code = 'chatbot.interactions.read'
ON CONFLICT (role_code, permission_id) DO NOTHING;

-- ── FAQ Permissions ────────────────────────────────────────────────────────────

INSERT INTO permissions (code, name, description)
VALUES
    ('faq.read',   'Read FAQs',   'View FAQ entries in the knowledge base'),
    ('faq.create', 'Create FAQ',  'Add new FAQ entries to the knowledge base'),
    ('faq.update', 'Update FAQ',  'Edit or toggle the active state of FAQ entries'),
    ('faq.delete', 'Delete FAQ',  'Remove FAQ entries from the knowledge base')
ON CONFLICT (code) DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        updated_at  = NOW();

-- faq.read → CLIENT, AGENT, ADMIN, SUPER_ADMIN
INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('CLIENT'), ('AGENT'), ('ADMIN'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code = 'faq.read'
ON CONFLICT (role_code, permission_id) DO NOTHING;

-- faq.create, faq.update, faq.delete → ADMIN, SUPER_ADMIN only
INSERT INTO role_permissions (role_code, permission_id)
SELECT role.code, permission.id
FROM (VALUES ('ADMIN'), ('SUPER_ADMIN')) AS role(code)
CROSS JOIN permissions permission
WHERE permission.code IN ('faq.create', 'faq.update', 'faq.delete')
ON CONFLICT (role_code, permission_id) DO NOTHING;
