ALTER TABLE "Location"
    ADD COLUMN IF NOT EXISTS timezone             VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN IF NOT EXISTS business_hours_start  TIME        NOT NULL DEFAULT '08:00:00',
    ADD COLUMN IF NOT EXISTS business_hours_end    TIME        NOT NULL DEFAULT '17:30:00';
