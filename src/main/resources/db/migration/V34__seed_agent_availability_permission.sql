INSERT INTO permissions (code, name, description)
VALUES ('agent.availability.update', 'Agent Availability Update', 'Update own availability status')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_id)
SELECT 'AGENT', id FROM permissions WHERE code = 'agent.availability.update'
ON CONFLICT (role_code, permission_id) DO NOTHING;
