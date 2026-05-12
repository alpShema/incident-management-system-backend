package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;


@Entity
@Table(name = "Incident")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {
    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Generated(event = EventType.INSERT)
    @Column(name = "incident_no", nullable = false, insertable = false, updatable = false)
    private Integer incidentNo;

    @Column(nullable = false)
    private String description;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "location_id", nullable = false)
    private String locationId;

    @Column(name = "incident_type_id", nullable = false)
    private String incidentTypeId;

    @Column(name = "status_id")
    private String statusId;

    @Column(name = "severity_id")
    private String severityId;

    @Column(name = "assigned_to_id")
    private String assignedToId;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Relationships ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", insertable = false, updatable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_type_id", insertable = false, updatable = false)
    private IncidentType incidentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", insertable = false, updatable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "severity_id", insertable = false, updatable = false)
    private Severity severity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id", insertable = false, updatable = false)
    private Agent assignedTo;

    // ── Lifecycle ──

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
