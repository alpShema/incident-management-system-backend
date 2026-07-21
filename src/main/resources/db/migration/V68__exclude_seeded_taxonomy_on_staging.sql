-- V21 seeded a placeholder Department/Category/Topic taxonomy so incidents
-- could be created out of the box. Those IDs drive real incident routing,
-- and having them present on staging pollutes the taxonomy and lets
-- incidents get miscreated/misrouted against fake categories during staging
-- validation.
--
-- This migration removes the known placeholder rows, gated by the
-- seedDemoTaxonomy Flyway placeholder (see spring.flyway.placeholders in
-- application.yaml / application-staging.yaml). It intentionally does NOT
-- edit V21 itself — that migration has already run against every existing
-- database, so editing it wouldn't clean up what's already there. Instead:
--   - dev/testing (seedDemoTaxonomy=true): no-op, today's behavior is unchanged.
--   - staging/production (seedDemoTaxonomy=false): the rows below are deleted
--     if unused. On a brand-new database this runs right after V21 inserts
--     them, so the net result is an empty taxonomy. On the current staging
--     database it retroactively cleans up the existing placeholder rows.
--
-- Deletes are guarded by NOT EXISTS rather than relying on the FK
-- (Incident.incident_type_id is ON DELETE RESTRICT) so that if staging
-- already has a real incident pointing at one of these seeded types, this
-- migration still succeeds — it just leaves that specific row in place. Any
-- row left behind is a signal an admin needs to reassign that incident
-- before the taxonomy is fully clean.
DO $$
BEGIN
    IF '${seedDemoTaxonomy}' = 'false' THEN
        DELETE FROM "IncidentType"
        WHERE id IN (
            'type-account-issues', 'type-network-issues', 'type-software-issues',
            'type-payroll', 'type-leave-time-off', 'type-workplace-conduct',
            'type-maintenance', 'type-security', 'type-safety'
        )
        AND NOT EXISTS (
            SELECT 1 FROM "Incident" i WHERE i.incident_type_id = "IncidentType".id
        );

        DELETE FROM "IncidentCategory"
        WHERE id IN ('cat-it', 'cat-hr', 'cat-facilities')
        AND NOT EXISTS (
            SELECT 1 FROM "IncidentType" it WHERE it.category_id = "IncidentCategory".id
        );
    END IF;
END $$;
