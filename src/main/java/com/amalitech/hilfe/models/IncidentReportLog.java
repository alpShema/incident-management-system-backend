package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "IncidentReportLog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentReportLog {

    @Id
    private String id;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "status_id", nullable = false)
    private String statusId;

    @Column(nullable = false)
    private Instant date;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Relationships ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", insertable = false, updatable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", insertable = false, updatable = false)
    private Status status;

    // ── Lifecycle ──

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
