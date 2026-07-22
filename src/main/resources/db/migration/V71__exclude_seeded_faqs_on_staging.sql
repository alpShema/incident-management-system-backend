-- V57 seeded 5 placeholder FAQs (password reset, incident reporting, incident
-- status, urgent support, resolution time) as demo content so the chatbot had
-- something to answer against out of the box. That placeholder content
-- shouldn't exist on staging/production — FAQs there should be authored
-- deliberately by an admin for the real deployment, not inherited from the
-- dev/testing seed set.
--
-- This migration removes the known seeded rows, gated by the seedDemoData
-- Flyway placeholder (see spring.flyway.placeholders in application.yaml /
-- application-staging.yaml) — per the note left on that placeholder, new seed
-- exclusions route through seedDemoData rather than adding a new flag. It
-- intentionally does NOT edit V57 itself — that migration has already run
-- against every existing database, so editing it wouldn't clean up what's
-- already there. Instead:
--   - dev/testing (seedDemoData=true): no-op, today's behavior is unchanged.
--   - staging/production (seedDemoData=false): the rows below are deleted if
--     unused. On a brand-new database this runs right after V57 inserts them,
--     so the net result is no seeded FAQs. On the current staging database it
--     retroactively cleans up the existing placeholder rows.
--
-- Guarded by NOT EXISTS rather than relying on the FK (ChatbotInteraction.faq_id
-- has no ON DELETE clause, i.e. RESTRICT) so this migration always succeeds —
-- if a seeded FAQ has already answered a real chatbot query on staging, it's
-- left in place, which is a signal to review that interaction before removing
-- the FAQ by hand.
DO $$
BEGIN
    IF '${seedDemoData}' = 'false' THEN
        DELETE FROM "Faq"
        WHERE id IN ('faq-001', 'faq-002', 'faq-003', 'faq-004', 'faq-005')
        AND NOT EXISTS (
            SELECT 1 FROM "ChatbotInteraction" ci WHERE ci.faq_id = "Faq".id
        );
    END IF;
END $$;