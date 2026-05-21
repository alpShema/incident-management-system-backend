CREATE TABLE IF NOT EXISTS "Department" (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE "IncidentCategory"
    ADD COLUMN IF NOT EXISTS department_id VARCHAR(255);

ALTER TABLE "IncidentCategory"
    ADD CONSTRAINT fk_incident_category_department
        FOREIGN KEY (department_id) REFERENCES "Department"(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_incident_category_department_id
    ON "IncidentCategory" (department_id);

ALTER TABLE "AgentGroup"
    ADD COLUMN IF NOT EXISTS primary_agent_id VARCHAR(255);

ALTER TABLE "AgentGroup"
    ADD CONSTRAINT fk_agent_group_primary_agent
        FOREIGN KEY (primary_agent_id) REFERENCES "Agent"(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_agent_group_primary_agent_id
    ON "AgentGroup" (primary_agent_id);

ALTER TABLE "IncidentType"
    ADD COLUMN IF NOT EXISTS agent_group_id VARCHAR(255);

UPDATE "IncidentType" incident_type
SET agent_group_id = agent.agent_group_id
FROM "Agent" agent
WHERE incident_type.agent_id = agent.id
  AND incident_type.agent_group_id IS NULL;

ALTER TABLE "IncidentType"
    ADD CONSTRAINT fk_incident_type_agent_group
        FOREIGN KEY (agent_group_id) REFERENCES "AgentGroup"(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_incident_type_agent_group_id
    ON "IncidentType" (agent_group_id);

INSERT INTO permissions (code, name, description)
VALUES
    ('department.read',   'Read Departments',   'View internal departments'),
    ('department.create', 'Create Departments', 'Create internal departments'),
    ('department.update', 'Update Departments', 'Update internal departments'),
    ('department.delete', 'Delete Departments', 'Delete internal departments')
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
    'department.read',
    'department.create',
    'department.update',
    'department.delete'
)
ON CONFLICT (role_code, permission_id) DO NOTHING;
