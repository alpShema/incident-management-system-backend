-- role_permissions.role_code had no foreign key back to roles.code, so deleting a role's
-- row directly (there is no delete-role feature in the app, only manual DB intervention)
-- silently orphaned its permission links. A later attempt to create a new role that
-- generates the same code then collides with the orphaned (role_code, permission_id) rows
-- on the unique constraint, failing with a generic "record already exists" error even
-- though no role with that name exists in the roles table.
DELETE FROM role_permissions
WHERE role_code NOT IN (SELECT code FROM roles);

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_role_permissions_role_code
    FOREIGN KEY (role_code) REFERENCES roles(code) ON DELETE CASCADE;
