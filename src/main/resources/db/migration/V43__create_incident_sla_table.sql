CREATE TABLE IF NOT EXISTS incident_sla (
    incident_id                        VARCHAR(255) PRIMARY KEY REFERENCES "Incident"(id) ON DELETE CASCADE,
    response_threshold_minutes         INTEGER,
    resolution_threshold_minutes       INTEGER,
    response_due_at                    TIMESTAMP,
    resolution_due_at                  TIMESTAMP,
    first_response_at                  TIMESTAMP,
    resolved_at_snapshot               TIMESTAMP,
    response_elapsed_ms                BIGINT,
    resolution_elapsed_ms              BIGINT,
    pause_started_at                   TIMESTAMP,
    accumulated_pause_ms               BIGINT NOT NULL DEFAULT 0,
    response_breached_at               TIMESTAMP,
    resolution_breached_at             TIMESTAMP,
    response_at_risk_notified_at       TIMESTAMP,
    response_breach_notified_at        TIMESTAMP,
    resolution_at_risk_notified_at     TIMESTAMP,
    resolution_breach_notified_at      TIMESTAMP,
    resolution_remaining_ms_on_resolve BIGINT,
    created_at                         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_incident_sla_response_due
    ON incident_sla(response_due_at)
    WHERE first_response_at IS NULL AND pause_started_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_incident_sla_resolution_due
    ON incident_sla(resolution_due_at)
    WHERE resolved_at_snapshot IS NULL AND pause_started_at IS NULL;
