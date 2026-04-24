package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {

    @Id
    private String id;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(nullable = false)
    private String url;

    // ── Relationships ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", insertable = false, updatable = false)
    private Incident incident;
}
