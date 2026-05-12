INSERT INTO "Status" (id, name, description, show_on_reply, created_at, updated_at)
VALUES
    ('status-open',     'Open',     'Incident has been submitted and is awaiting action', true,  NOW(), NOW()),
    ('status-pending',  'Pending',  'Incident is actively being worked on',               true,  NOW(), NOW()),
    ('status-resolved', 'Resolved', 'Incident has been resolved and is awaiting closure', true,  NOW(), NOW()),
    ('status-closed',   'Closed',   'Incident has been resolved and closed',               false, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
