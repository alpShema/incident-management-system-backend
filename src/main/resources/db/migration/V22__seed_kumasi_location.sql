INSERT INTO "Location" (id, name, description, created_at, updated_at)
VALUES ('loc-kumasi', 'Kumasi', 'Ashanti Region office', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;
