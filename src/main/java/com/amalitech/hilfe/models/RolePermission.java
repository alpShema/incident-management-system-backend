package com.amalitech.hilfe.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_permissions_role_code_permission_id", columnNames = {
                "role_code",
                "permission_id"
        })
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public static class RolePermissionBuilder {
        public RolePermissionBuilder roleCode(String roleCode) {
            this.roleCode = roleCode;
            return this;
        }

        public RolePermissionBuilder roleCode(RoleCode roleCode) {
            this.roleCode = roleCode == null ? null : roleCode.name();
            return this;
        }
    }
}
