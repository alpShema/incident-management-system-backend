package com.amalitech.hilfe.services;

import com.amalitech.hilfe.constants.ApiMessages;
import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Permission;
import com.amalitech.hilfe.models.Role;
import com.amalitech.hilfe.models.RolePermission;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.PermissionRepository;
import com.amalitech.hilfe.repositories.RolePermissionRepository;
import com.amalitech.hilfe.repositories.RoleRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RoleService {
    private static final Set<String> PROTECTED_ROLES = Set.of("CLIENT", "AGENT", "ADMIN", "ADMIN_AGENT", "SUPER_ADMIN");
    private static final String ROLE_NOT_FOUND = "Role not found";
    private static final String ROLE_ALREADY_EXISTS_MESSAGE = "A role with this name already exists. Please choose a different name.";
    private static final String INVALID_PERMISSIONS_MESSAGE = "One or more of the selected permissions are invalid.";
    private static final String AT_LEAST_ONE_USER_MESSAGE = "At least one user must be selected.";
    private static final String USERS_NOT_FOUND_MESSAGE = "One or more selected users could not be found.";
    private static final String SORT_NAME = "name";
    private static final String SORT_DESCRIPTION = "description";
    private static final String SORT_UPDATED_AT = "updatedAt";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(SORT_NAME, SORT_DESCRIPTION);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final RoleAccessSyncService roleAccessSyncService;

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        String code = generateRoleCode(request.name());
        if (PROTECTED_ROLES.contains(code) || roleRepository.existsByCode(code)) {
            throw new ArmsAuthException(ROLE_ALREADY_EXISTS_MESSAGE, 409);
        }
        if (roleRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException(ROLE_ALREADY_EXISTS_MESSAGE, 409);
        }

        List<String> normalizedPermissionCodes = request.permissionCodes().stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .toList();
        List<Permission> permissions = permissionRepository.findByCodeIn(normalizedPermissionCodes);
        if (permissions.size() != normalizedPermissionCodes.size()) {
            throw new ArmsAuthException(INVALID_PERMISSIONS_MESSAGE, 400);
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
        return roleRepository.findAllFiltered(pattern, ensureSorted(pageable)).map(this::toResponse);
    }

    /**
     * Only {@code name}/{@code description} are exposed as sortable fields; any other or absent
     * sort falls back to most-recently-created-or-edited first ({@code updatedAt} is refreshed on
     * both create and update, so it alone captures "created or edited").
     */
    private Pageable ensureSorted(Pageable pageable) {
        List<Sort.Order> allowedOrders = pageable.getSort().stream()
                .filter(order -> ALLOWED_SORT_FIELDS.contains(order.getProperty()))
                .toList();
        Sort sort = allowedOrders.isEmpty() ? Sort.by(Sort.Direction.DESC, SORT_UPDATED_AT) : Sort.by(allowedOrders);
        return pageable.isUnpaged()
                ? Pageable.unpaged(sort)
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    @Transactional
    public BulkAssignRoleResponse bulkAssignRole(String roleCode, BulkAssignRoleRequest request) {
        String normalizedCode = normalizeRoleCode(roleCode);
        Role role = roleRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ArmsAuthException(ROLE_NOT_FOUND, 404));

        List<String> userIds = request.userIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw new ArmsAuthException(AT_LEAST_ONE_USER_MESSAGE, 400);
        }

        List<User> users = userRepository.findAllById(userIds);
        if (users.size() != userIds.size()) {
            throw new ArmsAuthException(USERS_NOT_FOUND_MESSAGE, 404);
        }

        users.forEach(user -> {
            user.setRoleCode(role.getCode());
            roleAccessSyncService.syncAgentRecord(user, role.getCode());
            roleAccessSyncService.syncAdminRecord(user, role.getCode());
        });
        userRepository.saveAll(users);
        return new BulkAssignRoleResponse(role.getCode(), users.size(), users.stream().map(User::getId).toList());
    }

    @Transactional
    public RoleResponse updateRole(String roleCode, UpdateRoleRequest request) {
        String normalizedCode = normalizeRoleCode(roleCode);
        Role role = roleRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ArmsAuthException(ROLE_NOT_FOUND, 404));

        if (Boolean.TRUE.equals(role.getSystemDefined())) {
            throw new ArmsAuthException("System-defined roles cannot be modified.", 403);
        }

        if (request.name() != null) {
            String trimmedName = request.name().trim();
            if (trimmedName.isBlank()) {
                throw new ArmsAuthException("Role name must not be blank.", 400);
            }
            if (roleRepository.existsByNameIgnoreCaseAndCodeNot(trimmedName, normalizedCode)) {
                throw new ArmsAuthException(ROLE_ALREADY_EXISTS_MESSAGE, 409);
            }
            role.setName(trimmedName);
        }

        if (request.description() != null) {
            role.setDescription(request.description().isBlank() ? null : request.description().trim());
        }

        if (request.permissionCodes() != null) {
            List<String> normalizedCodes = request.permissionCodes().stream()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .distinct()
                    .toList();
            List<Permission> permissions = permissionRepository.findByCodeIn(normalizedCodes);
            if (permissions.size() != normalizedCodes.size()) {
                throw new ArmsAuthException(INVALID_PERMISSIONS_MESSAGE, 400);
            }
            rolePermissionRepository.deleteByRoleCode(normalizedCode);
            List<RolePermission> links = permissions.stream()
                    .map(p -> RolePermission.builder()
                            .roleCode(normalizedCode)
                            .permission(p)
                            .build())
                    .toList();
            rolePermissionRepository.saveAll(links);
            roleRepository.save(role);
            return toResponse(role, links);
        }

        roleRepository.save(role);
        return toResponse(role);
    }

    @Transactional
    public BulkAssignRoleResponse removeUsersFromRole(String roleCode, BulkAssignRoleRequest request) {
        String normalizedCode = normalizeRoleCode(roleCode);
        roleRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ArmsAuthException(ROLE_NOT_FOUND, 404));

        List<String> userIds = request.userIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw new ArmsAuthException(AT_LEAST_ONE_USER_MESSAGE, 400);
        }

        List<User> users = userRepository.findAllById(userIds);
        if (users.size() != userIds.size()) {
            throw new ArmsAuthException(USERS_NOT_FOUND_MESSAGE, 404);
        }

        List<User> toUpdate = users.stream()
                .filter(u -> normalizedCode.equals(u.getRoleCode()))
                .map(u -> { u.setRoleCode(null); return u; })
                .toList();
        toUpdate.forEach(user -> {
            roleAccessSyncService.syncAgentRecord(user, null);
            roleAccessSyncService.syncAdminRecord(user, null);
        });
        userRepository.saveAll(toUpdate);
        return new BulkAssignRoleResponse(normalizedCode, toUpdate.size(), toUpdate.stream().map(User::getId).toList());
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
            throw new ArmsAuthException(ApiMessages.ROLE_REQUIRED, 400);
        }
        return roleCode.trim().toUpperCase();
    }

}
