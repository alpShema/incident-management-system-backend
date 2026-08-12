package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Location")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean status = true;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "business_hours_start", nullable = false)
    @Builder.Default
    private LocalTime businessHoursStart = LocalTime.of(8, 0);

    @Column(name = "business_hours_end", nullable = false)
    @Builder.Default
    private LocalTime businessHoursEnd = LocalTime.of(17, 30);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Relationships ──

    @OneToMany(mappedBy = "location")
    @Builder.Default
    private List<User> users = new ArrayList<>();

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
