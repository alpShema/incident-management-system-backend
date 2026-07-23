-- Materializes the SLA status (MET, ON_TRACK, AT_RISK, BREACHED, NOT_TRACKED) that SlaService
-- already computes on read, so incident list queries can filter by SLA status without
-- reproducing the due-date/at-risk-window arithmetic in SQL.
ALTER TABLE incident_sla
    ADD COLUMN IF NOT EXISTS response_status   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS resolution_status VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_incident_sla_response_status
    ON incident_sla(response_status);

CREATE INDEX IF NOT EXISTS idx_incident_sla_resolution_status
    ON incident_sla(resolution_status);
