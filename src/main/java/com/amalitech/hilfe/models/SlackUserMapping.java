package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slack_user_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlackUserMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "slack_user_id", nullable = false, unique = true)
    private String slackUserId;

    @Column(name = "hilfe_user_id", nullable = false)
    private String hilfeUserId;

    @Column(name = "slack_team_id", nullable = false)
    private String slackTeamId;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "bot_token")
    private String botToken;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hilfe_user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        if (connectedAt == null) {
            connectedAt = Instant.now();
        }
    }
}
