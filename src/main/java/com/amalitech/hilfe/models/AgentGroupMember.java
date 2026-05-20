package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "AgentGroupMember")
@IdClass(AgentGroupMemberId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentGroupMember {

    @Id
    @Column(name = "agent_id")
    private String agentId;

    @Id
    @Column(name = "agent_group_id")
    private String agentGroupId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", insertable = false, updatable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_group_id", insertable = false, updatable = false)
    private AgentGroup agentGroup;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
