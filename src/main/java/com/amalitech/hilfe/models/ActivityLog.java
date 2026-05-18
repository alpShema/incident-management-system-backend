package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(name = "target_user_id")
    private String targetUserId;

    @Column(nullable = false)
    private String action;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(nullable = false)
    private String description;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", insertable = false, updatable = false)
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", insertable = false, updatable = false)
    private User targetUser;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
