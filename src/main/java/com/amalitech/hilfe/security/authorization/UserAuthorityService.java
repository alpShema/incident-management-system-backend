package com.amalitech.hilfe.security.authorization;

import com.amalitech.hilfe.models.Permission;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.RolePermission;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.RolePermissionRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class UserAuthorityService {
    private static final String ROLE_PREFIX = "ROLE_";

    private static final Map<String, String> LEGACY_PERMISSION_MAPPING = Map.ofEntries(
        Map.entry("create-agent", "agent.create"),
        Map.entry("edit-agent", "agent.update"),
        Map.entry("delete-agent", "agent.delete"),
        Map.entry("create-agent-group", "agent-group.create"),
        Map.entry("edit-agent-group", "agent-group.update"),
        Map.entry("delete-agent-group", "agent-group.delete"),
        Map.entry("create-status", "status.create"),
        Map.entry("edit-status", "status.update"),
        Map.entry("delete-status", "status.delete"),
        Map.entry("create-severity", "severity.create"),
        Map.entry("edit-severity", "severity.update"),
        Map.entry("delete-severity", "severity.delete"),
        Map.entry("create-location", "location.create"),
        Map.entry("edit-location", "location.update"),
        Map.entry("delete-location", "location.delete"),
        Map.entry("create-incident-type", "incident-type.create"),
        Map.entry("edit-incident-type", "incident-type.update"),
        Map.entry("delete-incident-type", "incident-type.delete"),
        Map.entry("change-status", "incident.status.change"),
        Map.entry("change-severity", "incident.severity.change"),
        Map.entry("assign-incident", "incident.assign")
    );

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final String bootstrapSuperAdminUserId;

    public UserAuthorityService(
        UserRepository userRepository,
        RolePermissionRepository rolePermissionRepository,
        @Value("${app.rbac.bootstrap-super-admin-user-id:}") String bootstrapSuperAdminUserId
    ) {
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.bootstrapSuperAdminUserId = bootstrapSuperAdminUserId;
    }

    public Optional<ResolvedAuthorities> resolveByUserId(String userId) {
        return userRepository.findAuthUserById(userId).map(this::resolve);
    }

    public ResolvedAuthorities resolve(User user) {
        RoleCode roleCode = resolveRoleCode(user);
        Set<String> authorities = new LinkedHashSet<>();

        authorities.add(toRoleAuthority(roleCode));
        loadRolePermissions(roleCode).stream()
            .map(Permission::getCode)
            .forEach(authorities::add);
        mapLegacyPermissions(user).forEach(authorities::add);

        return new ResolvedAuthorities(
            user.getId(),
            user.getEmail(),
            roleCode,
            authorities.stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    private RoleCode resolveRoleCode(User user) {
        if (isBootstrapSuperAdmin(user)) {
            return RoleCode.SUPER_ADMIN;
        }
        if (user.getRoleCode() != null) {
            return user.getRoleCode();
        }
        if (user.getAdmin() != null && user.getAdmin().isStatus()) {
            return RoleCode.ADMIN;
        }
        if (user.getAgent() != null && Boolean.TRUE.equals(user.getAgent().getStatus())) {
            return RoleCode.AGENT;
        }
        return RoleCode.CLIENT;
    }

    private boolean isBootstrapSuperAdmin(User user) {
        return !bootstrapSuperAdminUserId.isBlank() && bootstrapSuperAdminUserId.equals(user.getId());
    }

    private Set<String> mapLegacyPermissions(User user) {
        Set<String> permissions = new LinkedHashSet<>();
        if (user.getPermissions() == null) {
            return permissions;
        }

        for (String permission : user.getPermissions()) {
            String mappedPermission = LEGACY_PERMISSION_MAPPING.get(permission);
            if (mappedPermission != null) {
                permissions.add(mappedPermission);
            }
        }
        return permissions;
    }

    private Set<Permission> loadRolePermissions(RoleCode roleCode) {
        Set<Permission> permissions = new LinkedHashSet<>();
        for (RolePermission rolePermission : rolePermissionRepository.findAllByRoleCode(roleCode)) {
            permissions.add(rolePermission.getPermission());
        }
        return permissions;
    }

    private String toRoleAuthority(RoleCode roleCode) {
        return ROLE_PREFIX + roleCode.name();
    }

    public record ResolvedAuthorities(
        String userId,
        String email,
        RoleCode roleCode,
        Collection<? extends GrantedAuthority> authorities
    ) {}
}
