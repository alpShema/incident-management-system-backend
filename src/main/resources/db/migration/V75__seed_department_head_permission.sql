-- Seed department.head permission.
--
-- Unlike every other seeded permission, this one is NOT granted via role_permissions.
-- It is computed dynamically per-request in UserAuthorityService, based on whether
-- Department.head_user_id currently points at the resolving user — being a HOD is a
-- per-user, per-assignment fact, not a static property of a role. Deliberately no
-- INSERT INTO role_permissions here.

INSERT INTO permissions (code, name, description)
VALUES
    ('department.head', 'Department Head', 'Held by a user for as long as they are the assigned head of at least one department.')
ON CONFLICT (code) DO UPDATE
    SET name        = EXCLUDED.name,
        description = EXCLUDED.description,
        updated_at  = NOW();
