ALTER TABLE "Severity"
    ADD COLUMN IF NOT EXISTS response_time_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS resolution_time_minutes INTEGER;

UPDATE "Severity"
SET response_time_minutes = 15,
    resolution_time_minutes = 120
WHERE LOWER(name) = 'critical'
  AND (response_time_minutes IS NULL OR resolution_time_minutes IS NULL);

UPDATE "Severity"
SET response_time_minutes = 60,
    resolution_time_minutes = 480
WHERE LOWER(name) = 'high'
  AND (response_time_minutes IS NULL OR resolution_time_minutes IS NULL);

UPDATE "Severity"
SET response_time_minutes = 240,
    resolution_time_minutes = 1440
WHERE LOWER(name) = 'medium'
  AND (response_time_minutes IS NULL OR resolution_time_minutes IS NULL);

UPDATE "Severity"
SET response_time_minutes = 480,
    resolution_time_minutes = 4320
WHERE LOWER(name) = 'low'
  AND (response_time_minutes IS NULL OR resolution_time_minutes IS NULL);

INSERT INTO system_config (key, value, updated_at)
VALUES ('sla_at_risk_pct', '20', NOW())
ON CONFLICT (key) DO NOTHING;
