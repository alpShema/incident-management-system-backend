-- V21 seeded a placeholder "System" user (system-seed) purely to satisfy the
-- NOT NULL admin_id/agent_id FKs on the demo IncidentType rows removed in
-- V68. V35 seeded a "Super Admin" row in the roles catalog as one of four
-- built-in roles. Per HV-1468, neither should exist on staging/production —
-- they should be created deliberately by an admin instead.
--
-- Gated by the seedDemoData Flyway placeholder (see spring.flyway.placeholders
-- in application.yaml / application-staging.yaml):
--   - dev/testing (seedDemoData=true): no-op, today's behavior is unchanged.
--   - staging/production (seedDemoData=false): rows removed below.
--
-- System user: deleting "User" cascades to its "Agent" row (Agent.user_id is
-- ON DELETE CASCADE), but IncidentType.agent_id is ON DELETE RESTRICT, so
-- that cascade — and therefore the whole delete — fails outright if a real
-- IncidentType still points at the seeded agent (e.g. V68 left one in place
-- because a real Incident already used it). Guard with NOT EXISTS so this
-- migration always succeeds; a leftover row is a signal an admin still needs
-- to reassign that IncidentType before the system user can be removed.
--
-- Super Admin role: role_permissions.role_code and User.role_code are plain
-- varchar columns with no FK to roles, so removing this catalog row is safe
-- unconditionally — it only affects the admin-facing role list, not any
-- existing permission grants or user role assignments.
DO $$
BEGIN
    IF '${seedDemoData}' = 'false' THEN
        DELETE FROM "User"
        WHERE id = 'system-seed'
        AND NOT EXISTS (
            SELECT 1 FROM "IncidentType" it WHERE it.agent_id = 'agent-seed'
        );

        DELETE FROM roles WHERE id = 'role-super-admin';
    END IF;
END $$;
