-- Convert auto_close_hours to auto_close_seconds (multiply existing value by 3600)
INSERT INTO system_config (key, value)
SELECT 'auto_close_seconds', CAST(CAST(value AS INTEGER) * 3600 AS TEXT)
FROM system_config
WHERE key = 'auto_close_hours'
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

DELETE FROM system_config WHERE key = 'auto_close_hours';
