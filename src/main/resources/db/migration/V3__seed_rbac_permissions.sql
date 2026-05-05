INSERT INTO permissions (code, name, description, system_defined)
VALUES
    ('incident.create', 'Create Incident', 'Allows a user to create a new incident.', TRUE),
    ('incident.read.own', 'Read Own Incidents', 'Allows a user to view incidents they created.', TRUE),
    ('incident.read.assigned', 'Read Assigned Incidents', 'Allows an agent to view incidents assigned to them.', TRUE),
    ('incident.assign', 'Assign Incident', 'Allows an agent to reassign an incident.', TRUE),
    ('incident.status.change', 'Change Incident Status', 'Allows an agent to change incident status.', TRUE),
    ('incident.severity.change', 'Change Incident Severity', 'Allows an agent to change incident severity.', TRUE),
    ('agent.create', 'Create Agent', 'Allows creation of agent accounts.', TRUE),
    ('agent.read', 'Read Agent', 'Allows viewing agent details.', TRUE),
    ('agent.update', 'Update Agent', 'Allows editing agent details.', TRUE),
    ('agent.delete', 'Delete Agent', 'Allows deletion of agents.', TRUE),
    ('agent-group.create', 'Create Agent Group', 'Allows creation of agent groups.', TRUE),
    ('agent-group.update', 'Update Agent Group', 'Allows editing agent groups.', TRUE),
    ('agent-group.delete', 'Delete Agent Group', 'Allows deletion of agent groups.', TRUE),
    ('status.create', 'Create Status', 'Allows creation of incident statuses.', TRUE),
    ('status.update', 'Update Status', 'Allows editing incident statuses.', TRUE),
    ('status.delete', 'Delete Status', 'Allows deletion of incident statuses.', TRUE),
    ('severity.create', 'Create Severity', 'Allows creation of severities.', TRUE),
    ('severity.update', 'Update Severity', 'Allows editing severities.', TRUE),
    ('severity.delete', 'Delete Severity', 'Allows deletion of severities.', TRUE),
    ('location.create', 'Create Location', 'Allows creation of locations.', TRUE),
    ('location.update', 'Update Location', 'Allows editing locations.', TRUE),
    ('location.delete', 'Delete Location', 'Allows deletion of locations.', TRUE),
    ('incident-type.create', 'Create Incident Type', 'Allows creation of incident types.', TRUE),
    ('incident-type.update', 'Update Incident Type', 'Allows editing incident types.', TRUE),
    ('incident-type.delete', 'Delete Incident Type', 'Allows deletion of incident types.', TRUE),
    ('rbac.role.read', 'Read RBAC Roles', 'Allows viewing RBAC role definitions.', TRUE),
    ('rbac.role.update', 'Update RBAC Roles', 'Allows editing RBAC role permission mappings.', TRUE),
    ('rbac.user-role.update', 'Update User Role', 'Allows assigning or changing user roles.', TRUE),
    ('rbac.permission.read', 'Read RBAC Permissions', 'Allows viewing permission definitions.', TRUE)
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_defined = EXCLUDED.system_defined,
    updated_at = NOW();

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'CLIENT', permission.id
FROM permissions permission
WHERE permission.code IN (
    'incident.create',
    'incident.read.own'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'AGENT', permission.id
FROM permissions permission
WHERE permission.code IN (
    'incident.read.assigned',
    'incident.assign',
    'incident.status.change',
    'incident.severity.change',
    'agent.read'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'ADMIN', permission.id
FROM permissions permission
WHERE permission.code IN (
    'agent.create',
    'agent.read',
    'agent.update',
    'agent.delete',
    'agent-group.create',
    'agent-group.update',
    'agent-group.delete',
    'status.create',
    'status.update',
    'status.delete',
    'severity.create',
    'severity.update',
    'severity.delete',
    'location.create',
    'location.update',
    'location.delete',
    'incident-type.create',
    'incident-type.update',
    'incident-type.delete',
    'rbac.permission.read'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'SUPER_ADMIN', permission.id
FROM permissions permission
WHERE permission.code IN (
    'agent.create',
    'agent.read',
    'agent.update',
    'agent.delete',
    'agent-group.create',
    'agent-group.update',
    'agent-group.delete',
    'status.create',
    'status.update',
    'status.delete',
    'severity.create',
    'severity.update',
    'severity.delete',
    'location.create',
    'location.update',
    'location.delete',
    'incident-type.create',
    'incident-type.update',
    'incident-type.delete',
    'rbac.role.read',
    'rbac.role.update',
    'rbac.user-role.update',
    'rbac.permission.read'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
