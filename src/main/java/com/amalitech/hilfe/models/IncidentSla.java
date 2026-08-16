package com.amalitech.hilfe.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "incident_sla")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentSla {

    @Id
    @Column(name = "incident_id")
    private String incidentId;

    @Column(name = "response_threshold_minutes")
    private Integer responseThresholdMinutes;

    @Column(name = "resolution_threshold_minutes")
    private Integer resolutionThresholdMinutes;

    @Column(name = "response_due_at")
    private Instant responseDueAt;

    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "resolved_at_snapshot")
    private Instant resolvedAtSnapshot;

    @Column(name = "response_elapsed_ms")
    private Long responseElapsedMs;

    @Column(name = "resolution_elapsed_ms")
    private Long resolutionElapsedMs;

    @Column(name = "pause_started_at")
    private Instant pauseStartedAt;

    @Column(name = "accumulated_pause_ms", nullable = false)
    @Builder.Default
    private long accumulatedPauseMs = 0L;

    @Column(name = "response_breached_at")
    private Instant responseBreachedAt;

    @Column(name = "resolution_breached_at")
    private Instant resolutionBreachedAt;

    @Column(name = "response_at_risk_notified_at")
    private Instant responseAtRiskNotifiedAt;

    @Column(name = "response_breach_notified_at")
    private Instant responseBreachNotifiedAt;

    @Column(name = "resolution_at_risk_notified_at")
    private Instant resolutionAtRiskNotifiedAt;

    @Column(name = "resolution_breach_notified_at")
    private Instant resolutionBreachNotifiedAt;

    @Column(name = "resolution_remaining_ms_on_resolve")
    private Long resolutionRemainingMsOnResolve;

    // Materialized snapshot of SlaService.computeResponseStatus/computeResolutionStatus — one of
    // NOT_TRACKED, MET, BREACHED, AT_RISK, ON_TRACK. Kept in sync at every point those are recomputed
    // (creation, first response, resolution/reopen, pause/resume, and the per-minute scheduler scan)
    // so incident list queries can filter by SLA status without recomputing due-date arithmetic in SQL.
    @Column(name = "response_status")
    private String responseStatus;

    @Column(name = "resolution_status")
    private String resolutionStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", insertable = false, updatable = false)
    private Incident incident;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
