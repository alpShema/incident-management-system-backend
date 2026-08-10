package com.amalitech.hilfe.security.authorization;

import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.RolePermissionRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.*;

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
            Map.entry("create-department", "department.create"),
            Map.entry("edit-department", "department.update"),
            Map.entry("delete-department", "department.delete"),
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
    private final DepartmentRepository departmentRepository;
    private final String bootstrapSuperAdminUserId;

    public UserAuthorityService(
            UserRepository userRepository,
            RolePermissionRepository rolePermissionRepository,
            DepartmentRepository departmentRepository,
            @Value("${app.rbac.bootstrap-super-admin-user-id:}") String bootstrapSuperAdminUserId
    ) {
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.departmentRepository = departmentRepository;
        this.bootstrapSuperAdminUserId = bootstrapSuperAdminUserId;
    }

    public Optional<ResolvedAuthorities> resolveByUserId(String userId) {
        return userRepository.findAuthUserById(userId)
                .filter(u -> !Boolean.FALSE.equals(u.getStatus()))
                .map(this::resolve);
    }

    public ResolvedAuthorities resolve(User user) {
        String roleCode = resolveRoleCode(user);
        Set<String> authorities = new LinkedHashSet<>();

        authorities.add(toRoleAuthority(roleCode));
        authorities.addAll(loadRolePermissionCodes(roleCode));
        authorities.addAll(mapLegacyPermissions(user));
        if (departmentRepository.existsByHeadUserId(user.getId())) {
            authorities.add(RbacPermissions.DEPARTMENT_HEAD);
        }

        return new ResolvedAuthorities(
                user.getId(),
                user.getEmail(),
                roleCode,
                authorities.stream().map(SimpleGrantedAuthority::new).toList(),
                user.getTokenVersion()
        );
    }

    private String resolveRoleCode(User user) {
        if (isBootstrapSuperAdmin(user)) {
            return "SUPER_ADMIN";
        }
        if (user.getRoleCode() != null) {
            return user.getRoleCode();
        }
        if (user.getAdmin() != null && user.getAdmin().isStatus()) {
            return "ADMIN";
        }
        if (user.getAgent() != null && Boolean.TRUE.equals(user.getAgent().getStatus())) {
            return "AGENT";
        }
        return "CLIENT";
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

    private Set<String> loadRolePermissionCodes(String roleCode) {
        return new LinkedHashSet<>(rolePermissionRepository.findPermissionCodesByRoleCode(roleCode));
    }

    private String toRoleAuthority(String roleCode) {
        return ROLE_PREFIX + roleCode;
    }

    public record ResolvedAuthorities(
            String userId,
            String email,
            String roleCode,
            Collection<? extends GrantedAuthority> authorities,
            int tokenVersion
    ) {
        public ResolvedAuthorities(
                String userId,
                String email,
                RoleCode roleCode,
                Collection<? extends GrantedAuthority> authorities,
                int tokenVersion
        ) {
            this(userId, email, roleCode == null ? null : roleCode.name(), authorities, tokenVersion);
        }
    }
}
