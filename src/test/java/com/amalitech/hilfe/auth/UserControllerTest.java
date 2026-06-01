package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.controllers.UserController;
import com.amalitech.hilfe.dto.UpdateUserRoleRequest;
import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.services.TokenService;
import com.amalitech.hilfe.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class UserControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean UserService userService;
    @MockitoBean TokenService tokenService;

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken adminAuth() {
        var principal = new com.amalitech.hilfe.services.JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, List.of(() -> "ROLE_ADMIN"));
    }

    @Test
    void listUsers_adminRequest_returnsPaginatedUsers() throws Exception {
        when(userService.getUsers(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(new PageImpl<>(
                List.of(new UserRoleSummaryResponse("u1", "john@test.com", "John Doe", "http://img.png", RoleCode.ADMIN, true, "Accra", 3L, 1L)),
                PageRequest.of(0, 10),
                1
        ));

        mvc.perform(get("/users")
                        .param("page", "0")
                        .param("size", "10")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Users retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].userId").value("u1"))
                .andExpect(jsonPath("$.data.items[0].roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[0].submittedIncidentsCount").value(3))
                .andExpect(jsonPath("$.data.items[0].assignedIncidentsCount").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void assignUserRole_adminRequest_returnsUpdatedUserRole() throws Exception {
        when(userService.assignUserRole(anyString(), eq("u1"), eq("ADMIN"))).thenReturn(
                new UserRoleSummaryResponse("u1", "john@test.com", "John Doe", "http://img.png", RoleCode.ADMIN, true, "Accra", 3L, 0L)
        );

        mvc.perform(patch("/users/u1/role")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(RoleCode.ADMIN)))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User role updated successfully"))
                .andExpect(jsonPath("$.data.userId").value("u1"))
                .andExpect(jsonPath("$.data.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.data.submittedIncidentsCount").value(3))
                .andExpect(jsonPath("$.data.assignedIncidentsCount").value(0));
    }

    @Test
    void listUsers_filterByRole_returnsCountsFields() throws Exception {
        when(userService.getUsers(isNull(), eq(RoleCode.AGENT), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(new UserRoleSummaryResponse("u2", "agent@test.com", "Agent One", null, RoleCode.AGENT, true, "Accra", 1L, 7L)),
                        PageRequest.of(0, 20), 1));

        mvc.perform(get("/users").param("roleCode", "AGENT").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].roleCode").value("AGENT"))
                .andExpect(jsonPath("$.data.items[0].submittedIncidentsCount").value(1))
                .andExpect(jsonPath("$.data.items[0].assignedIncidentsCount").value(7));
    }

    @Test
    void listUsers_emptyResult_returns200() throws Exception {
        when(userService.getUsers(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/users").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listUsers_filterByStatusActive_passesStringToService() throws Exception {
        when(userService.getUsers(isNull(), isNull(), isNull(), eq("Active"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/users").param("status", "Active").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void listUsers_filterByLocationId_passesStringToService() throws Exception {
        when(userService.getUsers(isNull(), isNull(), eq("LOC-ACCRA"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/users").param("locationId", "LOC-ACCRA").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void assignUserRole_invalidRoleCode_returns404() throws Exception {
        when(userService.assignUserRole(anyString(), eq("u1"), eq("CLIENTS")))
                .thenThrow(new com.amalitech.hilfe.exceptions.ArmsAuthException("Role not found", 404));

        mvc.perform(patch("/users/u1/role")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"CLIENTS\"}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Role not found"));
    }
}
