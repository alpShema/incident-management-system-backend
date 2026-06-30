INSERT INTO roles (id, code, name, description, system_defined)
VALUES ('role-admin-agent', 'ADMIN_AGENT', 'Admin Agent', 'Built-in role combining full admin permissions with agent responsibilities', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'ADMIN_AGENT', p.id
FROM permissions p
WHERE p.code IN (
    'incident.create',
    'incident.read.own',
    'incident.read.assigned',
    'incident.assign',
    'incident.status.change',
    'incident.severity.change',
    'agent.create',
    'agent.read',
    'agent.update',
    'agent.delete',
    'admin.availability.update',
    'agent.availability.update',
    'agent.availability.update.any',
    'agent-group.read',
    'agent-group.create',
    'agent-group.update',
    'agent-group.delete',
    'department.read',
    'department.create',
    'department.update',
    'department.delete',
    'status.create',
    'status.update',
    'status.delete',
    'severity.create',
    'severity.update',
    'severity.delete',
    'location.create',
    'location.update',
    'location.delete',
    'incident-category.create',
    'incident-category.update',
    'incident-category.delete',
    'incident-type.create',
    'incident-type.update',
    'incident-type.delete',
    'rbac.role.read',
    'rbac.role.update',
    'rbac.user-role.update',
    'rbac.permission.read',
    'dashboard.admin',
    'system.config.read',
    'system.config.update',
    'faq.read',
    'faq.create',
    'faq.update',
    'faq.delete',
    'chatbot.query',
    'chatbot.interactions.read'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
