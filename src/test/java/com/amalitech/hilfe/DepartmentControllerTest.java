package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.DepartmentController;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.dto.UpdateDepartmentStatusRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.DepartmentService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepartmentController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class DepartmentControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean DepartmentService departmentService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
    }

    private DepartmentResponse department(Boolean status) {
        return new DepartmentResponse("dept-1", "Facilities", "Facilities dept", status, 0, null);
    }

    @Test
    void updateDepartmentStatus_activate_returns200() throws Exception {
        when(departmentService.updateDepartmentStatus("dept-1", true)).thenReturn(department(true));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.delete"));

        mvc.perform(patch("/departments/dept-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDepartmentStatusRequest(true)))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Department status updated successfully"))
                .andExpect(jsonPath("$.data.status").value(true));
    }

    @Test
    void updateDepartmentStatus_deactivate_returns200() throws Exception {
        when(departmentService.updateDepartmentStatus("dept-1", false)).thenReturn(department(false));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.delete"));

        mvc.perform(patch("/departments/dept-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDepartmentStatusRequest(false)))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(false));
    }

    @Test
    void updateDepartmentStatus_missingStatus_returns400() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.delete"));

        mvc.perform(patch("/departments/dept-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDepartmentStatus_notFound_returns404() throws Exception {
        when(departmentService.updateDepartmentStatus(eq("dept-1"), any()))
                .thenThrow(new ArmsAuthException("Department not found", 404));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.delete"));

        mvc.perform(patch("/departments/dept-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDepartmentStatusRequest(false)))
                        .with(authentication(auth)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDepartment_inactive_returns200() throws Exception {
        when(departmentService.getDepartment("dept-1")).thenReturn(department(false));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.read"));

        mvc.perform(get("/departments/dept-1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(false));
    }
}
