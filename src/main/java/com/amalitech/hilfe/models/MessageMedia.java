package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "MessageMedia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageMedia {

    @Id
    private String id;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", insertable = false, updatable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
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
