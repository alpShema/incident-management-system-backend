-- Enforces "one FAQ per question" at the database level so that two concurrent
-- submissions of the same question can never both insert a row (closing the
-- check-then-insert race that application-level lookups alone can't prevent).
-- The expression mirrors FaqRepository.findByNormalizedQuestion: case-insensitive,
-- whitespace-collapsed, and ignoring trailing "?", "!", "." so trivial phrasing
-- differences (e.g. a missing "?") are still treated as the same question.
CREATE UNIQUE INDEX IF NOT EXISTS faq_question_normalized_unique_idx
ON "Faq" (
    LOWER(TRIM(TRAILING '?!.' FROM REGEXP_REPLACE(TRIM(question), '\s+', ' ', 'g')))
);
