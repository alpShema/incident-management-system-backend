CREATE TABLE IF NOT EXISTS "ChatbotInteraction" (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES "User"(id),
    query       TEXT NOT NULL,
    faq_id      TEXT REFERENCES "Faq"(id),
    confidence  FLOAT,
    outcome     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS chatbot_interaction_user_idx     ON "ChatbotInteraction" (user_id);
CREATE INDEX IF NOT EXISTS chatbot_interaction_outcome_idx  ON "ChatbotInteraction" (outcome);
CREATE INDEX IF NOT EXISTS chatbot_interaction_created_idx  ON "ChatbotInteraction" (created_at);
