package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "AgentGroup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentGroup {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    private Boolean status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Relationships ──

    @OneToMany(mappedBy = "agentGroup")
    @Builder.Default
    private List<Agent> agents = new ArrayList<>();

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
