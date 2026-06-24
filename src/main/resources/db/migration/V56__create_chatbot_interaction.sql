CREATE TABLE "ChatbotInteraction" (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES "User"(id),
    query       TEXT NOT NULL,
    faq_id      TEXT REFERENCES "Faq"(id),
    confidence  FLOAT,
    outcome     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ON "ChatbotInteraction" (user_id);
CREATE INDEX ON "ChatbotInteraction" (outcome);
CREATE INDEX ON "ChatbotInteraction" (created_at);
