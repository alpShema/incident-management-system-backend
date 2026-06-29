package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.RoleController;
import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.RoleService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoleController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class RoleControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RoleService roleService;
    @MockitoBean TokenService tokenService;

    final ObjectMapper objectMapper = new ObjectMapper();

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(() -> "ROLE_ADMIN", () -> "rbac.role.update", () -> "rbac.role.read", () -> "rbac.user-role.update"));
    }

    private UsernamePasswordAuthenticationToken readOnlyAuth() {
        return new UsernamePasswordAuthenticationToken(
                "readonly", null, List.of(() -> "rbac.role.read"));
    }

    private UsernamePasswordAuthenticationToken clientAuth() {
        return new UsernamePasswordAuthenticationToken(
                "client", null, List.of(() -> "ROLE_CLIENT"));
    }

    private RoleResponse stubRole() {
        return new RoleResponse("r1", "CUSTOM", "Custom", "desc", false, List.of());
    }

    @Test
    void createRole_withAdminAndPermission_returns201() throws Exception {
        when(roleService.createRole(any(CreateRoleRequest.class))).thenReturn(stubRole());

        mvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoleRequest("Custom", "desc", List.of("incident.create"))))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Role created successfully"))
                .andExpect(jsonPath("$.data.roleCode").value("CUSTOM"));
    }

    @Test
    void createRole_withoutRoleAuthority_returns403() throws Exception {
        mvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoleRequest("Custom", "desc", List.of("incident.create"))))
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRoles_withPermission_returns200() throws Exception {
        when(roleService.listRoles(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stubRole())));

        mvc.perform(get("/roles")
                        .with(authentication(readOnlyAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Roles retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].roleCode").value("CUSTOM"));
    }

    @Test
    void listRoles_withoutPermission_returns403() throws Exception {
        mvc.perform(get("/roles")
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkAssign_withAdminAndPermission_returns200() throws Exception {
        when(roleService.bulkAssignRole(eq("CUSTOM"), any(BulkAssignRoleRequest.class)))
                .thenReturn(new BulkAssignRoleResponse("CUSTOM", 1, List.of("u1")));

        mvc.perform(patch("/roles/CUSTOM/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkAssignRoleRequest(List.of("u1"))))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Users assigned to role successfully"))
                .andExpect(jsonPath("$.data.updatedCount").value(1));
    }

    @Test
    void bulkAssign_withoutPermission_returns403() throws Exception {
        mvc.perform(patch("/roles/CUSTOM/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkAssignRoleRequest(List.of("u1"))))
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_withAdminAndPermission_returns200() throws Exception {
        when(roleService.updateRole(eq("CUSTOM"), any(UpdateRoleRequest.class))).thenReturn(stubRole());

        mvc.perform(patch("/roles/CUSTOM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("New Name", null, null)))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Role updated successfully"))
                .andExpect(jsonPath("$.data.roleCode").value("CUSTOM"));
    }

    @Test
    void updateRole_withoutRoleAuthority_returns403() throws Exception {
        mvc.perform(patch("/roles/CUSTOM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Name", null, null)))
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_serviceThrows409_returns409() throws Exception {
        when(roleService.updateRole(eq("CUSTOM"), any(UpdateRoleRequest.class)))
                .thenThrow(new ArmsAuthException("Role name already exists", 409));

        mvc.perform(patch("/roles/CUSTOM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Duplicate", null, null)))
                        .with(authentication(adminAuth())))
                .andExpect(status().isConflict());
    }

    @Test
    void updateRole_serviceThrows404_returns404() throws Exception {
        when(roleService.updateRole(eq("MISSING"), any(UpdateRoleRequest.class)))
                .thenThrow(new ArmsAuthException("Role not found", 404));

        mvc.perform(patch("/roles/MISSING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Name", null, null)))
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeUsers_withAdminAndPermission_returns200() throws Exception {
        when(roleService.removeUsersFromRole(eq("CUSTOM"), any(BulkAssignRoleRequest.class)))
                .thenReturn(new BulkAssignRoleResponse("CUSTOM", 1, List.of("u1")));

        mvc.perform(delete("/roles/CUSTOM/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkAssignRoleRequest(List.of("u1"))))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Users removed from role successfully"))
                .andExpect(jsonPath("$.data.updatedCount").value(1));
    }

    @Test
    void removeUsers_withoutPermission_returns403() throws Exception {
        mvc.perform(delete("/roles/CUSTOM/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkAssignRoleRequest(List.of("u1"))))
                        .with(authentication(clientAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRole_serviceThrows409_returns409() throws Exception {
        when(roleService.createRole(any(CreateRoleRequest.class)))
                .thenThrow(new ArmsAuthException("Role already exists", 409));

        mvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoleRequest("Admin", "desc", List.of("incident.create"))))
                        .with(authentication(adminAuth())))
                .andExpect(status().isConflict());
    }
}
