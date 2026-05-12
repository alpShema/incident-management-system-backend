ALTER TABLE "IncidentType"
    ADD COLUMN IF NOT EXISTS category_id VARCHAR(255)
        REFERENCES "IncidentCategory"(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_incident_type_category_id ON "IncidentType" (category_id);
