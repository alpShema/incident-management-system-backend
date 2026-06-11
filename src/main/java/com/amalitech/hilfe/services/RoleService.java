package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Permission;
import com.amalitech.hilfe.models.Role;
import com.amalitech.hilfe.models.RolePermission;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.PermissionRepository;
import com.amalitech.hilfe.repositories.RolePermissionRepository;
import com.amalitech.hilfe.repositories.RoleRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {
    private static final Set<String> PROTECTED_ROLES = Set.of("CLIENT", "AGENT", "ADMIN", "SUPER_ADMIN");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        String code = generateRoleCode(request.name());
        if (PROTECTED_ROLES.contains(code) || roleRepository.existsByCode(code)) {
            throw new ArmsAuthException("Role already exists", 409);
        }
        if (roleRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Role name already exists", 409);
        }

        List<String> normalizedPermissionCodes = request.permissionCodes().stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .toList();
        List<Permission> permissions = permissionRepository.findByCodeIn(normalizedPermissionCodes);
        if (permissions.size() != normalizedPermissionCodes.size()) {
            throw new ArmsAuthException("One or more permission codes are invalid", 400);
        }

        Role role = roleRepository.save(Role.builder()
                .id(UUID.randomUUID().toString())
                .code(code)
                .name(request.name().trim())
                .description(request.description().trim())
                .systemDefined(false)
                .build());

        List<RolePermission> links = permissions.stream()
                .map(permission -> RolePermission.builder()
                        .roleCode(role.getCode())
                        .permission(permission)
                        .build())
                .toList();
        rolePermissionRepository.saveAll(links);

        return toResponse(role, links);
    }

    public Page<RoleResponse> listRoles(String query, Pageable pageable) {
        String pattern = null;
        if (query != null && !query.isBlank()) {
            pattern = "%" + query.toLowerCase()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_") + "%";
        }
        return roleRepository.findAllFiltered(pattern, pageable).map(this::toResponse);
    }

    @Transactional
    public BulkAssignRoleResponse bulkAssignRole(String roleCode, BulkAssignRoleRequest request) {
        String normalizedCode = normalizeRoleCode(roleCode);
        Role role = roleRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ArmsAuthException("Role not found", 404));

        List<String> userIds = request.userIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw new ArmsAuthException("userIds must not be empty", 400);
        }

        List<User> users = userRepository.findAllById(userIds);
        if (users.size() != userIds.size()) {
            Set<String> found = users.stream().map(User::getId).collect(Collectors.toSet());
            List<String> missing = userIds.stream().filter(id -> !found.contains(id)).toList();
            throw new ArmsAuthException("Users not found: " + String.join(", ", missing), 404);
        }

        users.forEach(user -> {
            user.setRoleCode(role.getCode());
            ensureAgentRecordIfNeeded(user, role.getCode());
        });
        userRepository.saveAll(users);
        return new BulkAssignRoleResponse(role.getCode(), users.size(), users.stream().map(User::getId).toList());
    }

    public PermissionCatalogResponse permissionCatalog() {
        List<RoleResponse.PermissionItem> all = permissionRepository.findAllByOrderByCodeAsc().stream()
                .map(p -> new RoleResponse.PermissionItem(p.getCode(), p.getName(), p.getDescription()))
                .toList();

        List<RoleResponse.PermissionItem> incident = all.stream()
                .filter(p -> p.code() != null && p.code().startsWith("incident."))
                .toList();

        List<RoleResponse.PermissionItem> agentAndGroup = all.stream()
                .filter(p -> p.code() != null
                        && (p.code().startsWith("agent.") || p.code().startsWith("agent-group.")))
                .toList();

        List<RoleResponse.PermissionItem> settings = all.stream()
                .filter(p -> p.code() != null && (
                        p.code().startsWith("department.")
                                || p.code().startsWith("status.")
                                || p.code().startsWith("severity.")
                                || p.code().startsWith("location.")
                                || p.code().startsWith("incident-type.")
                                || p.code().startsWith("incident-category.")
                                || p.code().startsWith("system.")))
                .toList();

        List<RoleResponse.PermissionItem> user = all.stream()
                .filter(p -> p.code() != null && p.code().startsWith("rbac."))
                .toList();

        List<RoleResponse.PermissionItem> report = all.stream()
                .filter(p -> p.code() != null && p.code().startsWith("dashboard."))
                .toList();

        return new PermissionCatalogResponse(incident, agentAndGroup, settings, user, report);
    }

    private RoleResponse toResponse(Role role) {
        List<RolePermission> links = rolePermissionRepository.findByRoleCodeWithPermission(role.getCode());
        return toResponse(role, links);
    }

    private RoleResponse toResponse(Role role, List<RolePermission> links) {
        List<RoleResponse.PermissionItem> permissions = links.stream()
                .map(RolePermission::getPermission)
                .filter(Objects::nonNull)
                .map(p -> new RoleResponse.PermissionItem(p.getCode(), p.getName(), p.getDescription()))
                .sorted(Comparator.comparing(RoleResponse.PermissionItem::code, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getSystemDefined(),
                permissions
        );
    }

    private String generateRoleCode(String name) {
        return name.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
    }

    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new ArmsAuthException("roleCode is required", 400);
        }
        return roleCode.trim().toUpperCase();
    }

    private void ensureAgentRecordIfNeeded(User user, String roleCode) {
        if (!"AGENT".equalsIgnoreCase(roleCode)) {
            return;
        }
        if (agentRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }
        agentRepository.save(Agent.builder()
                .id(UUID.randomUUID().toString())
                .userId(user.getId())
                .status(true)
                .build());
    }
}
