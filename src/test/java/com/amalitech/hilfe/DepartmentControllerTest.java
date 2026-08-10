package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.DepartmentController;
import com.amalitech.hilfe.dto.CreateDepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.dto.UpdateDepartmentStatusRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepartmentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
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
    void createDepartment_valid_returns201() throws Exception {
        when(departmentService.createDepartment(eq("admin-1"), any()))
                .thenReturn(new DepartmentResponse("dept-1", "Facilities", "Facilities dept", true, 0, "admin-1"));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.create"));

        mvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateDepartmentRequest("Facilities", "Facilities dept", "admin-1")))
                        .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.headUserId").value("admin-1"));
    }

    @Test
    void createDepartment_missingHeadUserId_returns400() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.create"));

        mvc.perform(post("/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateDepartmentRequest("Facilities", "Facilities dept", null)))
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDepartment_setsHead_returns200() throws Exception {
        when(departmentService.updateDepartment(eq("admin-1"), eq("dept-1"), any()))
                .thenReturn(new DepartmentResponse("dept-1", "Facilities", "Facilities dept", true, 0, "admin-1"));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.update"));

        mvc.perform(patch("/departments/dept-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepartmentRequest("Facilities", null, "admin-1")))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headUserId").value("admin-1"));
    }

    @Test
    void updateDepartment_blankName_returns400() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.update"));

        mvc.perform(patch("/departments/dept-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepartmentRequest("   ", null, null)))
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDepartment_emptyBody_returns400() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.update"));

        mvc.perform(patch("/departments/dept-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepartmentRequest(null, null, null)))
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDepartment_descriptionOnly_returns200() throws Exception {
        when(departmentService.updateDepartment(eq("admin-1"), eq("dept-1"), any()))
                .thenReturn(new DepartmentResponse("dept-1", "Facilities", "New description", true, 0, null));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.update"));

        mvc.perform(patch("/departments/dept-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DepartmentRequest(null, "New description", null)))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Facilities"))
                .andExpect(jsonPath("$.data.description").value("New description"));
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

    @Test
    void listDepartmentsHeadedBy_returnsAllDepartmentsForUser() throws Exception {
        when(departmentService.listDepartmentsHeadedBy("admin-1")).thenReturn(List.of(
                new DepartmentResponse("dept-1", "Facilities", "Facilities dept", true, 0, "admin-1"),
                new DepartmentResponse("dept-2", "Support", "Support dept", true, 0, "admin-1")));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "department.read"));

        mvc.perform(get("/departments/heads/admin-1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("dept-1"))
                .andExpect(jsonPath("$.data[1].id").value("dept-2"));
    }
}
