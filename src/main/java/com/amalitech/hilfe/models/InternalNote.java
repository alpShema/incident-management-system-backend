package com.amalitech.hilfe.models;

import com.amalitech.hilfe.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "InternalNote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalNote {

    @Id
    private String id;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(nullable = false)
    @Convert(converter = EncryptedStringConverter.class)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Relationships ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    private User author;

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
