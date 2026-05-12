UPDATE "Incident"
SET status_id = 'status-open'
WHERE status_id IS NULL;
