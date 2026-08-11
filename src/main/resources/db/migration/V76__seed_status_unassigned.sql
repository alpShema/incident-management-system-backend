INSERT INTO "Status" (id, name, description, show_on_reply, created_at, updated_at)
VALUES
    ('status-unassigned', 'Unassigned', 'Incident has not yet been assigned to an agent.', false, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;