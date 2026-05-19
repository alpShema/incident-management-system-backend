INSERT INTO "Status" (id, name, description, show_on_reply, created_at, updated_at)
VALUES
    ('status-in-progress', 'In Progress', 'An agent has been assigned and is actively working on the incident.', true, NOW(), NOW()),
    ('status-reopened',    'Reopened',    'Client has indicated the issue persists after resolution.',           true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
