CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE "Faq" (
    id          TEXT PRIMARY KEY,
    question    TEXT NOT NULL,
    answer      TEXT NOT NULL,
    keywords    TEXT[],
    category    TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    embedding   vector(1536),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ON "Faq" USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
