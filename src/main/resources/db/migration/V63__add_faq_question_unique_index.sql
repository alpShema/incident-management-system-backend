-- Remove duplicate FAQs before enforcing uniqueness (keep the oldest by created_at).
-- Duplicates may exist on databases seeded before this constraint was introduced.
DELETE FROM "Faq"
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY LOWER(TRIM(TRAILING '?!.' FROM REGEXP_REPLACE(TRIM(question), '\s+', ' ', 'g')))
                   ORDER BY created_at ASC
               ) AS rn
        FROM "Faq"
    ) ranked
    WHERE rn > 1
);

-- Enforces "one FAQ per question" at the database level so that two concurrent
-- submissions of the same question can never both insert a row (closing the
-- check-then-insert race that application-level lookups alone can't prevent).
CREATE UNIQUE INDEX IF NOT EXISTS faq_question_normalized_unique_idx
ON "Faq" (
    LOWER(TRIM(TRAILING '?!.' FROM REGEXP_REPLACE(TRIM(question), '\s+', ' ', 'g')))
);
