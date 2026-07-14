-- IVFFlat is an approximate-search index meant for large datasets. With lists=100
-- but only a handful of FAQ rows, vectors are scattered across mostly-empty clusters,
-- so a query's nearest centroid can be empty and the index returns 0 rows even
-- though a match exists elsewhere in the table. A sequential scan is both exact and
-- fast at this row count, so the index is dropped rather than tuned.
DROP INDEX IF EXISTS "Faq_embedding_idx";
