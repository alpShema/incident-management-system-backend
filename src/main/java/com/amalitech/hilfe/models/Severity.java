package com.amalitech.hilfe.models;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "Severity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Severity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    private Boolean status = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
